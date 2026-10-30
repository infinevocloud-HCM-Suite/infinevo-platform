import { isPositiveDecimal } from './decimal.js';

/**
 * The starting earnings for a new salary structure, built from the company's earning catalogue
 * (`EarningResponse`) (W-75.2 §5).
 *
 * Ports the legacy form's two ideas into the catalogue: rows start from the company's % defaults
 * (legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:685-688), and one allowance
 * takes whatever the CTC has left (salaryDetails.js:392-396). The legacy form did that subtraction in
 * the browser; here the server computes the balancing earning (W-75.1) and the screen only marks it.
 */

/** Balancing earning codes, in order of preference (founder 2026-10-30). */
export const BALANCING_CODES = ['SPECIAL', 'FIXED_ALLOWANCE'];

function code(earning) {
  return String(earning?.code || '').toUpperCase();
}

function isActive(earning) {
  return earning?.active !== false;
}

function isBasic(earning) {
  return code(earning) === 'BASIC' || String(earning?.earningType || '').toUpperCase() === 'BASIC';
}

/** An earning a new structure starts with: active, in CTC, not variable, not FBP, default above zero. */
export function isDefaultEarning(earning) {
  return (
    isActive(earning) &&
    Boolean(earning.includedInCtc) &&
    !earning.variable &&
    !earning.fbpComponent &&
    isPositiveDecimal(earning.defaultValue)
  );
}

/**
 * An earning the server accepts as balancing: active, in CTC, not variable, not FBP, not Basic
 * (SalarySplitCalculator.balancingIneligibility; the row itself is always sent as FLAT).
 */
export function canBalance(earning) {
  return (
    isActive(earning) &&
    Boolean(earning.includedInCtc) &&
    !earning.variable &&
    !earning.fbpComponent &&
    !isBasic(earning)
  );
}

/** The balancing earning: `SPECIAL` if active, else `FIXED_ALLOWANCE` if active, else null. */
export function pickBalancingEarning(earnings = []) {
  for (const wanted of BALANCING_CODES) {
    const found = earnings.find((e) => code(e) === wanted && canBalance(e));
    if (found) return found;
  }
  return null;
}

/** A form row for an earning. The balancing row is FLAT; its value is ignored by the server. */
export function rowFor(earning, balancing = false) {
  if (balancing) {
    return {
      componentId: earning.id,
      calculationType: 'FLAT',
      value: '0',
      percentageOf: null,
      enabled: true,
    };
  }
  const isPercentage = earning.calculationType === 'PERCENTAGE';
  return {
    componentId: earning.id,
    calculationType: earning.calculationType || 'FLAT',
    value: earning.defaultValue != null ? String(earning.defaultValue) : '',
    percentageOf: isPercentage ? earning.percentageOf || 'BASIC' : null,
    enabled: true,
  };
}

/**
 * The starting rows and the balancing earning's id (null when the catalogue has neither code; the
 * officer then picks one).
 *
 * @param {Array<Object>} earnings the active earnings (`EarningResponse`)
 * @returns {{ rows: Array<Object>, balancingId: string|null }}
 */
export function buildDefaultRows(earnings = []) {
  const balancing = pickBalancingEarning(earnings);
  const rows = earnings
    .filter((e) => isDefaultEarning(e) && (!balancing || e.id !== balancing.id))
    .map((e) => rowFor(e));
  if (balancing) rows.push(rowFor(balancing, true));
  return { rows, balancingId: balancing ? balancing.id : null };
}
