import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Card,
  Row,
  Col,
  Space,
  Typography,
  Select,
  Tag,
  Button,
  Input,
  Drawer,
  Spin,
  Alert,
  InputNumber,
  Popconfirm,
  Modal,
  Form,
  Divider,
} from 'antd';
import {
  SafetyCertificateOutlined,
  ReloadOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  FilePdfOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { proofService } from './proofService';
import { currentFy, fyOptions, formatFyDisplay, fyForApi } from './financialYear';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { readError } from './apiError';

const { Title, Text, Paragraph } = Typography;

export function ProofReviewQueue() {
  const [selectedFy, setSelectedFy] = useState(currentFy());
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [queueData, setQueueData] = useState({ content: [], totalElements: 0, number: 0, size: 25 });
  const [error, setError] = useState(null);

  // Review Drawer state
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false);
  const [activeProofId, setActiveProofId] = useState(null);
  const [activeRow, setActiveRow] = useState(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewData, setReviewData] = useState(null);

  // Item approval modal state
  const [itemDecisionModal, setItemDecisionModal] = useState({ visible: false, item: null, action: 'APPROVE' });
  const [decisionAmount, setDecisionAmount] = useState(0);
  const [decisionComment, setDecisionComment] = useState('');
  const [deciding, setDeciding] = useState(false);

  // Final decision modal state
  const [finalDecisionModal, setFinalDecisionModal] = useState({ visible: false, action: 'APPROVE' });
  const [finalComment, setFinalComment] = useState('');
  const [finalSubmitting, setFinalSubmitting] = useState(false);

  const fetchQueue = useCallback(async (page = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data = await proofService.listQueue(
        selectedFy,
        statusFilter || undefined,
        searchQuery || undefined,
        page,
        queueData.size,
      );
      setQueueData(data || { content: [], totalElements: 0, number: 0, size: 25 });
    } catch (err) {
      setError(readError(err, 'Failed to load proof review queue').message);
    } finally {
      setLoading(false);
    }
  }, [selectedFy, statusFilter, searchQuery, queueData.size]);

  useEffect(() => {
    fetchQueue(0);
  }, [fetchQueue]);

  const openReviewDrawer = async (record) => {
    setActiveRow(record);
    setActiveProofId(record.proof_id);
    setReviewDrawerVisible(true);
    setReviewLoading(true);
    try {
      const review = await proofService.getReview(record.proof_id);
      setReviewData(review);
    } catch (err) {
      errorMsg(readError(err, 'Failed to load proof review details'));
    } finally {
      setReviewLoading(false);
    }
  };

  const reloadReview = async () => {
    if (!activeProofId) return;
    setReviewLoading(true);
    try {
      const review = await proofService.getReview(activeProofId);
      setReviewData(review);
      fetchQueue(queueData.number);
    } catch (err) {
      errorMsg(readError(err, 'Failed to reload review'));
    } finally {
      setReviewLoading(false);
    }
  };

  const handleOpenItemDecision = (item, action) => {
    setItemDecisionModal({
      visible: true,
      item,
      action,
    });
    setDecisionAmount(item.claimed_amount || item.declared_amount || 0);
    setDecisionComment('');
  };

  const handleConfirmItemDecision = async () => {
    const { item, action } = itemDecisionModal;
    setDeciding(true);
    try {
      await proofService.decideItem(activeProofId, item.id, {
        action,
        approvedAmount: action === 'APPROVE' ? decisionAmount : 0,
        comment: decisionComment,
      });
      successMsg('Item Decided', `Item marked as ${action}`);
      setItemDecisionModal({ visible: false, item: null, action: 'APPROVE' });
      reloadReview();
    } catch (err) {
      errorMsg(readError(err, 'Failed to record item decision'));
    } finally {
      setDeciding(false);
    }
  };

  const handleConfirmFinalDecision = async () => {
    const { action } = finalDecisionModal;
    setFinalSubmitting(true);
    try {
      await proofService.decideFinal(activeProofId, {
        action,
        comment: finalComment,
      });
      successMsg('Final Decision Recorded', `Proof successfully ${action === 'APPROVE' ? 'Approved' : 'Returned'}`);
      setFinalDecisionModal({ visible: false, action: 'APPROVE' });
      setReviewDrawerVisible(false);
      fetchQueue(queueData.number);
    } catch (err) {
      errorMsg(readError(err, 'Failed to record final decision'));
    } finally {
      setFinalSubmitting(false);
    }
  };

  const queueColumns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, record) => (
        <div>
          <Text strong>{record.name || 'Unnamed Employee'}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            ID: {record.number || record.employee_id}
          </Text>
        </div>
      ),
    },
    {
      title: 'Regime',
      dataIndex: 'tax_regime',
      key: 'tax_regime',
      width: 100,
      render: (regime) => (
        <Tag color={regime === 'NEW' ? 'cyan' : 'magenta'}>
          {regime || 'NEW'}
        </Tag>
      ),
    },
    {
      title: 'Proof Status',
      dataIndex: 'proof_status',
      key: 'proof_status',
      width: 140,
      render: (st) => {
        const color =
          st === 'APPROVED'
            ? 'success'
            : st === 'REJECTED'
            ? 'error'
            : st === 'SUBMITTED'
            ? 'processing'
            : st === 'DRAFT'
            ? 'warning'
            : 'default';
        return <Tag color={color}>{st || 'NOT_STARTED'}</Tag>;
      },
    },
    {
      title: 'Claimed Amount',
      dataIndex: 'claimed_total',
      key: 'claimed_total',
      width: 140,
      render: (val) => <Text>₹{Number(val || 0).toLocaleString('en-IN')}</Text>,
    },
    {
      title: 'Approved Amount',
      dataIndex: 'approved_total',
      key: 'approved_total',
      width: 140,
      render: (val) => (val !== null ? <Text strong>₹{Number(val).toLocaleString('en-IN')}</Text> : <Text type="secondary">—</Text>),
    },
    {
      title: 'Submitted At',
      dataIndex: 'submitted_at',
      key: 'submitted_at',
      width: 160,
      render: (val) => (val ? new Date(val).toLocaleDateString() : '—'),
    },
    {
      title: 'Action',
      key: 'action',
      width: 130,
      render: (_, record) => (
        <Button
          type="primary"
          size="small"
          icon={<EyeOutlined />}
          disabled={!record.proof_id}
          onClick={() => openReviewDrawer(record)}
          data-testid={`review-btn-${record.employee_id}`}
        >
          Review
        </Button>
      ),
    },
  ];

  const reviewItemColumns = [
    {
      title: 'Component / Kind',
      dataIndex: 'description',
      key: 'description',
      render: (text, item) => (
        <div>
          <Text strong>{text || item.source_kind}</Text>
          <br />
          <Tag color="geekblue" style={{ marginTop: 2 }}>{item.source_kind}</Tag>
        </div>
      ),
    },
    {
      title: 'Declared',
      dataIndex: 'declared_amount',
      key: 'declared_amount',
      width: 110,
      render: (val) => <Text>₹{Number(val || 0).toLocaleString('en-IN')}</Text>,
    },
    {
      title: 'Claimed',
      dataIndex: 'claimed_amount',
      key: 'claimed_amount',
      width: 110,
      render: (val) => <Text strong>₹{Number(val || 0).toLocaleString('en-IN')}</Text>,
    },
    {
      title: 'Verified',
      dataIndex: 'approved_amount',
      key: 'approved_amount',
      width: 110,
      render: (val) => (val !== null ? <Text style={{ color: '#52c41a' }} strong>₹{Number(val).toLocaleString('en-IN')}</Text> : '—'),
    },
    {
      title: 'Receipts / Proofs',
      key: 'documents',
      render: (_, item) => {
        const docs = item.documents || [];
        if (docs.length === 0) return <Text type="secondary" italic>No attachments</Text>;
        return (
          <Space orientation="vertical" size="small">
            {docs.map((doc) => (
              <Tag key={doc.document_id} icon={<FilePdfOutlined />} color="blue">
                <a
                  href={`/api/v1/payroll/employees/${activeRow?.employee_id}/proof-of-investment/${encodeURIComponent(fyForApi(selectedFy))}/items/${item.id}/documents/${doc.document_id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {doc.file_name}
                </a>
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (st) => (
        <Tag color={st === 'VERIFIED' ? 'success' : st === 'REJECTED' ? 'error' : 'warning'}>
          {st || 'PENDING'}
        </Tag>
      ),
    },
    {
      title: 'Review Decision',
      key: 'action',
      width: 180,
      render: (_, item) => (
        <Space size="small">
          <Button
            size="small"
            type="primary"
            icon={<CheckCircleOutlined />}
            onClick={() => handleOpenItemDecision(item, 'APPROVE')}
            data-testid={`btn-approve-item-${item.id}`}
          >
            Verify
          </Button>
          <Button
            size="small"
            danger
            icon={<CloseCircleOutlined />}
            onClick={() => handleOpenItemDecision(item, 'DISALLOW')}
            data-testid={`btn-disallow-item-${item.id}`}
          >
            Disallow
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <Card
        title={
          <Row justify="space-between" align="middle" wrap gutter={[12, 12]}>
            <Col>
              <Space align="center">
                <SafetyCertificateOutlined style={{ fontSize: 22, color: '#52c41a' }} />
                <div>
                  <Title level={4} style={{ margin: 0 }}>
                    Proof of Investment Verification Queue
                  </Title>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Review submitted employee tax saving investment receipts & proofs
                  </Text>
                </div>
              </Space>
            </Col>
            <Col>
              <Space align="center" wrap>
                <Text strong>FY:</Text>
                <Select
                  value={selectedFy}
                  onChange={(val) => setSelectedFy(val)}
                  options={fyOptions().map((opt) => ({
                    value: opt.value,
                    label: opt.label,
                  }))}
                  style={{ width: 130 }}
                  data-testid="queue-fy-select"
                />

                <Select
                  value={statusFilter}
                  onChange={(val) => setStatusFilter(val)}
                  placeholder="All Statuses"
                  allowClear
                  style={{ width: 150 }}
                  options={[
                    { value: '', label: 'All Statuses' },
                    { value: 'SUBMITTED', label: 'Submitted (Pending)' },
                    { value: 'APPROVED', label: 'Approved' },
                    { value: 'REJECTED', label: 'Rejected' },
                    { value: 'DRAFT', label: 'Draft' },
                    { value: 'NOT_STARTED', label: 'Not Started' },
                  ]}
                  data-testid="queue-status-select"
                />

                <Input
                  placeholder="Search employee..."
                  prefix={<SearchOutlined />}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onPressEnter={() => fetchQueue(0)}
                  style={{ width: 180 }}
                />

                <Button icon={<ReloadOutlined />} onClick={() => fetchQueue(queueData.number)} disabled={loading}>
                  Reload
                </Button>
              </Space>
            </Col>
          </Row>
        }
      >
        {error && (
          <Alert
            message="Error"
            description={error}
            type="error"
            showIcon
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          dataSource={queueData.content || []}
          columns={queueColumns}
          rowKey={(r) => r.employee_id || r.proof_id}
          loading={loading}
          pagination={{
            current: (queueData.number || 0) + 1,
            pageSize: queueData.size || 25,
            total: queueData.totalElements || 0,
            onChange: (p) => fetchQueue(p - 1),
          }}
          locale={{ emptyText: 'No employee proofs found matching selected criteria.' }}
        />
      </Card>

      {/* Review Drawer */}
      <Drawer
        title={
          <Space>
            <SafetyCertificateOutlined style={{ color: '#52c41a' }} />
            <span>
              Review POI: <Text strong>{activeRow?.name}</Text> (FY {formatFyDisplay(selectedFy)})
            </span>
          </Space>
        }
        placement="right"
        width={950}
        open={reviewDrawerVisible}
        onClose={() => setReviewDrawerVisible(false)}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={reloadReview} disabled={reviewLoading}>
              Reload
            </Button>
          </Space>
        }
        footer={
          <Row justify="space-between" align="middle">
            <Col>
              <Text type="secondary">
                Overall Proof Status:{' '}
                <Tag color={reviewData?.status === 'APPROVED' ? 'success' : 'processing'}>
                  {reviewData?.status || 'SUBMITTED'}
                </Tag>
              </Text>
            </Col>
            <Col>
              <Space>
                <Button
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => {
                    setFinalDecisionModal({ visible: true, action: 'RETURN' });
                    setFinalComment('');
                  }}
                  data-testid="btn-final-return"
                >
                  Return / Reject All
                </Button>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={() => {
                    setFinalDecisionModal({ visible: true, action: 'APPROVE' });
                    setFinalComment('');
                  }}
                  data-testid="btn-final-approve"
                >
                  Final Approval
                </Button>
              </Space>
            </Col>
          </Row>
        }
      >
        <Spin spinning={reviewLoading}>
          {reviewData && (
            <div>
              <Table
                dataSource={reviewData.items || []}
                columns={reviewItemColumns}
                rowKey="id"
                pagination={false}
                locale={{ emptyText: 'No declared investment items to verify.' }}
              />
            </div>
          )}
        </Spin>
      </Drawer>

      {/* Item Decision Modal */}
      <Modal
        title={`Verify Item: ${itemDecisionModal.item?.description || itemDecisionModal.item?.source_kind}`}
        open={itemDecisionModal.visible}
        onOk={handleConfirmItemDecision}
        onCancel={() => setItemDecisionModal({ visible: false, item: null, action: 'APPROVE' })}
        confirmLoading={deciding}
        okText="Record Decision"
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          {itemDecisionModal.action === 'APPROVE' && (
            <Form.Item label="Verified / Approved Amount (₹)" required>
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                value={decisionAmount}
                onChange={(val) => setDecisionAmount(val)}
                prefix="₹"
                data-testid="modal-verified-amount"
              />
            </Form.Item>
          )}

          <Form.Item
            label="Reviewer Comments"
            required={itemDecisionModal.action === 'DISALLOW'}
            extra={itemDecisionModal.action === 'DISALLOW' ? 'Mandatory explanation for rejection' : 'Optional notes'}
          >
            <Input.TextArea
              rows={3}
              value={decisionComment}
              onChange={(e) => setDecisionComment(e.target.value)}
              placeholder="Enter remarks or justification..."
              data-testid="modal-decision-comment"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Final Decision Modal */}
      <Modal
        title={`Finalize Proof Decision: ${finalDecisionModal.action === 'APPROVE' ? 'Approve' : 'Return to Employee'}`}
        open={finalDecisionModal.visible}
        onOk={handleConfirmFinalDecision}
        onCancel={() => setFinalDecisionModal({ visible: false, action: 'APPROVE' })}
        confirmLoading={finalSubmitting}
        okText={finalDecisionModal.action === 'APPROVE' ? 'Confirm Final Approval' : 'Confirm Return'}
        okButtonProps={{ danger: finalDecisionModal.action === 'RETURN' }}
      >
        <Paragraph>
          {finalDecisionModal.action === 'APPROVE'
            ? 'This will complete proof review and apply the verified investment amounts to employee income tax calculation.'
            : 'This will return the proof to the employee with your feedback for re-submission.'}
        </Paragraph>
        <Form layout="vertical">
          <Form.Item
            label="Officer Remarks"
            required={finalDecisionModal.action === 'RETURN'}
          >
            <Input.TextArea
              rows={3}
              value={finalComment}
              onChange={(e) => setFinalComment(e.target.value)}
              placeholder="Enter overall review notes..."
              data-testid="final-comment-input"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default ProofReviewQueue;
