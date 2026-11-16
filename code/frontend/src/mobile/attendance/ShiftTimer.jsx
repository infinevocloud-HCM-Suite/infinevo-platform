import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';

/**
 * Format total elapsed milliseconds into HH:mm:ss.
 */
function formatElapsed(ms) {
  if (ms == null || isNaN(ms) || ms < 0) return '00:00:00';
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const pad = (n) => String(n).padStart(2, '0');
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

/**
 * ShiftTimer (W-98.2 §4.2).
 * Real-time active shift counter calculated against server clock-in timestamp.
 */
export function ShiftTimer({ clockInAt, active = true }) {
  const [elapsed, setElapsed] = useState(() => {
    if (!clockInAt) return 0;
    const start = new Date(clockInAt).getTime();
    return Math.max(0, Date.now() - start);
  });

  useEffect(() => {
    if (!active || !clockInAt) return undefined;

    const start = new Date(clockInAt).getTime();
    const update = () => {
      setElapsed(Math.max(0, Date.now() - start));
    };

    update();
    const timerId = setInterval(update, 1000);
    return () => clearInterval(timerId);
  }, [clockInAt, active]);

  const formatted = formatElapsed(elapsed);
  const [h, m, s] = formatted.split(':');

  return (
    <div className="mobile-shift-timer" data-testid="shift-timer">
      <div className="mobile-timer-digits" aria-label={`Elapsed shift time: ${h} hours, ${m} minutes, ${s} seconds`}>
        <span className="mobile-timer-unit">
          <span className="mobile-timer-num">{h}</span>
          <span className="mobile-timer-label">HRS</span>
        </span>
        <span className="mobile-timer-sep">:</span>
        <span className="mobile-timer-unit">
          <span className="mobile-timer-num">{m}</span>
          <span className="mobile-timer-label">MIN</span>
        </span>
        <span className="mobile-timer-sep">:</span>
        <span className="mobile-timer-unit">
          <span className="mobile-timer-num">{s}</span>
          <span className="mobile-timer-label">SEC</span>
        </span>
      </div>
      <div className="mobile-timer-sub">Active Shift Time</div>
    </div>
  );
}

ShiftTimer.propTypes = {
  clockInAt: PropTypes.string,
  active: PropTypes.bool,
};

