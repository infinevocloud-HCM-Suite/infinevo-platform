import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, Space, Statistic, Table, Tag } from 'antd';

/** `team.projects` (W-48.6 §5): `managed`, `by_status` and at most five rows - all the server's figures. */
export function ManagedProjectsCard({ projects }) {
  if (!projects) return null;
  const columns = [
    {
      title: 'Project',
      key: 'name',
      render: (_, p) => <Link to={`/hrms/projects/${p.project_id}`}>{p.name}</Link>,
    },
    { title: 'Team', dataIndex: 'team_size', key: 'team_size' },
    { title: 'Open tasks', dataIndex: 'open_tasks', key: 'open_tasks' },
    { title: 'Overdue', dataIndex: 'overdue_tasks', key: 'overdue_tasks' },
  ];
  return (
    <Card title="Projects I manage">
      <Statistic title="Managed" value={projects.managed} />
      <Space wrap style={{ margin: '8px 0' }}>
        {Object.entries(projects.by_status ?? {}).map(([status, count]) => (
          <Tag key={status}>{`${status}: ${count}`}</Tag>
        ))}
      </Space>
      <Table size="small" pagination={false} rowKey="project_id" columns={columns} dataSource={projects.items ?? []} />
    </Card>
  );
}

ManagedProjectsCard.propTypes = {
  projects: PropTypes.shape({
    managed: PropTypes.number,
    by_status: PropTypes.object,
    items: PropTypes.arrayOf(PropTypes.object),
  }),
};
