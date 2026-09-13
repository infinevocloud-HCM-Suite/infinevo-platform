import React, { useState, useEffect, useContext } from "react";
import {
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Grid,
  Chip,
  Box,
  TextField,
  InputAdornment,
  Divider,
  Badge,
  FormControl,
  InputLabel,
  Select,
  Tooltip,
  TablePagination,
  CircularProgress,
  Snackbar,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Card,
  CardContent,
  Avatar,
  Stack,
} from "@mui/material";
import {
  MoreVert,
  ArrowBackIos,
  ArrowForwardIos,
  Notifications as NotificationsIcon,
  InsertDriveFile,
  Search,
  Add,
  Edit,
  Send,
  CheckCircle,
  Pending,
  Drafts,
  Visibility,
  FilterList,
  AccessTime,
  Cancel,
  CalendarToday,
  Description,
  Work,
} from "@mui/icons-material";
import { format, startOfWeek, addDays, parseISO, isWeekend } from "date-fns";
import { useLocation, useNavigate } from "react-router-dom";
import axios from "axios";
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import API_BASE_URL from "../config/apiConfig";
import { userContext } from '../context/ContextProvider';

const TimesheetDetailPage = () => {
  const { activeRole, actions } = useContext(userContext);
const Role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const location = useLocation();
  const [weekStart, setWeekStart] = useState(startOfWeek(new Date(), { weekStartsOn: 1 }));
  const [entries, setEntries] = useState([]);
  const [anchorEl, setAnchorEl] = useState(null);
  const [selectedEntry, setSelectedEntry] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [notificationAnchorEl, setNotificationAnchorEl] = useState(null);
  const [statusFilter, setStatusFilter] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [loading, setLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success",
  });
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [projects, setProjects] = useState([]);
  const [filters, setFilters] = useState({
    status: "All Statuses",
    project: "All Projects",
    startDate: null,
    endDate: new Date(),
  });

  const daysOfWeek = Array.from({ length: 7 }).map((_, index) => {
    const dayDate = addDays(weekStart, index);
    return {
      label: format(dayDate, "EEE dd"),
      date: dayDate,
      dayName: format(dayDate, "EEE"),
      isWeekend: isWeekend(dayDate),
    };
  });

  console.log("TimesheetDetailPage Role:", Role);
  console.log("TimesheetDetailPage actions:", actions);

  const hasAction = (actionName) => actions.includes(actionName);

  const fetchTimesheetEntries = async () => {
    try {
      setLoading(true);
      let response;

      if (filters.startDate || filters.project !== "All Projects" || filters.status !== "All Statuses") {
        const params = {
          startDate: filters.startDate ? format(filters.startDate, 'yyyy-MM-dd') : null,
          endDate: filters.endDate ? format(filters.endDate, 'yyyy-MM-dd') : null,
          projectName: filters.project !== "All Projects" ? filters.project : null,
          status: filters.status !== "All Statuses" ? filters.status : null
        };

        Object.keys(params).forEach(key => params[key] === null && delete params[key]);

        response = await axios.get(`${API_BASE_URL}/api/timesheets/employee/filter`, {
          params: params
        });
      } else {
        const weekStartDate = format(weekStart, 'yyyy-MM-dd');
        response = await axios.get(`${API_BASE_URL}/api/timesheets/employee/current-week`, {
          params: { weekStartDate }
        });
      }

      if (response.data && Array.isArray(response.data)) {
        const formattedEntries = response.data.map(timesheet => {
          const hours = new Array(7).fill(0);
          let totalHours = 0;
          const allProjects = [];
          const allTasks = [];
          const allComments = [];

          timesheet.projects?.forEach(project => {
            allProjects.push(project.projectName);

            project.tasks?.forEach(task => {
              allTasks.push(task.taskName);

              task.days?.forEach(day => {
                const dayName = day.dayName.toUpperCase();
                const dayIndex = {
                  'MONDAY': 0,
                  'TUESDAY': 1,
                  'WEDNESDAY': 2,
                  'THURSDAY': 3,
                  'FRIDAY': 4,
                  'SATURDAY': 5,
                  'SUNDAY': 6
                }[dayName];

                if (dayIndex !== undefined) {
                  hours[dayIndex] += day.hours || 0;
                  totalHours += day.hours || 0;
                }

                if (day.description) {
                  allComments.push(day.description);
                }
              });
            });
          });

          return {
            ...timesheet,
            hours,
            totalHours,
            projectNames: allProjects.join(", "),
            taskNames: allTasks.join(", "),
            allComments: allComments.join(" | "),
            createdAtFormatted: format(parseISO(timesheet.createdAt), "dd MMM yyyy, h:mm a"),
            weekStart: parseISO(timesheet.weekStartDate),
            weekEnd: parseISO(timesheet.weekEndDate)
          };
        });

        setEntries(formattedEntries);
      } else {
        console.error("Unexpected API response format:", response.data);
        setSnackbar({
          open: true,
          message: "Unexpected data format received from server",
          severity: "error",
        });
        setEntries([]);
      }
    } catch (error) {
      console.error("Error fetching timesheet entries:", error);
      setSnackbar({
        open: true,
        message: "Failed to load timesheet entries",
        severity: "error",
      });
      setEntries([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchProjects = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/projects/by-emp`);
      if (response.data && Array.isArray(response.data)) {
        setProjects(response.data);
      }
    } catch (error) {
      console.error("Error fetching projects:", error);
    }
  };

  const fetchTimesheetNotifications = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/api/timesheet-notifications/unread`);
      setNotifications(response.data);
      const countResponse = await axios.get(`${API_BASE_URL}/api/timesheet-notifications/unread-count`);
      setUnreadCount(countResponse.data);
    } catch (error) {
      console.error("Error fetching timesheet notifications:", error);
    }
  };

  useEffect(() => {
    fetchTimesheetEntries();
    fetchProjects();
    fetchTimesheetNotifications();

    const interval = setInterval(() => {
      fetchTimesheetNotifications();
    }, 30000);

    return () => clearInterval(interval);
  }, [location.state, refreshTrigger, weekStart, filters]);

  const handleWeekNavigation = (direction) => {
    setWeekStart(addDays(weekStart, direction * 7));
  };

  const handleMenuOpen = (event, entry) => {
    setAnchorEl(event.currentTarget);
    setSelectedEntry(entry);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setSelectedEntry(null);
  };

  const handleView = () => {
    if (!selectedEntry) return;

    navigate(`/${Role}/my-timesheet-view`, {
      state: {
        entry: selectedEntry
      },
    });
    handleMenuClose();
  };

  const handleEdit = () => {
    if (!selectedEntry) return;

    navigate(`/${Role}/edit-my-timesheet/${selectedEntry.timesheetId}`, {
      state: {
        existingEntry: selectedEntry,
        isEditMode: true,
        weekRange: `${format(selectedEntry.weekStart, "dd MMM")} - ${format(
          selectedEntry.weekEnd,
          "dd MMM yyyy"
        )}`,
        weekStart: selectedEntry.weekStart,
        weekEnd: selectedEntry.weekEnd,
        projects: selectedEntry.projects
      },
    });
    handleMenuClose();
  };

  const handleCancelEntry = async () => {
    try {
      setLoading(true);
      await axios.put(`${API_BASE_URL}/api/timesheets/${selectedEntry.timesheetId}/cancel`);
      setRefreshTrigger(prev => prev + 1);
      setSnackbar({
        open: true,
        message: "Timesheet entry cancelled successfully",
        severity: "success",
      });
    } catch (error) {
      console.error("Error cancelling timesheet entry:", error);
      setSnackbar({
        open: true,
        message: "Failed to cancel timesheet entry",
        severity: "error",
      });
    } finally {
      setLoading(false);
      handleMenuClose();
    }
  };

  const handleSubmitEntry = async () => {
    if (!selectedEntry) return;

    try {
      setLoading(true);
      await axios.put(
        `${API_BASE_URL}/api/timesheets/${selectedEntry.timesheetId}/submit`
      );

      setRefreshTrigger(prev => prev + 1);
      setSnackbar({
        open: true,
        message: "Timesheet submitted successfully",
        severity: "success",
      });

      setEntries(prevEntries =>
        prevEntries.map(entry =>
          entry.timesheetId === selectedEntry.timesheetId
            ? { ...entry, status: "SUBMITTED" }
            : entry
        )
      );

      // Refresh notifications after submission
      fetchTimesheetNotifications();
    } catch (error) {
      console.error("Error submitting timesheet:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to submit timesheet",
        severity: "error",
      });
    } finally {
      setLoading(false);
      handleMenuClose();
    }
  };

  const handleSubmitAll = async () => {
    try {
      setLoading(true);
      const response = await axios.put(
        `${API_BASE_URL}/api/timesheets/submit-all`,
        {},
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("token")}`,
          },
        }
      );

      if (response.status === 400 && response.data.invalidTimesheets) {
        setSnackbar({
          open: true,
          message: `Cannot submit timesheets with non-DRAFT projects: ${response.data.invalidTimesheets.join(", ")}`,
          severity: "error",
        });
        return;
      }

      setRefreshTrigger(prev => prev + 1);
      setSnackbar({
        open: true,
        message: response.data.message || "All draft timesheets submitted successfully",
        severity: "success",
      });

      // Refresh notifications after submission
      fetchTimesheetNotifications();
    } catch (error) {
      console.error("Error submitting all timesheets:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to submit all timesheets",
        severity: error.response?.status === 400 ? "error" : "error",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleAddTimesheetEntry = () => {
    navigate(`/${Role}/my-timesheet`, {
      state: {
        weekRange: `${format(weekStart, "dd MMM")} - ${format(
          addDays(weekStart, 6),
          "dd MMM yyyy"
        )}`,
      },
    });
  };

  const handleStatusFilter = (status) => {
    if (statusFilter === status) {
      setStatusFilter(null);
    } else {
      setStatusFilter(status);
    }
    setPage(0);
  };

  const handleFilterModalOpen = () => {
    setFilterModalOpen(true);
  };

  const handleFilterModalClose = () => {
    setFilterModalOpen(false);
  };

  const handleFilterChange = (field, value) => {
    setFilters(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const applyFilters = () => {
    setPage(0);
    setFilterModalOpen(false);
  };

  const resetFilters = () => {
    setFilters({
      status: "All Statuses",
      project: "All Projects",
      startDate: null,
      endDate: new Date(),
    });
    setFilterModalOpen(false);
  };

  const handleNotificationClick = (event) => {
    setNotificationAnchorEl(event.currentTarget);
  };

  const handleNotificationClose = () => {
    setNotificationAnchorEl(null);
  };

  const markNotificationAsRead = async (notificationId) => {
    try {
      await axios.post(`${API_BASE_URL}/api/timesheet-notifications/mark-as-read/${notificationId}`);
      fetchTimesheetNotifications();
    } catch (error) {
      console.error("Error marking notification as read:", error);
    }
  };

  const markAllNotificationsAsRead = async () => {
    try {
      await axios.post(`${API_BASE_URL}/api/timesheet-notifications/mark-all-read`);
      fetchTimesheetNotifications();
    } catch (error) {
      console.error("Error marking all notifications as read:", error);
    }
  };

  const handleNotificationItemClick = (notification) => {
    markNotificationAsRead(notification.id);
    if (notification.timesheetId) {
      navigate(`/${Role}/my-timesheet-view`, {
        state: { entry: { timesheetId: notification.timesheetId } }
      });
    }
    handleNotificationClose();
  };

  const filteredEntries = entries.filter(
    (entry) =>
      (entry.timesheetId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        entry.projectNames.toLowerCase().includes(searchTerm.toLowerCase()) ||
        entry.status.toLowerCase().includes(searchTerm.toLowerCase())) &&
      (statusFilter ? entry.status === statusFilter : true) &&
      (filters.project !== "All Projects" ? entry.projectNames.includes(filters.project) : true) &&
      (filters.startDate && filters.endDate ?
        entry.weekStart >= new Date(filters.startDate) &&
        entry.weekStart <= new Date(filters.endDate) : true)
  );

  const totalHours = entries.reduce((total, entry) => {
    return total + entry.totalHours;
  }, 0);

  const getStatusIcon = (status) => {
    switch (status) {
      case "SUBMITTED":
        return <Pending color="warning" fontSize="small" />;
      case "DRAFT":
        return <Drafts color="info" fontSize="small" />;
      case "APPROVED":
        return <CheckCircle color="success" fontSize="small" />;
      case "REJECTED":
        return <Pending color="error" fontSize="small" />;
      case "CANCELLED":
        return <Cancel color="error" fontSize="small" />;
      default:
        return <Drafts color="action" fontSize="small" />;
    }
  };

  const handleCloseSnackbar = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Paper elevation={0} sx={{ p: 3, width: "100%", maxWidth: 1400, mx: "auto" }}>
        <Grid container justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
          <Grid item>
            <Typography variant="h5" sx={{ fontWeight: "bold" }}>
              My Time Sheet
            </Typography>
          </Grid>

          <Grid item>
            <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
              <Box sx={{ display: "flex", alignItems: "center" }}>
                <IconButton
                  onClick={() => handleWeekNavigation(-1)}
                  size="small"
                  disabled={loading}
                >
                  <ArrowBackIos fontSize="small" />
                </IconButton>
                <Typography variant="subtitle1" sx={{ mx: 1 }}>
                  {format(weekStart, "dd")} - {format(
                    addDays(weekStart, 6),
                    "dd MMMM yyyy"
                  )}
                </Typography>
                <IconButton
                  onClick={() => handleWeekNavigation(1)}
                  size="small"
                  disabled={loading}
                >
                  <ArrowForwardIos fontSize="small" />
                </IconButton>
              </Box>

              <Chip
                label={`${entries.filter((e) => e.status === "DRAFT").length} Draft`}
                color="info"
                size="small"
                variant={statusFilter === "DRAFT" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("DRAFT")}
                clickable
              />
              <Chip
                label={`${entries.filter((e) => e.status === "SUBMITTED").length} Submitted`}
                color="warning"
                size="small"
                variant={statusFilter === "SUBMITTED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("SUBMITTED")}
                clickable
              />
              <Chip
                label={`${entries.filter((e) => e.status === "APPROVED").length} Approved`}
                color="success"
                size="small"
                variant={statusFilter === "APPROVED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("APPROVED")}
                clickable
              />
              <Chip
                label={`${entries.filter((e) => e.status === "REJECTED").length} Rejected`}
                color="error"
                size="small"
                variant={statusFilter === "REJECTED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("REJECTED")}
                clickable
              />
              <Chip
                label={`${entries.filter((e) => e.status === "CANCELLED").length} Cancelled`}
                color="error"
                size="small"
                variant={statusFilter === "CANCELLED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("CANCELLED")}
                clickable
              />

              <IconButton
                onClick={handleNotificationClick}
                disabled={loading}
                sx={{ mr: 1 }}
              >
                <Badge badgeContent={unreadCount} color="error">
                  <NotificationsIcon />
                </Badge>
              </IconButton>

              <Tooltip title="Export to Excel">
                <IconButton disabled={loading}>
                  <InsertDriveFile />
                </IconButton>
              </Tooltip>
            </Box>
          </Grid>
        </Grid>

        <Dialog open={filterModalOpen} onClose={handleFilterModalClose} maxWidth="sm" fullWidth>
          <DialogTitle>Filter Timesheets</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select
                  value={filters.status}
                  label="Status"
                  onChange={(e) => handleFilterChange('status', e.target.value)}
                >
                  <MenuItem value="All Statuses">All Statuses</MenuItem>
                  <MenuItem value="DRAFT">Draft</MenuItem>
                  <MenuItem value="SUBMITTED">Submitted</MenuItem>
                  <MenuItem value="APPROVED">Approved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                  <MenuItem value="CANCELLED">Cancelled</MenuItem>
                </Select>
              </FormControl>

              <FormControl fullWidth size="small">
                <InputLabel>Project</InputLabel>
                <Select
                  value={filters.project}
                  label="Project"
                  onChange={(e) => handleFilterChange('project', e.target.value)}
                >
                  <MenuItem value="All Projects">All Projects</MenuItem>
                  {projects.map((project) => (
                    <MenuItem key={project.id} value={project.name}>
                      {project.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <DatePicker
                label="Start Date"
                value={filters.startDate}
                onChange={(newValue) => handleFilterChange('startDate', newValue)}
                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                maxDate={filters.endDate}
              />

              <DatePicker
                label="End Date"
                value={filters.endDate}
                onChange={(newValue) => handleFilterChange('endDate', newValue)}
                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                minDate={filters.startDate}
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={resetFilters} color="error">
              Reset
            </Button>
            <Button onClick={applyFilters} variant="contained" color="primary">
              Apply Filters
            </Button>
          </DialogActions>
        </Dialog>

        <Menu
          anchorEl={notificationAnchorEl}
          open={Boolean(notificationAnchorEl)}
          onClose={handleNotificationClose}
        >
          <MenuItem
            onClick={() => {
              markAllNotificationsAsRead();
              handleNotificationClose();
            }}
            disabled={notifications.length === 0}
          >
            Mark all as read
          </MenuItem>
          <Divider />
          {notifications.length > 0 ? (
            notifications.map((notification) => (
              <MenuItem
                key={notification.id}
                onClick={() => handleNotificationItemClick(notification)}
                sx={{
                  backgroundColor: notification.isRead ? 'inherit' : '#f5f5f5',
                  maxWidth: 300,
                  whiteSpace: 'normal'
                }}
              >
                <Typography variant="body2">
                  {notification.message}
                  <br />
                  <Typography variant="caption" color="text.secondary">
                    {format(new Date(notification.createdAt), "MMM dd, h:mm a")}
                  </Typography>
                </Typography>
              </MenuItem>
            ))
          ) : (
            <MenuItem disabled>
              No new notifications
            </MenuItem>
          )}
        </Menu>

        <Box
          sx={{
            display: "flex",
            gap: 2,
            mb: 3,
            flexWrap: "wrap",
            alignItems: "center",
          }}
        >
          {hasAction("ADD_ENTRY") && (
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={handleAddTimesheetEntry}
              sx={{ minWidth: 150 }}
              disabled={loading}
            >
              Add Entry
            </Button>
          )}

          <TextField
            size="small"
            placeholder="Search timesheets, projects..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            sx={{ minWidth: 250 }}
            disabled={loading}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
            }}
          />

          <IconButton
            onClick={handleFilterModalOpen}
            color={filters.status !== "All Statuses" || filters.project !== "All Projects" || filters.startDate ? "primary" : "default"}
            disabled={loading}
          >
            <FilterList />
          </IconButton>
        </Box>

        {loading && entries.length === 0 ? (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <Card elevation={2} sx={{ mb: 3, borderRadius: 2 }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Avatar sx={{ bgcolor: 'primary.main', mr: 2 }}>
                    <AccessTime />
                  </Avatar>
                  <Typography variant="h6" sx={{ fontWeight: "bold" }}>
                    Time Sheet Summary
                  </Typography>
                </Box>
                <Divider sx={{ mb: 3 }} />

                <Grid container spacing={2}>
                  {daysOfWeek.map((day, idx) => (
                    <Grid item xs={6} sm={4} md={2} key={idx}>
                      <Card
                        elevation={0}
                        sx={{
                          p: 2,
                          backgroundColor: day.isWeekend ? '#fff0f0' : '#f5f5f5',
                          borderRadius: 2,
                          textAlign: 'center'
                        }}
                      >
                        <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                          {day.label}
                        </Typography>
                        <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                          {entries.reduce(
                            (sum, entry) => sum + (entry.hours[idx] || 0),
                            0
                          )} hours
                        </Typography>
                      </Card>
                    </Grid>
                  ))}
                  <Grid item xs={6} sm={4} md={2}>
                    <Card
                      elevation={0}
                      sx={{
                        p: 2,
                        backgroundColor: '#e3f2fd',
                        borderRadius: 2,
                        textAlign: 'center'
                      }}
                    >
                      <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                        Total
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                        {totalHours.toFixed(2)} hours
                      </Typography>
                    </Card>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            <Paper elevation={2} sx={{ p: 2 }}>
              <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 'bold' }}>
                Logged Time
              </Typography>

              <TableContainer>
                <Table size="small" sx={{ minWidth: 800 }}>
                  <TableHead>
                    <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                      <TableCell sx={{ width: "15%", fontWeight: 'bold' }}>Timesheet ID</TableCell>
                      <TableCell sx={{ width: "25%", fontWeight: 'bold' }}>Projects</TableCell>
                      <TableCell sx={{ width: "10%", fontWeight: 'bold' }}>Created At</TableCell>
                      {daysOfWeek.map((day, idx) => (
                        <TableCell
                          key={idx}
                          align="center"
                          sx={{
                            color: day.isWeekend ? "#f44336" : "inherit",
                            fontWeight: "bold",
                            width: "5%",
                          }}
                        >
                          {day.dayName}
                        </TableCell>
                      ))}
                      <TableCell
                        align="center"
                        sx={{ width: "5%", fontWeight: "bold" }}
                      >
                        Total
                      </TableCell>
                      <TableCell sx={{ width: "10%", fontWeight: 'bold' }}>Status</TableCell>
                      <TableCell sx={{ width: "5%", fontWeight: 'bold' }}>Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredEntries
                      .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                      .map((entry) => {
                        return (
                          <TableRow
                            key={entry.timesheetId}
                            hover
                            selected={selectedEntry?.timesheetId === entry.timesheetId}
                            onClick={() => setSelectedEntry(entry)}
                            sx={{
                              cursor: "pointer",
                              backgroundColor:
                                entry.status === "SUBMITTED" ? "#f5f5f5" :
                                  entry.status === "APPROVED" ? "#e8f5e9" :
                                    entry.status === "REJECTED" ? "#ffebee" : 
                                      entry.status === "CANCELLED" ? "#f5f5f5" : "inherit",
                              opacity:
                                entry.status === "SUBMITTED" ||
                                  entry.status === "APPROVED" ||
                                  entry.status === "CANCELLED" ? 0.8 : 1
                            }}
                          >
                            <TableCell>
                              <Typography fontWeight="bold">
                                {entry.timesheetId}
                              </Typography>
                            </TableCell>
                            <TableCell>{entry.projectNames}</TableCell>
                            <TableCell>{entry.createdAtFormatted}</TableCell>
                            {entry.hours.map((hour, dayIdx) => (
                              <TableCell
                                key={dayIdx}
                                align="center"
                                sx={{
                                  backgroundColor: hour > 0 ? "#e8f5e9" : "inherit",
                                  color:
                                    daysOfWeek[dayIdx].isWeekend && hour > 0
                                      ? "#f44336"
                                      : "inherit",
                                }}
                              >
                                {hour > 0 ? hour : "-"}
                              </TableCell>
                            ))}
                            <TableCell align="center" sx={{ fontWeight: "bold" }}>
                              {entry.totalHours}
                            </TableCell>
                            <TableCell>
                              <Box
                                sx={{ display: "flex", alignItems: "center", gap: 1 }}
                              >
                                {getStatusIcon(entry.status)}
                                <Typography variant="body2">
                                  {entry.status}
                                </Typography>
                              </Box>
                            </TableCell>
                            <TableCell>
                              <IconButton
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleMenuOpen(e, entry);
                                }}
                              >
                                <MoreVert />
                              </IconButton>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                  </TableBody>
                </Table>
              </TableContainer>

              <TablePagination
                rowsPerPageOptions={[5, 10, 25]}
                component="div"
                count={filteredEntries.length}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={handleChangePage}
                onRowsPerPageChange={handleChangeRowsPerPage}
              />

              <Divider sx={{ my: 2 }} />

              <Box
                sx={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <Typography variant="body2" color="text.secondary">
                  Showing {filteredEntries.length} of {entries.length} entries
                  {statusFilter && ` (Filtered by ${statusFilter})`}
                </Typography>

                <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
                  {hasAction("SUBMIT_ALL_TIMESHEET") && (
                    <Button
                      variant="contained"
                      color="primary"
                      onClick={handleSubmitAll}
                      disabled={
                        loading || !entries.some((e) => e.status === "DRAFT")
                      }
                      startIcon={loading ? <CircularProgress size={20} /> : <Send />}
                    >
                      Submit All Drafts
                    </Button>
                  )}
                </Box>
              </Box>
            </Paper>
          </>
        )}
      </Paper>

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
        onClick={(e) => e.stopPropagation()}
      >
        {hasAction("VIEW_TIMESHEET") && (
          <MenuItem onClick={handleView}>
            <Visibility fontSize="small" sx={{ mr: 1 }} /> View
          </MenuItem>
        )}

        {selectedEntry?.status !== "SUBMITTED" && (
          <>
            {(selectedEntry?.status === "DRAFT" || selectedEntry?.status === "REJECTED") && (
              hasAction("UPDATE_TIMESHEET") && (
                <MenuItem onClick={handleEdit}>
                  <Edit fontSize="small" sx={{ mr: 1 }} /> Edit
                </MenuItem>
              )
            )}

            {selectedEntry?.status === "DRAFT" && hasAction("CANCEL_TIMESHEET") && (
              <MenuItem onClick={handleCancelEntry}>
                <Cancel fontSize="small" sx={{ mr: 1 }} /> Cancel
              </MenuItem>
            )}

            {(selectedEntry?.status === "DRAFT" || selectedEntry?.status === "REJECTED") && (
              hasAction("SUBMIT_TIMESHEET") && (
                <MenuItem
                  onClick={handleSubmitEntry}
                  disabled={loading}
                >
                  <Send fontSize="small" sx={{ mr: 1 }} />
                  {loading ? "Submitting..." : "Submit"}
                </MenuItem>
              )
            )}
          </>
        )}
      </Menu>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ width: "100%" }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </LocalizationProvider>
  );
};

export default TimesheetDetailPage;