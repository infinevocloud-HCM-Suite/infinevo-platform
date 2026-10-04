import React, { lazy } from 'react';

const TimesheetList = lazy(() =>
  import('./timesheet/TimesheetList.jsx').then((m) => ({ default: m.TimesheetList }))
);
const TimesheetWeek = lazy(() =>
  import('./timesheet/TimesheetWeek.jsx').then((m) => ({ default: m.TimesheetWeek }))
);
const MyTimesheetPanel = lazy(() =>
  import('./timesheet/MyTimesheetPanel.jsx').then((m) => ({ default: m.MyTimesheetPanel }))
);
const MyAttendance = lazy(() =>
  import('./attendance/MyAttendance.jsx').then((m) => ({ default: m.MyAttendance }))
);
const AttendanceLog = lazy(() =>
  import('./attendance/AttendanceLog.jsx').then((m) => ({ default: m.AttendanceLog }))
);
const AttendanceSettings = lazy(() =>
  import('./attendance/AttendanceSettings.jsx').then((m) => ({ default: m.AttendanceSettings }))
);
const ReviewPage = lazy(() =>
  import('./timesheetreview/ReviewPage.jsx').then((m) => ({ default: m.ReviewPage }))
);
const ReviewWeekView = lazy(() =>
  import('./timesheetreview/WeekView.jsx').then((m) => ({ default: m.WeekView }))
);
const ReviewEntryView = lazy(() =>
  import('./timesheetreview/EntryView.jsx').then((m) => ({ default: m.EntryView }))
);
const ProjectList = lazy(() =>
  import('./projects/ProjectList.jsx').then((m) => ({ default: m.ProjectList }))
);
const ProjectPage = lazy(() =>
  import('./projects/ProjectPage.jsx').then((m) => ({ default: m.ProjectPage }))
);
const HrmsDashboardPage = lazy(() =>
  import('./dashboard/HrmsDashboardPage.jsx').then((m) => ({ default: m.HrmsDashboardPage }))
);
const MyRegularizations = lazy(() =>
  import('./requests/MyRegularizations.jsx').then((m) => ({ default: m.MyRegularizations }))
);
const RegularizationLog = lazy(() =>
  import('./requests/RegularizationLog.jsx').then((m) => ({ default: m.RegularizationLog }))
);
const RegularizationDetail = lazy(() =>
  import('./requests/RegularizationDetail.jsx').then((m) => ({ default: m.RegularizationDetail }))
);
const MyOvertime = lazy(() =>
  import('./requests/MyOvertime.jsx').then((m) => ({ default: m.MyOvertime }))
);
const OvertimeDetail = lazy(() =>
  import('./requests/OvertimeDetail.jsx').then((m) => ({ default: m.OvertimeDetail }))
);
const MyWork = lazy(() => import('./projects/MyWork.jsx').then((m) => ({ default: m.MyWork })));

// HRMS module entry point (W-45 §5, W-48.2 §5). The week grid sits beneath `/hrms/timesheets`, the menu path
// the feed carries (`hrms.timesheets`), so it mounts with it.
export const routes = [
  { path: '/hrms/timesheets', element: React.createElement(TimesheetList) },
  { path: '/hrms/timesheets/week/:weekStart', element: React.createElement(TimesheetWeek) },
  { path: '/hrms/attendance', element: React.createElement(MyAttendance) },
  { path: '/hrms/attendance-log', element: React.createElement(AttendanceLog) },
  { path: '/hrms/attendance-settings', element: React.createElement(AttendanceSettings) },
  // W-48.3 §5: timesheet review beneath `/hrms/timesheet-review` (feed `hrms.timesheet_review`).
  { path: '/hrms/timesheet-review', element: React.createElement(ReviewPage) },
  { path: '/hrms/timesheet-review/:id', element: React.createElement(ReviewWeekView) },
  { path: '/hrms/timesheet-review/entries/:entryId', element: React.createElement(ReviewEntryView) },
  // W-48.1 §5: projects (feed `hrms.projects`) with the project page beneath it; my work (`hrms.my_work`).
  { path: '/hrms/projects', element: React.createElement(ProjectList) },
  { path: '/hrms/projects/:id', element: React.createElement(ProjectPage) },
  { path: '/hrms/my-work', element: React.createElement(MyWork) },
  // W-48.6 §5: the dashboard (feed `hrms.dashboard`).
  { path: '/hrms/dashboard', element: React.createElement(HrmsDashboardPage) },
  // W-48.5 §5: requests (feed `hrms.regularizations`, `hrms.overtime_requests`). `/all` before `/:id`.
  { path: '/hrms/regularizations', element: React.createElement(MyRegularizations) },
  { path: '/hrms/regularizations/all', element: React.createElement(RegularizationLog) },
  { path: '/hrms/regularizations/:id', element: React.createElement(RegularizationDetail) },
  { path: '/hrms/overtime-requests', element: React.createElement(MyOvertime) },
  { path: '/hrms/overtime-requests/:id', element: React.createElement(OvertimeDetail) },
];

export const reducers = {};

// Components the `/me` portal mounts by panel code (W-48.2 §5). Lazy, so the portal renders them inside its
// Suspense boundary, as for payroll's.
export const portalPanels = [{ code: 'timesheet', component: MyTimesheetPanel }];
