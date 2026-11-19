import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  DatePicker,
  Alert,
  Tag,
  Space,
  Typography,
  message,
  Popconfirm,
  Drawer,
  Statistic,
  Row,
  Col,
  Progress,
  theme,
} from 'antd';
import {
  PlusOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  BarChartOutlined,
  FieldTimeOutlined,
} from '@ant-design/icons';
import { performanceService } from './performanceService.js';

const { Title, Text } = Typography;

export function PerformanceCyclesScreen() {
  const { token } = theme.useToken();
  const [cycles, setCycles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [summaryDrawerVisible, setSummaryDrawerVisible] = useState(false);
  const [summaryData, setSummaryData] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [form] = Form.useForm();

  const loadCycles = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await performanceService.listCycles();
      setCycles(Array.isArray(data) ? data : []);
    } catch (err) {
      const msg = err?.response?.data?.message || err.message || 'Failed to load performance cycles';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCycles();
  }, []);

  const handleCreate = async (values) => {
    try {
      const payload = {
        name: values.name,
        periodStart: values.periodRange[0].format('YYYY-MM-DD'),
        periodEnd: values.periodRange[1].format('YYYY-MM-DD'),
        selfReviewDeadline: values.selfReviewDeadline.format('YYYY-MM-DD'),
        managerReviewDeadline: values.managerReviewDeadline.format('YYYY-MM-DD'),
        ratingScaleMax: 5,
      };
      await performanceService.createCycle(payload);
      message.success('Performance review cycle created successfully');
      setCreateModalVisible(false);
      form.resetFields();
      loadCycles();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to create review cycle');
    }
  };

  const handleActivate = async (id) => {
    try {
      await performanceService.activateCycle(id);
      message.success('Cycle activated and reviews generated for active employees');
      loadCycles();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to activate cycle');
    }
  };

  const handleClose = async (id) => {
    try {
      await performanceService.closeCycle(id);
      message.success('Performance cycle closed successfully');
      loadCycles();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to close cycle');
    }
  };

  const handleViewSummary = async (cycle) => {
    setSummaryDrawerVisible(true);
    setSummaryLoading(true);
    try {
      const data = await performanceService.getCycleSummary(cycle.id);
      setSummaryData(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to load cycle summary');
    } finally {
      setSummaryLoading(false);
    }
  };

  const statusTag = (status) => {
    switch (status) {
      case 'DRAFT':
        return <Tag color="default">Draft</Tag>;
      case 'ACTIVE':
        return <Tag color="processing">Active</Tag>;
      case 'CLOSED':
        return <Tag color="success">Closed</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Cycle Name',
      dataIndex: 'name',
      key: 'name',
      render: (text) => <Text strong>{text}</Text>,
    },
    {
      title: 'Period',
      key: 'period',
      render: (_, record) => (
        <Text type="secondary">
          {record.periodStart} to {record.periodEnd}
        </Text>
      ),
    },
    {
      title: 'Deadlines',
      key: 'deadlines',
      render: (_, record) => (
        <Space orientation="vertical" size={2}>
          <Text type="secondary">Self: {record.selfReviewDeadline}</Text>
          <Text type="secondary">Manager: {record.managerReviewDeadline}</Text>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => statusTag(status),
    },
    {
      title: 'Scale',
      dataIndex: 'ratingScaleMax',
      key: 'scale',
      render: (max) => `1 - ${max}`,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space orientation="horizontal" size="small">
          {record.status === 'DRAFT' && (
            <Popconfirm
              title="Activate review cycle?"
              description="This will generate appraisal reviews for all active employees. Are you sure?"
              onConfirm={() => handleActivate(record.id)}
              okText="Activate"
              cancelText="Cancel"
            >
              <Button type="primary" size="small" icon={<CheckCircleOutlined />}>
                Activate
              </Button>
            </Popconfirm>
          )}
          {record.status === 'ACTIVE' && (
            <Popconfirm
              title="Close review cycle?"
              description="Closing the cycle marks all appraisals final and prevents further edits."
              onConfirm={() => handleClose(record.id)}
              okText="Close Cycle"
              cancelText="Cancel"
            >
              <Button danger size="small" icon={<CloseCircleOutlined />}>
                Close
              </Button>
            </Popconfirm>
          )}
          <Button
            size="small"
            icon={<BarChartOutlined />}
            onClick={() => handleViewSummary(record)}
          >
            Summary
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }} data-testid="performance-cycles-screen">
      <Card
        variant="borderless"
        title={
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Performance Review Cycles
            </Title>
            <Text type="secondary">
              Configure appraisal review cycles, manage submission deadlines, and monitor cycle progress.
            </Text>
          </div>
        }
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
            data-testid="create-cycle-button"
          >
            Create Cycle
          </Button>
        }
      >
        {error && (
          <Alert
            type="error"
            message="Failed to load performance cycles"
            description={error}
            action={
              <Button size="small" type="primary" onClick={loadCycles}>
                Retry
              </Button>
            }
            showIcon
            style={{ marginBottom: token.marginMD }}
            data-testid="cycles-load-error"
          />
        )}
        <Table
          rowKey="id"
          columns={columns}
          dataSource={cycles}
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title="Create Performance Review Cycle"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        okText="Create"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleCreate} initialValues={{ ratingScaleMax: 5 }}>
          <Form.Item
            name="name"
            label="Cycle Name"
            rules={[{ required: true, message: 'Please enter cycle name' }]}
          >
            <Input placeholder="e.g. Q4 2026 Annual Performance Review" />
          </Form.Item>
          <Form.Item
            name="periodRange"
            label="Review Period"
            rules={[{ required: true, message: 'Please select start and end dates' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="selfReviewDeadline"
                label="Self-Review Deadline"
                rules={[{ required: true, message: 'Please set self-review deadline' }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="managerReviewDeadline"
                label="Manager Review Deadline"
                rules={[{ required: true, message: 'Please set manager review deadline' }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Drawer
        title="Cycle Progress & Summary"
        width={480}
        open={summaryDrawerVisible}
        onClose={() => setSummaryDrawerVisible(false)}
        loading={summaryLoading}
      >
        {summaryData && (
          <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <div>
              <Title level={5}>{summaryData.cycleName}</Title>
              {statusTag(summaryData.status)}
            </div>
            <Card size="small">
              <Text strong>Overall Completion</Text>
              <Progress percent={summaryData.completionPercentage} status="active" />
            </Card>
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card size="small">
                  <Statistic title="Total Reviews" value={summaryData.totalReviews} />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small">
                  <Statistic
                    title="Completed"
                    value={summaryData.completedReviews}
                    valueStyle={{ color: token.colorSuccess }}
                  />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small">
                  <Statistic
                    title="Pending Self-Review"
                    value={summaryData.pendingSelfReviews}
                    prefix={<FieldTimeOutlined />}
                  />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small">
                  <Statistic
                    title="Pending Manager"
                    value={summaryData.pendingManagerReviews}
                    prefix={<FieldTimeOutlined />}
                  />
                </Card>
              </Col>
            </Row>
            {summaryData.averageRating != null && (
              <Card size="small">
                <Statistic
                  title="Average Manager Rating"
                  value={summaryData.averageRating}
                  suffix="/ 5.0"
                  precision={2}
                  valueStyle={{ color: token.colorPrimary }}
                />
              </Card>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
}

