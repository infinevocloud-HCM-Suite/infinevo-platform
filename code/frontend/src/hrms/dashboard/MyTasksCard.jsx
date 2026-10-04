import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, Col, Row, Statistic, Table, Typography } from 'antd';

/** Overdue = due date before the reply's `as_of` (ISO dates compare as strings); never the browser's date. */
export const isOverdue = (dueDate, asOf) => Boolean(dueDate && asOf && dueDate < asOf);

/** `me.tasks` (W-48.6 §5): counts are the server's; overdue rows in red. */
export function MyTasksCard({ tasks, asOf }) {
  if (!tasks) return null;
  const columns = [
    {
      title: 'Task',
      key: 'title',
      render: (_, t) => <Link to="/hrms/my-work">{t.title}</Link>,
    },
    { title: 'Project', dataIndex: 'project_name', key: 'project_name' },
    {
      title: 'Due',
      key: 'due_date',
      render: (_, t) =>
        isOverdue(t.due_date, asOf) ? (
          <Typography.Text type="danger" data-testid={`due-${t.task_id}`}>
            {t.due_date}
          </Typography.Text>
        ) : (
          <span data-testid={`due-${t.task_id}`}>{t.due_date ?? '-'}</span>
        ),
    },
  ];
  return (
    <Card title="My tasks" extra={<Link to="/hrms/my-work">My work</Link>}>
      <Row gutter={16}>
        <Col span={8}>
          <Statistic title="Open" value={tasks.open} />
        </Col>
        <Col span={8}>
          <Statistic title="Overdue" value={tasks.overdue} valueStyle={{ color: '#cf1322' }} />
        </Col>
        <Col span={8}>
          <Statistic title="Due this week" value={tasks.due_this_week} />
        </Col>
      </Row>
      <Table size="small" pagination={false} rowKey="task_id" columns={columns} dataSource={tasks.next ?? []} />
    </Card>
  );
}

MyTasksCard.propTypes = {
  tasks: PropTypes.shape({
    open: PropTypes.number,
    overdue: PropTypes.number,
    due_this_week: PropTypes.number,
    next: PropTypes.arrayOf(PropTypes.object),
  }),
  asOf: PropTypes.string,
};
