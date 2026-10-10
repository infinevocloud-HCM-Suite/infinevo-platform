import { describe, it, expect } from 'vitest';
import { formatDate, formatMoney, humanize } from './format.js';

describe('formatDate', () => {
  it('shows an ISO date as day, short month and year', () => {
    expect(formatDate('2026-04-01')).toBe('1 Apr 2026');
    expect(formatDate('2026-12-25')).toBe('25 Dec 2026');
  });

  it('shows a dash for a missing or unreadable date', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
    expect(formatDate('')).toBe('—');
    expect(formatDate('not a date')).toBe('—');
  });
});

describe('formatMoney', () => {
  it('groups rupees the Indian way with two decimals', () => {
    expect(formatMoney(125000)).toBe('₹ 1,25,000.00');
    expect(formatMoney(0)).toBe('₹ 0.00');
    expect(formatMoney(1234.5)).toBe('₹ 1,234.50');
  });

  it('formats a BigDecimal sent as a string without passing it through a float', () => {
    expect(formatMoney('125000.00')).toBe('₹ 1,25,000.00');
    expect(formatMoney('12345678901234567.89')).toBe('₹ 12,34,56,78,90,12,34,567.89');
    expect(formatMoney('-500.5')).toBe('₹ -500.50');
  });

  it('shows a dash for a missing or non-numeric amount', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
    expect(formatMoney('')).toBe('—');
    expect(formatMoney('abc')).toBe('—');
    expect(formatMoney(Number.NaN)).toBe('—');
  });
});

describe('humanize', () => {
  it('reads a server code as words', () => {
    expect(humanize('ACTIVE')).toBe('Active');
    expect(humanize('PART_TIME')).toBe('Part time');
    expect(humanize('MALE')).toBe('Male');
  });

  it('shows a dash for a missing code', () => {
    expect(humanize(null)).toBe('—');
    expect(humanize(undefined)).toBe('—');
    expect(humanize('')).toBe('—');
  });

  it('formatMoney takes another currency symbol; the rupee is only the default (payroll is India-only today)', () => {
    expect(formatMoney('1250.5', 'AED')).toBe('AED 1,250.50');
  });
});
