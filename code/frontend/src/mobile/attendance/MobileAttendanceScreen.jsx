import { useCallback, useEffect, useState } from 'react';
import { Card, Button, Alert, Tag, Spin, Typography, Space } from 'antd';
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  CompassOutlined,
  HistoryOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { mobileAttendanceService } from './mobileAttendanceService.js';
import { ShiftTimer } from './ShiftTimer.jsx';
import { hmm, timeOf } from '../../hrms/attendance/format.js';
import { formatDate } from '@shared/ui/format.js';
import { StatusTag } from '@shared/ui/StatusTag.jsx';

const { Title, Text } = Typography;

export function MobileAttendanceScreen() {
  const [today, setToday] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [locationStatus, setLocationStatus] = useState('checking'); // 'checking' | 'granted' | 'denied' | 'unavailable'
  const [coords, setCoords] = useState(null);

  const checkLocation = useCallback(async () => {
    setLocationStatus('checking');
    const result = await mobileAttendanceService.getCoordinates();
    setLocationStatus(result.status);
    if (result.latitude != null) {
      setCoords(result);
    }
  }, []);

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
    checkLocation();
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
      // Re-capture fresh coordinates before punch
      const freshCoords = await mobileAttendanceService.getCoordinates();
      if (isClockedIn && !isStale) {
        await mobileAttendanceService.clockOut(freshCoords);
      } else {
        await mobileAttendanceService.clockIn(freshCoords);
      }
      await loadData();
    } catch (err) {
      setError(err?.message || 'Attendance mark failed');
    } finally {
      setBusy(false);
    }
  };

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
          {locationStatus === 'granted' ? (
            <Tag color="success" icon={<EnvironmentOutlined />}>GPS Active</Tag>
          ) : locationStatus === 'denied' ? (
            <Tag color="warning" icon={<CompassOutlined />}>GPS Denied</Tag>
          ) : (
            <Tag color="default" icon={<CompassOutlined />}>GPS Off</Tag>
          )}
        </div>
      </div>

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
                {coords?.latitude != null ? 'Location tagged' : '1-Tap Punch'}
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

      {/* Weekly Recent Attendance History */}
      <Card
        className="mobile-recent-card"
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span><HistoryOutlined style={{ marginRight: 6 }} />Recent Activity</span>
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
        {history.length === 0 ? (
          <Text type="secondary" style={{ textAlign: 'center', display: 'block', padding: '16px 0' }}>
            No recent attendance punches recorded.
          </Text>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={10}>
            {history.slice(0, 7).map((session, idx) => (
              <div key={session.id || idx} className="mobile-history-row" data-testid="history-row">
                <div>
                  <Text strong>{session.attendanceDate ? formatDate(session.attendanceDate) : '—'}</Text>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    {timeOf(session.clockInAt)} - {timeOf(session.clockOutAt)}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <Text style={{ display: 'block', fontSize: 13, fontWeight: 500 }}>
                    {hmm(session.workedMinutes)}
                  </Text>
                  {session.dayStatus ? (
                    <StatusTag status={session.dayStatus} />
                  ) : (
                    <Tag color={session.clockOutAt ? 'blue' : 'processing'}>
                      {session.clockOutAt ? 'Completed' : 'Running'}
                    </Tag>
                  )}
                </div>
              </div>
            ))}
          </Space>
        )}
      </Card>
    </div>
  );
}

