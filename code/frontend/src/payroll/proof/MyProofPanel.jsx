import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Space, Typography, Tag, Alert, Button, Popconfirm, Spin, Empty } from 'antd';
import { SendOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatDate } from '@shared/ui/format.js';
import { readError } from '../tax/apiError';
import { formatFyDisplay } from '../tax/financialYear';
import { proofService } from './proofService';
import { proofStatus } from './proofLabels';
import { ProofItemCard } from './ProofItemCard';

const { Text, Paragraph } = Typography;

/**
 * The employee's proof of investment for one financial year (W-47.3c §3), a tab on the tax
 * declaration page. The server decides what may change (`editable`) and when (`proof_open`); the
 * screen only follows it.
 */
export function MyProofPanel({ fy }) {
  const [proof, setProof] = useState(null);
  const [loading, setLoading] = useState(false);
  const [notSubmitted, setNotSubmitted] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await proofService.own(fy);
      setProof(data);
      setNotSubmitted(false);
    } catch (err) {
      const info = readError(err, 'Failed to load your proof of investment');
      setProof(null);
      if (info.code === 'NOT_SUBMITTED') {
        setNotSubmitted(true);
      } else {
        setNotSubmitted(false);
        setError(info.message);
      }
    } finally {
      setLoading(false);
    }
  }, [fy]);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async () => {
    setSubmitting(true);
    try {
      await proofService.submit(fy);
      successMsg('Proof Submitted', 'Your proof of investment has been sent for verification.');
    } catch (err) {
      errorMsg(err);
    } finally {
      setSubmitting(false);
      await load();
    }
  };

  if (notSubmitted) {
    return (
      <Alert
        type="info"
        showIcon
        message="Submit your tax declaration first"
        description={`Proof of investment opens once your declaration for FY ${formatFyDisplay(fy)} is submitted. Each declared item then asks for its receipt here.`}
        data-testid="proof-not-submitted"
      />
    );
  }

  const items = Array.isArray(proof?.items) ? proof.items : [];
  const status = proofStatus(proof?.status);
  const editable = Boolean(proof?.editable);

  return (
    <Card
      variant="borderless"
      className="saas-card"
      data-testid="my-proof-panel"
      title={
        <Space>
          <SafetyCertificateOutlined />
          <span>Proof of Investment</span>
        </Space>
      }
      extra={
        proof && (
          <Space wrap>
            <Tag color={status.color} data-testid="proof-status">{status.label}</Tag>
            <Tag color={proof.proof_open ? 'green' : 'default'}>
              {proof.proof_open ? 'Proof window open' : 'Proof window closed'}
            </Tag>
            <Button size="small" icon={<ReloadOutlined />} onClick={load} aria-label="Refresh" />
          </Space>
        )
      }
    >
      {error && (
        <Alert type="error" showIcon message="Error" description={error} style={{ marginBottom: 16 }} />
      )}
      <Spin spinning={loading}>
        {proof && (
          <>
            <Paragraph type="secondary">
              {proof.due_date ? `Due by ${formatDate(proof.due_date)}. ` : 'No due date is set yet. '}
              {proof.attachment_mandatory
                ? 'Every item you claim needs at least one receipt.'
                : 'Receipts are optional for this year.'}
            </Paragraph>

            {proof.status === 'REJECTED' && proof.reviewer_note && (
              <Alert
                type="warning"
                showIcon
                message="Your proof was returned"
                description={proof.reviewer_note}
                style={{ marginBottom: 16 }}
                data-testid="proof-returned-note"
              />
            )}
            {!editable && proof.status !== 'SUBMITTED' && proof.status !== 'APPROVED' && (
              <Alert
                type="warning"
                showIcon
                message="The proof window is closed"
                description="Receipts cannot be added or changed until the payroll team opens the window."
                style={{ marginBottom: 16 }}
              />
            )}

            {items.length === 0 ? (
              <Empty description="Nothing on your declaration needs proof." />
            ) : (
              items.map((item) => (
                <ProofItemCard
                  key={item.id}
                  fy={fy}
                  item={item}
                  proofStatusCode={proof.status}
                  editable={editable}
                  onChanged={load}
                />
              ))
            )}

            {editable && items.length > 0 && (
              <div style={{ textAlign: 'right', marginTop: 8 }}>
                <Popconfirm
                  title="Submit proof of investment?"
                  description="Once submitted, the payroll team verifies each item. You can change it again only if it is returned."
                  okText="Yes, submit"
                  cancelText="Cancel"
                  onConfirm={submit}
                >
                  <Button type="primary" icon={<SendOutlined />} loading={submitting} data-testid="proof-submit">
                    Submit proof
                  </Button>
                </Popconfirm>
              </div>
            )}
            {proof.submitted_at && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Submitted {formatDate(proof.submitted_at)}
                {proof.decided_at ? ` · decided ${formatDate(proof.decided_at)}` : ''}
              </Text>
            )}
          </>
        )}
      </Spin>
    </Card>
  );
}

MyProofPanel.propTypes = {
  fy: PropTypes.string.isRequired,
};

export default MyProofPanel;
