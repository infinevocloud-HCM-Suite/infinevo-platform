import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, List, Statistic } from 'antd';

/** `team.approvals` (W-48.6 §5): the server's `waiting` count and the oldest five, each linking to its entry. */
export function ApprovalsCard({ approvals }) {
  if (!approvals) return null;
  return (
    <Card title="Waiting for me" extra={<Link to="/approvals">Go to approvals</Link>}>
      <Statistic title="Waiting" value={approvals.waiting} />
      <List
        size="small"
        dataSource={approvals.oldest ?? []}
        rowKey="project_entry_id"
        renderItem={(w) => (
          <List.Item>
            <Link to={`/hrms/timesheet-review/entries/${w.project_entry_id}`}>
              {`${w.employee_name} · ${w.project_name} · week of ${w.week_start}`}
            </Link>
          </List.Item>
        )}
      />
    </Card>
  );
}

ApprovalsCard.propTypes = {
  approvals: PropTypes.shape({
    waiting: PropTypes.number,
    oldest: PropTypes.arrayOf(PropTypes.object),
  }),
};
