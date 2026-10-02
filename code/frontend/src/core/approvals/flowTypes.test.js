import { describe, it, expect } from 'vitest';
import { FLOW_TYPES, flowTypesFor } from './flowTypes.js';

const keys = (flows) => flows.map((flow) => flow.key);

describe('flowTypesFor', () => {
  it('offers a Payroll-only tenant the two claim flows (D-36, D-37)', () => {
    expect(keys(flowTypesFor({ hasHrms: false, hasPayroll: true }))).toEqual([
      'REIMBURSEMENT',
      'PROOF_OF_INVESTMENT',
    ]);
  });

  it('offers an HRMS-only tenant the HRMS flows', () => {
    expect(keys(flowTypesFor({ hasHrms: true, hasPayroll: false }))).toEqual([
      'LEAVE',
      'REGULARIZATION',
      'OVERTIME',
      'TIMESHEET',
    ]);
  });

  it('offers every flow to a tenant holding both modules', () => {
    expect(flowTypesFor({ hasHrms: true, hasPayroll: true })).toEqual(FLOW_TYPES);
  });

  it('offers every flow when the feed reports no module, rather than an empty screen', () => {
    expect(flowTypesFor({ hasHrms: false, hasPayroll: false })).toEqual(FLOW_TYPES);
  });
});
