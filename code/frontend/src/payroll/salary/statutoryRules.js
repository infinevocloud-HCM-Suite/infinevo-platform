import { compareDecimal, isDecimal } from './decimal.js';

/**
 * Field checks for an employee's statutory numbers, shared by the salary setup drawer and the
 * statutory profile card (W-75.2 §5).
 *
 * UAN as exactly 12 digits ports legacy/Payroll-Fend-react/src/pages/mainPages/employee/basicDetails.js:279-297
 * (the same message; the check applies only when EPF is ticked). The length limits match the server's
 * (EmployeeStatutoryProfileServiceImpl, W-75.1): UAN 16, PF account number 32, ESI number 32.
 */

export const UAN_MAX_LENGTH = 16;
export const PF_ACCOUNT_MAX_LENGTH = 32;
export const ESI_NUMBER_MAX_LENGTH = 32;

/** The statutory ESI wage ceiling, used when the company's ESI setting does not name one. */
export const ESI_WAGE_CEILING = '21000';

export const MESSAGES = {
  uan: 'UAN must be exactly 12 digits',
  esiNumber: 'ESI number must be exactly 10 digits',
  maxLength: 'At most 32 characters',
};

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

/** The UAN error, or null. Checked only when EPF is ticked and a UAN is entered. */
export function uanError(uan, epfTicked) {
  const value = text(uan);
  if (!epfTicked || value === '') return null;
  return /^\d{12}$/.test(value) ? null : MESSAGES.uan;
}

/** The ESI number error, or null. Checked only when ESI is ticked and a number is entered. */
export function esiNumberError(esiNumber, esiTicked) {
  const value = text(esiNumber);
  if (value.length > ESI_NUMBER_MAX_LENGTH) return MESSAGES.maxLength;
  if (!esiTicked || value === '') return null;
  return /^\d{10}$/.test(value) ? null : MESSAGES.esiNumber;
}

/** The PF account number error, or null: at most 32 characters. */
export function pfAccountNumberError(pfAccountNumber) {
  return text(pfAccountNumber).length > PF_ACCOUNT_MAX_LENGTH ? MESSAGES.maxLength : null;
}

const wholeRupees = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

/**
 * The warning shown under the ESI tick when the preview's monthly gross is above the ESI wage
 * ceiling - the employee is ticked but no ESI will be deducted. A warning, never a block.
 *
 * @param {string|number} grossMonthly from the preview
 * @param {boolean} esiTicked
 * @param {string|number} [ceiling] the company's ESI wage ceiling; ₹21,000 when missing
 * @returns {string|null}
 */
export function esiCeilingWarning(grossMonthly, esiTicked, ceiling) {
  if (!esiTicked) return null;
  const limit = isDecimal(ceiling) ? String(ceiling).trim() : ESI_WAGE_CEILING;
  if (compareDecimal(grossMonthly, limit) !== 1) return null;
  return `Gross is above ₹${wholeRupees.format(limit)} — no ESI will be deducted`;
}

function asRule(check) {
  return {
    validator: (_, value) => {
      const message = check(value);
      return message ? Promise.reject(new Error(message)) : Promise.resolve();
    },
  };
}

/**
 * antd Form rules for the three numbers. `getTick(name)` reads a checkbox from the same form, so
 * the UAN and ESI rules follow the EPF and ESI ticks.
 */
export function statutoryFieldRules(getTick) {
  return {
    uan: [asRule((value) => uanError(value, getTick('eligibleForPf')))],
    pfAccountNumber: [asRule(pfAccountNumberError)],
    esiNumber: [asRule((value) => esiNumberError(value, getTick('eligibleForEsi')))],
  };
}

/** The server's field keys for the statutory profile (W-75.1, D-132). */
export const STATUTORY_FIELD_KEYS = ['uan', 'pfAccountNumber', 'esiNumber'];
