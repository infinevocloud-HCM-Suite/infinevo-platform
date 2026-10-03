import { describe, it, expect } from 'vitest';
import { currentStartYear, label, options } from './financialYear';

describe('dashboard financialYear (W-47.5 §7)', () => {
  it('31 March 2027 is FY 2026 and 1 April 2027 is FY 2027', () => {
    expect(currentStartYear(new Date(2027, 2, 31))).toBe(2026);
    expect(currentStartYear(new Date(2027, 3, 1))).toBe(2027);
  });

  it('label(2026) is 2026-27, and the century wraps', () => {
    expect(label(2026)).toBe('2026-27');
    expect(label(2099)).toBe('2099-00');
  });

  it('options gives the current year and the two before it, newest first', () => {
    expect(options(new Date(2026, 9, 3))).toEqual([
      { value: 2026, label: '2026-27' },
      { value: 2025, label: '2025-26' },
      { value: 2024, label: '2024-25' },
    ]);
  });
});
