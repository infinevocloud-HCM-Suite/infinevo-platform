import { describe, it, expect } from 'vitest';
import { routes, reducers, portalPanels, employeeTabs } from './index.js';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';
import salaryReducer from './salary/salarySlice.js';

describe('payroll module entry (W-45 §5, W-47.1a §5, W-47.2 §5, W-47.3 §5)', () => {
  it('registers the pay run, tax admin, and salary component routes as elements', () => {
    expect(routes.map((r) => r.path)).toEqual([
      '/payroll/runs',
      '/payroll/runs/new-off-cycle',
      '/payroll/runs/:id',
      '/payroll/settings/tax-declaration',
      '/payroll/tax-declarations/:employeeId/:fy',
      '/payroll/components',
    ]);
    routes.forEach((r) => expect(r.element).toBeTruthy());
  });

  it('registers the payrun, tax, and salary slices', () => {
    expect(reducers).toEqual({
      payrun: payrunReducer,
      tax: taxReducer,
      salary: salaryReducer,
    });
  });

  it('offers the taxDeclaration portal panel as a component', () => {
    expect(portalPanels).toHaveLength(1);
    expect(portalPanels[0].code).toBe('taxDeclaration');
    expect(portalPanels[0].component).toBeTruthy();
    expect(portalPanels[0]).not.toHaveProperty('element');
  });

  it('exports employeeTabs for shell composition with Salary tab', () => {
    expect(employeeTabs).toHaveLength(1);
    expect(employeeTabs[0].key).toBe('salary');
    expect(employeeTabs[0].label).toBe('Salary');
    expect(employeeTabs[0].action).toBe('payroll.salary.read');
    expect(employeeTabs[0].component).toBeTruthy();
    expect(typeof employeeTabs[0].render).toBe('function');
  });
});
