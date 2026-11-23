import { useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Input, DatePicker, Select, InputNumber, message } from 'antd';
import { fnfService } from './fnfService.js';

export function InitiateFnfModal({ open, onCancel, onSuccess }) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const payload = {
        employeeId: values.employeeId,
        exitDate: values.exitDate.format('YYYY-MM-DD'),
        resignationDate: values.resignationDate ? values.resignationDate.format('YYYY-MM-DD') : null,
        exitReason: values.exitReason,
        workingDaysInExitMonth: values.workingDaysInExitMonth,
        otherEarnings: values.otherEarnings,
        otherRecoveries: values.otherRecoveries,
        remarks: values.remarks,
      };

      const result = await fnfService.initiateSettlement(payload);
      message.success('F&F settlement initiated successfully');
      form.resetFields();
      if (onSuccess) {
        onSuccess(result);
      }
    } catch (err) {
      if (err.errorFields) return;
      message.error(err.response?.data?.message || err.message || 'Failed to initiate F&F settlement');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Initiate Full & Final (F&F) Settlement"
      open={open}
      onOk={handleOk}
      onCancel={() => {
        form.resetFields();
        onCancel();
      }}
      confirmLoading={loading}
      okText="Calculate & Save Draft"
      width={600}
    >
      <Form form={form} layout="vertical" initialValues={{ exitReason: 'RESIGNATION' }}>
        <Form.Item
          name="employeeId"
          label="Employee ID (UUID)"
          rules={[{ required: true, message: 'Please enter employee UUID' }]}
        >
          <Input placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000" />
        </Form.Item>

        <Form.Item
          name="exitDate"
          label="Actual Exit Date / Last Working Day"
          rules={[{ required: true, message: 'Please select exit date' }]}
        >
          <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item name="resignationDate" label="Resignation Date">
          <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
        </Form.Item>

        <Form.Item name="exitReason" label="Exit Reason">
          <Select
            options={[
              { label: 'Resignation', value: 'RESIGNATION' },
              { label: 'Retirement', value: 'RETIREMENT' },
              { label: 'Termination', value: 'TERMINATION' },
              { label: 'Absconding', value: 'ABSCONDING' },
            ]}
          />
        </Form.Item>

        <Form.Item name="workingDaysInExitMonth" label="Working Days in Exit Month (Optional)">
          <InputNumber min={0} max={31} style={{ width: '100%' }} placeholder="Defaults to exit day of month" />
        </Form.Item>

        <Form.Item name="otherEarnings" label="Additional Ad-hoc Earnings (₹)">
          <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="0.00" />
        </Form.Item>

        <Form.Item name="otherRecoveries" label="Additional Ad-hoc Deductions/Recoveries (₹)">
          <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="0.00" />
        </Form.Item>

        <Form.Item name="remarks" label="Remarks / Settlement Notes">
          <Input.TextArea rows={3} placeholder="Optional notes for payroll approver" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

InitiateFnfModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onCancel: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};
