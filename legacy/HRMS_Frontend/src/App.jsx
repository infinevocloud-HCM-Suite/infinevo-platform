import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom';
import LoginPage from './components/auth/LoginPage';
import RegistrationPage from './components/auth/RegistrationPage';
import UserManagement from './components/userManagement/UserMangement';
import UpdateUser from './components/userManagement/UserUpdate';
import CreateActionPage from './components/userManagement/CreateActionPage';
import CreateRolePage from './components/userManagement/CreateRolePage';
import ListActions from './components/userManagement/ListActions';
import ListRoles from './components/userManagement/ListRoles';
import RoleActionMapping from './components/userManagement/RoleActionMapping';
import Dashboard from './components/adminDashboard/Dashboard';
import Sidebar from './components/Sidebar/Sidebar';
import EmployeeDashboard from './components/EmployeeDashboard/EmployeeDashboard';
import Employee from './components/employee/Employee';
import Profile from './components/Profile';
import AddEmployee from './components/employee/AddEmployee';
import Leave from './components/Leaves/Leave';
import Project from './components/Projects/Project';
import Task from './components/Projects/Task';
import EditEmployee from './components/employee/EditEmployee';
import EmployeeDetails from './components/employee/EmployeeDetails';
import ProtectedRoute from './components/context/ProtectedRoute';
import ContextProvider from './components/context/ContextProvider';
import TimesheetDetailPage from './components/EmployeeDashboard/TimesheetDetailPage';
import TimesheetForm from './components/EmployeeDashboard/TimesheetForm';
import LeaveBalance from './components/Leaves/LeaveBalance';
import MyProject from './components/EmployeeDashboard/MyProject';
import MyTask from './components/EmployeeDashboard/MyTask';
import AdminTimesheetManagement from './components/adminDashboard/AdminTimesheetManagement';
import TimesheetDetailView from './components/adminDashboard/TimesheetDetailView';
import NotificationSettings from './components/adminDashboard/NotificationSettings';
import TimesheetViewPage from './components/EmployeeDashboard/TimesheetViewPage';
import LeaveRequest from './components/adminDashboard/LeaveRequest';
import AdminLeaveBalance from './components/adminDashboard/AdminLeaveBalance';
import OvertimeRequest from './components/adminDashboard/OvertimeRequest';
import HolidayAdmin from './components/adminDashboard/HolidayAdmin';
import HolidayUser from './components/Leaves/HolidayUser';
import UserAttendance from './components/EmployeeDashboard/UserAttendence';
import AdminAttendance from './components/adminDashboard/AdminAttendance';
import ForgetPassword from './components/ForgetPassword';
import ResetPassword from './components/ResetPassword';
import LeaveType from './components/adminDashboard/LeaveType';
import HrDashboard from './components/hrDashboard/HrDashboard';
import ManagerDashboard from './components/managerDashboard/ManagerDashboard';
import SupervisorDashboard from './components/supervisorDashboard/SupervisorDashboard';
import UserMapping from './components/userManagement/UserMapping';
import EditRolePage from './components/userManagement/EditRolePage';
import EditActionPage from './components/userManagement/EditActionPage';

import UnauthorisedPage from './components/UnauthorisedPage';
import OverTimeForm from './components/Leaves/OverTimeForm';
import ActionProtectedRoute from './components/context/ActionProtectedRoute';
import { useContext } from 'react';
import { userContext } from './components/context/ContextProvider';
import EmployeeLeaves from './components/adminDashboard/EmployeeLeaves';
import EmployeeLeaveDetails from './components/adminDashboard/EmployeeLeaveDetails';
import TimesheetEditForm from './components/EmployeeDashboard/TimesheetEditForm';
import ManagerTimesheetManagement from './components/managerDashboard/ManagerTimesheetManagement';
import ManagerTimesheetDetailView from './components/managerDashboard/ManagerTimesheetDetailView';
import MyTeam from './components/managerDashboard/MyTeam';
import ReportingManagerDashboard from './components/reportingmanagerDashboard/ReportingManagerDashboard';
import ReportingTeam from './components/reportingmanagerDashboard/ReportingTeam';
import ReportingManagerTimesheetManagement from './components/reportingmanagerDashboard/ReportingManagerTimesheetManagement';
import ReportingManagerTimesheetDetailView from './components/reportingmanagerDashboard/ReportingManagerTimesheetDetailView';
import ViewLeaveRequestDetails from './components/adminDashboard/ViewLeaveRequestDetails';
import ChangePassword from './components/ChangePassword';
import ChooseRole from './pages/ChooseRole';

function App() {
  const { roles, activeRole, loading } = useContext(userContext);

  // prevent route rendering until context is ready
  if (loading) {
    return <div style={{ textAlign: "center", marginTop: "20%" }}>Loading...</div>;
  }

  // small wrapper to render the appropriate dashboard based on context.activeRole
  const DashboardWrapper = () => {
    switch (activeRole?.toLowerCase()) {
      case "admin":
        return <Dashboard />;
      case "hr":
        return <HrDashboard />;
      case "manager":
        return <ManagerDashboard />;
      case "supervisor":
        return <SupervisorDashboard />;
      case "user":
        return <EmployeeDashboard />;
      case "reporting manager":
        return <ReportingManagerDashboard />;
      default:
        return <UnauthorisedPage />;
    }
  };

  return (
    <Router>
      <Routes>
        {/* Public Routes */}
        <Route path="/" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgetPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/unauthorized" element={<UnauthorisedPage />} />
        <Route path="/choose-role" element={<ChooseRole />} />

        {/* Role-based protected area (static param route) */}
        {/* This avoids removing the route when activeRole value changes */}
        <Route
          path="/:role/*"
          element={
            <ProtectedRoute allowedRoles={roles}>
              <Sidebar />
            </ProtectedRoute>
          }
        >
          {/* index => dashboard for the current logged-in role */}
          <Route index element={<DashboardWrapper />} />
          {/* Dashboard aliases so per-role navigate(...) paths resolve */}
<Route path="dashboard" element={<DashboardWrapper />} />
<Route path="hr-dashboard" element={<DashboardWrapper />} />
<Route path="manager-dashboard" element={<DashboardWrapper />} />
<Route path="supervisor-dashboard" element={<DashboardWrapper />} />
<Route path="reporting-manager-dashboard" element={<DashboardWrapper />} />
<Route path="employee-dashboard" element={<DashboardWrapper />} />


          {/* My Team  */}
          <Route path="my-team" element={
            <ActionProtectedRoute requiredActions={["VIEW_MY_TEAM"]}>
              <MyTeam />
            </ActionProtectedRoute>
          } />

          {/* reporting Team  */}
          <Route path="reporting-team" element={
            <ActionProtectedRoute requiredActions={["VIEW_REPORTING_TEAM"]}>
              <ReportingTeam />
            </ActionProtectedRoute>
          } />

          {/* Employee Module Management Routes */}
          <Route path="employees" element={
            <ActionProtectedRoute requiredActions={["MANAGE_EMPLOYEES"]}>
              <Employee />
            </ActionProtectedRoute>
          } />

          <Route path="add-employee" element={
            <ActionProtectedRoute requiredActions={["ADD_EMPLOYEE"]}>
              <AddEmployee />
            </ActionProtectedRoute>
          } />

          <Route path="edit-employee/:id" element={
            <ActionProtectedRoute requiredActions={["UPDATE_EMPLOYEE"]}>
              <EditEmployee />
            </ActionProtectedRoute>
          } />

          <Route path="employee-details/:id" element={
            <ActionProtectedRoute requiredActions={["VIEW_EMPLOYEE"]}>
              <EmployeeDetails />
            </ActionProtectedRoute>
          } />

          {/* User Management and Access Module Routes */}
          <Route path="user-management" element={
            <ActionProtectedRoute requiredActions={["MANAGE_USERS_MANAGEMENT"]}>
              <UserManagement />
            </ActionProtectedRoute>
          } />

          <Route path="register" element={
            <ActionProtectedRoute requiredActions={["CREATE_USER"]}>
              <RegistrationPage />
            </ActionProtectedRoute>
          } />

          <Route path="update-user/:userId" element={
            <ActionProtectedRoute requiredActions={["UPDATE_USER"]}>
              <UpdateUser />
            </ActionProtectedRoute>
          } />

          <Route path="user-mapping/:userId/:name" element={
            <ActionProtectedRoute requiredActions={["GRANTING_PERMISSIONS"]}>
              <UserMapping />
            </ActionProtectedRoute>
          } />

          <Route path="create-action" element={
            <ActionProtectedRoute requiredActions={["CREATE_ACTIONS"]}>
              <CreateActionPage />
            </ActionProtectedRoute>
          } />

          <Route path="list-actions" element={
            <ActionProtectedRoute requiredActions={["VIEW_LIST_ACTIONS"]}>
              <ListActions />
            </ActionProtectedRoute>
          } />

          <Route path="edit-action/:actionId" element={
            <ActionProtectedRoute requiredActions={["UPDATE_ACTIONS"]}>
              <EditActionPage />
            </ActionProtectedRoute>
          } />

          <Route path="create-role" element={
            <ActionProtectedRoute requiredActions={["CREATE_ROLE"]}>
              <CreateRolePage />
            </ActionProtectedRoute>
          } />

          <Route path="list-roles" element={
            <ActionProtectedRoute requiredActions={["VIEW_LIST_ROLES"]}>
              <ListRoles />
            </ActionProtectedRoute>
          } />

          <Route path="roles/mapping/:roleId" element={
            <ActionProtectedRoute requiredActions={["PERMISSION_MAPPING"]}>
              <RoleActionMapping />
            </ActionProtectedRoute>
          } />

          <Route path="edit-role/:roleId" element={
            <ActionProtectedRoute requiredActions={["UPDATE_ROLE"]}>
              <EditRolePage />
            </ActionProtectedRoute>
          } />

          {/* Projects and Tasks Module Routes */}
          <Route path="projects" element={
            <ActionProtectedRoute requiredActions={["MANAGE_PROJECTS"]}>
              <Project />
            </ActionProtectedRoute>
          } />

          <Route path="projects/tasks" element={
            <ActionProtectedRoute requiredActions={["MANAGE_TASKS"]}>
              <Task />
            </ActionProtectedRoute>
          } />

          <Route path="myproject" element={
            <ActionProtectedRoute requiredActions={["VIEW_PROJECT"]}>
              <MyProject />
            </ActionProtectedRoute>
          } />

          <Route path="mytask" element={
            <ActionProtectedRoute requiredActions={["VIEW_TASK"]}>
              <MyTask />
            </ActionProtectedRoute>
          } />

          {/* Timesheet Module Routes */}
          <Route path="timesheets" element={
            <ActionProtectedRoute requiredActions={["MANAGE_TIMESHEETS"]}>
              <AdminTimesheetManagement />
            </ActionProtectedRoute>
          } />

          <Route path="timesheets/:id" element={
            <ActionProtectedRoute requiredActions={["VIEW_TIMESHEET_REPORT"]}>
              <TimesheetDetailView />
            </ActionProtectedRoute>
          } />

          <Route path="my-team-timesheets" element={
            <ActionProtectedRoute requiredActions={["MANAGER_MANAGE_TIMESHEETS"]}>
              <ManagerTimesheetManagement />
            </ActionProtectedRoute>
          } />

          <Route path="my-team-timesheets/:id" element={
            <ActionProtectedRoute requiredActions={["MANAGER_VIEW_TIMESHEET_REPORT"]}>
              <ManagerTimesheetDetailView />
            </ActionProtectedRoute>
          } />

          <Route path="my-reporting-team-timesheets" element={
            <ActionProtectedRoute requiredActions={["REPORTING_MANAGER_MANAGE_TIMESHEETS"]}>
              <ReportingManagerTimesheetManagement />
            </ActionProtectedRoute>
          } />

          <Route path="reporting-timesheets/:timesheetId/projects/:projectId" element={
            <ActionProtectedRoute requiredActions={["VIEW_TIMESHEET_REPORT"]}>
              <ReportingManagerTimesheetDetailView />
            </ActionProtectedRoute>
          } />

          <Route path="timesheets/notifications" element={
            <ActionProtectedRoute requiredActions={["NOTIFICATION_SETTINGS"]}>
              <NotificationSettings />
            </ActionProtectedRoute>
          } />

          <Route path="my-timesheet" element={
            <ActionProtectedRoute requiredActions={["ADD_ENTRY"]}>
              <TimesheetForm />
            </ActionProtectedRoute>
          } />

          <Route path="my-timesheet-detail" element={
            <ActionProtectedRoute requiredActions={["MANAGE_TIMESHEET"]}>
              <TimesheetDetailPage />
            </ActionProtectedRoute>
          } />

          <Route path="my-timesheet-view" element={
            <ActionProtectedRoute requiredActions={["VIEW_TIMESHEET"]}>
              <TimesheetViewPage />
            </ActionProtectedRoute>
          } />

          <Route path="edit-my-timesheet/:timesheetId" element={
            <ActionProtectedRoute requiredActions={["ADD_ENTRY"]}>
              <TimesheetEditForm />
            </ActionProtectedRoute>
          } />

          {/* Attendance Routes  */}
          <Route path="attendance" element={
            <ActionProtectedRoute requiredActions={["MANAGE_ATTENDANCES"]}>
              <AdminAttendance />
            </ActionProtectedRoute>
          } />

          <Route path="my-attendance" element={
            <ActionProtectedRoute requiredActions={["VIEW_ATTENDANCE"]}>
              <UserAttendance />
            </ActionProtectedRoute>
          } />

          {/* Leave Management Module Routes */}
          <Route path="leaves" element={
            <ActionProtectedRoute requiredActions={["MANAGE_LEAVES"]}>
              <LeaveRequest />
            </ActionProtectedRoute>
          } />

          <Route path="leaves/:id" element={
            <ActionProtectedRoute requiredActions={["VIEW_LEAVE_REQUEST_DETAILS"]}>
              <ViewLeaveRequestDetails />
            </ActionProtectedRoute>
          } />

          <Route path="leaves-type" element={
            <ActionProtectedRoute requiredActions={["MANAGE_LEAVE_TYPES"]}>
              <LeaveType />
            </ActionProtectedRoute>
          } />

          <Route path="leave-balance" element={
            <ActionProtectedRoute requiredActions={["MANAGE_LEAVE_BALANCES"]}>
              <AdminLeaveBalance />
            </ActionProtectedRoute>
          } />

          <Route path="employee-leave-details" element={
            <ActionProtectedRoute requiredActions={["MANAGE_EMPLOYEES_LEAVES_DETAILS"]}>
              <EmployeeLeaves />
            </ActionProtectedRoute>
          } />

          <Route path="employee-leaves/:employeeId" element={
            <ActionProtectedRoute requiredActions={["VIEW_EMPLOYEES_LEAVES_BALANCES"]}>
              <EmployeeLeaveDetails />
            </ActionProtectedRoute>
          } />

          <Route path="overtime-request" element={
            <ActionProtectedRoute requiredActions={["MANAGE_OVERTIME_REQUESTS"]}>
              <OvertimeRequest />
            </ActionProtectedRoute>
          } />

          <Route path="holiday" element={
            <ActionProtectedRoute requiredActions={["MANAGE_HOLIDAYS"]}>
              <HolidayAdmin />
            </ActionProtectedRoute>
          } />

          <Route path="apply-leaves" element={
            <ActionProtectedRoute requiredActions={["APPLY_LEAVE"]}>
              <Leave />
            </ActionProtectedRoute>
          } />

          <Route path="my-leave-balance" element={
            <ActionProtectedRoute requiredActions={["VIEW_LEAVE_BALANCE"]}>
              <LeaveBalance />
            </ActionProtectedRoute>
          } />

          <Route path="overtime-form" element={
            <ActionProtectedRoute requiredActions={["APPLY_OVERTIME_REQUEST"]}>
              <OverTimeForm />
            </ActionProtectedRoute>
          } />

          <Route path="my-holiday" element={
            <ActionProtectedRoute requiredActions={["VIEW_HOLIDAY"]}>
              <HolidayUser />
            </ActionProtectedRoute>
          } />

          {/* Profile Routes  */}
          <Route path="profile" element={
            <ActionProtectedRoute requiredActions={["VIEW_PROFILE"]}>
              <Profile />
            </ActionProtectedRoute>
          } />

          {/* Change Password Route */}
          <Route path="change-password" element={
            <ActionProtectedRoute requiredActions={["CHANGE_PASSWORD"]}>
              <ChangePassword />
            </ActionProtectedRoute>
          } />
        </Route>

        {/* Catch-all route */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

export default App;
