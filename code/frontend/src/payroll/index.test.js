import { describe, it, expect } from 'vitest';
import { routes, reducers, portalPanels } from './index.js';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';

describe('payroll module entry (W-45 §5, W-47.2 §5, W-47.3 §5, W-47.6 §5)', () => {
  it('registers the pay run, tax and prior payroll routes as elements', () => {
    expect(routes.map((r) => r.path)).toEqual([
      '/payroll/runs',
      '/payroll/runs/new-off-cycle',
      '/payroll/runs/:id',
      '/payroll/settings/tax-declaration',
      '/payroll/tax-declarations/:employeeId/:fy',
      '/payroll/prior-payroll',
    ]);
    routes.forEach((r) => expect(r.element).toBeTruthy());
  });

  it('registers the payrun and tax slices', () => {
    expect(reducers).toEqual({ payrun: payrunReducer, tax: taxReducer });
  });

  it('offers the taxDeclaration portal panel as a component', () => {
    expect(portalPanels).toHaveLength(1);
    expect(portalPanels[0].code).toBe('taxDeclaration');
    expect(portalPanels[0].component).toBeTruthy();
    expect(portalPanels[0]).not.toHaveProperty('element');
  });
});
