/**
 * Financial Year utility mirroring backend FinancialYear (April 1 to March 31 rule).
 * Produces labels formatted as 'YYYY-YY' (e.g. '2026-27').
 */

/**
 * Returns the financial year string for a given date.
 *
 * @param {Date|string|number} [today=new Date()]
 * @returns {string} e.g. '2026-27'
 */
export function currentFy(today = new Date()) {
  let year;
  let month;

  if (typeof today === 'string' && /^\d{4}-\d{2}-\d{2}/.test(today)) {
    const parts = today.split(/[-T ]/);
    year = parseInt(parts[0], 10);
    month = parseInt(parts[1], 10) - 1;
  } else {
    const date = today instanceof Date ? today : new Date(today);
    year = date.getFullYear();
    month = date.getMonth();
  }

  const startYear = month >= 3 ? year : year - 1;
  const endYear = startYear + 1;
  return `${startYear}-${String(endYear % 100).padStart(2, '0')}`;
}

/**
 * Formats a start year into a financial year string.
 *
 * @param {number} startYear
 * @returns {string} e.g. 2026 -> '2026-27'
 */
export function formatFy(startYear) {
  const endYear = startYear + 1;
  return `${startYear}-${String(endYear % 100).padStart(2, '0')}`;
}

/**
 * Formats a financial year code or value for user-facing UI display.
 *
 * @param {string} [fy]
 * @returns {string}
 */
export function formatFyDisplay(fy) {
  return fy ? String(fy) : '';
}

/**
 * Returns an array of financial year options for Select dropdowns.
 * Lists the current FY and preceding (count - 1) financial years by default.
 *
 * @param {number} [count=3]
 * @param {Date|string|number} [today=new Date()]
 * @returns {Array<{label: string, value: string}>}
 */
export function fyOptions(count = 3, today = new Date()) {
  const current = currentFy(today);
  const startYear = parseInt(current.slice(0, 4), 10);

  const options = [];
  for (let i = 0; i < count; i += 1) {
    const fy = formatFy(startYear - i);
    options.push({ label: fy, value: fy });
  }
  return options;
}
