import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Rate,
  Input,
  Tag,
  Space,
  Typography,
  Alert,
  message,
  Descriptions,
  Divider,
  theme,
} from 'antd';
import { FormOutlined, EyeOutlined } from '@ant-design/icons';
import { performanceService } from '../../../hrms/performance/performanceService.js';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export function MyReviews() {
  const { token } = theme.useToken();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [activeReview, setActiveReview] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const loadMyReviews = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await performanceService.getMyReviews();
      setReviews(Array.isArray(data) ? data : []);
    } catch (err) {
      const msg = err?.response?.data?.message || err.message || 'Failed to load your performance reviews';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMyReviews();
  }, []);

  const pendingSelfReview = reviews.find((r) => r.status === 'PENDING_SELF_REVIEW');

  const openSelfReviewModal = (review) => {
    setActiveReview(review);
    form.setFieldsValue({
      selfRating: review.selfRating || undefined,
      comments: review.selfComments || '',
    });
    setModalVisible(true);
  };

  const openDetailModal = (review) => {
    setActiveReview(review);
    setDetailVisible(true);
  };

  const handleSelfSubmit = async (values) => {
    if (!activeReview) return;
    setSubmitting(true);
    try {
      await performanceService.submitSelfReview(activeReview.id, {
        selfRating: values.selfRating,
        comments: values.comments,
      });
      message.success('Self-review submitted successfully');
      setModalVisible(false);
      form.resetFields();
      loadMyReviews();
    } catch (err) {
      message.error(err?.response?.data?.message || err.message || 'Failed to submit self-review');
    } finally {
      setSubmitting(false);
    }
  };

  const statusTag = (status) => {
    switch (status) {
      case 'PENDING_SELF_REVIEW':
        return <Tag color="warning">Action Required: Self-Review</Tag>;
      case 'PENDING_MANAGER_REVIEW':
        return <Tag color="processing">Awaiting Manager Evaluation</Tag>;
      case 'COMPLETED':
        return <Tag color="success">Appraisal Completed</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Review Cycle',
      dataIndex: 'cycleName',
      key: 'cycleName',
      render: (name) => <Text strong>{name}</Text>,
    },
    {
      title: 'Manager',
      dataIndex: 'managerName',
      key: 'managerName',
      render: (name) => name || <Text type="secondary">Unassigned</Text>,
    },
    {
      title: 'Self-Rating',
      dataIndex: 'selfRating',
      key: 'selfRating',
      render: (rating) =>
        rating ? (
          <Space>
            <Rate disabled value={rating} style={{ fontSize: 13 }} />
            <Text>({rating}/5)</Text>
          </Space>
        ) : (
          <Text type="secondary">Pending</Text>
        ),
    },
    {
      title: 'Manager Rating',
      dataIndex: 'managerRating',
      key: 'managerRating',
      render: (rating, record) =>
        record.status === 'COMPLETED' && rating ? (
          <Space>
            <Rate disabled value={rating} style={{ fontSize: 13 }} />
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
      title: 'Action',
      key: 'action',
      render: (_, record) =>
        record.status === 'PENDING_SELF_REVIEW' ? (
          <Button
            type="primary"
            size="small"
            icon={<FormOutlined />}
            onClick={() => openSelfReviewModal(record)}
          >
            Complete Review
          </Button>
        ) : (
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetailModal(record)}
          >
            View
          </Button>
        ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }} data-testid="my-reviews-panel">
      {pendingSelfReview && (
        <Alert
          style={{ marginBottom: token.marginLG }}
          message={
            <Space>
              <Text strong>Self-Appraisal Action Required</Text>
              <Tag color="warning">{pendingSelfReview.cycleName}</Tag>
            </Space>
          }
          description="Your self-appraisal for this review cycle is now open. Submit your accomplishments and self-rating before the deadline."
          type="warning"
          showIcon
          action={
            <Button
              type="primary"
              size="small"
              icon={<FormOutlined />}
              onClick={() => openSelfReviewModal(pendingSelfReview)}
            >
              Fill Self-Review
            </Button>
          }
        />
      )}

      <Card
        variant="borderless"
        title={
          <div>
            <Title level={4} style={{ margin: 0 }}>
              My Performance Reviews
            </Title>
            <Text type="secondary">
              Track your appraisals, complete self-assessments, and review manager evaluations.
            </Text>
          </div>
        }
      >
        {error && (
          <Alert
            type="error"
            message="Failed to load your performance reviews"
            description={error}
            action={
              <Button size="small" type="primary" onClick={loadMyReviews}>
                Retry
              </Button>
            }
            showIcon
            style={{ marginBottom: token.marginMD }}
            data-testid="my-reviews-load-error"
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

      <Modal
        title={`Self-Appraisal: ${activeReview?.cycleName}`}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="Submit Self-Review"
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSelfSubmit}>
          <Form.Item
            name="selfRating"
            label="Self-Rating (1–5 Stars)"
            rules={[{ required: true, message: 'Please select your self-rating' }]}
          >
            <Rate style={{ fontSize: 24 }} />
          </Form.Item>
          <Form.Item
            name="comments"
            label="Key Achievements & Self-Assessment"
            rules={[{ required: true, message: 'Please provide self-assessment comments' }]}
          >
            <TextArea
              rows={4}
              placeholder="Reflect on your quarterly achievements, key projects delivered, and areas of growth..."
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`Review Details: ${activeReview?.cycleName}`}
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            Close
          </Button>,
        ]}
      >
        {activeReview && (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Review Cycle">{activeReview.cycleName}</Descriptions.Item>
              <Descriptions.Item label="Manager">{activeReview.managerName || 'Unassigned'}</Descriptions.Item>
              <Descriptions.Item label="Status">{statusTag(activeReview.status)}</Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ margin: `${token.marginSM}px 0` }}>
              My Self-Appraisal
            </Divider>
            <Card size="small" style={{ background: token.colorFillAlter }}>
              <div>
                <Text type="secondary">Self-Rating: </Text>
                {activeReview.selfRating ? (
                  <>
                    <Rate disabled value={activeReview.selfRating} style={{ fontSize: 13 }} />
                    <Text strong> ({activeReview.selfRating}/5)</Text>
                  </>
                ) : (
                  <Text type="secondary">Not submitted</Text>
                )}
              </div>
              <div style={{ marginTop: token.marginXS }}>
                <Text type="secondary">Comments:</Text>
                <Paragraph style={{ margin: 0, marginTop: token.marginXXS }}>
                  {activeReview.selfComments || 'No comments provided.'}
                </Paragraph>
              </div>
            </Card>

            <Divider orientation="left" style={{ margin: `${token.marginSM}px 0` }}>
              Manager Evaluation
            </Divider>
            <Card size="small" style={{ background: token.colorFillAlter }}>
              {activeReview.status === 'COMPLETED' ? (
                <>
                  <div>
                    <Text type="secondary">Manager Rating: </Text>
                    <Rate disabled value={activeReview.managerRating} style={{ fontSize: 13 }} />
                    <Text strong> ({activeReview.managerRating}/5)</Text>
                  </div>
                  <div style={{ marginTop: token.marginXS }}>
                    <Text type="secondary">Feedback:</Text>
                    <Paragraph style={{ margin: 0, marginTop: token.marginXXS }}>
                      {activeReview.managerComments || 'No feedback documented.'}
                    </Paragraph>
                  </div>
                </>
              ) : (
                <Text type="secondary">Manager evaluation is pending.</Text>
              )}
            </Card>
          </Space>
        )}
      </Modal>
    </div>
  );
}

