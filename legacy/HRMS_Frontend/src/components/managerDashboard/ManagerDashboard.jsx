import React, { useState, useEffect } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
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
    LinearProgress,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    useTheme,
    Divider,
    IconButton,
    List,
    ListItem,
    ListItemIcon,
    ListItemText
} from "@mui/material";
import {
    Dashboard as DashboardIcon,
    AccessTime as AccessTimeIcon,
    CalendarToday as CalendarTodayIcon,
    Person as PersonIcon,
    Logout as LogoutIcon,
    Work as WorkIcon,
    ExpandLess,
    ExpandMore,
    Male as MaleIcon,
    Female as FemaleIcon,
    People,
    Groups,
    Assignment,
    Event,
    CheckCircle,
    PendingActions,
    Notifications as NotificationsIcon,
    ChevronRight,
    MoreVert,
    Task,
    TrendingUp
} from "@mui/icons-material";
import axios from "axios";
import UserService from "../service/UserService";
import { format } from 'date-fns';
import Sidebar from "../Sidebar/Sidebar";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import API_BASE_URL from "../config/apiConfig";


const ManagerDashboard = () => {
    const navigate = useNavigate();
      const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
    const theme = useTheme();
    const [searchQuery, setSearchQuery] = useState("");
    const [profileData, setProfileData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [snackbarOpen, setSnackbarOpen] = useState(false);
    const [dashboardData, setDashboardData] = useState({
        teamMembers: [],
        projects: [],
        tasks: [],
        pendingTimesheets: []
    });
    const [dashboardLoading, setDashboardLoading] = useState(true);


    useEffect(() => {
        const fetchProfile = async () => {
            try {
                const token = localStorage.getItem("token");
                if (!token) throw new Error("No authentication token found");

                const response = await UserService.getCompleteProfile(token);

                if (response.employeeData) {
                    setProfileData(response.employeeData);
                    fetchManagerData(); // 🔄 No argument here
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


        const fetchManagerData = async () => {
            try {
                setDashboardLoading(true);
                const res = await axios.get(`${API_BASE_URL}/dashboard/manager`); // ✅ No ID passed
                const {
                    projects = [],
                    teamMembers = [],
                    tasks = [],
                    pendingTimesheets = []
                } = res.data || {};
                setDashboardData({ projects, teamMembers, tasks, pendingTimesheets });
            } catch (error) {
                console.error("Error fetching manager dashboard:", error);
                setError("Failed to load dashboard data");
                setSnackbarOpen(true);
            } finally {
                setDashboardLoading(false);
            }
        };

        fetchProfile();
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

    const renderDashboardContent = () => {
        if (dashboardLoading) {
            return (
                <Box sx={{ 
                    display: 'flex', 
                    justifyContent: 'center', 
                    alignItems: 'center', 
                    height: '60vh'
                }}>
                    <CircularProgress size={60} />
                </Box>
            );
        }

        return (
            <Box sx={{ p: 3 }}>
                {/* Dashboard Header - Modern Design */}
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
                            Good {getTimeOfDay()}, {profileData?.personal?.firstName || 'Manager'}
                        </Typography>
                        <Typography variant="body1" sx={{ opacity: 0.9 }}>
                            {new Date().toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                        </Typography>
                    </Box>
                </Box>

                {/* Stats Cards - Modern Design */}
                <Grid container spacing={3} sx={{ mb: 4 }}>
                    {[
                        { 
                            icon: <People fontSize="large" />, 
                            title: "Team Members", 
                            value: dashboardData.teamMembers?.length || 0,
                            color: 'primary',
                            link: `/${role}/my-team`
                        },
                        { 
                            icon: <WorkIcon fontSize="large" />, 
                            title: "Total Projects", 
                            value: dashboardData.projects.length,
                            color: 'secondary',
                            link: `/${role}/projects`
                        },
                    
// In ManagerDashboard.jsx, update the pending timesheets card configuration
{ 
  icon: <PendingActions fontSize="large" />, 
  title: "Pending Timesheets", 
  value: dashboardData.pendingTimesheets.length,
  color: 'warning',
  onClick: () => navigate(`/${role}/my-team-timesheets`, {
    state: { statusFilter: "Pending" }
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
                                onClick={stat.onClick ? stat.onClick : () => navigate(stat.link)}
                            >
                                <Box sx={{ 
                                    display: 'flex',
                                    alignItems: 'center',
                                    mb: 2
                                }}>
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
                                        <Typography 
                                            variant="h4" 
                                            fontWeight="bold"
                                        >
                                            {stat.value}
                                        </Typography>
                                    </Box>
                                </Box>
                                <Box sx={{ 
                                    height: '4px',
                                    borderRadius: '2px',
                                    bgcolor: 'divider',
                                    overflow: 'hidden'
                                }}>
                                    <Box sx={{ 
                                        height: '100%',
                                        width: '60%',
                                        bgcolor: `${stat.color}.main`,
                                        borderRadius: '2px'
                                    }} />
                                </Box>
                            </Card>
                        </Grid>
                    ))}
                </Grid>

                {/* Projects and Tasks Section - Modern Design */}
                <Grid container spacing={3} sx={{ mb: 4 }}>
                    {/* Recent Projects */}
                    <Grid item xs={12} md={6}>
                        <Card sx={{ 
                            p: 2,
                            borderRadius: '16px',
                            background: 'white',
                            height: '100%',
                            boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
                        }}>
                            <Box sx={{ 
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                mb: 2
                            }}>
                                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                    <WorkIcon color="primary" sx={{ mr: 1 }} />
                                    <Typography variant="subtitle1" fontWeight="bold">
                                        Recent Projects
                                    </Typography>
                                </Box>
                                <Button 
                                    size="small"
                                    endIcon={<ChevronRight />}
                                    sx={{ textTransform: 'none' }}
                                    onClick={() => navigate(`/${role}/projects`)}
                                >
                                    View All
                                </Button>
                            </Box>
                            
                            {dashboardData.projects.filter(p => p.status !== 'COMPLETED').length > 0 ? (
             <List>
  {dashboardData.projects
    .filter(p => p.projectStatus !== 'COMPLETED')
    .slice(0, 3)
    .map((project) => (
      <ListItem 
        key={project.projectId}
        sx={{ 
          p: 1.5,
          borderRadius: '8px',
          '&:hover': {
            backgroundColor: 'action.hover',
            cursor: 'pointer'
          }
        }}
        // onClick={() => navigate(`/${role}/projects/${project.projectId}`)}
      >
        <ListItemIcon sx={{ minWidth: 40 }}>
          <WorkIcon color="primary" />
        </ListItemIcon>
        <ListItemText
          primary={project.projectName}
          secondary={`Status: ${project.projectStatus}`}
        />
                                                <Chip 
                                                    label={project.priority} 
                                                    size="small" 
                                                    color={
                                                        project.priority === 'HIGH' ? 'error' :
                                                        project.priority === 'MEDIUM' ? 'warning' : 'success'
                                                    }
                                                    variant="outlined"
                                                />
                                            </ListItem>
                                        ))}
                                </List>
                            ) : (
                                <Box sx={{ 
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    py: 4,
                                    color: 'text.secondary'
                                }}>
                                    <WorkIcon fontSize="large" sx={{ mb: 1 }} />
                                    <Typography variant="body1">
                                        No recent projects
                                    </Typography>
                                </Box>
                            )}
                        </Card>
                    </Grid>

                    {/* Recent Tasks */}
                    <Grid item xs={12} md={6}>
                        <Card sx={{ 
                            p: 2,
                            borderRadius: '16px',
                            background: 'white',
                            height: '100%',
                            boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
                        }}>
                            <Box sx={{ 
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                mb: 2
                            }}>
                                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                    <Task color="secondary" sx={{ mr: 1 }} />
                                    <Typography variant="subtitle1" fontWeight="bold">
                                        Recent Tasks
                                    </Typography>
                                </Box>
                                <Button 
                                    size="small"
                                    endIcon={<ChevronRight />}
                                    sx={{ textTransform: 'none' }}
                                    onClick={() => navigate(`/${role}/projects/tasks`)}
                                >
                                    View All
                                </Button>
                            </Box>
                            
                            {dashboardData.tasks.length > 0 ? (
    <List>
        {dashboardData.tasks
            .slice(0, 3)
            .map((task) => (
                <ListItem 
                    key={task.id}
                    sx={{ 
                        p: 1.5,
                        borderRadius: '8px',
                        '&:hover': {
                            backgroundColor: 'action.hover',
                            cursor: 'pointer'
                        }
                    }}
                    // onClick={() => navigate(`/${role}/tasks/${task.id}`)}
                >
                    <ListItemIcon sx={{ minWidth: 40 }}>
                        <Assignment color={task.status === 'COMPLETED' ? 'success' : 'action'} />
                    </ListItemIcon>
                    <ListItemText
                        primary={task.title}
                        secondary={`Due ${formatDate(task.dueDate)}`}
                    />
                    <Chip 
                        label={task.status} 
                        size="small" 
                        color={
                            task.status === 'COMPLETED' ? 'success' :
                            task.status === 'IN_PROGRESS' ? 'info' : 'warning'
                        }
                        variant="outlined"
                    />
                </ListItem>
            ))}
    </List>
                            ) : (
                                <Box sx={{ 
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    py: 4,
                                    color: 'text.secondary'
                                }}>
                                    <Task fontSize="large" sx={{ mb: 1 }} />
                                    <Typography variant="body1">
                                        No tasks available
                                    </Typography>
                                </Box>
                            )}
                        </Card>
                    </Grid>
                </Grid>

                {/* Pending Timesheets Section */}
                <Card sx={{ 
                    p: 2,
                    borderRadius: '16px',
                    background: 'white',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.05)'
                }}>
                    <Box sx={{ 
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        mb: 2
                    }}>
                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <PendingActions color="warning" sx={{ mr: 1 }} />
                            <Typography variant="subtitle1" fontWeight="bold">
                                Pending Timesheets
                            </Typography>
                        </Box>
                        <Button 
                            size="small"
                            endIcon={<ChevronRight />}
                            sx={{ textTransform: 'none' }}
                            onClick={() => navigate(`/${role}/timesheets`)}
                        >
                            View All
                        </Button>
                    </Box>
                    
{dashboardData.pendingTimesheets.length > 0 ? (
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
                </TableRow>
            </TableHead>
            <TableBody>
                {dashboardData.pendingTimesheets.slice(0, 5).map((timesheet) => (
                    <TableRow
                        key={timesheet.timesheetId}
                        hover
                        onClick={() => navigate(`/${role}/timesheets/${timesheet.timesheetId}`)}
                        sx={{ cursor: 'pointer' }}
                    >
                        <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                <Avatar sx={{ width: 24, height: 24, mr: 1 }}>
                                    {timesheet.employeeName?.charAt(0)}
                                </Avatar>
                                {timesheet.employeeName}
                            </Box>
                        </TableCell>
                        <TableCell>
                            {timesheet.projects[0]?.projectName || "N/A"}
                        </TableCell>
                        <TableCell>
                            {format(new Date(timesheet.weekStartDate), "MMM dd")} -{" "}
                            {format(new Date(timesheet.weekEndDate), "MMM dd")}
                        </TableCell>
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    </TableContainer>
                    ) : (
                        <Box sx={{ 
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            py: 4,
                            color: 'text.secondary'
                        }}>
                            <PendingActions fontSize="large" sx={{ mb: 1 }} />
                            <Typography variant="body1">
                                No pending timesheets
                            </Typography>
                        </Box>
                    )}
                </Card>
            </Box>
        );
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

    const genderIcon = profileData?.personal?.gender?.toLowerCase() === 'male'
        ? <MaleIcon fontSize="small" color="primary" />
        : <FemaleIcon fontSize="small" color="secondary" />;

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

export default ManagerDashboard;