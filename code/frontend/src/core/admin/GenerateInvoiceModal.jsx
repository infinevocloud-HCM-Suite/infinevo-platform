import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, DatePicker, Select, Input, Alert } from 'antd';
import dayjs from 'dayjs';
import { billingService } from '../billing/billingService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { RangePicker } = DatePicker;

const FREQUENCIES = [
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'ANNUAL', label: 'Annual (15% Plan Discount)' },
];

/**
 * Platform Admin modal to generate a new GST subscription invoice for a tenant (W-94.2).
 */
export function GenerateInvoiceModal({ open, onClose, tenant, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open && tenant) {
      const startOfCurrentMonth = dayjs().startOf('month');
      const endOfCurrentMonth = dayjs().endOf('month');
      form.setFieldsValue({
        billingPeriod: [startOfCurrentMonth, endOfCurrentMonth],
        billingFrequency: 'MONTHLY',
        invoiceDate: dayjs(),
        dueDate: dayjs().add(15, 'day'),
        customerGstin: '',
        customerStateCode: tenant.country_code === 'IN' ? '29' : '',
      });
    } else {
      form.resetFields();
    }
  }, [open, tenant, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const [start, end] = values.billingPeriod;
      const payload = {
        tenantId: tenant.tenant_id,
        billingPeriodStart: start.format('YYYY-MM-DD'),
        billingPeriodEnd: end.format('YYYY-MM-DD'),
        billingFrequency: values.billingFrequency,
        invoiceDate: values.invoiceDate.format('YYYY-MM-DD'),
        dueDate: values.dueDate.format('YYYY-MM-DD'),
        customerGstin: values.customerGstin ? values.customerGstin.trim().toUpperCase() : null,
        customerStateCode: values.customerStateCode ? values.customerStateCode.trim() : null,
      };

      const created = await billingService.generateInvoice(payload);
      successMsg(
        'Invoice generated',
        `Invoice ${created?.invoiceNumber || ''} generated successfully for ${tenant.name}.`
      );
      if (onSuccess) onSuccess(created);
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={tenant ? `Generate Invoice for ${tenant.name}` : 'Generate Invoice'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText="Generate Invoice"
      confirmLoading={submitting}
      destroyOnHidden
    >
      {tenant && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Invoice will be calculated based on ${tenant.name}'s active headcount, subscription plan (${tenant.plan || 'Default'}), and statutory GST.`}
        />
      )}

      <Form form={form} layout="vertical">
        <Form.Item
          label="Billing Period"
          name="billingPeriod"
          rules={[{ required: true, message: 'Please select billing period range' }]}
        >
          <RangePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item
          label="Billing Frequency"
          name="billingFrequency"
          rules={[{ required: true, message: 'Please select billing frequency' }]}
        >
          <Select options={FREQUENCIES} />
        </Form.Item>

        <Form.Item
          label="Invoice Date"
          name="invoiceDate"
          rules={[{ required: true, message: 'Please select invoice date' }]}
        >
          <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item
          label="Due Date"
          name="dueDate"
          rules={[{ required: true, message: 'Please select payment due date' }]}
        >
          <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item
          label="Customer GSTIN (Optional)"
          name="customerGstin"
          rules={[{ max: 15, message: 'GSTIN must be at most 15 characters' }]}
        >
          <Input placeholder="e.g. 29ABCDE1234F1Z5" maxLength={15} />
        </Form.Item>

        <Form.Item
          label="State Code (for GST determination)"
          name="customerStateCode"
          rules={[{ max: 2, message: 'State code must be 2 digits (e.g. 29)' }]}
        >
          <Input placeholder="e.g. 29 (Karnataka), 27 (Maharashtra)" maxLength={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

GenerateInvoiceModal.propTypes = {
  open: PropTypes.bool,
  onClose: PropTypes.func.isRequired,
  tenant: PropTypes.shape({
    tenant_id: PropTypes.string,
    name: PropTypes.string,
    plan: PropTypes.string,
    country_code: PropTypes.string,
  }),
  onSuccess: PropTypes.func,
};
