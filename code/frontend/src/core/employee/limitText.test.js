import { describe, it, expect } from 'vitest';
import { limitBadgeText, limitReached, EMPLOYEE_LIMIT_REACHED } from './limitText.js';

describe('limitBadgeText (W-84.2)', () => {
  it('reads "N of M employees" when the plan sets a limit', () => {
    expect(limitBadgeText(12, 50)).toBe('12 of 50 employees');
    expect(limitBadgeText(0, 0)).toBe('0 of 0 employees');
  });

  it('reads "N employees" when the plan sets none', () => {
    expect(limitBadgeText(12, null)).toBe('12 employees');
    expect(limitBadgeText(12, undefined)).toBe('12 employees');
  });
});

describe('limitReached (W-84.2)', () => {
  it('is true only when a limit is set and used is at or over it', () => {
    expect(limitReached(50, 50)).toBe(true);
    expect(limitReached(51, 50)).toBe(true);
    expect(limitReached(12, 50)).toBe(false);
    expect(limitReached(12, null)).toBe(false);
  });

  it('names the server error code', () => {
    expect(EMPLOYEE_LIMIT_REACHED).toBe('EMPLOYEE_LIMIT_REACHED');
  });
});
