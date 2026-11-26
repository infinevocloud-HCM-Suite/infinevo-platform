import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Select, DatePicker, Input, message } from 'antd';
import dayjs from 'dayjs';
import { assetService } from './assetService.js';
import { EmployeePicker } from '../projects/EmployeePicker.jsx';

const CONDITION_OPTIONS = [
  { value: 'NEW', label: 'New' },
  { value: 'GOOD', label: 'Good' },
  { value: 'FAIR', label: 'Fair' },
  { value: 'DAMAGED', label: 'Damaged' },
];

export function AssignModal({ open, onClose, asset, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open && asset) {
      form.setFieldsValue({
        assignedDate: dayjs(),
        condition: asset.itemCondition || 'GOOD',
        notes: '',
        employeeId: undefined,
      });
    }
  }, [open, asset, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await assetService.assign(asset.id, {
        employeeId: values.employeeId,
        assignedDate: values.assignedDate ? values.assignedDate.format('YYYY-MM-DD') : undefined,
        condition: values.condition,
        notes: values.notes,
      });
      message.success(`Asset ${asset.assetTag} assigned successfully`);
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to assign asset');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={`Assign Asset: ${asset?.assetTag || ''} (${asset?.assetName || ''})`}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={submitting}
      destroyOnHidden
      okText="Assign Equipment"
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="employeeId"
          label="Assign to Employee"
          rules={[{ required: true, message: 'Please select an employee' }]}
        >
          <EmployeePicker placeholder="Type employee name or ID..." />
        </Form.Item>

        <Form.Item
          name="assignedDate"
          label="Handover Date"
          rules={[{ required: true, message: 'Please select handover date' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="condition"
          label="Condition at Handover"
          rules={[{ required: true, message: 'Please select item condition' }]}
        >
          <Select options={CONDITION_OPTIONS} />
        </Form.Item>

        <Form.Item name="notes" label="Handover Notes / Accessories Included">
          <Input.TextArea rows={3} placeholder="Charger, bag, dongle, serial note..." />
        </Form.Item>
      </Form>
    </Modal>
  );
}

AssignModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  asset: PropTypes.object,
  onSuccess: PropTypes.func,
};

