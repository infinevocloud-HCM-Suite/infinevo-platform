import { useState, useEffect, useCallback } from 'react';
import { Button, Space, Select, Drawer, Form, Input, Alert, Typography, message } from 'antd';
import { useCan } from '@shell/screens';
import { userInvitationService } from '../invitation/userInvitationService.js';
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
import { InvitationTable } from '../invitation/InvitationTable.jsx';
import { userService } from './userService.js';

const { Text } = Typography;

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pending' },
  { value: '', label: 'All statuses' },
  { value: 'ACCEPTED', label: 'Accepted' },
  { value: 'DECLINED', label: 'Declined' },
  { value: 'REVOKED', label: 'Revoked' },
  { value: 'EXPIRED', label: 'Expired' },
];

const SERVICES = { USER: userInvitationService, EMPLOYEE: employeeInvitationService };

/**
 * Both kinds of invitation in one table (W-73.4 §2): user invitations (`core.user.manage`) and employee
 * invitations (`core.employee.create`), each read only when the caller holds its action, with a Kind
 * column. Resend and Revoke go to the endpoint of the row's kind. "Invite user" is the user invitation
 * form; the role picker shows only with `core.role.assign`, which the server requires to grant roles.
 */
export function InvitationsTab() {
  const canManage = useCan('core.user.manage');
  const canSeeEmployees = useCan('core.employee.create');
  const canAssign = useCan('core.role.assign');

  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [resendingId, setResendingId] = useState(null);
  const [revokingId, setRevokingId] = useState(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);
  const [roles, setRoles] = useState([]);
  const [rolesLoading, setRolesLoading] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const params = { status: statusFilter || undefined };
    const sources = [];
    if (canManage) sources.push(['USER', userInvitationService.list(params)]);
    if (canSeeEmployees) sources.push(['EMPLOYEE', employeeInvitationService.list(params)]);
    const results = await Promise.allSettled(sources.map(([, p]) => p));
    const merged = [];
    const failures = [];
    results.forEach((result, i) => {
      const kind = sources[i][0];
      if (result.status === 'fulfilled') {
        merged.push(...(result.value || []).map((inv) => ({ ...inv, kind })));
      } else {
        failures.push(result.reason?.message || `Failed to load ${kind.toLowerCase()} invitations`);
      }
    });
    merged.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
    setRows(merged);
    setError(failures.length ? failures.join(' · ') : null);
    setLoading(false);
  }, [canManage, canSeeEmployees, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const openDrawer = async () => {
    form.resetFields();
    setFormError(null);
    setDrawerOpen(true);
    if (!canAssign) return;
    setRolesLoading(true);
    try {
      setRoles(await userService.roles());
    } catch (err) {
      setFormError(err?.message || 'Failed to load roles');
    } finally {
      setRolesLoading(false);
    }
  };

  const invite = async (values) => {
    setSubmitting(true);
    setFormError(null);
    try {
      await userInvitationService.create({
        email: values.email?.trim(),
        roleIds: canAssign ? values.roleIds || [] : [],
      });
      message.success('Invitation sent');
      setDrawerOpen(false);
      load();
    } catch (err) {
      setFormError(err?.message || 'Failed to send invitation');
    } finally {
      setSubmitting(false);
    }
  };

  const act = async (id, record, verb) => {
    const setBusy = verb === 'resend' ? setResendingId : setRevokingId;
    setBusy(id);
    try {
      await SERVICES[record?.kind || 'USER'][verb](id);
      message.success(verb === 'resend' ? 'Invitation resent' : 'Invitation revoked');
      load();
    } catch (err) {
      message.error(err?.message || `Failed to ${verb} invitation`);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, gap: 8, flexWrap: 'wrap' }}>
        <Space>
          <span>Status:</span>
          <Select
            id="select-invitation-status"
            value={statusFilter}
            onChange={setStatusFilter}
            style={{ width: 160 }}
            options={STATUS_OPTIONS}
          />
        </Space>
        {canManage && (
          <Button type="primary" id="btn-invite-user" onClick={openDrawer}>
            Invite user
          </Button>
        )}
      </div>

      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}

      <InvitationTable
        data={rows}
        showKind
        loading={loading}
        onResend={(id, record) => act(id, record, 'resend')}
        onRevoke={(id, record) => act(id, record, 'revoke')}
        resendingId={resendingId}
        revokingId={revokingId}
        emptyText="No invitations found"
      />

      <Drawer title="Invite user" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={420} destroyOnClose>
        {formError && <Alert type="error" message={formError} showIcon style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={invite} id="form-invite-user">
          <Form.Item
            name="email"
            label="Email address"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Please enter a valid email address' },
            ]}
          >
            <Input id="input-invite-email" placeholder="colleague@example.com" />
          </Form.Item>
          {canAssign ? (
            <Form.Item
              name="roleIds"
              label="Roles"
              rules={[{ required: true, message: 'At least one role is required' }]}
            >
              <Select
                mode="multiple"
                id="select-invite-roles"
                placeholder="Select roles"
                loading={rolesLoading}
                options={roles.map((r) => ({ value: r.id, label: r.name || r.code }))}
              />
            </Form.Item>
          ) : (
            <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
              The user is invited without roles; someone who can assign roles gives them later.
            </Text>
          )}
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={() => setDrawerOpen(false)}>Cancel</Button>
            <Button type="primary" htmlType="submit" id="btn-submit-invite" loading={submitting}>
              Send invitation
            </Button>
          </Space>
        </Form>
      </Drawer>
    </>
  );
}
