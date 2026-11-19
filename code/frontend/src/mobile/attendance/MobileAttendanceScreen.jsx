import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Button, Alert, Tag, Spin, Typography, Space } from 'antd';
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  CompassOutlined,
  HistoryOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { mobileAttendanceService } from './mobileAttendanceService.js';
import { ShiftTimer } from './ShiftTimer.jsx';
import { hmm, timeOf } from '../../hrms/attendance/format.js';
import { formatDate } from '@shared/ui/format.js';
import { StatusTag } from '@shared/ui/StatusTag.jsx';

const { Title, Text } = Typography;

/**
 * Builds exactly 7 calendar day rows from the given target date (newest to oldest).
 * Each row aggregates that day's sessions, total worked time, and status.
 */
export function buildLast7Days(todayDateStr, rawSessions = []) {
  const baseDate = todayDateStr ? dayjs(todayDateStr) : dayjs();

  const byDate = new Map();
  for (const s of rawSessions) {
    if (!s.attendanceDate) continue;
    const list = byDate.get(s.attendanceDate) || [];
    list.push(s);
    byDate.set(s.attendanceDate, list);
  }

  const days = [];
  for (let i = 0; i < 7; i++) {
    const d = baseDate.subtract(i, 'day');
    const dateStr = d.format('YYYY-MM-DD');
    const daySessions = byDate.get(dateStr) || [];
    const liveSessions = daySessions.filter((s) => !s.voidedAt);

    const workedMinutes = liveSessions.reduce((sum, s) => sum + (s.workedMinutes || 0), 0);
    const ins = liveSessions.map((s) => s.clockInAt).filter(Boolean).sort();
    const outs = liveSessions.map((s) => s.clockOutAt).filter(Boolean).sort();
    const firstIn = ins[0] || null;
    const lastOut = outs[outs.length - 1] || null;

    let status = null;
    if (daySessions.length > 0 && daySessions[0].dayStatus) {
      status = daySessions[0].dayStatus;
    } else if (workedMinutes > 0) {
      status = 'PRESENT';
    } else {
      const dayOfWeek = d.day(); // 0: Sunday, 6: Saturday
      if (dayOfWeek === 0 || dayOfWeek === 6) {
        status = 'WEEKEND';
      } else {
        status = 'ABSENT';
      }
    }

    days.push({
      date: dateStr,
      firstIn,
      lastOut,
      workedMinutes,
      dayStatus: status,
      sessionCount: daySessions.length,
    });
  }

  return days;
}

export function MobileAttendanceScreen() {
  const [today, setToday] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [locationStatus, setLocationStatus] = useState('checking'); // 'checking' | 'granted' | 'denied' | 'unavailable' | 'timeout'
  const [coords, setCoords] = useState(null);
  const [consentGiven, setConsentGiven] = useState(false);

  const checkLocation = useCallback(async () => {
    setLocationStatus('checking');
    const result = await mobileAttendanceService.getCoordinates();
    setLocationStatus(result.status);
    if (result.latitude != null) {
      setCoords(result);
    } else {
      setCoords(null);
    }
    return result;
  }, []);

  const handleGrantConsent = async () => {
    await mobileAttendanceService.grantLocationConsent();
    setConsentGiven(true);
    await checkLocation();
  };

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await mobileAttendanceService.getToday();
      setToday(data);
      if (data?.date) {
        const historyData = await mobileAttendanceService.getWeeklyHistory(data.date);
        setHistory(Array.isArray(historyData) ? historyData : []);
      }
    } catch (err) {
      setError(err?.message || 'Unable to load attendance data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    mobileAttendanceService.hasLocationConsent().then((consented) => {
      setConsentGiven(consented);
      if (consented) {
        checkLocation();
      } else {
        setLocationStatus('unavailable');
      }
    });
  }, [loadData, checkLocation]);

  const openSession = today?.openSession;
  const isClockedIn = Boolean(openSession);

  // Stale session check (> 24h)
  const isStale = Boolean(
    openSession &&
      ((openSession.attendanceDate && today?.date && openSession.attendanceDate < today.date) ||
        (openSession.clockInAt && Date.now() - new Date(openSession.clockInAt).getTime() > 24 * 60 * 60 * 1000))
  );

  const handlePunch = async () => {
    setBusy(true);
    setError(null);
    try {
      let punchCoords = coords;
      if (consentGiven) {
        punchCoords = await mobileAttendanceService.getCoordinates();
        if (punchCoords?.latitude != null) {
          setCoords(punchCoords);
          setLocationStatus('granted');
        } else {
          setCoords(null);
          setLocationStatus(punchCoords?.status || 'unavailable');
        }
      }
      if (isClockedIn && !isStale) {
        await mobileAttendanceService.clockOut(punchCoords);
      } else {
        await mobileAttendanceService.clockIn(punchCoords);
      }
      await loadData();
    } catch (err) {
      setError(err?.message || 'Attendance mark failed');
    } finally {
      setBusy(false);
    }
  };

  const dayRows = useMemo(() => {
    return buildLast7Days(today?.date, history);
  }, [today?.date, history]);

  if (loading && !today) {
    return (
      <div className="mobile-loading-container" data-testid="mobile-attendance-loading">
        <Spin size="large" />
        <Text type="secondary" style={{ marginTop: 12 }}>Loading attendance...</Text>
      </div>
    );
  }

  const sessions = today?.sessions || [];
  const firstIn = sessions.length ? sessions[0].clockInAt : openSession?.clockInAt;
  const lastOut = sessions.length ? sessions[sessions.length - 1].clockOutAt : null;
  const workedMinutes = today?.workedMinutes || 0;
  const hasLocation = locationStatus === 'granted' && coords?.latitude != null;

  return (
    <div className="mobile-attendance-screen" data-testid="mobile-attendance-screen">
      {/* Top Status & Date Card */}
      <div className="mobile-attendance-header-card">
        <div>
          <Title level={4} style={{ margin: 0 }}>
            {today?.date ? formatDate(today.date) : 'Today'}
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {isClockedIn && !isStale ? 'You are currently clocked in' : 'Shift not started'}
          </Text>
        </div>

        <div className="mobile-gps-chip" data-testid="gps-chip">
          {hasLocation ? (
            <Tag color="success" icon={<EnvironmentOutlined />}>GPS Active</Tag>
          ) : (
            <Tag color="default" icon={<CompassOutlined />}>Location not recorded</Tag>
          )}
        </div>
      </div>

      {/* Consent Notice - Shown before first location capture */}
      {!consentGiven && (
        <Alert
          type="info"
          showIcon
          data-testid="location-consent-banner"
          message="Location Permission Required"
          description="Infinevo records your location only at the moment you clock in or out, so your employer can see where the punch was made. It is not tracked at any other time."
          action={
            <Button size="small" type="primary" onClick={handleGrantConsent} data-testid="consent-grant-btn">
              I Understand
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      {isStale && (
        <Alert
          type="warning"
          showIcon
          message="Previous Session Expired (>24h)"
          description="Your previous shift exceeded 24 hours. Tapping Clock In will close it and record your new session."
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Main Circular Punch Section */}
      <div className="mobile-punch-hero">
        {isClockedIn && !isStale ? (
          <ShiftTimer clockInAt={openSession?.clockInAt} active={!busy} />
        ) : (
          <div className="mobile-clock-ready">
            <ClockCircleOutlined style={{ fontSize: 36, color: '#10b981', marginBottom: 8 }} />
            <div className="mobile-clock-ready-text">Ready to Punch In</div>
          </div>
        )}

        <button
          type="button"
          data-testid="punch-button"
          className={`mobile-punch-circle-btn ${isClockedIn && !isStale ? 'clocked-in' : 'clocked-out'} ${busy ? 'busy' : ''}`}
          onClick={handlePunch}
          disabled={busy}
          aria-label={isClockedIn && !isStale ? 'Clock Out' : 'Clock In'}
        >
          {busy ? (
            <Spin />
          ) : (
            <div className="mobile-punch-circle-inner">
              <span className="mobile-punch-action-label">
                {isClockedIn && !isStale ? 'CLOCK OUT' : 'CLOCK IN'}
              </span>
              <span className="mobile-punch-time-label">
                {hasLocation ? 'Location tagged' : 'Location not recorded'}
              </span>
            </div>
          )}
        </button>
      </div>

      {/* Today's Shift Metrics Summary */}
      <Card className="mobile-shift-summary-card" title="Today's Shift Summary">
        <div className="mobile-metrics-grid">
          <div className="mobile-metric-item">
            <Text type="secondary" className="mobile-metric-label">First In</Text>
            <Title level={4} className="mobile-metric-val">{timeOf(firstIn)}</Title>
          </div>
          <div className="mobile-metric-item">
            <Text type="secondary" className="mobile-metric-label">Last Out</Text>
            <Title level={4} className="mobile-metric-val">{timeOf(lastOut)}</Title>
          </div>
          <div className="mobile-metric-item">
            <Text type="secondary" className="mobile-metric-label">Total Hours</Text>
            <Title level={4} className="mobile-metric-val">{hmm(workedMinutes)}</Title>
          </div>
        </div>
      </Card>

      {/* Weekly Recent Attendance History: exactly 7 days */}
      <Card
        className="mobile-recent-card"
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span><HistoryOutlined style={{ marginRight: 6 }} />Recent Activity (Last 7 Days)</span>
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              onClick={loadData}
              aria-label="Refresh attendance data"
            />
          </div>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={10}>
          {dayRows.map((day) => (
            <div key={day.date} className="mobile-history-row" data-testid="history-row">
              <div>
                <Text strong>{formatDate(day.date)}</Text>
                <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                  {day.firstIn ? `${timeOf(day.firstIn)} - ${timeOf(day.lastOut)}` : 'No punch recorded'}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <Text style={{ display: 'block', fontSize: 13, fontWeight: 500 }}>
                  {hmm(day.workedMinutes)}
                </Text>
                <StatusTag status={day.dayStatus} />
              </div>
            </div>
          ))}
        </Space>
      </Card>
    </div>
  );
}
