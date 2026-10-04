import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, Statistic, Table } from 'antd';

/** `me.projects` (W-48.6 §5): the server's `active` count, then at most five rows. */
export function MyProjectsCard({ projects }) {
  if (!projects) return null;
  const columns = [
    {
      title: 'Project',
      key: 'name',
      render: (_, p) => <Link to={`/hrms/projects/${p.project_id}`}>{p.name}</Link>,
    },
    {
      title: 'Progress',
      dataIndex: 'progress',
      key: 'progress',
      render: (v) => `${v}%`,
    },
    { title: 'End date', dataIndex: 'end_date', key: 'end_date' },
  ];
  return (
    <Card title="My projects">
      <Statistic title="Active" value={projects.active} />
      <Table size="small" pagination={false} rowKey="project_id" columns={columns} dataSource={projects.items ?? []} />
    </Card>
  );
}

MyProjectsCard.propTypes = {
  projects: PropTypes.shape({
    active: PropTypes.number,
    items: PropTypes.arrayOf(PropTypes.object),
  }),
};
