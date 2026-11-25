import { useEffect } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Form, Input, InputNumber, Select, DatePicker, Button, Space, message } from 'antd';
import { hiringService } from '../hiringService.js';

export function JobOpeningDrawer({ open, onClose, onSuccess, initialValues }) {
  const [form] = Form.useForm();
  const isEdit = Boolean(initialValues?.id);

  useEffect(() => {
    if (open) {
      if (initialValues) {
        form.setFieldsValue({
          ...initialValues,
          targetDate: initialValues.targetDate ? null : null,
        });
      } else {
        form.resetFields();
      }
    }
  }, [open, initialValues, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        targetDate: values.targetDate ? values.targetDate.format('YYYY-MM-DD') : null,
      };

      if (isEdit) {
        await hiringService.openings.update(initialValues.id, payload);
        message.success('Job opening updated successfully');
      } else {
        await hiringService.openings.create(payload);
        message.success('Job opening created successfully');
      }
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Operation failed');
    }
  };

  return (
    <Drawer
      title={isEdit ? 'Edit Job Opening' : 'Create Job Opening'}
      width={560}
      open={open}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" onClick={handleSubmit}>
            {isEdit ? 'Save Changes' : 'Create Opening'}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="openingCode"
          label="Opening Code"
          rules={[{ required: true, message: 'Please enter job opening code' }]}
        >
          <Input placeholder="e.g. JOB-2026-001" disabled={isEdit} />
        </Form.Item>

        <Form.Item
          name="title"
          label="Job Title"
          rules={[{ required: true, message: 'Please enter job title' }]}
        >
          <Input placeholder="e.g. Senior Software Engineer" />
        </Form.Item>

        <Form.Item
          name="employmentType"
          label="Employment Type"
          initialValue="FULL_TIME"
          rules={[{ required: true }]}
        >
          <Select>
            <Select.Option value="FULL_TIME">Full-Time</Select.Option>
            <Select.Option value="PART_TIME">Part-Time</Select.Option>
            <Select.Option value="CONTRACT">Contract</Select.Option>
            <Select.Option value="INTERN">Internship</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="openingsCount"
          label="Target Headcount"
          initialValue={1}
          rules={[{ required: true, message: 'Please specify required headcount' }]}
        >
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>

        <Space orientation="horizontal" style={{ width: '100%' }} size="middle">
          <Form.Item name="minSalary" label="Min CTC (₹)" orientation="vertical">
            <InputNumber min={0} style={{ width: 240 }} placeholder="Min annual salary" />
          </Form.Item>
          <Form.Item name="maxSalary" label="Max CTC (₹)" orientation="vertical">
            <InputNumber min={0} style={{ width: 240 }} placeholder="Max annual salary" />
          </Form.Item>
        </Space>

        <Space orientation="horizontal" style={{ width: '100%' }} size="middle">
          <Form.Item name="minExperienceYears" label="Min Experience (Yrs)">
            <InputNumber min={0} max={40} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="maxExperienceYears" label="Max Experience (Yrs)">
            <InputNumber min={0} max={40} style={{ width: 240 }} />
          </Form.Item>
        </Space>

        <Form.Item name="targetDate" label="Target Closure Date">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="description" label="Job Description">
          <Input.TextArea rows={4} placeholder="Role responsibilities, required skills, and qualification notes" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

JobOpeningDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
  initialValues: PropTypes.object,
};

