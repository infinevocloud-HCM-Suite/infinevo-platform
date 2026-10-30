import { describe, it, expect } from 'vitest';
import {
  uanError,
  esiNumberError,
  pfAccountNumberError,
  esiCeilingWarning,
  statutoryFieldRules,
} from './statutoryRules.js';
import { compareDecimal } from './decimal.js';

describe('statutoryRules (W-75.2 §7)', () => {
  it('UAN must be exactly 12 digits when EPF is ticked', () => {
    expect(uanError('10012345678', true)).toBe('UAN must be exactly 12 digits');
    expect(uanError('100123456789', true)).toBeNull();
    expect(uanError('1001234567890', true)).toBe('UAN must be exactly 12 digits');
    expect(uanError('10012345678A', true)).toBe('UAN must be exactly 12 digits');
  });

  it('UAN is not checked when EPF is off or the UAN is blank', () => {
    expect(uanError('123', false)).toBeNull();
    expect(uanError('', true)).toBeNull();
    expect(uanError(null, true)).toBeNull();
  });

  it('ESI number must be exactly 10 digits when ESI is ticked', () => {
    expect(esiNumberError('1100012345', true)).toBeNull();
    expect(esiNumberError('110001234', true)).toBe('ESI number must be exactly 10 digits');
    expect(esiNumberError('11000123456', true)).toBe('ESI number must be exactly 10 digits');
    expect(esiNumberError('110001234', false)).toBeNull();
    expect(esiNumberError('', true)).toBeNull();
  });

  it('PF account number of 33 characters is refused; 32 is accepted', () => {
    expect(pfAccountNumberError('A'.repeat(33))).toBe('At most 32 characters');
    expect(pfAccountNumberError('A'.repeat(32))).toBeNull();
    expect(pfAccountNumberError('')).toBeNull();
  });

  it('warns when gross is above the ESI ceiling, only when ESI is ticked', () => {
    const message = 'Gross is above ₹21,000 — no ESI will be deducted';
    expect(esiCeilingWarning('50000.0000', true)).toBe(message);
    expect(esiCeilingWarning('21000.0001', true)).toBe(message);
    expect(esiCeilingWarning('21000.0000', true)).toBeNull();
    expect(esiCeilingWarning('15000', true)).toBeNull();
    expect(esiCeilingWarning('50000', false)).toBeNull();
    expect(esiCeilingWarning(null, true)).toBeNull();
  });

  it('uses the company ceiling when one is given', () => {
    expect(esiCeilingWarning('22000', true, '25000.00')).toBeNull();
    expect(esiCeilingWarning('26000', true, '25000.00')).toBe('Gross is above ₹25,000 — no ESI will be deducted');
  });

  it('antd rules follow the EPF and ESI ticks', async () => {
    const ticks = { eligibleForPf: true, eligibleForEsi: false };
    const rules = statutoryFieldRules((name) => ticks[name]);

    await expect(rules.uan[0].validator(null, '123')).rejects.toThrow('UAN must be exactly 12 digits');
    await expect(rules.esiNumber[0].validator(null, '123')).resolves.toBeUndefined();
    await expect(rules.pfAccountNumber[0].validator(null, 'X'.repeat(33))).rejects.toThrow('At most 32 characters');
  });

  it('compares decimals digit by digit, never through a float', () => {
    expect(compareDecimal('12345678901234567.89', '12345678901234567.88')).toBe(1);
    expect(compareDecimal('0010.50', '10.5')).toBe(0);
    expect(compareDecimal('-1', '0')).toBe(-1);
    expect(compareDecimal('abc', '0')).toBeNull();
  });
});
