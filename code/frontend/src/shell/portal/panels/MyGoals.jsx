import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  DatePicker,
  Progress,
  Tag,
  Space,
  Typography,
  Alert,
  message,
  Row,
  Col,
  Statistic,
  Slider,
  theme,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  TrophyOutlined,
  ClockCircleOutlined,
  FlagOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { goalService } from '../../../hrms/performance/goalService.js';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;

export function MyGoals() {
  const { token } = theme.useToken();
  const [goals, setGoals] = useState([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [checkInModalVisible, setCheckInModalVisible] = useState(false);
  const [activeGoal, setActiveGoal] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm();
  const [checkInForm] = Form.useForm();

  const loadGoals = async () => {
    setLoading(true);
    try {
      const data = await goalService.getMyGoals();
      setGoals(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to load your goals');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadGoals();
  }, []);

  const handleCreateGoal = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        title: values.title.trim(),
        description: values.description ? values.description.trim() : null,
        category: values.category || 'INDIVIDUAL',
        metricType: values.metricType || 'PERCENTAGE',
        targetValue: values.targetValue != null ? values.targetValue : 100,
        currentValue: values.currentValue != null ? values.currentValue : 0,
        metricUnit: values.metricUnit ? values.metricUnit.trim() : '%',
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        dueDate: values.dateRange[1].format('YYYY-MM-DD'),
        status: 'IN_PROGRESS',
      };
      await goalService.createGoal(payload);
      message.success('Goal created successfully');
      setCreateModalVisible(false);
      createForm.resetFields();
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to create goal');
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpenCheckIn = (goal) => {
    setActiveGoal(goal);
    checkInForm.setFieldsValue({
      currentValue: goal.currentValue,
      progressPercentage: goal.progressPercentage,
      status: goal.status,
      progressNotes: '',
    });
    setCheckInModalVisible(true);
  };

  const handleCheckIn = async (values) => {
    if (!activeGoal) return;
    setSubmitting(true);
    try {
      const payload = {
        currentValue: values.currentValue,
        progressPercentage: values.progressPercentage,
        status: values.status,
        progressNotes: values.progressNotes ? values.progressNotes.trim() : null,
      };
      await goalService.updateProgress(activeGoal.id, payload);
      message.success('Progress updated successfully');
      setCheckInModalVisible(false);
      checkInForm.resetFields();
      setActiveGoal(null);
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to update progress');
    } finally {
      setSubmitting(false);
    }
  };

  const activeGoalsCount = goals.filter((g) => g.status === 'IN_PROGRESS').length;
  const achievedGoalsCount = goals.filter((g) => g.status === 'ACHIEVED').length;
  const avgProgress =
    goals.length > 0
      ? Math.round(goals.reduce((acc, g) => acc + (g.progressPercentage || 0), 0) / goals.length)
      : 0;

  const columns = [
    {
      title: 'Goal / OKR',
      key: 'title',
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: token.fontSize }}>
            {record.title}
          </Text>
          {record.description && (
            <Paragraph
              type="secondary"
              ellipsis={{ rows: 2 }}
              style={{ margin: '4px 0 0', fontSize: token.fontSizeSM }}
            >
              {record.description}
            </Paragraph>
          )}
          <Space orientation="horizontal" size={4} style={{ marginTop: 4 }}>
            <Tag color={record.category === 'COMPANY' ? 'purple' : record.category === 'TEAM' ? 'blue' : 'cyan'}>
              {record.category}
            </Tag>
            {record.cycleName && <Tag color="geekblue">{record.cycleName}</Tag>}
          </Space>
        </div>
      ),
    },
    {
      title: 'Progress',
      key: 'progress',
      width: 220,
      render: (_, record) => {
        let statusColor = token.colorPrimary;
        if (record.status === 'ACHIEVED') statusColor = token.colorSuccess;
        if (record.status === 'CANCELLED') statusColor = token.colorTextTertiary;

        return (
          <div orientation="vertical" style={{ minWidth: 160 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
              <Text style={{ fontSize: token.fontSizeSM }}>
                {record.currentValue} / {record.targetValue} {record.metricUnit}
              </Text>
              <Text strong style={{ fontSize: token.fontSizeSM }}>
                {record.progressPercentage}%
              </Text>
            </div>
            <Progress
              percent={record.progressPercentage}
              strokeColor={statusColor}
              size="small"
              showInfo={false}
            />
          </div>
        );
      },
    },
    {
      title: 'Timeline',
      key: 'timeline',
      width: 150,
      render: (_, record) => {
        const isOverdue =
          record.status === 'IN_PROGRESS' && dayjs(record.dueDate).isBefore(dayjs(), 'day');
        return (
          <div>
            <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
              Start: {record.startDate}
            </div>
            <div
              style={{
                fontSize: token.fontSizeSM,
                fontWeight: isOverdue ? 600 : 400,
                color: isOverdue ? token.colorError : token.colorText,
              }}
            >
              Due: {record.dueDate}
            </div>
            {isOverdue && (
              <Tag color="error" style={{ marginTop: 2 }}>
                Overdue
              </Tag>
            )}
          </div>
        );
      },
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_, record) => {
        let color = 'processing';
        let text = record.status;
        if (record.status === 'ACHIEVED') {
          color = 'success';
          text = 'Achieved';
        } else if (record.status === 'IN_PROGRESS') {
          color = 'processing';
          text = 'In Progress';
        } else if (record.status === 'DRAFT') {
          color = 'default';
          text = 'Draft';
        } else if (record.status === 'CANCELLED') {
          color = 'warning';
          text = 'Cancelled';
        }
        return <Tag color={color}>{text}</Tag>;
      },
    },
    {
      title: 'Action',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Button
          type="primary"
          ghost
          size="small"
          icon={<EditOutlined />}
          onClick={() => handleOpenCheckIn(record)}
          disabled={record.status === 'CANCELLED'}
        >
          Check-in
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Metric Summary Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Active Goals"
              value={activeGoalsCount}
              prefix={<ClockCircleOutlined style={{ color: token.colorPrimary }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Achieved Goals"
              value={achievedGoalsCount}
              prefix={<TrophyOutlined style={{ color: token.colorSuccess }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Average Progress"
              value={avgProgress}
              suffix="%"
              prefix={<FlagOutlined style={{ color: token.colorWarning }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* Main Goals Card */}
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>My Goals & OKRs</span>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalVisible(true)}
            >
              Add Goal
            </Button>
          </div>
        }
        bordered
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={goals}
          loading={loading}
          pagination={{ pageSize: 8, hideOnSinglePage: true }}
          locale={{ emptyText: 'No goals found. Create your first goal to begin tracking.' }}
        />
      </Card>

      {/* Create Goal Modal */}
      <Modal
        title="Create New Goal / OKR"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          createForm.resetFields();
        }}
        footer={null}
        destroyOnHidden
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={handleCreateGoal}
          initialValues={{
            category: 'INDIVIDUAL',
            metricType: 'PERCENTAGE',
            targetValue: 100,
            currentValue: 0,
            metricUnit: '%',
          }}
        >
          <Form.Item
            name="title"
            label="Goal Title"
            rules={[
              { required: true, message: 'Please enter a goal title' },
              { max: 200, message: 'Goal title must not exceed 200 characters' },
            ]}
          >
            <Input placeholder="e.g. Implement Mobile Attendance Offline Sync" />
          </Form.Item>

          <Form.Item name="description" label="Description / Success Criteria">
            <TextArea rows={3} placeholder="Describe the expected outcomes, milestones, and deliverables..." />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="category" label="Category" rules={[{ required: true }]}>
                <Select>
                  <Option value="INDIVIDUAL">Individual</Option>
                  <Option value="TEAM">Team</Option>
                  <Option value="COMPANY">Company</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="metricType" label="Metric Type" rules={[{ required: true }]}>
                <Select>
                  <Option value="PERCENTAGE">Percentage (%)</Option>
                  <Option value="NUMERIC">Numeric Count</Option>
                  <Option value="MILESTONE">Milestone (Done/Not Done)</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="currentValue" label="Current Value">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="targetValue" label="Target Value" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="metricUnit" label="Unit">
                <Input placeholder="%, tickets, pts" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="dateRange"
            label="Goal Period (Start & Due Date)"
            rules={[{ required: true, message: 'Please select start and due dates' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateModalVisible(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Goal
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Progress Check-in Modal */}
      <Modal
        title={`Goal Progress Check-in: ${activeGoal?.title || ''}`}
        open={checkInModalVisible}
        onCancel={() => {
          setCheckInModalVisible(false);
          checkInForm.resetFields();
          setActiveGoal(null);
        }}
        footer={null}
        destroyOnHidden
      >
        <Form form={checkInForm} layout="vertical" onFinish={handleCheckIn}>
          {activeGoal && (
            <Alert
              type="info"
              showIcon
              message={`Target: ${activeGoal.targetValue} ${activeGoal.metricUnit} | Due: ${activeGoal.dueDate}`}
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item name="currentValue" label="Updated Value">
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              onChange={(val) => {
                if (activeGoal && activeGoal.targetValue > 0 && val != null) {
                  const pct = Math.min(100, Math.round((val / activeGoal.targetValue) * 100));
                  checkInForm.setFieldsValue({
                    progressPercentage: pct,
                    status: pct >= 100 ? 'ACHIEVED' : 'IN_PROGRESS',
                  });
                }
              }}
            />
          </Form.Item>

          <Form.Item name="progressPercentage" label="Progress %">
            <Slider
              min={0}
              max={100}
              marks={{ 0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%' }}
            />
          </Form.Item>

          <Form.Item name="status" label="Status" rules={[{ required: true }]}>
            <Select>
              <Option value="IN_PROGRESS">In Progress</Option>
              <Option value="ACHIEVED">Achieved</Option>
              <Option value="CANCELLED">Cancelled</Option>
            </Select>
          </Form.Item>

          <Form.Item name="progressNotes" label="Check-in Notes / Accomplishments">
            <TextArea rows={3} placeholder="Add key achievements, milestones reached, or blockers encountered..." />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCheckInModalVisible(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Check-in
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

