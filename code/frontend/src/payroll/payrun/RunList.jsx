import { useEffect, useState, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Table,
  Button,
  Select,
  Segmented,
  Tag,
  Space,
  Typography,
  Card,
  Modal,
  DatePicker,
  Alert,
  Form,
  Row,
  Col,
} from 'antd';
import { PlusOutlined, ThunderboltOutlined, ReloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { payrunService } from './payrunService.js';

const { Title, Text } = Typography;

const STATUS_OPTIONS = [
  { label: 'All Statuses', value: 'ALL' },
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'LOCKED', value: 'LOCKED' },
  { label: 'COMPUTING', value: 'COMPUTING' },
  { label: 'COMPUTED', value: 'COMPUTED' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'APPROVED', value: 'APPROVED' },
  { label: 'PAID', value: 'PAID' },
  { label: 'CANCELLED', value: 'CANCELLED' },
];

const STATUS_COLOR_MAP = {
  DRAFT: 'default',
  LOCKED: 'processing',
  COMPUTING: 'cyan',
  COMPUTED: 'blue',
  FAILED: 'error',
  APPROVED: 'orange',
  PAID: 'success',
  CANCELLED: 'default',
};

export function RunList() {
  const navigate = useNavigate();
  const canExecute = useCan('payroll.run.execute');

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [loadError, setLoadError] = useState(null);

  // New run modal state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [is409Conflict, setIs409Conflict] = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState(null);

  const fetchRuns = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await payrunService.list({
        status: statusFilter !== 'ALL' ? statusFilter : undefined,
        runType: typeFilter !== 'ALL' ? typeFilter : undefined,
        page: page - 1,
        size: pageSize,
      });
      const content = res?.content || (Array.isArray(res) ? res : []);
      setData(content);
      setTotal(res?.totalElements || res?.total_elements || content.length);
    } catch (err) {
      setData([]);
      setTotal(0);
      setLoadError(err?.message || 'Failed to load pay runs');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, typeFilter, page, pageSize]);

  useEffect(() => {
    fetchRuns();
  }, [fetchRuns]);

  const handleCreateRun = async () => {
    if (!selectedPeriod) return;
    const periodStr = selectedPeriod.format('YYYY-MM');
    setCreateLoading(true);
    setCreateError(null);
    setIs409Conflict(false);

    try {
      const newRun = await payrunService.create({ period: periodStr });
      setCreateModalOpen(false);
      setSelectedPeriod(null);
      if (newRun?.id) {
        navigate(`/payroll/runs/${newRun.id}`);
      } else {
        fetchRuns();
      }
    } catch (err) {
      const isConflict =
        err?.status === 409 ||
        err?.code === 'CONFLICT' ||
        (err?.message && err.message.toLowerCase().includes('already exists')) ||
        (err?.message && err.message.toLowerCase().includes('schedule'));
      setIs409Conflict(isConflict);
      setCreateError(err?.message || 'Failed to create pay run');
    } finally {
      setCreateLoading(false);
    }
  };

  const columns = [
    {
      title: 'Period',
      dataIndex: 'period',
      key: 'period',
      render: (period) => <Text strong>{period}</Text>,
    },
    {
      title: 'Pay Date',
      dataIndex: 'pay_date',
      key: 'pay_date',
      render: (date, record) => <Text>{date || record.payDate || '-'}</Text>,
    },
    {
      title: 'Type',
      dataIndex: 'run_type',
      key: 'run_type',
      render: (type, record) => {
        const val = type || record.runType || 'REGULAR';
        return (
          <Tag color={val === 'OFF_CYCLE' ? 'purple' : 'blue'}>
            {val}
          </Tag>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={STATUS_COLOR_MAP[status] || 'default'}>
          {status}
        </Tag>
      ),
    },
    {
      title: 'Employees (Inc / Skip)',
      key: 'counts',
      render: (_, record) => {
        const inc = record.included_count !== undefined ? record.included_count : record.includedCount ?? '-';
        const skip = record.skipped_count !== undefined ? record.skipped_count : record.skippedCount ?? '-';
        return <Text>{`${inc} / ${skip}`}</Text>;
      },
    },
    {
      title: 'Net Total',
      dataIndex: 'total_net_pay',
      key: 'total_net_pay',
      align: 'right',
      render: (net, record) => {
        const val = net !== undefined ? net : record.totalNetPay;
        const status = record.status;
        const isComputed = status === 'COMPUTED' || status === 'APPROVED' || status === 'PAID';
        if (!isComputed || val === undefined || val === null) {
          return <Text type="secondary">-</Text>;
        }
        return (
          <Text strong style={{ fontFamily: 'monospace' }}>
            {Number(val).toFixed(2)}
          </Text>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '24px', maxWidth: 1200, margin: '0 auto' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
          flexWrap: 'wrap',
          gap: 16,
        }}
      >
        <Title level={2} style={{ margin: 0 }}>
          Pay Runs
        </Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchRuns} loading={loading}>
            Refresh
          </Button>
          {canExecute && (
            <>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setCreateError(null);
                  setIs409Conflict(false);
                  setCreateModalOpen(true);
                }}
              >
                New Run
              </Button>
              <Button
                icon={<ThunderboltOutlined />}
                onClick={() => navigate('/payroll/runs/new-off-cycle')}
              >
                New Off-Cycle Run
              </Button>
            </>
          )}
        </Space>
      </div>

      <Card style={{ marginBottom: 20 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={12} md={8}>
            <Space align="center" style={{ width: '100%' }}>
              <Text type="secondary">Status:</Text>
              <Select
                options={STATUS_OPTIONS}
                value={statusFilter}
                onChange={(val) => {
                  setStatusFilter(val);
                  setPage(1);
                }}
                style={{ width: 160 }}
              />
            </Space>
          </Col>
          <Col xs={24} sm={12} md={10}>
            <Space align="center">
              <Text type="secondary">Type:</Text>
              <Segmented
                options={[
                  { label: 'All', value: 'ALL' },
                  { label: 'Regular', value: 'REGULAR' },
                  { label: 'Off-Cycle', value: 'OFF_CYCLE' },
                ]}
                value={typeFilter}
                onChange={(val) => {
                  setTypeFilter(val);
                  setPage(1);
                }}
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {loadError && (
        <Alert
          message="Error loading pay runs"
          description={loadError}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Table
        dataSource={data}
        columns={columns}
        rowKey="id"
        loading={loading}
        onRow={(record) => ({
          onClick: () => navigate(`/payroll/runs/${record.id}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page,
          pageSize,
          total,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
          showSizeChanger: true,
        }}
      />

      <Modal
        title="Create Regular Pay Run"
        open={createModalOpen}
        onOk={handleCreateRun}
        onCancel={() => {
          setCreateModalOpen(false);
          setSelectedPeriod(null);
          setCreateError(null);
          setIs409Conflict(false);
        }}
        confirmLoading={createLoading}
        okButtonProps={{ disabled: !selectedPeriod }}
      >
        <div style={{ marginTop: 16, marginBottom: 16 }}>
          {createError && (
            <Alert
              message={createError}
              description={
                is409Conflict ? (
                  <div style={{ marginTop: 8 }}>
                    <Link to="/payroll/settings/pay-schedule">
                      Configure Pay Schedule Settings
                    </Link>
                  </div>
                ) : null
              }
              type="error"
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          <Form layout="vertical">
            <Form.Item label="Select Pay Period" required>
              <DatePicker
                picker="month"
                value={selectedPeriod}
                onChange={(date) => {
                  setSelectedPeriod(date);
                  setCreateError(null);
                  setIs409Conflict(false);
                }}
                style={{ width: '100%' }}
                placeholder="Choose Year and Month"
              />
            </Form.Item>
          </Form>
        </div>
      </Modal>
    </div>
  );
}
