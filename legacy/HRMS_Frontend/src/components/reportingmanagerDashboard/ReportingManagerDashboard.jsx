import React from "react";
import { useContext, useEffect, useState } from "react";
import { Outlet, useNavigate, useMatch } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Box,
  Avatar,
  TextField,
  Snackbar,
  Alert,
  Grid,
  Card,
  Chip,
  LinearProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  useTheme,
  IconButton,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TablePagination
} from "@mui/material";
import {
  Person as PersonIcon,
  Logout as LogoutIcon,
  People,
  PendingActions,
  ChevronRight,
  MoreVert,
  Event,
  CheckCircle,
  CalendarToday,
  Refresh,
  Male as MaleIcon,
  Female as FemaleIcon,
  Close
} from "@mui/icons-material";
import axios from "axios";
import Sidebar from "../Sidebar/Sidebar";
import { userContext } from "../context/ContextProvider";
import API_BASE_URL from "../config/apiConfig";
import UserService from "../service/UserService";

const ReportingManagerDashboard = () => {
  const navigate = useNavigate();
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const theme = useTheme();

  const [searchQuery, setSearchQuery] = useState("");
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [profileData, setProfileData] = useState(null);
  const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);
  const [onLeaveEmployees, setOnLeaveEmployees] = useState([]);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const [dashboardData, setDashboardData] = useState({
    teamMembers: [],
    pendingTimesheets: [],
    pendingLeaves: [],
    attendanceStats: {
      presentToday: 0,
      onLeave: 0,
      absent: 0
    }
  });

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      if (!token) throw new Error("No authentication token found");

      const profileResponse = await UserService.getCompleteProfile(token);
      
      if (profileResponse.employeeData) {
        setProfileData(profileResponse.employeeData);
      } else {
        throw new Error("No employee data found in profile response");
      }

      const [
        teamMembersRes,
        pendingTimesheetsRes,
        pendingLeavesRes,
        attendanceRes,
        leavesRes
      ] = await Promise.all([
        axios.get(`${API_BASE_URL}/employees/reporting-manager`, {
          headers: { Authorization: `Bearer ${token}` }
        }),
        axios.get(`${API_BASE_URL}/api/timesheets/reporting-manager/timesheets`, {
          headers: { Authorization: `Bearer ${token}` },
          params: { status: 'SUBMITTED' }
        }),
        axios.get(`${API_BASE_URL}/leaves/reporting-manager`, {
          headers: { Authorization: `Bearer ${token}` }
        }),
        axios.get(`${API_BASE_URL}/attendance/my-reporting-team`, {
          headers: { Authorization: `Bearer ${token}` }
        }),
        axios.get(`${API_BASE_URL}/leaves/reporting-manager`, {
          headers: { Authorization: `Bearer ${token}` }
        })
      ]);

      const teamMembers = teamMembersRes.data || [];
      const pendingLeaves = pendingLeavesRes.data || [];
      const allLeaves = leavesRes.data || [];

      const pendingTimesheets = (pendingTimesheetsRes.data || []).map(timesheet => ({
        timesheetId: timesheet.timesheetId,
        empName: timesheet.employeeName,
        empId: timesheet.employeeId,
        projectName: timesheet.projects?.[0]?.projectName || 'N/A',
        projectId: timesheet.projects?.[0]?.projectId,
        weekStart: timesheet.weekStartDate,
        weekEnd: timesheet.weekEndDate,
        totalHours: timesheet.projects?.reduce((sum, project) => {
          return sum + (project.tasks?.reduce((taskSum, task) => {
            return taskSum + (task.days?.reduce((daySum, day) => daySum + (day.hours || 0), 0) || 0);
          }, 0) || 0);
        }, 0) || 0
      }));

      const today = new Date().toISOString().split('T')[0];
      
      // Get attendance data for today
      const todayAttendance = (attendanceRes.data || []).filter(a => 
        new Date(a.date || a.inTime).toISOString().split('T')[0] === today
      );

      const presentToday = todayAttendance.filter(a => a.status === 'Present').length;
      
      // Get employees on leave today
      const todayLeaves = allLeaves.filter(leave => {
        const start = new Date(leave.fromDate);
        const end = new Date(leave.toDate);
        const todayDate = new Date(today);
        return todayDate >= start && todayDate <= end && 
               (leave.reportingManagerStatus === 'APPROVED' || leave.hrStatus === 'APPROVED');
      });

      // Prepare on leave employees data
      const onLeaveEmployeesData = todayLeaves.map(leave => {
        const employee = teamMembers.find(member => member.employeeId === leave.employeeId);
        return {
          name: leave.employeeName || employee?.name || 'Unknown',
          id: leave.employeeId,
          // email: employee?.email || 'N/A',
          // leaveType: Object.keys(leave.manualDaysAllocation || {})[0] || leave.leaveType || 'N/A',
          startDate: leave.fromDate,
          endDate: leave.toDate,
          status: leave.hrStatus === 'APPROVED' ? 'APPROVED' : 
                 leave.reportingManagerStatus === 'APPROVED' ? 'APPROVED_BY_MANAGER' : 'PENDING'
        };
      });

      setOnLeaveEmployees(onLeaveEmployeesData);
      const onLeave = onLeaveEmployeesData.length;

      setDashboardData({
        teamMembers,
        pendingTimesheets,
        pendingLeaves: pendingLeaves.filter(leave => leave.reportingManagerStatus === 'PENDING'),
        attendanceStats: {
          presentToday,
          onLeave,
          absent: teamMembers.length - presentToday - onLeave
        }
      });

    } catch (err) {
      console.error("Error fetching reporting manager dashboard data:", err);
      setError(err.response?.data?.message || err.message || 'Failed to fetch dashboard data');
      setSnackbarOpen(true);
    } finally {
      setLoading(false);
    }
  };

  const handleCloseSnackbar = () => {
    setSnackbarOpen(false);
  };

  const handleLogout = () => {
    localStorage.removeItem("token", { path: '/' });
    localStorage.removeItem("role", { path: '/' });
    navigate("/", { replace: true });
  };

  const getTimeOfDay = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Morning';
    if (hour < 18) return 'Afternoon';
    return 'Evening';
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', { 
        weekday: 'short', 
        month: 'short', 
        day: 'numeric' 
      });
    } catch {
      return 'N/A';
    }
  };

  const handleTimesheetClick = (timesheetId, projectId) => {
    navigate(`/${role}/reporting-timesheets/${timesheetId}/projects/${projectId}`);
  };

  const handleLeaveClick = (leaveId) => {
    navigate(`/${role}/leaves/${leaveId}`);
  };

  const handleLeaveDialogOpen = () => {
    setLeaveDialogOpen(true);
  };

  const handleLeaveDialogClose = () => {
    setLeaveDialogOpen(false);
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const genderIcon = profileData?.personal?.gender?.toLowerCase() === 'male'
    ? <MaleIcon fontSize="small" color="primary" />
    : <FemaleIcon fontSize="small" color="secondary" />;

  const renderDashboardContent = () => {
    if (loading) {
      return (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      );
    }

    return (
      <Box sx={{ px: 2, pt: 0, pb: 2 }}>
        {/* Dashboard Header */}
        <Box sx={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center',
          mb: 4,
          background: 'linear-gradient(135deg, #3f51b5 0%, #2196f3 100%)',
          p: 3,
          borderRadius: '16px',
          color: 'white',
          boxShadow: '0 4px 20px rgba(0,0,0,0.1)'
        }}>
          <Box>
            <Typography variant="h3" fontWeight="bold" sx={{ mb: 1 }}>
              Good {getTimeOfDay()}, {profileData?.personal?.firstName || 'Reporting Manager'}
            </Typography>
            <Typography variant="body1" sx={{ opacity: 0.9 }}>
              {new Date().toLocaleDateString('en-US', { 
                weekday: 'long', 
                year: 'numeric', 
                month: 'long', 
                day: 'numeric' 
              })}
            </Typography>
          </Box>
          <Box>
            <IconButton onClick={fetchDashboardData} color="inherit">
              <Refresh />
            </IconButton>
          </Box>
        </Box>

        {/* Stats Cards */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {[ 
            { 
              icon: <People fontSize="large" />, 
              title: "Team Members", 
              value: dashboardData.teamMembers?.length || 0,
              color: 'primary',
              link: `/${role}/reporting-team`
            },
            { 
              icon: <PendingActions fontSize="large" />, 
              title: "Pending Timesheets", 
              value: dashboardData.pendingTimesheets?.length || 0,
              color: 'warning',
              onClick: () => navigate(`/${role}/my-reporting-team-timesheets`, { 
                state: { statusFilter: 'Pending' } 
              })
            },
            { 
              icon: <CalendarToday fontSize="large" />, 
              title: "Leave Requests", 
              value: dashboardData.pendingLeaves?.length || 0,
              color: 'info',
              onClick: () => navigate(`/${role}/leaves`, { 
                state: { statusFilter: 'PENDING_MANAGER_APPROVAL' } 
              })
            }
          ].map((stat, index) => (
            <Grid item xs={12} sm={6} md={4} key={index}>
              <Card 
                sx={{ 
                  p: 3,
                  borderRadius: '16px',
                  background: 'white',
                  cursor: 'pointer',
                  transition: 'all 0.3s ease',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.05)',
                  '&:hover': {
                    transform: 'translateY(-5px)',
                    boxShadow: '0 8px 24px rgba(0,0,0,0.1)'
                  }
                }}
                onClick={stat.onClick || (() => navigate(stat.link))}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Box sx={{
                    width: 56,
                    height: 56,
                    borderRadius: '12px',
                    bgcolor: `${stat.color}.light`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mr: 2,
                    flexShrink: 0
                  }}>
                    {React.cloneElement(stat.icon, { 
                      sx: { 
                        color: `${stat.color}.dark`,
                        fontSize: '28px'
                      } 
                    })}
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      {stat.title}
                    </Typography>
                    <Typography variant="h4" fontWeight="bold">
                      {stat.value}
                    </Typography>
                  </Box>
                </Box>
                <LinearProgress 
                  variant="determinate" 
                  value={60}
                  sx={{ 
                    height: 4,
                    borderRadius: 2,
                    backgroundColor: `${stat.color}.light`,
                    '& .MuiLinearProgress-bar': {
                      backgroundColor: `${stat.color}.main`
                    }
                  }}
                />
              </Card>
            </Grid>
          ))}
        </Grid>

        {/* Team Attendance Overview */}
        <Card sx={{
          p: 3,
          borderRadius: '16px',
          background: 'white',
          mb: 4,
          boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
        }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
            <Typography variant="h6" fontWeight="bold">
              Team Attendance Overview
            </Typography>
            {/* <Button 
              size="small"
              endIcon={<ChevronRight />}
              sx={{ textTransform: 'none', color: 'primary.main' }}
              onClick={() => navigate(`/${role}/attendance`)}
            >
              View Details
            </Button> */}
          </Box>
          
          <Grid container spacing={3}>
            {[
              { 
                label: 'Present Today', 
                value: dashboardData.attendanceStats.presentToday, 
                total: dashboardData.teamMembers?.length || 0, 
                color: 'success', 
                icon: <CheckCircle />,
                onClick: () => navigate(`/${role}/attendance`, { 
                  state: { 
                    statusFilter: 'Present',
                    dateFilter: new Date().toISOString().split('T')[0]
                  } 
                })
              },
              { 
                label: 'On Leave', 
                value: dashboardData.attendanceStats.onLeave, 
                total: dashboardData.teamMembers?.length || 0, 
                color: 'info', 
                icon: <Event />,
                onClick: handleLeaveDialogOpen
              },
              { 
                label: 'Absent', 
                value: dashboardData.attendanceStats.absent, 
                total: dashboardData.teamMembers?.length || 0, 
                color: 'error', 
                icon: <PersonIcon />,
                onClick: () => navigate(`/${role}/attendance`, { 
                  state: { 
                    statusFilter: 'Absent',
                    dateFilter: new Date().toISOString().split('T')[0]
                  } 
                })
              }
            ].map((item, i) => (
              <Grid item xs={12} sm={6} md={4} key={i}>
                <Card sx={{ 
                  p: 2,
                  borderRadius: '12px',
                  borderLeft: `4px solid ${theme.palette[item.color].main}`,
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    transform: 'translateY(-3px)',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                  },
                  cursor: 'pointer'
                }}
                onClick={item.onClick}>
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                    <Box sx={{
                      width: 40,
                      height: 40,
                      borderRadius: '50%',
                      bgcolor: `${item.color}.light`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      mr: 2
                    }}>
                      {React.cloneElement(item.icon, {
                        sx: { color: `${item.color}.main`, fontSize: '20px' }
                      })}
                    </Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      {item.label}
                    </Typography>
                  </Box>
                  <Typography variant="h4" fontWeight="bold" sx={{ color: `${item.color}.main`, mb: 1 }}>
                    {item.value}/{item.total}
                  </Typography>
                  <LinearProgress 
                    variant="determinate" 
                    value={(item.value / item.total) * 100} 
                    sx={{ 
                      height: 8,
                      borderRadius: 4,
                      backgroundColor: `${item.color}.light`,
                      '& .MuiLinearProgress-bar': {
                        backgroundColor: `${item.color}.main`,
                        borderRadius: 4
                      }
                    }}
                  />
                </Card>
              </Grid>
            ))}
          </Grid>
        </Card>

        {/* Recent Activity Section */}
        <Grid container spacing={3}>
          {/* Pending Timesheets */}
          <Grid item xs={12} md={6}>
            <Card sx={{ 
              p: 2,
              borderRadius: '16px',
              background: 'white',
              height: '100%',
              boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
            }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <PendingActions color="warning" sx={{ mr: 1 }} />
                  <Typography variant="subtitle1" fontWeight="bold">
                    Pending Timesheets
                  </Typography>
                </Box>
              </Box>
              
              <TableContainer component={Paper} elevation={0}>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ 
                      backgroundColor: 'primary.light',
                      '& th': {
                        fontWeight: 'bold',
                        color: 'primary.contrastText'
                      }
                    }}>
                      <TableCell>Employee</TableCell>
                      <TableCell>Project</TableCell>
                      <TableCell>Week</TableCell>
                      <TableCell align="right">Hours</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {dashboardData.pendingTimesheets?.slice(0, 5).map((timesheet) => (
                      <TableRow 
                        key={timesheet.timesheetId || Math.random()}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => handleTimesheetClick(timesheet.timesheetId, timesheet.projectId)}
                      >
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Avatar sx={{ width: 24, height: 24, mr: 1 }}>
                              {timesheet.empName?.charAt(0) || 'U'}
                            </Avatar>
                            {timesheet.empName || 'Unknown'}
                          </Box>
                        </TableCell>
                        <TableCell>{timesheet.projectName || 'N/A'}</TableCell>
                        <TableCell>
                          {formatDate(timesheet.weekStart)} - {formatDate(timesheet.weekEnd)}
                        </TableCell>
                        <TableCell align="right">
                          <Chip 
                            label={timesheet.totalHours || 0} 
                            size="small" 
                            color={timesheet.totalHours >= 40 ? 'success' : 'warning'}
                            variant="outlined"
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Card>
          </Grid>

          {/* Pending Leave Requests */}
          <Grid item xs={12} md={6}>
            <Card sx={{ 
              p: 2,
              borderRadius: '16px',
              background: 'white',
              height: '100%',
              boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
            }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <Event color="info" sx={{ mr: 1 }} />
                  <Typography variant="subtitle1" fontWeight="bold">
                    Pending Leave Requests
                  </Typography>
                </Box>
                <IconButton size="small" onClick={() => navigate(`/${role}/leaves`)}>
                  <MoreVert />
                </IconButton>
              </Box>
              
              <TableContainer component={Paper} elevation={0}>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ 
                      backgroundColor: 'info.light',
                      '& th': {
                        fontWeight: 'bold',
                        color: 'info.contrastText'
                      }
                    }}>
                      <TableCell>Employee</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Dates</TableCell>
                      <TableCell align="right">Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {dashboardData.pendingLeaves?.slice(0, 5).map((leave) => (
                      <TableRow 
                        key={leave.id || Math.random()}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => handleLeaveClick(leave.id)}
                      >
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Avatar sx={{ width: 24, height: 24, mr: 1 }}>
                              {leave.employeeName?.charAt(0) || 'U'}
                            </Avatar>
                            {leave.employeeName || 'Unknown'}
                          </Box>
                        </TableCell>
                        <TableCell>{leave.leaveType || 'N/A'}</TableCell>
                        <TableCell>
                          {formatDate(leave.startDate)} - {formatDate(leave.endDate)}
                        </TableCell>
                        <TableCell align="right">
                          <Chip 
                            label={leave.status || 'PENDING'} 
                            size="small" 
                            color="warning"
                            variant="outlined"
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Card>
          </Grid>
        </Grid>

        {/* Employees on Leave Dialog */}
        <Dialog
          open={leaveDialogOpen}
          onClose={handleLeaveDialogClose}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" fontWeight="bold">
                Employees on Leave Today ({onLeaveEmployees.length})
              </Typography>
              <IconButton onClick={handleLeaveDialogClose}>
                <Close />
              </IconButton>
            </Box>
          </DialogTitle>
          <DialogContent>
            {onLeaveEmployees.length === 0 ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 100 }}>
                <Typography variant="body1" color="text.secondary">
                  No employees are on leave today
                </Typography>
              </Box>
            ) : (
              <>
                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow sx={{ backgroundColor: 'info.light' }}>
                        <TableCell>Employee</TableCell>
                        <TableCell>Employee ID</TableCell>
                        {/* <TableCell>Email</TableCell>
                        <TableCell>Leave Type</TableCell> */}
                        <TableCell>Leave Duration</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {onLeaveEmployees
                        .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                        .map((employee, index) => (
                          <TableRow key={index}>
                            <TableCell>
                              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                <Avatar sx={{ width: 32, height: 32, mr: 1 }}>
                                  {employee.name?.charAt(0) || 'E'}
                                </Avatar>
                                {employee.name || 'Unknown'}
                              </Box>
                            </TableCell>
                            <TableCell>{employee.id || 'N/A'}</TableCell>
                            {/* <TableCell>{employee.email || 'N/A'}</TableCell>
                            <TableCell>{employee.leaveType || 'N/A'}</TableCell> */}
                            <TableCell>
                              {formatDate(employee.startDate)} - {formatDate(employee.endDate)}
                            </TableCell>
                            <TableCell>
                              <Chip 
                                label={employee.status} 
                                color={
                                  employee.status === 'APPROVED' ? 'success' :
                                  employee.status === 'APPROVED_BY_MANAGER' ? 'info' :
                                  'warning'
                                }
                                size="small"
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination
                  rowsPerPageOptions={[5, 10, 25]}
                  component="div"
                  count={onLeaveEmployees.length}
                  rowsPerPage={rowsPerPage}
                  page={page}
                  onPageChange={handleChangePage}
                  onRowsPerPageChange={handleChangeRowsPerPage}
                />
              </>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleLeaveDialogClose} color="primary">
              Close
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    );
  };

return (
  <>
    {/* Error Snackbar */}
    <Snackbar
      open={snackbarOpen}
      autoHideDuration={6000}
      onClose={handleCloseSnackbar}
      anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
      <Alert onClose={handleCloseSnackbar} severity="error" sx={{ width: '100%' }}>
        {error || 'An error occurred'}
      </Alert>
    </Snackbar>

    {/* Main Dashboard Content */}
    <Box
      component="main"
      sx={{
        flexGrow: 1,
        bgcolor: "#f8fafc",
        minHeight: "100vh",
        overflowY: "auto",
        pt: 0,
        p: 2, // only small padding inside
      }}
    >
      {renderDashboardContent()}
    </Box>
  </>
);

};

export default ReportingManagerDashboard;