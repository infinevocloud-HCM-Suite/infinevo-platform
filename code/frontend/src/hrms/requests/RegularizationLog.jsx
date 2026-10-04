import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, DatePicker, Select, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { NotEntitled, useCan } from '@shell/screens';
import { requestService } from './requestService.js';
import { FMT, MAX_RANGE_DAYS, STATUS_COLOR, presets, spanDays, stamp, timeOf } from './range.js';

const STATUSES = ['PENDING', 'APPROVED', 'REJECTED'].map((s) => ({ value: s, label: s }));

/** HR: everyone's regularization requests for a range (W-48.5 §5). */
export function RegularizationLog() {
  const canRead = useCan('core.attendance.read');
  const navigate = useNavigate();
  const [range, setRange] = useState(null);
  const [status, setStatus] = useState(undefined);
  const [employeeId, setEmployeeId] = useState(undefined);
  const [rows, setRows] = useState([]);
  const [names, setNames] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async (r, st, emp) => {
    setLoading(true);
    setError(null);
    try {
      const data = await requestService.allRegularizations(r[0].format(FMT), r[1].format(FMT), st, emp);
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
    if (canRead && range) load(range, status, employeeId);
  }, [canRead, load, range, status, employeeId]);

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
    { title: 'Employee', key: 'employee', render: (_, r) => r.employeeName || r.employeeId },
    { title: 'Date', dataIndex: 'date', key: 'date' },
    { title: 'In', key: 'in', render: (_, r) => timeOf(r.inAt) },
    { title: 'Out', key: 'out', render: (_, r) => timeOf(r.outAt) },
    { title: 'Reason', dataIndex: 'reason', key: 'reason', ellipsis: true },
    { title: 'Status', key: 'status', render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag> },
    { title: 'Decided at', key: 'decidedAt', render: (_, r) => stamp(r.decidedAt) },
  ];

  return (
    <Card title="Everyone's regularizations">
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
            placeholder="All statuses"
            style={{ minWidth: 160 }}
            options={STATUSES}
            value={status}
            onChange={(v) => setStatus(v || undefined)}
            aria-label="Status"
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
          onRow={(r) => ({ onClick: () => navigate(`/hrms/regularizations/${r.id}`), style: { cursor: 'pointer' } })}
          locale={{ emptyText: range ? 'No requests in this range' : 'Pick a range to load requests' }}
        />
      </Space>
    </Card>
  );
}
