import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Space, Typography, Tag, Button, Alert, Spin, Card, Row, Col, Modal, Input, InputNumber, Empty } from 'antd';
import { EyeOutlined, CheckOutlined, StopOutlined, RollbackOutlined, PaperClipOutlined } from '@ant-design/icons';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatMoney, formatDate } from '@shared/ui/format.js';
import { readError } from '../tax/apiError';
import { formatFyDisplay } from '../tax/financialYear';
import { proofService } from './proofService';
import { proofStatus, sourceKindLabel, formatBytes } from './proofLabels';
import { openReceipt } from './openReceipt';

const { Text, Paragraph } = Typography;

/** An item's state as the reviewer reads it. */
const ITEM_STATUSES = {
  PENDING: { label: 'Pending', color: 'warning' },
  APPROVED: { label: 'Verified', color: 'success' },
  DISALLOWED: { label: 'Disallowed', color: 'error' },
  RETURNED: { label: 'Returned', color: 'warning' },
};

const ACTION_TITLES = {
  APPROVE: 'Verify item',
  DISALLOW: 'Disallow item',
  RETURN: 'Return item to the employee',
  FINAL_APPROVE: 'Approve the proof',
  FINAL_RETURN: 'Return the proof to the employee',
};

/** A decimal string or number at scale 4, as an integer, so two amounts compare without a float. */
function scaled(value) {
  const text = String(value ?? '').trim();
  const match = text.match(/^(\d+)(?:\.(\d*))?$/);
  if (!match) return null;
  const fraction = (match[2] || '').padEnd(4, '0').slice(0, 4);
  return BigInt(match[1]) * 10000n + BigInt(fraction || '0');
}

/**
 * Why a decision cannot be sent, or null (W-34.2 §3, the wrapper's 400 rules, checked here first so
 * the reviewer is told before the round trip).
 */
export function decisionProblem({ action, amount, comment, claimed, commentMandatory }) {
  const hasComment = Boolean(comment && comment.trim());
  if (action === 'APPROVE') {
    const value = scaled(amount);
    const cap = scaled(claimed);
    if (value === null) return 'Enter the verified amount';
    if (cap !== null && value > cap) return 'The verified amount cannot be more than the amount claimed';
    if (cap !== null && value < cap && !hasComment) return 'Say why less than the claim is verified';
  }
  if ((action === 'DISALLOW' || action === 'RETURN' || action === 'FINAL_RETURN') && !hasComment) {
    return 'A reason is required; the employee reads it';
  }
  if (commentMandatory && !hasComment) return 'A comment is required for every decision this year';
  return null;
}

/**
 * One employee's proof, opened from the queue (W-47.3c §3). A holder of `payroll.proof.review` decides
 * each item and then the proof; anyone else with `payroll.proof.read` reads it.
 */
export function ProofReviewDrawer({ row, fy, canReview, onClose, onDecided }) {
  const [proof, setProof] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [decision, setDecision] = useState(null);
  const [amount, setAmount] = useState(null);
  const [comment, setComment] = useState('');
  const [problem, setProblem] = useState(null);
  const [sending, setSending] = useState(false);

  const open = Boolean(row);

  const load = useCallback(async () => {
    if (!row) return;
    setLoading(true);
    setError(null);
    try {
      const data = canReview ? await proofService.review(row.proof_id) : await proofService.of(row.employee_id, fy);
      setProof(data);
    } catch (err) {
      setProof(null);
      setError(readError(err, 'Failed to load this proof').message);
    } finally {
      setLoading(false);
    }
  }, [row, fy, canReview]);

  useEffect(() => {
    setProof(null);
    load();
  }, [load]);

  const startDecision = (action, item) => {
    setDecision({ action, item });
    setAmount(action === 'APPROVE' ? item?.claimed_amount ?? null : null);
    setComment('');
    setProblem(null);
  };

  const sendDecision = async () => {
    const { action, item } = decision;
    const found = decisionProblem({
      action,
      amount,
      comment,
      claimed: item?.claimed_amount,
      commentMandatory: Boolean(proof?.comment_mandatory),
    });
    if (found) {
      setProblem(found);
      return;
    }
    setSending(true);
    try {
      const text = comment && comment.trim() ? comment.trim() : null;
      if (action === 'FINAL_APPROVE' || action === 'FINAL_RETURN') {
        await proofService.decideFinal(proof.id, {
          action: action === 'FINAL_APPROVE' ? 'APPROVE' : 'RETURN',
          comment: text,
        });
        successMsg(
          action === 'FINAL_APPROVE' ? 'Proof Approved' : 'Proof Returned',
          action === 'FINAL_APPROVE'
            ? 'The verified amounts now count toward the employee\'s tax.'
            : 'The employee can change the proof and submit it again.',
        );
      } else {
        await proofService.decideItem(proof.id, item.id, {
          action,
          approved_amount: action === 'APPROVE' ? String(amount) : null,
          comment: text,
        });
      }
      setDecision(null);
      await load();
      if (onDecided) onDecided();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSending(false);
    }
  };

  const viewReceipt = async (item, doc) => {
    try {
      await openReceipt(
        () => proofService.receiptOf(row.employee_id, fy, item.id, doc.document_id),
        doc,
      );
    } catch (err) {
      errorMsg(err);
    }
  };

  const status = proofStatus(proof?.status);
  const underReview = proof?.status === 'SUBMITTED';
  const items = Array.isArray(proof?.items) ? proof.items : [];
  const canFinal = canReview && underReview && Boolean(proof?.final_step_id);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={760}
      destroyOnClose
      title={
        row ? (
          <Space direction="vertical" size={0}>
            <Text strong>{row.name || 'Proof of investment'}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {row.number ? `${row.number} · ` : ''}FY {formatFyDisplay(fy)}
            </Text>
          </Space>
        ) : null
      }
      extra={proof && <Tag color={status.color}>{status.label}</Tag>}
      footer={
        canFinal ? (
          <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
            <Button icon={<RollbackOutlined />} onClick={() => startDecision('FINAL_RETURN')}>
              Return proof
            </Button>
            <Button type="primary" icon={<CheckOutlined />} onClick={() => startDecision('FINAL_APPROVE')}>
              Approve proof
            </Button>
          </Space>
        ) : null
      }
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      <Spin spinning={loading}>
        {proof && (
          <>
            {!canReview && (
              <Alert
                type="info"
                showIcon
                message="Read only"
                description="Verifying a proof needs the review permission."
                style={{ marginBottom: 16 }}
              />
            )}
            {proof.reviewer_note && (
              <Paragraph>
                <Text type="secondary">Reviewer&apos;s note on the proof: </Text>
                {proof.reviewer_note}
              </Paragraph>
            )}
            {proof.submitted_at && (
              <Paragraph type="secondary">Submitted {formatDate(proof.submitted_at)}</Paragraph>
            )}
            {items.length === 0 && <Empty description="No items on this proof." />}
            {items.map((item) => {
              const itemState = ITEM_STATUSES[item.status] || { label: item.status, color: 'default' };
              const decidable = canReview && underReview && Boolean(item.open_step_id);
              const documents = Array.isArray(item.documents) ? item.documents : [];
              return (
                <Card
                  key={item.id}
                  size="small"
                  style={{ marginBottom: 12 }}
                  data-testid={`review-item-${item.id}`}
                  title={
                    <Space direction="vertical" size={0}>
                      <Text strong>{item.description || sourceKindLabel(item.source_kind)}</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {sourceKindLabel(item.source_kind)}
                      </Text>
                    </Space>
                  }
                  extra={<Tag color={itemState.color}>{itemState.label}</Tag>}
                >
                  <Row gutter={[16, 8]}>
                    <Col xs={8}>
                      <Text type="secondary">Declared</Text>
                      <div>{formatMoney(item.declared_amount)}</div>
                    </Col>
                    <Col xs={8}>
                      <Text type="secondary">Claimed</Text>
                      <div>{formatMoney(item.claimed_amount)}</div>
                    </Col>
                    <Col xs={8}>
                      <Text type="secondary">Verified</Text>
                      <div>{formatMoney(item.approved_amount)}</div>
                    </Col>
                  </Row>
                  {item.employee_note && (
                    <Paragraph style={{ marginTop: 8, marginBottom: 0 }}>
                      <Text type="secondary">Employee&apos;s note: </Text>
                      {item.employee_note}
                    </Paragraph>
                  )}
                  {item.reviewer_note && (
                    <Paragraph style={{ marginTop: 8, marginBottom: 0 }}>
                      <Text type="secondary">Reason: </Text>
                      {item.reviewer_note}
                    </Paragraph>
                  )}
                  <div style={{ marginTop: 8 }}>
                    {documents.length === 0 ? (
                      <Text type="secondary">No receipt attached.</Text>
                    ) : (
                      documents.map((doc) => (
                        <Space key={doc.document_id} style={{ display: 'flex', padding: '4px 0' }}>
                          <PaperClipOutlined />
                          <Text>{doc.file_name}</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {formatBytes(doc.size_bytes)}
                          </Text>
                          <Button size="small" icon={<EyeOutlined />} onClick={() => viewReceipt(item, doc)}>
                            Open receipt
                          </Button>
                        </Space>
                      ))
                    )}
                  </div>
                  {decidable && (
                    <Space wrap style={{ marginTop: 12 }}>
                      <Button type="primary" icon={<CheckOutlined />} onClick={() => startDecision('APPROVE', item)}>
                        Verify
                      </Button>
                      <Button danger icon={<StopOutlined />} onClick={() => startDecision('DISALLOW', item)}>
                        Disallow
                      </Button>
                      <Button icon={<RollbackOutlined />} onClick={() => startDecision('RETURN', item)}>
                        Return
                      </Button>
                    </Space>
                  )}
                </Card>
              );
            })}
          </>
        )}
      </Spin>

      <Modal
        open={Boolean(decision)}
        title={decision ? ACTION_TITLES[decision.action] : ''}
        onCancel={() => setDecision(null)}
        onOk={sendDecision}
        okText="Confirm"
        confirmLoading={sending}
        destroyOnClose
      >
        {decision?.action === 'RETURN' && (
          <Paragraph type="secondary">
            Returning an item sends the whole proof back to the employee to correct and resubmit.
          </Paragraph>
        )}
        {decision?.action === 'APPROVE' && (
          <div style={{ marginBottom: 12 }}>
            <Text>Verified amount (claimed {formatMoney(decision.item?.claimed_amount)})</Text>
            <InputNumber
              stringMode
              min="0"
              precision={2}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="Verified amount"
            />
          </div>
        )}
        <Text>
          {decision?.action === 'APPROVE' || decision?.action === 'FINAL_APPROVE' ? 'Comment' : 'Reason'}
        </Text>
        <Input.TextArea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={1000}
          rows={3}
          aria-label="Decision comment"
        />
        {problem && (
          <Alert type="error" showIcon message={problem} style={{ marginTop: 12 }} data-testid="decision-problem" />
        )}
      </Modal>
    </Drawer>
  );
}

ProofReviewDrawer.propTypes = {
  row: PropTypes.shape({
    proof_id: PropTypes.string,
    employee_id: PropTypes.string,
    name: PropTypes.string,
    number: PropTypes.string,
  }),
  fy: PropTypes.string.isRequired,
  canReview: PropTypes.bool,
  onClose: PropTypes.func.isRequired,
  onDecided: PropTypes.func,
};

export default ProofReviewDrawer;
