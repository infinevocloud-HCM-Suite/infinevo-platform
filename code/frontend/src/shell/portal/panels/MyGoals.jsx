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
  List,
  Popconfirm,
  theme,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  PlayCircleOutlined,
  StopOutlined,
  HistoryOutlined,
  TrophyOutlined,
  ClockCircleOutlined,
  FlagOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { goalService } from '../../../hrms/performance/goalService.js';
import { performanceService } from '../../../hrms/performance/performanceService.js';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;

export function MyGoals() {
  const { token } = theme.useToken();
  const [goals, setGoals] = useState([]);
  const [cycles, setCycles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);

  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [checkInModalVisible, setCheckInModalVisible] = useState(false);
  const [historyModalVisible, setHistoryModalVisible] = useState(false);

  const [activeGoal, setActiveGoal] = useState(null);
  const [checkIns, setCheckIns] = useState([]);
  const [checkInsLoading, setCheckInsLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [checkInForm] = Form.useForm();

  const loadGoals = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await goalService.getMyGoals();
      setGoals(Array.isArray(data) ? data : []);
    } catch (err) {
      setLoadError(err?.response?.data?.message || err.message || 'Failed to load your goals');
    } finally {
      setLoading(false);
    }
  };

  const loadCycles = async () => {
    try {
      const res = await performanceService.listCycles();
      const list = Array.isArray(res) ? res : [];
      setCycles(list.filter((c) => c.status !== 'CLOSED'));
    } catch {
      // Ignore cycle load failure
    }
  };

  useEffect(() => {
    loadGoals();
    loadCycles();
  }, []);

  const handleCreateGoal = async (values, isDraft = false) => {
    setSubmitting(true);
    try {
      const payload = {
        title: values.title.trim(),
        description: values.description ? values.description.trim() : null,
        category: values.category || 'INDIVIDUAL',
        metricType: values.metricType || 'PERCENTAGE',
        targetValue: values.targetValue != null ? values.targetValue : 100,
        metricUnit: values.metricUnit ? values.metricUnit.trim() : '%',
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        dueDate: values.dateRange[1].format('YYYY-MM-DD'),
        cycleId: values.cycleId || null,
        draft: isDraft,
      };
      await goalService.createGoal(payload);
      message.success(isDraft ? 'Goal saved as draft' : 'Goal created successfully');
      setCreateModalVisible(false);
      createForm.resetFields();
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to create goal');
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpenEdit = (goal) => {
    setActiveGoal(goal);
    editForm.setFieldsValue({
      title: goal.title,
      description: goal.description,
      category: goal.category,
      targetValue: goal.targetValue,
      metricUnit: goal.metricUnit,
      dateRange: [dayjs(goal.startDate), dayjs(goal.dueDate)],
      cycleId: goal.cycleId,
    });
    setEditModalVisible(true);
  };

  const handleEditGoal = async (values) => {
    if (!activeGoal) return;
    setSubmitting(true);
    try {
      const payload = {
        title: values.title.trim(),
        description: values.description ? values.description.trim() : null,
        category: values.category,
        targetValue: values.targetValue,
        metricUnit: values.metricUnit ? values.metricUnit.trim() : '%',
        startDate: values.dateRange[0].format('YYYY-MM-DD'),
        dueDate: values.dateRange[1].format('YYYY-MM-DD'),
        cycleId: values.cycleId || null,
      };
      await goalService.editGoal(activeGoal.id, payload);
      message.success('Goal updated successfully');
      setEditModalVisible(false);
      editForm.resetFields();
      setActiveGoal(null);
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to update goal');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStartGoal = async (goalId) => {
    try {
      await goalService.startGoal(goalId);
      message.success('Goal started');
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to start goal');
    }
  };

  const handleCancelGoal = async (goalId) => {
    try {
      await goalService.cancelGoal(goalId);
      message.success('Goal cancelled');
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to cancel goal');
    }
  };

  const loadGoalCheckIns = async (goalId) => {
    setCheckInsLoading(true);
    try {
      const res = await goalService.getCheckIns(goalId);
      setCheckIns(Array.isArray(res) ? res : []);
    } catch {
      setCheckIns([]);
    } finally {
      setCheckInsLoading(false);
    }
  };

  const handleOpenCheckIn = (goal) => {
    setActiveGoal(goal);
    checkInForm.setFieldsValue({
      currentValue: goal.currentValue,
      notes: '',
    });
    loadGoalCheckIns(goal.id);
    setCheckInModalVisible(true);
  };

  const handleOpenHistory = (goal) => {
    setActiveGoal(goal);
    loadGoalCheckIns(goal.id);
    setHistoryModalVisible(true);
  };

  const handleCheckIn = async (values) => {
    if (!activeGoal) return;
    setSubmitting(true);
    try {
      const payload = {
        currentValue: values.currentValue,
        notes: values.notes ? values.notes.trim() : null,
      };
      await goalService.checkIn(activeGoal.id, payload);
      message.success('Check-in recorded successfully');
      setCheckInModalVisible(false);
      checkInForm.resetFields();
      setActiveGoal(null);
      loadGoals();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to record check-in');
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

  const isReadOnly = (record) =>
    Boolean(record.readOnly || record.status === 'ACHIEVED' || record.status === 'CANCELLED');

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
            {record.readOnly && <Tag color="default">Locked</Tag>}
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
      width: 220,
      render: (_, record) => {
        if (isReadOnly(record)) {
          return (
            <Button
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => handleOpenHistory(record)}
            >
              History
            </Button>
          );
        }

        return (
          <Space size={6} wrap>
            {record.status === 'DRAFT' && (
              <Button
                type="primary"
                size="small"
                icon={<PlayCircleOutlined />}
                onClick={() => handleStartGoal(record.id)}
              >
                Start
              </Button>
            )}
            {record.status === 'IN_PROGRESS' && (
              <Button
                type="primary"
                ghost
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleOpenCheckIn(record)}
              >
                Check-in
              </Button>
            )}
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenEdit(record)}
            >
              Edit
            </Button>
            <Popconfirm
              title="Cancel this goal?"
              description="This will mark the goal as cancelled permanently."
              onConfirm={() => handleCancelGoal(record.id)}
              okText="Yes, Cancel"
              cancelText="No"
            >
              <Button danger size="small" icon={<StopOutlined />}>
                Cancel
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  if (loadError) {
    return (
      <Card bordered>
        <Alert
          type="error"
          showIcon
          message="Failed to load your goals"
          description={loadError}
          action={
            <Button icon={<ReloadOutlined />} onClick={loadGoals}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

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
          onFinish={(values) => handleCreateGoal(values, false)}
          initialValues={{
            category: 'INDIVIDUAL',
            metricType: 'PERCENTAGE',
            targetValue: 100,
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
            <Col span={12}>
              <Form.Item name="targetValue" label="Target Value" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="metricUnit" label="Unit">
                <Input placeholder="%, tickets, pts" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="dateRange"
                label="Goal Period (Start & Due Date)"
                rules={[{ required: true, message: 'Please select start and due dates' }]}
              >
                <DatePicker.RangePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cycleId" label="Link to Review Cycle (Optional)">
                <Select allowClear placeholder="Select an active cycle">
                  {cycles.map((c) => (
                    <Option key={c.id} value={c.id}>
                      {c.name}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateModalVisible(false)}>Cancel</Button>
              <Button
                onClick={() => {
                  createForm.validateFields().then((vals) => handleCreateGoal(vals, true));
                }}
                loading={submitting}
              >
                Save as Draft
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Goal
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Goal Modal */}
      <Modal
        title={`Edit Goal: ${activeGoal?.title || ''}`}
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false);
          editForm.resetFields();
          setActiveGoal(null);
        }}
        footer={null}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditGoal}>
          <Form.Item
            name="title"
            label="Goal Title"
            rules={[
              { required: true, message: 'Please enter a goal title' },
              { max: 200, message: 'Goal title must not exceed 200 characters' },
            ]}
          >
            <Input />
          </Form.Item>

          <Form.Item name="description" label="Description / Success Criteria">
            <TextArea rows={3} />
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
              <Form.Item name="targetValue" label="Target Value" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="metricUnit" label="Unit">
                <Input placeholder="%, tickets, pts" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cycleId" label="Link to Review Cycle">
                <Select allowClear placeholder="Select an active cycle">
                  {cycles.map((c) => (
                    <Option key={c.id} value={c.id}>
                      {c.name}
                    </Option>
                  ))}
                </Select>
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
              <Button
                onClick={() => {
                  setEditModalVisible(false);
                  setActiveGoal(null);
                }}
              >
                Cancel
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Changes
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
          setCheckIns([]);
        }}
        footer={null}
        destroyOnHidden
      >
        <Form form={checkInForm} layout="vertical" onFinish={handleCheckIn}>
          {activeGoal && (
            <Alert
              type="info"
              showIcon
              message={`Target: ${activeGoal.targetValue} ${activeGoal.metricUnit} | Current: ${activeGoal.currentValue} | Due: ${activeGoal.dueDate}`}
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item
            name="currentValue"
            label="Updated Metric Value"
            rules={[{ required: true, message: 'Please enter current value' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="notes" label="Check-in Notes / Accomplishments">
            <TextArea
              rows={3}
              maxLength={2000}
              placeholder="Add key achievements, milestones reached, or blockers encountered..."
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 16, textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setCheckInModalVisible(false);
                  setActiveGoal(null);
                }}
              >
                Cancel
              </Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Check-in
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <div>
          <Text strong>Past Check-ins</Text>
          <List
            style={{ marginTop: 8 }}
            loading={checkInsLoading}
            dataSource={checkIns}
            locale={{ emptyText: 'No check-ins recorded yet.' }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Text strong>
                        {item.currentValue} ({item.progressPercentage}%)
                      </Text>
                      <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                        {item.createdAt ? dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') : ''}
                      </Text>
                    </div>
                  }
                  description={item.notes || 'No notes provided'}
                />
              </List.Item>
            )}
          />
        </div>
      </Modal>

      {/* History Modal for Read-Only / Achieved / Cancelled Goals */}
      <Modal
        title={`Check-in History: ${activeGoal?.title || ''}`}
        open={historyModalVisible}
        onCancel={() => {
          setHistoryModalVisible(false);
          setActiveGoal(null);
          setCheckIns([]);
        }}
        footer={[
          <Button
            key="close"
            onClick={() => {
              setHistoryModalVisible(false);
              setActiveGoal(null);
            }}
          >
            Close
          </Button>,
        ]}
      >
        <List
          loading={checkInsLoading}
          dataSource={checkIns}
          locale={{ emptyText: 'No check-ins recorded yet.' }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Text strong>
                      {item.currentValue} ({item.progressPercentage}%)
                    </Text>
                    <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                      {item.createdAt ? dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') : ''}
                    </Text>
                  </div>
                }
                description={item.notes || 'No notes provided'}
              />
            </List.Item>
          )}
        />
      </Modal>
    </div>
  );
}
