import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Modal, Form, Select, DatePicker, Input, message } from 'antd';
import dayjs from 'dayjs';
import { assetService } from './assetService.js';

const CONDITION_OPTIONS = [
  { value: 'NEW', label: 'New' },
  { value: 'GOOD', label: 'Good' },
  { value: 'FAIR', label: 'Fair' },
  { value: 'DAMAGED', label: 'Damaged' },
];

export function ReturnModal({ open, onClose, asset, onSuccess }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open && asset) {
      form.setFieldsValue({
        returnedDate: dayjs(),
        condition: asset.itemCondition || 'GOOD',
        notes: '',
      });
    }
  }, [open, asset, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await assetService.returnAsset(asset.id, {
        returnedDate: values.returnedDate ? values.returnedDate.format('YYYY-MM-DD') : undefined,
        condition: values.condition,
        notes: values.notes,
      });
      message.success(`Asset ${asset.assetTag} return recorded`);
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to record asset return');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={`Record Asset Return: ${asset?.assetTag || ''} (${asset?.assetName || ''})`}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={submitting}
      destroyOnHidden
      okText="Accept Return"
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="returnedDate"
          label="Return Date"
          rules={[{ required: true, message: 'Please select return date' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="condition"
          label="Condition Upon Return"
          rules={[{ required: true, message: 'Please assess return condition' }]}
        >
          <Select options={CONDITION_OPTIONS} />
        </Form.Item>

        <Form.Item name="notes" label="Return Notes / Inspection Comments">
          <Input.TextArea rows={3} placeholder="Inspection notes, scratches, missing cables, etc." />
        </Form.Item>
      </Form>
    </Modal>
  );
}

ReturnModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  asset: PropTypes.object,
  onSuccess: PropTypes.func,
};

