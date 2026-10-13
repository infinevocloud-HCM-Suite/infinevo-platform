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
 * A pay or leave month - '2026-04' or any date inside it - as "Apr 2026" (D-78).
 *
 * @param {string|null} value
 * @returns {string}
 */
export function formatMonth(value) {
  if (isBlank(value)) return EMPTY;
  const text = String(value).trim();
  const month = dayjs(/^\d{4}-\d{2}$/.test(text) ? `${text}-01` : text);
  return month.isValid() ? month.format('MMM YYYY') : text;
}

/**
 * A moment in time, in the browser's zone, as "1 Apr 2026, 14:05" (D-78).
 *
 * @param {string|Date|number|null} value an ISO instant such as '2026-04-01T08:35:00Z'
 * @returns {string}
 */
export function formatDateTime(value) {
  if (isBlank(value)) return EMPTY;
  const date = dayjs(value);
  return date.isValid() ? date.format('D MMM YYYY, HH:mm') : EMPTY;
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
export function formatMoney(value, symbol = '₹') {
  if (isBlank(value)) return EMPTY;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return DECIMAL.test(trimmed) ? `${symbol} ${moneyFormat.format(trimmed)}` : EMPTY;
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return `${symbol} ${moneyFormat.format(value)}`;
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

/**
 * Capitalises a name typed in all lower case ("asha rao" becomes "Asha Rao") and leaves anything
 * else exactly as typed. A word that already holds a capital letter was cased on purpose -
 * "McDonald", "D'Souza", "HR", "R&D" - and rewriting it would be the defect, not the fix. One capital
 * anywhere in the value means the whole value was cased by hand and is kept. The same
 * rule as the server's (EmployeeServiceImpl.toTitleCase), so what the form shows is what is stored.
 *
 * @param {string|null|undefined} value
 * @returns {string|null|undefined}
 */
export function toTitleCase(value) {
  if (value === null || value === undefined) return value;
  const str = String(value).trim();
  if (!str) return '';
  if (/[A-Z]/.test(str)) return str;
  return str.replace(/(^|\s)(\S)/g, (match, separator, first) => separator + first.toUpperCase());
}

/*
 * Status codes the server sends, with the words and antd Tag colour a screen shows for each
 * (D-78). One table for the whole app, so "APPROVED" is the same green on every screen.
 * Colours: success (done, good), processing (in flight), warning (waiting on someone),
 * error (refused or broken), default (ended or not started).
 */
const STATUS_TAGS = {
  ACTIVE: ['Active', 'success'],
  INACTIVE: ['Inactive', 'default'],
  SUSPENDED: ['Suspended', 'warning'],
  TERMINATED: ['Terminated', 'error'],
  EXITED: ['Exited', 'default'],
  RESIGNED: ['Resigned', 'default'],
  ON_NOTICE: ['On notice', 'warning'],
  PAST_DUE: ['Past due', 'warning'],
  NOT_STARTED: ['Not started', 'default'],
  DRAFT: ['Draft', 'default'],
  SUBMITTED: ['Submitted', 'processing'],
  PENDING: ['Pending', 'warning'],
  IN_PROGRESS: ['In progress', 'processing'],
  IN_REVIEW: ['In review', 'processing'],
  UNDER_REVIEW: ['Under review', 'processing'],
  APPROVED: ['Approved', 'success'],
  VERIFIED: ['Verified', 'success'],
  ACCEPTED: ['Accepted', 'success'],
  REJECTED: ['Rejected', 'error'],
  DECLINED: ['Declined', 'error'],
  CANCELLED: ['Cancelled', 'default'],
  CANCELED: ['Cancelled', 'default'],
  WITHDRAWN: ['Withdrawn', 'default'],
  REVOKED: ['Revoked', 'default'],
  EXPIRED: ['Expired', 'warning'],
  POSTED: ['Posted', 'success'],
  LOCKED: ['Locked', 'processing'],
  COMPUTING: ['Computing', 'processing'],
  COMPUTED: ['Computed', 'processing'],
  PAID: ['Paid', 'success'],
  FAILED: ['Failed', 'error'],
  ERROR: ['Error', 'error'],
  QUEUED: ['Queued', 'default'],
  RUNNING: ['Running', 'processing'],
  COMPLETED: ['Completed', 'success'],
  DONE: ['Done', 'success'],
  SUCCESS: ['Success', 'success'],
  OPEN: ['Open', 'processing'],
  CLOSED: ['Closed', 'default'],
  ON_HOLD: ['On hold', 'warning'],
  TODO: ['To do', 'default'],
  BLOCKED: ['Blocked', 'error'],
  PRESENT: ['Present', 'success'],
  ABSENT: ['Absent', 'error'],
  HALF_DAY: ['Half day', 'warning'],
  ON_LEAVE: ['On leave', 'processing'],
  HOLIDAY: ['Holiday', 'default'],
  WEEKLY_OFF: ['Weekly off', 'default'],
};

/**
 * The label and antd Tag colour for a status code: 'APPROVED' -> { label: 'Approved', color: 'success' }.
 * An unknown code is read as words in the default colour; a missing one shows '—'.
 *
 * @param {string|null|undefined} code
 * @returns {{ label: string, color: string }}
 */
export function statusTag(code) {
  if (isBlank(code)) return { label: EMPTY, color: 'default' };
  const known = STATUS_TAGS[String(code).trim().toUpperCase()];
  if (known) return { label: known[0], color: known[1] };
  return { label: humanize(code), color: 'default' };
}

/** Just the words for a status code - for a Select option or plain text. */
export function statusLabel(code) {
  return statusTag(code).label;
}

/**
 * A table cell or description value that may be missing: the value itself, or '—'.
 *
 * @param {*} value
 * @returns {*}
 */
export function orEmpty(value) {
  return isBlank(value) ? EMPTY : value;
}

