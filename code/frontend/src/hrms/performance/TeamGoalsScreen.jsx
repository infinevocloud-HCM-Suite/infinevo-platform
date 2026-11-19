import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Tag,
  Space,
  Typography,
  Drawer,
  Descriptions,
  Progress,
  Input,
  Select,
  Row,
  Col,
  Statistic,
  Alert,
  List,
  theme,
} from 'antd';
import {
  EyeOutlined,
  SearchOutlined,
  TrophyOutlined,
  ClockCircleOutlined,
  FlagOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { goalService } from './goalService.js';

const { Text, Paragraph } = Typography;
const { Option } = Select;

export function TeamGoalsScreen() {
  const { token } = theme.useToken();
  const [goals, setGoals] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [selectedGoal, setSelectedGoal] = useState(null);
  const [checkIns, setCheckIns] = useState([]);
  const [checkInsLoading, setCheckInsLoading] = useState(false);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const loadTeamGoals = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await goalService.getTeamGoals();
      setGoals(Array.isArray(data) ? data : []);
    } catch (err) {
      setLoadError(err?.response?.data?.message || err.message || 'Failed to load team goals');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTeamGoals();
  }, []);

  const openGoalDetail = async (goal) => {
    setSelectedGoal(goal);
    setDrawerVisible(true);
    setCheckInsLoading(true);
    try {
      const history = await goalService.getCheckIns(goal.id);
      setCheckIns(Array.isArray(history) ? history : []);
    } catch {
      setCheckIns([]);
    } finally {
      setCheckInsLoading(false);
    }
  };

  const filteredGoals = goals.filter((g) => {
    const matchesSearch =
      !searchFilter ||
      g.employeeName?.toLowerCase().includes(searchFilter.toLowerCase()) ||
      g.title?.toLowerCase().includes(searchFilter.toLowerCase()) ||
      g.employeeNumber?.toLowerCase().includes(searchFilter.toLowerCase());

    const matchesCategory = categoryFilter === 'ALL' || g.category === categoryFilter;
    const matchesStatus = statusFilter === 'ALL' || g.status === statusFilter;

    return matchesSearch && matchesCategory && matchesStatus;
  });

  const totalGoals = goals.length;
  const activeCount = goals.filter((g) => g.status === 'IN_PROGRESS').length;
  const achievedCount = goals.filter((g) => g.status === 'ACHIEVED').length;

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      width: 200,
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: token.fontSize }}>
            {record.employeeName || 'Unknown'}
          </Text>
          {record.employeeNumber && (
            <div>
              <Tag color="default" style={{ marginTop: 2, fontSize: token.fontSizeSM - 1 }}>
                {record.employeeNumber}
              </Tag>
            </div>
          )}
        </div>
      ),
    },
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
      width: 200,
      render: (_, record) => {
        let statusColor = token.colorPrimary;
        if (record.status === 'ACHIEVED') statusColor = token.colorSuccess;
        if (record.status === 'CANCELLED') statusColor = token.colorTextTertiary;

        return (
          <div style={{ minWidth: 150 }}>
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
      width: 140,
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
      width: 120,
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
      width: 90,
      render: (_, record) => (
        <Button
          size="small"
          icon={<EyeOutlined />}
          onClick={() => openGoalDetail(record)}
        >
          View
        </Button>
      ),
    },
  ];

  if (loadError) {
    return (
      <Card bordered>
        <Alert
          type="error"
          showIcon
          message="Failed to load team goals"
          description={loadError}
          action={
            <Button icon={<ReloadOutlined />} onClick={loadTeamGoals}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Overview Metric Row */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Total Team Goals"
              value={totalGoals}
              prefix={<FlagOutlined style={{ color: token.colorPrimary }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Active Goals"
              value={activeCount}
              prefix={<ClockCircleOutlined style={{ color: token.colorWarning }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small">
            <Statistic
              title="Achieved Goals"
              value={achievedCount}
              prefix={<TrophyOutlined style={{ color: token.colorSuccess }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* Main Team Goals Table Card */}
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
            <span>Team Goals & OKRs</span>
            <Space wrap>
              <Input
                placeholder="Search team member or goal..."
                prefix={<SearchOutlined />}
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                style={{ width: 220 }}
                allowClear
              />
              <Select
                value={categoryFilter}
                onChange={setCategoryFilter}
                style={{ width: 130 }}
              >
                <Option value="ALL">All Categories</Option>
                <Option value="INDIVIDUAL">Individual</Option>
                <Option value="TEAM">Team</Option>
                <Option value="COMPANY">Company</Option>
              </Select>
              <Select
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ width: 120 }}
              >
                <Option value="ALL">All Status</Option>
                <Option value="IN_PROGRESS">In Progress</Option>
                <Option value="ACHIEVED">Achieved</Option>
                <Option value="DRAFT">Draft</Option>
                <Option value="CANCELLED">Cancelled</Option>
              </Select>
            </Space>
          </div>
        }
        bordered
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={filteredGoals}
          loading={loading}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: 'No team goals found' }}
        />
      </Card>

      {/* Goal Detail Drawer with Check-in History */}
      <Drawer
        title="Goal & OKR Details"
        placement="right"
        width={500}
        open={drawerVisible}
        onClose={() => {
          setDrawerVisible(false);
          setSelectedGoal(null);
          setCheckIns([]);
        }}
      >
        {selectedGoal && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Employee">
                {selectedGoal.employeeName} ({selectedGoal.employeeNumber})
              </Descriptions.Item>
              <Descriptions.Item label="Goal Title">
                {selectedGoal.title}
              </Descriptions.Item>
              <Descriptions.Item label="Category">
                <Tag color="blue">{selectedGoal.category}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Metric Type">
                {selectedGoal.metricType}
              </Descriptions.Item>
              <Descriptions.Item label="Target Metric">
                {selectedGoal.targetValue} {selectedGoal.metricUnit}
              </Descriptions.Item>
              <Descriptions.Item label="Current Metric">
                {selectedGoal.currentValue} {selectedGoal.metricUnit}
              </Descriptions.Item>
              <Descriptions.Item label="Progress">
                <Progress percent={selectedGoal.progressPercentage} size="small" />
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={selectedGoal.status === 'ACHIEVED' ? 'success' : 'processing'}>
                  {selectedGoal.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Start Date">
                {selectedGoal.startDate}
              </Descriptions.Item>
              <Descriptions.Item label="Due Date">
                {selectedGoal.dueDate}
              </Descriptions.Item>
            </Descriptions>

            {selectedGoal.description && (
              <div>
                <Text strong>Description / Scope:</Text>
                <Paragraph style={{ marginTop: 4 }}>{selectedGoal.description}</Paragraph>
              </div>
            )}

            <div>
              <Text strong>Check-in History</Text>
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
          </div>
        )}
      </Drawer>
    </div>
  );
}
