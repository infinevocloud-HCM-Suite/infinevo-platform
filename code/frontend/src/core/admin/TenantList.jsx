import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Tag, Button, Space, Typography, Card, theme } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { LoadError } from '@shared/ui/LoadError.jsx';
import { tenantService } from './tenantService.js';
import { isPlatformTenant, STATUS_COLORS } from './platform.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';

const { Title } = Typography;

/** The Admin cell for a `TenantOverview.admin_invitation` of `{ email, status }`. */
export function adminInvitationLabel(invitation) {
  if (invitation?.status === 'ACCEPTED') return 'accepted';
  if (invitation?.status === 'PENDING') {
    return invitation.email ? `invited ${invitation.email}` : 'invited';
  }
  return '—';
}

/**
 * Every tenant on the platform, for platform staff (W-65.3 §5). Reached from the `core.tenants`
 * menu item, which only `platform-admin` in the Infinevo tenant is shown (W-65.1 §4).
 *
 * The Infinevo row is tagged "platform" and has no actions: it is not a customer, holds no
 * module and cannot be impersonated (W-65.1 §4, W-65.2).
 *
 * The Admin column shows where the tenant's administrator invitation stands (D-42):
 * "invited <email>" while it is open, "accepted" once taken up, "—" when there is none.
 */
export function TenantList() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  // A failed read shows an error with Retry and no rows, never an empty list (D-85).
  const [loadError, setLoadError] = useState(null);
  const alive = useRef(true);

  const load = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    tenantService
      .list()
      .then((res) => {
        if (alive.current) setRows(Array.isArray(res) ? res : []);
      })
      .catch((err) => {
        if (!alive.current) return;
        setRows([]);
        setLoadError(err || new Error('Could not load tenants'));
      })
      .finally(() => {
        if (alive.current) setLoading(false);
      });
  }, []);

  useEffect(() => {
    alive.current = true;
    load();
    return () => {
      alive.current = false;
    };
  }, [load]);

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name, row) => (
        <Space size="small">
          <span>{name}</span>
          {isPlatformTenant(row.tenant_id) && <Tag color="purple">platform</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={STATUS_COLORS[status] || 'default'}>{statusLabel(status)}</Tag>,
    },
    {
      title: 'Modules',
      dataIndex: 'modules',
      key: 'modules',
      render: (modules) =>
        (modules || []).length === 0
          ? <Typography.Text type="secondary">none</Typography.Text>
          : (modules || []).map((m) => <Tag key={m}>{m}</Tag>),
    },
    {
      title: 'Admin',
      dataIndex: 'admin_invitation',
      key: 'admin_invitation',
      render: (invitation) => {
        const label = adminInvitationLabel(invitation);
        return invitation?.status === 'ACCEPTED' || invitation?.status === 'PENDING'
          ? <span>{label}</span>
          : <Typography.Text type="secondary">{label}</Typography.Text>;
      },
    },
    {
      title: 'Created',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (value) => formatDate(value),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) =>
        isPlatformTenant(row.tenant_id) ? null : (
          <Button
            type="link"
            onClick={() => navigate(`/admin/tenants/${row.tenant_id}`)}
            aria-label={`Open ${row.name}`}
          >
            Open
          </Button>
        ),
    },
  ];

  return (
    <Card>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: token.marginMD }}>
        <Title level={3} style={{ margin: 0 }}>
          Tenants
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/tenants/new')}>
          New tenant
        </Button>
      </Space>
      {loadError ? (
        <LoadError title="Could not load tenants" error={loadError} onRetry={load} />
      ) : (
        <Table
          rowKey="tenant_id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
        />
      )}
    </Card>
  );
}
