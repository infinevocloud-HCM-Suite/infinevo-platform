import { describe, it, expect } from 'vitest';
import { getItemRoute } from './itemRoutes.js';

describe('itemRoutes', () => {
  it('returns valid path for LEAVE flow type with itemId and null for unrouted flows', () => {
    expect(getItemRoute('LEAVE', 'req-1')).toBe('/leave/requests/req-1');
    expect(getItemRoute('REGULARIZATION', 'reg-2')).toBeNull();
    expect(getItemRoute('OVERTIME', 'ot-3')).toBeNull();
    expect(getItemRoute('PROOF_OF_INVESTMENT', 'poi-5')).toBeNull();
    expect(getItemRoute('PAY_RUN', 'run-6')).toBeNull();
  });

  it('opens a timesheet project entry at /hrms/timesheet-review/entries/{id} (W-48.3 §5)', () => {
    expect(getItemRoute('TIMESHEET', 'pe-7')).toBe('/hrms/timesheet-review/entries/pe-7');
    expect(getItemRoute('TIMESHEET', null)).toBeNull();
  });

  it('opens a reimbursement claim at /payroll/claims/{id} (W-47.4 §5)', () => {
    expect(getItemRoute('REIMBURSEMENT', 'claim-4')).toBe('/payroll/claims/claim-4');
    expect(getItemRoute('REIMBURSEMENT', null)).toBeNull();
    expect(getItemRoute('REIMBURSEMENT', '')).toBeNull();
  });

  it('returns null when flowType is unknown or missing or itemId is empty', () => {
    expect(getItemRoute('UNKNOWN_FLOW', 'id-1')).toBeNull();
    expect(getItemRoute(null, 'id-1')).toBeNull();
    expect(getItemRoute('LEAVE', null)).toBeNull();
    expect(getItemRoute('LEAVE', '')).toBeNull();
  });
});
