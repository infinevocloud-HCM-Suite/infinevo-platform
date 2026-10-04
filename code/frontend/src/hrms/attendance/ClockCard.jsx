import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Card, Space, Spin, Tag, Timeline, Typography } from 'antd';
import { attendanceService } from './attendanceService.js';
import { hmm, timeOf } from './format.js';

/**
 * Clock in / clock out for today (W-48.4 §5). The date shown is the server's; the running time is display
 * only and is never sent anywhere.
 */
export function ClockCard({ onDay }) {
  const [today, setToday] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [dayStatus, setDayStatus] = useState(null);
  const [now, setNow] = useState(() => Date.now());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await attendanceService.today();
      setToday(data);
      if (onDay && data?.date) onDay(data.date);
    } catch (err) {
      setError(err?.message || 'Could not load today');
    } finally {
      setLoading(false);
    }
  }, [onDay]);

  useEffect(() => {
    load();
  }, [load]);

  const open = today?.openSession;

  useEffect(() => {
    if (!open) return undefined;
    const id = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(id);
  }, [open]);

  const act = async () => {
    setBusy(true);
    setError(null);
    try {
      if (open) {
        const out = await attendanceService.clockOut();
        setDayStatus(out?.status || null);
      } else {
        await attendanceService.clockIn();
        setDayStatus(null);
      }
      await load();
    } catch (err) {
      setError(err?.message || 'Something went wrong');
    } finally {
      setBusy(false);
    }
  };

  if (loading && !today)
    return (
      <Card>
        <Spin />
      </Card>
    );

  const runningMinutes = open ? Math.max(0, Math.floor((now - new Date(open.clockInAt).getTime()) / 60000)) : 0;
  const sessions = today?.sessions || [];

  return (
    <Card title={`Today${today?.date ? ` — ${today.date}` : ''}`}>
      <Space direction="vertical" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error} showIcon />}
        <Button type="primary" size="large" danger={!!open} loading={busy} onClick={act}>
          {open ? 'Clock out' : 'Clock in'}
        </Button>
        {open && (
          <Typography.Text>
            Clocked in at {timeOf(open.clockInAt)} · running {hmm(runningMinutes)}
          </Typography.Text>
        )}
        <Typography.Text strong>Worked today: {hmm(today?.workedMinutes ?? 0)}</Typography.Text>
        {dayStatus && (
          <span>
            Day status: <Tag>{dayStatus}</Tag>
          </span>
        )}
        {sessions.length > 0 && (
          <Timeline
            items={sessions.map((s) => ({
              key: s.id,
              children: `${timeOf(s.clockInAt)} – ${s.clockOutAt ? timeOf(s.clockOutAt) : 'now'} (${hmm(
                s.workedMinutes
              )})`,
            }))}
          />
        )}
      </Space>
    </Card>
  );
}

ClockCard.propTypes = { onDay: PropTypes.func };
