import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, DatePicker, Space, Table, Tag, Typography } from 'antd';
import { CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { NotEntitled } from '@shell/screens';
import { timesheetService } from './timesheetService.js';
import {
  DATE_FORMAT,
  STATUS_COLOR,
  STATUS_LABEL,
  addWeeks,
  formatHundredths,
  mondayOf,
  responseTotal,
} from './weekGrid.js';

const { Title } = Typography;

/** The default range: the last 12 weeks, this one included (W-48.2 §3). */
function defaultRange() {
  const thisMonday = mondayOf(dayjs());
  return [dayjs(addWeeks(thisMonday, -11)), dayjs(thisMonday).add(6, 'day')];
}

/**
 * The caller's weeks, newest first, with each week's and each project's status (W-48.2 §5).
 */
export function TimesheetList() {
  const navigate = useNavigate();
  const [range, setRange] = useState(defaultRange);
  const [weeks, setWeeks] = useState([]);
  const [projectNames, setProjectNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notEntitled, setNotEntitled] = useState(false);

  const load = useCallback(async (from, to) => {
    setLoading(true);
    setError(null);
    try {
      const data = await timesheetService.mine(from, to);
      setWeeks(Array.isArray(data) ? data : []);
    } catch (err) {
      setWeeks([]);
      if (err?.isModuleNotEntitled) setNotEntitled(true);
      setError(err?.message || 'Could not load your timesheets');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(range[0]?.format(DATE_FORMAT), range[1]?.format(DATE_FORMAT));
  }, [load, range]);

  useEffect(() => {
    timesheetService
      .myProjects()
      .then((list) =>
        setProjectNames(Object.fromEntries((Array.isArray(list) ? list : []).map((p) => [p.id, p.name])))
      )
      .catch(() => setProjectNames({}));
  }, []);

  if (notEntitled) return <NotEntitled />;

  const columns = [
    {
      title: 'Week',
      dataIndex: 'week_start_date',
      key: 'week',
      render: (start, row) => (
        <Link to={`/hrms/timesheets/week/${start}`}>
          {dayjs(start).format('D MMM YYYY')} – {dayjs(row.week_end_date || dayjs(start).add(6, 'day')).format('D MMM YYYY')}
        </Link>
      ),
    },
    {
      title: 'Total hours',
      key: 'total',
      render: (_, row) => formatHundredths(responseTotal(row)),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status] || status}</Tag>,
    },
    {
      title: 'Projects',
      key: 'projects',
      render: (_, row) => (
        <Space size={4} wrap>
          {(row.projects || []).map((p) => (
            <Tag key={p.project_id} color={STATUS_COLOR[p.status]}>
              {projectNames[p.project_id] || 'Project'}: {STATUS_LABEL[p.status] || p.status}
            </Tag>
          ))}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }} data-testid="timesheet-list">
      <Space align="center" wrap style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          My timesheets
        </Title>
        <Button
          type="primary"
          icon={<CalendarOutlined />}
          onClick={() => navigate(`/hrms/timesheets/week/${mondayOf(dayjs())}`)}
        >
          Open this week
        </Button>
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(value) => value && setRange(value)}
        />
      </Space>

      {error && (
        <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />
      )}

      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={weeks}
          loading={loading}
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: 'No timesheets in this range.' }}
        />
      </Card>
    </div>
  );
}

export default TimesheetList;
