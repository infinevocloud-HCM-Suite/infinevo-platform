import { describe, it, expect } from 'vitest';
import { buildDefaultRows, pickBalancingEarning, isDefaultEarning } from './salaryDefaults.js';

function earning(overrides) {
  return {
    id: overrides.code.toLowerCase(),
    name: overrides.code,
    calculationType: 'FLAT',
    defaultValue: '0',
    percentageOf: null,
    includedInCtc: true,
    variable: false,
    fbpComponent: false,
    active: true,
    ...overrides,
  };
}

const BASIC = earning({ code: 'BASIC', calculationType: 'PERCENTAGE', defaultValue: '50.0000', percentageOf: 'CTC' });
const HRA = earning({ code: 'HRA', calculationType: 'PERCENTAGE', defaultValue: '50', percentageOf: 'BASIC' });
const SPECIAL = earning({ code: 'SPECIAL' });
const FIXED = earning({ code: 'FIXED_ALLOWANCE' });

describe('salaryDefaults (W-75.2 §7)', () => {
  it('pre-fills Basic 50% of CTC and HRA 50% of Basic, with Special as the balancing row', () => {
    const { rows, balancingId } = buildDefaultRows([BASIC, HRA, SPECIAL]);

    expect(balancingId).toBe('special');
    expect(rows).toEqual([
      { componentId: 'basic', calculationType: 'PERCENTAGE', value: '50.0000', percentageOf: 'CTC', enabled: true },
      { componentId: 'hra', calculationType: 'PERCENTAGE', value: '50', percentageOf: 'BASIC', enabled: true },
      { componentId: 'special', calculationType: 'FLAT', value: '0', percentageOf: null, enabled: true },
    ]);
  });

  it('leaves out inactive, variable, FBP and zero-default earnings', () => {
    const inactive = earning({ code: 'OLD', defaultValue: '100', active: false });
    const variable = earning({ code: 'BONUS', defaultValue: '100', variable: true });
    const fbp = earning({ code: 'FUEL', defaultValue: '100', fbpComponent: true });
    const zero = earning({ code: 'CONVEYANCE', defaultValue: '0.0000' });
    const missing = earning({ code: 'LTA', defaultValue: null });
    const outsideCtc = earning({ code: 'OUTSIDE', defaultValue: '100', includedInCtc: false });

    const { rows } = buildDefaultRows([BASIC, inactive, variable, fbp, zero, missing, outsideCtc]);

    expect(rows.map((r) => r.componentId)).toEqual(['basic']);
    [inactive, variable, fbp, zero, missing, outsideCtc].forEach((e) => expect(isDefaultEarning(e)).toBe(false));
  });

  it('chooses SPECIAL over FIXED_ALLOWANCE when both are active', () => {
    expect(pickBalancingEarning([BASIC, FIXED, SPECIAL])?.code).toBe('SPECIAL');
  });

  it('chooses FIXED_ALLOWANCE when there is no SPECIAL', () => {
    expect(pickBalancingEarning([BASIC, FIXED])?.code).toBe('FIXED_ALLOWANCE');
    expect(pickBalancingEarning([BASIC, FIXED, { ...SPECIAL, active: false }])?.code).toBe('FIXED_ALLOWANCE');
  });

  it('chooses no balancing row when neither is active', () => {
    const { rows, balancingId } = buildDefaultRows([BASIC, HRA, { ...FIXED, active: false }]);

    expect(balancingId).toBeNull();
    expect(rows.map((r) => r.componentId)).toEqual(['basic', 'hra']);
  });

  it('adds the balancing earning once even when it has a default of its own', () => {
    const special = { ...SPECIAL, defaultValue: '1000' };
    const { rows } = buildDefaultRows([BASIC, special]);

    expect(rows.filter((r) => r.componentId === 'special')).toHaveLength(1);
    expect(rows[rows.length - 1]).toMatchObject({ componentId: 'special', calculationType: 'FLAT' });
  });
});
