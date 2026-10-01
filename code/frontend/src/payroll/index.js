import React, { lazy } from 'react';
import payrunReducer from './payrun/payrunSlice.js';

const RunList = lazy(() =>
  import('./payrun/RunList.jsx').then((m) => ({ default: m.RunList }))
);
const OffCycleCreate = lazy(() =>
  import('./payrun/OffCycleCreate.jsx').then((m) => ({ default: m.OffCycleCreate }))
);
const RunPage = lazy(() =>
  import('./payrun/RunPage.jsx').then((m) => ({ default: m.RunPage }))
);

// Payroll module entry point (W-45 §5, W-47.2 §5).
// Screens and slices land here as Payroll features are built.
export const routes = [
  { path: '/payroll/runs', element: React.createElement(RunList) },
  { path: '/payroll/runs/new-off-cycle', element: React.createElement(OffCycleCreate) },
  { path: '/payroll/runs/:id', element: React.createElement(RunPage) },
];

export const reducers = {
  payrun: payrunReducer,
};
