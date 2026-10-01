import React, { lazy } from 'react';
import employeeReducer from './employee/employeeSlice.js';
import approvalReducer from './approvals/approvalSlice.js';

const EmployeeList = lazy(() =>
  import('./employee/EmployeeList.jsx').then((m) => ({ default: m.EmployeeList }))
);
const EmployeeCreate = lazy(() =>
  import('./employee/EmployeeCreate.jsx').then((m) => ({ default: m.EmployeeCreate }))
);
const EmployeePage = lazy(() =>
  import('./employee/EmployeePage.jsx').then((m) => ({ default: m.EmployeePage }))
);

const Departments = lazy(() =>
  import('./org/Departments.jsx').then((m) => ({ default: m.Departments }))
);
const Designations = lazy(() =>
  import('./org/Designations.jsx').then((m) => ({ default: m.Designations }))
);
const WorkLocations = lazy(() =>
  import('./org/WorkLocations.jsx').then((m) => ({ default: m.WorkLocations }))
);
const WorkLocationForm = lazy(() =>
  import('./org/WorkLocationForm.jsx').then((m) => ({ default: m.WorkLocationForm }))
);

const Inbox = lazy(() =>
  import('./approvals/Inbox.jsx').then((m) => ({ default: m.Inbox }))
);
const InstanceDetail = lazy(() =>
  import('./approvals/InstanceDetail.jsx').then((m) => ({ default: m.InstanceDetail }))
);
const Delegations = lazy(() =>
  import('./approvals/Delegations.jsx').then((m) => ({ default: m.Delegations }))
);
const Definitions = lazy(() =>
  import('./approvals/Definitions.jsx').then((m) => ({ default: m.Definitions }))
);

const Calendars = lazy(() =>
  import('./holiday/Calendars.jsx').then((m) => ({ default: m.Calendars }))
);
const CalendarHolidays = lazy(() =>
  import('./holiday/CalendarHolidays.jsx').then((m) => ({ default: m.CalendarHolidays }))
);
const HolidayLookup = lazy(() =>
  import('./holiday/HolidayLookup.jsx').then((m) => ({ default: m.HolidayLookup }))
);

const SetupChecklist = lazy(() =>
  import('./setup/SetupChecklist.jsx').then((m) => ({ default: m.SetupChecklist }))
);

// Employee, leave, holidays, organisation setup, approvals. Available to every tenant.
// Screens and slices land here as their work items are built.
export const routes = [
  { path: '/employees', element: React.createElement(EmployeeList) },
  { path: '/employees/new', element: React.createElement(EmployeeCreate) },
  { path: '/employees/:id', element: React.createElement(EmployeePage) },
  { path: '/org/departments', element: React.createElement(Departments) },
  { path: '/org/designations', element: React.createElement(Designations) },
  { path: '/org/work-locations', element: React.createElement(WorkLocations) },
  { path: '/org/work-locations/new', element: React.createElement(WorkLocationForm) },
  { path: '/org/work-locations/:id/edit', element: React.createElement(WorkLocationForm) },
  { path: '/approvals', element: React.createElement(Inbox) },
  { path: '/approvals/:instanceId', element: React.createElement(InstanceDetail) },
  { path: '/approvals/delegations', element: React.createElement(Delegations) },
  { path: '/approvals/definitions', element: React.createElement(Definitions) },
  { path: '/holidays', element: React.createElement(Calendars) },
  { path: '/holidays/new', element: React.createElement(Calendars) },
  { path: '/holidays/lookup', element: React.createElement(HolidayLookup) },
  { path: '/holidays/:id/edit', element: React.createElement(Calendars) },
  { path: '/holidays/:id', element: React.createElement(CalendarHolidays) },
  { path: '/setup', element: React.createElement(SetupChecklist) },
];

export const reducers = {
  employee: employeeReducer,
  approvals: approvalReducer,
};
