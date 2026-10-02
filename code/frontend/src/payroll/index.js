import React, { lazy } from 'react';
import taxReducer from './tax/taxSlice.js';

const TaxWindowScreen = lazy(() =>
  import('./tax/TaxWindowScreen.jsx').then((m) => ({ default: m.TaxWindowScreen }))
);
const OfficerDeclarationView = lazy(() =>
  import('./tax/OfficerDeclarationView.jsx').then((m) => ({ default: m.OfficerDeclarationView }))
);
const DeclarationPage = lazy(() =>
  import('./tax/DeclarationPage.jsx').then((m) => ({ default: m.DeclarationPage }))
);

// Payroll module entry point (W-45 §5). Screens and slices land here as Payroll features are built.
export const routes = [
  { path: '/payroll/settings/tax-declaration', element: React.createElement(TaxWindowScreen) },
  {
    path: '/payroll/tax-declarations/:employeeId/:fy',
    element: React.createElement(OfficerDeclarationView),
  },
];

export const reducers = {
  tax: taxReducer,
};

// Components the `/me` portal mounts by panel code (W-47.3 §5, §14 decision 1). Lazy, so the
// portal renders them inside a Suspense boundary, as AppShell does for routes.
export const portalPanels = [{ code: 'taxDeclaration', component: DeclarationPage }];
