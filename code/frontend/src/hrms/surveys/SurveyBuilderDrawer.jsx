import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
  Drawer,
  Form,
  Input,
  Select,
  Switch,
  DatePicker,
  Button,
  Space,
  Card,
  Checkbox,
  Typography,
  Divider,
  message,
  theme,
} from 'antd';
import { PlusOutlined, DeleteOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { apiClient } from '@shared/api/client.js';
import { surveyService } from './surveyService.js';

const { Text } = Typography;

export function SurveyBuilderDrawer({ open, onClose, onSuccess }) {
  const [form] = Form.useForm();
  const { token } = theme.useToken();
  const [submitting, setSubmitting] = useState(false);
  const [targetType, setTargetType] = useState('ALL_EMPLOYEES');
  const [departments, setDepartments] = useState([]);
  const [workLocations, setWorkLocations] = useState([]);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({
        category: 'PULSE_POLL',
        targetType: 'ALL_EMPLOYEES',
        isAnonymous: false,
        questions: [
          {
            questionText: '',
            questionType: 'RATING_1_5',
            isRequired: true,
          },
        ],
      });
      setTargetType('ALL_EMPLOYEES');

      // Fetch org masters for targeting
      apiClient.get('/v1/departments', { params: { activeOnly: true } })
        .then((res) => setDepartments(Array.isArray(res?.data) ? res.data : []))
        .catch(() => setDepartments([]));

      apiClient.get('/v1/work-locations', { params: { activeOnly: true } })
        .then((res) => setWorkLocations(Array.isArray(res?.data) ? res.data : []))
        .catch(() => setWorkLocations([]));
    }
  }, [open, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const payload = {
        title: values.title.trim(),
        description: values.description ? values.description.trim() : null,
        category: values.category,
        isAnonymous: Boolean(values.isAnonymous),
        targetType: values.targetType,
        targetId: values.targetId || null,
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        endDate: values.dateRange[1].format('YYYY-MM-DD'),
        questions: (values.questions || []).map((q, idx) => ({
          questionText: q.questionText.trim(),
          questionType: q.questionType,
          options: (q.options || []).map((o) => (typeof o === 'string' ? o.trim() : o?.text?.trim() || '')).filter(Boolean),
          isRequired: Boolean(q.isRequired),
          sortOrder: idx + 1,
        })),
      };

      await surveyService.create(payload);
      message.success('Survey created successfully in Draft state');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to create survey');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title="Create Survey / Pulse Poll"
      width={680}
      open={open}
      onClose={onClose}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={submitting} onClick={handleSubmit}>
            Create Survey
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="title"
          label="Survey Title"
          rules={[{ required: true, message: 'Please enter a survey title' }]}
        >
          <Input placeholder="e.g. Q4 Pulse Check & Employee Satisfaction" />
        </Form.Item>

        <Form.Item name="description" label="Instructions / Description">
          <Input.TextArea rows={3} placeholder="Provide context and guidelines for participants" />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: token.marginMD }}>
          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Please select a category' }]}
          >
            <Select
              options={[
                { value: 'PULSE_POLL', label: 'Pulse Poll' },
                { value: 'SATISFACTION', label: 'Job Satisfaction' },
                { value: 'ONBOARDING_FEEDBACK', label: 'Onboarding Feedback' },
                { value: 'GENERAL', label: 'General Survey' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="dateRange"
            label="Active Schedule Window"
            rules={[{ required: true, message: 'Please select schedule dates' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: token.marginMD }}>
          <Form.Item
            name="targetType"
            label="Target Audience"
            rules={[{ required: true, message: 'Please select target audience' }]}
          >
            <Select
              onChange={(val) => {
                setTargetType(val);
                form.setFieldValue('targetId', undefined);
              }}
              options={[
                { value: 'ALL_EMPLOYEES', label: 'All Employees' },
                { value: 'DEPARTMENT', label: 'Specific Department' },
                { value: 'WORK_LOCATION', label: 'Specific Work Location' },
              ]}
            />
          </Form.Item>

          {targetType === 'DEPARTMENT' && (
            <Form.Item
              name="targetId"
              label="Select Department"
              rules={[{ required: true, message: 'Please select a department' }]}
            >
              <Select
                placeholder="Choose department"
                options={departments.map((d) => ({ value: d.id, label: d.name }))}
              />
            </Form.Item>
          )}

          {targetType === 'WORK_LOCATION' && (
            <Form.Item
              name="targetId"
              label="Select Work Location"
              rules={[{ required: true, message: 'Please select a location' }]}
            >
              <Select
                placeholder="Choose location"
                options={workLocations.map((l) => ({ value: l.id, label: l.name }))}
              />
            </Form.Item>
          )}
        </div>

        <Card
          size="small"
          style={{
            background: token.colorFillAlter,
            borderColor: token.colorBorderSecondary,
            marginBottom: token.marginLG,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Text strong>Anonymous Responses</Text>
              <br />
              <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                Decouples employee identity, records date only, and withholds results until at least 5 responses exist.
              </Text>
            </div>
            <Form.Item name="isAnonymous" valuePropName="checked" noStyle>
              <Switch />
            </Form.Item>
          </div>
        </Card>

        <Divider orientation="left" style={{ margin: `${token.marginMD}px 0` }}>
          <Space>
            <QuestionCircleOutlined />
            <span>Survey Questions</span>
          </Space>
        </Divider>

        <Form.List
          name="questions"
          rules={[
            {
              validator: async (_, names) => {
                if (!names || names.length < 1) {
                  return Promise.reject(new Error('At least one question is required'));
                }
              },
            },
          ]}
        >
          {(fields, { add, remove }) => (
            <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginMD }}>
              {fields.map(({ key, name, ...restField }, index) => (
                <Card
                  key={key}
                  size="small"
                  title={`Question ${index + 1}`}
                  extra={
                    fields.length > 1 && (
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => remove(name)}
                      />
                    )
                  }
                  style={{ borderColor: token.colorBorderSecondary }}
                >
                  <Form.Item
                    {...restField}
                    name={[name, 'questionText']}
                    label="Question Text"
                    rules={[{ required: true, message: 'Question prompt is required' }]}
                  >
                    <Input placeholder="e.g. How satisfied are you with career growth opportunities?" />
                  </Form.Item>

                  <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: token.marginMD }}>
                    <Form.Item
                      {...restField}
                      name={[name, 'questionType']}
                      label="Response Format"
                      rules={[{ required: true, message: 'Format is required' }]}
                    >
                      <Select
                        options={[
                          { value: 'RATING_1_5', label: '1 - 5 Star Rating Scale' },
                          { value: 'SINGLE_CHOICE', label: 'Single Choice (Radio)' },
                          { value: 'MULTIPLE_CHOICE', label: 'Multiple Choice (Checkboxes)' },
                          { value: 'TEXT', label: 'Open-ended Text Feedback' },
                        ]}
                      />
                    </Form.Item>

                    <Form.Item
                      {...restField}
                      name={[name, 'isRequired']}
                      valuePropName="checked"
                      label="Requirement"
                    >
                      <Checkbox>Mandatory</Checkbox>
                    </Form.Item>
                  </div>

                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, cur) =>
                      prev.questions?.[name]?.questionType !== cur.questions?.[name]?.questionType
                    }
                  >
                    {({ getFieldValue }) => {
                      const currentType = getFieldValue(['questions', name, 'questionType']);
                      if (currentType === 'SINGLE_CHOICE' || currentType === 'MULTIPLE_CHOICE') {
                        return (
                          <div style={{ marginTop: token.marginXS }}>
                            <Text type="secondary" style={{ fontSize: token.fontSizeSM, marginBottom: 8, display: 'block' }}>
                              Answer Choices:
                            </Text>
                            <Form.List name={[name, 'options']}>
                              {(optFields, { add: addOption, remove: removeOption }) => (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginXS }}>
                                  {optFields.map((optField, optIdx) => (
                                    <div key={optField.key} style={{ display: 'flex', gap: token.marginXS, alignItems: 'center' }}>
                                      <Form.Item
                                        {...optField}
                                        noStyle
                                        rules={[{ required: true, message: 'Option choice text is required' }]}
                                      >
                                        <Input placeholder={`Option ${optIdx + 1}`} />
                                      </Form.Item>
                                      <Button
                                        type="text"
                                        danger
                                        icon={<DeleteOutlined />}
                                        onClick={() => removeOption(optField.name)}
                                      />
                                    </div>
                                  ))}
                                  <Button
                                    type="dashed"
                                    size="small"
                                    icon={<PlusOutlined />}
                                    onClick={() => addOption('')}
                                    style={{ marginTop: 4, width: 'fit-content' }}
                                  >
                                    Add Choice
                                  </Button>
                                </div>
                              )}
                            </Form.List>
                          </div>
                        );
                      }
                      return null;
                    }}
                  </Form.Item>
                </Card>
              ))}

              <Button
                type="dashed"
                icon={<PlusOutlined />}
                onClick={() =>
                  add({
                    questionText: '',
                    questionType: 'RATING_1_5',
                    isRequired: true,
                  })
                }
                style={{ width: '100%' }}
              >
                Add Another Question
              </Button>
            </div>
          )}
        </Form.List>
      </Form>
    </Drawer>
  );
}

SurveyBuilderDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};

