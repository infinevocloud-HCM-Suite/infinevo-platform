import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, Table, Tag } from 'antd';
import { STATUS_COLOR, STATUS_LABEL } from '../timesheet/weekGrid.js';

/** `me.timesheets` (W-48.6 §5): this week and last; hours as sent. */
export function TimesheetsCard({ timesheets }) {
  if (!timesheets) return null;
  const rows = [
    { key: 'this', label: 'This week', week: timesheets.this_week },
    { key: 'last', label: 'Last week', week: timesheets.last_week },
  ].filter((r) => r.week);
  const columns = [
    {
      title: 'Week',
      key: 'week',
      render: (_, r) => (
        <Link to={`/hrms/timesheets/week/${r.week.week_start}`}>{`${r.label} (${r.week.week_start})`}</Link>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_, r) =>
        r.week.timesheet_id && r.week.status ? (
          <Tag color={STATUS_COLOR[r.week.status]}>{STATUS_LABEL[r.week.status] ?? r.week.status}</Tag>
        ) : (
          <Tag>Not started</Tag>
        ),
    },
    { title: 'Hours', key: 'hours', render: (_, r) => r.week.hours },
  ];
  return (
    <Card title="Timesheets">
      <Table size="small" pagination={false} rowKey="key" columns={columns} dataSource={rows} />
    </Card>
  );
}

TimesheetsCard.propTypes = {
  timesheets: PropTypes.shape({
    this_week: PropTypes.object,
    last_week: PropTypes.object,
  }),
};
