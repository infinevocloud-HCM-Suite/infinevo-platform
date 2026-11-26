import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
  Modal,
  Form,
  Rate,
  Radio,
  Checkbox,
  Input,
  Typography,
  Tag,
  Divider,
  Spin,
  Alert,
  message,
  theme,
} from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { surveyService } from '../../../hrms/surveys/surveyService.js';

const { Text, Paragraph } = Typography;

export function SurveyModal({ open, surveyId, onClose, onSuccess }) {
  const [form] = Form.useForm();
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(true);
  const [survey, setSurvey] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open && surveyId) {
      setLoading(true);
      surveyService.getForTaking(surveyId)
        .then((data) => {
          setSurvey(data);
          form.resetFields();
        })
        .catch((err) => {
          message.error(err?.response?.data?.message || err?.message || 'Failed to load survey questions');
          onClose();
        })
        .finally(() => setLoading(false));
    }
  }, [open, surveyId, form, onClose]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const answers = (survey?.questions || []).map((q) => {
        const val = values[q.id];
        let ratingValue = null;
        let selectedOptions = null;
        let textValue = null;

        if (q.questionType === 'RATING_1_5') {
          ratingValue = Number(val) || null;
        } else if (q.questionType === 'SINGLE_CHOICE') {
          selectedOptions = val ? [val] : [];
        } else if (q.questionType === 'MULTIPLE_CHOICE') {
          selectedOptions = Array.isArray(val) ? val : [];
        } else if (q.questionType === 'TEXT') {
          textValue = val || null;
        }

        return {
          questionId: q.id,
          ratingValue,
          selectedOptions,
          textValue,
        };
      });

      await surveyService.submit(surveyId, answers);
      message.success('Survey response submitted successfully');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to submit response');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={survey?.title || 'Take Survey'}
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      okText="Submit Response"
      confirmLoading={submitting}
      destroyOnHidden
      width={640}
    >
      {loading ? (
        <div style={{ padding: 32, textAlign: 'center' }}>
          <Spin size="large" />
        </div>
      ) : (
        <div>
          {survey?.isAnonymous && (
            <Alert
              message="Anonymous Survey"
              description="Your responses are recorded without personal identity markers. A secure one-way hash ensures one submission per person."
              type="info"
              showIcon
              icon={<LockOutlined />}
              style={{ marginBottom: token.marginMD }}
            />
          )}

          {survey?.description && (
            <Paragraph type="secondary" style={{ marginBottom: token.marginMD }}>
              {survey.description}
            </Paragraph>
          )}

          <Form form={form} layout="vertical">
            {(survey?.questions || []).map((q, idx) => (
              <div key={q.id} style={{ marginBottom: token.marginLG }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: token.marginXS, marginBottom: 6 }}>
                  <Text strong>{`Q${idx + 1}. ${q.questionText}`}</Text>
                  {q.isRequired && <Tag color="error">Required</Tag>}
                </div>

                <Form.Item
                  name={q.id}
                  rules={[{ required: q.isRequired, message: 'Please provide an answer for this question' }]}
                  style={{ marginBottom: 8 }}
                >
                  {q.questionType === 'RATING_1_5' && (
                    <Rate allowClear={false} style={{ fontSize: 24 }} />
                  )}

                  {q.questionType === 'SINGLE_CHOICE' && (
                    <Radio.Group style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {(q.options || []).map((opt) => (
                        <Radio key={opt} value={opt}>
                          {opt}
                        </Radio>
                      ))}
                    </Radio.Group>
                  )}

                  {q.questionType === 'MULTIPLE_CHOICE' && (
                    <Checkbox.Group style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {(q.options || []).map((opt) => (
                        <Checkbox key={opt} value={opt}>
                          {opt}
                        </Checkbox>
                      ))}
                    </Checkbox.Group>
                  )}

                  {q.questionType === 'TEXT' && (
                    <Input.TextArea rows={3} placeholder="Type your response here..." />
                  )}
                </Form.Item>
                <Divider style={{ margin: `${token.marginSM}px 0` }} />
              </div>
            ))}
          </Form>
        </div>
      )}
    </Modal>
  );
}

SurveyModal.propTypes = {
  open: PropTypes.bool.isRequired,
  surveyId: PropTypes.string,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};

