import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  Tag,
  Statistic,
  Row,
  Col,
  Skeleton,
  Result,
  Button,
  Typography,
  Space,
  Progress,
  theme,
} from 'antd';
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Text } = Typography;

export function MyLeave({ actions, onNavigate }) {
  const [requests, setRequests] = useState([]);
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchLeaveData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [reqData, balData] = await Promise.allSettled([
        portalService.getLeaveRequests(),
        portalService.getLeaveBalances(),
      ]);

      if (reqData.status === 'fulfilled') {
        // GET /me/leave-requests answers with a Spring page: the rows are under `content`.
        setRequests(Array.isArray(reqData.value) ? reqData.value : reqData.value?.content || []);
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
      render: (leaveTypeId) => leaveTypeNames.get(leaveTypeId) || '—',
    },
    {
      title: 'From Date',
      dataIndex: 'fromDate',
      key: 'fromDate',
    },
    {
      title: 'To Date',
      dataIndex: 'toDate',
      key: 'toDate',
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (days) => (days !== undefined && days !== null ? `${days} day(s)` : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        let color = 'default';
        let icon = null;
        if (status === 'APPROVED') {
          color = 'success';
          icon = <CheckCircleOutlined />;
        } else if (status === 'PENDING' || status === 'SUBMITTED') {
          color = 'processing';
          icon = <ClockCircleOutlined />;
        } else if (status === 'REJECTED') {
          color = 'error';
          icon = <CloseCircleOutlined />;
        }
        return (
          <Tag color={color} icon={icon}>
            {status || 'PENDING'}
          </Tag>
        );
      },
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      render: (reason) => reason || '—',
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }} data-testid="panel-leave">
      {balances && balances.length > 0 && (
        <Card
          data-testid="panel-leave-balances"
          title={
            <Space size="small" style={{ fontSize: 16, fontWeight: 600 }}>
              <CalendarOutlined style={{ color: token.colorPrimary }} />
              <span>Leave Balances</span>
            </Space>
          }
          style={{
            borderRadius: token.borderRadiusLG,
            boxShadow: token.boxShadowTertiary,
            border: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Row gutter={[16, 16]}>
            {balances.map((bal, idx) => {
              const remaining = Number(bal.remainingDays ?? 0);
              const consumed = Number(bal.consumedDays ?? 0);
              const total = bal.entitlementDays ? Number(bal.entitlementDays) : remaining + consumed;
              const percent = total > 0 ? Math.min(100, Math.max(0, Math.round((remaining / total) * 100))) : 0;

              return (
                <Col xs={24} sm={12} md={8} lg={6} key={bal.leaveTypeId || bal.leaveTypeCode || idx}>
                  <Card
                    type="inner"
                    style={{
                      height: '100%',
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      borderRadius: token.borderRadiusMD,
                      border: `1px solid ${token.colorBorderSecondary}`,
                      background: `linear-gradient(180deg, ${token.colorFillAlter} 0%, ${token.colorBgContainer} 100%)`,
                      boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
                      transition: 'all 0.2s ease',
                    }}
                    styles={{
                      body: {
                        display: 'flex',
                        flexDirection: 'column',
                        height: '100%',
                        padding: token.paddingMD,
                        justifyContent: 'space-between',
                      },
                    }}
                  >
                    <div>
                      <div style={{ marginBottom: token.marginXS }}>
                        <Text
                          strong
                          style={{
                            fontSize: 15,
                            color: token.colorTextHeading,
                            display: 'block',
                            lineHeight: 1.3,
                            minHeight: 20,
                          }}
                        >
                          {bal.leaveTypeName || bal.leaveTypeCode || 'Leave'}
                        </Text>
                      </div>

                      <Statistic
                        value={remaining}
                        precision={Number.isInteger(remaining) ? 0 : 1}
                        suffix={<span style={{ fontSize: 13, color: token.colorTextSecondary, fontWeight: 400 }}>days available</span>}
                        valueStyle={{ color: token.colorPrimary, fontWeight: 700, fontSize: 26 }}
                      />

                      <div style={{ margin: `${token.marginSM}px 0` }}>
                        <Progress
                          percent={percent}
                          showInfo={false}
                          strokeColor={token.colorPrimary}
                          trailColor={token.colorFillAlter}
                          size={['100%', 6]}
                        />
                      </div>
                    </div>

                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginTop: token.marginXS,
                        paddingTop: token.marginXS,
                        borderTop: `1px dashed ${token.colorBorderSecondary}`,
                      }}
                    >
                      {bal.consumedDays !== undefined && bal.consumedDays !== null ? (
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM, fontWeight: 500 }}>
                          Used: {bal.consumedDays} days
                        </Text>
                      ) : (
                        <span />
                      )}
                      {bal.entitlementDays ? (
                        <Tag style={{ margin: 0, borderRadius: 10, fontSize: 11 }}>
                          Total: {bal.entitlementDays}d
                        </Tag>
                      ) : null}
                    </div>
                  </Card>
                </Col>
              );
            })}
          </Row>
        </Card>
      )}

      <Card
        title={
          <Space size="small" style={{ fontSize: 16, fontWeight: 600 }}>
            <ClockCircleOutlined style={{ color: token.colorPrimary }} />
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
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          border: `1px solid ${token.colorBorderSecondary}`,
        }}
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
            rowKey={(r) => r.id || `${r.fromDate}-${r.leaveTypeId}`}
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: 'No leave requests submitted yet.' }}
          />
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
