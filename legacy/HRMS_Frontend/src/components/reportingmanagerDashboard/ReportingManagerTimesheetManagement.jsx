import React, { useState, useEffect, useContext } from "react";
import { useLocation } from 'react-router-dom';
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
    FormControl,
    InputLabel,
    Select,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    DialogActions,
    Snackbar,
    Alert,
    Badge,
    Avatar,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    CircularProgress,
    Tabs,
    Tab,
    Stack,
} from "@mui/material";
import {
    Search,
    MoreVert,
    Visibility,
    FileDownload,
    Email,
    Notifications as NotificationsIcon,
    PictureAsPdf,
    GridOn,
    FilterAlt,
    Close
} from "@mui/icons-material";
import * as XLSX from 'xlsx';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { useNavigate } from "react-router-dom";
import { parseISO, format, isWithinInterval } from 'date-fns';
import TablePagination from '@mui/material/TablePagination';
import axios from 'axios';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import API_BASE_URL from "../config/apiConfig";
import { userContext } from '../context/ContextProvider';

const ReportingManagerTimesheetManagement = () => {
      const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
    const navigate = useNavigate();
    const location = useLocation();
    const [timesheets, setTimesheets] = useState([]);
    const [filteredTimesheets, setFilteredTimesheets] = useState([]);
    const [searchTerm, setSearchTerm] = useState("");
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(5);
    const [selectedTimesheet, setSelectedTimesheet] = useState(null);
    const [anchorEl, setAnchorEl] = useState(null);
    const [snackbarOpen, setSnackbarOpen] = useState(false);
    const [snackbarMessage, setSnackbarMessage] = useState("");
    const [snackbarSeverity, setSnackbarSeverity] = useState("success");
    const [loading, setLoading] = useState(true);
    const [notificationAnchorEl, setNotificationAnchorEl] = useState(null);
    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [notificationLoading, setNotificationLoading] = useState(false);
    const [exportModalOpen, setExportModalOpen] = useState(false);
    const [filterModalOpen, setFilterModalOpen] = useState(false);
    const [exportFilters, setExportFilters] = useState({
        status: "All Statuses",
        startDate: null,
        endDate: new Date(),
        project: "All Projects"
    });
    const [appliedFilters, setAppliedFilters] = useState({
        status: "All Statuses",
        project: "All Projects",
        startDate: null,
        endDate: new Date()
    });
    const [activeFilterCount, setActiveFilterCount] = useState(0);
    const [exportType, setExportType] = useState('excel');
    const [projects, setProjects] = useState([]);
    const [statusFilter, setStatusFilter] = useState(null);

    const hasAction = (actionName) => actions.includes(actionName);

    const handleStatusFilter = (status) => {
        if (statusFilter === status) {
            setStatusFilter(null);
            handleFilterChange("status", "All Statuses");
        } else {
            setStatusFilter(status);
            handleFilterChange("status", status);
        }
        setPage(0);
    };

    const fetchProjects = async () => {
        try {
            const response = await axios.get(`${API_BASE_URL}/projects`);
            if (response.data && Array.isArray(response.data)) {
                const projectsMap = new Map();
                response.data.forEach(project => {
                    if (!projectsMap.has(project.projectId)) {
                        projectsMap.set(project.projectId, project);
                    }
                });
                setProjects(Array.from(projectsMap.values()));
            }
        } catch (error) {
            console.error("Error fetching projects:", error);
            showSnackbar("Failed to load projects", "error");
        }
    };

    const fetchTimesheets = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${API_BASE_URL}/api/timesheets/reporting-manager/timesheets`);

            if (!response.data || !Array.isArray(response.data)) {
                throw new Error("Invalid data format received from server");
            }

            const processedTimesheets = response.data.flatMap(timesheet => {
                return timesheet.projects.map(project => {
                    // Calculate total hours more accurately
                    const totalHours = project.tasks?.reduce((sum, task) => {
                        const taskHours = task.days?.reduce((taskSum, day) => {
                            return taskSum + (parseFloat(day.hours) || 0);
                        }, 0) || 0;
                        return sum + taskHours;
                    }, 0) || 0;

                    return {
                        id: timesheet.timesheetId,
                        employeeId: timesheet.employeeId,
                        employeeName: timesheet.employeeName,
                        employeeEmail: `${timesheet.employeeName?.toLowerCase()?.replace(/\s+/g, '')}@company.com`,
                        project: project.projectName,
                        projectId: project.projectId,
                        tasks: project.tasks?.map(task => task.taskName).join(", ") || "No tasks",
                        startDate: timesheet.weekStartDate,
                        endDate: timesheet.weekEndDate,
                        status: project.status === "SUBMITTED" ? "Pending" : project.status || "Unknown",
                        totalHours: totalHours.toFixed(2), // Format to 2 decimal places
                        submittedAt: timesheet.submitted_at,
                        comments: timesheet.comments || "",
                        rejectionReason: project.rejectionReason || "",
                        managerId: project.managerId || "N/A",
                        managerName: project.managerName || "No Manager"
                    };
                });
            });

            // Added line to sort the timesheets by submittedAt field. (newest first)
            processedTimesheets.sort((a, b) => new Date(b.submittedAt) - new Date(a.submittedAt));

            setTimesheets(processedTimesheets);
            setFilteredTimesheets(processedTimesheets);
            setLoading(false);
        } catch (error) {
            console.error("Error fetching timesheets:", error);
            showSnackbar("Failed to load timesheets", "error");
            setLoading(false);
            setTimesheets([]);
            setFilteredTimesheets([]);
        }
    };

    const fetchTimesheetNotifications = async () => {
        try {
            setNotificationLoading(true);
            const response = await axios.get(`${API_BASE_URL}/api/timesheet-notifications/manager/unread`);
            setNotifications(response.data);
            const countResponse = await axios.get(`${API_BASE_URL}/api/timesheet-notifications/manager/unread-count`);
            setUnreadCount(countResponse.data);
        } catch (error) {
            console.error("Error fetching timesheet notifications:", error);
            showSnackbar("Failed to load notifications", "error");
        } finally {
            setNotificationLoading(false);
        }
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

    const applyFilters = () => {
        let filtered = [...timesheets];

        if (searchTerm) {
            const lowercasedTerm = searchTerm.toLowerCase();
            filtered = filtered.filter(timesheet => {
                const searchFields = [
                    timesheet.id,
                    timesheet.employeeId,
                    timesheet.employeeName,
                    timesheet.project,
                    timesheet.tasks,
                    timesheet.status,
                    format(parseISO(timesheet.startDate), "MMM dd, yyyy"),
                    format(parseISO(timesheet.endDate), "MMM dd, yyyy")
                ];
                return searchFields.some(
                    field => field && field.toString().toLowerCase().includes(lowercasedTerm)
                );
            });
        }

        if (appliedFilters.status !== "All Statuses") {
            filtered = filtered.filter(ts => {
                if (appliedFilters.status === "Pending") {
                    return ts.status === "Pending" || ts.status === "SUBMITTED";
                }
                return ts.status.toUpperCase() === appliedFilters.status.toUpperCase();
            });
        }

        if (appliedFilters.project !== "All Projects") {
            filtered = filtered.filter(ts =>
                ts.project?.toLowerCase().trim() === appliedFilters.project.toLowerCase().trim()
            );
        }

        if (appliedFilters.startDate && appliedFilters.endDate) {
            filtered = filtered.filter(ts => {
                const tsDate = parseISO(ts.startDate);
                return isWithinInterval(tsDate, {
                    start: appliedFilters.startDate,
                    end: appliedFilters.endDate
                });
            });
        }

        setFilteredTimesheets(filtered);
        setPage(0);
        setActiveFilterCount(countActiveFilters(appliedFilters));
    };

    const countActiveFilters = (filters) => {
        let count = 0;
        if (filters.status !== "All Statuses") count++;
        if (filters.project !== "All Projects") count++;
        if (filters.startDate) count++;
        if (filters.endDate) count++;
        return count;
    };

    const resetFilters = () => {
        setAppliedFilters({
            status: "All Statuses",
            project: "All Projects",
            startDate: null,
            endDate: new Date()
        });
        setStatusFilter(null);
        setActiveFilterCount(0);
        setFilteredTimesheets(timesheets);
        setPage(0);
    };

    const handleSearchChange = (e) => {
        setSearchTerm(e.target.value);
    };

    const handleFilterChange = (name, value) => {
        setAppliedFilters(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleNotificationClick = (event) => {
        setNotificationAnchorEl(event.currentTarget);
    };

    const handleNotificationClose = () => {
        setNotificationAnchorEl(null);
    };

    const handleMenuOpen = (event, timesheet) => {
        setAnchorEl(event.currentTarget);
        setSelectedTimesheet(timesheet);
    };

    const handleMenuClose = () => {
        setAnchorEl(null);
    };

    const handleViewDetails = () => {
        if (!selectedTimesheet) return;

        navigate(`/${role}/reporting-timesheets/${selectedTimesheet.id}/projects/${selectedTimesheet.projectId}`, {
            state: {
                timesheetId: selectedTimesheet.id,
                projectId: selectedTimesheet.projectId,
                employeeName: selectedTimesheet.employeeName,
                projectName: selectedTimesheet.project,
                weekStartDate: selectedTimesheet.startDate,
                weekEndDate: selectedTimesheet.endDate
            }
        });
        handleMenuClose();
    };

    const showSnackbar = (message, severity) => {
        setSnackbarMessage(message);
        setSnackbarSeverity(severity);
        setSnackbarOpen(true);
    };

    const handleSnackbarClose = () => {
        setSnackbarOpen(false);
    };

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(parseInt(event.target.value, 10));
        setPage(0);
    };

    const getStatusChip = (status) => {
        switch (status) {
            case "Approved":
            case "APPROVED":
                return <Chip label="Approved" color="success" size="small" />;
            case "Rejected":
            case "REJECTED":
                return <Chip label="Rejected" color="error" size="small" />;
            case "Pending":
            case "SUBMITTED":
                return <Chip label="Pending" color="warning" size="small" />;
            case "Draft":
            case "DRAFT":
                return <Chip label="Draft" color="info" size="small" />;
            default:
                return <Chip label={status} size="small" />;
        }
    };

    const handleExportModalOpen = () => {
        setExportFilters({
            status: "All Statuses",
            startDate: null,
            endDate: new Date(),
            project: "All Projects"
        });
        setExportModalOpen(true);
    };

    const handleExportModalClose = () => {
        setExportModalOpen(false);
    };

    const handleExportFilterChange = (field, value) => {
        setExportFilters(prev => ({
            ...prev,
            [field]: value
        }));
    };

    const handleExportTypeChange = (event, newValue) => {
        setExportType(newValue);
    };

    const getFilteredDataForExport = () => {
        return timesheets.filter(ts => {
            if (exportFilters.status !== "All Statuses") {
                if (exportFilters.status === "Pending" && ts.status !== "Pending" && ts.status !== "SUBMITTED") {
                    return false;
                }
                if (exportFilters.status !== "Pending" && ts.status.toUpperCase() !== exportFilters.status.toUpperCase()) {
                    return false;
                }
            }

            if (exportFilters.project !== "All Projects" && ts.project !== exportFilters.project) {
                return false;
            }

            if (exportFilters.startDate && exportFilters.endDate) {
                const tsDate = parseISO(ts.startDate);
                if (!isWithinInterval(tsDate, {
                    start: exportFilters.startDate,
                    end: exportFilters.endDate
                })) {
                    return false;
                }
            }

            return true;
        });
    };

    const exportToExcel = (filteredData) => {
        const wb = XLSX.utils.book_new();

        const ws = XLSX.utils.aoa_to_sheet([
            ["Timesheet Report"],
            ["Generated on: " + format(new Date(), "yyyy-MM-dd HH:mm")],
            [""],
        ]);

        const exportData = filteredData.map(ts => ({
            "Timesheet ID": ts.id,
            "Employee ID": ts.employeeId,
            "Employee Name": ts.employeeName,
            "Project": ts.project,
            "Tasks": ts.tasks,
            "Week Start": format(parseISO(ts.startDate), "yyyy-MM-dd"),
            "Week End": format(parseISO(ts.endDate), "yyyy-MM-dd"),
            "Status": ts.status,
            "Total Hours": ts.totalHours,
            "Submitted At": ts.submittedAt ? format(parseISO(ts.submittedAt), "yyyy-MM-dd HH:mm") : "N/A",
            "Comments": ts.comments || "N/A",
            "Rejection Reason": ts.rejectionReason || "N/A"
        }));

        XLSX.utils.sheet_add_json(ws, exportData, { origin: "A5", skipHeader: true });
        const headers = Object.keys(exportData[0]);
        XLSX.utils.sheet_add_aoa(ws, [headers], { origin: "A4" });

        XLSX.utils.book_append_sheet(wb, ws, "Timesheets");

        let fileNameParts = [`Timesheets_${format(new Date(), "yyyy-MM-dd")}`];
        if (exportFilters.status !== "All Statuses") fileNameParts.push(exportFilters.status);
        if (exportFilters.project !== "All Projects") fileNameParts.push(exportFilters.project.replace(/\s+/g, '_'));
        if (exportFilters.startDate && exportFilters.endDate) {
            fileNameParts.push(
                `${format(exportFilters.startDate, "yyyy-MM-dd")}-${format(exportFilters.endDate, "yyyy-MM-dd")}`
            );
        }

        const fileName = fileNameParts.join('_');

        XLSX.writeFile(wb, `${fileName}.xlsx`);
    };

    const exportToPDF = (filteredData) => {
        const doc = new jsPDF();

        doc.setFontSize(16);
        doc.text('Timesheet Report', 70, 20);
        doc.setFontSize(12);

        let filtersText = `Generated on: ${format(new Date(), "yyyy-MM-dd HH:mm")}`;
        if (exportFilters.status !== "All Statuses") filtersText += ` | Status: ${exportFilters.status}`;
        if (exportFilters.project !== "All Projects") filtersText += ` | Project: ${exportFilters.project}`;
        if (exportFilters.startDate && exportFilters.endDate) {
            filtersText += ` | Date Range: ${format(exportFilters.startDate, "yyyy-MM-dd")} to ${format(exportFilters.endDate, "yyyy-MM-dd")}`;
        }

        const splitText = doc.splitTextToSize(filtersText, 180);
        doc.text(splitText, 15, 35);

        autoTable(doc, {
            head: [['ID', 'Employee', 'Project', 'Tasks', 'Week', 'Status', 'Hours', 'Submitted At']],
            body: filteredData.map(ts => [
                ts.id,
                ts.employeeName,
                ts.project,
                ts.tasks,
                `${format(parseISO(ts.startDate), "MMM dd")} - ${format(parseISO(ts.endDate), "MMM dd")}`,
                ts.status,
                ts.totalHours,
                ts.submittedAt ? format(parseISO(ts.submittedAt), "MMM dd, HH:mm") : "N/A"
            ]),
            startY: 45,
            styles: {
                fontSize: 8,
                cellPadding: 2,
                overflow: 'linebreak'
            },
            headStyles: {
                fillColor: [41, 128, 185],
                textColor: 255,
                fontStyle: 'bold'
            },
            columnStyles: {
                0: { cellWidth: 15 },
                1: { cellWidth: 25 },
                2: { cellWidth: 25 },
                3: { cellWidth: 25 },
                4: { cellWidth: 25 },
                5: { cellWidth: 15 },
                6: { cellWidth: 10 },
                7: { cellWidth: 25 }
            }
        });

        const pageCount = doc.internal.getNumberOfPages();
        for (let i = 1; i <= pageCount; i++) {
            doc.setPage(i);
            doc.setFontSize(8);
            doc.setTextColor(150);
            doc.text(`Page ${i} of ${pageCount}`, 105, doc.internal.pageSize.height - 10);
        }

        let fileNameParts = [`Timesheets_${format(new Date(), "yyyy-MM-dd")}`];
        if (exportFilters.status !== "All Statuses") fileNameParts.push(exportFilters.status);
        if (exportFilters.project !== "All Projects") fileNameParts.push(exportFilters.project.replace(/\s+/g, '_'));
        if (exportFilters.startDate && exportFilters.endDate) {
            fileNameParts.push(`${format(exportFilters.startDate, "yyyy-MM-dd")}-${format(exportFilters.endDate, "yyyy-MM-dd")}`);
        }
        const fileName = fileNameParts.join('_');

        doc.save(`${fileName}.pdf`);
    };

    const handleExport = () => {
        try {
            const filteredData = getFilteredDataForExport();

            if (filteredData.length === 0) {
                showSnackbar("No data to export with current filters", "warning");
                return;
            }

            if (exportType === 'excel') {
                exportToExcel(filteredData);
            } else {
                exportToPDF(filteredData);
            }

            showSnackbar(`Exported ${filteredData.length} timesheets as ${exportType.toUpperCase()}`, "success");
            handleExportModalClose();
        } catch (error) {
            console.error(`Error exporting to ${exportType}:`, error);
            showSnackbar(`Failed to export as ${exportType.toUpperCase()}`, "error");
        }
    };

    useEffect(() => {
    const fetchData = async () => {
        await fetchTimesheets();
        await fetchTimesheetNotifications();
    };

    fetchData();

    // Apply initial status filter ONLY if passed via location.state and not already set
    if (location.state?.statusFilter && !statusFilter) {
        setStatusFilter(location.state.statusFilter);
        setAppliedFilters(prev => ({
            ...prev,
            status: location.state.statusFilter
        }));
    }

    const interval = setInterval(() => {
        fetchTimesheetNotifications();
    }, 30000);

    return () => clearInterval(interval);
}, []);

    useEffect(() => {
        const uniqueProjects = Array.from(new Set(timesheets.map(ts => ts.project)))
            .filter(Boolean)
            .map(name => ({ projectName: name }));
        setProjects(uniqueProjects);
    }, [timesheets]);

    useEffect(() => {
        applyFilters();
    }, [appliedFilters, timesheets, searchTerm]);

    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <CircularProgress />
            </Box>
        );
    }

    function stringToColor(string) {
        let hash = 0;
        let i;

        for (i = 0; i < string.length; i += 1) {
            hash = string.charCodeAt(i) + ((hash << 5) - hash);
        }

        let color = '#';

        for (i = 0; i < 3; i += 1) {
            const value = (hash >> (i * 8)) & 0xff;
            color += `00${value.toString(16)}`.slice(-2);
        }

        return color;
    }

    function getInitials(name) {
        if (!name) return '';
        const names = name.split(' ');
        let initials = names[0].substring(0, 1).toUpperCase();

        if (names.length > 1) {
            initials += names[names.length - 1].substring(0, 1).toUpperCase();
        }

        return initials;
    }

    return (
        <LocalizationProvider dateAdapter={AdapterDateFns}>
            <Paper elevation={0} sx={{
                p: 3,
                width: "100%",
                maxWidth: 1600,
                mx: "auto",
                borderRadius: 3,
                boxShadow: '0px 2px 10px rgba(0, 0, 0, 0.08)'
            }}>
                {/* Header Section */}
                <Grid container justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                    <Grid item>
                        <Typography variant="h5" sx={{ fontWeight: "bold", color: '#2c3e50' }}>
                            Reporting Timesheet Management
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            Review timesheets for your reporting employees
                        </Typography>
                    </Grid>
                    <Grid item>
                        <Stack direction="row" spacing={2} alignItems="center">
                            {/* Status Filter Chips */}
                            <Stack direction="row" spacing={1}>
                                <Chip
                                    label={`${timesheets.filter(e => e.status === "Pending" || e.status === "SUBMITTED").length} Pending`}
                                    color="warning"
                                    size="small"
                                    variant={statusFilter === "Pending" ? "filled" : "outlined"}
                                    onClick={() => handleStatusFilter("Pending")}
                                    clickable
                                    sx={{
                                        fontWeight: 600,
                                        borderWidth: 2,
                                        '&:hover': { borderWidth: 2 }
                                    }}
                                />
                                <Chip
                                    label={`${timesheets.filter(e => e.status === "APPROVED").length} Approved`}
                                    color="success"
                                    size="small"
                                    variant={statusFilter === "APPROVED" ? "filled" : "outlined"}
                                    onClick={() => handleStatusFilter("APPROVED")}
                                    clickable
                                    sx={{
                                        fontWeight: 600,
                                        borderWidth: 2,
                                        '&:hover': { borderWidth: 2 }
                                    }}
                                />
                                <Chip
                                    label={`${timesheets.filter(e => e.status === "REJECTED").length} Rejected`}
                                    color="error"
                                    size="small"
                                    variant={statusFilter === "REJECTED" ? "filled" : "outlined"}
                                    onClick={() => handleStatusFilter("REJECTED")}
                                    clickable
                                    sx={{
                                        fontWeight: 600,
                                        borderWidth: 2,
                                        '&:hover': { borderWidth: 2 }
                                    }}
                                />
                            </Stack>

                            {hasAction("EXPORT_TIMESHEET_DATA") && (
                                <Button
                                    variant="contained"
                                    startIcon={<FileDownload />}
                                    onClick={handleExportModalOpen}
                                    sx={{
                                        backgroundColor: '#3498db',
                                        '&:hover': {
                                            backgroundColor: '#2980b9',
                                        }
                                    }}
                                >
                                    Export
                                </Button>
                            )}

                            {/* <IconButton
                                onClick={handleNotificationClick}
                                sx={{
                                    backgroundColor: '#f5f5f5',
                                    '&:hover': {
                                        backgroundColor: '#e0e0e0',
                                    }
                                }}
                            >
                                <Badge
                                    badgeContent={unreadCount}
                                    color="error"
                                    overlap="circular"
                                    anchorOrigin={{
                                        vertical: 'top',
                                        horizontal: 'right',
                                    }}
                                    sx={{
                                        '& .MuiBadge-badge': {
                                            right: 5,
                                            top: 5,
                                            padding: '0 4px',
                                            height: 16,
                                            minWidth: 16,
                                        }
                                    }}
                                >
                                    <NotificationsIcon sx={{ color: '#555' }} />
                                </Badge>
                            </IconButton> */}

                            {hasAction("NOTIFICATION_SETTINGS") && (
                                <Button
                                    variant="outlined"
                                    startIcon={<Email />}
                                    onClick={() => navigate(`/${role}/timesheets/notifications`)}
                                    sx={{
                                        borderWidth: 2,
                                        '&:hover': {
                                            borderWidth: 2,
                                        }
                                    }}
                                >
                                    Notification Settings
                                </Button>
                            )}
                        </Stack>
                    </Grid>
                </Grid>

                {/* Search and Filters */}
                <Box sx={{
                    display: "flex",
                    gap: 2,
                    mb: 3,
                    flexWrap: "wrap",
                    backgroundColor: '#f8f9fa',
                    p: 2,
                    borderRadius: 2
                }}>
                    <TextField
                        size="small"
                        placeholder="Search timesheets..."
                        value={searchTerm}
                        onChange={handleSearchChange}
                        sx={{
                            minWidth: 300,
                            backgroundColor: 'white',
                            borderRadius: 1,
                            '& .MuiOutlinedInput-root': {
                                borderRadius: 1,
                            }
                        }}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <Search color="action" />
                                </InputAdornment>
                            ),
                            endAdornment: searchTerm && (
                                <InputAdornment position="end">
                                    <IconButton
                                        size="small"
                                        onClick={() => setSearchTerm("")}
                                        edge="end"
                                    >
                                        <Close fontSize="small" color="action" />
                                    </IconButton>
                                </InputAdornment>
                            )
                        }}
                    />

                    <Button
                        variant="outlined"
                        startIcon={<FilterAlt />}
                        onClick={() => setFilterModalOpen(true)}
                        sx={{
                            position: 'relative',
                            borderWidth: 2,
                            '&:hover': {
                                borderWidth: 2,
                            }
                        }}
                    >
                        Filters
                        {activeFilterCount > 0 && (
                            <Box
                                sx={{
                                    position: 'absolute',
                                    top: -8,
                                    right: -8,
                                    backgroundColor: 'primary.main',
                                    color: 'white',
                                    borderRadius: '50%',
                                    width: 20,
                                    height: 20,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: '0.75rem'
                                }}
                            >
                                {activeFilterCount}
                            </Box>
                        )}
                    </Button>
                </Box>

                {/* Timesheets Table */}
                <TableContainer
                    component={Paper}
                    elevation={2}
                    sx={{
                        borderRadius: 2,
                        border: '1px solid #e0e0e0',
                        overflow: 'hidden'
                    }}
                >
                    <Table sx={{ minWidth: 1200 }}>
                        <TableHead sx={{ backgroundColor: '#f5f5f5' }}>
                            <TableRow>
                                <TableCell sx={{ fontWeight: 'bold' }}>Timesheet ID</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Employee</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Project</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Tasks</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Week</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Total Hours</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Status</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Submitted At</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Actions</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {filteredTimesheets.length > 0 ? (
                                filteredTimesheets
                                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                                    .map((timesheet) => (
                                        <TableRow
                                            key={`${timesheet.id}-${timesheet.projectId}`}
                                            hover
                                            sx={{ '&:last-child td, &:last-child th': { border: 0 } }}
                                        >
                                            <TableCell>{timesheet.id}</TableCell>
                                            <TableCell>
                                                <Stack direction="row" alignItems="center" spacing={1}>
                                                    <Avatar
                                                        sx={{
                                                            width: 32,
                                                            height: 32,
                                                            backgroundColor: stringToColor(timesheet.employeeName),
                                                            fontSize: '0.875rem',
                                                        }}
                                                    >
                                                        {getInitials(timesheet.employeeName)}
                                                    </Avatar>
                                                    <Box>
                                                        <Typography fontWeight="bold">{timesheet.employeeName}</Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            {timesheet.employeeId}
                                                        </Typography>
                                                    </Box>
                                                </Stack>
                                            </TableCell>
                                            <TableCell>{timesheet.project}</TableCell>
                                            <TableCell>
                                                <Box sx={{ maxWidth: 200, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                                    {timesheet.tasks}
                                                </Box>
                                            </TableCell>
                                            <TableCell>
                                                {format(parseISO(timesheet.startDate), "MMM dd")} -{" "}
                                                {format(parseISO(timesheet.endDate), "MMM dd, yyyy")}
                                            </TableCell>
                                            <TableCell align="right">{timesheet.totalHours}</TableCell>
                                            <TableCell>{getStatusChip(timesheet.status)}</TableCell>
                                            <TableCell>
                                                {timesheet.submittedAt
                                                    ? format(parseISO(timesheet.submittedAt), "MMM dd, h:mm a")
                                                    : "N/A"}
                                            </TableCell>
                                            <TableCell>
                                                <IconButton
                                                    onClick={(e) => handleMenuOpen(e, timesheet)}
                                                    size="small"
                                                >
                                                    <MoreVert />
                                                </IconButton>
                                            </TableCell>
                                        </TableRow>
                                    ))
                            ) : (
                                <TableRow>
                                    <TableCell colSpan={9} align="center" sx={{ py: 4 }}>
                                        <Typography color="text.secondary">
                                            No timesheets found
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>

                <TablePagination
                    rowsPerPageOptions={[5, 10, 25]}
                    component="div"
                    count={filteredTimesheets.length}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onPageChange={handleChangePage}
                    onRowsPerPageChange={handleChangeRowsPerPage}
                    sx={{
                        borderTop: 1,
                        borderColor: 'divider',
                        '& .MuiTablePagination-toolbar': {
                            paddingLeft: 2,
                        }
                    }}
                />

                {/* Filter Dialog */}
                <Dialog open={filterModalOpen} onClose={() => setFilterModalOpen(false)} maxWidth="sm" fullWidth>
                    <DialogTitle sx={{ backgroundColor: '#f8f9fa', borderBottom: 1, borderColor: 'divider' }}>
                        <Box display="flex" justifyContent="space-between" alignItems="center">
                            <Typography variant="h6">Filter Timesheets</Typography>
                            <IconButton onClick={() => setFilterModalOpen(false)}>
                                <Close />
                            </IconButton>
                        </Box>
                    </DialogTitle>
                    <DialogContent>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 2 }}>
                            <FormControl fullWidth size="small">
                                <InputLabel>Project</InputLabel>
                                <Select
                                    value={appliedFilters.project}
                                    label="Project"
                                    onChange={(e) => handleFilterChange("project", e.target.value)}
                                >
                                    <MenuItem value="All Projects">All Projects</MenuItem>
                                    {projects.map((project) => (
                                        <MenuItem key={project.projectName} value={project.projectName}>
                                            {project.projectName}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>

                            <FormControl fullWidth size="small">
                                <InputLabel>Status</InputLabel>
                                <Select
                                    value={appliedFilters.status}
                                    label="Status"
                                    onChange={(e) => handleFilterChange("status", e.target.value)}
                                >
                                    <MenuItem value="All Statuses">All Statuses</MenuItem>
                                    <MenuItem value="Pending">Pending</MenuItem>
                                    <MenuItem value="Approved">Approved</MenuItem>
                                    <MenuItem value="Rejected">Rejected</MenuItem>
                                </Select>
                            </FormControl>

                            <DatePicker
                                label="Start Date"
                                value={appliedFilters.startDate}
                                onChange={(newValue) => handleFilterChange("startDate", newValue)}
                                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                                maxDate={appliedFilters.endDate || new Date()}
                            />

                            <DatePicker
                                label="End Date"
                                value={appliedFilters.endDate}
                                onChange={(newValue) => handleFilterChange("endDate", newValue)}
                                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                                minDate={appliedFilters.startDate}
                                maxDate={new Date()}
                            />
                        </Box>
                    </DialogContent>
                    <DialogActions sx={{ p: 2, backgroundColor: '#f8f9fa', borderTop: 1, borderColor: 'divider' }}>
                        <Button onClick={resetFilters} color="error" variant="outlined">
                            Reset Filters
                        </Button>
                        <Button
                            onClick={() => setFilterModalOpen(false)}
                            variant="contained"
                            color="primary"
                        >
                            Apply Filters
                        </Button>
                    </DialogActions>
                </Dialog>

                {/* Export Dialog */}
                <Dialog open={exportModalOpen} onClose={handleExportModalClose} maxWidth="sm" fullWidth>
                    <DialogTitle sx={{ backgroundColor: '#f8f9fa', borderBottom: 1, borderColor: 'divider' }}>
                        Export Timesheets
                    </DialogTitle>
                    <DialogContent>
                        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
                            <Tabs
                                value={exportType}
                                onChange={handleExportTypeChange}
                                aria-label="export type tabs"
                            >
                                <Tab
                                    icon={<GridOn />}
                                    iconPosition="start"
                                    label="Excel"
                                    value="excel"
                                    sx={{ minWidth: 'auto' }}
                                />
                                <Tab
                                    icon={<PictureAsPdf />}
                                    iconPosition="start"
                                    label="PDF"
                                    value="pdf"
                                    sx={{ minWidth: 'auto' }}
                                />
                            </Tabs>
                        </Box>

                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            <FormControl fullWidth size="small">
                                <InputLabel>Project</InputLabel>
                                <Select
                                    value={exportFilters.project}
                                    label="Project"
                                    onChange={(e) => handleExportFilterChange("project", e.target.value)}
                                >
                                    <MenuItem value="All Projects">All Projects</MenuItem>
                                    {projects.map((project) => (
                                        <MenuItem key={project.projectId} value={project.projectName}>
                                            {project.projectName}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>

                            <FormControl fullWidth size="small">
                                <InputLabel>Status</InputLabel>
                                <Select
                                    value={exportFilters.status}
                                    label="Status"
                                    onChange={(e) => handleExportFilterChange("status", e.target.value)}
                                >
                                    <MenuItem value="All Statuses">All Statuses</MenuItem>
                                    <MenuItem value="Pending">Pending</MenuItem>
                                    <MenuItem value="Approved">Approved</MenuItem>
                                    <MenuItem value="Rejected">Rejected</MenuItem>
                                </Select>
                            </FormControl>

                            <DatePicker
                                label="Start Date"
                                value={exportFilters.startDate}
                                onChange={(newValue) => handleExportFilterChange("startDate", newValue)}
                                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                                maxDate={exportFilters.endDate || new Date()}
                            />

                            <DatePicker
                                label="End Date"
                                value={exportFilters.endDate}
                                onChange={(newValue) => handleExportFilterChange("endDate", newValue)}
                                renderInput={(params) => <TextField {...params} fullWidth size="small" />}
                                minDate={exportFilters.startDate}
                                maxDate={new Date()}
                            />
                        </Box>
                    </DialogContent>
                    <DialogActions sx={{ backgroundColor: '#f8f9fa', borderTop: 1, borderColor: 'divider' }}>
                        <Button onClick={handleExportModalClose}>Cancel</Button>
                        <Button
                            onClick={handleExport}
                            variant="contained"
                            color="primary"
                            disabled={exportFilters.startDate && exportFilters.endDate && exportFilters.startDate > exportFilters.endDate}
                            startIcon={exportType === 'excel' ? <GridOn /> : <PictureAsPdf />}
                        >
                            Export as {exportType.toUpperCase()}
                        </Button>
                    </DialogActions>
                </Dialog>

                {/* Notification Menu */}
                <Menu
                    anchorEl={notificationAnchorEl}
                    open={Boolean(notificationAnchorEl)}
                    onClose={handleNotificationClose}
                    anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                    }}
                    transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                    }}
                    PaperProps={{
                        style: {
                            maxHeight: '400px',
                            width: '400px',
                            borderRadius: 8,
                            boxShadow: '0px 5px 15px rgba(0, 0, 0, 0.1)'
                        },
                    }}
                >
                    <MenuItem
                        onClick={() => {
                            markAllNotificationsAsRead();
                            handleNotificationClose();
                        }}
                        disabled={notifications.length === 0}
                        sx={{ fontWeight: 600 }}
                    >
                        Mark all as read
                    </MenuItem>
                    <Divider />
                    {notificationLoading ? (
                        <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
                            <CircularProgress size={24} />
                        </Box>
                    ) : notifications.length > 0 ? (
                        <List sx={{ width: '100%', maxWidth: 360 }}>
                            {notifications.map((notification) => (
                                <ListItem
                                    key={notification.id}
                                    alignItems="flex-start"
                                    onClick={() => {
                                        markNotificationAsRead(notification.id);
                                        if (notification.timesheetId) {
                                            navigate(`/${role}/reporting-timesheets/${notification.timesheetId}`, {
                                                state: {
                                                    timesheetId: notification.timesheetId,
                                                    projectId: notification.projectId
                                                }
                                            });
                                        }
                                        handleNotificationClose();
                                    }}
                                    sx={{
                                        cursor: 'pointer',
                                        '&:hover': {
                                            backgroundColor: '#f5f5f5'
                                        }
                                    }}
                                >
                                    <ListItemAvatar>
                                        <Avatar sx={{ bgcolor: stringToColor(notification.message) }}>
                                            {getInitials(notification.message)}
                                        </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                        primary={notification.message}
                                        secondary={format(new Date(notification.createdAt), "MMM dd, h:mm a")}
                                        primaryTypographyProps={{ fontWeight: 500 }}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    ) : (
                        <MenuItem disabled>
                            No new notifications
                        </MenuItem>
                    )}
                </Menu>

                {/* Action Menu */}
                <Menu
                    anchorEl={anchorEl}
                    open={Boolean(anchorEl)}
                    onClose={handleMenuClose}
                    anchorOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                    }}
                    transformOrigin={{
                        vertical: 'top',
                        horizontal: 'right',
                    }}
                    PaperProps={{
                        elevation: 1,
                        sx: {
                            borderRadius: 2,
                            minWidth: 180,
                        }
                    }}
                >

                    {hasAction("VIEW_TIMESHEET_REPORT") && (
                        <MenuItem onClick={handleViewDetails} sx={{ py: 1 }}>
                            <Visibility fontSize="small" sx={{ mr: 1 }} /> View Details
                        </MenuItem>
                    )}
                </Menu>

                {/* Snackbar for notifications */}
                <Snackbar
                    open={snackbarOpen}
                    autoHideDuration={6000}
                    onClose={handleSnackbarClose}
                    anchorOrigin={{ vertical: "top", horizontal: "right" }}
                >
                    <Alert
                        onClose={handleSnackbarClose}
                        severity={snackbarSeverity}
                        sx={{ width: "100%" }}
                    >
                        {snackbarMessage}
                    </Alert>
                </Snackbar>
            </Paper>
        </LocalizationProvider>
    );
};

export default ReportingManagerTimesheetManagement;