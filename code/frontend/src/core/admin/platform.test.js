import { describe, it, expect } from 'vitest';
import {
  employeesText, overLimit, PLANS, PLAN_LABELS, SUPPORT_PLANS, SUPPORT_PLAN_LABELS,
} from './platform.js';

describe('platform', () => {
  it('employeesText reads "N / M" with a limit and "N" without one (W-84.1)', () => {
    expect(employeesText(12, 50)).toBe('12 / 50');
    expect(employeesText(12, null)).toBe('12');
    expect(employeesText(12, undefined)).toBe('12');
    expect(employeesText(0, 1)).toBe('0 / 1');
    expect(employeesText(undefined, null)).toBe('0');
  });

  it('overLimit is true only with a limit and a count above it (W-84.1)', () => {
    expect(overLimit(55, 50)).toBe(true);
    expect(overLimit(50, 50)).toBe(false);
    expect(overLimit(12, 50)).toBe(false);
    expect(overLimit(999, null)).toBe(false);
    expect(overLimit(999, undefined)).toBe(false);
  });

  it('labels every plan and support plan', () => {
    expect(PLANS.map((p) => PLAN_LABELS[p])).toEqual(['Starter', 'Growth', 'Enterprise']);
    expect(SUPPORT_PLANS.map((p) => SUPPORT_PLAN_LABELS[p])).toEqual(['Standard', 'Priority']);
  });
});
