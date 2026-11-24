/**
 * The pay months of a financial year, for the TDS effective month (D-145). The year is either form,
 * '2026-27' or '2026-2027'; months are 'YYYY-MM'.
 */

function startYear(fy) {
  const match = String(fy || '').match(/^(\d{4})-\d{2,4}$/);
  return match ? parseInt(match[1], 10) : null;
}

/** April and March of the year: `{ first: '2026-04', last: '2027-03' }`, or null for a bad year. */
export function fyMonthBounds(fy) {
  const start = startYear(fy);
  if (start === null) return null;
  return { first: `${start}-04`, last: `${start + 1}-03` };
}

/** True when the month ('YYYY-MM') falls outside the year; a bad month or year is outside. */
export function isMonthOutsideFy(month, fy) {
  const bounds = fyMonthBounds(fy);
  if (!bounds || !/^\d{4}-\d{2}$/.test(String(month || ''))) return true;
  return month < bounds.first || month > bounds.last;
}

/** The month a new figure starts by default: this month, held inside the year. */
export function defaultEffectiveMonth(fy, today = new Date()) {
  const bounds = fyMonthBounds(fy);
  if (!bounds) return null;
  const current = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
  if (current < bounds.first) return bounds.first;
  if (current > bounds.last) return bounds.last;
  return current;
}
