import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import {
  Modal,
  Form,
  Input,
  DatePicker,
  Select,
  Button,
  Row,
  Col,
  Switch,
  Alert,
  Typography,
  Card,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { fetchActiveComponents } from './salarySlice.js';
import { salaryService } from './salaryService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { TextArea } = Input;
const { Text } = Typography;

const DECIMAL_REGEX = /^\d+(\.\d{1,4})?$/;

export function SalaryVersionForm({
  open,
  onClose,
  onSuccess,
  employeeId,
  mode = 'create',
  initialValues = null,
}) {
  const dispatch = useDispatch();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [errorText, setErrorText] = useState(null);

  const activeComponents = useSelector((state) => state.salary?.components) || {
    earnings: [],
    deductions: [],
    benefits: [],
    reimbursements: [],
  };

  useEffect(() => {
    if (open) {
      dispatch(fetchActiveComponents());
    }
  }, [open, dispatch]);

  useEffect(() => {
    if (open) {
      setErrorText(null);
      if (initialValues) {
        form.setFieldsValue({
          annualCtc: initialValues.annualCtc ? String(initialValues.annualCtc) : '',
          effectiveFrom: initialValues.effectiveFrom ? dayjs(initialValues.effectiveFrom) : dayjs(),
          notes: initialValues.notes || '',
          earnings: initialValues.earnings?.map((e) => ({
            componentId: e.componentId,
            calculationType: e.calculationType || 'FLAT',
            value: e.value != null ? String(e.value) : '',
            percentageOf: e.percentageOf || null,
            enabled: e.enabled ?? true,
          })) || [],
          benefits: initialValues.benefits?.map((b) => ({
            componentId: b.componentId,
            calculationType: b.calculationType || 'FLAT',
            value: b.value != null ? String(b.value) : '',
            percentageOf: b.percentageOf || null,
            enabled: b.enabled ?? true,
          })) || [],
          reimbursements: initialValues.reimbursements?.map((r) => ({
            componentId: r.componentId,
            calculationType: r.calculationType || 'FLAT',
            value: r.value != null ? String(r.value) : '',
            percentageOf: r.percentageOf || null,
            enabled: r.enabled ?? true,
          })) || [],
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          annualCtc: '',
          effectiveFrom: dayjs(),
          notes: '',
          earnings: [],
          benefits: [],
          reimbursements: [],
        });
      }
    }
  }, [open, initialValues, form]);

  const handleSubmit = async (values) => {
    setSubmitting(true);
    setErrorText(null);
    try {
      const payload = {
        annualCtc: values.annualCtc,
        effectiveFrom: values.effectiveFrom ? values.effectiveFrom.format('YYYY-MM-DD') : null,
        notes: values.notes || null,
        earnings: (values.earnings || []).map((item) => ({
          componentId: item.componentId,
          calculationType: item.calculationType,
          value: item.value,
          percentageOf: item.calculationType === 'PERCENTAGE' ? item.percentageOf : null,
          enabled: item.enabled ?? true,
        })),
        benefits: (values.benefits || []).map((item) => ({
          componentId: item.componentId,
          calculationType: item.calculationType,
          value: item.value,
          percentageOf: item.calculationType === 'PERCENTAGE' ? item.percentageOf : null,
          enabled: item.enabled ?? true,
        })),
        reimbursements: (values.reimbursements || []).map((item) => ({
          componentId: item.componentId,
          calculationType: item.calculationType,
          value: item.value,
          percentageOf: item.calculationType === 'PERCENTAGE' ? item.percentageOf : null,
          enabled: item.enabled ?? true,
        })),
      };

      let result;
      if (mode === 'revise') {
        result = await salaryService.revise(employeeId, payload);
      } else if (mode === 'edit') {
        result = await salaryService.update(employeeId, initialValues.id, payload);
      } else {
        result = await salaryService.create(employeeId, payload);
      }

      await successMsg(
        'Success',
        mode === 'revise'
          ? 'Salary revision created'
          : mode === 'edit'
          ? 'Salary version updated'
          : 'Salary structure created'
      );
      if (onSuccess) onSuccess(result);
      onClose();
    } catch (err) {
      const msg = err?.message || 'Failed to save salary structure';
      setErrorText(msg);
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  const renderComponentList = (fieldName, title, availableItems) => (
    <Card
      size="small"
      title={title}
      style={{ marginBottom: 16 }}
      extra={
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => {
            const current = form.getFieldValue(fieldName) || [];
            form.setFieldsValue({
              [fieldName]: [
                ...current,
                {
                  componentId: availableItems[0]?.id || '',
                  calculationType: 'FLAT',
                  value: '',
                  percentageOf: null,
                  enabled: true,
                },
              ],
            });
          }}
        >
          Add {title.slice(0, -1)}
        </Button>
      }
    >
      <Form.List name={fieldName}>
        {(fields, { remove }) => (
          <div>
            {fields.length === 0 && (
              <Text type="secondary" style={{ fontStyle: 'italic' }}>
                No {title.toLowerCase()} allocated.
              </Text>
            )}
            {fields.map(({ key, name, ...restField }) => {
              const rowValues = form.getFieldValue([fieldName, name]) || {};
              const isPercentage = rowValues.calculationType === 'PERCENTAGE';
              return (
                <Row key={key} gutter={8} align="middle" style={{ marginBottom: 12 }}>
                  <Col span={7}>
                    <Form.Item
                      {...restField}
                      name={[name, 'componentId']}
                      rules={[{ required: true, message: 'Select component' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="Component">
                        {availableItems.map((comp) => (
                          <Select.Option key={comp.id} value={comp.id}>
                            {comp.name} ({comp.code})
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={5}>
                    <Form.Item
                      {...restField}
                      name={[name, 'calculationType']}
                      rules={[{ required: true, message: 'Type' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select
                        placeholder="Type"
                        onChange={() => {
                          form.setFieldsValue({ [fieldName]: [...form.getFieldValue(fieldName)] });
                        }}
                      >
                        <Select.Option value="FLAT">Flat</Select.Option>
                        <Select.Option value="PERCENTAGE">Percentage</Select.Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={5}>
                    <Form.Item
                      {...restField}
                      name={[name, 'value']}
                      rules={[
                        { required: true, message: 'Value' },
                        {
                          pattern: DECIMAL_REGEX,
                          message: 'Up to 4 decimals',
                        },
                      ]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="Value / %" />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    {isPercentage ? (
                      <Form.Item
                        {...restField}
                        name={[name, 'percentageOf']}
                        rules={[{ required: true, message: 'Base' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select placeholder="Base">
                          <Select.Option value="BASIC">Basic</Select.Option>
                          <Select.Option value="CTC">CTC</Select.Option>
                        </Select>
                      </Form.Item>
                    ) : (
                      <div />
                    )}
                  </Col>
                  <Col span={2}>
                    <Form.Item
                      {...restField}
                      name={[name, 'enabled']}
                      valuePropName="checked"
                      style={{ marginBottom: 0 }}
                    >
                      <Switch size="small" />
                    </Form.Item>
                  </Col>
                  <Col span={1}>
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => remove(name)}
                    />
                  </Col>
                </Row>
              );
            })}
          </div>
        )}
      </Form.List>
    </Card>
  );

  const titleText =
    mode === 'revise'
      ? 'Revise Salary Structure'
      : mode === 'edit'
      ? 'Edit Salary Structure'
      : 'Create Salary Structure';

  return (
    <Modal
      title={titleText}
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          Cancel
        </Button>,
        <Button
          key="submit"
          id="btn-salary-version-submit"
          type="primary"
          loading={submitting}
          onClick={() => form.submit()}
        >
          Save
        </Button>,
      ]}
      width={760}
      destroyOnHidden
    >
      {errorText && (
        <Alert
          type="error"
          message={errorText}
          showIcon
          closable
          onClose={() => setErrorText(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="annualCtc"
              label="Annual CTC"
              rules={[
                { required: true, message: 'Annual CTC is required' },
                { pattern: DECIMAL_REGEX, message: 'Must be a valid decimal (max 4 places)' },
              ]}
            >
              <Input id="input-annual-ctc" placeholder="e.g. 600000" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="effectiveFrom"
              label="Effective From"
              rules={[{ required: true, message: 'Effective date is required' }]}
            >
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="notes" label="Notes">
          <TextArea rows={2} placeholder="Optional notes for this salary structure version" />
        </Form.Item>

        {renderComponentList('earnings', 'Earnings', activeComponents.earnings)}
        {renderComponentList('benefits', 'Benefits', activeComponents.benefits)}
        {renderComponentList('reimbursements', 'Reimbursements', activeComponents.reimbursements)}
      </Form>
    </Modal>
  );
}

SalaryVersionForm.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
  employeeId: PropTypes.string.isRequired,
  mode: PropTypes.oneOf(['create', 'revise', 'edit']),
  initialValues: PropTypes.object,
};
