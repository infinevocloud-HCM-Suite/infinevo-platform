import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Alert, Card, Descriptions, Progress, Tag } from 'antd';
import { formatAmount, formatDate } from '../claims/claimLabels.js';
import { runStatus } from './labels.js';

/** Today in local time as `YYYY-MM-DD`, the shape of `pay_date`. */
function todayIso(now = new Date()) {
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${m}-${d}`;
}

/**
 * The newest non-cancelled run (W-47.5 §5, W-37 §4). Progress only while `COMPUTING`; "Payment
 * due" when the pay date has passed and the run is not `PAID`, which W-37 leaves to the screen.
 */
export function CurrentRunCard({ run, today }) {
  const tag = runStatus(run.status);
  const computing = run.status === 'COMPUTING';
  const now = today ?? todayIso();
  const due = Boolean(run.pay_date) && run.pay_date < now && run.status !== 'PAID';
  const percent =
    computing && run.progress_total > 0 ? Math.floor((run.progress_done * 100) / run.progress_total) : 0;

  return (
    <Card
      title={`Current run: ${run.period}`}
      extra={<Link to={`/payroll/runs/${run.payrun_id}`}>Open run</Link>}
    >
      {due && <Alert type="warning" showIcon message="Payment due" style={{ marginBottom: 16 }} />}
      {computing && (
        <div style={{ marginBottom: 16 }}>
          <Progress percent={percent} aria-label="Compute progress" />
          <span>
            {run.progress_done ?? 0} of {run.progress_total ?? 0} employees computed
          </span>
        </div>
      )}
      <Descriptions column={{ xs: 1, sm: 2, md: 3 }} size="small">
        <Descriptions.Item label="Status">
          <Tag color={tag.color}>{tag.label}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Pay date">{formatDate(run.pay_date)}</Descriptions.Item>
        <Descriptions.Item label="Paid on">{formatDate(run.paid_on)}</Descriptions.Item>
        <Descriptions.Item label="Included">{run.included}</Descriptions.Item>
        <Descriptions.Item label="Skipped">{run.skipped}</Descriptions.Item>
        <Descriptions.Item label="Gross">{formatAmount(run.gross)}</Descriptions.Item>
        <Descriptions.Item label="Deductions">{formatAmount(run.deductions)}</Descriptions.Item>
        <Descriptions.Item label="Net pay">{formatAmount(run.net_pay)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

CurrentRunCard.propTypes = {
  run: PropTypes.shape({
    payrun_id: PropTypes.string,
    period: PropTypes.string,
    status: PropTypes.string,
    pay_date: PropTypes.string,
    paid_on: PropTypes.string,
    included: PropTypes.number,
    skipped: PropTypes.number,
    progress_done: PropTypes.number,
    progress_total: PropTypes.number,
    gross: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    deductions: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    net_pay: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  }).isRequired,
  /** `YYYY-MM-DD`; defaults to today. For tests. */
  today: PropTypes.string,
};
