import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Space, Typography, Tag, InputNumber, Input, Button, Upload, Popconfirm, Alert, Row, Col } from 'antd';
import { InboxOutlined, EyeOutlined, DeleteOutlined, PaperClipOutlined, SaveOutlined } from '@ant-design/icons';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { formatMoney, formatDate } from '@shared/ui/format.js';
import { proofService } from './proofService';
import { itemStatus, sourceKindLabel, receiptProblem, formatBytes, ACCEPTED_RECEIPT_TYPES } from './proofLabels';
import { openReceipt } from './openReceipt';

const { Text } = Typography;

/**
 * One declared item on the employee's proof (W-47.3c §3): the claim, the receipts and the reviewer's
 * answer. One component for every kind of item, so no section repeats another (DEBT-026).
 */
export function ProofItemCard({ fy, item, proofStatusCode, editable, onChanged }) {
  const [claimed, setClaimed] = useState(item.claimed_amount ?? null);
  const [note, setNote] = useState(item.employee_note ?? '');
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    setClaimed(item.claimed_amount ?? null);
    setNote(item.employee_note ?? '');
  }, [item.claimed_amount, item.employee_note]);

  const status = itemStatus(item.status, proofStatusCode);
  const documents = Array.isArray(item.documents) ? item.documents : [];
  const dirty =
    String(claimed ?? '') !== String(item.claimed_amount ?? '') || (note || '') !== (item.employee_note || '');
  const showReason = Boolean(item.reviewer_note) && item.status !== 'PENDING';

  const saveClaim = async () => {
    setSaving(true);
    try {
      await proofService.updateItem(fy, item.id, {
        claimed_amount: claimed === null || claimed === '' ? null : String(claimed),
        employee_note: note ? note : null,
      });
      await onChanged();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  const upload = async (file) => {
    const problem = receiptProblem(file);
    if (problem) {
      errorMsg('Receipt not accepted', problem);
      return;
    }
    setUploading(true);
    try {
      await proofService.attach(fy, item.id, file);
      await onChanged();
    } catch (err) {
      errorMsg(err);
    } finally {
      setUploading(false);
    }
  };

  const remove = async (doc) => {
    try {
      await proofService.detach(fy, item.id, doc.document_id);
      await onChanged();
    } catch (err) {
      errorMsg(err);
    }
  };

  const view = async (doc) => {
    try {
      await openReceipt(() => proofService.ownReceipt(fy, item.id, doc.document_id), doc);
    } catch (err) {
      errorMsg(err);
    }
  };

  return (
    <Card
      size="small"
      className="saas-card"
      data-testid={`proof-item-${item.id}`}
      title={
        <Space direction="vertical" size={0}>
          <Text strong>{item.description || sourceKindLabel(item.source_kind)}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {sourceKindLabel(item.source_kind)}
          </Text>
        </Space>
      }
      extra={<Tag color={status.color} data-testid="proof-item-status">{status.label}</Tag>}
      style={{ marginBottom: 12 }}
    >
      {showReason && (
        <Alert
          type={item.status === 'APPROVED' ? 'info' : 'warning'}
          showIcon
          message="Reviewer's note"
          description={item.reviewer_note}
          style={{ marginBottom: 12 }}
          data-testid="proof-item-reason"
        />
      )}

      <Row gutter={[16, 12]}>
        <Col xs={24} sm={8}>
          <Text type="secondary">Declared</Text>
          <div>
            <Text strong>{formatMoney(item.declared_amount)}</Text>
          </div>
        </Col>
        <Col xs={24} sm={8}>
          <Text type="secondary">Claimed with proof</Text>
          <div>
            {editable ? (
              <InputNumber
                stringMode
                min="0"
                precision={2}
                value={claimed}
                onChange={setClaimed}
                style={{ width: '100%' }}
                aria-label="Claimed amount"
                data-testid="proof-claimed-input"
              />
            ) : (
              <Text strong>{formatMoney(item.claimed_amount)}</Text>
            )}
          </div>
        </Col>
        <Col xs={24} sm={8}>
          <Text type="secondary">Verified</Text>
          <div>
            <Text strong>{formatMoney(item.approved_amount)}</Text>
          </div>
        </Col>
      </Row>

      {editable ? (
        <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
          <Input.TextArea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={1000}
            rows={2}
            placeholder="Note for the reviewer (optional)"
            aria-label="Note for the reviewer"
          />
          <Button
            icon={<SaveOutlined />}
            onClick={saveClaim}
            loading={saving}
            disabled={!dirty}
            data-testid="proof-save-claim"
          >
            Save claim
          </Button>
        </Space>
      ) : (
        item.employee_note && (
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">Your note: </Text>
            <Text>{item.employee_note}</Text>
          </div>
        )
      )}

      <div style={{ marginTop: 16 }}>
        <Text strong>Receipts</Text>
        {documents.length === 0 ? (
          <div>
            <Text type="secondary">No receipt attached.</Text>
          </div>
        ) : (
          documents.map((doc) => (
            <Row key={doc.document_id} align="middle" justify="space-between" style={{ padding: '6px 0' }}>
              <Space>
                <PaperClipOutlined />
                <Text>{doc.file_name}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {formatBytes(doc.size_bytes)} · {formatDate(doc.uploaded_at)}
                </Text>
              </Space>
              <Space>
                <Button size="small" icon={<EyeOutlined />} onClick={() => view(doc)}>
                  View
                </Button>
                {editable && (
                  <Popconfirm
                    title="Remove this receipt?"
                    okText="Remove"
                    cancelText="Keep"
                    onConfirm={() => remove(doc)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />} data-testid="proof-remove-receipt">
                      Remove
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            </Row>
          ))
        )}
      </div>

      {editable && (
        <div style={{ marginTop: 12 }} data-testid="proof-dropzone">
          <Upload.Dragger
            accept={ACCEPTED_RECEIPT_TYPES}
            multiple={false}
            showUploadList={false}
            disabled={uploading}
            beforeUpload={(file) => {
              upload(file);
              return Upload.LIST_IGNORE;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">
              {uploading
                ? 'Uploading…'
                : item.status === 'RETURNED'
                  ? 'Drop the replacement receipt here, or click to choose'
                  : 'Drop a receipt here, or click to choose'}
            </p>
            <p className="ant-upload-hint">PDF, JPG or PNG, up to 10 MB</p>
          </Upload.Dragger>
        </div>
      )}
    </Card>
  );
}

ProofItemCard.propTypes = {
  fy: PropTypes.string.isRequired,
  item: PropTypes.shape({
    id: PropTypes.string.isRequired,
    source_kind: PropTypes.string,
    description: PropTypes.string,
    declared_amount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    claimed_amount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    approved_amount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    status: PropTypes.string,
    employee_note: PropTypes.string,
    reviewer_note: PropTypes.string,
    documents: PropTypes.array,
  }).isRequired,
  proofStatusCode: PropTypes.string,
  editable: PropTypes.bool,
  onChanged: PropTypes.func.isRequired,
};

export default ProofItemCard;
