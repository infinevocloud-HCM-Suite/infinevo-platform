import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Space,
  Select,
  Modal,
  Form,
  Alert,
  Typography,
  Breadcrumb,
  message,
} from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { apiClient } from '@shared/api/client.js';
import { employeeInvitationService } from './employeeInvitationService.js';
import { InvitationTable } from './InvitationTable.jsx';

const { Title, Text } = Typography;

export function EmployeeInvitations() {
  const canCreate = useCan('core.employee.create');

  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [error, setError] = useState(null);

  // Invite modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSubmitting, setModalSubmitting] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Employee search state
  const [employees, setEmployees] = useState([]);
  const [employeesLoading, setEmployeesLoading] = useState(false);
  const [selectedEmployee, setSelectedEmployee] = useState(null);

  // Action loading states
  const [resendingId, setResendingId] = useState(null);
  const [revokingId, setRevokingId] = useState(null);

  const [form] = Form.useForm();

  const fetchInvitations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await employeeInvitationService.list({
        status: statusFilter || undefined,
      });
      setInvitations(data);
    } catch (err) {
      setError(err?.message || 'Failed to load employee invitations');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (canCreate) {
      fetchInvitations();
    }
  }, [canCreate, fetchInvitations]);

  const searchEmployees = useCallback(async (query = '') => {
    setEmployeesLoading(true);
    try {
      const res = await apiClient.get('/v1/employees', {
        params: { q: query || undefined, size: 50 },
      });
      const data = res?.data?.content || (Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []));
      setEmployees(data);
    } catch {
      setEmployees([]);
    } finally {
      setEmployeesLoading(false);
    }
  }, []);

  const handleOpenModal = () => {
    form.resetFields();
    setSelectedEmployee(null);
    setModalError(null);
    setModalOpen(true);
    searchEmployees();
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    form.resetFields();
    setSelectedEmployee(null);
    setModalError(null);
  };

  const handleEmployeeChange = (employeeId) => {
    const found = employees.find((e) => e.id === employeeId);
    setSelectedEmployee(found || null);
  };

  const handleCreate = async (values) => {
    setModalSubmitting(true);
    setModalError(null);
    try {
      await employeeInvitationService.create({
        employeeId: values.employeeId,
      });
      message.success('Employee invitation sent successfully');
      handleCloseModal();
      fetchInvitations();
    } catch (err) {
      setModalError(err?.message || 'Failed to send employee invitation');
    } finally {
      setModalSubmitting(false);
    }
  };

  const handleResend = async (id) => {
    setResendingId(id);
    try {
      await employeeInvitationService.resend(id);
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
      await employeeInvitationService.revoke(id);
      message.success('Invitation revoked successfully');
      fetchInvitations();
    } catch (err) {
      message.error(err?.message || 'Failed to revoke invitation');
    } finally {
      setRevokingId(null);
    }
  };

  if (!canCreate) {
    return <NotEntitled action="core.employee.create" />;
  }

  return (
    <div style={{ padding: 24 }}>
      <Breadcrumb
        items={[
          { title: 'Home' },
          { title: 'Employee Invitations' },
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
          Employee Invitations
        </Title>
        <Button
          type="primary"
          id="btn-invite-employee"
          onClick={handleOpenModal}
        >
          Invite Employee
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
              id="select-employee-status-filter"
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
          emptyText="No employee invitations found"
        />
      </Card>

      <Modal
        title="Invite Employee to Platform"
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

        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          id="form-invite-employee"
        >
          <Form.Item
            name="employeeId"
            label="Select Employee"
            rules={[{ required: true, message: 'Please select an employee' }]}
          >
            <Select
              showSearch
              id="select-invite-employee"
              placeholder="Search employee by name or ID"
              loading={employeesLoading}
              filterOption={false}
              onSearch={searchEmployees}
              onChange={handleEmployeeChange}
              options={employees.map((e) => {
                const name = `${e.firstName || ''} ${e.lastName || ''}`.trim() || e.employeeNumber || e.id;
                return {
                  value: e.id,
                  label: `${name} (${e.workEmail || 'no email'})`,
                };
              })}
            />
          </Form.Item>

          {selectedEmployee && (
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary">Invitation will be sent to: </Text>
              <Text strong id="display-employee-email">
                {selectedEmployee.workEmail || 'No work email on file'}
              </Text>
            </div>
          )}

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={handleCloseModal}>Cancel</Button>
              <Button
                type="primary"
                htmlType="submit"
                id="btn-submit-employee-invite"
                loading={modalSubmitting}
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
