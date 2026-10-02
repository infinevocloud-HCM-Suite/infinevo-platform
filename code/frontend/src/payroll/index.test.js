import { describe, it, expect } from 'vitest';
import { routes, reducers, portalPanels } from './index.js';
import taxReducer from './tax/taxSlice.js';

describe('payroll module entry (W-45 §5, W-47.3 §5)', () => {
  it('registers the two admin routes as elements', () => {
    expect(routes.map((r) => r.path)).toEqual([
      '/payroll/settings/tax-declaration',
      '/payroll/tax-declarations/:employeeId/:fy',
    ]);
    routes.forEach((r) => expect(r.element).toBeTruthy());
  });

  it('registers the tax slice', () => {
    expect(reducers).toEqual({ tax: taxReducer });
  });

  it('offers the taxDeclaration portal panel as a component', () => {
    expect(portalPanels).toHaveLength(1);
    expect(portalPanels[0].code).toBe('taxDeclaration');
    expect(portalPanels[0].component).toBeTruthy();
    expect(portalPanels[0]).not.toHaveProperty('element');
  });
});
