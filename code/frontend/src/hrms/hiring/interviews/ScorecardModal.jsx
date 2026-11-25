import PropTypes from 'prop-types';
import { Modal, Form, Rate, Radio, Input, message } from 'antd';
import { hiringService } from '../hiringService.js';

export function ScorecardModal({ open, interviewId, onClose, onSuccess }) {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await hiringService.interviews.submitFeedback(interviewId, values);
      message.success('Evaluation scorecard submitted successfully');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Submission failed');
    }
  };

  return (
    <Modal
      title="Submit Interview Scorecard"
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      okText="Submit Scorecard"
      destroyOnHidden
    >
      <Form form={form} layout="vertical" initialValues={{ rating: 5 }} style={{ marginTop: 16 }}>
        <Form.Item
          name="rating"
          label="Overall Candidate Rating"
          rules={[{ required: true, message: 'Please select a rating' }]}
        >
          <Rate allowClear={false} />
        </Form.Item>

        <Form.Item
          name="recommendation"
          label="Hiring Recommendation"
          rules={[{ required: true, message: 'Please select a recommendation' }]}
        >
          <Radio.Group>
            <Radio value="STRONG_YES">Strong Yes</Radio>
            <Radio value="YES">Yes</Radio>
            <Radio value="NEUTRAL">Neutral</Radio>
            <Radio value="NO">No</Radio>
          </Radio.Group>
        </Form.Item>

        <Form.Item name="strengths" label="Key Strengths">
          <Input.TextArea rows={3} placeholder="Technical proficiencies, problem-solving abilities, communication" />
        </Form.Item>

        <Form.Item name="weaknesses" label="Areas of Improvement">
          <Input.TextArea rows={3} placeholder="Knowledge gaps or potential development areas" />
        </Form.Item>

        <Form.Item name="comments" label="Private Evaluation Notes">
          <Input.TextArea rows={3} placeholder="Additional hiring committee context" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

ScorecardModal.propTypes = {
  open: PropTypes.bool.isRequired,
  interviewId: PropTypes.string,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};

