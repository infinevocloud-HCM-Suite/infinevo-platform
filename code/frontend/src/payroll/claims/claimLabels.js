/**
 * Labels, `Tag` colours and amount display for the claim and deduction screens, in one place
 * (W-47.4 §5, DEBT-026): the `/me` panel and the officer screens read the same table.
 *
 * Amounts are shown as the API sends them. Nothing here adds, subtracts or rounds money: the
 * helpers below work on the decimal text, never on floating-point values.
 */

export const CLAIM_STATUS = {
  SUBMITTED: { label: 'Submitted', color: 'processing' },
  APPROVED: { label: 'Approved', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'error' },
};

export const DEDUCTION_STATUS = {
  POSTED: { label: 'Posted', color: 'blue' },
  REVERSED: { label: 'Reversed', color: 'default' },
};

/** The six `DeductionType` values (`payroll/.../deduction/DeductionType.java`). */
export const DEDUCTION_TYPE = {
  ADVANCE_RECOVERY: 'Advance recovery',
  LOAN_RECOVERY: 'Loan recovery',
  DAMAGE: 'Damage',
  PENALTY: 'Penalty',
  EXCESS_PAYMENT: 'Excess payment',
  OTHER: 'Other',
};

const toOptions = (map, read) => Object.keys(map).map((value) => ({ value, label: read(map[value]) }));

export const CLAIM_STATUS_OPTIONS = toOptions(CLAIM_STATUS, (s) => s.label);
export const DEDUCTION_STATUS_OPTIONS = toOptions(DEDUCTION_STATUS, (s) => s.label);
export const DEDUCTION_TYPE_OPTIONS = toOptions(DEDUCTION_TYPE, (s) => s);

export function claimStatus(code) {
  return CLAIM_STATUS[code] ?? { label: code ?? '-', color: 'default' };
}

export function deductionStatus(code) {
  return DEDUCTION_STATUS[code] ?? { label: code ?? '-', color: 'default' };
}

export function deductionTypeLabel(code) {
  return DEDUCTION_TYPE[code] ?? code ?? '-';
}

const DECIMAL = /^-?\d+(\.\d+)?$/;

/** Splits a decimal (number or text) into sign, integer digits and fraction digits; null if not one. */
function parts(value) {
  if (value === null || value === undefined || value === '') return null;
  const text = String(value).trim();
  if (!DECIMAL.test(text)) return null;
  const negative = text.startsWith('-');
  const [int, frac = ''] = (negative ? text.slice(1) : text).split('.');
  return { negative, int: int.replace(/^0+(?=\d)/, ''), frac };
}

/** Indian digit grouping on the integer part: 12,38,952. */
function group(int) {
  if (int.length <= 3) return int;
  const last3 = int.slice(-3);
  const rest = int.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
  return `${rest},${last3}`;
}

/**
 * An amount for display, with grouping and at least two decimals: `1500` -> `1,500.00`. The
 * fraction is padded, never rounded, so what the API sent is what is shown. `-` when absent.
 */
export function formatAmount(value) {
  const p = parts(value);
  if (!p) return value === null || value === undefined || value === '' ? '-' : String(value);
  const frac = p.frac.length >= 2 ? p.frac : p.frac.padEnd(2, '0');
  return `${p.negative ? '-' : ''}${group(p.int)}.${frac}`;
}

/**
 * The amount as the API wants it: text with exactly two decimals (`'2500'` -> `'2500.00'`).
 * Returns null for anything that is not a decimal with at most two places.
 */
export function toAmountString(value) {
  const p = parts(value);
  if (!p || p.frac.length > 2) return null;
  return `${p.negative ? '-' : ''}${p.int}.${p.frac.padEnd(2, '0')}`;
}

/**
 * Compares two non-negative decimals by their digits: -1, 0 or 1; null if either is not a
 * decimal. Used for the advisory `max_limit` warning, without converting money to a float.
 */
export function compareAmounts(a, b) {
  const x = parts(a);
  const y = parts(b);
  if (!x || !y || x.negative || y.negative) return null;
  if (x.int.length !== y.int.length) return x.int.length > y.int.length ? 1 : -1;
  if (x.int !== y.int) return x.int > y.int ? 1 : -1;
  const width = Math.max(x.frac.length, y.frac.length);
  const fx = x.frac.padEnd(width, '0');
  const fy = y.frac.padEnd(width, '0');
  if (fx === fy) return 0;
  return fx > fy ? 1 : -1;
}

/** `2026-10-03T08:15:00Z` -> `2026-10-03`; dates pass through. */
export function formatDate(value) {
  if (!value) return '-';
  return String(value).slice(0, 10);
}

/** An employee row from core's search as a picker label. */
export function employeeLabel(emp) {
  if (!emp) return '';
  const name = [emp.firstName, emp.lastName].filter(Boolean).join(' ');
  return emp.employeeNumber ? `${emp.employeeNumber} — ${name}` : name;
}
