import React, { lazy } from 'react';
import employeeReducer from './employee/employeeSlice.js';
import approvalReducer from './approvals/approvalSlice.js';
import leaveReducer from './leave/leaveSlice.js';
import impersonationReducer from './admin/impersonationSlice.js';

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

const UserInvitations = lazy(() =>
  import('./invitation/UserInvitations.jsx').then((m) => ({ default: m.UserInvitations }))
);
const EmployeeInvitations = lazy(() =>
  import('./invitation/EmployeeInvitations.jsx').then((m) => ({ default: m.EmployeeInvitations }))
);
const AcceptInvitation = lazy(() =>
  import('./invitation/AcceptInvitation.jsx').then((m) => ({ default: m.AcceptInvitation }))
);

const Roles = lazy(() =>
  import('./authz/Roles.jsx').then((m) => ({ default: m.Roles }))
);
const RoleForm = lazy(() =>
  import('./authz/RoleForm.jsx').then((m) => ({ default: m.RoleForm }))
);

const LeaveTypes = lazy(() =>
  import('./leave/LeaveTypes.jsx').then((m) => ({ default: m.LeaveTypes }))
);
const Allocations = lazy(() =>
  import('./leave/Allocations.jsx').then((m) => ({ default: m.Allocations }))
);
const LeaveRequests = lazy(() =>
  import('./leave/LeaveRequests.jsx').then((m) => ({ default: m.LeaveRequests }))
);
const RecordLeave = lazy(() =>
  import('./leave/RecordLeave.jsx').then((m) => ({ default: m.RecordLeave }))
);
const LeaveRequestDetail = lazy(() =>
  import('./leave/LeaveRequestDetail.jsx').then((m) => ({ default: m.LeaveRequestDetail }))
);
const EmployeeLeave = lazy(() =>
  import('./leave/EmployeeLeave.jsx').then((m) => ({ default: m.EmployeeLeave }))
);
const LeaveImport = lazy(() =>
  import('./leave/LeaveImport.jsx').then((m) => ({ default: m.LeaveImport }))
);

const TenantList = lazy(() =>
  import('./admin/TenantList.jsx').then((m) => ({ default: m.TenantList }))
);
const TenantCreate = lazy(() =>
  import('./admin/TenantCreate.jsx').then((m) => ({ default: m.TenantCreate }))
);
const TenantDetail = lazy(() =>
  import('./admin/TenantDetail.jsx').then((m) => ({ default: m.TenantDetail }))
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
  { path: '/invitations/users', element: React.createElement(UserInvitations) },
  { path: '/invitations/employees', element: React.createElement(EmployeeInvitations) },
  { path: '/leave/types', element: React.createElement(LeaveTypes) },
  { path: '/leave/allocations', element: React.createElement(Allocations) },
  { path: '/leave/requests', element: React.createElement(LeaveRequests) },
  { path: '/leave/requests/new', element: React.createElement(RecordLeave) },
  { path: '/leave/requests/:id', element: React.createElement(LeaveRequestDetail) },
  { path: '/leave/employees/:id', element: React.createElement(EmployeeLeave) },
  { path: '/leave/import', element: React.createElement(LeaveImport) },
  // Platform staff only: mounted when the feed carries `core.tenants` (W-65.1 §4, W-65.3 §5).
  { path: '/admin/tenants', element: React.createElement(TenantList) },
  { path: '/admin/tenants/new', element: React.createElement(TenantCreate) },
  { path: '/admin/tenants/:id', element: React.createElement(TenantDetail) },
  // Roles & permissions (W-11.1 §5, BUG-D4-02).
  { path: '/roles', element: React.createElement(Roles) },
  { path: '/roles/new', element: React.createElement(RoleForm) },
  { path: '/roles/:id', element: React.createElement(RoleForm) },
];

export const publicRoutes = [
  { path: '/invitations/accept', element: React.createElement(AcceptInvitation) },
];

export const reducers = {
  employee: employeeReducer,
  approvals: approvalReducer,
  leave: leaveReducer,
  impersonation: impersonationReducer,
};

export * from './portal/index.js';
export { Roles, RoleForm };
