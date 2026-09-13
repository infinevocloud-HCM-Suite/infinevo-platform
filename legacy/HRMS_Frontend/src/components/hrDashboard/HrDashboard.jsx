import React, { useState, useEffect } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Box,
  TextField,
  CircularProgress,
  Snackbar,
  Alert,
  Grid,
  Card,
  CardContent,
  Chip,
  Badge,
  alpha,
  useTheme,
  styled,
  Pagination
} from "@mui/material";
import {
  People as PeopleIcon,
  CheckCircle as CheckCircleIcon,
  Event as EventIcon,
  EventAvailable as EventAvailableIcon,
  ChevronRight,
  Logout as LogoutIcon,
  PendingActions,
  Search as SearchIcon
} from "@mui/icons-material";
import UserService from "../service/UserService";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import ListItemIcon from '@mui/material/ListItemIcon';
import Avatar from '@mui/material/Avatar';
import { Doughnut } from 'react-chartjs-2';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';

ChartJS.register(ArcElement, Tooltip, Legend);

// API configuration
axios.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Styled components
const GradientCard = styled(Card)(({ theme }) => ({
  background: `linear-gradient(135deg, ${theme.palette.background.paper} 0%, ${alpha(theme.palette.background.default, 0.7)} 100%)`,
  borderRadius: '12px',
  boxShadow: '0 4px 20px 0 rgba(31, 38, 135, 0.1)',
  backdropFilter: 'blur(4px)',
  border: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
  transition: 'all 0.3s ease',
  '&:hover': {
    transform: 'translateY(-3px)',
    boxShadow: '0 8px 24px 0 rgba(31, 38, 135, 0.15)'
  }
}));

const StatIconWrapper = styled(Box)(({ theme, color }) => ({
  width: 48,
  height: 48,
  borderRadius: '10px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  marginRight: theme.spacing(2),
  background: `linear-gradient(135deg, ${alpha(theme.palette[color].main, 0.2)} 0%, ${alpha(theme.palette[color].dark, 0.2)} 100%)`,
  boxShadow: `0 4px 12px 0 ${alpha(theme.palette[color].main, 0.15)}`
}));

const HrDashboard = () => {
  const theme = useTheme();
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [dashboardData, setDashboardData] = useState({
    stats: null,
    recentAttendance: [],
    currentLeaves: [],
    upcomingLeaves: [],
    recentLeaves: [],
    notifications: [],
    holidays: []
  });

  // Pagination states for each leave section
  const [currentLeavesPage, setCurrentLeavesPage] = useState(1);
  const [upcomingLeavesPage, setUpcomingLeavesPage] = useState(1);
  const [recentLeavesPage, setRecentLeavesPage] = useState(1);
  const leavesPerPage = 4;

  useEffect(() => {
    const fetchAllData = async () => {
      try {
        setLoading(true);
        const token = localStorage.getItem("token");
        if (!token) {
          throw new Error("No authentication token found");
        }

        const profileResponse = await UserService.getCompleteProfile(token);
        setProfileData(profileResponse.employeeData);

        const [
          statsRes,
          attendanceRes,
          currentLeavesRes,
          upcomingLeavesRes,
          recentLeavesRes,
          notificationsRes,
          holidaysRes
        ] = await Promise.all([
          axios.get(`${API_BASE_URL}/hrdashboard/stats`),
          axios.get(`${API_BASE_URL}/hrdashboard/attendance/recent`),
          axios.get(`${API_BASE_URL}/hrdashboard/leaves/current`),
          axios.get(`${API_BASE_URL}/hrdashboard/leaves/upcoming`),
          axios.get(`${API_BASE_URL}/hrdashboard/leaves/recent`),
          axios.get(`${API_BASE_URL}/hrdashboard/notifications/recent`),
          axios.get(`${API_BASE_URL}/hrdashboard/holidays/upcoming`)
        ]);

        setDashboardData({
          stats: statsRes.data,
          recentAttendance: attendanceRes.data,
          currentLeaves: currentLeavesRes.data,
          upcomingLeaves: upcomingLeavesRes.data,
          recentLeaves: recentLeavesRes.data,
          notifications: notificationsRes.data,
          holidays: holidaysRes.data || []
        });

      } catch (error) {
        console.error("Failed to fetch data:", error);
        setError(error.response?.data?.message || "Failed to load dashboard data");
        setSnackbarOpen(true);
      } finally {
        setLoading(false);
      }
    };

    fetchAllData();
  }, []);

  const handleCloseSnackbar = (event, reason) => {
    if (reason === 'clickaway') {
      return;
    }
    setSnackbarOpen(false);
  };

  const handleLogout = async () => {
    try {
      await UserService.logout();
      navigate("/", { replace: true });
    } catch (error) {
      console.error("Logout error:", error);
      localStorage.removeItem("token", { path: '/' });
      localStorage.removeItem("role", { path: '/' });
      navigate("/", { replace: true });
    }
  };

  const getTimeOfDay = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Morning';
    if (hour < 18) return 'Afternoon';
    return 'Evening';
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    const options = { weekday: 'short', month: 'short', day: 'numeric' };
    return new Date(dateString).toLocaleDateString('en-US', options);
  };

  const getLeaveStatus = (leave) => {
    if (leave.hrStatus === 'APPROVED') return 'APPROVED';
    if (leave.reportingManagerStatus === 'REJECTED') return 'REJECTED_BY_MANAGER';
    if (leave.hrStatus === 'REJECTED') return 'REJECTED_BY_HR';
    if (leave.reportingManagerStatus === 'APPROVED' && leave.hrStatus === 'PENDING') return 'PENDING_HR_APPROVAL';
    if (leave.reportingManagerStatus === 'PENDING') return 'PENDING_MANAGER_APPROVAL';
    return 'UNKNOWN_STATUS';
  };

  const getStatusColor = (status) => {
    switch(status) {
      case 'APPROVED': return 'success';
      case 'REJECTED_BY_MANAGER':
      case 'REJECTED_BY_HR': return 'error';
      case 'PENDING_HR_APPROVAL': return 'info';
      case 'PENDING_MANAGER_APPROVAL': return 'warning';
      default: return 'default';
    }
  };

  const handlePresentTodayClick = () => {
    const today = new Date().toISOString().split('T')[0];
    navigate(`/${role}/attendance`, {
      state: {
        dateFilter: today,
        statusFilter: 'Present'
      }
    });
  };

  const handleAttendanceStatusClick = (status) => {
    const today = new Date().toISOString().split('T')[0];
    navigate(`/${role}/attendance`, {
      state: {
        dateFilter: today,
        statusFilter: status
      }
    });
  };

  const handleLeaveRequestsClick = () => {
    navigate(`/${role}/leaves`, {
      state: {
        statusFilter: 'PENDING_HR_APPROVAL'
      }
    });
  };

  const handleOnLeaveClick = () => {
    // Scroll to the Currently on Leave section
    const element = document.getElementById('currently-on-leave');
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleLeaveClick = (leaveId) => {
    navigate(`/${role}/leaves/${leaveId}`);
  };

  // Pagination functions for each leave section
  const handleCurrentLeavesPageChange = (event, value) => {
    setCurrentLeavesPage(value);
  };

  const handleUpcomingLeavesPageChange = (event, value) => {
    setUpcomingLeavesPage(value);
  };

  const handleRecentLeavesPageChange = (event, value) => {
    setRecentLeavesPage(value);
  };

  // Get paginated data for each leave section
  const getPaginatedLeaves = (leaves, currentPage) => {
    const startIndex = (currentPage - 1) * leavesPerPage;
    const endIndex = startIndex + leavesPerPage;
    return leaves.slice(startIndex, endIndex);
  };

  if (loading) {
    return (
      <Box sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        background: theme.palette.background.default
      }}>
        <CircularProgress size={60} />
      </Box>
    );
  }

  // Donut chart data
  const attendanceData = {
    labels: ['Present', 'Absent', 'On Leave'],
    datasets: [{
      data: [
        dashboardData.stats?.attendanceToday?.present || 0,
        dashboardData.stats?.attendanceToday?.absent || 0,
        dashboardData.stats?.attendanceToday?.onLeave || 0
      ],
      backgroundColor: [
        theme.palette.success.main,
        theme.palette.error.main,
        theme.palette.info.main
      ],
      borderColor: theme.palette.background.paper,
      borderWidth: 2,
      cutout: '70%'
    }]
  };

  const attendanceOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          padding: 20,
          usePointStyle: true,
          pointStyle: 'circle',
          color: theme.palette.text.primary
        }
      },
      tooltip: {
        enabled: true,
        backgroundColor: theme.palette.background.paper,
        titleColor: theme.palette.text.primary,
        bodyColor: theme.palette.text.secondary,
        borderColor: theme.palette.divider,
        borderWidth: 1,
        padding: 12,
        boxPadding: 6,
        usePointStyle: true,
        cornerRadius: 8,
        callbacks: {
          label: function(context) {
            const label = context.label || '';
            const value = context.raw || 0;
            const total = context.dataset.data.reduce((a, b) => a + b, 0);
            const percentage = Math.round((value / total) * 100);
            return `${label}: ${value} (${percentage}%)`;
          }
        }
      }
    }
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100vh", background: theme.palette.background.default }}>
      {/* Error Snackbar */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity="error"
          sx={{ width: '100%', boxShadow: theme.shadows[6] }}
        >
          {error}
        </Alert>
      </Snackbar>

      <Box sx={{ display: "flex", flexGrow: 1}}>

        <Box
  component="main"
  sx={{
    flexGrow: 1,
    bgcolor: "#f8fafc",
    minHeight: "100vh",
    pt: 0,   // remove top space
    px: 2,   // small left-right padding
  }}
>

          {window.location.pathname === '/hr/hr-dashboard' ? (
            <Box sx={{ maxWidth: '1800px', mx: 'auto' }}>
              {/* Dashboard Header */}
              <Box sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: { xs: 'flex-start', sm: 'center' },
                mb: 4,
                flexDirection: { xs: 'column', sm: 'row' },
                gap: 2
              }}>
                <Box>
                  <Typography variant="h4" fontWeight="bold" sx={{ mb: 1 }}>
                    Good {getTimeOfDay()}, {profileData?.personal?.firstName || 'HR'}
                  </Typography>
                  <Typography variant="body1" color="text.secondary">
                    {new Date().toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                  </Typography>
                </Box>
              </Box>

              {/* Stats Cards */}
              <Grid container spacing={2} sx={{ mb: 4 }}>
                {[
                  {
                    icon: <PeopleIcon fontSize="medium" />,
                    title: "Total Employees",
                    value: dashboardData.stats?.employees || 0,
                    color: 'primary',
                    link: `/${role}/employees`
                  },
                  {
                    icon: <CheckCircleIcon fontSize="medium" />,
                    title: "Present Today",
                    value: `${dashboardData.stats?.attendanceToday?.present || 0}/${dashboardData.stats?.employees || 0}`,
                    color: 'success',
                    onClick: handlePresentTodayClick
                  },
                  {
                    icon: <PendingActions fontSize="medium" />,
                    title: "Leave Requests",
                    value: dashboardData.stats?.leaves?.pending || 0,
                    color: 'warning',
                    onClick: handleLeaveRequestsClick
                  }
                ].map((stat, index) => (
                  <Grid item xs={12} sm={6} md={4} key={index}>
                    <GradientCard
                      onClick={stat.onClick || (() => navigate(stat.link))}
                      sx={{
                        p: 2.5,
                        cursor: 'pointer'
                      }}
                    >
                      <Box sx={{
                        display: 'flex',
                        alignItems: 'center',
                        mb: 1
                      }}>
                        <StatIconWrapper color={stat.color}>
                          {React.cloneElement(stat.icon, {
                            sx: {
                              color: theme.palette[stat.color].main,
                              fontSize: '24px'
                            }
                          })}
                        </StatIconWrapper>
                        <Box>
                          <Typography variant="subtitle2" color="text.secondary">
                            {stat.title}
                          </Typography>
                          <Typography
                            variant="h4"
                            fontWeight="bold"
                            sx={{ 
                              mt: 0.5,
                              background: `linear-gradient(135deg, ${theme.palette[stat.color].main} 0%, ${theme.palette[stat.color].dark} 100%)`,
                              WebkitBackgroundClip: 'text',
                              WebkitTextFillColor: 'transparent'
                            }}
                          >
                            {stat.value}
                          </Typography>
                        </Box>
                      </Box>
                    </GradientCard>
                  </Grid>
                ))}
              </Grid>

              {/* Activity Overview */}
              <Grid container spacing={2} sx={{ mb: 4 }}>
                {/* Daily Attendance Trend */}
                <Grid item xs={12} md={8}>
                  <Card sx={{ p: 3, borderRadius: '16px', background: 'white', height: '100%' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                      <Typography variant="h6" fontWeight="bold">
                        Daily Attendance Trend
                      </Typography>
                    </Box>

                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Box sx={{ width: '60%', height: 220 }}>
                        <Doughnut data={attendanceData} options={attendanceOptions} />
                      </Box>

                      <Box sx={{ ml: 4, width: '40%', display: 'flex', flexDirection: 'column', gap: 2 }}>
                        {[
                          { 
                            label: 'Present', 
                            value: dashboardData.stats?.attendanceToday?.present || 0, 
                            color: 'success',
                            onClick: () => handleAttendanceStatusClick('Present')
                          },
                          { 
                            label: 'Absent', 
                            value: dashboardData.stats?.attendanceToday?.absent || 0, 
                            color: 'error',
                            onClick: () => handleAttendanceStatusClick('Absent')
                          },
                          { 
                            label: 'On Leave', 
                            value: dashboardData.stats?.attendanceToday?.onLeave || 0, 
                            color: 'info',
                            onClick: handleOnLeaveClick
                          }
                        ].map((item, i) => (
                          <Box 
                            key={i} 
                            onClick={item.onClick}
                            sx={{
                              p: 1.5,
                              borderRadius: '8px',
                              bgcolor: (theme) => theme.palette[item.color].main + '10',
                              color: (theme) => theme.palette[item.color].dark,
                              display: 'flex',
                              flexDirection: 'column',
                              alignItems: 'flex-start',
                              boxShadow: 1,
                              cursor: 'pointer',
                              '&:hover': {
                                transform: 'translateY(-2px)',
                                boxShadow: 2
                              }
                            }}
                          >
                            <Typography variant="subtitle2">
                              {item.label}
                            </Typography>
                            <Typography variant="h6" fontWeight="bold">
                              {item.value}
                            </Typography>
                          </Box>
                        ))}
                      </Box>
                    </Box>
                  </Card>
                </Grid>

                {/* Next Holiday Card */}
                <Grid item xs={12} md={4}>
                  <Card sx={{ p: 3, borderRadius: '16px', height: '100%', background: 'white' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                      <Typography variant="h6" fontWeight="bold">
                        Next Holiday
                      </Typography>
                      <Button
                        size="small"
                        endIcon={<ChevronRight />}
                        sx={{ textTransform: 'none' }}
                        onClick={() => navigate(`/${role}/holiday`)}
                      >
                        View Calendar
                      </Button>
                    </Box>

                    {dashboardData.holidays.length > 0 ? (
                      <Box sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        height: '260px',
                        textAlign: 'center'
                      }}>
                        <EventAvailableIcon
                          color="info"
                          sx={{ fontSize: 60, mb: 2, color: 'info.main' }}
                        />
                        <Typography variant="h5" fontWeight="bold" sx={{ mb: 1 }}>
                          {dashboardData.holidays[0].name}
                        </Typography>
                        <Typography variant="subtitle1" color="text.secondary" sx={{ mb: 2 }}>
                          {formatDate(dashboardData.holidays[0].date)}
                        </Typography>
                        <Typography variant="body2">
                          {dashboardData.holidays[0].description || 'Company holiday'}
                        </Typography>
                      </Box>
                    ) : (
                      <Box sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        height: '260px',
                        textAlign: 'center',
                        color: 'text.secondary'
                      }}>
                        <EventAvailableIcon sx={{ fontSize: 60, mb: 2, color: 'text.secondary' }} />
                        <Typography variant="body1">
                          No upcoming holidays
                        </Typography>
                      </Box>
                    )}
                  </Card>
                </Grid>
              </Grid>

              {/* Leave Activity Section */}
              <Box sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                  <Typography variant="h6" fontWeight="bold">
                    Leave Activity
                  </Typography>
                  <Button
                    size="small"
                    endIcon={<ChevronRight fontSize="small" />}
                    sx={{ 
                      textTransform: 'none',
                      color: 'text.secondary',
                      '&:hover': {
                        color: theme.palette.primary.main,
                        backgroundColor: alpha(theme.palette.primary.main, 0.08)
                      }
                    }}
                    onClick={() => navigate(`/${role}/leaves`)}
                  >
                    View All Leaves
                  </Button>
                </Box>

                <Grid container spacing={2}>
                  {/* Employees Currently on Leave */}
                  <Grid item xs={12} md={4}>
                    <GradientCard sx={{ p: 0, height: '100%', overflow: 'hidden' }} id="currently-on-leave">
                      <Box sx={{ 
                        p: 2,
                        display: 'flex',
                        alignItems: 'center',
                        borderBottom: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
                        background: `linear-gradient(135deg, ${alpha(theme.palette.error.light, 0.05)} 0%, ${alpha(theme.palette.background.paper, 0.9)} 100%)`
                      }}>
                        <Box sx={{
                          width: 36,
                          height: 36,
                          borderRadius: '8px',
                          bgcolor: alpha(theme.palette.error.main, 0.1),
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          mr: 2
                        }}>
                          <EventIcon sx={{ fontSize: '18px', color: theme.palette.error.main }} />
                        </Box>
                        <Typography variant="subtitle1" fontWeight="bold">
                          Currently on Leave
                        </Typography>
                        <Box sx={{ ml: 'auto' }}>
                          <Chip 
                            label={dashboardData.currentLeaves.length}
                            size="small"
                            sx={{
                              backgroundColor: alpha(theme.palette.error.main, 0.1),
                              color: theme.palette.error.main,
                              fontWeight: 'bold'
                            }}
                          />
                        </Box>
                      </Box>

                      <List sx={{ p: 0 }}>
                        {dashboardData.currentLeaves.length > 0 ? (
                          <>
                            {getPaginatedLeaves(dashboardData.currentLeaves, currentLeavesPage).map((leave) => (
                              <ListItem
                                key={leave.id}
                                disablePadding
                                sx={{
                                  '&:hover': { bgcolor: alpha(theme.palette.error.main, 0.03) },
                                  borderBottom: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
                                  '&:last-child': { borderBottom: 'none' }
                                }}
                                onClick={() => handleLeaveClick(leave.id)}
                              >
                                <ListItemButton sx={{ px: 2, py: 1.5 }}>
                                  <ListItemIcon sx={{ minWidth: '40px' }}>
                                    <Avatar sx={{
                                      width: 32,
                                      height: 32,
                                      bgcolor: alpha(theme.palette.error.main, 0.1),
                                      color: theme.palette.error.main,
                                      fontSize: '14px',
                                      fontWeight: 'bold'
                                    }}>
                                      {leave.employeeName.charAt(0)}
                                    </Avatar>
                                  </ListItemIcon>
                                  <ListItemText
                                    primary={
                                      <Typography variant="subtitle1" fontWeight="medium">
                                        {leave.employeeName}
                                      </Typography>
                                    }
                                    secondary={
                                      <Box sx={{ mt: 0.5 }}>
                                        <Typography variant="body2" color="text.secondary">
                                          {leave.leaveType} • Until {formatDate(leave.toDate)}
                                        </Typography>
                                      </Box>
                                    }
                                  />
                                  <ChevronRight sx={{ color: theme.palette.text.disabled, fontSize: '20px' }} />
                                </ListItemButton>
                              </ListItem>
                            ))}
                            {dashboardData.currentLeaves.length > leavesPerPage && (
                              <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
                                <Pagination
                                  count={Math.ceil(dashboardData.currentLeaves.length / leavesPerPage)}
                                  page={currentLeavesPage}
                                  onChange={handleCurrentLeavesPageChange}
                                  color="primary"
                                  size="small"
                                />
                              </Box>
                            )}
                          </>
                        ) : (
                          <Box sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            py: 3,
                            color: 'text.secondary'
                          }}>
                            <EventIcon fontSize="medium" sx={{ 
                              mb: 1, 
                              color: alpha(theme.palette.text.secondary, 0.3),
                              fontSize: '40px'
                            }} />
                            <Typography variant="body2">
                              No employees on leave
                            </Typography>
                          </Box>
                        )}
                      </List>
                    </GradientCard>
                  </Grid>

                  {/* Upcoming Leave Requests */}
                  <Grid item xs={12} md={4}>
                    <GradientCard sx={{ p: 0, height: '100%', overflow: 'hidden' }}>
                      <Box sx={{ 
                        p: 2,
                        display: 'flex',
                        alignItems: 'center',
                        borderBottom: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
                        background: `linear-gradient(135deg, ${alpha(theme.palette.warning.light, 0.05)} 0%, ${alpha(theme.palette.background.paper, 0.9)} 100%)`
                      }}>
                        <Box sx={{
                          width: 36,
                          height: 36,
                          borderRadius: '8px',
                          bgcolor: alpha(theme.palette.warning.main, 0.1),
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          mr: 2
                        }}>
                          <EventAvailableIcon sx={{ fontSize: '18px', color: theme.palette.warning.main }} />
                        </Box>
                        <Typography variant="subtitle1" fontWeight="bold">
                          Upcoming Leaves
                        </Typography>
                        <Box sx={{ ml: 'auto' }}>
                          <Chip 
                            label={dashboardData.upcomingLeaves.length}
                            size="small"
                            sx={{
                              backgroundColor: alpha(theme.palette.warning.main, 0.1),
                              color: theme.palette.warning.main,
                              fontWeight: 'bold'
                            }}
                          />
                        </Box>
                      </Box>

                      <List sx={{ p: 0 }}>
                        {dashboardData.upcomingLeaves.length > 0 ? (
                          <>
                            {getPaginatedLeaves(dashboardData.upcomingLeaves, upcomingLeavesPage).map((leave) => (
                              <ListItem
                                key={leave.id}
                                disablePadding
                                sx={{
                                  '&:hover': { bgcolor: alpha(theme.palette.warning.main, 0.03) },
                                  borderBottom: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
                                  '&:last-child': { borderBottom: 'none' }
                                }}
                                onClick={() => handleLeaveClick(leave.id)}
                              >
                                <ListItemButton sx={{ px: 2, py: 1.5 }}>
                                  <ListItemIcon sx={{ minWidth: '40px' }}>
                                    <Avatar sx={{
                                      width: 32,
                                      height: 32,
                                      bgcolor: alpha(theme.palette.warning.main, 0.1),
                                      color: theme.palette.warning.main,
                                      fontSize: '14px',
                                      fontWeight: 'bold'
                                    }}>
                                      {leave.employeeName.charAt(0)}
                                    </Avatar>
                                  </ListItemIcon>
                                  <ListItemText
                                    primary={
                                      <Typography variant="subtitle1" fontWeight="medium">
                                        {leave.employeeName}
                                      </Typography>
                                    }
                                    secondary={
                                      <Box sx={{ mt: 0.5 }}>
                                        <Typography variant="body2" color="text.secondary">
                                          {leave.leaveType} • {formatDate(leave.fromDate)} to {formatDate(leave.toDate)}
                                        </Typography>
                                      </Box>
                                    }
                                  />
                                  <ChevronRight sx={{ color: theme.palette.text.disabled, fontSize: '20px' }} />
                                </ListItemButton>
                              </ListItem>
                            ))}
                            {dashboardData.upcomingLeaves.length > leavesPerPage && (
                              <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
                                <Pagination
                                  count={Math.ceil(dashboardData.upcomingLeaves.length / leavesPerPage)}
                                  page={upcomingLeavesPage}
                                  onChange={handleUpcomingLeavesPageChange}
                                  color="primary"
                                  size="small"
                                />
                              </Box>
                            )}
                          </>
                        ) : (
                          <Box sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            py: 3,
                            color: 'text.secondary'
                          }}>
                            <EventAvailableIcon fontSize="medium" sx={{ 
                              mb: 1, 
                              color: alpha(theme.palette.text.secondary, 0.3),
                              fontSize: '40px'
                            }} />
                            <Typography variant="body2">
                              No upcoming leaves
                            </Typography>
                          </Box>
                        )}
                      </List>
                    </GradientCard>
                  </Grid>

                  {/* Recent Leave Approvals/Rejections */}
                  <Grid item xs={12} md={4}>
                    <GradientCard sx={{ p: 0, height: '100%', overflow: 'hidden' }}>
                      <Box sx={{ 
                        p: 2,
                        display: 'flex',
                        alignItems: 'center',
                        borderBottom: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
                        background: `linear-gradient(135deg, ${alpha(theme.palette.success.light, 0.05)} 0%, ${alpha(theme.palette.background.paper, 0.9)} 100%)`
                      }}>
                        <Box sx={{
                          width: 36,
                          height: 36,
                          borderRadius: '8px',
                          bgcolor: alpha(theme.palette.success.main, 0.1),
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          mr: 2
                        }}>
                          <CheckCircleIcon sx={{ fontSize: '18px', color: theme.palette.success.main }} />
                        </Box>
                        <Typography variant="subtitle1" fontWeight="bold">
                          Recent Decisions
                        </Typography>
                        <Box sx={{ ml: 'auto' }}>
                          <Chip 
                            label={dashboardData.recentLeaves.length}
                            size="small"
                            sx={{
                              backgroundColor: alpha(theme.palette.success.main, 0.1),
                              color: theme.palette.success.main,
                              fontWeight: 'bold'
                            }}
                          />
                        </Box>
                      </Box>

                      <List sx={{ p: 0 }}>
                        {dashboardData.recentLeaves.length > 0 ? (
                          <>
                            {getPaginatedLeaves(dashboardData.recentLeaves, recentLeavesPage).map((leave) => {
                              const status = getLeaveStatus(leave);
                              return (
                                <ListItem
                                  key={leave.id}
                                  disablePadding
                                  sx={{
                                    '&:hover': { 
                                      bgcolor: status === 'APPROVED' ? 
                                        alpha(theme.palette.success.main, 0.03) : 
                                        alpha(theme.palette.error.main, 0.03)
                                    },
                                    borderBottom: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
                                    '&:last-child': { borderBottom: 'none' }
                                  }}
                                  onClick={() => handleLeaveClick(leave.id)}
                                >
                                  <ListItemButton sx={{ px: 2, py: 1.5 }}>
                                    <ListItemIcon sx={{ minWidth: '40px' }}>
                                      <Avatar sx={{
                                        width: 32,
                                        height: 32,
                                        bgcolor: status === 'APPROVED' ? 
                                          alpha(theme.palette.success.main, 0.1) : 
                                          alpha(theme.palette.error.main, 0.1),
                                        color: status === 'APPROVED' ? 
                                          theme.palette.success.main : 
                                          theme.palette.error.main,
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                      }}>
                                        {leave.employeeName.charAt(0)}
                                      </Avatar>
                                    </ListItemIcon>
                                    <ListItemText
                                      primary={
                                        <Typography variant="subtitle1" fontWeight="medium">
                                          {leave.employeeName}
                                        </Typography>
                                      }
                                      secondary={
                                        <Box sx={{ mt: 0.5, display: 'flex', alignItems: 'center' }}>
                                          <Chip
                                            label={status}
                                            size="small"
                                            sx={{
                                              bgcolor: status === 'APPROVED'
                                                ? alpha(theme.palette.success.main, 0.1)
                                                : alpha(theme.palette.error.main, 0.1),
                                              color: status === 'APPROVED'
                                                ? theme.palette.success.main
                                                : theme.palette.error.main,
                                              mr: 1,
                                              height: '20px',
                                              fontSize: '0.65rem',
                                              fontWeight: 'bold'
                                            }}
                                          />
                                          <Typography variant="body2" color="text.secondary">
                                            {formatDate(leave.fromDate)} to {formatDate(leave.toDate)}
                                          </Typography>
                                        </Box>
                                      }
                                    />
                                    <ChevronRight sx={{ color: theme.palette.text.disabled, fontSize: '20px' }} />
                                  </ListItemButton>
                                </ListItem>
                              );
                            })}
                            {dashboardData.recentLeaves.length > leavesPerPage && (
                              <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
                                <Pagination
                                  count={Math.ceil(dashboardData.recentLeaves.length / leavesPerPage)}
                                  page={recentLeavesPage}
                                  onChange={handleRecentLeavesPageChange}
                                  color="primary"
                                  size="small"
                                />
                              </Box>
                            )}
                          </>
                        ) : (
                          <Box sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            py: 3,
                            color: 'text.secondary'
                          }}>
                            <CheckCircleIcon fontSize="medium" sx={{ 
                              mb: 1, 
                              color: alpha(theme.palette.text.secondary, 0.3),
                              fontSize: '40px'
                            }} />
                            <Typography variant="body2">
                              No recent decisions
                            </Typography>
                          </Box>
                        )}
                      </List>
                    </GradientCard>
                  </Grid>
                </Grid>
              </Box>
            </Box>
          ) : (
            <Outlet />
          )}
        </Box>
      </Box>
    </Box>
  );
};

export default HrDashboard;