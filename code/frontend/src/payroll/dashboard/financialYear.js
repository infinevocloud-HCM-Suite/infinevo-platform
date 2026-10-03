/**
 * The dashboard's financial year, as the start year the API takes in `?fy=` (W-47.5 §5).
 * April to March: 31 March 2027 is FY 2026, 1 April 2027 is FY 2027. Pure functions; every one
 * takes `today` so a test can fix the date.
 */

/** The start year of the financial year containing `today`. */
export function currentStartYear(today = new Date()) {
  const year = today.getFullYear();
  return today.getMonth() >= 3 ? year : year - 1;
}

/** `2026` -> `'2026-27'`. */
export function label(startYear) {
  return `${startYear}-${String((startYear + 1) % 100).padStart(2, '0')}`;
}

/** The current financial year and the two before it, newest first: `[{ value: 2026, label: '2026-27' }, ...]`. */
export function options(today = new Date()) {
  const current = currentStartYear(today);
  return [current, current - 1, current - 2].map((value) => ({ value, label: label(value) }));
}
