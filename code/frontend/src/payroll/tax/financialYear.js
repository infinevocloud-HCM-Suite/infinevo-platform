/**
 * Financial Year utility mirroring backend FinancialYear (April 1 to March 31 rule).
 *
 * Display labels use the short form 'YYYY-YY' (e.g. '2026-27') shown in the UI.
 * API payloads and URL path segments must use the long form 'YYYY-YYYY' (e.g. '2026-2027')
 * because the backend FinancialYear.parse() only accepts ^(\d{4})-(\d{4})$ (B-3 fix).
 *
 * Use currentFy() / formatFy() / fyOptions() / normalizeFy() for display.
 * Use fyForApi()  when passing the FY to any backend API call.
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
 * Converts a display-format FY string (e.g. '2026-27') to the full API format
 * (e.g. '2026-2027') that the backend FinancialYear.parse() accepts.
 *
 * B-3 fix: the backend regex is ^(\d{4})-(\d{4})$; sending '2026-27' causes 400.
 *
 * @param {string} fy - display format e.g. '2026-27'
 * @returns {string} api format e.g. '2026-2027'
 */
export function fyForApi(fy) {
  if (!fy) return fy;
  const match = String(fy).match(/^(\d{4})-(\d{2})$/);
  if (match) {
    const startYear = parseInt(match[1], 10);
    const endYear = startYear + 1;
    return `${startYear}-${endYear}`;
  }
  // Already in full format (YYYY-YYYY) or unrecognised - return as-is
  return fy;
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

/**
 * Normalizes any financial year representation into the standard display format 'YYYY-YY'
 * (e.g. '2026-27'). Handles '2026', '2026-2027', or '2026-27'.
 *
 * @param {string|number} raw
 * @param {Date|string|number} [fallbackDate]
 * @returns {string} e.g. '2026-27'
 */
export function normalizeFy(raw, fallbackDate) {
  if (!raw) return currentFy(fallbackDate);
  const str = String(raw).trim();
  // Case 1: Short format e.g. '2026-27'
  if (/^\d{4}-\d{2}$/.test(str)) {
    return str;
  }
  // Case 2: Full API/backend format e.g. '2026-2027'
  const matchFull = str.match(/^(\d{4})-(\d{4})$/);
  if (matchFull) {
    const startYear = parseInt(matchFull[1], 10);
    return formatFy(startYear);
  }
  // Case 3: Single 4-digit start year e.g. '2026' or 2026
  if (/^\d{4}$/.test(str)) {
    return formatFy(parseInt(str, 10));
  }
  return str;
}

