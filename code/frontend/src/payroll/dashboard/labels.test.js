import { describe, it, expect } from 'vitest';
import { runStatus, skipReasonLabel } from './labels';

describe('dashboard labels (W-47.5 §5)', () => {
  it('reads every run status and falls back to the code', () => {
    expect(runStatus('PAID')).toEqual({ label: 'Paid', color: 'success' });
    expect(runStatus('COMPUTING').label).toBe('Computing');
    expect(runStatus('NEW_ONE')).toEqual({ label: 'NEW_ONE', color: 'default' });
  });

  it('reads the skip reasons and passes an unknown one through', () => {
    expect(skipReasonLabel('NO_BANK_DETAILS')).toBe('No bank details');
    expect(skipReasonLabel('NO_STATUTORY_PROFILE')).toBe('NO_STATUTORY_PROFILE');
  });
});
