import React, { useState, useEffect } from "react";
import { Link, Outlet, useNavigate, useLocation } from "react-router-dom";
import moment from "moment";
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Box,
  Avatar,
  TextField,
  CircularProgress,
  Snackbar,
  Alert,
  Grid,
  Card,
  CardContent,
  Chip,
  Divider,
  Paper,
  useTheme,
  LinearProgress,
  Badge,
  IconButton,
  styled,
  alpha,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions
} from "@mui/material";
import {
  Dashboard as DashboardIcon,
  AccessTime as AccessTimeIcon,
  
  CalendarToday as CalendarTodayIcon,
  Person as PersonIcon,
  Logout as LogoutIcon,
  Work as WorkIcon,
  Male as MaleIcon,
  Female as FemaleIcon,
  CheckCircle,
  Pending,
  HourglassEmpty,
  EventAvailable,
  EventBusy,
  People,
  Task,
  Notifications,
  Refresh,
  Login as LoginIcon,
  Logout as LogoutOutlinedIcon,
  Event as EndDayIcon
} from "@mui/icons-material";
import axios from "axios";
import { motion } from 'framer-motion';
import { Doughnut, Bar } from 'react-chartjs-2';
import { Chart, ArcElement, CategoryScale, LinearScale, BarElement, Tooltip, Legend } from 'chart.js';
import UserService from "../service/UserService";
import Sidebar from "../Sidebar/Sidebar";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

// Register Chart.js components
Chart.register(ArcElement, CategoryScale, LinearScale, BarElement, Tooltip, Legend);

// API configuration
axios.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Custom styled components
const GradientCard = styled(Card)(({ theme }) => ({
  background: `linear-gradient(135deg, ${alpha(theme.palette.background.paper, 0.8)} 0%, ${alpha(theme.palette.background.default, 0.4)} 100%)`,
  backdropFilter: 'blur(10px)',
  borderRadius: '16px',
  boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.1)',
  border: '1px solid rgba(255, 255, 255, 0.2)',
  transition: 'all 0.3s ease',
  height: '100%',
  '&:hover': {
    transform: 'translateY(-5px)',
    boxShadow: '0 12px 40px 0 rgba(31, 38, 135, 0.2)'
  }
}));

// ---- helper functions (UTC from API -> convert local) ----
const parseApiDate = (dateString) => {
  if (!dateString) return null;
  const m = moment(dateString);
  if (!m.isValid()) return null;
  return m.toDate();
};

const formatLocalDateTime = (dateString) => {
  if (!dateString) return "N/A";
  return moment(dateString).format("DD-MMM-YYYY HH:mm");
};

const formatTimeRemaining = (minutes) => {
  if (minutes === null) return '';
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m remaining in session`;
};


const EmployeeDashboard = ({ embedded = false }) => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const [searchQuery, setSearchQuery] = useState("");
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [clockInLoading, setClockInLoading] = useState(false);
  const [clockOutLoading, setClockOutLoading] = useState(false);
  const [leaveTypesData, setLeaveTypesData] = useState([]);
  
  // Attendance states from UserAttendance
  const [attendance, setAttendance] = useState([]);
  const [activeSession, setActiveSession] = useState(null);
  const [dayEnded, setDayEnded] = useState(false);
  const [confirmEndDay, setConfirmEndDay] = useState(false);
  const [timeRemaining, setTimeRemaining] = useState(null);
  const [sessionStartTime, setSessionStartTime] = useState(null);

  // Dashboard data state
  const [dashboardData, setDashboardData] = useState({
    projects: [],
    tasks: [],
    attendance: [],
    leaveBalance: {},
    timesheets: [],
    holidays: [],
    leaves: []
  });
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Restore session start from localStorage on load
  // useEffect(() => {
  //   const saved = localStorage.getItem('sessionStartTime');
  //   if (saved) {
  //     const parsedTime = new Date(saved);
  //     setSessionStartTime(parsedTime);

  //     const sessionEnd = new Date(parsedTime);
  //     sessionEnd.setHours(sessionEnd.getHours() + 9);
  //     const remainingMs = sessionEnd - new Date();
  //     const remainingMinutes = Math.max(0, Math.floor(remainingMs / (1000 * 60)));

  //     setTimeRemaining(remainingMinutes);
  //   }
  // }, []);

  useEffect(() => {
  const saved = localStorage.getItem('sessionStartTime');
  if (saved) {
    const parsedTime = parseApiDate(saved) || new Date(saved);
    setSessionStartTime(parsedTime);

    const sessionEnd = moment(parsedTime).add(9, 'hours');
    const remainingMinutes = Math.max(
      0,
      Math.floor(sessionEnd.diff(moment()) / (1000 * 60))
    );
    setTimeRemaining(remainingMinutes);
  }
}, []);


  useEffect(() => {
    if (sessionStartTime) {
      localStorage.setItem('sessionStartTime', sessionStartTime.toISOString());
    }
  }, [sessionStartTime]);

  const fetchMyAttendance = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/attendance/my-attendance`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      const attendanceData = response.data;
      setAttendance(attendanceData);
      updateActiveSession(attendanceData);
    } catch (err) {
      console.error("Failed to fetch attendance:", err);
      setError(err.response?.data?.message || 'Failed to fetch attendance');
      setSnackbarOpen(true);
    }
  };

  // const updateActiveSession = (attendanceData) => {
  //   const active = attendanceData.find(session => 
  //     !session.outTime || 
  //     (session.clockSessions && session.clockSessions.some(cs => !cs.outTime))
  //   );
    
  //   setActiveSession(active || null);
  //   setDayEnded(!!active?.manuallyEnded);

  //   if (active?.clockSessions?.length > 0) {
  //     const allClockIns = active.clockSessions
  //       .filter(cs => cs.inTime)
  //       .map(cs => new Date(cs.inTime));

  //     const earliestInTime = new Date(Math.min(...allClockIns.map(d => d.getTime())));

  //     setSessionStartTime(earliestInTime);

  //     const sessionEnd = new Date(earliestInTime);
  //     sessionEnd.setHours(sessionEnd.getHours() + 9);

  //     const remainingMs = sessionEnd - new Date();
  //     const remainingMinutes = Math.max(0, Math.floor(remainingMs / (1000 * 60)));

  //     setTimeRemaining(remainingMinutes);
  //     return;
  //   }


const updateActiveSession = (attendanceData) => {
  const active = attendanceData.find(session =>
    !session.outTime ||
    (session.clockSessions && session.clockSessions.some(cs => !cs.outTime))
  );

  setActiveSession(active || null);
  if (!active?.manuallyEnded) setDayEnded(false);

  if (active?.clockSessions?.length > 0) {
    const allClockIns = active.clockSessions
      .filter(cs => cs.inTime)
      .map(cs => parseApiDate(cs.inTime))
      .filter(Boolean);

    if (allClockIns.length > 0) {
      const earliestInTime = new Date(Math.min(...allClockIns.map(d => d.getTime())));
      setSessionStartTime(earliestInTime);

      const sessionEnd = moment(earliestInTime).add(9, 'hours');
      const remainingMinutes = Math.max(
        0,
        Math.floor(sessionEnd.diff(moment()) / (1000 * 60))
      );
      setTimeRemaining(remainingMinutes);
      return;
    }
  }

  setSessionStartTime(null);
  setTimeRemaining(null);
};


  const fetchDashboardData = async () => {
    try {
      const token = localStorage.getItem("token");
      if (!token) {
        navigate("/login");
        return;
      }

      setRefreshing(true);
      
      const response = await axios.get(`${API_BASE_URL}/employee-dashboard/dashboard-data`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      const data = response.data;
      
      setDashboardData({
        projects: data.projects || [],
        tasks: data.tasks || [],
        attendance: data.attendance || [],
        leaveBalance: data.leaveBalance || {},
        timesheets: data.timesheets || [],
        holidays: data.holidays || [],
        leaves: data.leaves || []
      });

      await fetchMyAttendance();

      const leaveTypesResponse = await axios.get(`${API_BASE_URL}/my-leave-types`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setLeaveTypesData(leaveTypesResponse.data || []);

    } catch (error) {
      console.error("Error fetching dashboard data:", error);
      setError("Failed to load dashboard data");
      setSnackbarOpen(true);
    } finally {
      setDashboardLoading(false);
      setRefreshing(false);
    }
  };

  // Fetch profile data
  const fetchProfile = async () => {
    try {
      const token = localStorage.getItem("token");
      if (!token) {
        throw new Error("No authentication token found");
      }

      const response = await UserService.getCompleteProfile(token);
      if (response.employeeData) {
        setProfileData(response.employeeData);
      } else {
        throw new Error("No employee data found in response");
      }
    } catch (error) {
      console.error("Failed to fetch profile:", error);
      setError(error.message || "Failed to load profile data");
      setSnackbarOpen(true);
    } finally {
      setLoading(false);
    }
  };

  // Initial data fetch
  useEffect(() => {
    const loadData = async () => {
      await fetchProfile();
      await fetchDashboardData();
    };
    loadData();
  }, []);

  // Auto-refresh every 30 seconds
  // useEffect(() => {
  //   const interval = setInterval(fetchDashboardData, 30000);
  //   return () => clearInterval(interval);
  // }, []);

  // Clock-in handler
  const handleClockIn = async () => {
    try {
      setClockInLoading(true);
await axios.post(`${API_BASE_URL}/attendance/clock-in`, {
  date: moment().format("YYYY-MM-DD"),
  time: moment().format("HH:mm:ss")
}, {
  headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
});

      await fetchMyAttendance();
      setDayEnded(false);
    } catch (error) {
      console.error("Clock-in error:", error);
      setError("Failed to clock in");
      setSnackbarOpen(true);
    } finally {
      setClockInLoading(false);
    }
  };

  // Clock-out handler
  const handleClockOut = async () => {
    try {
      setClockOutLoading(true);
      const activeClockSession = activeSession?.clockSessions?.find(cs => !cs.outTime);
      if (!activeClockSession) throw new Error("No active session");

 await axios.put(`${API_BASE_URL}/attendance/${activeSession.id}/clock-out`, {
  date: moment().format("YYYY-MM-DD"),
  time: moment().format("HH:mm:ss")
}, {
  headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
});

      await fetchMyAttendance();
    } catch (error) {
      console.error("Clock-out error:", error);
      setError("Failed to clock out");
      setSnackbarOpen(true);
    } finally {
      setClockOutLoading(false);
    }
  };

  const handleEndDay = async () => {
    try {
await axios.post(`${API_BASE_URL}/attendance/end-day`, {
  date: moment().format("YYYY-MM-DD"),
  time: moment().format("HH:mm:ss")
}, {
  headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
});

      setDayEnded(true);
      localStorage.removeItem('sessionStartTime');
      setSessionStartTime(null);
      setConfirmEndDay(false);
      await fetchMyAttendance();
    } catch (error) {
      console.error("End day error:", error);
      setError("Failed to end day");
      setSnackbarOpen(true);
    }
  };

  // Helper functions
  const getTaskStatusCount = (status) => {
    return dashboardData.tasks.filter(task => task.status === status).length;
  };

  const getProjectStatusCount = (status) => {
    return dashboardData.projects.filter(project => project.status === status).length;
  };

  const getLeaveStatusCount = (status) => {
    return dashboardData.leaves.filter(leave => leave.status === status).length;
  };

  const getRecentTimesheets = () => {
    return [...dashboardData.timesheets]
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 3);
  };

  // Chart data
  const taskStatusData = {
    labels: ['To Do', 'In Progress', 'Completed'],
    datasets: [
      {
        data: [
          getTaskStatusCount('TODO'),
          getTaskStatusCount('IN_PROGRESS'),
          getTaskStatusCount('COMPLETED')
        ],
        backgroundColor: [
          alpha(theme.palette.info.main, 0.8),
          alpha(theme.palette.warning.main, 0.8),
          alpha(theme.palette.success.main, 0.8)
        ],
        borderColor: [
          theme.palette.info.dark,
          theme.palette.warning.dark,
          theme.palette.success.dark
        ],
        borderWidth: 1,
        cutout: '70%'
      },
    ],
  };

  // Modern bar chart design
  const leaveTypesChartData = {
    labels: leaveTypesData.map(lt => lt.name),
    datasets: [
      {
        label: 'Allotted Days',
        data: leaveTypesData.map(lt => lt.defaultDays),
        backgroundColor: alpha(theme.palette.primary.main, 0.8),
        borderRadius: 6,
        borderWidth: 0,
        barThickness: 20,
        categoryPercentage: 0.6,
        barPercentage: 0.9
      },
      {
        label: 'Used Days',
        data: leaveTypesData.map(lt => lt.defaultDays - lt.remainingDays),
        backgroundColor: alpha(theme.palette.secondary.main, 0.8),
        borderRadius: 6,
        borderWidth: 0,
        barThickness: 20,
        categoryPercentage: 0.6,
        barPercentage: 0.9
      }
    ],
  };

  // Enhanced dashboard content
  const renderDashboardContent = () => {
    if (dashboardLoading) {
      return (
        <Box sx={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          height: '60vh',
          background: `linear-gradient(135deg, ${alpha(theme.palette.background.paper, 0.8)} 0%, ${alpha(theme.palette.background.default, 0.4)} 100%)`,
          borderRadius: '16px'
        }}>
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
          >
            <CircularProgress size={60} thickness={4} sx={{ color: theme.palette.primary.light }} />
          </motion.div>
        </Box>
      );
    }

    const hasActiveClockSession = activeSession?.clockSessions?.some(cs => !cs.outTime);
    const hasActiveDaySession = activeSession && !activeSession.outTime;

    return (
      <Box sx={{ px: 2, pt: 0, pb: 2  }}>
        {/* Header with animated gradient - kept original color */}
        <Box sx={{ 
          mb: 4,
          p: 3,
          borderRadius: '16px',
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.1)} 0%, ${alpha(theme.palette.secondary.main, 0.1)} 100%)`,
          position: 'relative',
          overflow: 'hidden',
          '&::before': {
            content: '""',
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: `linear-gradient(45deg, transparent 45%, ${alpha(theme.palette.primary.light, 0.1)} 50%, transparent 55%)`,
            backgroundSize: '300% 300%',
            animation: 'shimmer 8s infinite linear',
            '@keyframes shimmer': {
              '0%': { backgroundPosition: '0% 0%' },
              '100%': { backgroundPosition: '100% 100%' }
            }
          }
        }}>
          <Box sx={{ position: 'relative', zIndex: 1 }}>
            <Typography variant="h3" sx={{ 
              fontWeight: 'bold',
              mb: 1,
              background: `linear-gradient(90deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              display: 'inline-block'
            }}>
              Welcome back, {profileData?.personal?.firstName || 'User'}!
            </Typography>
            <Typography variant="subtitle1" sx={{ color: 'text.secondary' }}>
              Here's what's happening today
            </Typography>
          </Box>
        </Box>

        {refreshing && (
          <LinearProgress 
            color="primary" 
            sx={{ 
              mb: 3,
              height: 4,
              borderRadius: '4px',
              background: alpha(theme.palette.primary.main, 0.1),
              '& .MuiLinearProgress-bar': {
                borderRadius: '4px',
                background: `linear-gradient(90deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`
              }
            }} 
          />
        )}

        {/* Stats Cards */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {[
            {
              title: 'Active Projects',
              value: getProjectStatusCount('STARTED'),
              total: dashboardData.projects.length,
              icon: <WorkIcon fontSize="medium" />,
              color: 'primary',
              link: `/${role}/myproject`
            },
            {
              title: 'Completed Tasks',
              value: getTaskStatusCount('COMPLETED'),
              total: dashboardData.tasks.length,
              icon: <Task fontSize="medium" />,
              color: 'success',
              link: `/${role}/mytask`
            },
            {
              title: 'Attendance',
              value: hasActiveClockSession ? 'Clocked In' : 'Not Clocked In',
              total: hasActiveClockSession ? new Date(activeSession.clockSessions.find(cs => !cs.outTime).inTime).toLocaleTimeString() : '--:--',
              icon: <AccessTimeIcon fontSize="medium" />,
              color: hasActiveClockSession ? 'success' : 'error',
              link: `/${role}/employee-dashboard/attendence`,
              hasActiveClockSession,
              dayEnded,
              handleClockIn,
              handleClockOut,
              handleEndDay,
              clockInLoading,
              clockOutLoading,
              setConfirmEndDay
            }
          ].map((stat, index) => (
            <Grid item xs={12} sm={6} md={4} key={index} sx={{ display: 'flex' }}>
              <motion.div whileHover={{ y: -3 }} style={{ flex: 1, display: 'flex' }}>
                <GradientCard sx={{ 
                  flex: 1, 
                  display: 'flex', 
                  flexDirection: 'column',
                  px: 2,
                  py: 2,
                  minHeight: 180 // Increased height to accommodate both buttons
                }}>
                  <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', p: 1 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', flexGrow: 1 }}>
                      <Box>
                        <Typography variant="h6" color="text.secondary" sx={{ opacity: 0.8 }}>
                          {stat.title}
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'flex-end', mt: 1 }}>
                          <Typography variant="h5" sx={{ 
                            fontWeight: 'bold',
                            mr: 1,
                            color: theme.palette[stat.color].main
                          }}>
                            {stat.value}
                          </Typography>
                          {stat.total !== undefined && stat.total !== '' && (
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                              / {stat.total}
                            </Typography>
                          )}
                        </Box>
                      </Box>
                      <Box sx={{
                        width: 48,
                        height: 48,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: `linear-gradient(135deg, ${alpha(theme.palette[stat.color].main, 0.2)} 0%, transparent 100%)`
                      }}>
                        {React.cloneElement(stat.icon, { 
                          sx: { color: theme.palette[stat.color].main },
                          fontSize: "medium" 
                        })}
                      </Box>
                    </Box>
             
                    {/* Attendance Actions */}
                    {stat.title === 'Attendance' && (
                      <Box sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
                        {stat.hasActiveClockSession ? (
                          <>
                            <Button
                              variant="outlined"
                              color="error"
                              size="small"
                              startIcon={clockOutLoading ? <CircularProgress size={16} /> : <LogoutOutlinedIcon />}
                              onClick={stat.handleClockOut}
                              disabled={clockOutLoading || stat.dayEnded}
                              sx={{
                                textTransform: 'none',
                                '&:hover': {
                                  background: alpha(theme.palette.error.main, 0.1)
                                }
                              }}
                            >
                              Clock Out
                            </Button>
                            <Button
                              variant="outlined"
                              color="secondary"
                              size="small"
                              startIcon={<EndDayIcon />}
                              onClick={() => stat.setConfirmEndDay(true)}
                              disabled={stat.dayEnded}
                              sx={{
                                textTransform: 'none',
                                '&:hover': {
                                  background: alpha(theme.palette.secondary.main, 0.1)
                                }
                              }}
                            >
                              End Day
                            </Button>
                          </>
                        ) : (
                          <Button
                            variant="outlined"
                            color="primary"
                            size="small"
                            startIcon={clockInLoading ? <CircularProgress size={16} /> : <LoginIcon />}
                            onClick={stat.handleClockIn}
                            disabled={clockInLoading || stat.dayEnded}
                            sx={{
                              textTransform: 'none',
                              '&:hover': {
                                background: alpha(theme.palette.primary.main, 0.1)
                              }
                            }}
                          >
                            Clock In
                          </Button>
                        )}
                      </Box>
                    )}
                    
                    {/* Link Button for other cards */}
                    {stat.title !== 'Attendance' && (
                      <Button
                        component={Link}
                        to={stat.link}
                        size="small"
                        sx={{
                          mt: 2,
                          textTransform: 'none',
                          color: theme.palette[stat.color].main,
                          '&:hover': {
                            background: alpha(theme.palette[stat.color].main, 0.1)
                          }
                        }}
                      >
                        View details
                      </Button>
                    )}
                  </CardContent>
                </GradientCard>
              </motion.div>
            </Grid>
          ))}
        </Grid>

        {/* Leave Dashboard Section */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12}>
            <GradientCard>
              <CardContent>
                <Box sx={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'center',
                  mb: 3
                }}>
                  <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
                    Leave Overview
                  </Typography>
                </Box>
                <Grid container spacing={3}>
                  <Grid item xs={12} md={8}>
                    <Box sx={{ height: 400, position: 'relative' }}>
                      <Typography variant="h6" sx={{ mb: 2, textAlign: 'center' }}>
                        My Leave Allocation and Usage
                      </Typography>
                      <Bar 
                        data={leaveTypesChartData}
                        options={{
                          maintainAspectRatio: false,
                          responsive: true,
                          scales: {
                            y: {
                              beginAtZero: true,
                              grid: {
                                color: alpha(theme.palette.text.primary, 0.1),
                                drawBorder: false
                              },
                              ticks: {
                                color: theme.palette.text.secondary,
                                padding: 8
                              }
                            },
                            x: {
                              grid: {
                                display: false,
                                drawBorder: false
                              },
                              ticks: {
                                color: theme.palette.text.secondary,
                                padding: 8
                              }
                            }
                          },
                          plugins: {
                            legend: {
                              position: 'top',
                              labels: {
                                usePointStyle: true,
                                padding: 16,
                                color: theme.palette.text.primary,
                                font: {
                                  size: 12
                                },
                                boxWidth: 8,
                                boxHeight: 8
                              }
                            },
                            tooltip: {
                              backgroundColor: theme.palette.background.paper,
                              titleColor: theme.palette.text.primary,
                              bodyColor: theme.palette.text.secondary,
                              borderColor: theme.palette.divider,
                              borderWidth: 1,
                              padding: 12,
                              usePointStyle: true,
                              cornerRadius: 8,
                              bodyFont: {
                                size: 12
                              },
                              titleFont: {
                                size: 14,
                                weight: 'bold'
                              },
                              boxPadding: 6
                            },
                            datalabels: {
                              display: false
                            }
                          },
                          layout: {
                            padding: {
                              top: 16,
                              bottom: 16,
                              left: 16,
                              right: 16
                            }
                          }
                        }}
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Box sx={{ p: 2 }}>
                      <Typography variant="h6" sx={{ mb: 2, textAlign: 'center' }}>
                        Quick Actions
                      </Typography>
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <Button
                          component={Link}
                          to={`/${role}/apply-leaves`}
                          variant="contained"
                          color="primary"
                          fullWidth
                          startIcon={<EventAvailable />}
                        >
                          Apply for Leave
                        </Button>
                        <Button
                          component={Link}
                          to={`/${role}/my-leave-balance`}
                          variant="outlined"
                          color="secondary"
                          fullWidth
                          startIcon={<CalendarTodayIcon />}
                        >
                          View Leave Balance
                        </Button>
                        <Button
                          component={Link}
                          to={`/${role}/my-holiday`}
                          variant="outlined"
                          fullWidth
                          startIcon={<EventBusy />}
                        >
                          View Holidays
                        </Button>
                      </Box>
                      <Box sx={{ mt: 3 }}>
                        <Typography variant="subtitle2" sx={{ mb: 1 }}>
                          Leave Summary
                        </Typography>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2">Total Leaves:</Typography>
                          <Typography variant="body2" fontWeight="bold">
                            {leaveTypesData.reduce((total, lt) => total + lt.defaultDays, 0) || 0}
                          </Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant="body2">Leaves Used:</Typography>
                          <Typography variant="body2" fontWeight="bold">
                            {leaveTypesData.reduce((total, lt) => total + (lt.defaultDays - lt.remainingDays), 0) || 0}
                          </Typography>
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography variant="body2">Leaves Remaining:</Typography>
                          <Typography variant="body2" fontWeight="bold">
                            {leaveTypesData.reduce((total, lt) => total + lt.remainingDays, 0) || 0}
                          </Typography>
                        </Box>
                      </Box>
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </GradientCard>
          </Grid>
        </Grid>

        {/* Charts Section */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} md={6}>
            <GradientCard>
              <CardContent>
                <Typography variant="h6" sx={{ mb: 3, fontWeight: 'bold' }}>
                  Task Status Distribution
                </Typography>
                <Box sx={{ height: 300, position: 'relative' }}>
                  <Doughnut 
                    data={taskStatusData}
                    options={{
                      maintainAspectRatio: false,
                      plugins: {
                        legend: {
                          position: 'bottom',
                          labels: {
                            usePointStyle: true,
                            padding: 20,
                            color: theme.palette.text.primary
                          }
                        },
                        tooltip: {
                          backgroundColor: theme.palette.background.paper,
                          titleColor: theme.palette.text.primary,
                          bodyColor: theme.palette.text.secondary,
                          borderColor: theme.palette.divider,
                          borderWidth: 1,
                          padding: 12,
                          usePointStyle: true
                        }
                      },
                      cutout: '75%'
                    }}
                  />
                </Box>
              </CardContent>
            </GradientCard>
          </Grid>

          <Grid item xs={12} md={6}>
            <GradientCard>
              <CardContent>
                <Box sx={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'center',
                  mb: 3
                }}>
                  <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                    Recent Timesheets
                  </Typography>
                  <Button
                    component={Link}
                    to={`/${role}/my-timesheet-detail`}
                    size="small"
                    sx={{
                      textTransform: 'none',
                      color: theme.palette.primary.main,
                      '&:hover': {
                        background: alpha(theme.palette.primary.main, 0.1)
                      }
                    }}
                  >
                    View All
                  </Button>
                </Box>
                {getRecentTimesheets().map((timesheet) => (
                  <motion.div 
                    key={timesheet.id}
                    whileHover={{ scale: 1.01 }}
                    transition={{ type: 'spring', stiffness: 400, damping: 10 }}
                  >
                    <Paper 
                      sx={{ 
                        p: 2, 
                        mb: 2,
                        borderRadius: '12px',
                        background: alpha(theme.palette.background.paper, 0.7),
                        backdropFilter: 'blur(10px)',
                        border: '1px solid rgba(255, 255, 255, 0.1)',
                        boxShadow: '0 4px 20px 0 rgba(0,0,0,0.05)',
                        transition: 'all 0.3s ease',
                        '&:hover': {
                          boxShadow: '0 8px 30px 0 rgba(0,0,0,0.1)'
                        }
                      }}
                    >
                      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Box>
                          <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
                            {timesheet.taskName}
                          </Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                            {timesheet.projectName}
                          </Typography>
                        </Box>
                        <Chip 
                          label={timesheet.status} 
                          size="small" 
                          sx={{
                            height: 24,
                            fontWeight: 'bold',
                            background: 
                              timesheet.status === 'APPROVED' ? alpha(theme.palette.success.main, 0.2) :
                              timesheet.status === 'SUBMITTED' ? alpha(theme.palette.primary.main, 0.2) :
                              timesheet.status === 'REJECTED' ? alpha(theme.palette.error.main, 0.2) :
                              alpha(theme.palette.grey[500], 0.2),
                            color: 
                              timesheet.status === 'APPROVED' ? theme.palette.success.main :
                              timesheet.status === 'SUBMITTED' ? theme.palette.primary.main :
                              timesheet.status === 'REJECTED' ? theme.palette.error.main :
                              theme.palette.text.secondary
                          }}
                        />
                      </Box>
                      <Box sx={{ display: 'flex', alignItems: 'center', mt: 1.5 }}>
                        <AccessTimeIcon fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />
                      </Box>
                    </Paper>
                  </motion.div>
                ))}
                {dashboardData.timesheets.length === 0 && (
                  <Box sx={{ 
                    display: 'flex', 
                    flexDirection: 'column', 
                    alignItems: 'center', 
                    py: 4,
                    textAlign: 'center'
                  }}>
                    <AccessTimeIcon color="disabled" sx={{ fontSize: 40, mb: 1 }} />
                    <Typography variant="body2" color="text.secondary">
                      No recent timesheets
                    </Typography>
                  </Box>
                )}
              </CardContent>
            </GradientCard>
          </Grid>
        </Grid>
      </Box>
    );
  };

  const genderIcon = profileData?.personal?.gender?.toLowerCase() === 'male'
    ? <MaleIcon fontSize="small" color="primary" />
    : <FemaleIcon fontSize="small" color="secondary" />;

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

  if (loading) {
    return (
      <Box sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh'
      }}>
        <CircularProgress size={60} />
      </Box>
    );
  }

return (
  <Box sx={{ display: "flex", flexDirection: "column", height: "100vh" }}>
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
        sx={{ width: '100%' }}
      >
        {error}
      </Alert>
    </Snackbar>

    {/* Main Dashboard Content (aligned with global Sidebar layout) */}
    <Box
      component="main"
      sx={{
        flexGrow: 1,
        bgcolor: "#f8fafc",
        minHeight: "100vh",
        // overflowY: "auto",
        pt: 0,     // ✅ removed top padding
        px: 2,     // ✅ small side padding
      }}
    >
      {renderDashboardContent()}
    </Box>

    {/* End Day Confirmation Dialog */}
    <Dialog open={confirmEndDay} onClose={() => setConfirmEndDay(false)}>
      <DialogTitle>Confirm End Day</DialogTitle>
      <DialogContent>
        <Typography>Are you sure you want to end your work day?</Typography>
        <Typography variant="body2" color="text.secondary" mt={2}>
          This will finalize your attendance record for today and cannot be undone.
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setConfirmEndDay(false)}>Cancel</Button>
        <Button
          onClick={handleEndDay}
          color="secondary"
          variant="contained"
          startIcon={<EndDayIcon />}
        >
          Confirm End Day
        </Button>
      </DialogActions>
    </Dialog>
  </Box>
);
};

export default EmployeeDashboard;