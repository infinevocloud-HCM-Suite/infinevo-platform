import React, { lazy } from 'react';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';

const RunList = lazy(() =>
  import('./payrun/RunList.jsx').then((m) => ({ default: m.RunList }))
);
const OffCycleCreate = lazy(() =>
  import('./payrun/OffCycleCreate.jsx').then((m) => ({ default: m.OffCycleCreate }))
);
const RunPage = lazy(() =>
  import('./payrun/RunPage.jsx').then((m) => ({ default: m.RunPage }))
);

const TaxWindowScreen = lazy(() =>
  import('./tax/TaxWindowScreen.jsx').then((m) => ({ default: m.TaxWindowScreen }))
);
const OfficerDeclarationView = lazy(() =>
  import('./tax/OfficerDeclarationView.jsx').then((m) => ({ default: m.OfficerDeclarationView }))
);
const DeclarationPage = lazy(() =>
  import('./tax/DeclarationPage.jsx').then((m) => ({ default: m.DeclarationPage }))
);
const PriorPayrollPage = lazy(() =>
  import('./priorpayroll/PriorPayrollPage.jsx').then((m) => ({ default: m.PriorPayrollPage }))
);

// Payroll module entry point (W-45 §5, W-47.2 §5, W-47.3 §5, W-47.6 §5).
// Screens and slices land here as Payroll features are built.
export const routes = [
  { path: '/payroll/runs', element: React.createElement(RunList) },
  { path: '/payroll/runs/new-off-cycle', element: React.createElement(OffCycleCreate) },
  { path: '/payroll/runs/:id', element: React.createElement(RunPage) },
  { path: '/payroll/settings/tax-declaration', element: React.createElement(TaxWindowScreen) },
  {
    path: '/payroll/tax-declarations/:employeeId/:fy',
    element: React.createElement(OfficerDeclarationView),
  },
  { path: '/payroll/prior-payroll', element: React.createElement(PriorPayrollPage) },
];

export const reducers = {
  payrun: payrunReducer,
  tax: taxReducer,
};

// Components the `/me` portal mounts by panel code (W-47.3 §5, §14 decision 1). Lazy, so the
// portal renders them inside a Suspense boundary, as AppShell does for routes.
export const portalPanels = [{ code: 'taxDeclaration', component: DeclarationPage }];
