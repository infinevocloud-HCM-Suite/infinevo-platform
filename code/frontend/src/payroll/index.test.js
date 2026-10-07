import { describe, it, expect } from 'vitest';
import { routes, reducers, portalPanels, employeeTabs } from './index.js';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';
import salaryReducer from './salary/salarySlice.js';
import settingsReducer from './settings/settingsSlice.js';

describe('payroll module entry (W-45 §5, W-47.1a §5, W-47.1b §5, §5a, W-47.2 §5, W-47.3 §5, W-47.4 §5, W-47.6 §5)', () => {
  it('registers the dashboard, pay run, settings, officer tax declaration, salary component, prior payroll, claim and deduction routes as elements', () => {
    expect(routes.map((r) => r.path)).toEqual([
      '/payroll/dashboard',
      '/payroll/runs',
      '/payroll/runs/new-off-cycle',
      '/payroll/runs/:id',
      '/payroll/settings/pay-schedule',
      '/payroll/settings/epf',
      '/payroll/settings/esi',
      '/payroll/settings/professional-tax',
      '/payroll/settings/fbp',
      '/payroll/settings/tax-declaration',
      '/payroll/settings/tax-declaration/:year',
      '/employees/:employeeId/tax-declaration/:fy',
      '/payroll/components',
      '/payroll/prior-payroll',
      '/payroll/claims',
      '/payroll/claims/:id',
      '/payroll/deductions',
      '/payroll/tax-declarations/review',
      '/payroll/proof-of-investment',
    ]);
    routes.forEach((r) => expect(r.element).toBeTruthy());
  });

  it('registers the payrun, tax, salary, and settings slices', () => {
    expect(reducers).toEqual({
      payrun: payrunReducer,
      tax: taxReducer,
      salary: salaryReducer,
      settings: settingsReducer,
    });
  });

  it('offers the taxDeclaration and claims portal panels as components', () => {
    expect(portalPanels.map((p) => p.code)).toEqual(['taxDeclaration', 'claims']);
    portalPanels.forEach((p) => {
      expect(p.component).toBeTruthy();
      expect(p).not.toHaveProperty('element');
    });
  });

  it('exports employeeTabs for shell composition with Salary, FBP, and Tax declaration tabs', () => {
    expect(employeeTabs).toHaveLength(3);

    expect(employeeTabs[0].key).toBe('salary');
    expect(employeeTabs[0].label).toBe('Salary');
    expect(employeeTabs[0].action).toBe('payroll.salary.read');
    expect(employeeTabs[0].component).toBeTruthy();
    expect(typeof employeeTabs[0].render).toBe('function');

    expect(employeeTabs[1].key).toBe('fbp');
    expect(employeeTabs[1].label).toBe('FBP');
    expect(employeeTabs[1].action).toBe('payroll.fbp.read');
    expect(employeeTabs[1].component).toBeTruthy();
    expect(typeof employeeTabs[1].render).toBe('function');

    expect(employeeTabs[2].key).toBe('tax-declaration');
    expect(employeeTabs[2].label).toBe('Tax declaration');
    expect(employeeTabs[2].action).toBe('payroll.tax_declaration.read');
    expect(employeeTabs[2].component).toBeTruthy();
    expect(typeof employeeTabs[2].render).toBe('function');
  });
});
