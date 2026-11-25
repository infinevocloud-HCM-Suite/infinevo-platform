import PropTypes from 'prop-types';
import { Drawer, Form, Input, InputNumber, Button, Space, message } from 'antd';
import { hiringService } from '../hiringService.js';

export function CandidateDrawer({ open, onClose, onSuccess }) {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await hiringService.candidates.create(values);
      message.success('Candidate profile created successfully');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Failed to create candidate');
    }
  };

  return (
    <Drawer
      title="Add Candidate Profile"
      width={480}
      open={open}
      onClose={onClose}
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" onClick={handleSubmit}>
            Create Profile
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Space style={{ width: '100%' }} size="middle">
          <Form.Item
            name="firstName"
            label="First Name"
            rules={[{ required: true, message: 'Please enter first name' }]}
            style={{ width: 200 }}
          >
            <Input placeholder="John" />
          </Form.Item>

          <Form.Item
            name="lastName"
            label="Last Name"
            rules={[{ required: true, message: 'Please enter last name' }]}
            style={{ width: 200 }}
          >
            <Input placeholder="Doe" />
          </Form.Item>
        </Space>

        <Form.Item
          name="email"
          label="Email Address"
          rules={[
            { required: true, message: 'Please enter email' },
            { type: 'email', message: 'Invalid email address' },
          ]}
        >
          <Input placeholder="john.doe@example.com" />
        </Form.Item>

        <Form.Item name="phone" label="Phone Number">
          <Input placeholder="+91 98765 43210" />
        </Form.Item>

        <Form.Item name="currentCompany" label="Current Company">
          <Input placeholder="Acme Technologies" />
        </Form.Item>

        <Form.Item name="currentTitle" label="Current Designation / Title">
          <Input placeholder="Software Engineer" />
        </Form.Item>

        <Space style={{ width: '100%' }} size="middle">
          <Form.Item name="experienceYears" label="Total Experience (Yrs)">
            <InputNumber min={0} max={50} step={0.5} style={{ width: 200 }} />
          </Form.Item>

          <Form.Item name="noticePeriodDays" label="Notice Period (Days)">
            <InputNumber min={0} max={180} style={{ width: 200 }} />
          </Form.Item>
        </Space>
      </Form>
    </Drawer>
  );
}

CandidateDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};

