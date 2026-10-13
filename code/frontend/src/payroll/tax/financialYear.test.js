import { describe, it, expect } from 'vitest';
import { currentFy, formatFy, formatFyDisplay, fyOptions, normalizeFy } from './financialYear';

describe('financialYear utility', () => {
  it('correctly resolves 31 March 2027 to 2026-27', () => {
    const march31 = new Date(2027, 2, 31); // Month 2 is March
    expect(currentFy(march31)).toBe('2026-27');
  });

  it('correctly resolves 1 April 2027 to 2027-28', () => {
    const april1 = new Date(2027, 3, 1); // Month 3 is April
    expect(currentFy(april1)).toBe('2027-28');
  });

  it('correctly resolves 1 January 2027 to 2026-27', () => {
    const jan1 = new Date(2027, 0, 1);
    expect(currentFy(jan1)).toBe('2026-27');
  });

  it('correctly resolves 31 December 2026 to 2026-27', () => {
    const dec31 = new Date(2026, 11, 31);
    expect(currentFy(dec31)).toBe('2026-27');
  });

  it('handles string date inputs', () => {
    expect(currentFy('2027-03-31T23:59:59Z')).toBe('2026-27');
    expect(currentFy('2027-04-01T00:00:00Z')).toBe('2027-28');
  });

  it('formats start year with two-digit century rollover', () => {
    expect(formatFy(2026)).toBe('2026-27');
    expect(formatFy(1999)).toBe('1999-00');
    expect(formatFy(2008)).toBe('2008-09');
  });

  it('formats FY string for UI display', () => {
    expect(formatFyDisplay('2026-27')).toBe('2026-27');
    expect(formatFyDisplay(null)).toBe('');
  });

  it('generates fyOptions with requested count descending from current FY', () => {
    const refDate = new Date(2026, 6, 15); // July 2026 -> 2026-27
    const options = fyOptions(3, refDate);
    expect(options).toEqual([
      { label: '2026-27', value: '2026-27' },
      { label: '2025-26', value: '2025-26' },
      { label: '2024-25', value: '2024-25' },
    ]);
  });

  describe('normalizeFy', () => {
    it('normalizes standard short format unchanged', () => {
      expect(normalizeFy('2026-27')).toBe('2026-27');
    });

    it('normalizes long format YYYY-YYYY to YYYY-YY', () => {
      expect(normalizeFy('2026-2027')).toBe('2026-27');
    });

    it('normalizes single 4-digit start year to YYYY-YY', () => {
      expect(normalizeFy('2026')).toBe('2026-27');
      expect(normalizeFy(2026)).toBe('2026-27');
    });

    it('defaults to currentFy when given null, empty or undefined', () => {
      const refDate = new Date(2026, 6, 15);
      expect(normalizeFy(null, refDate)).toBe('2026-27');
      expect(normalizeFy('', refDate)).toBe('2026-27');
    });
  });
});
