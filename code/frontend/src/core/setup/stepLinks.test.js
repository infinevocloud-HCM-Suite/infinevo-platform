/**
 * D-65 — every setup step the backend catalogue sends (SetupStepCatalogue.DEFAULT_STEPS) has an
 * Open link, and each link asks for the action of the menu item that mounts its route.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { stepLinks, stepActions } from './stepLinks.js';

const CATALOGUE = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../backend/core/src/main/java/com/infinevo/core/setup/SetupStepCatalogue.java'
);

function backendStepCodes() {
  const source = readFileSync(CATALOGUE, 'utf8');
  return [...source.matchAll(/new StepDefinition\("([A-Z_]+)"/g)].map((m) => m[1]);
}

describe('stepLinks', () => {
  it('has an Open link and a required action for every step code the backend catalogue sends', () => {
    const codes = backendStepCodes();
    expect(codes.length).toBeGreaterThan(0);
    expect(codes.filter((code) => !stepLinks[code])).toEqual([]);
    expect(codes.filter((code) => !stepActions[code])).toEqual([]);
  });

  it('D-65: the payroll steps open their payroll screens', () => {
    expect(stepLinks).toMatchObject({
      PAY_SCHEDULE: '/payroll/settings/pay-schedule',
      PRIOR_PAYROLL: '/payroll/prior-payroll',
      SALARY_COMPONENTS: '/payroll/components',
      EPF: '/payroll/settings/epf',
      ESI: '/payroll/settings/esi',
      PROFESSIONAL_TAX: '/payroll/settings/professional-tax',
    });
  });

  it('D-65: each payroll link asks for the action of the menu item that mounts its route', () => {
    expect(stepActions).toMatchObject({
      PAY_SCHEDULE: 'payroll.settings.manage',
      PRIOR_PAYROLL: 'payroll.run.read',
      SALARY_COMPONENTS: 'payroll.structure.read',
      EPF: 'payroll.settings.manage',
      ESI: 'payroll.settings.manage',
      PROFESSIONAL_TAX: 'payroll.settings.manage',
    });
  });

  it('keeps the core steps as they were', () => {
    expect(stepLinks.WORK_LOCATION).toBe('/org/work-locations');
    expect(stepLinks.EMPLOYEE).toBe('/employees');
    expect(stepActions.WORK_LOCATION).toBe('core.org.read');
    expect(stepActions.EMPLOYEE).toBe('core.employee.read');
  });
});
