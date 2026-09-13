import React, { useState, useEffect } from "react";
import {
  Box, Button, Typography, TextField, Modal, Card, CardContent, CardActions,
  IconButton, Tabs, Tab, MenuItem, Select, InputLabel, FormControl,
  Chip, Divider, Tooltip, Badge, CircularProgress,
  Snackbar, Alert, List, ListItem, ListItemText, ListItemSecondaryAction,
  Dialog, DialogTitle, DialogContent, DialogActions, Autocomplete, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, Paper
} from "@mui/material";
import {
  Edit, Delete, Add, Search, DateRange, AttachMoney, PriorityHigh,
  Description, Category, CheckCircle, HourglassEmpty,
  People, Assignment, Visibility, Person
} from "@mui/icons-material";
import { styled } from "@mui/material/styles";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
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

const StatusChip = styled(Chip)(({ status, theme }) => ({
  backgroundColor:
    status === "COMPLETED" 
      ? theme.palette.success.lighter || '#e8f5e9'  // Lighter green
      : theme.palette.info.lighter || '#e3f2fd',    // Lighter blue
  color:
    status === "COMPLETED" 
      ? theme.palette.success.dark 
      : theme.palette.info.dark,
  fontWeight: 600,
  borderRadius: "8px",
  textTransform: 'capitalize'
}));

const Project = () => {
  const [employees, setEmployees] = useState([]);
  const [managers, setManagers] = useState([]);
  const [employeeSearch, setEmployeeSearch] = useState("");
  const [managerSearch, setManagerSearch] = useState("");
  const [filteredEmployees, setFilteredEmployees] = useState([]);
  const [filteredManagers, setFilteredManagers] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [tab, setTab] = useState("ALL");
  const [openModal, setOpenModal] = useState(false);
  const [newProject, setNewProject] = useState({
    name: "",
    category: "",
    startDate: "",
    endDate: "",
    priority: "MEDIUM",
    budget: "",
    description: "",
    status: "STARTED",
    managerId: "",
    managerName: ""
  });
  const [openAssignmentModal, setOpenAssignmentModal] = useState(false);
  const [currentProjectId, setCurrentProjectId] = useState(null);
  const [assignments, setAssignments] = useState([]);
  const [newAssignment, setNewAssignment] = useState({
    empId: "",
    empName: "",
    projectId: null
  });
  const [openDeleteDialog, setOpenDeleteDialog] = useState(false);
  const [assignmentToDelete, setAssignmentToDelete] = useState(null);
  const [openEditModal, setOpenEditModal] = useState(false);
  const [currentAssignment, setCurrentAssignment] = useState(null);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "info"
  });
  const [openViewAssignments, setOpenViewAssignments] = useState(false);
  const [projectToEdit, setProjectToEdit] = useState(null);

  const { actions } = useContext(userContext);
  const hasAction = (actionName) => actions.includes(actionName);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [projectsRes, managersRes] = await Promise.all([
          axios.get(`${API_BASE_URL}/projects`),
          axios.get(`${API_BASE_URL}/managers`)
        ]);

        setProjects(projectsRes.data || []);
        setManagers(managersRes?.data || []);
        setFilteredManagers(managersRes?.data || []);
        setLoading(false);
      } catch (error) {
        console.error("Error fetching data:", error);
        setSnackbar({
          open: true,
          message: "Failed to fetch data",
          severity: "error"
        });
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const handleSearchChange = (e) => setSearchQuery(e.target.value);
  const handleTabChange = (e, newValue) => setTab(newValue);
  const handleOpenModal = () => setOpenModal(true);
  const handleCloseModal = () => {
    setOpenModal(false);
    setProjectToEdit(null);
  };

  const handleOpenEditProjectModal = (project) => {
    setProjectToEdit(project);
    setNewProject({
      name: project.name,
      category: project.category,
      startDate: project.startDate ? project.startDate.split('T')[0] : "",
      endDate: project.endDate ? project.endDate.split('T')[0] : "",
      priority: project.priority,
      budget: project.budget,
      description: project.description,
      status: project.status,
      managerId: project.managerId || "",
      managerName: project.managerName || ""
    });
    setOpenModal(true);
  };

  const handleOpenAssignmentModal = async (projectId) => {
    setCurrentProjectId(projectId);
    setOpenAssignmentModal(true);
    await fetchEmployees();
    fetchAssignments(projectId);
    setNewAssignment({
      empId: "",
      empName: "",
      projectId: projectId
    });
    setEmployeeSearch("");
  };

  const handleCloseAssignmentModal = () => setOpenAssignmentModal(false);

  const fetchEmployees = async (searchQuery = "") => {
    try {
      const response = await axios.get(`${API_BASE_URL}/employees/search?query=${searchQuery}`);
      setEmployees(response.data?.data || []);
      setFilteredEmployees(response.data?.data || []);
    } catch (error) {
      console.error("Error fetching employees:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to fetch employees",
        severity: "error"
      });
    }
  };

  const handleEmployeeSearch = async (searchValue) => {
    setEmployeeSearch(searchValue);
    if (searchValue.length > 0) {
      await fetchEmployees(searchValue);
    } else {
      setFilteredEmployees(employees);
    }
  };

  const handleManagerSearch = (searchValue) => {
    setManagerSearch(searchValue);
    if (searchValue.length > 0) {
      const filtered = managers.filter(manager =>
        (manager.name?.toLowerCase().includes(searchValue.toLowerCase()) ||
         manager.empId?.toLowerCase().includes(searchValue.toLowerCase()))
      );
      setFilteredManagers(filtered);
    } else {
      setFilteredManagers(managers);
    }
  };

  const fetchAssignments = async (projectId) => {
    try {
      const response = await axios.get(`${API_BASE_URL}/assignments/project/${projectId}`);
      setAssignments(response.data || []);
    } catch (error) {
      console.error("Error fetching assignments:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to fetch assignments",
        severity: "error"
      });
    }
  };

  const handleCreateProject = async () => {
    if (!newProject.name || !newProject.name.trim()) {
      setSnackbar({
        open: true,
        message: "Project name cannot be empty or only spaces",
        severity: "warning"
      });
      return;
    }

    if (newProject.startDate && newProject.endDate) {
      const startDate = new Date(newProject.startDate);
      const endDate = new Date(newProject.endDate);
      if (endDate < startDate) {
        setSnackbar({
          open: true,
          message: "End date cannot be before start date",
          severity: "warning"
        });
        return;
      }
    }

    if (!newProject.category) {
      setSnackbar({
        open: true,
        message: "Please fill out all required fields",
        severity: "warning"
      });
      return;
    }

    try {
      const projectToCreate = {
        ...newProject,
        startDate: newProject.startDate ? new Date(newProject.startDate).toISOString() : null,
        endDate: newProject.endDate ? new Date(newProject.endDate).toISOString() : null
      };

      let response;
      if (projectToEdit) {
        response = await axios.put(`${API_BASE_URL}/projects/${projectToEdit.id}`, projectToCreate);
        setProjects(projects.map(p => p.id === projectToEdit.id ? response.data : p));
      } else {
        response = await axios.post(`${API_BASE_URL}/projects/add`, projectToCreate);
        setProjects([...projects, response.data]);
      }

      setSnackbar({
        open: true,
        message: `Project ${projectToEdit ? 'updated' : 'created'} successfully`,
        severity: "success"
      });
      handleCloseModal();
    } catch (error) {
      console.error("Error creating project:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || `Failed to ${projectToEdit ? 'update' : 'create'} project`,
        severity: "error"
      });
    }
  };

  const handleDeleteProject = async (id) => {
    if (window.confirm("Are you sure you want to delete this project?")) {
      try {
        await axios.delete(`${API_BASE_URL}/projects/${id}`);
        setProjects(projects.filter((project) => project.id !== id));
        setSnackbar({
          open: true,
          message: "Project deleted successfully",
          severity: "success"
        });
      } catch (error) {
        console.error("Error deleting project:", error);
        setSnackbar({
          open: true,
          message: error.response?.data?.message || "Failed to delete project",
          severity: "error"
        });
      }
    }
  };

  const handleStatusChange = async (id, newStatus) => {
    try {
      const response = await axios.patch(
        `${API_BASE_URL}/projects/${id}/status`,
        newStatus,
        { headers: { 'Content-Type': 'text/plain' } }
      );
      setProjects(projects.map((project) =>
        project.id === id ? response.data : project
      ));

      setSnackbar({
        open: true,
        message: "Status updated successfully",
        severity: "success"
      });
    } catch (error) {
      console.error("Error updating status:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to update status",
        severity: "error"
      });
    }
  };

  const handleAssignmentChange = (e) => {
    const { name, value } = e.target;
    setNewAssignment(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleCreateAssignment = async () => {
    if (!newAssignment.empId) {
      setSnackbar({
        open: true,
        message: "Please select an employee",
        severity: "warning"
      });
      return;
    }

    const isAlreadyAssigned = assignments.some(
      assignment => assignment.empId === newAssignment.empId
    );

    if (isAlreadyAssigned) {
      setSnackbar({
        open: true,
        message: "This employee is already assigned to the project",
        severity: "warning"
      });
      return;
    }

    try {
      const response = await axios.post(`${API_BASE_URL}/assignments`, newAssignment);
      setAssignments([...assignments, response.data]);
      setSnackbar({
        open: true,
        message: "Assignment created successfully",
        severity: "success"
      });
      setNewAssignment({
        empId: "",
        empName: "",
        projectId: currentProjectId
      });
      setEmployeeSearch("");
    } catch (error) {
      console.error("Error creating assignment:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to create assignment",
        severity: "error"
      });
    }
  };

  const handleOpenDeleteDialog = (assignment) => {
    setAssignmentToDelete(assignment);
    setOpenDeleteDialog(true);
  };

  const handleCloseDeleteDialog = () => {
    setOpenDeleteDialog(false);
    setAssignmentToDelete(null);
  };

  const handleDeleteAssignment = async () => {
    try {
      await axios.delete(`${API_BASE_URL}/assignments/${assignmentToDelete.id}`);
      setAssignments(assignments.filter(a => a.id !== assignmentToDelete.id));
      setSnackbar({
        open: true,
        message: "Assignment deleted successfully",
        severity: "success"
      });
      handleCloseDeleteDialog();
    } catch (error) {
      console.error("Error deleting assignment:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to delete assignment",
        severity: "error"
      });
    }
  };

  const handleOpenEditModal = (assignment) => {
    setCurrentAssignment(assignment);
    setOpenEditModal(true);
  };

  const handleCloseEditModal = () => {
    setOpenEditModal(false);
    setCurrentAssignment(null);
  };

  const handleUpdateAssignment = async () => {
    if (!currentAssignment.empId || !currentAssignment.empName) {
      setSnackbar({
        open: true,
        message: "Please fill out all required fields",
        severity: "warning"
      });
      return;
    }

    try {
      const response = await axios.put(
        `${API_BASE_URL}/assignments/${currentAssignment.id}`,
        currentAssignment
      );
      setAssignments(assignments.map(a =>
        a.id === currentAssignment.id ? response.data : a
      ));
      setSnackbar({
        open: true,
        message: "Assignment updated successfully",
        severity: "success"
      });
      handleCloseEditModal();
    } catch (error) {
      console.error("Error updating assignment:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to update assignment",
        severity: "error"
      });
    }
  };

  const filteredProjects = [...projects]
  .sort((a, b) => {
    // First try sorting by createdAt date (newest first)
    if (a.createdAt && b.createdAt) {
      return new Date(b.createdAt) - new Date(a.createdAt);
    }
    // Fallback to sorting by ID (assuming higher IDs are newer)
    return b.id - a.id;
  })
  .filter((project) =>
    (tab === "ALL" || project.status === tab) &&
    project.name?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getStatusIcon = (status) => {
    switch (status) {
      case "COMPLETED":
        return <CheckCircle fontSize="small" />;
      default:
        return <HourglassEmpty fontSize="small" />;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "";
    const options = { year: "numeric", month: "short", day: "numeric" };
    return new Date(dateString).toLocaleDateString(undefined, options);
  };

  const handleCloseSnackbar = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  const handleViewAssignments = (projectId) => {
    setCurrentProjectId(projectId);
    fetchAssignments(projectId);
    setOpenViewAssignments(true);
  };

  if (loading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh"
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, backgroundColor: "#f8fafc", minHeight: "100vh" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: 700 }}>
        Manage Projects
      </Typography>

      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
          gap: 2,
          flexWrap: "wrap"
        }}
      >
        {hasAction("CREATE_PROJECT") && (
          <Button
            variant="contained"
            color="primary"
            onClick={() => {
              setProjectToEdit(null);
              setNewProject({
                name: "",
                category: "",
                startDate: "",
                endDate: "",
                priority: "",
                budget: "",
                description: "",
                status: "STARTED",
                managerId: "",
                managerName: ""
              });
              setManagerSearch("");
              handleOpenModal();
            }}
            startIcon={<Add />}
            sx={{
              borderRadius: "10px",
              px: 3,
              py: 1,
              textTransform: "none"
            }}
          >
            New Project
          </Button>
        )}
        <TextField
          variant="outlined"
          size="small"
          placeholder="Search projects..."
          value={searchQuery}
          onChange={handleSearchChange}
          sx={{
            width: 300,
            "& .MuiOutlinedInput-root": {
              borderRadius: "10px",
              backgroundColor: "white"
            }
          }}
          InputProps={{
            startAdornment: <Search sx={{ mr: 1 }} />
          }}
        />
      </Box>

      <Tabs
        value={tab}
        onChange={handleTabChange}
        textColor="primary"
        indicatorColor="primary"
        sx={{
          mb: 3,
          "& .MuiTabs-flexContainer": {
            gap: 1
          }
        }}
      >
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Badge badgeContent={projects.length} color="primary" />
              All
            </Box>
          }
          value="ALL"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <HourglassEmpty fontSize="small" />
              Started
              <Badge
                badgeContent={projects.filter((p) => p.status === "STARTED").length}
                color="primary"
              />
            </Box>
          }
          value="STARTED"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CheckCircle fontSize="small" />
              Completed
              <Badge
                badgeContent={projects.filter((p) => p.status === "COMPLETED").length}
                color="primary"
              />
            </Box>
          }
          value="COMPLETED"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
      </Tabs>

      {filteredProjects.length === 0 ? (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "300px",
            backgroundColor: "white",
            borderRadius: "12px",
            boxShadow: "0 4px 20px 0 rgba(0,0,0,0.05)"
          }}
        >
          <Typography variant="h6" color="textSecondary">
            No projects found. Create a new project to get started!
          </Typography>
        </Box>
      ) : (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, 1fr)",
              lg: "repeat(3, 1fr)",
              xl: "repeat(4, 1fr)"
            },
            gap: 3
          }}
        >
          {filteredProjects.map((project) => (
            <StyledCard key={project.id}>
              <CardContent>
                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                    mb: 2
                  }}
                >
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {project.name}
                  </Typography>
                  <StatusChip
                    label={project.status.toLowerCase()}
                    status={project.status}
                    icon={getStatusIcon(project.status)}
                    size="small"
                  />
                </Box>

                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}
                >
                  <Category fontSize="small" /> {project.category}
                </Typography>

                {project.managerId && (
                  <Typography
                    variant="body2"
                    sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}
                  >
                    <Person fontSize="small" />
                    Manager: {project.managerName} ({project.managerId})
                  </Typography>
                )}

                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: "flex", alignItems: "center", gap: 1 }}
                  >
                    <DateRange fontSize="small" />
                    {formatDate(project.startDate)} - {formatDate(project.endDate)}
                  </Typography>
                  <Chip
                    label={project.priority.toLowerCase()}
                    size="small"
                    sx={{
                      backgroundColor:
                        project.priority === "HIGH"
                          ? "#ffebee"
                          : project.priority === "MEDIUM"
                            ? "#fff3e0"
                            : "#e8f5e9",
                      color:
                        project.priority === "HIGH"
                          ? "#c62828"
                          : project.priority === "MEDIUM"
                            ? "#e65100"
                            : "#2e7d32"
                    }}
                  />
                </Box>

                <Divider sx={{ my: 2 }} />

                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center"
                  }}
                >
                  <Typography
                    variant="body2"
                    sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
                  >
                    <AttachMoney fontSize="small" />
                    {project.budget ? project.budget.toLocaleString() : "N/A"}
                  </Typography>
                </Box>
              </CardContent>
              <CardActions
                sx={{
                  display: "flex",
                  justifyContent: "space-between",
                  px: 2,
                  pb: 2
                }}
              >
                <Box>
                  <Select
                    value={project.status}
                    onChange={(e) => handleStatusChange(project.id, e.target.value)}
                    size="small"
                    sx={{
                      borderRadius: "8px",
                      "& .MuiSelect-select": { py: 0.75 }
                    }}
                  >
                    <MenuItem value="STARTED">Started</MenuItem>
                    <MenuItem value="COMPLETED">Completed</MenuItem>
                  </Select>
                </Box>
                <Box>
                  {hasAction("VIEW_ASSIGN_EMPLOYEE") && (
                    <Tooltip title="View Assignments">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => handleViewAssignments(project.id)}
                      >
                        <Visibility fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  {hasAction("ASSIGN_EMPLOYEE") && (
                    <Tooltip title="Assign Employee">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => handleOpenAssignmentModal(project.id)}
                      >
                        <People fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  {hasAction("UPDATE_PROJECT") && (
                    <Tooltip title="Edit">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => handleOpenEditProjectModal(project)}
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  {hasAction("DELETE_PROJECT") && (
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDeleteProject(project.id)}
                      >
                        <Delete fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </Box>
              </CardActions>
            </StyledCard>
          ))}
        </Box>
      )}

      {/* Create/Edit Project Modal */}
      <Modal open={openModal} onClose={handleCloseModal}>
        <Box
          sx={{
            width: { xs: "90%", sm: "70%", md: "50%" },
            maxWidth: "800px",
            maxHeight: "90vh",
            overflowY: "auto",
            backgroundColor: "white",
            p: 4,
            borderRadius: "16px",
            mx: "auto",
            my: "5vh",
            boxShadow: 24
          }}
        >
          <Typography variant="h5" gutterBottom sx={{ fontWeight: 600 }}>
            {projectToEdit ? "Edit Project" : "Create New Project"}
          </Typography>

          <Box sx={{ mb: 3 }}>
            <TextField
              fullWidth
              label="Project Name"
              variant="outlined"
              value={newProject.name}
              onChange={(e) =>
                setNewProject({ ...newProject, name: e.target.value })
              }
              sx={{ mb: 2 }}
            />

            {/* Manager Selection */}
            <Autocomplete
              freeSolo
              options={filteredManagers || []}
              getOptionLabel={(option) =>
                option ? `${option.empId} - ${option.name}` : ""
              }
              inputValue={managerSearch}
              onInputChange={(event, newInputValue) => {
                handleManagerSearch(newInputValue);
              }}
              onChange={(event, newValue) => {
                if (newValue) {
                  setNewProject({
                    ...newProject,
                    managerId: newValue.empId,
                    managerName: newValue.name
                  });
                } else {
                  setNewProject({
                    ...newProject,
                    managerId: "",
                    managerName: ""
                  });
                }
              }}
              value={managers?.find(m => m.empId === newProject.managerId) || null}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Project Manager"
                  variant="outlined"
                  fullWidth
                  sx={{ mb: 2 }}
                  InputProps={{
                    ...params.InputProps,
                    startAdornment: <Person sx={{ mr: 1 }} />
                  }}
                />
              )}
              renderOption={(props, option) => (
                <li {...props}>
                  <Box sx={{ display: "flex", flexDirection: "column" }}>
                    <Typography>{option.empId}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {option.name}
                    </Typography>
                  </Box>
                </li>
              )}
            />

            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>Project Category</InputLabel>
              <Select
                label="Project Category"
                value={newProject.category}
                onChange={(e) =>
                  setNewProject({ ...newProject, category: e.target.value })
                }
              >
                <MenuItem value="UI/UX Design">UI/UX Design</MenuItem>
                <MenuItem value="Web Development">Web Development</MenuItem>
                <MenuItem value="Mobile Development">Mobile Development</MenuItem>
                <MenuItem value="Testing">Testing</MenuItem>
                <MenuItem value="Other">Other</MenuItem>
              </Select>
            </FormControl>

            <TextField
              fullWidth
              label="Start Date"
              type="date"
              value={newProject.startDate}
              onChange={(e) =>
                setNewProject({ ...newProject, startDate: e.target.value })
              }
              InputLabelProps={{ shrink: true }}
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              label="End Date"
              type="date"
              value={newProject.endDate}
              onChange={(e) =>
                setNewProject({ ...newProject, endDate: e.target.value })
              }
              InputLabelProps={{ shrink: true }}
              sx={{ mb: 2 }}
            />

            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>Priority</InputLabel>
              <Select
                label="Priority"
                value={newProject.priority}
                onChange={(e) =>
                  setNewProject({ ...newProject, priority: e.target.value })
                }
              >
                <MenuItem value="HIGH">High</MenuItem>
                <MenuItem value="MEDIUM">Medium</MenuItem>
                <MenuItem value="LOW">Low</MenuItem>
              </Select>
            </FormControl>

            <TextField
              fullWidth
              label="Budget"
              type="number"
              value={newProject.budget}
              onChange={(e) =>
                setNewProject({ ...newProject, budget: e.target.value })
              }
              sx={{ mb: 2 }}
              InputProps={{
                inputProps: {
                  min: 0
                }
              }}
            />
            <TextField
              fullWidth
              label="Description"
              multiline
              rows={3}
              value={newProject.description}
              onChange={(e) =>
                setNewProject({ ...newProject, description: e.target.value })
              }
              sx={{ mb: 2 }}
            />
          </Box>

          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between"
            }}
          >
            <Button variant="contained" color="primary" onClick={handleCreateProject}>
              {projectToEdit ? "Update" : "Create"}
            </Button>
            <Button variant="outlined" color="secondary" onClick={handleCloseModal}>
              Cancel
            </Button>
          </Box>
        </Box>
      </Modal>

      {/* Assignment Modal */}
      <Modal open={openAssignmentModal} onClose={handleCloseAssignmentModal}>
        <Box
          sx={{
            width: { xs: "90%", sm: "70%", md: "50%" },
            maxWidth: "600px",
            maxHeight: "90vh",
            overflowY: "auto",
            backgroundColor: "white",
            p: 4,
            borderRadius: "16px",
            mx: "auto",
            my: "5vh",
            boxShadow: 24
          }}
        >
          <Typography variant="h5" gutterBottom sx={{ fontWeight: 600 }}>
            Assign Project to Employee
          </Typography>

          <Box sx={{ mb: 3 }}>
            <Autocomplete
              freeSolo
              options={filteredEmployees || []}
              getOptionLabel={(option) =>
                option ? `${option.empId} - ${option.name}` : ""
              }
              inputValue={employeeSearch}
              onInputChange={(event, newInputValue) => {
                handleEmployeeSearch(newInputValue);
              }}
              onChange={(event, newValue) => {
                if (newValue) {
                  setNewAssignment({
                    empId: newValue.empId,
                    empName: newValue.name,
                    projectId: currentProjectId
                  });
                }
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Search Employee"
                  variant="outlined"
                  fullWidth
                  sx={{ mb: 2 }}
                  InputProps={{
                    ...params.InputProps,
                    startAdornment: <Search sx={{ mr: 1 }} />
                  }}
                />
              )}
              renderOption={(props, option) => (
                <li {...props}>
                  <Box sx={{ display: "flex", flexDirection: "column" }}>
                    <Typography>{option.empId}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {option.name}
                    </Typography>
                  </Box>
                </li>
              )}
            />

            <Button
              variant="contained"
              color="primary"
              onClick={handleCreateAssignment}
              startIcon={<Assignment />}
              sx={{ mb: 3 }}
              disabled={!newAssignment.empId}
            >
              Assign
            </Button>

            <Typography variant="h6" sx={{ mb: 2 }}>
              Current Assignments
            </Typography>
            {assignments.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No employees assigned to this project yet.
              </Typography>
            ) : (
              <List>
                {assignments.map((assignment) => (
                  <ListItem key={assignment.id}>
                    <ListItemText
                      primary={assignment.empName}
                      secondary={`Employee ID: ${assignment.empId}`}
                    />
                    <ListItemSecondaryAction>
                      <IconButton
                        edge="end"
                        onClick={() => handleOpenEditModal(assignment)}
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton
                        edge="end"
                        onClick={() => handleOpenDeleteDialog(assignment)}
                      >
                        <Delete fontSize="small" color="error" />
                      </IconButton>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}
          </Box>

          <Box
            sx={{
              display: "flex",
              justifyContent: "flex-end"
            }}
          >
            <Button variant="outlined" color="secondary" onClick={handleCloseAssignmentModal}>
              Close
            </Button>
          </Box>
        </Box>
      </Modal>

      {/* View Assignments Dialog */}
      <Dialog
        open={openViewAssignments}
        onClose={() => setOpenViewAssignments(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Assigned Employees</DialogTitle>
        <DialogContent>
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Employee ID</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {assignments.map((assignment) => (
                  <TableRow key={assignment.id}>
                    <TableCell>{assignment.empId}</TableCell>
                    <TableCell>{assignment.empName}</TableCell>
                    <TableCell>
                      <IconButton
                        onClick={() => handleOpenEditModal(assignment)}
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton
                        onClick={() => handleOpenDeleteDialog(assignment)}
                      >
                        <Delete fontSize="small" color="error" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenViewAssignments(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Edit Assignment Dialog */}
      <Dialog open={openEditModal} onClose={handleCloseEditModal}>
        <DialogTitle>Edit Assignment</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <TextField
              fullWidth
              label="Employee ID"
              name="empId"
              value={currentAssignment?.empId || ""}
              onChange={(e) =>
                setCurrentAssignment({
                  ...currentAssignment,
                  empId: e.target.value
                })
              }
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              label="Employee Name"
              name="empName"
              value={currentAssignment?.empName || ""}
              onChange={(e) =>
                setCurrentAssignment({
                  ...currentAssignment,
                  empName: e.target.value
                })
              }
              sx={{ mb: 2 }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseEditModal}>Cancel</Button>
          <Button onClick={handleUpdateAssignment} color="primary">
            Update
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Assignment Dialog */}
      <Dialog open={openDeleteDialog} onClose={handleCloseDeleteDialog}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete this assignment for employee{" "}
            {assignmentToDelete?.empName}?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDeleteDialog}>Cancel</Button>
          <Button onClick={handleDeleteAssignment} color="error">
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ width: '100%' }}
          variant="filled"
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default Project;