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
const OvertimeLog = lazy(() =>
  import('./requests/OvertimeLog.jsx').then((m) => ({ default: m.OvertimeLog }))
);
const OvertimeDetail = lazy(() =>
  import('./requests/OvertimeDetail.jsx').then((m) => ({ default: m.OvertimeDetail }))
);
const LossOfPayLog = lazy(() =>
  import('./lossofpay/LossOfPayLog.jsx').then((m) => ({ default: m.LossOfPayLog }))
);
const MyWork = lazy(() => import('./projects/MyWork.jsx').then((m) => ({ default: m.MyWork })));

// W-112: Hiring & Recruitment Management
const JobOpeningList = lazy(() =>
  import('./hiring/openings/JobOpeningList.jsx').then((m) => ({ default: m.JobOpeningList }))
);
const JobOpeningDetail = lazy(() =>
  import('./hiring/openings/JobOpeningDetail.jsx').then((m) => ({ default: m.JobOpeningDetail }))
);
const CandidateList = lazy(() =>
  import('./hiring/candidates/CandidateList.jsx').then((m) => ({ default: m.CandidateList }))
);
const InterviewList = lazy(() =>
  import('./hiring/interviews/InterviewList.jsx').then((m) => ({ default: m.InterviewList }))
);
const OfferList = lazy(() =>
  import('./hiring/offers/OfferList.jsx').then((m) => ({ default: m.OfferList }))
);

// W-110: Surveys & Polls
const SurveyList = lazy(() =>
  import('./surveys/SurveyList.jsx').then((m) => ({ default: m.SurveyList }))
);
const SurveyAnalytics = lazy(() =>
  import('./surveys/SurveyAnalytics.jsx').then((m) => ({ default: m.SurveyAnalytics }))
);
const MySurveys = lazy(() =>
  import('../shell/portal/panels/MySurveys.jsx').then((m) => ({ default: m.MySurveys }))
);

// W-111: Asset Tracking
const AssetList = lazy(() =>
  import('./assets/AssetList.jsx').then((m) => ({ default: m.AssetList }))
);
const AssetDetail = lazy(() =>
  import('./assets/AssetDetail.jsx').then((m) => ({ default: m.AssetDetail }))
);
const MyAssets = lazy(() =>
  import('./assets/MyAssets.jsx').then((m) => ({ default: m.MyAssets }))
);
const AssetsTab = lazy(() =>
  import('./assets/AssetsTab.jsx').then((m) => ({ default: m.AssetsTab }))
);

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
  // D-72: a detail also mounts with the approvals inbox, which links to it; a manager holds no employee-only menu.
  { path: '/hrms/regularizations', element: React.createElement(MyRegularizations) },
  { path: '/hrms/regularizations/all', element: React.createElement(RegularizationLog) },
  { path: '/hrms/regularizations/:id', mountWith: ['/approvals', '/hrms/regularizations/all'], element: React.createElement(RegularizationDetail) },
  { path: '/hrms/overtime-requests', element: React.createElement(MyOvertime) },
  // W-68 §5: HR overtime list. `/all` before `/:id`.
  { path: '/hrms/overtime-requests/all', element: React.createElement(OvertimeLog) },
  { path: '/hrms/overtime-requests/:id', mountWith: ['/approvals', '/hrms/overtime-requests/all'], element: React.createElement(OvertimeDetail) },
  // D-140: HR's manual loss of pay (feed `hrms.loss_of_pay`).
  { path: '/hrms/loss-of-pay', element: React.createElement(LossOfPayLog) },
  // W-112: Hiring & Recruitment Management
  { path: '/hrms/hiring/openings', element: React.createElement(JobOpeningList) },
  { path: '/hrms/hiring/openings/:id', element: React.createElement(JobOpeningDetail) },
  { path: '/hrms/hiring/candidates', element: React.createElement(CandidateList) },
  { path: '/hrms/hiring/interviews', element: React.createElement(InterviewList) },
  { path: '/hrms/hiring/offers', element: React.createElement(OfferList) },
  // W-110: Surveys & Polls
  { path: '/hrms/surveys', element: React.createElement(SurveyList) },
  { path: '/hrms/surveys/:id/results', element: React.createElement(SurveyAnalytics) },
  { path: '/me/surveys', element: React.createElement(MySurveys) },
  // W-111: Asset Tracking
  { path: '/hrms/assets', element: React.createElement(AssetList) },
  { path: '/hrms/assets/:id', element: React.createElement(AssetDetail) },
  { path: '/me/assets', element: React.createElement(MyAssets) },
];

export const reducers = {};

// Components the `/me` portal mounts by panel code (W-48.2 §5). Lazy, so the portal renders them inside its
// Suspense boundary, as for payroll's.
export const portalPanels = [
  { code: 'timesheet', component: MyTimesheetPanel },
  { code: 'surveys', component: MySurveys },
  { code: 'assets', component: MyAssets },
];

export const employeeTabs = [
  {
    key: 'assets',
    label: 'Assets',
    action: 'hrms.asset.read',
    component: AssetsTab,
    render: (props) => React.createElement(AssetsTab, props),
  },
];
