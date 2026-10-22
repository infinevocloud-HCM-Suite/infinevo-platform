import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Card, Space, Spin, Timeline, Typography } from 'antd';
import { attendanceService } from './attendanceService.js';
import { hmm, timeOf } from './format.js';
import { formatDate } from '@shared/ui/format.js';
import { StatusTag } from '@shared/ui/StatusTag.jsx';

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

  // A session is stale if it started on an earlier date or has been open for > 24 hours (D-104).
  const isStale = Boolean(
    open &&
      ((open.attendanceDate && today?.date && open.attendanceDate < today.date) ||
        (open.clockInAt && now - new Date(open.clockInAt).getTime() > 24 * 60 * 60 * 1000))
  );

  useEffect(() => {
    if (!open) return undefined;
    const id = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(id);
  }, [open]);

  const act = async () => {
    setBusy(true);
    setError(null);
    try {
      if (open && !isStale) {
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
    <Card title={`Today${today?.date ? ` — ${formatDate(today.date)}` : ''}`}>
      <Space direction="vertical" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error} showIcon />}
        {isStale && (
          <Alert
            type="warning"
            showIcon
            message="Previous clock-in session expired (>24 hours)"
            description={`Your previous session from ${formatDate(open.attendanceDate || open.clockInAt)} exceeded 24 hours and cannot be clocked out. Clicking "Clock in" will automatically close it (marked as Not Clocked Out) and start your new session for today. You can regularize that day's hours via Attendance Regularization.`}
          />
        )}
        <Button
          type="primary"
          size="large"
          danger={Boolean(open && !isStale)}
          loading={busy}
          onClick={act}
        >
          {open && !isStale ? 'Clock out' : 'Clock in'}
        </Button>
        {open && !isStale && (
          <Typography.Text>
            Clocked in at {timeOf(open.clockInAt)} · running {hmm(runningMinutes)}
          </Typography.Text>
        )}
        {open && isStale && (
          <Typography.Text type="secondary">
            Previous session clocked in at {timeOf(open.clockInAt)} ({formatDate(open.attendanceDate || open.clockInAt)}) · exceeded 24h
          </Typography.Text>
        )}
        <Typography.Text strong>Worked today: {hmm(today?.workedMinutes ?? 0)}</Typography.Text>
        {dayStatus && (
          <span>
            Day status: <StatusTag status={dayStatus} />
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
