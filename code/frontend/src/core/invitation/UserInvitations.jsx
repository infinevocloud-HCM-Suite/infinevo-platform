import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Space,
  Select,
  Modal,
  Form,
  Input,
  Alert,
  Typography,
  Breadcrumb,
  message,
} from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { apiClient } from '@shared/api/client.js';
import { userInvitationService } from './userInvitationService.js';
import { InvitationTable } from './InvitationTable.jsx';

const { Title } = Typography;

export function UserInvitations() {
  const canManage = useCan('core.user.manage');

  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [error, setError] = useState(null);

  // Invite modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSubmitting, setModalSubmitting] = useState(false);
  const [modalError, setModalError] = useState(null);
  const [roles, setRoles] = useState([]);
  const [rolesLoading, setRolesLoading] = useState(false);
  const [rolesError, setRolesError] = useState(null);

  // Action loading states
  const [resendingId, setResendingId] = useState(null);
  const [revokingId, setRevokingId] = useState(null);

  const [form] = Form.useForm();

  const fetchInvitations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await userInvitationService.list({
        status: statusFilter || undefined,
      });
      setInvitations(data);
    } catch (err) {
      setError(err?.message || 'Failed to load user invitations');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (canManage) {
      fetchInvitations();
    }
  }, [canManage, fetchInvitations]);

  const loadRoles = useCallback(async () => {
    setRolesLoading(true);
    setRolesError(null);
    try {
      const res = await apiClient.get('/v1/roles');
      const data = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
      setRoles(data);
    } catch (err) {
      setRolesError(err?.message || 'Failed to load roles');
    } finally {
      setRolesLoading(false);
    }
  }, []);

  const handleOpenModal = () => {
    form.resetFields();
    setModalError(null);
    setModalOpen(true);
    loadRoles();
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    form.resetFields();
    setModalError(null);
  };

  const handleCreate = async (values) => {
    setModalSubmitting(true);
    setModalError(null);
    try {
      await userInvitationService.create({
        email: values.email?.trim(),
        roleIds: values.roleIds,
      });
      message.success('User invitation sent successfully');
      handleCloseModal();
      fetchInvitations();
    } catch (err) {
      setModalError(err?.message || 'Failed to send invitation');
    } finally {
      setModalSubmitting(false);
    }
  };

  const handleResend = async (id) => {
    setResendingId(id);
    try {
      await userInvitationService.resend(id);
      message.success('Invitation resent successfully');
      fetchInvitations();
    } catch (err) {
      message.error(err?.message || 'Failed to resend invitation');
    } finally {
      setResendingId(null);
    }
  };

  const handleRevoke = async (id) => {
    setRevokingId(id);
    try {
      await userInvitationService.revoke(id);
      message.success('Invitation revoked successfully');
      fetchInvitations();
    } catch (err) {
      message.error(err?.message || 'Failed to revoke invitation');
    } finally {
      setRevokingId(null);
    }
  };

  if (!canManage) {
    return <NotEntitled action="core.user.manage" />;
  }

  return (
    <div style={{ padding: 24 }}>
      <Breadcrumb
        items={[
          { title: 'Home' },
          { title: 'User Invitations' },
        ]}
        style={{ marginBottom: 16 }}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          User Invitations
        </Title>
        <Button
          type="primary"
          id="btn-invite-user"
          onClick={handleOpenModal}
        >
          Invite User
        </Button>
      </div>

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Card>
        <div style={{ marginBottom: 16 }}>
          <Space>
            <span>Filter Status:</span>
            <Select
              id="select-status-filter"
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 160 }}
              options={[
                { value: '', label: 'All Statuses' },
                { value: 'PENDING', label: 'Pending' },
                { value: 'ACCEPTED', label: 'Accepted' },
                { value: 'DECLINED', label: 'Declined' },
                { value: 'REVOKED', label: 'Revoked' },
                { value: 'EXPIRED', label: 'Expired' },
              ]}
            />
          </Space>
        </div>

        <InvitationTable
          data={invitations}
          loading={loading}
          onResend={handleResend}
          onRevoke={handleRevoke}
          resendingId={resendingId}
          revokingId={revokingId}
          emptyText="No user invitations found"
        />
      </Card>

      <Modal
        title="Invite Company User"
        open={modalOpen}
        onCancel={handleCloseModal}
        footer={null}
        destroyOnClose
      >
        {modalError && (
          <Alert
            type="error"
            message={modalError}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}
        {rolesError && (
          <Alert
            type="error"
            message={rolesError}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          id="form-invite-user"
        >
          <Form.Item
            name="email"
            label="Email Address"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Please enter a valid email address' },
            ]}
          >
            <Input id="input-invite-email" placeholder="colleague@example.com" />
          </Form.Item>

          <Form.Item
            name="roleIds"
            label="Roles"
            rules={[{ required: true, message: 'At least one role is required' }]}
          >
            <Select
              mode="multiple"
              id="select-invite-roles"
              placeholder="Select assigned roles"
              loading={rolesLoading}
              options={roles.map((r) => ({
                value: r.id,
                label: r.name || r.code || r.id,
              }))}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={handleCloseModal}>Cancel</Button>
              <Button
                type="primary"
                htmlType="submit"
                id="btn-submit-invite"
                loading={modalSubmitting}
                disabled={Boolean(rolesError)}
              >
                Send Invitation
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
