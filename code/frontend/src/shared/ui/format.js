import dayjs from 'dayjs';

/**
 * Display formatting for values the server sends (D-74, D-77).
 *
 * Every helper shows '—' for a value that is missing, so a screen never prints "null",
 * "undefined" or "Invalid Date".
 */

export const EMPTY = '—';

const DECIMAL = /^-?\d+(\.\d+)?$/;

const moneyFormat = new Intl.NumberFormat('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function isBlank(value) {
  return value === null || value === undefined || (typeof value === 'string' && value.trim() === '');
}

/**
 * A calendar date as "1 Apr 2026".
 *
 * @param {string|Date|number|null} value an ISO date such as '2026-04-01', or anything dayjs reads
 * @returns {string}
 */
export function formatDate(value) {
  if (isBlank(value)) return EMPTY;
  const date = dayjs(value);
  return date.isValid() ? date.format('D MMM YYYY') : EMPTY;
}

/**
 * An amount in rupees as "₹ 1,25,000.00", grouped the Indian way.
 *
 * The server sends BigDecimal amounts; a numeric string is formatted as the exact decimal it
 * spells, never through a float, so '12345678901234567.89' keeps every digit.
 *
 * @param {string|number|null} value
 * @returns {string}
 */
export function formatMoney(value) {
  if (isBlank(value)) return EMPTY;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return DECIMAL.test(trimmed) ? `₹ ${moneyFormat.format(trimmed)}` : EMPTY;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return `₹ ${moneyFormat.format(value)}`;
  }
  return EMPTY;
}

/**
 * A server code read as words: 'ACTIVE' -> 'Active', 'PART_TIME' -> 'Part time'.
 *
 * @param {string|null} code
 * @returns {string}
 */
export function humanize(code) {
  if (isBlank(code)) return EMPTY;
  const words = String(code).trim().replace(/[_\s]+/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
