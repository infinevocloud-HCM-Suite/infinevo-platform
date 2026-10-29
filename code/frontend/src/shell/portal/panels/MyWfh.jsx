import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Row, Col, Skeleton, Result, Button, Typography, Space, Progress, Tag, Popconfirm, Alert, Modal, Descriptions, theme } from 'antd';
import { HomeOutlined, PlusOutlined, UndoOutlined, StopOutlined, EyeOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { wfhService } from '../../../core/wfh/wfhService.js';
import { formatDate, humanize } from '../../../shared/ui/format.js';
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
  const hasAllocation = balance?.entitledDays !== undefined && balance?.entitledDays !== null && Number(balance?.entitledDays) > 0;
  const entitled = hasAllocation ? days(balance.entitledDays) : 0;
  const consumed = days(balance?.consumedDays || 0);
  const pending = days(balance?.pendingDays || 0);
  const available = hasAllocation ? days(balance.availableDays) : 0;
  const monthlyLimit = hasAllocation ? days(balance?.monthlyLimit || 2) : 0;
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

      {!hasAllocation && (
        <Alert
          type="info"
          showIcon
          message="No WFH Quota Allocated"
          description="You do not have an active WFH quota allocated for this year. Please contact HR to assign your remote work quota."
          style={{ marginTop: token.marginXS }}
          data-testid="no-wfh-quota-alert"
        />
      )}
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
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [viewModalVisible, setViewModalVisible] = useState(false);
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
        return <Tag>{status ? humanize(status) : '—'}</Tag>;
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
          <Button
            size="small"
            type="link"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedRequest(r);
              setViewModalVisible(true);
            }}
            data-testid={`view-wfh-${r.id}`}
          >
            View
          </Button>

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

  const hasAllocation = balance?.entitledDays !== undefined && balance?.entitledDays !== null && Number(balance?.entitledDays) > 0;
  const availableDaysCount = hasAllocation ? Number(balance?.availableDays || 0) : 0;

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
          disabled={!hasAllocation || availableDaysCount <= 0}
          title={!hasAllocation ? 'No WFH quota allocated by HR' : (availableDaysCount <= 0 ? 'No available WFH days remaining' : '')}
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

      <Modal
        title="WFH Request Details"
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setViewModalVisible(false)}>
            Close
          </Button>,
        ]}
        width={560}
      >
        {selectedRequest && (
          <Descriptions column={1} bordered size="small" style={{ marginTop: 12 }}>
            <Descriptions.Item label="Dates">
              {formatDate(selectedRequest.fromDate)}
              {selectedRequest.fromDate !== selectedRequest.toDate && ` – ${formatDate(selectedRequest.toDate)}`}
            </Descriptions.Item>
            <Descriptions.Item label="Duration">
              {selectedRequest.halfDay
                ? `0.5 day (${selectedRequest.halfDayPeriod === 'first' ? '1st Half' : '2nd Half'})`
                : `${showDays(selectedRequest.workingDays)} days`}
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              {getStatusTag(selectedRequest.status)}
            </Descriptions.Item>
            <Descriptions.Item label="Reason">
              {selectedRequest.reason || 'No reason provided'}
            </Descriptions.Item>
            <Descriptions.Item label="Submission Type">
              {selectedRequest.onBehalf ? 'On-Behalf (Manager/HR)' : 'Self-Service'}
            </Descriptions.Item>
            <Descriptions.Item label="Applied On">
              {formatDate(selectedRequest.createdAt)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </Space>
  );
}

MyWfh.propTypes = {
  onNavigate: PropTypes.func,
};
