import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, Descriptions, Result, Skeleton, Space, Tag } from 'antd';
import { ClockCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { timesheetService } from './timesheetService.js';
import { STATUS_COLOR, STATUS_LABEL, formatHundredths, mondayOf, responseTotal } from './weekGrid.js';

/**
 * The `/me` Timesheet panel (W-48.2 §5): this week's status, total hours, each project's status and a link to
 * the week grid. `data: null` from `/me/timesheet` is a week not started yet.
 */
export function MyTimesheetPanel() {
  const weekStart = mondayOf(dayjs());
  const [week, setWeek] = useState(null);
  const [projectNames, setProjectNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const [weekResult, projectsResult] = await Promise.allSettled([
      timesheetService.week(weekStart),
      timesheetService.myProjects(),
    ]);
    if (weekResult.status === 'fulfilled') {
      setWeek(weekResult.value ?? null);
    } else {
      setWeek(null);
      setError(weekResult.reason?.message || 'Could not load this week');
    }
    const list = projectsResult.status === 'fulfilled' ? projectsResult.value : [];
    setProjectNames(Object.fromEntries((Array.isArray(list) ? list : []).map((p) => [p.id, p.name])));
    setLoading(false);
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return <Skeleton active paragraph={{ rows: 4 }} />;
  }
  if (error) {
    return (
      <Result
        status="error"
        title="Could not load your timesheet"
        subTitle={error}
        extra={
          <Button type="primary" onClick={load}>
            Retry
          </Button>
        }
      />
    );
  }

  const status = week?.status || 'NOT_STARTED';
  return (
    <Card
      data-testid="panel-timesheet"
      title={
        <Space>
          <ClockCircleOutlined />
          <span>Week of {dayjs(weekStart).format('D MMM YYYY')}</span>
        </Space>
      }
      extra={<Link to={`/hrms/timesheets/week/${weekStart}`}>Open week</Link>}
    >
      <Descriptions column={1} size="small">
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status] || status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Total hours">{formatHundredths(responseTotal(week))}</Descriptions.Item>
        {(week?.projects || []).length > 0 && (
          <Descriptions.Item label="Projects">
            <Space size={4} wrap>
              {week.projects.map((p) => (
                <Tag key={p.project_id} color={STATUS_COLOR[p.status]}>
                  {projectNames[p.project_id] || 'Project'}: {STATUS_LABEL[p.status] || p.status}
                </Tag>
              ))}
            </Space>
          </Descriptions.Item>
        )}
      </Descriptions>
    </Card>
  );
}

export default MyTimesheetPanel;
