import { useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Input, Button, Alert } from 'antd';
import { successMsg } from '@shared/ui/msgHelper.js';
import { setupService } from './setupService.js';

export function SkipStepModal({ open, step, onClose, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const handleCancel = () => {
    form.resetFields();
    setErrorMessage(null);
    onClose();
  };

  const handleFinish = async (values) => {
    try {
      setSubmitting(true);
      setErrorMessage(null);
      const reason = values.reason?.trim();
      await setupService.skip(step.code, reason);
      await successMsg('Step Skipped', `"${step.label}" has been marked as skipped.`);
      form.resetFields();
      onSuccess();
      onClose();
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        'Failed to skip setup step.';
      setErrorMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={`Skip Setup Step: ${step?.label || ''}`}
      open={open}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel} id="btn-cancel-skip-step">
          Cancel
        </Button>,
        <Button
          key="submit"
          type="primary"
          danger
          loading={submitting}
          onClick={() => form.submit()}
          id="btn-confirm-skip-step"
        >
          Confirm Skip
        </Button>,
      ]}
    >
      {errorMessage && (
        <Alert
          type="error"
          message={errorMessage}
          showIcon
          style={{ marginBottom: 16 }}
          id="alert-skip-error"
        />
      )}

      <p style={{ color: 'rgba(0, 0, 0, 0.65)', marginBottom: 16 }}>
        Skipping a step indicates your organization does not require this configuration or
        handles it externally. A reason is required for administrative audit records.
      </p>

      <Form form={form} layout="vertical" onFinish={handleFinish}>
        <Form.Item
          name="reason"
          label="Reason for Skipping"
          rules={[
            { required: true, message: 'Please provide a reason for skipping this step.' },
            {
              validator: (_, value) => {
                if (!value || !value.trim()) {
                  return Promise.reject(new Error('Skip reason must not be blank.'));
                }
                if (value.trim().length > 500) {
                  return Promise.reject(new Error('Reason must not exceed 500 characters.'));
                }
                return Promise.resolve();
              },
            },
          ]}
        >
          <Input.TextArea
            rows={4}
            maxLength={500}
            showCount
            placeholder="e.g. Handled by external vendor or not applicable for current operations"
            id="input-skip-reason"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

SkipStepModal.propTypes = {
  open: PropTypes.bool.isRequired,
  step: PropTypes.shape({
    code: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    module: PropTypes.string,
    displayOrder: PropTypes.number,
  }),
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func.isRequired,
};
