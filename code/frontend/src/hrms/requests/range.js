import dayjs from 'dayjs';

/** The server's longest range (RegularizationServiceImpl, W-48.5 §3). */
export const MAX_RANGE_DAYS = 93;
export const FMT = 'YYYY-MM-DD';

/** Picker presets only — nothing is requested until the user picks (W-48.5 §13 #4). */
export const presets = [
  { label: 'Last 93 days', value: () => [dayjs().subtract(MAX_RANGE_DAYS - 1, 'day'), dayjs()] },
  { label: 'This month', value: () => [dayjs().startOf('month'), dayjs()] },
];

export function spanDays(from, to) {
  return to.startOf('day').diff(from.startOf('day'), 'day') + 1;
}

export const STATUS_COLOR = { PENDING: 'gold', APPROVED: 'green', REJECTED: 'red', POSTED: 'blue' };

export function timeOf(instant) {
  return instant ? dayjs(instant).format('HH:mm') : '—';
}

export function stamp(instant) {
  return instant ? dayjs(instant).format('YYYY-MM-DD HH:mm') : '—';
}
