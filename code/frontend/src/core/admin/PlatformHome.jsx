import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Empty,
  Result,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  theme,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { tenantService } from './tenantService.js';
import { STATUS_COLORS } from './platform.js';

const { Title, Text } = Typography;

const INVITATION_TAGS = {
  PENDING: { color: 'blue', label: 'Pending' },
  EXPIRED: { color: 'red', label: 'Expired' },
};

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : '';
}

/**
 * `/admin`, the platform staff's home page (W-73.2 §5), built from `GET /v1/tenants/summary` alone:
 * four tiles (total, active, suspended, waiting for admin), the tenants whose administrator has not
 * taken up their invitation with a Resend for each, the ten newest tenants, and Create tenant.
 * The platform tenant itself is not in any of it - the server leaves it out.
 */
export function PlatformHome() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [resending, setResending] = useState(null);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const next = await tenantService.getSummary();
      if (id === requestId.current) setData(next);
    } catch (err) {
      if (id === requestId.current) {
        setData(null);
        setError(err?.message || 'Could not load the dashboard');
      }
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const resend = async (row) => {
    setResending(row.id);
    try {
      await tenantService.resendAdminInvitation(row.id);
      successMsg('Invitation sent', `A new invitation was sent to ${row.adminEmail}.`);
      await load();
    } catch (err) {
      errorMsg(err);
    } finally {
      setResending(null);
    }
  };

  const createButton = (
    <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/tenants/new')}>
      Create tenant
    </Button>
  );

  const header = (
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: token.marginMD }}>
      <Title level={3} style={{ margin: 0 }}>
        Dashboard
      </Title>
      {createButton}
    </Space>
  );

  if (error) {
    return (
      <Card>
        {header}
        <Result
          status="error"
          title="Could not load the dashboard"
          subTitle={error}
          extra={
            <Button type="primary" onClick={() => load()}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

  if (loading && !data) {
    return (
      <Card>
        {header}
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin aria-label="Loading" />
        </div>
      </Card>
    );
  }

  const byStatus = data?.byStatus || {};
  const waiting = data?.waitingForAdmin || [];
  const recent = data?.recent || [];
  const total = data?.total ?? 0;

  if (total === 0) {
    return (
      <Card>
        {header}
        <Empty description="No tenants yet. Create the first one to get started.">{createButton}</Empty>
      </Card>
    );
  }

  const tiles = [
    { key: 'total', title: 'Tenants', value: total },
    { key: 'active', title: 'Active', value: byStatus.ACTIVE ?? 0 },
    { key: 'suspended', title: 'Suspended', value: byStatus.SUSPENDED ?? 0 },
    { key: 'waiting', title: 'Waiting for admin', value: waiting.length },
  ];

  const waitingColumns = [
    {
      title: 'Tenant',
      dataIndex: 'name',
      key: 'name',
      render: (name, row) => <Link to={`/admin/tenants/${row.id}`}>{name}</Link>,
    },
    { title: 'Admin email', dataIndex: 'adminEmail', key: 'adminEmail' },
    {
      title: 'Invitation',
      dataIndex: 'invitationStatus',
      key: 'invitationStatus',
      render: (status) => {
        const tag = INVITATION_TAGS[status] || { color: 'default', label: status };
        return <Tag color={tag.color}>{tag.label}</Tag>;
      },
    },
    {
      title: 'Expires',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      render: formatDate,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Button
          type="link"
          loading={resending === row.id}
          disabled={resending !== null && resending !== row.id}
          onClick={() => resend(row)}
          aria-label={`Resend invitation for ${row.name}`}
        >
          Resend
        </Button>
      ),
    },
  ];

  const recentColumns = [
    {
      title: 'Tenant',
      dataIndex: 'name',
      key: 'name',
      render: (name, row) => <Link to={`/admin/tenants/${row.id}`}>{name}</Link>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) =>
        status ? <Tag color={STATUS_COLORS[status] || 'default'}>{status}</Tag> : <Text type="secondary">—</Text>,
    },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', render: formatDate },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        {header}
        <Row gutter={[token.marginMD, token.marginMD]}>
          {tiles.map((tile) => (
            <Col key={tile.key} xs={24} sm={12} lg={6}>
              <Card size="small" data-testid={`tile-${tile.key}`}>
                <Statistic title={tile.title} value={tile.value} />
              </Card>
            </Col>
          ))}
        </Row>
        {typeof data?.createdLast30Days === 'number' && (
          <Text type="secondary" style={{ display: 'block', marginTop: token.marginSM }}>
            {data.createdLast30Days} created in the last 30 days
          </Text>
        )}
      </Card>
      <Card title="Needs attention">
        <Table
          rowKey="id"
          columns={waitingColumns}
          dataSource={waiting}
          pagination={false}
          locale={{ emptyText: 'No administrator invitation is waiting.' }}
        />
      </Card>
      <Card
        title="Recent tenants"
        extra={<Link to="/admin/tenants">All tenants</Link>}
      >
        <Table rowKey="id" columns={recentColumns} dataSource={recent} pagination={false} />
      </Card>
    </Space>
  );
}
