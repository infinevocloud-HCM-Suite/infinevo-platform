import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Table,
  Row,
  Col,
  Statistic,
  Button,
  Typography,
  Space,
  Tag,
  Skeleton,
  Result,
  theme,
} from 'antd';
import {
  ArrowLeftOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { portalService } from '@shell/portal/portalService.js';
import { EMPTY } from '@shared/ui/format.js';

const { Title, Text } = Typography;

function days(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function showDays(value) {
  const n = days(value);
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

export function LeaveBalanceDetail({ onNavigate }) {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const handleBack = () => {
    if (onNavigate) {
      onNavigate('/me/leave');
    } else {
      navigate('/me/leave');
    }
  };

  const handleApply = () => {
    if (onNavigate) {
      onNavigate('/me/leave/apply');
    } else {
      navigate('/me/leave/apply');
    }
  };

  const fetchBalances = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await portalService.getLeaveBalances();
      setBalances(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load leave balances');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBalances();
  }, []);

  // Compute aggregate stats across all active leave allocations
  const activeBalances = balances.filter((b) => !b.expired);
  const totalEntitled = activeBalances.reduce((sum, b) => sum + days(b.entitlementDays), 0);
  const totalCarried = activeBalances.reduce((sum, b) => sum + days(b.carriedForwardDays), 0);
  const totalConsumed = activeBalances.reduce((sum, b) => sum + days(b.consumedDays), 0);
  const totalPending = activeBalances.reduce((sum, b) => sum + days(b.pendingDays), 0);
  const totalAvailable = activeBalances.reduce(
    (sum, b) => sum + (b.availableDays !== undefined ? days(b.availableDays) : Math.max(0, days(b.remainingDays) - days(b.pendingDays))),
    0
  );
  const leaveYear = balances[0]?.leaveYear || dayjs().format('YYYY');

  const columns = [
    {
      title: 'Leave Type',
      dataIndex: 'leaveTypeName',
      key: 'leaveTypeName',
      render: (name, record) => (
        <Space direction="vertical" size={2}>
          <Space>
            <Text strong>{name || record.leaveTypeCode || 'Leave'}</Text>
            {record.leaveTypeCode && <Tag>{record.leaveTypeCode}</Tag>}
            {record.expired && <Tag color="error">Expired</Tag>}
          </Space>
          {record.leaveTypeValidTo && (
            <Text type="secondary" style={{ fontSize: token.fontSizeXS }}>
              Valid till: {dayjs(record.leaveTypeValidTo).format('DD MMM YYYY')}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Annual Entitlement',
      dataIndex: 'entitlementDays',
      key: 'entitlementDays',
      align: 'right',
      render: (val) => `${showDays(val)} days`,
    },
    {
      title: 'Carried Over',
      dataIndex: 'carriedForwardDays',
      key: 'carriedForwardDays',
      align: 'right',
      render: (val, record) => {
        const d = days(val);
        if (d <= 0) return EMPTY;
        return (
          <Space direction="vertical" size={0} align="end">
            <Text>{showDays(d)} days</Text>
            {record.carryForwardExpiresOn && (
              <Tag color="orange" style={{ margin: 0, fontSize: token.fontSizeXS }}>
                Exp: {dayjs(record.carryForwardExpiresOn).format('DD MMM YYYY')}
              </Tag>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Accrued',
      dataIndex: 'accruedDays',
      key: 'accruedDays',
      align: 'right',
      render: (val) => (days(val) > 0 ? `${showDays(val)} days` : EMPTY),
    },
    {
      title: 'Used / Consumed',
      dataIndex: 'consumedDays',
      key: 'consumedDays',
      align: 'right',
      render: (val) => `${showDays(val)} days`,
    },
    {
      title: 'Pending Approval',
      dataIndex: 'pendingDays',
      key: 'pendingDays',
      align: 'right',
      render: (val) => {
        const d = days(val);
        if (d <= 0) return '0 days';
        return (
          <Tag color="warning" icon={<ClockCircleOutlined />}>
            {showDays(d)} days
          </Tag>
        );
      },
    },
    {
      title: 'Available Balance',
      dataIndex: 'availableDays',
      key: 'availableDays',
      align: 'right',
      render: (val, record) => {
        const avail = val !== undefined ? days(val) : Math.max(0, days(record.remainingDays) - days(record.pendingDays));
        return (
          <Text strong style={{ color: record.expired ? token.colorTextDisabled : token.colorPrimary, fontSize: token.fontSizeLG }}>
            {showDays(avail)} days
          </Text>
        );
      },
    },
  ];

  if (loading) {
    return (
      <Card style={{ margin: token.marginLG }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  if (error) {
    return (
      <Card style={{ margin: token.marginLG }}>
        <Result
          status="error"
          title="Could not load leave balances"
          subTitle={error}
          extra={[
            <Button key="back" icon={<ArrowLeftOutlined />} onClick={handleBack}>
              Back to My Leave
            </Button>,
            <Button key="retry" type="primary" icon={<ReloadOutlined />} onClick={fetchBalances}>
              Try Again
            </Button>,
          ]}
        />
      </Card>
    );
  }

  return (
    <div style={{ padding: token.paddingLG, maxWidth: 1400, margin: '0 auto' }} data-testid="leave-balance-detail-page">
      {/* Page Header */}
      <div style={{ marginBottom: token.marginMD, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: token.marginSM }}>
        <Space size="middle">
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack} id="btn-back-to-leave">
            Back to Leave
          </Button>
          <div>
            <Title level={3} style={{ margin: 0 }}>
              <CalendarOutlined style={{ marginRight: 8, color: token.colorPrimary }} />
              My Leave Balances
            </Title>
            <Text type="secondary">Leave Allocation Period: {leaveYear}</Text>
          </div>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchBalances} id="btn-refresh-balances">
            Refresh
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleApply} id="btn-apply-from-balance">
            Apply for Leave
          </Button>
        </Space>
      </div>

      {/* Summary KPI Cards */}
      <Row gutter={[16, 16]} style={{ marginBottom: token.marginLG }}>
        <Col xs={24} sm={12} md={6}>
          <Card variant="borderless" style={{ background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}>
            <Statistic
              title="Total Annual Entitlement"
              value={showDays(totalEntitled)}
              suffix="days"
              valueStyle={{ color: token.colorText }}
            />
            {totalCarried > 0 && (
              <Text type="secondary" style={{ fontSize: token.fontSizeXS }}>
                Includes {showDays(totalCarried)} days carried over
              </Text>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card variant="borderless" style={{ background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}>
            <Statistic
              title="Total Consumed"
              value={showDays(totalConsumed)}
              suffix="days"
              valueStyle={{ color: token.colorTextSecondary }}
            />
            <Text type="secondary" style={{ fontSize: token.fontSizeXS }}>Approved and taken</Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card variant="borderless" style={{ background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}>
            <Statistic
              title="Pending Approval"
              value={showDays(totalPending)}
              suffix="days"
              valueStyle={{ color: totalPending > 0 ? token.colorWarning : token.colorTextSecondary }}
            />
            <Text type="secondary" style={{ fontSize: token.fontSizeXS }}>
              {totalPending > 0 ? 'Awaiting manager approval' : 'No in-flight requests'}
            </Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card
            variant="borderless"
            style={{
              background: token.colorBgContainer,
              borderLeft: `4px solid ${token.colorPrimary}`,
              borderRadius: token.borderRadiusLG,
            }}
          >
            <Statistic
              title="Net Available Balance"
              value={showDays(totalAvailable)}
              suffix="days"
              prefix={<CheckCircleOutlined style={{ color: token.colorPrimary }} />}
              valueStyle={{ color: token.colorPrimary, fontWeight: 700 }}
            />
            <Text type="secondary" style={{ fontSize: token.fontSizeXS }}>Ready for new applications</Text>
          </Card>
        </Col>
      </Row>

      {/* Detailed Balance Table */}
      <Card
        variant="borderless"
        title="Leave Entitlement & Consumption Breakdown"
        style={{ borderRadius: token.borderRadiusLG }}
      >
        <Table
          dataSource={balances}
          columns={columns}
          rowKey={(r) => r.leaveTypeId || r.leaveTypeCode}
          pagination={false}
          scroll={{ x: 800 }}
        />
      </Card>
    </div>
  );
}

LeaveBalanceDetail.propTypes = {
  onNavigate: PropTypes.func,
};

export default LeaveBalanceDetail;

