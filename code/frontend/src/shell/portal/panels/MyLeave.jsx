import { useEffect, useState } from 'react';
import { Card, Table, Tag, Statistic, Row, Col, Skeleton, Result, Button, Typography, Space, theme } from 'antd';
import { CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Text } = Typography;

export function MyLeave() {
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
        setRequests(Array.isArray(reqData.value) ? reqData.value : reqData.value?.items || []);
      }
      if (balData.status === 'fulfilled') {
        setBalances(Array.isArray(balData.value) ? balData.value : balData.value?.items || []);
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

  const columns = [
    {
      title: 'Leave Type',
      dataIndex: 'leaveTypeName',
      key: 'leaveTypeName',
      render: (text, record) => text || record.leaveTypeCode || 'General Leave',
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
      dataIndex: 'daysCount',
      key: 'daysCount',
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
    <Space orientation="vertical" size="large" style={{ width: '100%' }} data-testid="panel-leave">
      {balances && balances.length > 0 && (
        <Card title="Leave Balances" style={{ borderRadius: token.borderRadiusLG }}>
          <Row gutter={[16, 16]}>
            {balances.map((bal, idx) => (
              <Col xs={24} sm={12} md={8} lg={6} key={bal.id || bal.leaveTypeCode || idx}>
                <Card type="inner" style={{ textAlign: 'center' }}>
                  <Statistic
                    title={bal.leaveTypeName || bal.leaveTypeCode || 'Leave'}
                    value={bal.availableDays !== undefined ? bal.availableDays : bal.balance || 0}
                    precision={1}
                    suffix="days available"
                    valueStyle={{ color: token.colorPrimary }}
                  />
                  {bal.usedDays !== undefined && (
                    <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                      Used: {bal.usedDays} days
                    </Text>
                  )}
                </Card>
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
        style={{ borderRadius: token.borderRadiusLG }}
      >
        <Table
          dataSource={requests}
          columns={columns}
          rowKey={(r, index) => r.id || index}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: 'No leave requests submitted yet.' }}
        />
      </Card>
    </Space>
  );
}

export default MyLeave;
