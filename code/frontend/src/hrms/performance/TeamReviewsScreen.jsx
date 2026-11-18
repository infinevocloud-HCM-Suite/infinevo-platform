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

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export function TeamReviewsScreen() {
  const { token } = theme.useToken();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedReview, setSelectedReview] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const loadTeamReviews = async () => {
    setLoading(true);
    try {
      const data = await performanceService.getTeamReviews();
      setReviews(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to load team reviews');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTeamReviews();
  }, []);

  const openEvaluationDrawer = (review) => {
    setSelectedReview(review);
    form.setFieldsValue({
      managerRating: review.managerRating || undefined,
      comments: review.managerComments || '',
    });
    setDrawerVisible(true);
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
      render: (_, record) => (
        <Button
          type={record.status === 'PENDING_MANAGER_REVIEW' ? 'primary' : 'default'}
          size="small"
          icon={record.status === 'COMPLETED' ? <EyeOutlined /> : <EditOutlined />}
          onClick={() => openEvaluationDrawer(record)}
        >
          {record.status === 'COMPLETED' ? 'View Evaluation' : 'Evaluate'}
        </Button>
      ),
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

