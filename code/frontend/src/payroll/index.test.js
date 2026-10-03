import { describe, it, expect } from 'vitest';
import { routes, reducers, portalPanels, employeeTabs } from './index.js';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';
import salaryReducer from './salary/salarySlice.js';
import settingsReducer from './settings/settingsSlice.js';

describe('payroll module entry (W-45 §5, W-47.1a §5, W-47.1b §5, §5a, W-47.2 §5, W-47.3 §5)', () => {
  it('registers the pay run, settings, officer tax declaration, and salary component routes as elements', () => {
    expect(routes.map((r) => r.path)).toEqual([
      '/payroll/runs',
      '/payroll/runs/new-off-cycle',
      '/payroll/runs/:id',
      '/payroll/settings/pay-schedule',
      '/payroll/settings/epf',
      '/payroll/settings/esi',
      '/payroll/settings/professional-tax',
      '/payroll/settings/fbp',
      '/payroll/settings/tax-declaration',
      '/employees/:employeeId/tax-declaration/:fy',
      '/payroll/components',
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

  it('offers the taxDeclaration portal panel as a component', () => {
    expect(portalPanels).toHaveLength(1);
    expect(portalPanels[0].code).toBe('taxDeclaration');
    expect(portalPanels[0].component).toBeTruthy();
    expect(portalPanels[0]).not.toHaveProperty('element');
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
