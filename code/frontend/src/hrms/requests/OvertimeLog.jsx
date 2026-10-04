import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, DatePicker, Select, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { NotEntitled, useCan } from '@shell/screens';
import { requestService } from './requestService.js';
import { FMT, MAX_RANGE_DAYS, STATUS_COLOR, presets, spanDays } from './range.js';

/** HR: everyone's overtime requests for a range (W-68 §5). Amount shown as sent. */
export function OvertimeLog() {
  const canRead = useCan('core.overtime.read');
  const navigate = useNavigate();
  const [range, setRange] = useState(null);
  const [employeeId, setEmployeeId] = useState(undefined);
  const [rows, setRows] = useState([]);
  const [names, setNames] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async (r, emp) => {
    setLoading(true);
    setError(null);
    try {
      const data = await requestService.allOvertime(r[0].format(FMT), r[1].format(FMT), emp);
      const list = Array.isArray(data) ? data : [];
      setRows(list);
      setNames((prev) => {
        const next = { ...prev };
        for (const x of list) if (x.employeeId) next[x.employeeId] = x.employeeName || x.employeeId;
        return next;
      });
    } catch (err) {
      setRows([]);
      setError(err?.message || 'Could not load requests');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead && range) load(range, employeeId);
  }, [canRead, load, range, employeeId]);

  const options = useMemo(
    () =>
      Object.entries(names)
        .map(([value, label]) => ({ value, label }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [names]
  );

  if (!canRead) return <NotEntitled />;

  const onRange = (value) => {
    if (!value || !value[0] || !value[1]) return;
    if (spanDays(value[0], value[1]) > MAX_RANGE_DAYS) {
      setError(`Pick at most ${MAX_RANGE_DAYS} days.`);
      return;
    }
    setRange([value[0], value[1]]);
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => r.employeeName || r.employeeId,
    },
    { title: 'Date', dataIndex: 'overtimeDate', key: 'date' },
    { title: 'Hours', dataIndex: 'hours', key: 'hours' },
    { title: 'Amount', dataIndex: 'amount', key: 'amount' },
    {
      title: 'Status',
      key: 'status',
      render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag>,
    },
    { title: 'Source', dataIndex: 'source', key: 'source' },
    { title: 'Posted period', dataIndex: 'postedPeriod', key: 'postedPeriod' },
  ];

  return (
    <Card title="Everyone's overtime">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space wrap>
          <DatePicker.RangePicker
            value={range}
            onChange={onRange}
            allowClear={false}
            aria-label="Range"
            placeholder={['Pick a range', `at most ${MAX_RANGE_DAYS} days`]}
            presets={presets}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="All employees"
            style={{ minWidth: 220 }}
            options={options}
            value={employeeId}
            onChange={(v) => setEmployeeId(v || undefined)}
            aria-label="Employee"
          />
        </Space>
        {error && <Alert type="error" message={error} showIcon />}
        <Table
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          onRow={(r) => ({
            onClick: () => navigate(`/hrms/overtime-requests/${r.id}`),
            style: { cursor: 'pointer' },
          })}
          locale={{
            emptyText: range ? 'No requests in this range' : 'Pick a range to load requests',
          }}
        />
      </Space>
    </Card>
  );
}
