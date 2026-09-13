import React, { useState, useEffect, useContext } from 'react';
import { Link } from 'react-router-dom';
import {
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  
  Collapse,
  Box,
  Typography,
  Avatar,
  Button,
  Divider,
  AppBar,
  Toolbar,
  CircularProgress,
  Snackbar,
  Alert,
  Select,
  MenuItem
} from '@mui/material';
import {
  Dashboard as DashboardIcon,
  People as PeopleIcon,
  Lock as LockIcon,
  CalendarToday as CalendarTodayIcon,
  Work as WorkIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  CheckCircle as CheckCircleIcon,
  ExpandLess,
  ExpandMore,
  AccessTime as AccessTimeIcon,
  Male as MaleIcon,
  Female as FemaleIcon,
  Logout as LogoutIcon
} from '@mui/icons-material';
import { userContext } from '../context/ContextProvider';
import UserService from '../service/UserService';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import { useNavigate } from "react-router-dom";
import { Outlet } from "react-router-dom";



const Sidebar = () => {
const { actions, activeRole, roles, updateAuthState } = useContext(userContext);
const role = activeRole?.toLowerCase();

  const [expandedMenus, setExpandedMenus] = useState({
    leave: false,
    system: false,
    project: false,
    time: false
  });
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const navigate = useNavigate();


  // Handle Role Switch

const handleRoleChange = async (newRole) => {
  try {
    const token = localStorage.getItem("token");
    if (!token) throw new Error("No token found, please login again.");

    const response = await axios.post(
      `${API_BASE_URL}/auth/switch-role`,
      { role: newRole },
      { headers: { Authorization: `Bearer ${token}` } }
    );

    if (response.data.statusCode === 200) {
      const { token: newToken, roles, actions, activeRole } = response.data;

      // ✅ Persist the new token immediately
      localStorage.setItem("token", newToken);

      // ✅ Build full new state (with fresh actions and role)
      const newAuthState = {
        roles: roles || [],
        actions: actions || [],
        activeRole: activeRole.toLowerCase(),
        authenticated: true,
        token: newToken,
      };

      // ✅ Save in localStorage
      localStorage.setItem("token", response.data.token);
localStorage.setItem("activeRole", response.data.activeRole.toLowerCase());
localStorage.setItem("roles", JSON.stringify(response.data.roles.map(r => r.toLowerCase())));
localStorage.setItem("actions", JSON.stringify(response.data.actions || []));
localStorage.setItem("authState", JSON.stringify(newAuthState));

updateAuthState(newAuthState);


      console.log("✅ Role switched:", newAuthState.activeRole);
      console.log("✅ Actions updated:", newAuthState.actions.length);

      setTimeout(() => {
      // ✅ Redirect based on the new role
      switch (newAuthState.activeRole) {
        case "admin":
          navigate("/admin/dashboard", { replace: true });
          break;
        case "hr":
          navigate("/hr/hr-dashboard", { replace: true });
          break;
        case "manager":
          navigate("/manager/manager-dashboard", { replace: true });
          break;
        case "supervisor":
          navigate("/supervisor/supervisor-dashboard", { replace: true });
          break;
        case "reporting manager":
          navigate("/reporting%20manager/reporting-manager-dashboard", { replace: true });
          break;
        default:
          navigate("/user/employee-dashboard", { replace: true });
      }
      }, 60);
    }
  } catch (err) {
    console.error("Failed to switch role:", err);
    setError("Failed to switch role");
    setSnackbarOpen(true);
  }
};

const fetchProfile = async (overrideToken = null) => {
  try {
    const token = overrideToken || localStorage.getItem("token");
    if (!token) throw new Error("No authentication token found");

    const response = await UserService.getCompleteProfile(token);

    if (response.employeeData) {
      setProfileData(response.employeeData);
    } else {
      throw new Error("No employee data found in response");
    }
  } catch (error) {
    console.error("Failed to fetch profile:", error);

    // 🔁 Retry once if token changed mid-load
    if (!overrideToken) {
      const newToken = localStorage.getItem("token");
      if (newToken) {
        console.warn("Retrying profile fetch with updated token...");
        return fetchProfile(newToken);
      }
    }

    setError(error.message || "Failed to load profile data");
    setSnackbarOpen(true);
  } finally {
    setLoading(false);
  }
};

useEffect(() => {
  fetchProfile();
}, []);


  const toggleMenu = (menu) => {
    setExpandedMenus(prev => ({
      ...prev,
      [menu]: !prev[menu]
    }));
  };

  const handleCloseSnackbar = (event, reason) => {
    if (reason === 'clickaway') {
      return;
    }
    setSnackbarOpen(false);
  };

  const handleLogout = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("activeRole");  // clear chosen role
  localStorage.removeItem("roles");       // clear roles array
  localStorage.removeItem("authState");   // clear context state if stored
  window.location.href = "/";
};

  // Get the appropriate dashboard link based on user role
  const getDashboardLink = () => {
    switch (role) {
      case 'admin':
        return `/${role}/dashboard`;
      case 'hr':
        return `/${role}/hr-dashboard`;
      case 'manager':
        return `/${role}/manager-dashboard`;
      case 'supervisor':
        return `/${role}/supervisor-dashboard`;
      case 'reporting manager':
        return `/${role}/reporting-manager-dashboard`;
      default:
        return `/${role}/employee-dashboard`;
    }
  };


  const menuItemsConfig = [
    {
      text: "Dashboard",
      icon: <DashboardIcon />,
      link: getDashboardLink(),

      visible:
        actions?.includes("VIEW_ADMIN_DASHBOARD") ||
        actions?.includes("VIEW_HR_DASHBOARD") ||
        actions?.includes("VIEW_MANAGER_DASHBOARD") ||
        actions?.includes("VIEW_SUPERVISOR_DASHBOARD") ||
        actions?.includes("VIEW_EMPLOYEE_DASHBOARD") ||
        actions?.includes("VIEW_REPORTING_MANAGER_DASHBOARD"),
      requiredActions: [
        "VIEW_ADMIN_DASHBOARD",
        "VIEW_HR_DASHBOARD",
        "VIEW_MANAGER_DASHBOARD",
        "VIEW_SUPERVISOR_DASHBOARD",
        "VIEW_EMPLOYEE_DASHBOARD",
        "VIEW_REPORTING_MANAGER_DASHBOARD"
      ]
    },
    {
      text: "My Team",
      icon: <PeopleIcon />,
      link: `/${role}/my-team`,
      visible: actions?.includes("VIEW_MY_TEAM"),
      requiredActions: ["VIEW_MY_TEAM"]
    },

    {
      text: "Reporting Team",
      icon: <PeopleIcon />,
      link: `/${role}/reporting-team`,
      visible: actions?.includes("VIEW_REPORTING_TEAM"),
      requiredActions: ["VIEW_REPORTING_TEAM"]
    },
    {
      text: "Employees",
      icon: <PeopleIcon />,
      link: `/${role}/employees`,
      visible: actions?.includes("MANAGE_EMPLOYEES"),
      requiredActions: ["MANAGE_EMPLOYEES"]
    },
    {
      text: "System",
      icon: <SettingsIcon />,
      menuKey: "system",
      visible: (
        actions?.includes("MANAGE_USERS_MANAGEMENT") ||
        actions?.includes("CREATE_ACTIONS") ||
        actions?.includes("CREATE_ROLE") ||
        actions?.includes("VIEW_LIST_ACTIONS") ||
        actions?.includes("VIEW_LIST_ROLES")
      ),
      requiredActions: [],
      subItems: [
        {
          text: "User Management",
          link: `/${role}/user-management`,
          visible: actions?.includes("MANAGE_USERS_MANAGEMENT"),
          requiredActions: ["MANAGE_USERS_MANAGEMENT"]
        },
        {
          text: "Actions",
          link: `/${role}/create-action`,
          visible: actions?.includes("CREATE_ACTIONS"),
          requiredActions: ["CREATE_ACTIONS"]
        },
        {
          text: "Roles",
          link: `/${role}/create-role`,
          visible: actions?.includes("CREATE_ROLE"),
          requiredActions: ["CREATE_ROLE"]
        },
        {
          text: "List Actions",
          link: `/${role}/list-actions`,
          visible: actions?.includes("VIEW_LIST_ACTIONS"),
          requiredActions: ["VIEW_LIST_ACTIONS"]
        },
        {
          text: "List Roles",
          link: `/${role}/list-roles`,
          visible: actions?.includes("VIEW_LIST_ROLES"),
          requiredActions: ["VIEW_LIST_ROLES"]
        },
      ].filter(item => item?.visible)
    },
    {
      text: "Projects",
      icon: <WorkIcon />,
      menuKey: "project",
      visible: (actions?.includes("MANAGE_PROJECTS") || actions?.includes("MANAGE_TASKS")
        || actions?.includes("VIEW_PROJECT") || actions?.includes("VIEW_TASK")),
      requiredActions: [],
      subItems: [
        {
          text: "My Projects",
          link: `/${role}/myproject`,
          visible: actions?.includes("VIEW_PROJECT"),
          requiredActions: ["VIEW_PROJECT"]
        },
        {
          text: "My Tasks",
          link: `/${role}/mytask`,
          visible: actions?.includes("VIEW_TASK"),
          requiredActions: ["VIEW_TASK"]
        },
        {
          text: "Projects",
          link: `/${role}/projects`,
          visible: actions?.includes("MANAGE_PROJECTS"),
          requiredActions: ["MANAGE_PROJECTS"]
        },
        {
          text: "Tasks",
          link: `/${role}/projects/tasks`,
          visible: actions?.includes("MANAGE_TASKS"),
          requiredActions: ["MANAGE_TASKS"]
        },
      ].filter(item => item?.visible)
    },
    {
      text: "Time & Management",
      icon: <AccessTimeIcon />,
      menuKey: "time",
      visible: (actions?.includes("MANAGE_TIMESHEET") || actions?.includes("VIEW_ATTENDANCE") ||
        actions?.includes("MANAGE_TIMESHEETS") || actions?.includes("MANAGE_ATTENDANCES")) ||
        actions?.includes("MANAGER_MANAGE_TIMESHEETS") || actions?.includes("REPORTING_MANAGER_MANAGE_TIMESHEETS"),
      requiredActions: [],
      subItems: [
        {
          text: "My Timesheet",
          link: `/${role}/my-timesheet-detail`,
          visible: actions?.includes("MANAGE_TIMESHEET"),
          requiredActions: ["MANAGE_TIMESHEET"]
        },


        {
          text: "My Teams Timesheets",
          link: `/${role}/my-team-timesheets`,
          visible: actions?.includes("MANAGER_MANAGE_TIMESHEETS"),
          requiredActions: ["MANAGER_MANAGE_TIMESHEETS"]
        },
        {
          text: "My Reporting Team Timesheets",
          link: `/${role}/my-reporting-team-timesheets`,
          visible: actions?.includes("REPORTING_MANAGER_MANAGE_TIMESHEETS"),
          requiredActions: ["REPORTING_MANAGER_MANAGE_TIMESHEETS"]
        },
        {
          text: "Timesheets",
          link: `/${role}/timesheets`,
          visible: actions?.includes("MANAGE_TIMESHEETS"),
          requiredActions: ["MANAGE_TIMESHEETS"]
        },
        {
          text: "My Attendance",
          link: `/${role}/my-attendance`,
          visible: actions?.includes("VIEW_ATTENDANCE"),
          requiredActions: ["VIEW_ATTENDANCE"]
        },
        {
          text: "Attendances",
          link: `/${role}/attendance`,
          visible: actions?.includes("MANAGE_ATTENDANCES"),
          requiredActions: ["MANAGE_ATTENDANCES"]
        },
      ].filter(item => item?.visible)
    },
    {
      text: "Leave Management",
      icon: <CalendarTodayIcon />,
      menuKey: "leave",
      visible: (
        actions?.includes("MANAGE_LEAVES") ||
        actions?.includes("MANAGE_LEAVE_TYPES") ||
        actions?.includes("MANAGE_LEAVE_BALANCES") ||
        actions?.includes("MANAGE_OVERTIME_REQUESTS") ||
        actions?.includes("MANAGE_HOLIDAYS") ||
        actions?.includes("APPLY_LEAVE") ||
        actions?.includes("VIEW_LEAVE_BALANCE") ||
        actions?.includes("APPLY_OVERTIME_REQUEST") ||
        actions?.includes("VIEW_HOLIDAY")
      ),
      requiredActions: [],
      subItems: [
        {
          text: "Apply Leaves",
          link: `/${role}/apply-leaves`,
          visible: actions?.includes("APPLY_LEAVE"),
          requiredActions: ["APPLY_LEAVE"]
        },
        {
          text: "My Leave Balance",
          link: `/${role}/my-leave-balance`,
          visible: actions?.includes("VIEW_LEAVE_BALANCE"),
          requiredActions: ["VIEW_LEAVE_BALANCE"]
        },
        {
          text: "My Overtime",
          link: `/${role}/overtime-form`,
          visible: actions?.includes("APPLY_OVERTIME_REQUEST"),
          requiredActions: ["APPLY_OVERTIME_REQUEST"]
        },
        {
          text: "My Holiday",
          link: `/${role}/my-holiday`,
          visible: actions?.includes("VIEW_HOLIDAY"),
          requiredActions: ["VIEW_HOLIDAY"]
        },
        {
          text: "Leaves",
          link: `/${role}/leaves`,
          visible: actions?.includes("MANAGE_LEAVES"),
          requiredActions: ["MANAGE_LEAVES"]
        },
        // {
        //   text: "Leave Type",
        //   link: `/${role}/leaves-type`,
        //   visible: actions?.includes("MANAGE_LEAVE_TYPES"),
        //   requiredActions: ["MANAGE_LEAVE_TYPES"]
        // },
        {
          text: "Leave Details",
          link: `/${role}/employee-leave-details`,
          visible: actions?.includes("MANAGE_EMPLOYEES_LEAVES_DETAILS"),
          requiredActions: ["MANAGE_EMPLOYEES_LEAVES_DETAILS"]
        },
        {
          text: "Leave Balance",
          link: `/${role}/leave-balance`,
          visible: actions?.includes("MANAGE_LEAVE_BALANCES"),
          requiredActions: ["MANAGE_LEAVE_BALANCES"]
        },
        {
          text: "Overtime",
          link: `/${role}/overtime-request`,
          visible: actions?.includes("MANAGE_OVERTIME_REQUESTS"),
          requiredActions: ["MANAGE_OVERTIME_REQUESTS"]
        },
        {
          text: "Holiday",
          link: `/${role}/holiday`,
          visible: actions?.includes("MANAGE_HOLIDAYS"),
          requiredActions: ["MANAGE_HOLIDAYS"]
        },
      ].filter(item => item?.visible)
    },
  ];

  const commonMenuItems = [
    {
      text: "Profile",
      icon: <PersonIcon />,
      link: `/${role}/profile`,
      visible: actions?.includes("VIEW_PROFILE"),
      requiredActions: ["VIEW_PROFILE"]
    },
    {
  text: "Settings",
  icon: <SettingsIcon />,
  visible: actions?.includes("VIEW_SETTINGS"),
  requiredActions: ["VIEW_SETTINGS"],
  subItems: [
   
    {
      text: "Change Password",
      icon: <LockIcon />,  // or <PasswordIcon /> if you have it
      link: `/${role}/change-password`,
      visible: actions?.includes("CHANGE_PASSWORD"),
      requiredActions: ["CHANGE_PASSWORD"]
    }
  ]
}
  ];

  const allMenuItems = [...menuItemsConfig, ...commonMenuItems]
    .filter(item => item?.visible);

  const genderIcon = profileData?.personal?.gender?.toLowerCase() === 'male'
    ? <MaleIcon fontSize="small" color="primary" />
    : <FemaleIcon fontSize="small" color="secondary" />;

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
    <Box sx={{ display: 'flex', height: '100vh' }}>
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

      {/* Top App Bar */}
       {/* ✅ Top App Bar with Role Switch */}
      <AppBar position="fixed" sx={{ zIndex: 1201, bgcolor: '#1976d2', height: '64px' }}>
        <Toolbar sx={{ display: 'flex', justifyContent: 'space-between', px: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar sx={{ bgcolor: 'white' }}>{genderIcon}</Avatar>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 'bold', color: 'white' }}>
                {profileData?.personal?.firstName || 'User'} {profileData?.personal?.lastName || ''}
              </Typography>
              <Typography variant="caption" sx={{ color: 'white' }}>
                ID: {profileData?.personal?.empId || 'N/A'}
              </Typography>
            </Box>
          </Box>

                    {/* 🔽 Right side controls (Role Switch + Logout) */}
          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            {roles && roles.length > 1 ? (
              <Select
                value={activeRole || ""}
                onChange={(e) => handleRoleChange(e.target.value)}
                size="small"
                variant="outlined"
                sx={{
                  bgcolor: "white",
                  color: "#1976d2",
                  borderRadius: "50px",
                  fontWeight: "bold",
                  fontSize: "0.85rem",
                  "& .MuiOutlinedInput-notchedOutline": { border: "none" },
                  "&:hover .MuiOutlinedInput-notchedOutline": { border: "none" }
                }}
              >
                {roles.map((r) => (
                  <MenuItem key={r} value={r.toLowerCase()}>
                    {r.toUpperCase()}
                  </MenuItem>
                ))}
              </Select>
            ) : (
              <Box
  sx={{
    mr: 2,
    px: 2,
    py: 0.5,
    bgcolor: "white",
    color: "#1976d2",
    borderRadius: "50px",
    fontWeight: "bold",
    fontSize: "0.8rem",
    display: "flex",
    alignItems: "center",
    boxShadow: "0 2px 6px rgba(0,0,0,0.15)"
  }}
>
  {activeRole?.toUpperCase()}
</Box>

            )}

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

      {/* Left Sidebar */}
      <Box sx={{
        width: 245,
        bgcolor: "#1e293b",
        color: "white",
        top: '64px',
        height: 'calc(100vh - 64px)',
        position: 'fixed',
        left: 0,
        overflowY: 'auto',
        zIndex: 1100,
        borderRight: '1px solid #334155'
      }}>
        <List sx={{ py: 1 }}>
          {allMenuItems.map((item, index) => (
            <React.Fragment key={index}>
              <ListItem disablePadding>
                <ListItemButton
                  component={item.subItems ? "button" : Link}
                  to={item.subItems ? undefined : item.link}
                  onClick={item.subItems ? () => toggleMenu(item.menuKey) : undefined}
                  sx={{
                    "&:hover": { bgcolor: "#64748b" },
                    borderRadius: 1,
                    mb: 0.5,
                    px: 2
                  }}
                >
                  <ListItemIcon sx={{ color: "white", minWidth: '40px' }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.text}
                    primaryTypographyProps={{ fontSize: '0.95rem' }}
                  />
                  {item.subItems && (
                    expandedMenus[item.menuKey] ? <ExpandLess /> : <ExpandMore />
                  )}
                </ListItemButton>
              </ListItem>

              {item.subItems && item.subItems.length > 0 && (
                <Collapse in={expandedMenus[item.menuKey]} timeout="auto" unmountOnExit>
                  <List component="div" disablePadding>
                    {item.subItems.map((subItem, subIndex) => (
                      <ListItemButton
                        key={subIndex}
                        component={Link}
                        to={subItem.link}
                        sx={{
                          pl: 6,
                          "&:hover": { bgcolor: "#475569" },
                          borderRadius: 1,
                          mb: 0.5
                        }}
                      >
                        <ListItemText
                          primary={subItem.text}
                          primaryTypographyProps={{ fontSize: '0.9rem' }}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                </Collapse>
              )}
            </React.Fragment>
          ))}
        </List>
      </Box>

      {/* Main Content Area */}
      <Box sx={{
        ml: '245px',
        mt: '64px',
        flexGrow: 1,
        p: 3,
        bgcolor: '#f8fafc',
        minHeight: 'calc(100vh - 64px)',
        overflowY: 'auto'
      }}>
        {/* Outlet for child routes will be rendered here */}
        <Outlet />
      </Box>
    </Box>
  );
};

export default Sidebar;