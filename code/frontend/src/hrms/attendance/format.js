import dayjs from 'dayjs';

/** Minutes as h:mm; null or undefined shows a dash. */
export function hmm(minutes) {
  if (minutes === null || minutes === undefined) return '—';
  const m = Math.max(0, Math.floor(minutes));
  return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`;
}

/** An instant as local time of day, or a dash. */
export function timeOf(instant) {
  return instant ? dayjs(instant).format('HH:mm') : '—';
}
