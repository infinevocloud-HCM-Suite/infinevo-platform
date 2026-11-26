import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Input, InputNumber, DatePicker, Select, Typography, Space } from 'antd';
import dayjs from 'dayjs';
import { billingService } from '../billing/billingService.js';
import { formatMoney } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

const PAYMENT_METHODS = [
  { value: 'NEFT', label: 'NEFT (National Electronic Funds Transfer)' },
  { value: 'RTGS', label: 'RTGS (Real Time Gross Settlement)' },
  { value: 'UPI', label: 'UPI (Unified Payments Interface)' },
  { value: 'CREDIT_CARD', label: 'Credit Card' },
  { value: 'NET_BANKING', label: 'Net Banking' },
  { value: 'CHEQUE', label: 'Cheque' },
];

/**
 * Platform Admin modal to record offline/online payment for a tenant invoice (W-94.2).
 */
export function RecordPaymentModal({ open, onClose, invoice, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const balanceDue = invoice
    ? Math.max(0, Number(invoice.totalAmount || 0) - Number(invoice.amountPaid || 0))
    : 0;

  useEffect(() => {
    if (open && invoice) {
      form.setFieldsValue({
        paymentDate: dayjs(),
        amount: balanceDue,
        paymentMethod: 'NEFT',
        transactionReference: '',
        notes: '',
      });
    } else {
      form.resetFields();
    }
  }, [open, invoice, balanceDue, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const payload = {
        paymentDate: values.paymentDate.format('YYYY-MM-DD'),
        amount: values.amount,
        paymentMethod: values.paymentMethod,
        transactionReference: values.transactionReference.trim(),
        notes: values.notes ? values.notes.trim() : null,
      };

      await billingService.recordPayment(invoice.id, payload);
      successMsg(
        'Payment recorded',
        `Payment of ₹${values.amount} recorded for invoice ${invoice.invoiceNumber}.`
      );
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      if (err?.errorFields) return; // Validation failed in form
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={invoice ? `Record Payment for ${invoice.invoiceNumber}` : 'Record Payment'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText="Record Payment"
      confirmLoading={submitting}
      destroyOnHidden
    >
      {invoice && (
        <div style={{ marginBottom: 16 }}>
          <Space direction="vertical" size={2}>
            <Text type="secondary">Invoice Total: <Text strong>{formatMoney(invoice.totalAmount)}</Text></Text>
            <Text type="secondary">Already Paid: <Text strong>{formatMoney(invoice.amountPaid)}</Text></Text>
            <Text type="secondary">Balance Due: <Text type="danger" strong>{formatMoney(balanceDue)}</Text></Text>
          </Space>
        </div>
      )}

      <Form form={form} layout="vertical">
        <Form.Item
          label="Payment Date"
          name="paymentDate"
          rules={[{ required: true, message: 'Please select payment date' }]}
        >
          <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item
          label="Amount Paid (₹)"
          name="amount"
          rules={[
            { required: true, message: 'Please enter payment amount' },
            {
              validator: (_, value) => {
                if (value === undefined || value === null) return Promise.resolve();
                if (Number(value) <= 0) return Promise.reject(new Error('Amount must be greater than zero'));
                return Promise.resolve();
              },
            },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={0.01}
            precision={2}
            placeholder="Enter payment amount"
          />
        </Form.Item>

        <Form.Item
          label="Payment Mode"
          name="paymentMethod"
          rules={[{ required: true, message: 'Please select payment mode' }]}
        >
          <Select options={PAYMENT_METHODS} placeholder="Select payment method" />
        </Form.Item>

        <Form.Item
          label="UTR / Transaction Reference"
          name="transactionReference"
          rules={[
            { required: true, whitespace: true, message: 'Please enter UTR or transaction reference' },
            { max: 100, message: 'Transaction reference must be at most 100 characters' },
          ]}
        >
          <Input placeholder="e.g. UTR1234567890, UPI-TXN-987" />
        </Form.Item>

        <Form.Item
          label="Notes (Optional)"
          name="notes"
          rules={[{ max: 500, message: 'Notes must be at most 500 characters' }]}
        >
          <Input.TextArea rows={2} placeholder="Add any bank or remittance details" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

RecordPaymentModal.propTypes = {
  open: PropTypes.bool,
  onClose: PropTypes.func.isRequired,
  invoice: PropTypes.shape({
    id: PropTypes.string,
    invoiceNumber: PropTypes.string,
    totalAmount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    amountPaid: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  }),
  onSuccess: PropTypes.func,
};
