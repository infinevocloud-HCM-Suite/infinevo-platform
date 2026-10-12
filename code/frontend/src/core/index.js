import React, { lazy } from 'react';
import { Navigate } from 'react-router-dom';
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
const EmployeeImport = lazy(() =>
  import('./employee/EmployeeImport.jsx').then((m) => ({ default: m.EmployeeImport }))
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

const UsersScreen = lazy(() =>
  import('./users/UsersScreen.jsx').then((m) => ({ default: m.UsersScreen }))
);
const AcceptInvitation = lazy(() =>
  import('./invitation/AcceptInvitation.jsx').then((m) => ({ default: m.AcceptInvitation }))
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

const RolesScreen = lazy(() =>
  import('./roles/RolesScreen.jsx').then((m) => ({ default: m.RolesScreen }))
);
const AuditLogScreen = lazy(() =>
  import('./audit/AuditLogScreen.jsx').then((m) => ({ default: m.AuditLogScreen }))
);

const CompanyProfile = lazy(() =>
  import('./settings/CompanyProfile.jsx').then((m) => ({ default: m.CompanyProfile }))
);

const PlatformHome = lazy(() =>
  import('./admin/PlatformHome.jsx').then((m) => ({ default: m.PlatformHome }))
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

const INVITATIONS_TAB = { to: '/users?tab=invitations', replace: true };

// Employee, leave, holidays, organisation setup, approvals. Available to every tenant.
// Screens and slices land here as their work items are built.
export const routes = [
  { path: '/employees', element: React.createElement(EmployeeList) },
  { path: '/employees/new', element: React.createElement(EmployeeCreate) },
  // W-73.7: beneath the Employees item, no menu entry of its own; the screen checks core.employee.create.
  { path: '/employees/import', element: React.createElement(EmployeeImport) },
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
  // W-73.4: one screen for accounts, roles and invitations; the two old invitation paths redirect to it.
  { path: '/users', element: React.createElement(UsersScreen) },
  // D-73: the Roles and Audit log menu items (NavigationCatalogue core.roles, core.audit).
  { path: '/roles', element: React.createElement(RolesScreen) },
  { path: '/audit', element: React.createElement(AuditLogScreen) },
  { path: '/invitations/users', mountWith: '/users', element: React.createElement(Navigate, INVITATIONS_TAB) },
  { path: '/invitations/employees', mountWith: '/users', element: React.createElement(Navigate, INVITATIONS_TAB) },
  { path: '/leave/types', element: React.createElement(LeaveTypes) },
  { path: '/leave/allocations', element: React.createElement(Allocations) },
  { path: '/leave/requests', element: React.createElement(LeaveRequests) },
  { path: '/leave/requests/new', element: React.createElement(RecordLeave) },
  // D-72: an approver opens a leave request from the inbox, so it mounts with /approvals too.
  { path: '/leave/requests/:id', mountWith: '/approvals', element: React.createElement(LeaveRequestDetail) },
  { path: '/leave/employees/:id', element: React.createElement(EmployeeLeave) },
  { path: '/leave/import', element: React.createElement(LeaveImport) },
  // W-73.1: mounted when the feed carries `core.settings.company`.
  { path: '/settings/company', element: React.createElement(CompanyProfile) },
  // Platform staff only: mounted when the feed carries `core.admin.home` (W-73.2) or `core.tenants`
  // (W-65.1 §4, W-65.3 §5).
  { path: '/admin', element: React.createElement(PlatformHome) },
  { path: '/admin/tenants', element: React.createElement(TenantList) },
  { path: '/admin/tenants/new', element: React.createElement(TenantCreate) },
  { path: '/admin/tenants/:id', element: React.createElement(TenantDetail) },
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
