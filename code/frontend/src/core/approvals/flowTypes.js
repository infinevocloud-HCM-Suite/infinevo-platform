/**
 * The approval flow types (W-15.1 §4) and the module each belongs to - the one list the inbox
 * filter and the definitions tabs both read.
 */
export const FLOW_TYPES = [
  { key: 'LEAVE', label: 'Leave', module: 'HRMS' },
  { key: 'REGULARIZATION', label: 'Regularization', module: 'HRMS' },
  { key: 'OVERTIME', label: 'Overtime', module: 'HRMS' },
  { key: 'TIMESHEET', label: 'Timesheet', module: 'HRMS' },
  { key: 'REIMBURSEMENT', label: 'Reimbursement', module: 'PAYROLL' },
  { key: 'PROOF_OF_INVESTMENT', label: 'Proof of Investment', module: 'PAYROLL' },
  { key: 'PAY_RUN', label: 'Pay Run', module: 'PAYROLL' },
];

const PAYROLL_ONLY = ['REIMBURSEMENT', 'PROOF_OF_INVESTMENT'];

/**
 * The flow types to offer a tenant holding these modules (W-46.4 §8, D-36, D-37): a Payroll-only
 * tenant gets the two claim flows, an HRMS-only tenant the HRMS flows, and a tenant holding both
 * gets all of them. When the feed reports neither module the full list is returned - hiding
 * everything on missing information would leave an admin with an empty screen.
 *
 * @param {{ hasHrms: boolean, hasPayroll: boolean }} held
 * @returns {Array<{ key: string, label: string, module: string }>}
 */
export function flowTypesFor({ hasHrms, hasPayroll }) {
  if (hasPayroll && !hasHrms) {
    return FLOW_TYPES.filter((flow) => PAYROLL_ONLY.includes(flow.key));
  }
  if (hasHrms && !hasPayroll) {
    return FLOW_TYPES.filter((flow) => flow.module === 'HRMS');
  }
  return FLOW_TYPES;
}
