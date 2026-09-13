import React, { useState, useEffect, useContext } from "react";
import {
  Paper,
  Typography,
  Badge,
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
  CircularProgress,
  Tabs,
  Tab,
  Stack,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
} from "@mui/material";
import {
  Search,
  MoreVert,
  Cancel,
  Visibility,
  FileDownload,
  PictureAsPdf,
  GridOn,
  FilterAlt,
  Email,
  Notifications as NotificationsIcon,
  Close,
} from "@mui/icons-material";
import * as XLSX from "xlsx";
import { jsPDF } from "jspdf";
import "jspdf-autotable";
import { useNavigate } from "react-router-dom";
import { parseISO, format } from "date-fns";
import TablePagination from "@mui/material/TablePagination";
import axios from "axios";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import autoTable from "jspdf-autotable";
import logo from "../images/logo.png";
import API_BASE_URL from "../config/apiConfig";
import { userContext } from "../context/ContextProvider";

const AdminTimesheetManagement = () => {
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
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
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [notificationAnchorEl, setNotificationAnchorEl] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationLoading, setNotificationLoading] = useState(false);

  const [exportFilters, setExportFilters] = useState({
    status: "All Statuses",
    startDate: null,
    endDate: new Date(),
    project: "All Projects",
    manager: "All Managers",
  });
  const [appliedFilters, setAppliedFilters] = useState({
    status: "All Statuses",
    project: "All Projects",
    startDate: null,
    endDate: new Date(),
    manager: "All Managers",
  });
  const [exportType, setExportType] = useState("excel");
  const [projects, setProjects] = useState([]);
  const [projectManagers, setProjectManagers] = useState({});
  const [managers, setManagers] = useState([]);
  const [managerProjects, setManagerProjects] = useState([]);
  const [statusFilter, setStatusFilter] = useState(null);

  const hasAction = (actionName) => actions.includes(actionName);

  const handleStatusFilter = (status) => {
    const newStatusFilter = statusFilter === status ? null : status;
    setStatusFilter(newStatusFilter);
    
    setAppliedFilters(prev => ({
      ...prev,
      status: newStatusFilter ? newStatusFilter : "All Statuses"
    }));
    
    setPage(0);
  };

  const fetchManagers = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/managers`);
      setManagers(response.data || []);
    } catch (error) {
      console.error("Error fetching managers:", error);
      showSnackbar("Failed to load managers", "error");
    }
  };

  const fetchProjectsForManager = async (managerId) => {
    try {
      if (!managerId || managerId === "All Managers") {
        setManagerProjects([]);
        return;
      }

      const response = await axios.get(
        `${API_BASE_URL}/projects?managerId=${managerId}`
      );
      const projects = response.data
        .map((project) => ({
          id: project.id,
          name: project.name || project.projectName,
          managerId: project.managerId,
        }))
        .filter((project) => project.managerId === managerId);

      setManagerProjects(projects);
    } catch (error) {
      console.error("Error fetching projects for manager:", error);
      showSnackbar("Failed to load manager's projects", "error");
      setManagerProjects([]);
    }
  };

  const fetchProjects = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/projects`);
      if (response.data && Array.isArray(response.data)) {
        setProjects(response.data);

        const managersMap = {};
        response.data.forEach((project) => {
          managersMap[project.id] = {
            managerId: project.managerId || "N/A",
            managerName: project.managerName || "No Manager",
          };
        });
        setProjectManagers(managersMap);
      }
    } catch (error) {
      console.error("Error fetching projects:", error);
      showSnackbar("Failed to load projects", "error");
    }
  };

  const fetchTimesheets = async () => {
    try {
      const response = await axios.get(
        `${API_BASE_URL}/api/timesheets/non-drafts`
      );

      if (!response.data || !Array.isArray(response.data)) {
        throw new Error("Invalid data format received from server");
      }

      if (projects.length === 0) {
        await fetchProjects();
      }

      const processedTimesheets = [];
      const projectManagerMap = {};
      for (const project of projects) {
        projectManagerMap[project.id] = {
          managerId: project.managerId || "N/A",
          managerName: project.managerName || "No Manager",
        };
      }

      for (const ts of response.data) {
        if (ts.projects && ts.projects.length > 0) {
          for (const project of ts.projects) {
            let projectHours = 0;
            if (project.tasks && project.tasks.length > 0) {
              for (const task of project.tasks) {
                if (task.days && task.days.length > 0) {
                  for (const day of task.days) {
                    projectHours += day.hours || 0;
                  }
                }
              }
            }

            let managerInfo = projectManagerMap[project.projectId];
            if (!managerInfo) {
              try {
                const projectResponse = await axios.get(
                  `${API_BASE_URL}/projects/${project.projectId}`
                );
                managerInfo = {
                  managerId: projectResponse.data.managerId || "N/A",
                  managerName: projectResponse.data.managerName || "No Manager",
                };
                projectManagerMap[project.projectId] = managerInfo;
              } catch (error) {
                console.error(
                  `Error fetching project ${project.projectId}:`,
                  error
                );
                managerInfo = {
                  managerId: "N/A",
                  managerName: "No Manager",
                };
              }
            }

            processedTimesheets.push({
              id: ts.timesheetId || "N/A",
              employeeId: ts.employeeId || "N/A",
              employeeName: ts.employeeName || "Unknown Employee",
              employeeEmail:
                `${ts.employeeName
                  ?.toLowerCase()
                  ?.replace(/\s+/g, "")}@company.com` || "unknown@company.com",
              project: project.projectName || "N/A",
              projectId: project.projectId || null,
              task:
                project.tasks && project.tasks.length > 0
                  ? project.tasks.map((t) => t.taskName).join(", ")
                  : "N/A",
              startDate: ts.weekStartDate || new Date().toISOString(),
              endDate: ts.weekEndDate || new Date().toISOString(),
              status: project.status || ts.status || "Unknown",
              totalHours: projectHours,
              submittedAt: ts.submitted_at || null,
              comments: ts.comments || "",
              rejectionReason:
                project.status === "REJECTED"
                  ? project.rejectionReason || "Rejected by admin"
                  : "",
              managerId: managerInfo.managerId,
              managerName: managerInfo.managerName,
              projectData: project,
              timesheetData: ts,
            });
          }
        } else {
          processedTimesheets.push({
            id: ts.timesheetId || "N/A",
            employeeId: ts.employeeId || "N/A",
            employeeName: ts.employeeName || "Unknown Employee",
            employeeEmail:
              `${ts.employeeName
                ?.toLowerCase()
                ?.replace(/\s+/g, "")}@company.com` || "unknown@company.com",
            project: "N/A",
            projectId: null,
            task: "N/A",
            startDate: ts.weekStartDate || new Date().toISOString(),
            endDate: ts.weekEndDate || new Date().toISOString(),
            status: ts.status || "Unknown",
            totalHours: 0,
            submittedAt: ts.submitted_at || null,
            comments: ts.comments || "",
            rejectionReason:
              ts.status === "REJECTED"
                ? ts.rejectionReason || "Rejected by admin"
                : "",
            managerId: "N/A",
            managerName: "No Manager",
            timesheetData: ts,
          });
        }
      }

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
   
  };

  const applySearch = (term, data) => {
    if (!term) return data;

    const lowercasedTerm = term.toLowerCase();

    return data.filter((timesheet) => {
      const searchFields = [
        timesheet.id,
        timesheet.employeeId,
        timesheet.employeeName,
        timesheet.managerId,
        timesheet.managerName,
        timesheet.project,
        timesheet.task,
        timesheet.status,
        timesheet.comments,
        timesheet.rejectionReason,
        format(parseISO(timesheet.startDate), "MMM dd, yyyy"),
        format(parseISO(timesheet.endDate), "MMM dd, yyyy"),
        timesheet.submittedAt
          ? format(parseISO(timesheet.submittedAt), "MMM dd, yyyy h:mm a")
          : "",
        timesheet.totalHours.toString(),
      ];

      return searchFields.some(
        (field) =>
          field && field.toString().toLowerCase().includes(lowercasedTerm)
      );
    });
  };

  const applyFilters = () => {
    let filtered = timesheets;

    // Apply search term filter
    filtered = applySearch(searchTerm, filtered);

    // Apply status filter (from chips or dropdown)
    const activeStatusFilter = statusFilter || appliedFilters.status;
    if (activeStatusFilter && activeStatusFilter !== "All Statuses") {
      filtered = filtered.filter((ts) => {
        if (activeStatusFilter === "SUBMITTED") {
          return ts.status === "Pending" || ts.status === "SUBMITTED";
        }
        return ts.status.toUpperCase() === activeStatusFilter.toUpperCase();
      });
    }

    // Apply manager filter
    if (appliedFilters.manager && appliedFilters.manager !== "All Managers") {
      filtered = filtered.filter(
        (ts) => ts.managerId === appliedFilters.manager
      );

      // Apply project filter only if a specific manager is selected
      if (appliedFilters.project && appliedFilters.project !== "All Projects") {
        filtered = filtered.filter(
          (ts) => ts.project === appliedFilters.project
        );
      }
    }

    // Apply date range filter
    if (appliedFilters.startDate && appliedFilters.endDate) {
      const startDate = new Date(appliedFilters.startDate);
      const endDate = new Date(appliedFilters.endDate);

      filtered = filtered.filter((ts) => {
        const tsDate = new Date(ts.startDate);
        return tsDate >= startDate && tsDate <= endDate;
      });
    }

    setFilteredTimesheets(filtered);
    setPage(0);
  };

  const handleNotificationClick = (event) => {
    setNotificationAnchorEl(event.currentTarget);
  };

  const handleNotificationClose = () => {
    setNotificationAnchorEl(null);
  };

  const handleSearchChange = (e) => {
    const term = e.target.value;
    setSearchTerm(term);

    if (term === "") {
      applyFilters();
    } else {
      const searched = applySearch(term, timesheets);
      setFilteredTimesheets(searched);
      setPage(0);
    }
  };

  const resetSearch = () => {
    setSearchTerm("");
    applyFilters();
  };

  const handleMenuOpen = (event, timesheet) => {
    setAnchorEl(event.currentTarget);
    setSelectedTimesheet(timesheet);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const showSnackbar = (message, severity) => {
    setSnackbarMessage(message);
    setSnackbarSeverity(severity);
    setSnackbarOpen(true);
  };

  const handleSnackbarClose = () => {
    setSnackbarOpen(false);
  };

  const handleExportModalOpen = () => {
    setExportFilters((prev) => ({
      ...prev,
      endDate: new Date(),
    }));
    setExportModalOpen(true);
  };

  const handleExportModalClose = () => {
    setExportModalOpen(false);
    setExportFilters({
      status: "All Statuses",
      startDate: null,
      endDate: new Date(),
      project: "All Projects",
      manager: "All Managers",
    });
  };

  const handleFilterModalOpen = () => {
    setFilterModalOpen(true);
  };

  const handleFilterModalClose = () => {
    setFilterModalOpen(false);
  };

  const handleExportFilterChange = async (field, value) => {
    if (field === "manager") {
      setExportFilters((prev) => ({
        ...prev,
        [field]: value,
        project: "All Projects",
      }));

      await fetchProjectsForManager(value);
    } else {
      setExportFilters((prev) => ({
        ...prev,
        [field]: value,
      }));
    }
  };

  const handleExportTypeChange = (event, newValue) => {
    setExportType(newValue);
  };

  const getFilteredData = () => {
    return timesheets.filter((ts) => {
      if (exportFilters.status && exportFilters.status !== "All Statuses") {
        if (exportFilters.status === "SUBMITTED") {
          // Include both "Pending" and "SUBMITTED" statuses
          if (ts.status !== "Pending" && ts.status !== "SUBMITTED") {
            return false;
          }
        } else if (!ts.status.toUpperCase().includes(exportFilters.status.toUpperCase())) {
          return false;
        }
      }

      if (exportFilters.startDate && exportFilters.endDate) {
        const startDate = new Date(exportFilters.startDate);
        const endDate = new Date(exportFilters.endDate);
        const tsDate = new Date(ts.startDate);

        if (tsDate < startDate || tsDate > endDate) {
          return false;
        }
      }

      if (
        exportFilters.project &&
        exportFilters.project !== "All Projects" &&
        ts.project !== exportFilters.project
      ) {
        return false;
      }

      if (
        exportFilters.manager &&
        exportFilters.manager !== "All Managers" &&
        ts.managerId !== exportFilters.manager
      ) {
        return false;
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

    const exportData = filteredData.map((ts) => ({
      "Timesheet ID": ts.id,
      "Employee ID": ts.employeeId,
      "Employee Name": ts.employeeName,
      "Manager ID": ts.managerId,
      "Manager Name": ts.managerName,
      Project: ts.project,
      Task: ts.task,
      "Week Start": format(parseISO(ts.startDate), "yyyy-MM-dd"),
      "Week End": format(parseISO(ts.endDate), "yyyy-MM-dd"),
      Status: ts.status,
      "Total Hours": ts.totalHours,
      "Submitted At": ts.submittedAt
        ? format(parseISO(ts.submittedAt), "yyyy-MM-dd HH:mm")
        : "N/A",
      Comments: ts.comments || "N/A",
      "Rejection Reason": ts.rejectionReason || "N/A",
    }));

    XLSX.utils.sheet_add_json(ws, exportData, {
      origin: "A5",
      skipHeader: true,
    });
    const headers = Object.keys(exportData[0]);
    XLSX.utils.sheet_add_aoa(ws, [headers], { origin: "A4" });

    XLSX.utils.book_append_sheet(wb, ws, "Timesheets");

    let fileNameParts = [`Timesheets_${format(new Date(), "yyyy-MM-dd")}`];
    if (exportFilters.status && exportFilters.status !== "All Statuses")
      fileNameParts.push(exportFilters.status);
    if (exportFilters.project && exportFilters.project !== "All Projects")
      fileNameParts.push(exportFilters.project.replace(/\s+/g, "_"));
    if (exportFilters.manager && exportFilters.manager !== "All Managers") {
      const manager = managers.find((m) => m.empId === exportFilters.manager);
      fileNameParts.push(
        `Manager_${manager ? manager.name : exportFilters.manager}`
      );
    }
    if (exportFilters.startDate && exportFilters.endDate) {
      fileNameParts.push(
        `${format(new Date(exportFilters.startDate), "yyyy-MM-dd")}-${format(
          new Date(exportFilters.endDate),
          "yyyy-MM-dd"
        )}`
      );
    }

    const fileName = fileNameParts.join("_");

    XLSX.writeFile(wb, `${fileName}.xlsx`);
  };

  const exportToPDF = (filteredData) => {
    const doc = new jsPDF();

    doc.addImage(logo, "PNG", 15, 10, 40, 15);

    doc.setFontSize(16);
    doc.text("Timesheet Report", 70, 20);
    doc.setFontSize(12);

    let filtersText = `Generated on: ${format(new Date(), "yyyy-MM-dd HH:mm")}`;
    if (exportFilters.status && exportFilters.status !== "All Statuses")
      filtersText += ` | Status: ${exportFilters.status}`;
    if (exportFilters.project && exportFilters.project !== "All Projects")
      filtersText += ` | Project: ${exportFilters.project}`;
    if (exportFilters.manager && exportFilters.manager !== "All Managers") {
      const manager = managers.find((m) => m.empId === exportFilters.manager);
      filtersText += ` | Manager: ${manager ? manager.name : exportFilters.manager
        }`;
    }
    if (exportFilters.startDate && exportFilters.endDate) {
      filtersText += ` | Date Range: ${format(
        new Date(exportFilters.startDate),
        "yyyy-MM-dd"
      )} to ${format(new Date(exportFilters.endDate), "yyyy-MM-dd")}`;
    }

    const splitText = doc.splitTextToSize(filtersText, 180);
    doc.text(splitText, 15, 35);

    autoTable(doc, {
      head: [
        [
          "ID",
          "Employee",
          "Manager",
          "Project",
          "Task",
          "Week",
          "Status",
          "Hours",
          "Submitted At",
        ],
      ],
      body: filteredData.map((ts) => [
        ts.id,
        ts.employeeName,
        ts.managerName,
        ts.project,
        ts.task,
        `${format(parseISO(ts.startDate), "MMM dd")} - ${format(
          parseISO(ts.endDate),
          "MMM dd"
        )}`,
        ts.status,
        ts.totalHours,
        ts.submittedAt
          ? format(parseISO(ts.submittedAt), "MMM dd, HH:mm")
          : "N/A",
      ]),
      startY: 45,
      styles: {
        fontSize: 8,
        cellPadding: 2,
        overflow: "linebreak",
      },
      headStyles: {
        fillColor: [41, 128, 185],
        textColor: 255,
        fontStyle: "bold",
      },
      columnStyles: {
        0: { cellWidth: 15 },
        1: { cellWidth: 25 },
        2: { cellWidth: 25 },
        3: { cellWidth: 25 },
        4: { cellWidth: 25 },
        5: { cellWidth: 25 },
        6: { cellWidth: 15 },
        7: { cellWidth: 10 },
        8: { cellWidth: 25 },
      },
    });

    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(150);
      doc.text(
        `Page ${i} of ${pageCount}`,
        105,
        doc.internal.pageSize.height - 10
      );
      doc.addImage(logo, "PNG", 15, doc.internal.pageSize.height - 15, 30, 10);
    }

    let fileNameParts = [`Timesheets_${format(new Date(), "yyyy-MM-dd")}`];
    if (exportFilters.status && exportFilters.status !== "All Statuses")
      fileNameParts.push(exportFilters.status);
    if (exportFilters.project && exportFilters.project !== "All Projects")
      fileNameParts.push(exportFilters.project.replace(/\s+/g, "_"));
    if (exportFilters.manager && exportFilters.manager !== "All Managers") {
      const manager = managers.find((m) => m.empId === exportFilters.manager);
      fileNameParts.push(
        `Manager_${manager ? manager.name : exportFilters.manager}`
      );
    }
    if (exportFilters.startDate && exportFilters.endDate) {
      fileNameParts.push(
        `${format(new Date(exportFilters.startDate), "yyyy-MM-dd")}-${format(
          new Date(exportFilters.endDate),
          "yyyy-MM-dd"
        )}`
      );
    }
    const fileName = fileNameParts.join("_");

    doc.save(`${fileName}.pdf`);
  };

  const handleExport = () => {
    try {
      const filteredData = getFilteredData();

      if (filteredData.length === 0) {
        showSnackbar("No data to export with current filters", "warning");
        return;
      }

      if (exportType === "excel") {
        exportToExcel(filteredData);
      } else {
        exportToPDF(filteredData);
      }

      showSnackbar(
        `Exported ${filteredData.length
        } timesheets as ${exportType.toUpperCase()}`,
        "success"
      );
      handleExportModalClose();
    } catch (error) {
      console.error(`Error exporting to ${exportType}:`, error);
      showSnackbar(`Failed to export as ${exportType.toUpperCase()}`, "error");
    }
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
      case "Cancelled":
      case "CANCELLED":
        return <Chip label="Cancelled" color="error" size="small" />;  
      default:
        return <Chip label={status} size="small" />;
    }
  };

  const handleFilterChange = async (field, value) => {
    if (field === 'manager') {
      const newFilters = {
        ...appliedFilters,
        manager: value,
        project: "All Projects"
      };
      setAppliedFilters(newFilters);
      
      if (value !== "All Managers") {
        await fetchProjectsForManager(value);
      } else {
        setManagerProjects([]);
      }
    } else {
      setAppliedFilters(prev => ({
        ...prev,
        [field]: value
      }));
    }
  };

  const resetFilters = () => {
    setAppliedFilters({
      status: "All Statuses",
      project: "All Projects",
      startDate: null,
      endDate: new Date(),
      manager: "All Managers",
    });
    setStatusFilter(null);
    setFilteredTimesheets(timesheets);
    setPage(0);
    setFilterModalOpen(false);
  };

  const handleViewDetails = () => {
    navigate(`/${role}/timesheets/${selectedTimesheet.id}`, {
      state: {
        timesheet: selectedTimesheet.timesheetData,
        projectId: selectedTimesheet.projectId,
      },
    });
    handleMenuClose();
  };

  useEffect(() => {
    const fetchData = async () => {
      await fetchProjects();
      await fetchManagers();
      await fetchTimesheets();
      await fetchTimesheetNotifications();
    };

    fetchData();

    const interval = setInterval(() => {
      fetchTimesheetNotifications();
    }, 30000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (appliedFilters.manager && appliedFilters.manager !== "All Managers") {
      fetchProjectsForManager(appliedFilters.manager);
    }
  }, [appliedFilters.manager]);

  useEffect(() => {
    applyFilters();
  }, [statusFilter, appliedFilters.status]);

  if (loading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh",
        }}
      >
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
      <Paper
        elevation={0}
        sx={{ 
          p: 3, 
          width: "100%", 
          maxWidth: 1600, 
          overflowX: 'auto',
          mx: "auto",
          borderRadius: 3,
          boxShadow: "0px 2px 10px rgba(0, 0, 0, 0.08)",
        }}
      >
        {/* Header Section */}
        <Grid
          container
          justifyContent="space-between"
          alignItems="center"
          sx={{ mb: 3 }}
        >
          <Grid item>
            <Typography variant="h5" sx={{ fontWeight: "bold", color: "#2c3e50" }}>
              Timesheet Management
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Review employee timesheets
            </Typography>
          </Grid>
          <Grid item>
            <Stack direction="row" spacing={1} alignItems="center">
              {/* Status Filter Chips */}
              <Chip
                label={`${timesheets.filter(
                  (e) => e.status === "Pending" || e.status === "SUBMITTED"
                ).length
                  } Pending`}
                color="warning"
                size="small"
                variant={statusFilter === "SUBMITTED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("SUBMITTED")}
                clickable
                sx={{ 
                  fontWeight: 600,
                  borderWidth: 2,
                  '&:hover': { borderWidth: 2 }
                }}
              />
              <Chip
                label={`${timesheets.filter((e) => e.status === "APPROVED").length
                  } Approved`}
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
                label={`${timesheets.filter((e) => e.status === "REJECTED").length
                  } Rejected`}
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
              <Chip
                label={`${timesheets.filter((e) => e.status === "CANCELLED").length
                  } Cancelled`}
                color="error"
                size="small"
                variant={statusFilter === "CANCELLED" ? "filled" : "outlined"}
                onClick={() => handleStatusFilter("CANCELLED")}
                clickable
                sx={{
                  fontWeight: 600,
                  borderWidth: 2,
                  '&:hover': { borderWidth: 2 }
                }}
              />
              
              {hasAction("EXPORT_TIMESHEET_DATA") && (
                <Button
                  variant="contained"
                  startIcon={<FileDownload />}
                  onClick={handleExportModalOpen}
                  sx={{ 
                    ml: 2,
                    backgroundColor: "#3498db",
                    '&:hover': {
                      backgroundColor: "#2980b9",
                    }
                  }}
                >
                  Export
                </Button>
              )}

             
              
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
          backgroundColor: "#f8f9fa",
          p: 2,
          borderRadius: 2,
        }}>
          <TextField
            size="small"
            placeholder="Search timesheets..."
            value={searchTerm}
            onChange={handleSearchChange}
            sx={{ 
              minWidth: 300,
              backgroundColor: "white",
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
                  <IconButton size="small" onClick={resetSearch} edge="end">
                    <Cancel fontSize="small" color="action" />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
          <Button
            variant="outlined"
            startIcon={<FilterAlt />}
            onClick={handleFilterModalOpen}
            sx={{
              borderWidth: 2,
              '&:hover': {
                borderWidth: 2,
              }
            }}
          >
            Filters
          </Button>
        </Box>

        {/* Filter Modal */}
        <Dialog
          open={filterModalOpen}
          onClose={handleFilterModalClose}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle sx={{ backgroundColor: "#f8f9fa", borderBottom: 1, borderColor: "divider" }}>
            Filter Timesheets
          </DialogTitle>
          <DialogContent>
            <Box
              sx={{ display: "flex", flexDirection: "column", gap: 3, pt: 2 }}
            >
              <FormControl fullWidth size="small">
                <InputLabel>Manager</InputLabel>
                <Select
                  value={appliedFilters.manager}
                  label="Manager"
                  onChange={(e) =>
                    handleFilterChange("manager", e.target.value)
                  }
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Managers">All Managers</MenuItem>
                  {Array.isArray(managers) &&
                    managers.map((manager) => (
                      <MenuItem key={manager.empId} value={manager.empId}>
                        {manager.name}
                      </MenuItem>
                    ))}
                </Select>
              </FormControl>

              <FormControl fullWidth size="small">
                <InputLabel>Project</InputLabel>
                <Select
                  value={appliedFilters.project}
                  label="Project"
                  onChange={(e) => setAppliedFilters(prev => ({
                    ...prev,
                    project: e.target.value
                  }))}
                  disabled={appliedFilters.manager === "All Managers"}
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Projects">All Projects</MenuItem>
                  {managerProjects
                    .filter(project =>
                      appliedFilters.manager === "All Managers" ||
                      project.managerId === appliedFilters.manager
                    )
                    .map(project => (
                      <MenuItem key={project.id} value={project.name}>
                        {project.name}
                      </MenuItem>
                    ))
                  }
                </Select>
              </FormControl>

              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select
                  value={appliedFilters.status}
                  label="Status"
                  onChange={(e) => handleFilterChange("status", e.target.value)}
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Statuses">All Statuses</MenuItem>
                  <MenuItem value="SUBMITTED">Pending</MenuItem>
                  <MenuItem value="APPROVED">Approved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                  <MenuItem value="CANCELLED">Cancelled</MenuItem>
                </Select>
              </FormControl>

              <DatePicker
                label="Start Date"
                value={appliedFilters.startDate}
                onChange={(newValue) =>
                  handleFilterChange("startDate", newValue)
                }
                renderInput={(params) => (
                  <TextField {...params} fullWidth size="small" />
                )}
                maxDate={appliedFilters.endDate}
              />

              <DatePicker
                label="End Date"
                value={appliedFilters.endDate}
                onChange={(newValue) => handleFilterChange("endDate", newValue)}
                renderInput={(params) => (
                  <TextField {...params} fullWidth size="small" />
                )}
                minDate={appliedFilters.startDate}
              />
            </Box>
          </DialogContent>
          <DialogActions sx={{ backgroundColor: "#f8f9fa", borderTop: 1, borderColor: "divider" }}>
            <Button onClick={resetFilters} color="error">
              Reset
            </Button>
            <Button
              onClick={() => {
                applyFilters();
                handleFilterModalClose();
              }}
              variant="contained"
              color="primary"
              disabled={
                appliedFilters.startDate &&
                appliedFilters.endDate &&
                appliedFilters.startDate > appliedFilters.endDate
              }
            >
              Apply Filters
            </Button>
          </DialogActions>
        </Dialog>

        {/* Export Filter Modal */}
        <Dialog
          open={exportModalOpen}
          onClose={handleExportModalClose}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle sx={{ backgroundColor: "#f8f9fa", borderBottom: 1, borderColor: "divider" }}>
            Export Timesheets
          </DialogTitle>
          <DialogContent>
            <Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
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
                  sx={{ minWidth: "auto" }}
                />
                <Tab
                  icon={<PictureAsPdf />}
                  iconPosition="start"
                  label="PDF"
                  value="pdf"
                  sx={{ minWidth: "auto" }}
                />
              </Tabs>
            </Box>

            <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel>Manager</InputLabel>
                <Select
                  value={exportFilters.manager}
                  label="Manager"
                  onChange={(e) =>
                    handleExportFilterChange("manager", e.target.value)
                  }
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Managers">All Managers</MenuItem>
                  {Array.isArray(managers) &&
                    managers.map((manager) => (
                      <MenuItem key={manager.empId} value={manager.empId}>
                        {manager.name}
                      </MenuItem>
                    ))}
                </Select>
              </FormControl>

              <FormControl fullWidth size="small">
                <InputLabel>Project</InputLabel>
                <Select
                  value={exportFilters.project}
                  label="Project"
                  onChange={(e) =>
                    handleExportFilterChange("project", e.target.value)
                  }
                  disabled={exportFilters.manager === "All Managers"}
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Projects">All Projects</MenuItem>
                  {Array.isArray(managerProjects) &&
                    managerProjects.map((project) => (
                      <MenuItem key={project.id} value={project.name}>
                        {project.name}
                      </MenuItem>
                    ))}
                </Select>
              </FormControl>

              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select
                  value={exportFilters.status}
                  label="Status"
                  onChange={(e) =>
                    handleExportFilterChange("status", e.target.value)
                  }
                  MenuProps={{
                    disableAutoFocusItem: true,
                  }}
                >
                  <MenuItem value="All Statuses">All Statuses</MenuItem>
                  <MenuItem value="SUBMITTED">Pending</MenuItem>
                  <MenuItem value="APPROVED">Approved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                  <MenuItem value="CANCELLED">Cancelled</MenuItem>
                </Select>
              </FormControl>

              <DatePicker
                label="Start Date"
                value={exportFilters.startDate}
                onChange={(newValue) =>
                  handleExportFilterChange("startDate", newValue)
                }
                renderInput={(params) => (
                  <TextField {...params} fullWidth size="small" />
                )}
                maxDate={exportFilters.endDate}
              />

              <DatePicker
                label="End Date"
                value={exportFilters.endDate}
                onChange={(newValue) =>
                  handleExportFilterChange("endDate", newValue)
                }
                renderInput={(params) => (
                  <TextField {...params} fullWidth size="small" />
                )}
                minDate={exportFilters.startDate}
              />
            </Box>
          </DialogContent>
          <DialogActions sx={{ backgroundColor: "#f8f9fa", borderTop: 1, borderColor: "divider" }}>
            <Button onClick={handleExportModalClose}>Cancel</Button>
            <Button
              onClick={handleExport}
              variant="contained"
              color="primary"
              disabled={
                exportFilters.startDate &&
                exportFilters.endDate &&
                exportFilters.startDate > exportFilters.endDate
              }
              startIcon={exportType === "excel" ? <GridOn /> : <PictureAsPdf />}
            >
              Export as {exportType.toUpperCase()}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Timesheets Table */}
        <TableContainer 
          component={Paper} 
          elevation={0}
          sx={{
            minWidth: 1200, // Set a minimum width to ensure horizontal scrolling when needed
            border: 'none',
          }}
        >
          <Table sx={{ minWidth: 1200 }}>
            <TableHead sx={{ backgroundColor: "#f5f5f5" }}>
              <TableRow>
                <TableCell sx={{ fontWeight: "bold" }}>Timesheet ID</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Employee</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Manager</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Project</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Task</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Week</TableCell>
                <TableCell align="right" sx={{ fontWeight: "bold" }}>Total Hours</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Status</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Submitted At</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Actions</TableCell>
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
                            <Typography fontWeight="bold">
                              {timesheet.employeeName}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {timesheet.employeeId}
                            </Typography>
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Typography fontWeight="bold">
                          {timesheet.managerName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {timesheet.managerId}
                        </Typography>
                      </TableCell>
                      <TableCell>{timesheet.project}</TableCell>
                      <TableCell>
                        <Box sx={{ 
                          maxWidth: 200, 
                          whiteSpace: 'nowrap', 
                          overflow: 'hidden', 
                          textOverflow: 'ellipsis' 
                        }}>
                          {timesheet.task}
                        </Box>
                      </TableCell>
                      <TableCell>
                        {format(parseISO(timesheet.startDate), "MMM dd")} -{" "}
                        {format(parseISO(timesheet.endDate), "MMM dd, yyyy")}
                      </TableCell>
                      <TableCell align="right">
                        {timesheet.totalHours}
                      </TableCell>
                      <TableCell>{getStatusChip(timesheet.status)}</TableCell>
                      <TableCell>
                        {timesheet.submittedAt
                          ? format(
                            parseISO(timesheet.submittedAt),
                            "MMM dd, h:mm a"
                          )
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
                  <TableCell colSpan={10} align="center" sx={{ py: 4 }}>
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
            borderColor: "divider",
            '& .MuiTablePagination-toolbar': {
              paddingLeft: 2,
            }
          }}
        />

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
                      navigate(`/${role}/timesheets/${notification.timesheetId}`, {
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
            vertical: "top",
            horizontal: "right",
          }}
          transformOrigin={{
            vertical: "top",
            horizontal: "right",
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

export default AdminTimesheetManagement;