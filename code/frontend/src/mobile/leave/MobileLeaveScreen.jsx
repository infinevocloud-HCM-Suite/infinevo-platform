import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Tag,
  Typography,
  Space,
  Empty,
  Spin,
  Alert,
  Popconfirm,
  Badge,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  CalendarOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { mobileLeaveService } from './mobileLeaveService.js';
import { LeaveBalanceCard } from './LeaveBalanceCard.jsx';
import { ApplyLeaveDrawer } from './ApplyLeaveDrawer.jsx';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

function renderStatusTag(status) {
  switch (status) {
    case 'APPROVED':
      return (
        <Tag color="success" icon={<CheckCircleOutlined />} style={{ borderRadius: 6 }}>
          Approved
        </Tag>
      );
    case 'PENDING':
      return (
        <Tag color="warning" icon={<ClockCircleOutlined />} style={{ borderRadius: 6 }}>
          Pending
        </Tag>
      );
    case 'REJECTED':
      return (
        <Tag color="error" icon={<CloseCircleOutlined />} style={{ borderRadius: 6 }}>
          Rejected
        </Tag>
      );
    case 'CANCELLED':
      return (
        <Tag icon={<StopOutlined />} style={{ borderRadius: 6 }}>
          Cancelled
        </Tag>
      );
    case 'WITHDRAWN':
      return (
        <Tag icon={<UndoOutlined />} style={{ borderRadius: 6 }}>
          Withdrawn
        </Tag>
      );
    default:
      return <Tag style={{ borderRadius: 6 }}>{status}</Tag>;
  }
}

/**
 * Mobile Leave Screen (W-98.3 §4.2)
 * Clean touch dashboard with swipeable balance cards, chronological history,
 * separate Withdraw (PENDING) and Cancel (future APPROVED) actions,
 * and floating "+ Apply Leave" bottom sheet action.
 */
export function MobileLeaveScreen() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [balances, setBalances] = useState([]);
  const [requests, setRequests] = useState([]);
  const [errorBanner, setErrorBanner] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  const loadData = useCallback(async () => {
    try {
      setErrorBanner(null);
      const [balancesData, requestsData] = await Promise.all([
        mobileLeaveService.getBalances(),
        mobileLeaveService.getRequests({ page: 0, size: 25 }),
      ]);
      setBalances(balancesData);
      setRequests(requestsData.rows || []);
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to load leave records';
      setErrorBanner(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData();
  };

  const handleWithdrawRequest = async (requestId) => {
    setActionLoadingId(requestId);
    try {
      await mobileLeaveService.withdrawLeave(requestId, 'Withdrawn from mobile app');
      await successMsg('Leave Request Withdrawn', 'Your leave request has been withdrawn.');
      await loadData();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleCancelRequest = async (requestId) => {
    setActionLoadingId(requestId);
    try {
      await mobileLeaveService.cancelLeave(requestId, 'Cancelled from mobile app');
      await successMsg('Leave Request Cancelled', 'The leave request has been cancelled.');
      await loadData();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setActionLoadingId(null);
    }
  };

  const isFutureApproved = (req) => {
    if (req.status !== 'APPROVED') return false;
    const start = dayjs(req.fromDate);
    return start.isValid() && start.isAfter(dayjs(), 'day');
  };

  if (loading) {
    return (
      <div
        style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '60vh', gap: 12 }}
        data-testid="mobile-leave-loading"
      >
        <Spin size="large" />
        <Text type="secondary">Loading leave details...</Text>
      </div>
    );
  }

  // Error view when initial load failed
  if (errorBanner && balances.length === 0 && requests.length === 0) {
    return (
      <div className="mobile-leave-container" data-testid="mobile-leave-error" style={{ padding: 16 }}>
        <Alert
          type="error"
          message="Failed to load leave records"
          description={errorBanner}
          showIcon
          action={
            <Button size="small" danger onClick={loadData} data-testid="leave-retry-btn">
              Retry
            </Button>
          }
          style={{ marginTop: 24, borderRadius: 8 }}
        />
      </div>
    );
  }

  return (
    <div className="mobile-leave-container" data-testid="mobile-leave-screen">
      {/* Top Header & Refresh */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            Leave & Balances
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Track entitlement, history & apply
          </Text>
        </div>
        <Button
          type="text"
          shape="circle"
          icon={<ReloadOutlined spin={refreshing} />}
          onClick={handleRefresh}
          aria-label="Refresh leave data"
        />
      </div>

      {errorBanner && (
        <Alert
          type="error"
          message={errorBanner}
          showIcon
          style={{ marginBottom: 16, borderRadius: 8 }}
          data-testid="leave-screen-error"
        />
      )}

      {/* Section 1: Horizontal Swipeable Balance Cards Carousel */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Text strong style={{ fontSize: 14, color: '#4b5563', textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Available Balances
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Swipe for all types &rarr;
          </Text>
        </div>

        {balances.length === 0 ? (
          <Card style={{ borderRadius: 12, textAlign: 'center' }}>
            <Empty description="No leave balance allocations found." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </Card>
        ) : (
          <div
            className="mobile-leave-carousel"
            data-testid="leave-balance-carousel"
            style={{
              display: 'flex',
              gap: 12,
              overflowX: 'auto',
              paddingBottom: 8,
              scrollSnapType: 'x mandatory',
              WebkitOverflowScrolling: 'touch',
            }}
          >
            {balances.map((balance) => (
              <LeaveBalanceCard key={balance.leaveTypeId || balance.leaveTypeCode} balance={balance} />
            ))}
          </div>
        )}
      </div>

      {/* Section 2: Recent Leave Requests History List */}
      <div style={{ marginBottom: 80 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <Text strong style={{ fontSize: 14, color: '#4b5563', textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Recent Applications
          </Text>
          <Badge count={requests.length} overflowCount={99} style={{ backgroundColor: '#1677ff' }} />
        </div>

        {requests.length === 0 ? (
          <Card style={{ borderRadius: 12, textAlign: 'center', padding: '24px 0' }}>
            <Empty
              description="No leave requests yet"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          </Card>
        ) : (
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            {requests.map((req) => {
              const isHalf = !!req.isHalfDay;
              const periodLabel =
                req.halfDayPeriod?.toLowerCase() === 'second' || req.halfDayPeriod === 'SECOND_HALF'
                  ? 'Second Half'
                  : 'First Half';
              const daysNum = Number(req.daysCount ?? req.days ?? (isHalf ? 0.5 : 1));
              const countStr = isHalf
                ? `0.5 Day (${periodLabel})`
                : `${daysNum} ${daysNum === 1 ? 'Day' : 'Days'}`;

              const dateRange =
                req.fromDate === req.toDate || !req.toDate || isHalf
                  ? req.fromDate
                  : `${req.fromDate} - ${req.toDate}`;

              const isPending = req.status === 'PENDING';
              const canCancelApproved = isFutureApproved(req);
              const isActing = actionLoadingId === req.id;

              return (
                <Card
                  key={req.id}
                  className="mobile-leave-request-card"
                  data-testid="leave-request-item"
                  style={{
                    borderRadius: 12,
                    border: '1px solid #f0f0f0',
                    boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
                  }}
                  styles={{ body: { padding: '14px 16px' } }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
                    <div>
                      <Text strong style={{ fontSize: 15 }}>
                        {req.leaveTypeName || req.leaveTypeCode || 'Leave'}
                      </Text>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
                        <CalendarOutlined style={{ color: '#8c8c8c', fontSize: 13 }} />
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          {dateRange} &bull; <strong style={{ color: '#262626' }}>{countStr}</strong>
                        </Text>
                      </div>
                    </div>
                    {renderStatusTag(req.status)}
                  </div>

                  {req.reason && (
                    <div style={{ marginTop: 6, background: '#fafafa', padding: '6px 10px', borderRadius: 6 }}>
                      <Text type="secondary" style={{ fontSize: 12, fontStyle: 'italic' }}>
                        &ldquo;{req.reason}&rdquo;
                      </Text>
                    </div>
                  )}

                  {/* PENDING -> Withdraw button */}
                  {isPending && (
                    <div style={{ marginTop: 10, textAlign: 'right' }}>
                      <Popconfirm
                        title="Withdraw Leave Request"
                        description="Are you sure you want to withdraw this pending leave application?"
                        onConfirm={() => handleWithdrawRequest(req.id)}
                        okText="Yes, Withdraw"
                        cancelText="Keep"
                        okButtonProps={{ danger: true, loading: isActing }}
                      >
                        <Button
                          size="small"
                          danger
                          type="text"
                          icon={<UndoOutlined />}
                          data-testid="btn-withdraw-leave"
                          disabled={isActing}
                        >
                          Withdraw Request
                        </Button>
                      </Popconfirm>
                    </div>
                  )}

                  {/* Future APPROVED -> Cancel button */}
                  {canCancelApproved && (
                    <div style={{ marginTop: 10, textAlign: 'right' }}>
                      <Popconfirm
                        title="Cancel Leave Request"
                        description="Are you sure you want to cancel this approved leave application?"
                        onConfirm={() => handleCancelRequest(req.id)}
                        okText="Yes, Cancel"
                        cancelText="Keep"
                        okButtonProps={{ danger: true, loading: isActing }}
                      >
                        <Button
                          size="small"
                          danger
                          type="text"
                          icon={<CloseCircleOutlined />}
                          data-testid="btn-cancel-leave"
                          disabled={isActing}
                        >
                          Cancel Request
                        </Button>
                      </Popconfirm>
                    </div>
                  )}
                </Card>
              );
            })}
          </Space>
        )}
      </div>

      {/* Floating Action Button (FAB): + Apply Leave */}
      <div className="mobile-fab-container" style={{ position: 'fixed', bottom: 76, right: 16, zIndex: 90 }}>
        <Button
          type="primary"
          shape="round"
          size="large"
          icon={<PlusOutlined />}
          onClick={() => setDrawerOpen(true)}
          style={{
            height: 48,
            paddingLeft: 20,
            paddingRight: 24,
            fontWeight: 600,
            boxShadow: '0 4px 14px rgba(22, 119, 255, 0.4)',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
          data-testid="fab-apply-leave"
        >
          Apply Leave
        </Button>
      </div>

      {/* Apply Leave Bottom Sheet Drawer */}
      <ApplyLeaveDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        balances={balances}
        onSuccess={loadData}
      />
    </div>
  );
}
