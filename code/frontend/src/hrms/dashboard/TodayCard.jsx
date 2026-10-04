import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Card, Descriptions, Tag } from 'antd';
import { hmm, timeOf } from '../attendance/format.js';

/** `me.today` (W-48.6 §5); nothing when the block is null. */
export function TodayCard({ today }) {
  if (!today) return null;
  return (
    <Card title="Today" extra={<Link to="/hrms/attendance">Attendance</Link>}>
      <Descriptions column={1} size="small">
        <Descriptions.Item label="Status">
          <Tag color={today.clocked_in ? 'green' : 'default'}>{today.clocked_in ? 'Clocked in' : 'Not clocked in'}</Tag>
        </Descriptions.Item>
        {today.clocked_in && <Descriptions.Item label="Since">{timeOf(today.clocked_in_at)}</Descriptions.Item>}
        <Descriptions.Item label="Worked">{hmm(today.worked_minutes)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

TodayCard.propTypes = {
  today: PropTypes.shape({
    clocked_in: PropTypes.bool,
    clocked_in_at: PropTypes.string,
    worked_minutes: PropTypes.number,
  }),
};
