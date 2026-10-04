import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, DatePicker, Space, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { ClockCard } from './ClockCard.jsx';
import { attendanceService } from './attendanceService.js';
import { hmm, timeOf } from './format.js';

/** Groups sessions into one row per attendance date (W-48.4 §5). */
function byDay(sessions) {
  const days = new Map();
  for (const s of sessions) {
    const d = days.get(s.attendanceDate) || {
      date: s.attendanceDate,
      sessions: [],
    };
    d.sessions.push(s);
    days.set(s.attendanceDate, d);
  }
  return [...days.values()]
    .map((d) => {
      const live = d.sessions.filter((s) => !s.voidedAt);
      const ins = live
        .map((s) => s.clockInAt)
        .filter(Boolean)
        .sort();
      const outs = live
        .map((s) => s.clockOutAt)
        .filter(Boolean)
        .sort();
      return {
        ...d,
        firstIn: ins[0] || null,
        lastOut: outs[outs.length - 1] || null,
        worked: live.reduce((sum, s) => sum + (s.workedMinutes || 0), 0),
        voided: d.sessions.filter((s) => s.voidedAt),
      };
    })
    .sort((a, b) => (a.date < b.date ? 1 : -1));
}

export function MyAttendance() {
  // The month starts from the server's date, once ClockCard has read it.
  const [month, setMonth] = useState(null);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const onDay = useCallback((date) => setMonth((m) => m || dayjs(date).startOf('month')), []);

  useEffect(() => {
    if (!month) return;
    setLoading(true);
    setError(null);
    attendanceService
      .mySessions(month.format('YYYY-MM-DD'), month.endOf('month').format('YYYY-MM-DD'))
      .then((data) => setRows(Array.isArray(data) ? data : []))
      .catch((err) => {
        setRows([]);
        setError(err?.message || 'Could not load your days');
      })
      .finally(() => setLoading(false));
  }, [month]);

  const days = useMemo(() => byDay(rows), [rows]);

  const columns = [
    { title: 'Date', dataIndex: 'date', key: 'date' },
    { title: 'First in', key: 'in', render: (_, r) => timeOf(r.firstIn) },
    { title: 'Last out', key: 'out', render: (_, r) => timeOf(r.lastOut) },
    { title: 'Worked', key: 'worked', render: (_, r) => hmm(r.worked) },
    { title: 'Sessions', key: 'sessions', render: (_, r) => r.sessions.length },
    {
      title: 'Voided',
      key: 'voided',
      render: (_, r) =>
        r.voided.map((s) => (
          <Tag key={s.id} color="red">
            {s.voidReason || 'VOIDED'}
          </Tag>
        )),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <ClockCard onDay={onDay} />
      <Card
        title="My days"
        extra={
          <DatePicker
            picker="month"
            value={month}
            allowClear={false}
            onChange={(m) => m && setMonth(m.startOf('month'))}
          />
        }
      >
        {error && <Alert type="error" message={error} showIcon />}
        <Table rowKey="date" columns={columns} dataSource={days} loading={loading} pagination={false} />
      </Card>
    </Space>
  );
}
