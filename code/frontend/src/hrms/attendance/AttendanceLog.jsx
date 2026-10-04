import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, DatePicker, Select, Space, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { attendanceService } from './attendanceService.js';
import { hmm, timeOf } from './format.js';

export const MAX_RANGE_DAYS = 31;
const FMT = 'YYYY-MM-DD';

/**
 * Monday to Sunday of the week containing {@code d}. Offered as a picker preset only: the log sends no request
 * until HR picks a range, because nothing HR may call returns the tenant's date and §3 forbids deriving "today" from
 * the browser for a request (the clock card can, through /today, which needs hrms.attendance.mark).
 */
export function weekOf(d) {
  const monday = d.subtract((d.day() + 6) % 7, 'day').startOf('day');
  return [monday, monday.add(6, 'day')];
}

/** Days in an inclusive range. */
export function spanDays(from, to) {
  return to.startOf('day').diff(from.startOf('day'), 'day') + 1;
}

/** HR: everyone's clock sessions for a range (W-48.4 §5). */
export function AttendanceLog() {
  const [range, setRange] = useState(null);
  const [employeeId, setEmployeeId] = useState(undefined);
  const [rows, setRows] = useState([]);
  const [names, setNames] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async (from, to, emp) => {
    setLoading(true);
    setError(null);
    try {
      const data = await attendanceService.allSessions(from.format(FMT), to.format(FMT), emp);
      const list = Array.isArray(data) ? data : [];
      setRows(list);
      setNames((prev) => {
        const next = { ...prev };
        for (const s of list) if (s.employeeId) next[s.employeeId] = s.employeeName || s.employeeId;
        return next;
      });
    } catch (err) {
      setRows([]);
      setError(err?.message || 'Could not load sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!range) return;
    load(range[0], range[1], employeeId);
  }, [load, range, employeeId]);

  const onRange = (value) => {
    if (!value || !value[0] || !value[1]) return;
    if (spanDays(value[0], value[1]) > MAX_RANGE_DAYS) {
      setError(`Pick at most ${MAX_RANGE_DAYS} days.`);
      return;
    }
    setRange([value[0], value[1]]);
  };

  const options = useMemo(
    () =>
      Object.entries(names)
        .map(([value, label]) => ({ value, label }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [names]
  );

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => r.employeeName || r.employeeId,
    },
    { title: 'Date', dataIndex: 'attendanceDate', key: 'date' },
    { title: 'In', key: 'in', render: (_, r) => timeOf(r.clockInAt) },
    { title: 'Out', key: 'out', render: (_, r) => timeOf(r.clockOutAt) },
    { title: 'Worked', key: 'worked', render: (_, r) => hmm(r.workedMinutes) },
    { title: 'Origin', dataIndex: 'origin', key: 'origin' },
    {
      title: 'Voided',
      key: 'voided',
      render: (_, r) => (r.voidedAt ? <Tag color="red">{r.voidReason || 'VOIDED'}</Tag> : null),
    },
  ];

  return (
    <Card title="Attendance log">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space wrap>
          <DatePicker.RangePicker
            value={range}
            onChange={onRange}
            allowClear={false}
            aria-label="Range"
            placeholder={['Pick a range', 'at most 31 days']}
            presets={[{ label: 'This week', value: () => weekOf(dayjs()) }]}
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
          locale={{ emptyText: range ? 'No sessions in this range' : 'Pick a range to load sessions' }}
        />
      </Space>
    </Card>
  );
}
