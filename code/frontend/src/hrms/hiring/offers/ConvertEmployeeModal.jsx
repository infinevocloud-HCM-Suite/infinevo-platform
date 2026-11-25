import PropTypes from 'prop-types';
import { Modal, Form, Input, Select, Typography, Space, Divider, message, theme } from 'antd';
import { hiringService } from '../hiringService.js';

const { Text } = Typography;

export function ConvertEmployeeModal({ open, offer, onClose, onSuccess }) {
  const [form] = Form.useForm();
  const { token } = theme.useToken();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await hiringService.offers.convertToEmployee(offer.id, values);
      message.success('Candidate successfully converted into active employee');
      form.resetFields();
      onSuccess?.();
      onClose();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.response?.data?.message || err?.message || 'Employee conversion failed');
    }
  };

  if (!offer) return null;

  return (
    <Modal
      title="Convert Candidate to Active Employee"
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      okText="Confirm & Create Employee"
      destroyOnHidden
    >
      <div style={{ marginTop: 8 }}>
        <Text type="secondary">Review offer details and enter required employee master records:</Text>
        <div style={{ background: token.colorFillAlter, padding: 12, borderRadius: token.borderRadiusSM, marginTop: 8, marginBottom: 16 }}>
          <Space direction="vertical" size={2}>
            <Text strong>Candidate: {offer.candidateName}</Text>
            <Text>Designation: {offer.designationTitle || '—'}</Text>
            <Text>Department: {offer.departmentName || '—'}</Text>
            <Text>Location: {offer.workLocationName || '—'}</Text>
            <Text>Joining Date: {offer.targetJoinDate}</Text>
          </Space>
        </div>

        <Divider style={{ margin: '12px 0' }} />

        <Form form={form} layout="vertical">
          <Form.Item
            name="employeeNumber"
            label="Employee Number / ID"
            rules={[{ required: true, message: 'Please enter employee number' }]}
          >
            <Input placeholder="e.g. EMP-2026-088" />
          </Form.Item>

          <Form.Item
            name="workEmail"
            label="Official Work Email"
            initialValue={offer.candidateEmail}
            rules={[{ type: 'email', message: 'Invalid email format' }]}
          >
            <Input placeholder="work.email@company.com" />
          </Form.Item>

          <Form.Item name="gender" label="Gender" initialValue="UNDISCLOSED">
            <Select>
              <Select.Option value="MALE">Male</Select.Option>
              <Select.Option value="FEMALE">Female</Select.Option>
              <Select.Option value="OTHER">Other</Select.Option>
              <Select.Option value="UNDISCLOSED">Undisclosed</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
}

ConvertEmployeeModal.propTypes = {
  open: PropTypes.bool.isRequired,
  offer: PropTypes.object,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
};

