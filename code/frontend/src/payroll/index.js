import React, { lazy } from 'react';
import payrunReducer from './payrun/payrunSlice.js';
import taxReducer from './tax/taxSlice.js';
import salaryReducer from './salary/salarySlice.js';
import settingsReducer from './settings/settingsSlice.js';
import { currentFy } from './tax/financialYear.js';

const RunList = lazy(() =>
  import('./payrun/RunList.jsx').then((m) => ({ default: m.RunList }))
);
const OffCycleCreate = lazy(() =>
  import('./payrun/OffCycleCreate.jsx').then((m) => ({ default: m.OffCycleCreate }))
);
const RunPage = lazy(() =>
  import('./payrun/RunPage.jsx').then((m) => ({ default: m.RunPage }))
);

const SettingsLayout = lazy(() =>
  import('./settings/SettingsLayout.jsx').then((m) => ({ default: m.SettingsLayout }))
);
const PayScheduleScreen = lazy(() =>
  import('./settings/PayScheduleScreen.jsx').then((m) => ({ default: m.PayScheduleScreen }))
);
const EpfScreen = lazy(() =>
  import('./settings/EpfScreen.jsx').then((m) => ({ default: m.EpfScreen }))
);
const EsiScreen = lazy(() =>
  import('./settings/EsiScreen.jsx').then((m) => ({ default: m.EsiScreen }))
);
const ProfessionalTaxScreen = lazy(() =>
  import('./settings/ProfessionalTaxScreen.jsx').then((m) => ({ default: m.ProfessionalTaxScreen }))
);
const FbpPlanScreen = lazy(() =>
  import('./settings/FbpPlanScreen.jsx').then((m) => ({ default: m.FbpPlanScreen }))
);
const FbpDeclarationTab = lazy(() =>
  import('./settings/FbpDeclarationTab.jsx').then((m) => ({ default: m.FbpDeclarationTab }))
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

const ComponentsScreen = lazy(() =>
  import('./salary/ComponentsScreen.jsx').then((m) => ({ default: m.ComponentsScreen }))
);
const SalaryTab = lazy(() =>
  import('./salary/SalaryTab.jsx').then((m) => ({ default: m.SalaryTab }))
);

// Payroll module entry point (W-45 §5, W-47.1a §5, W-47.1b §5, §5a, W-47.2 §5, W-47.3 §5).
export const routes = [
  { path: '/payroll/runs', element: React.createElement(RunList) },
  { path: '/payroll/runs/new-off-cycle', element: React.createElement(OffCycleCreate) },
  { path: '/payroll/runs/:id', element: React.createElement(RunPage) },
  {
    path: '/payroll/settings/pay-schedule',
    element: React.createElement(SettingsLayout, null, React.createElement(PayScheduleScreen)),
  },
  {
    path: '/payroll/settings/epf',
    element: React.createElement(SettingsLayout, null, React.createElement(EpfScreen)),
  },
  {
    path: '/payroll/settings/esi',
    element: React.createElement(SettingsLayout, null, React.createElement(EsiScreen)),
  },
  {
    path: '/payroll/settings/professional-tax',
    element: React.createElement(SettingsLayout, null, React.createElement(ProfessionalTaxScreen)),
  },
  {
    path: '/payroll/settings/fbp',
    element: React.createElement(SettingsLayout, null, React.createElement(FbpPlanScreen)),
  },
  {
    path: '/payroll/settings/tax-declaration',
    element: React.createElement(SettingsLayout, null, React.createElement(TaxWindowScreen)),
  },
  {
    path: '/employees/:employeeId/tax-declaration/:fy',
    element: React.createElement(OfficerDeclarationView),
  },
  { path: '/payroll/components', element: React.createElement(ComponentsScreen) },
];

export const reducers = {
  payrun: payrunReducer,
  tax: taxReducer,
  salary: salaryReducer,
  settings: settingsReducer,
};

// Components the `/me` portal mounts by panel code (W-47.3 §5, §14 decision 1). Lazy, so the
// portal renders them inside a Suspense boundary, as AppShell does for routes.
export const portalPanels = [{ code: 'taxDeclaration', component: DeclarationPage }];

// Employee page tabs exported for shell composition (W-47.1a §5, W-47.1b §5, §5a).
export const employeeTabs = [
  {
    key: 'salary',
    label: 'Salary',
    action: 'payroll.salary.read',
    component: SalaryTab,
    render: ({ employee }) => React.createElement(SalaryTab, { employeeId: employee?.id }),
  },
  {
    key: 'fbp',
    label: 'FBP',
    action: 'payroll.fbp.read',
    component: FbpDeclarationTab,
    render: ({ employee }) => React.createElement(FbpDeclarationTab, { employeeId: employee?.id }),
  },
  {
    key: 'tax-declaration',
    label: 'Tax declaration',
    action: 'payroll.tax_declaration.read',
    component: OfficerDeclarationView,
    render: ({ employee }) =>
      React.createElement(OfficerDeclarationView, {
        employeeId: employee?.id,
        fy: currentFy(),
      }),
  },
];
