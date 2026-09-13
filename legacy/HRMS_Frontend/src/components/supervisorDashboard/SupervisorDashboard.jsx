import React, { useState, useEffect } from "react";
import { Link, Outlet, useNavigate, useLocation } from "react-router-dom";
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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions
} from "@mui/material";
import { styled } from "@mui/material/styles";
import {
  Dashboard as DashboardIcon,
  AccessTime as AccessTimeIcon,
  CalendarToday as CalendarTodayIcon,
  Person as PersonIcon,
  Logout as LogoutIcon,
  Work as WorkIcon,
  Male as MaleIcon,
  Female as FemaleIcon,
  People,
  Groups,
  Assignment,
  Visibility,
  Notifications,
  Check,
  Close
} from "@mui/icons-material";
import UserService from "../service/UserService";
import axios from "axios";
import Sidebar from "../Sidebar/Sidebar";

import API_BASE_URL from "../config/apiConfig";
import  { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

axios.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});


const StyledCard = styled(Card)(({ theme }) => ({
  borderRadius: "12px",
  boxShadow: "0 4px 20px 0 rgba(0,0,0,0.08)",
  transition: "transform 0.3s, box-shadow 0.3s",
  "&:hover": {
    transform: "translateY(-5px)",
    boxShadow: "0 8px 30px 0 rgba(0,0,0,0.12)"
  }
}));

const SupervisorDashboard = () => {
  const navigate = useNavigate();
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const location = useLocation();
  const theme = useTheme();
  const [searchQuery, setSearchQuery] = useState("");
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  
  const [teamEmployees, setTeamEmployees] = useState([]);
  const [projects, setProjects] = useState([]);
  const [pendingLeaves, setPendingLeaves] = useState([]);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [showTeam, setShowTeam] = useState(false);
  const [showProjects, setShowProjects] = useState(false);
  const [selectedLeave, setSelectedLeave] = useState(null);
  const [openLeaveDialog, setOpenLeaveDialog] = useState(false);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const token = localStorage.getItem("token");
        if (!token) {
          throw new Error("No authentication token found");
        }

        const response = await UserService.getCompleteProfile(token);

        if (response.employeeData) {
          setProfileData(response.employeeData);
          fetchSupervisorData(response.employeeData);
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

    const fetchSupervisorData = async (employeeData) => {
      try {
        setDashboardLoading(true);
        const empId = employeeData.personal.empId;
        
        const [employeesRes, projectsRes, leavesRes] = await Promise.all([
          axios.get(`${API_BASE_URL}/employees/search?supervisorId=${empId}`),
          axios.get(`${API_BASE_URL}/projects`),
          axios.get(`${API_BASE_URL}/leaves/pending`)
        ]);

        // Process projects data
        const projectsWithDetails = projectsRes.data.map(project => ({
          id: project.id,
          name: project.name,
          description: project.description,
          status: project.status,
          progress: project.progress,
          assignments: project.assignments.map(assignment => ({
            empId: assignment.empId,
            empName: assignment.empName
          })),
          assignedEmployeeNames: project.assignedEmployeeNames || []
        }));

        // Process employees data
        const employeesWithDetails = employeesRes.data.data.map(employee => ({
          empId: employee.empId,
          name: employee.name,
          position: employee.position,
          status: employee.status,
          projects: projectsWithDetails.filter(project => 
            project.assignments.some(a => a.empId === employee.empId)
          )
        }));

        setTeamEmployees(employeesWithDetails);
        setProjects(projectsWithDetails);
        setPendingLeaves(leavesRes.data);
      } catch (error) {
        console.error("Error fetching supervisor data:", error);
        setError("Failed to load supervisor dashboard data");
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

  const handleMyDashboard = () => {
    navigate("/user/employee-dashboard");
  };

  const handleApproveLeave = async (leaveId) => {
    try {
      await axios.patch(`${API_BASE_URL}/leaves/${leaveId}/approve`);
      setPendingLeaves(pendingLeaves.filter(leave => leave.id !== leaveId));
      setOpenLeaveDialog(false);
      setSelectedLeave(null);
    } catch (error) {
      console.error("Error approving leave:", error);
      setError("Failed to approve leave");
      setSnackbarOpen(true);
    }
  };

  const handleRejectLeave = async (leaveId) => {
    try {
      await axios.patch(`${API_BASE_URL}/leaves/${leaveId}/reject`);
      setPendingLeaves(pendingLeaves.filter(leave => leave.id !== leaveId));
      setOpenLeaveDialog(false);
      setSelectedLeave(null);
    } catch (error) {
      console.error("Error rejecting leave:", error);
      setError("Failed to reject leave");
      setSnackbarOpen(true);
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

  const genderIcon = profileData?.personal?.gender?.toLowerCase() === 'male'
    ? <MaleIcon fontSize="small" color="primary" />
    : <FemaleIcon fontSize="small" color="secondary" />;

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

    if (showTeam) {
      return (
        <Box sx={{ p: 3 }}>
          <Box sx={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            mb: 3
          }}>
            <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
              My Team Members
            </Typography>
            <Button 
              variant="outlined" 
              onClick={() => setShowTeam(false)}
              startIcon={<People />}
            >
              Back to Dashboard
            </Button>
          </Box>
          
          <TableContainer component={Paper} sx={{ borderRadius: '12px', boxShadow: '0 4px 20px 0 rgba(0,0,0,0.05)' }}>
            <Table>
              <TableHead>
                <TableRow sx={{ backgroundColor: theme.palette.primary.light }}>
                  <TableCell>Employee ID</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Position</TableCell>
                  <TableCell>Projects</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {teamEmployees.length > 0 ? (
                  teamEmployees.map((employee) => (
                    <TableRow key={employee.empId}>
                      <TableCell>{employee.empId}</TableCell>
                      <TableCell>{employee.name}</TableCell>
                      <TableCell>{employee.position || 'N/A'}</TableCell>
                      <TableCell>
                        {employee.projects.length > 0 ? (
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                            {employee.projects.slice(0, 2).map(project => (
                              <Chip 
                                key={project.id} 
                                label={project.name} 
                                size="small" 
                                color="primary"
                              />
                            ))}
                            {employee.projects.length > 2 && (
                              <Chip 
                                label={`+${employee.projects.length - 2}`} 
                                size="small" 
                                variant="outlined"
                              />
                            )}
                          </Box>
                        ) : 'No projects'}
                      </TableCell>
                      <TableCell>
                        <Chip 
                          label={employee.status || 'ACTIVE'} 
                          color={employee.status === 'ACTIVE' ? 'success' : 'default'}
                          size="small"
                        />
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      No team members found
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      );
    }

    if (showProjects) {
      return (
        <Box sx={{ p: 3 }}>
          <Box sx={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            mb: 3
          }}>
            <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
              My Projects
            </Typography>
            <Button 
              variant="outlined" 
              onClick={() => setShowProjects(false)}
              startIcon={<WorkIcon />}
            >
              Back to Dashboard
            </Button>
          </Box>
          
          <TableContainer component={Paper} sx={{ borderRadius: '12px', boxShadow: '0 4px 20px 0 rgba(0,0,0,0.05)' }}>
            <Table>
              <TableHead>
                <TableRow sx={{ backgroundColor: theme.palette.primary.light }}>
                  <TableCell>Project Name</TableCell>
                  <TableCell>Team Members</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Progress</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {projects.length > 0 ? (
                  projects.map((project) => (
                    <TableRow key={project.id}>
                      <TableCell>
                        <Typography fontWeight="bold">{project.name}</Typography>
                        <Typography variant="body2">{project.description}</Typography>
                      </TableCell>
                      <TableCell>
                        {project.assignedEmployeeNames.length > 0 ? (
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                            {project.assignedEmployeeNames.slice(0, 3).map((name, index) => (
                              <Chip 
                                key={index} 
                                label={name} 
                                size="small" 
                                variant="outlined"
                              />
                            ))}
                            {project.assignedEmployeeNames.length > 3 && (
                              <Chip 
                                label={`+${project.assignedEmployeeNames.length - 3}`} 
                                size="small" 
                                variant="outlined"
                              />
                            )}
                          </Box>
                        ) : 'No team members'}
                      </TableCell>
                      <TableCell>
                        <Chip 
                          label={project.status} 
                          color={
                            project.status === 'COMPLETED' ? 'success' : 
                            project.status === 'IN_PROGRESS' ? 'primary' : 'default'
                          }
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          <LinearProgress 
                            variant="determinate" 
                            value={project.progress || 0} 
                            sx={{ width: '100%' }}
                          />
                          <Typography variant="body2">{project.progress || 0}%</Typography>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      No projects found
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      );
    }

    return (
      <Box sx={{ p: 3 }}>
        {/* Dashboard Header with My Dashboard button */}
        <Box sx={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center',
          mb: 4
        }}>
          <Typography variant="h4" gutterBottom sx={{ fontWeight: 'bold' }}>
            Welcome, {profileData?.personal?.firstName || 'Supervisor'}
          </Typography>
          <Button
            variant="contained"
            color="primary"
            onClick={handleMyDashboard}
            sx={{ 
              alignSelf: 'flex-start',
              textTransform: 'none',
              borderRadius: '8px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
            }}
          >
            My Dashboard
          </Button>
        </Box>

        {/* Stats Cards */}
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} md={4}>
            <StyledCard sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Groups fontSize="large" color="primary" sx={{ mr: 2 }} />
                  <Typography variant="h5">Team Members</Typography>
                </Box>
                <Typography variant="h3" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {teamEmployees.length}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Employees under your supervision
                </Typography>
                <Button
                  variant="contained"
                  fullWidth
                  onClick={() => setShowTeam(true)}
                  startIcon={<People />}
                >
                  Manage Team
                </Button>
              </CardContent>
            </StyledCard>
          </Grid>

          <Grid item xs={12} md={4}>
            <StyledCard sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <WorkIcon fontSize="large" color="primary" sx={{ mr: 2 }} />
                  <Typography variant="h5">Active Projects</Typography>
                </Box>
                <Typography variant="h3" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {projects.filter(p => p.status !== 'COMPLETED').length}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Projects you're managing
                </Typography>
                <Button
                  variant="contained"
                  fullWidth
                  onClick={() => setShowProjects(true)}
                  startIcon={<Assignment />}
                >
                  View Projects
                </Button>
              </CardContent>
            </StyledCard>
          </Grid>

          <Grid item xs={12} md={4}>
            <StyledCard sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Badge badgeContent={pendingLeaves.length} color="error" sx={{ mr: 2 }}>
                    <Notifications fontSize="large" color="primary" />
                  </Badge>
                  <Typography variant="h5">Leave Requests for Review</Typography>
                </Box>
                <Typography variant="h3" sx={{ fontWeight: 'bold', mb: 1 }}>
                  {pendingLeaves.length}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Leaves awaiting approval
                </Typography>
                <Button
                  variant="contained"
                  fullWidth
                  onClick={() => {
                    if (pendingLeaves.length > 0) {
                      setSelectedLeave(pendingLeaves[0]);
                      setOpenLeaveDialog(true);
                    }
                  }}
                  startIcon={<CalendarTodayIcon />}
                  disabled={pendingLeaves.length === 0}
                  color={pendingLeaves.length > 0 ? "warning" : "primary"}
                >
                  {pendingLeaves.length > 0 ? "Review Leaves" : "No Pending Leaves"}
                </Button>
              </CardContent>
            </StyledCard>
          </Grid>
        </Grid>
      </Box>
    );
  };

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

      {/* Leave Approval Dialog */}
      <Dialog open={openLeaveDialog} onClose={() => setOpenLeaveDialog(false)}>
        <DialogTitle>Review Leave Request</DialogTitle>
        <DialogContent>
          {selectedLeave && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="body1"><strong>Employee:</strong> {selectedLeave.empName}</Typography>
              <Typography variant="body1"><strong>Type:</strong> {selectedLeave.leaveType}</Typography>
              <Typography variant="body1"><strong>From:</strong> {new Date(selectedLeave.startDate).toLocaleDateString()}</Typography>
              <Typography variant="body1"><strong>To:</strong> {new Date(selectedLeave.endDate).toLocaleDateString()}</Typography>
              <Typography variant="body1"><strong>Reason:</strong> {selectedLeave.reason}</Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button 
            onClick={() => handleRejectLeave(selectedLeave.id)} 
            color="error"
            startIcon={<Close />}
          >
            Reject
          </Button>
          <Button 
            onClick={() => handleApproveLeave(selectedLeave.id)} 
            color="success"
            startIcon={<Check />}
          >
            Approve
          </Button>
        </DialogActions>
      </Dialog>

      <AppBar position="static" color="primary">
        <Toolbar sx={{ justifyContent: "space-between" }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            <Avatar
              sx={{
                width: 48,
                height: 48,
                backgroundColor: 'background.paper'
              }}
            >
              {genderIcon}
            </Avatar>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
                {profileData?.personal?.firstName || 'Unknown'} {profileData?.personal?.lastName || 'User'}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" sx={{ lineHeight: 1.2 }}>
                  ID: {profileData?.personal?.empId || 'N/A'}
                </Typography>
                <Chip label="Supervisor" size="small" color="secondary" />
              </Box>
            </Box>
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            <Badge badgeContent={pendingLeaves.length} color="error">
              <IconButton color="inherit" onClick={() => {
                if (pendingLeaves.length > 0) {
                  setSelectedLeave(pendingLeaves[0]);
                  setOpenLeaveDialog(true);
                }
              }}>
                <Notifications />
              </IconButton>
            </Badge>
            <TextField
              variant="outlined"
              size="small"
              placeholder="Search..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              sx={{ 
                backgroundColor: "white", 
                borderRadius: 1,
                '& .MuiOutlinedInput-root': {
                  '& fieldset': {
                    borderColor: 'transparent',
                  },
                  '&:hover fieldset': {
                    borderColor: 'transparent',
                  },
                  '&.Mui-focused fieldset': {
                    borderColor: 'transparent',
                  },
                }
              }}
            />
            <Button
              variant="contained"
              color="secondary"
              startIcon={<LogoutIcon />}
              onClick={handleLogout}
            >
              Logout
            </Button>
          </Box>
        </Toolbar>
      </AppBar>

      <Box sx={{ display: "flex", flexGrow: 1 }}>
        <Sidebar />
        <Box component="main" sx={{ flexGrow: 1, p: 3, bgcolor: "#f8fafc", ml: '200px' }}>
 
          {location.pathname === '/supervisor/supervisor-dashboard' ? renderDashboardContent() : <Outlet />}
        </Box>
      </Box>
    </Box>
  );
};

export default SupervisorDashboard;