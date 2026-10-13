import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Row, Col, Skeleton, Result, Button, Typography, Space, Progress, theme } from 'antd';
import { CalendarOutlined, PlusOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { EMPTY, formatDate } from '../../../shared/ui/format.js';
import { renderStatus } from '../../../shared/ui/StatusTag.jsx';

const { Text } = Typography;

/** Requests per page read from the server; Load more asks for the next page (D-80). */
export const REQUESTS_PAGE_SIZE = 25;

/** The rows and total of a Spring page, or of a bare array. */
function pageOf(value) {
  if (Array.isArray(value)) return { rows: value, total: value.length };
  const rows = value?.content || [];
  return { rows, total: value?.totalElements ?? rows.length };
}

/** A day count from the server's BigDecimal, or 0 when it is missing. Days, not money. */
function days(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** "10" or "2.5": a whole day count without a trailing ".0". */
function showDays(value) {
  const n = days(value);
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

/**
 * One leave type's balance as an equal-height card with a meter of days used out of the year's
 * total (D-53). Total is what was used plus what remains, which is the server's own working:
 * remaining = entitlement + accrued + carried forward - consumed (LeaveBalanceResponse).
 */
function BalanceCard({ balance, token }) {
  const used = days(balance.consumedDays);
  const remaining = days(balance.remainingDays);
  const total = used + remaining;
  const percent = total > 0 ? Math.round((used / total) * 100) : 0;
  return (
    <Card
      type="inner"
      data-testid="leave-balance-card"
      style={{ width: '100%', height: '100%' }}
      styles={{ body: { height: '100%', display: 'flex', flexDirection: 'column', gap: token.marginXS } }}
    >
      <Text strong ellipsis={{ tooltip: true }}>
        {balance.leaveTypeName || balance.leaveTypeCode || 'Leave'}
      </Text>
      <div>
        <Text style={{ fontSize: token.fontSizeHeading3, fontWeight: 600, color: token.colorPrimary }}>
          {showDays(remaining)}
        </Text>
        <Text type="secondary"> days available</Text>
      </div>
      <Progress
        percent={percent}
        showInfo={false}
        size="small"
        aria-label={`${showDays(used)} of ${showDays(total)} days used`}
        style={{ marginTop: 'auto', marginBottom: 0 }}
      />
      <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
        Used: {showDays(used)} of {showDays(total)} days
      </Text>
    </Card>
  );
}

BalanceCard.propTypes = {
  balance: PropTypes.object.isRequired,
  token: PropTypes.object.isRequired,
};

export function MyLeave({ actions, onNavigate }) {
  const [requests, setRequests] = useState([]);
  // D-80: the server pages the requests; the panel used to show the first 25 and nothing past them.
  const [requestTotal, setRequestTotal] = useState(0);
  const [requestPage, setRequestPage] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchLeaveData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [reqData, balData] = await Promise.allSettled([
        portalService.getLeaveRequests({ page: 0, size: REQUESTS_PAGE_SIZE }),
        portalService.getLeaveBalances(),
      ]);

      if (reqData.status === 'fulfilled') {
        // GET /me/leave-requests answers with a Spring page: the rows are under `content`.
        const { rows, total } = pageOf(reqData.value);
        setRequests(rows);
        setRequestTotal(total);
        setRequestPage(0);
      }
      if (balData.status === 'fulfilled') {
        setBalances(Array.isArray(balData.value) ? balData.value : []);
      }

      if (reqData.status === 'rejected' && balData.status === 'rejected') {
        throw reqData.reason || balData.reason;
      }
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Failed to load leave data');
    } finally {
      setLoading(false);
    }
  };

  const loadMoreRequests = async () => {
    setLoadingMore(true);
    try {
      const next = requestPage + 1;
      const { rows, total } = pageOf(
        await portalService.getLeaveRequests({ page: next, size: REQUESTS_PAGE_SIZE }),
      );
      setRequests((prev) => [...prev, ...rows.filter((r) => !prev.some((p) => p.id && p.id === r.id))]);
      setRequestTotal(total);
      setRequestPage(next);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Failed to load leave data');
    } finally {
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    fetchLeaveData();
  }, []);

  if (loading) {
    return (
      <Card data-testid="panel-leave-loading">
        <Skeleton active paragraph={{ rows: 5 }} />
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Result
          status="error"
          title="Unable to Load Leave Data"
          subTitle={error}
          extra={
            <Button type="primary" onClick={fetchLeaveData}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

  // A request carries only the leave type's id; the balances name it.
  const leaveTypeNames = new Map(
    (balances || []).map((bal) => [bal.leaveTypeId, bal.leaveTypeName || bal.leaveTypeCode]),
  );

  const columns = [
    {
      title: 'Leave Type',
      dataIndex: 'leaveTypeId',
      key: 'leaveTypeId',
      render: (leaveTypeId, row) => row.leaveTypeName || leaveTypeNames.get(leaveTypeId) || EMPTY,
    },
    {
      title: 'From Date',
      dataIndex: 'fromDate',
      key: 'fromDate',
      render: formatDate,
    },
    {
      title: 'To Date',
      dataIndex: 'toDate',
      key: 'toDate',
      render: formatDate,
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (count) => (count !== undefined && count !== null ? `${count} day(s)` : EMPTY),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: renderStatus,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      render: (reason) => reason || EMPTY,
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }} data-testid="panel-leave">
      {balances && balances.length > 0 && (
        <Card title="Leave Balances" style={{ borderRadius: token.borderRadiusLG }}>
          <Row gutter={[16, 16]} align="stretch">
            {balances.map((bal, idx) => (
              <Col xs={24} sm={12} md={8} lg={6} key={bal.leaveTypeId || bal.leaveTypeCode || idx} style={{ display: 'flex' }}>
                <BalanceCard balance={bal} token={token} />
              </Col>
            ))}
          </Row>
        </Card>
      )}

      <Card
        title={
          <Space>
            <CalendarOutlined />
            <span>My Leave Requests</span>
          </Space>
        }
        extra={
          actions?.ApplyLeave ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                if (onNavigate) {
                  onNavigate('/me/leave/apply');
                } else if (typeof window !== 'undefined') {
                  window.location.href = '/me/leave/apply';
                }
              }}
              id="btn-apply-leave"
            >
              Apply for Leave
            </Button>
          ) : null
        }
        style={{ borderRadius: token.borderRadiusLG }}
      >
        {actions?.MyRequests ? (
          <actions.MyRequests
            requests={requests.map((r) => ({
              ...r,
              leaveTypeName: r.leaveTypeName || leaveTypeNames.get(r.leaveTypeId),
            }))}
            onRefresh={fetchLeaveData}
          />
        ) : (
          <Table
            dataSource={requests}
            columns={columns}
            rowKey={(r, index) => r.id || index}
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: 'No leave requests submitted yet.' }}
          />
        )}
        {requests.length < requestTotal && (
          <div style={{ textAlign: 'center', marginTop: token.marginSM }}>
            <Button onClick={loadMoreRequests} loading={loadingMore} id="btn-leave-load-more">
              Load more ({requests.length} of {requestTotal})
            </Button>
          </div>
        )}
      </Card>
    </Space>
  );
}

MyLeave.propTypes = {
  actions: PropTypes.shape({
    ApplyLeave: PropTypes.elementType,
    MyRequests: PropTypes.elementType,
  }),
  onNavigate: PropTypes.func,
};

export default MyLeave;
