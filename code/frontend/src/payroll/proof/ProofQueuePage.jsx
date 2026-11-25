import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card, Tabs, Select, Space, Typography, Tag, Alert } from 'antd';
import { AuditOutlined, TeamOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { formatDate } from '@shared/ui/format.js';
import { currentFy, fyOptions, formatFyDisplay, normalizeFy } from '../tax/financialYear';
import { readError } from '../tax/apiError';
import { proofService } from './proofService';
import { proofStatus } from './proofLabels';
import { ProofTable } from './ProofTable';
import { ProofChaseList } from './ProofChaseList';
import { ProofReviewDrawer } from './ProofReviewDrawer';

const { Text } = Typography;

const COUNT_ORDER = ['NOT_STARTED', 'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'];

/**
 * `/payroll/proof-of-investment` (W-47.3c §3): the verification queue and the chase list for one
 * financial year, and the drawer that opens one employee's proof. Mounted on `payroll.proof.read`, the
 * menu item's action; deciding needs `payroll.proof.review`, which the drawer checks.
 */
export function ProofQueuePage() {
  const canRead = useCan('payroll.proof.read');
  const canReview = useCan('payroll.proof.review');
  const [fy, setFy] = useState(currentFy());
  const [tab, setTab] = useState('queue');
  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState(null);
  const [linkNotice, setLinkNotice] = useState(null);
  const [openRow, setOpenRow] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const { proofId } = useParams();

  // Opened from the approvals inbox with the proof id (itemRoutes.js): find whose proof and which
  // year, then open it in the drawer. Only a reviewer can read a proof by its id.
  useEffect(() => {
    if (!proofId) return undefined;
    if (!canReview) {
      setLinkNotice('Only a proof reviewer can open this proof. The list below is read-only.');
      return undefined;
    }
    let cancelled = false;
    proofService
      .review(proofId)
      .then((proof) => {
        if (cancelled || !proof) return;
        setFy(normalizeFy(proof.financial_year));
        setOpenRow({ proof_id: proof.id, employee_id: proof.employee_id });
      })
      .catch((err) => {
        if (!cancelled) setSummaryError(readError(err, 'Failed to open this proof').message);
      });
    return () => {
      cancelled = true;
    };
  }, [proofId, canReview]);

  const loadSummary = useCallback(async () => {
    if (!canRead) return;
    setSummaryError(null);
    try {
      setSummary(await proofService.summary(fy));
    } catch (err) {
      setSummary(null);
      setSummaryError(readError(err, 'Failed to load the proof summary').message);
    }
  }, [fy, canRead]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  if (!canRead) return <NotEntitled />;

  const counts = summary?.counts || {};
  const decided = () => {
    setReloadKey((k) => k + 1);
    loadSummary();
  };

  return (
    <div className="saas-page-container fadeSlideUp">
      <PageHeader
        title="Proof of investment"
        subtitle={`Financial Year ${formatFyDisplay(fy)}`}
        actions={
          <Space>
            <Text strong>FY:</Text>
            <Select
              value={fy}
              onChange={(v) => {
                setFy(v);
                setOpenRow(null);
              }}
              options={fyOptions().map((o) => ({ value: o.value, label: o.label }))}
              style={{ width: 130 }}
              aria-label="Financial year"
            />
          </Space>
        }
      />

      {linkNotice && (
        <Alert type="info" showIcon message={linkNotice} style={{ marginBottom: 12 }} data-testid="proof-link-notice" />
      )}
      {summaryError && <Alert type="warning" showIcon message={summaryError} style={{ marginBottom: 12 }} />}
      {summary && (
        <Space wrap style={{ marginBottom: 12 }} data-testid="proof-summary">
          {COUNT_ORDER.map((code) => {
            const s = proofStatus(code);
            return (
              <Tag key={code} color={s.color}>
                {s.label}: {counts[code] ?? 0}
              </Tag>
            );
          })}
          <Text type="secondary">
            {summary.proof_open ? 'Proof window open' : 'Proof window closed'}
            {summary.due_date ? ` · due ${formatDate(summary.due_date)}` : ''}
          </Text>
        </Space>
      )}

      <Card variant="borderless" className="saas-card">
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'queue',
              label: (
                <span>
                  <AuditOutlined /> Verification queue
                </span>
              ),
              children: (
                <ProofTable
                  key={`queue-${fy}`}
                  fy={fy}
                  defaultStatus="SUBMITTED"
                  onOpen={setOpenRow}
                  openLabel={canReview ? 'Review' : 'View'}
                  reloadKey={reloadKey}
                />
              ),
            },
            {
              key: 'chase',
              label: (
                <span>
                  <TeamOutlined /> Chase list
                </span>
              ),
              children: <ProofChaseList key={`chase-${fy}`} fy={fy} dueDate={summary?.due_date} onOpen={setOpenRow} />,
            },
          ]}
        />
      </Card>

      <ProofReviewDrawer
        row={openRow}
        fy={fy}
        canReview={canReview}
        onClose={() => setOpenRow(null)}
        onDecided={decided}
      />
    </div>
  );
}

export default ProofQueuePage;
