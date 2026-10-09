import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Table, Tag, Button, Space, Input, Drawer, Form, Select, Alert, Popconfirm, Typography, message } from 'antd';
import { useCan } from '@shell/screens';
import { userService } from './userService.js';

const { Text } = Typography;

/**
 * The tenant's accounts (W-73.4 §2): roles, linked employee, status. Change roles needs
 * `core.role.assign` (the server's rule on `PUT /users/{id}/roles`), so the button shows only with it;
 * the server's 409 — your own tenant-admin, the last tenant-admin — is shown as it comes.
 */
export function UsersTab() {
  const canAssign = useCan('core.role.assign');

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [q, setQ] = useState('');
  const [busyId, setBusyId] = useState(null);

  const [editing, setEditing] = useState(null);
  const [roles, setRoles] = useState([]);
  const [rolesLoading, setRolesLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);
  const [form] = Form.useForm();

  const load = useCallback(async (query) => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await userService.list({ q: query || undefined }));
    } catch (err) {
      setError(err?.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load('');
  }, [load]);

  const openRoles = async (user) => {
    setEditing(user);
    setFormError(null);
    setRolesLoading(true);
    try {
      setRoles(await userService.roles());
    } catch (err) {
      setFormError(err?.message || 'Failed to load roles');
    } finally {
      setRolesLoading(false);
    }
  };

  const saveRoles = async ({ roleIds }) => {
    setSaving(true);
    setFormError(null);
    try {
      await userService.setRoles(editing.id, roleIds || []);
      message.success('Roles updated. They apply from the user’s next request.');
      setEditing(null);
      load(q);
    } catch (err) {
      setFormError(err?.message || 'Failed to update roles');
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (user) => {
    setBusyId(user.id);
    try {
      if (user.enabled) {
        await userService.disable(user.id);
        message.success(`${user.email} disabled`);
      } else {
        await userService.enable(user.id);
        message.success(`${user.email} enabled`);
      }
      load(q);
    } catch (err) {
      message.error(err?.message || 'Failed to change the account');
    } finally {
      setBusyId(null);
    }
  };

  // Roles the user holds that the picker would not offer (platform-admin) still show as chosen.
  const options = [
    ...roles.map((r) => ({ value: r.id, label: r.name || r.code })),
    ...(editing?.roles || [])
      .filter((held) => !roles.some((r) => r.id === held.id))
      .map((held) => ({ value: held.id, label: held.name || held.code, disabled: true })),
  ];

  const columns = [
    {
      title: 'User',
      key: 'user',
      render: (_, u) => (
        <div>
          <div>{u.displayName}</div>
          {u.displayName !== u.email && <Text type="secondary">{u.email}</Text>}
        </div>
      ),
    },
    {
      title: 'Roles',
      key: 'roles',
      render: (_, u) =>
        u.roles.length ? u.roles.map((r) => <Tag key={r.id}>{r.name || r.code}</Tag>) : <Text type="secondary">None</Text>,
    },
    {
      title: 'Employee',
      key: 'employee',
      render: (_, u) => (u.employeeId ? <Link to={`/employees/${u.employeeId}`}>{u.employeeNumber}</Link> : '—'),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_, u) => (u.enabled ? <Tag color="success">Active</Tag> : <Tag color="default">Disabled</Tag>),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, u) => (
        <Space size="small">
          {canAssign && (
            <Button size="small" id={`btn-change-roles-${u.id}`} onClick={() => openRoles(u)}>
              Change roles
            </Button>
          )}
          {u.enabled ? (
            <Popconfirm
              title="Disable this user?"
              description="They can no longer sign in. Their roles are kept."
              okText="Disable"
              okButtonProps={{ danger: true, id: `btn-confirm-disable-${u.id}` }}
              onConfirm={() => toggle(u)}
            >
              <Button size="small" danger id={`btn-disable-${u.id}`} loading={busyId === u.id}>
                Disable
              </Button>
            </Popconfirm>
          ) : (
            <Button size="small" id={`btn-enable-${u.id}`} loading={busyId === u.id} onClick={() => toggle(u)}>
              Enable
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Input.Search
        id="input-user-search"
        placeholder="Search by name or email"
        allowClear
        value={q}
        onChange={(e) => setQ(e.target.value)}
        onSearch={(value) => load(value)}
        style={{ maxWidth: 320, marginBottom: 16 }}
      />
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ pageSize: 20 }}
        locale={{ emptyText: 'No users found' }}
        scroll={{ x: true }}
      />

      <Drawer
        title={editing ? `Change roles — ${editing.email}` : 'Change roles'}
        open={!!editing}
        onClose={() => setEditing(null)}
        width={420}
        destroyOnClose
      >
        {formError && <Alert type="error" message={formError} showIcon style={{ marginBottom: 16 }} />}
        <Form
          key={editing?.id}
          form={form}
          layout="vertical"
          onFinish={saveRoles}
          id="form-change-roles"
          initialValues={{ roleIds: (editing?.roles || []).map((r) => r.id) }}
        >
          <Form.Item name="roleIds" label="Roles">
            <Select mode="multiple" id="select-user-roles" loading={rolesLoading} options={options} />
          </Form.Item>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="primary" htmlType="submit" id="btn-save-roles" loading={saving}>
              Save
            </Button>
          </Space>
        </Form>
      </Drawer>
    </>
  );
}
