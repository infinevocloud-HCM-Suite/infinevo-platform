import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Table,
  Button,
  Upload,
  Tag,
  Space,
  Typography,
  Alert,
  Spin,
  InputNumber,
  Popconfirm,
  Row,
  Col,
  Card,
  Modal,
} from 'antd';
import {
  UploadOutlined,
  DeleteOutlined,
  FilePdfOutlined,
  SendOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import { proofService } from './proofService';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { readError } from './apiError';
import { formatFyDisplay } from './financialYear';

const { Text, Title, Paragraph } = Typography;

export function ProofUploadSection({ fy, onRefresh }) {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [proof, setProof] = useState(null);
  const [error, setError] = useState(null);
  const [savingItem, setSavingItem] = useState({});

  const loadProof = useCallback(async () => {
    if (!fy) return;
    setLoading(true);
    setError(null);
    try {
      const data = await proofService.getOwn(fy);
      setProof(data);
    } catch (err) {
      setError(readError(err, 'Failed to load proof of investment').message);
    } finally {
      setLoading(false);
    }
  }, [fy]);

  useEffect(() => {
    loadProof();
  }, [loadProof]);

  const handleClaimedAmountChange = async (item, newAmount) => {
    if (newAmount === undefined || newAmount === null) return;
    setSavingItem((prev) => ({ ...prev, [item.id]: true }));
    try {
      await proofService.updateItemOwn(fy, item.id, newAmount);
      successMsg('Saved', 'Claimed amount updated');
      loadProof();
      if (onRefresh) onRefresh();
    } catch (err) {
      errorMsg(readError(err, 'Failed to update claimed amount'));
    } finally {
      setSavingItem((prev) => ({ ...prev, [item.id]: false }));
    }
  };

  const handleFileUpload = async (item, file) => {
    try {
      await proofService.attachDocumentOwn(fy, item.id, file);
      successMsg('Uploaded', `${file.name} attached successfully`);
      loadProof();
    } catch (err) {
      errorMsg(readError(err, 'Failed to attach file'));
    }
    return false; // prevent default antd upload POST
  };

  const handleDeleteDocument = async (item, doc) => {
    try {
      await proofService.detachDocumentOwn(fy, item.id, doc.document_id);
      successMsg('Removed', 'Document removed');
      loadProof();
    } catch (err) {
      errorMsg(readError(err, 'Failed to delete document'));
    }
  };

  const handleSubmitProof = async () => {
    setSubmitting(true);
    try {
      await proofService.submitOwn(fy);
      successMsg('Submitted', 'Proof of investment submitted for review');
      loadProof();
      if (onRefresh) onRefresh();
    } catch (err) {
      errorMsg(readError(err, 'Failed to submit proof'));
    } finally {
      setSubmitting(false);
    }
  };

  const canEdit = Boolean(proof && proof.editable && proof.proof_open);
  const items = proof?.items || [];

  const columns = [
    {
      title: 'Item / Category',
      dataIndex: 'description',
      key: 'description',
      render: (text, record) => (
        <div>
          <Text strong>{text || record.source_kind}</Text>
          <br />
          <Tag color="geekblue" style={{ marginTop: 4 }}>
            {record.source_kind}
          </Tag>
        </div>
      ),
    },
    {
      title: 'Declared',
      dataIndex: 'declared_amount',
      key: 'declared_amount',
      width: 130,
      render: (val) => <Text>₹{Number(val || 0).toLocaleString('en-IN')}</Text>,
    },
    {
      title: 'Claimed (Actual)',
      dataIndex: 'claimed_amount',
      key: 'claimed_amount',
      width: 170,
      render: (val, record) => {
        if (!canEdit) {
          return <Text strong>₹{Number(val || 0).toLocaleString('en-IN')}</Text>;
        }
        return (
          <InputNumber
            min={0}
            style={{ width: '100%' }}
            defaultValue={val !== null ? val : record.declared_amount}
            onBlur={(e) => {
              const parsed = Number(e.target.value.replace(/[^0-9.]/g, ''));
              if (!Number.isNaN(parsed) && parsed !== Number(val)) {
                handleClaimedAmountChange(record, parsed);
              }
            }}
            prefix="₹"
            disabled={savingItem[record.id]}
            data-testid={`claimed-input-${record.id}`}
          />
        );
      },
    },
    {
      title: 'Receipts / Proofs',
      key: 'documents',
      render: (_, record) => {
        const docs = record.documents || [];
        return (
          <div>
            <Space orientation="vertical" size="small" style={{ width: '100%' }}>
              {docs.map((doc) => (
                <Space key={doc.document_id} size="small" wrap>
                  <Tag icon={<FilePdfOutlined />} color="blue">
                    <a
                      href={`/api/v1/me/proof-of-investment/${encodeURIComponent(fy)}/items/${record.id}/documents/${doc.document_id}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {doc.file_name}
                    </a>
                  </Tag>
                  {canEdit && (
                    <Popconfirm
                      title="Remove document?"
                      onConfirm={() => handleDeleteDocument(record, doc)}
                      okText="Remove"
                      cancelText="Cancel"
                    >
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        data-testid={`btn-del-doc-${doc.document_id}`}
                      />
                    </Popconfirm>
                  )}
                </Space>
              ))}

              {canEdit && (
                <Upload
                  showUploadList={false}
                  beforeUpload={(file) => handleFileUpload(record, file)}
                  accept=".pdf,.png,.jpg,.jpeg"
                >
                  <Button
                    size="small"
                    icon={<UploadOutlined />}
                    data-testid={`upload-btn-${record.id}`}
                  >
                    Upload Proof (PDF/Image)
                  </Button>
                </Upload>
              )}
            </Space>
          </div>
        );
      },
    },
    {
      title: 'Verification Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status, record) => {
        if (status === 'VERIFIED') {
          return (
            <div>
              <Tag color="success" icon={<CheckCircleOutlined />}>
                VERIFIED
              </Tag>
              {record.approved_amount !== null && (
                <div style={{ fontSize: 12, marginTop: 4 }}>
                  <Text type="secondary">Approved:</Text> ₹{Number(record.approved_amount).toLocaleString('en-IN')}
                </div>
              )}
            </div>
          );
        }
        if (status === 'REJECTED') {
          return (
            <div>
              <Tag color="error" icon={<CloseCircleOutlined />}>
                REJECTED
              </Tag>
              {record.reviewer_note && (
                <div style={{ fontSize: 12, marginTop: 4, color: '#cf1322' }}>
                  Note: {record.reviewer_note}
                </div>
              )}
            </div>
          );
        }
        return (
          <Tag color="warning" icon={<ClockCircleOutlined />}>
            PENDING
          </Tag>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '8px 0' }}>
      {error && (
        <Alert
          message="Error"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {proof && (
        <Card
          size="small"
          style={{ marginBottom: 16, background: '#fafafa' }}
          bodyStyle={{ padding: '12px 16px' }}
        >
          <Row justify="space-between" align="middle" wrap gutter={[12, 12]}>
            <Col>
              <Space align="center" wrap>
                <Text strong>Overall Proof Status:</Text>
                <Tag
                  color={
                    proof.status === 'APPROVED'
                      ? 'success'
                      : proof.status === 'REJECTED'
                      ? 'error'
                      : proof.status === 'SUBMITTED'
                      ? 'processing'
                      : 'default'
                  }
                >
                  {proof.status || 'DRAFT'}
                </Tag>
                {proof.due_date && (
                  <Text type="secondary">
                    Due Date: <Text strong>{proof.due_date}</Text>
                  </Text>
                )}
                <Tag color={proof.proof_open ? 'green' : 'default'}>
                  {proof.proof_open ? 'Proof Window Open' : 'Proof Window Closed'}
                </Tag>
              </Space>
            </Col>
            <Col>
              {canEdit && (
                <Popconfirm
                  title="Submit proofs for review?"
                  description="Once submitted, your proofs will be sent to the payroll officer for verification."
                  onConfirm={handleSubmitProof}
                  okText="Submit"
                  cancelText="Cancel"
                >
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    loading={submitting}
                    disabled={loading}
                    data-testid="btn-submit-proof"
                  >
                    Submit Proofs for Verification
                  </Button>
                </Popconfirm>
              )}
            </Col>
          </Row>
        </Card>
      )}

      <Spin spinning={loading}>
        <Table
          dataSource={items}
          columns={columns}
          rowKey="id"
          pagination={false}
          locale={{ emptyText: 'No declared investment items found for this financial year.' }}
        />
      </Spin>
    </div>
  );
}

ProofUploadSection.propTypes = {
  fy: PropTypes.string.isRequired,
  onRefresh: PropTypes.func,
};

export default ProofUploadSection;
