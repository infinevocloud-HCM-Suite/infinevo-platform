import PropTypes from 'prop-types';
import { Card, Descriptions, Statistic, Typography } from 'antd';
import { skipReasonLabel } from './labels.js';

const { Text } = Typography;

/**
 * Readiness (W-47.5 §5): who is employed today, and what the newest run included and skipped,
 * with the skipped grouped by reason. The "last run" part is hidden when `as_at_run` is null.
 */
export function HeadcountCard({ employees }) {
  const run = employees?.as_at_run;
  const reasons = Object.entries(run?.skipped_by_reason ?? {});
  return (
    <Card title="Employees">
      <Statistic title="Active today" value={employees?.active_today ?? 0} />
      {run && (
        <div style={{ marginTop: 16 }}>
          <Text strong>At the last run ({run.period})</Text>
          <Descriptions column={1} size="small" style={{ marginTop: 8 }}>
            <Descriptions.Item label="Included">{run.included}</Descriptions.Item>
            <Descriptions.Item label="Skipped">{run.skipped}</Descriptions.Item>
          </Descriptions>
          {reasons.length > 0 && (
            <ul aria-label="Skipped by reason" style={{ margin: 0, paddingLeft: 20 }}>
              {reasons.map(([code, count]) => (
                <li key={code}>
                  {skipReasonLabel(code)}: {count}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

HeadcountCard.propTypes = {
  employees: PropTypes.shape({
    active_today: PropTypes.number,
    as_at_run: PropTypes.shape({
      payrun_id: PropTypes.string,
      period: PropTypes.string,
      included: PropTypes.number,
      skipped: PropTypes.number,
      skipped_by_reason: PropTypes.objectOf(PropTypes.number),
    }),
  }),
};
