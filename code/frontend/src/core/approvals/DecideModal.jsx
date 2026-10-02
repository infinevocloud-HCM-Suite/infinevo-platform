import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Input, InputNumber, Typography } from 'antd';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { approvalService } from './approvalService.js';

const { Text } = Typography;
const { TextArea } = Input;

export function DecideModal({ open, decision, step, onCancel, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  const flowType = step?.flowType || '';
  const isMoneyFlow = flowType === 'REIMBURSEMENT' || flowType === 'PROOF_OF_INVESTMENT';

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const payload = {
        decision,
        comment: values.comment || '',
      };

      if (isMoneyFlow && values.approvedAmount !== undefined && values.approvedAmount !== null && String(values.approvedAmount).trim() !== '') {
        const raw = String(values.approvedAmount).trim();
        const parts = raw.split('.');
        const intPart = parts[0] || '0';
        const fracPart = (parts[1] || '').padEnd(2, '0').slice(0, 2);
        payload.approvedAmount = `${intPart}.${fracPart}`;
      }

      await approvalService.decide(step.id, payload);
      onSuccess();
    } catch (err) {
      if (err?.errorFields) return; // Form validation error
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  const isReject = decision === 'REJECTED';
  const title = isReject ? 'Reject Approval Request' : 'Approve Request';
  const okText = isReject ? 'Reject' : 'Approve';
  const okButtonProps = isReject
    ? { danger: true, id: 'btn-confirm-decide' }
    : { type: 'primary', id: 'btn-confirm-decide' };

  return (
    <Modal
      title={title}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={submitting}
      okText={okText}
      okButtonProps={okButtonProps}
    >
      <div style={{ marginBottom: 16, marginTop: 8 }}>
        <Text type="secondary">
          {step ? `Step ${step.stepIndex + 1} • ${step.itemRef || flowType}` : ''}
        </Text>
      </div>

      <Form form={form} layout="vertical" preserve={false}>
        {isMoneyFlow && (
          <Form.Item
            name="approvedAmount"
            label="Approved Amount (INR)"
            rules={[
              {
                required: !isReject,
                message: 'Please enter the approved amount.',
              },
            ]}
          >
            <InputNumber
              id="input-approved-amount"
              style={{ width: '100%' }}
              precision={2}
              min={0}
              placeholder="0.00"
              stringMode
            />
          </Form.Item>
        )}

        <Form.Item
          name="comment"
          label="Comment"
          rules={[
            {
              required: isReject,
              message: 'Comment is required when rejecting a request.',
            },
            {
              max: 1000,
              message: 'Comment cannot exceed 1000 characters.',
            },
          ]}
        >
          <TextArea
            id="input-decide-comment"
            rows={4}
            maxLength={1000}
            showCount
            placeholder={isReject ? 'Please state the reason for rejection...' : 'Optional comment...'}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

DecideModal.propTypes = {
  open: PropTypes.bool.isRequired,
  decision: PropTypes.oneOf(['APPROVED', 'REJECTED']).isRequired,
  step: PropTypes.shape({
    id: PropTypes.string,
    instanceId: PropTypes.string,
    stepIndex: PropTypes.number,
    itemRef: PropTypes.string,
    flowType: PropTypes.string,
    assigneeEmployeeId: PropTypes.string,
  }),
  onCancel: PropTypes.func.isRequired,
  onSuccess: PropTypes.func.isRequired,
};
