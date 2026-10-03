import { describe, it, expect } from 'vitest';
import {
  claimStatus,
  compareAmounts,
  deductionTypeLabel,
  DEDUCTION_TYPE_OPTIONS,
  formatAmount,
  toAmountString,
} from './claimLabels.js';

describe('claimLabels (W-47.4 §5)', () => {
  it('formats amounts for display without rounding', () => {
    expect(formatAmount(1500)).toBe('1,500.00');
    expect(formatAmount('5010000')).toBe('50,10,000.00');
    expect(formatAmount(250.5)).toBe('250.50');
    expect(formatAmount('0.125')).toBe('0.125');
    expect(formatAmount(null)).toBe('-');
  });

  it('turns an input into a two-decimal string, refusing more places', () => {
    expect(toAmountString('2500')).toBe('2500.00');
    expect(toAmountString('2500.5')).toBe('2500.50');
    expect(toAmountString('1.234')).toBeNull();
    expect(toAmountString('abc')).toBeNull();
  });

  it('compares decimals by their digits', () => {
    expect(compareAmounts('2500', 2000)).toBe(1);
    expect(compareAmounts('2000.00', 2000)).toBe(0);
    expect(compareAmounts('1999.99', '2000')).toBe(-1);
    expect(compareAmounts('0.1', '0.09')).toBe(1);
    expect(compareAmounts(null, '1')).toBeNull();
  });

  it('labels statuses and the six deduction types', () => {
    expect(claimStatus('SUBMITTED').label).toBe('Submitted');
    expect(claimStatus('WHATEVER').color).toBe('default');
    expect(DEDUCTION_TYPE_OPTIONS.map((o) => o.value)).toEqual([
      'ADVANCE_RECOVERY',
      'LOAN_RECOVERY',
      'DAMAGE',
      'PENALTY',
      'EXCESS_PAYMENT',
      'OTHER',
    ]);
    expect(deductionTypeLabel('EXCESS_PAYMENT')).toBe('Excess payment');
  });
});
