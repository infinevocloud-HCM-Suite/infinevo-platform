import PropTypes from 'prop-types';
import { Card, Col, List, Row, Statistic } from 'antd';

/** `team.reports` (W-48.6 §5): the server's counts and at most five late names. */
export function ReportsCard({ reports }) {
  if (!reports) return null;
  return (
    <Card title="My team today">
      <Row gutter={16}>
        <Col span={8}>
          <Statistic title="Reports" value={reports.reports} />
        </Col>
        <Col span={8}>
          <Statistic title="Clocked in" value={reports.clocked_in_today} />
        </Col>
        <Col span={8}>
          <Statistic title="Late last week" value={reports.late_last_week} />
        </Col>
      </Row>
      <List
        size="small"
        header="Late timesheets"
        dataSource={reports.late ?? []}
        rowKey="employee_id"
        renderItem={(r) => <List.Item>{r.name}</List.Item>}
      />
    </Card>
  );
}

ReportsCard.propTypes = {
  reports: PropTypes.shape({
    reports: PropTypes.number,
    clocked_in_today: PropTypes.number,
    late_last_week: PropTypes.number,
    late: PropTypes.arrayOf(PropTypes.object),
  }),
};
