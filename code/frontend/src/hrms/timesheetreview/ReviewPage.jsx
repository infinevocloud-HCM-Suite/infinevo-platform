import { useEffect, useMemo, useState } from 'react';
import { Card, DatePicker, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { NotEntitled, useCan } from '@shell/screens';
import { reviewService } from './reviewService.js';
import {
  DATE_FORMAT,
  STATUS_COLOR,
  STATUS_LABEL,
  formatHundredths,
  mondayOf,
  responseTotal,
} from '../timesheet/weekGrid.js';

const { RangePicker } = DatePicker;

/** The server refuses DRAFT in a review list (`TimesheetReviewServiceImpl.java`, Filters.of). */
export const REVIEW_STATUSES = ['SUBMITTED', 'APPROVED', 'REJECTED'];
const PAGE_SIZE = 20;

const TABS = [
  { key: 'managed', label: 'My projects', by: 'projectId' },
  { key: 'team', label: 'My team', by: 'employeeId' },
  { key: 'all', label: 'All', by: 'employeeId' },
];

function defaultRange() {
  const end = dayjs();
  return [dayjs(mondayOf(end.subtract(21, 'day'))), end];
}

/** Timesheet review (W-48.3 §5): one tab per list the caller may see, shared filters, server paging. */
export function ReviewPage() {
  const canApprove = useCan('hrms.timesheet.approve');
  const canTeam = useCan('hrms.timesheet.read_team');
  const canAll = useCan('hrms.timesheet.read');
  const allowed = { managed: canApprove, team: canTeam, all: canAll };
  const tabs = TABS.filter((t) => allowed[t.key]);
  const navigate = useNavigate();

  const [tab, setTab] = useState(tabs[0]?.key);
  const [range, setRange] = useState(defaultRange);
  const [status, setStatus] = useState('SUBMITTED');
  const [subject, setSubject] = useState(undefined);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], total_elements: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const current = TABS.find((t) => t.key === tab);

  useEffect(() => {
    if (!current) return undefined;
    let live = true;
    const filters = {
      from: range?.[0] ? range[0].format(DATE_FORMAT) : undefined,
      to: range?.[1] ? range[1].format(DATE_FORMAT) : undefined,
      status,
      [current.by]: subject,
      page,
      size: PAGE_SIZE,
    };
    setLoading(true);
    setError(null);
    reviewService[current.key](filters)
      .then((d) => live && setData(d || { content: [], total_elements: 0 }))
      .catch(() => live && setError('Could not load timesheets.'))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [current, range, status, subject, page]);

  const options = useMemo(() => {
    const seen = new Map();
    for (const row of data.content || []) {
      if (current?.by === 'projectId') {
        for (const p of row.projects || []) seen.set(p.project_id, p.project_name || p.project_id);
      } else {
        seen.set(row.employee_id, row.employee_name || row.employee_id);
      }
    }
    if (subject && !seen.has(subject)) seen.set(subject, subject);
    return [...seen].map(([value, label]) => ({ value, label }));
  }, [data, current, subject]);

  if (tabs.length === 0) return <NotEntitled />;

  const columns = [
    { title: 'Employee', key: 'employee', render: (_, r) => r.employee_name || r.employee_id },
    { title: 'Week', dataIndex: 'week_start_date', key: 'week' },
    {
      title: 'Total hours',
      key: 'total',
      align: 'right',
      render: (_, r) => formatHundredths(responseTotal(r)),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{STATUS_LABEL[r.status] || r.status}</Tag>,
    },
    {
      title: 'Projects',
      key: 'projects',
      render: (_, r) => (
        <Space wrap>
          {(r.projects || []).map((p) => (
            <Tag key={p.id} color={STATUS_COLOR[p.status]}>
              {p.project_name || p.project_id}: {STATUS_LABEL[p.status] || p.status}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Submitted at',
      key: 'submitted',
      render: (_, r) => (r.submitted_at ? dayjs(r.submitted_at).format('YYYY-MM-DD HH:mm') : ''),
    },
  ];

  const reset = (fn) => (v) => {
    fn(v);
    setPage(0);
  };
  const byProject = current?.by === 'projectId';

  return (
    <Card title="Timesheet review">
      <Tabs
        activeKey={tab}
        onChange={(k) => {
          setTab(k);
          setSubject(undefined);
          setPage(0);
        }}
        items={tabs.map((t) => ({ key: t.key, label: t.label }))}
      />
      <Space wrap style={{ marginBottom: 16 }}>
        <RangePicker value={range} onChange={reset(setRange)} />
        <Select
          aria-label="Status"
          style={{ width: 160 }}
          value={status}
          onChange={reset(setStatus)}
          options={REVIEW_STATUSES.map((s) => ({ value: s, label: STATUS_LABEL[s] }))}
        />
        <Select
          aria-label={byProject ? 'Project' : 'Employee'}
          placeholder={byProject ? 'Any project' : 'Any employee'}
          allowClear
          style={{ width: 220 }}
          value={subject}
          onChange={reset(setSubject)}
          options={options}
        />
      </Space>
      {error && <Typography.Text type="danger">{error}</Typography.Text>}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data.content || []}
        onRow={(r) => ({
          onClick: () => navigate(`/hrms/timesheet-review/${r.id}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: data.total_elements || 0,
          showSizeChanger: false,
          onChange: (p) => setPage(p - 1),
        }}
      />
    </Card>
  );
}
