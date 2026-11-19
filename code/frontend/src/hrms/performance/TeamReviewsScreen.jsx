import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Tag,
  Space,
  Typography,
  Drawer,
  Form,
  Rate,
  Input,
  message,
  Descriptions,
  Divider,
  Alert,
  theme,
} from 'antd';
import { CheckOutlined, EditOutlined, EyeOutlined } from '@ant-design/icons';
import { performanceService } from './performanceService.js';
import { goalService } from './goalService.js';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export function TeamReviewsScreen() {
  const { token } = theme.useToken();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedReview, setSelectedReview] = useState(null);
  const [linkedGoals, setLinkedGoals] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const loadTeamReviews = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await performanceService.getTeamReviews();
      setReviews(Array.isArray(data) ? data : []);
    } catch (err) {
      const msg = err?.response?.data?.message || err.message || 'Failed to load team reviews';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTeamReviews();
  }, []);

  const openEvaluationDrawer = async (review) => {
    setSelectedReview(review);
    form.setFieldsValue({
      managerRating: review.managerRating || undefined,
      comments: review.managerComments || '',
    });
    setDrawerVisible(true);
    if (review?.employeeId) {
      try {
        const goals = await goalService.getGoalsForEmployee(review.employeeId, review.cycleId);
        setLinkedGoals(Array.isArray(goals) ? goals : []);
      } catch {
        setLinkedGoals([]);
      }
    } else {
      setLinkedGoals([]);
    }
  };

  const handleManagerSubmit = async (values) => {
    if (!selectedReview) return;
    setSubmitting(true);
    try {
      await performanceService.submitManagerReview(selectedReview.id, {
        managerRating: values.managerRating,
        comments: values.comments,
      });
      message.success('Manager review submitted successfully');
      setDrawerVisible(false);
      form.resetFields();
      loadTeamReviews();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to submit manager appraisal');
    } finally {
      setSubmitting(false);
    }
  };

  const statusTag = (status) => {
    switch (status) {
      case 'PENDING_SELF_REVIEW':
        return <Tag color="warning">Awaiting Self-Review</Tag>;
      case 'PENDING_MANAGER_REVIEW':
        return <Tag color="processing">Ready for Manager Review</Tag>;
      case 'COMPLETED':
        return <Tag color="success">Completed</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, record) => (
        <div>
          <Text strong>{record.employeeName}</Text>
          {record.employeeNumber && (
            <div>
              <Text type="secondary" style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
                {record.employeeNumber}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Cycle',
      dataIndex: 'cycleName',
      key: 'cycleName',
    },
    {
      title: 'Self-Rating',
      dataIndex: 'selfRating',
      key: 'selfRating',
      render: (rating) =>
        rating ? (
          <Space>
            <Rate disabled value={rating} style={{ fontSize: 14 }} />
            <Text>({rating}/5)</Text>
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Manager Rating',
      dataIndex: 'managerRating',
      key: 'managerRating',
      render: (rating) =>
        rating ? (
          <Space>
            <Rate disabled value={rating} style={{ fontSize: 14 }} />
            <Text strong>({rating}/5)</Text>
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => statusTag(status),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => {
        if (record.status === 'PENDING_MANAGER_REVIEW') {
          return (
            <Button
              type="primary"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEvaluationDrawer(record)}
            >
              Evaluate
            </Button>
          );
        }
        if (record.status === 'COMPLETED') {
          return (
            <Button
              type="default"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openEvaluationDrawer(record)}
            >
              View Evaluation
            </Button>
          );
        }
        return (
          <Button size="small" disabled>
            Evaluate
          </Button>
        );
      },
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }} data-testid="team-reviews-screen">
      <Card
        variant="borderless"
        title={
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Team Performance Reviews
            </Title>
            <Text type="secondary">
              Review and evaluate appraisal submissions for direct reports in your reporting chain.
            </Text>
          </div>
        }
      >
        {error && (
          <Alert
            type="error"
            message="Failed to load team reviews"
            description={error}
            action={
              <Button size="small" type="primary" onClick={loadTeamReviews}>
                Retry
              </Button>
            }
            showIcon
            style={{ marginBottom: token.marginMD }}
            data-testid="team-reviews-load-error"
          />
        )}
        <Table
          rowKey="id"
          columns={columns}
          dataSource={reviews}
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Drawer
        title={
          selectedReview?.status === 'COMPLETED'
            ? `Review: ${selectedReview?.employeeName}`
            : `Evaluate: ${selectedReview?.employeeName}`
        }
        width={520}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        destroyOnClose
      >
        {selectedReview && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Employee">
                {selectedReview.employeeName} ({selectedReview.employeeNumber})
              </Descriptions.Item>
              <Descriptions.Item label="Review Cycle">
                {selectedReview.cycleName}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                {statusTag(selectedReview.status)}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ margin: `${token.marginSM}px 0` }}>
              Linked Goals & OKRs
            </Divider>
            {linkedGoals.length > 0 ? (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {linkedGoals.map((goal) => (
                  <Card key={goal.id} size="small" style={{ background: token.colorFillAlter }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong>{goal.title}</Text>
                      <Tag
                        color={
                          goal.status === 'ACHIEVED'
                            ? 'success'
                            : goal.status === 'CANCELLED'
                            ? 'default'
                            : 'processing'
                        }
                      >
                        {goal.status}
                      </Tag>
                    </div>
                    {goal.description && (
                      <Paragraph style={{ margin: 0, marginTop: 4 }}>{goal.description}</Paragraph>
                    )}
                    <div style={{ marginTop: 6 }}>
                      <Text type="secondary">
                        Progress: {goal.progressPercentage}% ({goal.currentValue} / {goal.targetValue}{' '}
                        {goal.metricUnit || ''})
                      </Text>
                    </div>
                  </Card>
                ))}
              </Space>
            ) : (
              <Text type="secondary">No goals linked to this review cycle.</Text>
            )}

            <Divider orientation="left" style={{ margin: `${token.marginSM}px 0` }}>
              Employee Self-Appraisal
            </Divider>

            {selectedReview.selfRating ? (
              <Card size="small" style={{ background: token.colorFillAlter }}>
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <div>
                    <Text type="secondary">Self-Rating: </Text>
                    <Rate disabled value={selectedReview.selfRating} style={{ fontSize: 14 }} />
                    <Text strong> ({selectedReview.selfRating} / 5)</Text>
                  </div>
                  {selectedReview.selfComments ? (
                    <div>
                      <Text type="secondary">Achievements & Remarks:</Text>
                      <Paragraph style={{ margin: 0, marginTop: token.marginXXS }}>
                        {selectedReview.selfComments}
                      </Paragraph>
                    </div>
                  ) : (
                    <Text type="secondary">No qualitative comments provided.</Text>
                  )}
                </Space>
              </Card>
            ) : (
              <Alert
                type="info"
                message="Self-Review Pending"
                description="The employee has not submitted their self-appraisal yet. You can still input ratings if the deadline has passed."
                showIcon
              />
            )}

            <Divider orientation="left" style={{ margin: `${token.marginSM}px 0` }}>
              Manager Evaluation
            </Divider>

            {selectedReview.status === 'COMPLETED' ? (
              <Card size="small" style={{ background: token.colorFillAlter }}>
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <div>
                    <Text type="secondary">Final Rating: </Text>
                    <Rate disabled value={selectedReview.managerRating} style={{ fontSize: 14 }} />
                    <Text strong> ({selectedReview.managerRating} / 5)</Text>
                  </div>
                  <div>
                    <Text type="secondary">Manager Feedback:</Text>
                    <Paragraph style={{ margin: 0, marginTop: token.marginXXS }}>
                      {selectedReview.managerComments || 'No feedback documented.'}
                    </Paragraph>
                  </div>
                </Space>
              </Card>
            ) : (
              <Form form={form} layout="vertical" onFinish={handleManagerSubmit}>
                <Form.Item
                  name="managerRating"
                  label="Performance Rating (1–5 Stars)"
                  rules={[{ required: true, message: 'Please select a manager rating' }]}
                >
                  <Rate style={{ fontSize: 24 }} />
                </Form.Item>
                <Form.Item
                  name="comments"
                  label="Manager Qualitative Feedback & Goals Review"
                  rules={[{ required: true, message: 'Please provide evaluation comments' }]}
                >
                  <TextArea rows={4} placeholder="Document strengths, areas for improvement, and accomplishments..." />
                </Form.Item>
                <Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    icon={<CheckOutlined />}
                    loading={submitting}
                    block
                  >
                    Submit Performance Rating
                  </Button>
                </Form.Item>
              </Form>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
}

