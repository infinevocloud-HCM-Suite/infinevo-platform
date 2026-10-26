import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Row, Col, Skeleton, Result, Button, Typography, Space, Progress, Tag, Popconfirm, theme } from 'antd';
import { HomeOutlined, PlusOutlined, UndoOutlined, StopOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { wfhService } from '../../../core/wfh/wfhService.js';
import { formatDate } from '../../../shared/ui/format.js';
import { successMsg, errorMsg } from '../../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

function days(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function showDays(value) {
  const n = days(value);
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

function WfhBalanceCard({ balance, token }) {
  const entitled = days(balance?.entitledDays || 12);
  const consumed = days(balance?.consumedDays || 0);
  const pending = days(balance?.pendingDays || 0);
  const available = days(balance?.availableDays || 12);
  const monthlyLimit = days(balance?.monthlyLimit || 2);
  const monthConsumed = days(balance?.monthConsumed || 0);

  const percent = entitled > 0 ? Math.min(100, Math.round((consumed / entitled) * 100)) : 0;

  return (
    <Card
      type="inner"
      data-testid="wfh-balance-card"
      style={{
        width: '100%',
        borderRadius: token.borderRadiusLG,
        boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
      }}
      styles={{ body: { display: 'flex', flexDirection: 'column', gap: token.marginSM } }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <HomeOutlined style={{ fontSize: 20, color: token.colorPrimary }} />
          <Text strong style={{ fontSize: token.fontSizeLG }}>
            Annual WFH Quota ({balance?.wfhYear || new Date().getFullYear()})
          </Text>
        </Space>
        <Tag color="cyan">Zero LOP Guarantee</Tag>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={6}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>Available Days</Text>
          <div>
            <Text style={{ fontSize: token.fontSizeHeading2, fontWeight: 700, color: token.colorPrimary }}>
              {showDays(available)}
            </Text>
            <Text type="secondary"> / {showDays(entitled)}</Text>
          </div>
        </Col>

        <Col xs={12} sm={6}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>Consumed</Text>
          <div>
            <Text style={{ fontSize: token.fontSizeHeading3, fontWeight: 600 }}>
              {showDays(consumed)}
            </Text>
            <Text type="secondary"> days</Text>
          </div>
        </Col>

        <Col xs={12} sm={6}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>Pending Approval</Text>
          <div>
            <Text style={{ fontSize: token.fontSizeHeading3, fontWeight: 600, color: token.colorWarning }}>
              {showDays(pending)}
            </Text>
            <Text type="secondary"> days</Text>
          </div>
        </Col>

        <Col xs={12} sm={6}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>This Month Usage</Text>
          <div>
            <Text style={{ fontSize: token.fontSizeHeading3, fontWeight: 600 }}>
              {showDays(monthConsumed)}
            </Text>
            <Text type="secondary"> / {showDays(monthlyLimit)} limit</Text>
          </div>
        </Col>
      </Row>

      <div style={{ marginTop: token.marginXS }}>
        <Progress
          percent={percent}
          showInfo={false}
          strokeColor={{ from: token.colorPrimary, to: token.colorInfo }}
          size="small"
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            {showDays(consumed)} of {showDays(entitled)} annual days used
          </Text>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            Monthly pacing: max {showDays(monthlyLimit)} days/mo
          </Text>
        </div>
      </div>
    </Card>
  );
}

WfhBalanceCard.propTypes = {
  balance: PropTypes.object,
  token: PropTypes.object.isRequired,
};

export function MyWfh({ onNavigate }) {
  const [requests, setRequests] = useState([]);
  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState({});
  const { token } = theme.useToken();

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [reqData, balData] = await Promise.allSettled([
        portalService.getWfhRequests(),
        portalService.getWfhBalance(),
      ]);

      if (reqData.status === 'fulfilled') {
        const list = Array.isArray(reqData.value) ? reqData.value : reqData.value?.content || [];
        setRequests(list);
      }
      if (balData.status === 'fulfilled') {
        setBalance(balData.value);
      }
    } catch (err) {
      setError(err?.message || 'Failed to load WFH data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleWithdraw = async (id) => {
    setActionLoading((prev) => ({ ...prev, [id]: true }));
    try {
      await wfhService.withdraw(id, 'Employee requested withdrawal');
      await successMsg('WFH Request Withdrawn', 'Your WFH application has been withdrawn.');
      fetchData();
    } catch (err) {
      await errorMsg('Withdrawal Failed', err?.response?.data?.message || err.message);
    } finally {
      setActionLoading((prev) => ({ ...prev, [id]: false }));
    }
  };

  const handleCancel = async (id) => {
    setActionLoading((prev) => ({ ...prev, [id]: true }));
    try {
      await wfhService.cancel(id, 'Employee cancelled approved WFH');
      await successMsg('WFH Request Cancelled', 'Your approved WFH booking has been cancelled and quota restored.');
      fetchData();
    } catch (err) {
      await errorMsg('Cancellation Failed', err?.response?.data?.message || err.message);
    } finally {
      setActionLoading((prev) => ({ ...prev, [id]: false }));
    }
  };

  const getStatusTag = (status) => {
    switch (status) {
      case 'APPROVED':
        return <Tag color="success">Approved</Tag>;
      case 'PENDING':
        return <Tag color="processing">Pending Approval</Tag>;
      case 'REJECTED':
        return <Tag color="error">Rejected</Tag>;
      case 'WITHDRAWN':
        return <Tag color="default">Withdrawn</Tag>;
      case 'CANCELLED':
        return <Tag color="warning">Cancelled</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const isFutureDate = (dateStr) => {
    if (!dateStr) return false;
    const today = new Date().toISOString().split('T')[0];
    return dateStr > today;
  };

  const columns = [
    {
      title: 'Dates',
      key: 'dates',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          <Text strong>
            {formatDate(r.fromDate)}
            {r.fromDate !== r.toDate && ` – ${formatDate(r.toDate)}`}
          </Text>
          {r.halfDay && (
            <Tag color="purple" style={{ fontSize: 11 }}>
              Half Day ({r.halfDayPeriod === 'first' ? '1st Half' : '2nd Half'})
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (v) => `${showDays(v)} d`,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => getStatusTag(s),
    },
    {
      title: 'Applied On',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d) => formatDate(d),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, r) => (
        <Space size="small">
          {r.status === 'PENDING' && (
            <Popconfirm
              title="Withdraw WFH Request"
              description="Are you sure you want to withdraw this application?"
              onConfirm={() => handleWithdraw(r.id)}
              okText="Withdraw"
              cancelText="Keep"
            >
              <Button
                size="small"
                icon={<UndoOutlined />}
                loading={actionLoading[r.id]}
                data-testid={`withdraw-wfh-${r.id}`}
              >
                Withdraw
              </Button>
            </Popconfirm>
          )}

          {r.status === 'APPROVED' && isFutureDate(r.fromDate) && (
            <Popconfirm
              title="Cancel Approved WFH"
              description="Are you sure you want to cancel this booking? Quota will be reversed."
              onConfirm={() => handleCancel(r.id)}
              okText="Cancel Booking"
              cancelText="Keep"
            >
              <Button
                size="small"
                danger
                icon={<StopOutlined />}
                loading={actionLoading[r.id]}
                data-testid={`cancel-wfh-${r.id}`}
              >
                Cancel
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  if (error) {
    return (
      <Result
        status="error"
        title="Unable to load Work From Home"
        subTitle={error}
        extra={<Button onClick={fetchData}>Retry</Button>}
      />
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Work From Home (WFH)</Title>
          <Text type="secondary">Request remote work days and track your quota consumption</Text>
        </div>

        <Button
          type="primary"
          icon={<PlusOutlined />}
          data-testid="apply-wfh-btn"
          onClick={() => {
            if (onNavigate) {
              onNavigate('/me/wfh/apply');
            } else {
              window.location.hash = '/me/wfh/apply';
            }
          }}
        >
          Apply for WFH
        </Button>
      </div>

      <WfhBalanceCard balance={balance} token={token} />

      <Card
        title="WFH History & Requests"
        data-testid="wfh-requests-card"
        style={{ borderRadius: token.borderRadiusLG }}
      >
        <Table
          dataSource={requests}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 10, showSizeChanger: false }}
          locale={{ emptyText: 'No WFH requests submitted yet' }}
          data-testid="wfh-requests-table"
        />
      </Card>
    </Space>
  );
}

MyWfh.propTypes = {
  onNavigate: PropTypes.func,
};
