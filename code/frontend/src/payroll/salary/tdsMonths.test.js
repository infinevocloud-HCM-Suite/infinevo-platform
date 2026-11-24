import { describe, it, expect } from 'vitest';
import { fyMonthBounds, isMonthOutsideFy, defaultEffectiveMonth } from './tdsMonths.js';

describe('tdsMonths (D-145)', () => {
  it('bounds a year in either form from April to March', () => {
    expect(fyMonthBounds('2026-27')).toEqual({ first: '2026-04', last: '2027-03' });
    expect(fyMonthBounds('2026-2027')).toEqual({ first: '2026-04', last: '2027-03' });
    expect(fyMonthBounds('bad')).toBeNull();
  });

  it('a month before April or after March of the year is outside', () => {
    expect(isMonthOutsideFy('2026-03', '2026-27')).toBe(true);
    expect(isMonthOutsideFy('2026-04', '2026-27')).toBe(false);
    expect(isMonthOutsideFy('2027-03', '2026-27')).toBe(false);
    expect(isMonthOutsideFy('2027-04', '2026-27')).toBe(true);
    expect(isMonthOutsideFy('', '2026-27')).toBe(true);
  });

  it('the default effective month is this month, held inside the year', () => {
    expect(defaultEffectiveMonth('2026-27', new Date(2026, 10, 23))).toBe('2026-11');
    expect(defaultEffectiveMonth('2027-28', new Date(2026, 10, 23))).toBe('2027-04');
    expect(defaultEffectiveMonth('2025-26', new Date(2026, 10, 23))).toBe('2026-03');
  });
});
