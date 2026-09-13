import React, { useState, useEffect } from "react";
import {
  Box, Button, Typography, TextField, Modal, Card, CardContent, CardActions,
  IconButton, Tabs, Tab, MenuItem, Select, InputLabel, FormControl,
  LinearProgress, Chip, Divider, Tooltip, Badge, CircularProgress,
  Snackbar, Alert, Avatar, List, ListItem, ListItemAvatar, ListItemText,
  Checkbox, FormControlLabel, Dialog, DialogTitle, DialogContent
} from "@mui/material";
import {
  Edit, Delete, Add, Search, DateRange, PriorityHigh, Notifications,
  Description, Category, CheckCircle, HourglassEmpty, ThumbUp, Person,
  Assignment, CalendarToday, Alarm, Close
} from "@mui/icons-material";
import { styled } from "@mui/material/styles";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

// Add axios interceptor for auth tokens
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

const StatusChip = styled(Chip)(({ status }) => ({
  backgroundColor:
    status === "COMPLETED"
      ? "#e8f5e9"
      : status === "IN_REVIEW"
        ? "#fff3e0"
        : "#e3f2fd",
  color:
    status === "COMPLETED"
      ? "#2e7d32"
      : status === "IN_REVIEW"
        ? "#e65100"
        : "#1565c0",
  fontWeight: 600,
  borderRadius: "8px"
}));

const Task = () => {
  const [tasks, setTasks] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [openModal, setOpenModal] = useState(false);
  const [editingTaskId, setEditingTaskId] = useState(null);
  const [openEmployeeDialog, setOpenEmployeeDialog] = useState(false);
  const [selectedTaskEmployees, setSelectedTaskEmployees] = useState([]);

  const { actions } = useContext(userContext);
  const hasAction = (actionName) => actions.includes(actionName);

  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success"
  });
  const [newTask, setNewTask] = useState({
    title: "",
    description: "",
    projectId: "",
    assigneeId: "",
    dueDate: "",
    priority: "MEDIUM",
    status: "TODO",
    estimatedHours: 0
  });

  // Fetch tasks and projects from backend
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [tasksRes, projectsRes] = await Promise.all([
          axios.get(`${API_BASE_URL}/alltasks`),
          axios.get(`${API_BASE_URL}/projects`)
        ]);
        setTasks(tasksRes.data);
        setProjects(projectsRes.data);
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

  const handleTabChange = (event, newTab) => {
    setTab(newTab);
  };

  const handleSearchChange = (e) => {
    setSearchQuery(e.target.value);
  };

  const handleOpenModal = () => setOpenModal(true);
  const handleCloseModal = () => setOpenModal(false);

  const handleCreateTask = async () => {
    if (!newTask.title.trim() || !newTask.projectId) {
      setSnackbar({
        open: true,
        message: "Please fill out all required fields",
        severity: "warning"
      });
      return;
    }

    if (newTask.estimatedHours < 0) {
      setSnackbar({
        open: true,
        message: "Estimated hours must be non-negative",
        severity: "error"
      });
      return;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const selectedDate = new Date(newTask.dueDate);
    
    if (selectedDate < today) {
      setSnackbar({
        open: true,
        message: "Due date cannot be in the past",
        severity: "error"
      });
      return;
    }

    try {
      let response;
      if (editingTaskId) {
        response = await axios.put(`${API_BASE_URL}/tasks/${editingTaskId}`, newTask);
        setTasks(tasks.map((t) => (t.id === editingTaskId ? response.data : t)));
      } else {
        response = await axios.post(`${API_BASE_URL}/tasks/add`, newTask);
        setTasks([...tasks, response.data]);
      }

      setSnackbar({
        open: true,
        message: `Task ${editingTaskId ? "updated" : "created"} successfully`,
        severity: "success"
      });

      setNewTask({
        title: "",
        description: "",
        projectId: "",
        assigneeId: "",
        dueDate: "",
        priority: "MEDIUM",
        status: "TODO",
        estimatedHours: 0
      });
      setEditingTaskId(null);
      handleCloseModal();
    } catch (error) {
      console.error("Error saving task:", error);
      setSnackbar({
        open: true,
        message: `Failed to ${editingTaskId ? "update" : "create"} task`,
        severity: "error"
      });
    }
  };

  const handleDeleteTask = async (id) => {
    if (window.confirm("Are you sure you want to delete this task?")) {
      try {
        await axios.delete(`${API_BASE_URL}/tasks/${id}`);
        setTasks(tasks.filter((task) => task.id !== id));
        setSnackbar({
          open: true,
          message: "Task deleted successfully",
          severity: "success"
        });
      } catch (error) {
        console.error("Error deleting task:", error);
        setSnackbar({
          open: true,
          message: "Failed to delete task",
          severity: "error"
        });
      }
    }
  };

  const handleStatusChange = async (id, newStatus) => {
    try {
      const response = await axios.patch(
        `${API_BASE_URL}/tasks/${id}/status`,
        newStatus,
        { headers: { 'Content-Type': 'text/plain' } }
      );
      setTasks(
        tasks.map((task) =>
          task.id === id ? response.data : task
        )
      );
      setSnackbar({
        open: true,
        message: "Status updated successfully",
        severity: "success"
      });
    } catch (error) {
      console.error("Error updating status:", error);
      setSnackbar({
        open: true,
        message: "Failed to update status",
        severity: "error"
      });
    }
  };

  const handleTaskCompletion = async (id, isCompleted) => {
    const newStatus = isCompleted ? "COMPLETED" : "TODO";
    await handleStatusChange(id, newStatus);
  };

  const filteredTasks = [...tasks]
  .sort((a, b) => {
    // First try sorting by createdAt date (newest first)
    if (a.createdAt && b.createdAt) {
      return new Date(b.createdAt) - new Date(a.createdAt);
    }
    // Fallback to sorting by ID (assuming higher IDs are newer)
    return b.id - a.id;
  })
  .filter((task) =>
    (tab === "ALL" || task.status === tab) &&
    task.title.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getStatusIcon = (status) => {
    switch (status) {
      case "COMPLETED":
        return <CheckCircle fontSize="small" />;
      case "IN_REVIEW":
        return <ThumbUp fontSize="small" />;
      case "IN_PROGRESS":
        return <HourglassEmpty fontSize="small" />;
      default:
        return <Assignment fontSize="small" />;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "No due date";
    const options = { year: "numeric", month: "short", day: "numeric" };
    return new Date(dateString).toLocaleDateString(undefined, options);
  };

  const getProjectName = (projectId) => {
    const project = projects.find(p => p.id === projectId);
    return project ? project.name : "No project";
  };

  const handleCloseSnackbar = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  const handleOpenEmployeeDialog = (task) => {
    if (task.project.assignments && task.project.assignments.length > 0) {
      setSelectedTaskEmployees(task.project.assignments);
      setOpenEmployeeDialog(true);
    }
  };

  const handleCloseEmployeeDialog = () => {
    setOpenEmployeeDialog(false);
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
        Task Management
      </Typography>

      {/* Add Task Button and Search */}
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
        {hasAction("CREATE_TASK") && (
          <Button
            variant="contained"
            color="primary"
            onClick={handleOpenModal}
            startIcon={<Add />}
            sx={{
              borderRadius: "10px",
              px: 3,
              py: 1,
              textTransform: "none"
            }}
          >
            New Task
          </Button>
        )}
        <TextField
          variant="outlined"
          size="small"
          placeholder="Search tasks..."
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

      {/* Tabs for Task Status */}
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
              <Badge badgeContent={tasks.length} color="primary" />
              All
            </Box>
          }
          value="ALL"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Assignment fontSize="small" />
              To Do
              <Badge
                badgeContent={tasks.filter((t) => t.status === "TODO").length}
                color="primary"
              />
            </Box>
          }
          value="TODO"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <HourglassEmpty fontSize="small" />
              In Progress
              <Badge
                badgeContent={tasks.filter((t) => t.status === "IN_PROGRESS").length}
                color="primary"
              />
            </Box>
          }
          value="IN_PROGRESS"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <ThumbUp fontSize="small" />
              In Review
              <Badge
                badgeContent={tasks.filter((t) => t.status === "IN_REVIEW").length}
                color="primary"
              />
            </Box>
          }
          value="IN_REVIEW"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CheckCircle fontSize="small" />
              Completed
              <Badge
                badgeContent={tasks.filter((t) => t.status === "COMPLETED").length}
                color="primary"
              />
            </Box>
          }
          value="COMPLETED"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
      </Tabs>

      {/* Task Cards */}
      {filteredTasks.length === 0 ? (
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
            No tasks found. Create a new task to get started!
          </Typography>
        </Box>
      ) : (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, 1fr)",
              lg: "repeat(3, 1fr)"
            },
            gap: 3
          }}
        >
          {filteredTasks.map((task) => (
            <StyledCard key={task.id}>
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
                    {task.title}
                  </Typography>
                  <StatusChip
                    label={task.status.toLowerCase().replace('_', ' ')}
                    status={task.status}
                    icon={getStatusIcon(task.status)}
                    size="small"
                  />
                </Box>

                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}
                >
                  <Category fontSize="small" /> {task.project.name}
                </Typography>

                <Typography
                  variant="body2"
                  sx={{ mb: 2 }}
                >
                  {task.description || "No description"}
                </Typography>

                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: "flex", alignItems: "center", gap: 1 }}
                  >
                    <CalendarToday fontSize="small" />
                    {formatDate(task.dueDate)}
                  </Typography>
                  <Chip
                    label={task.priority.toLowerCase()}
                    size="small"
                    sx={{
                      backgroundColor:
                        task.priority === "HIGH"
                          ? "#ffebee"
                          : task.priority === "MEDIUM"
                            ? "#fff3e0"
                            : "#e8f5e9",
                      color:
                        task.priority === "HIGH"
                          ? "#c62828"
                          : task.priority === "MEDIUM"
                            ? "#e65100"
                            : "#2e7d32"
                    }}
                  />
                </Box>

                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mt: 2 }}>
                  <Box 
                    sx={{ 
                      display: "flex", 
                      alignItems: "center", 
                      gap: 1,
                      cursor: task.project.assignments?.length > 0 ? "pointer" : "default"
                    }}
                    onClick={() => handleOpenEmployeeDialog(task)}
                  >
                    <Avatar sx={{ width: 24, height: 24 }}>
                      <Person fontSize="small" />
                    </Avatar>
                    <Typography variant="body2">
                      {task.project.assignments && task.project.assignments.length > 0
                        ? `Assigned (${task.project.assignments.length})`
                        : "Unassigned"}
                    </Typography>
                  </Box>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <Alarm fontSize="small" />
                    <Typography variant="body2">
                      {task.estimatedHours}h
                    </Typography>
                  </Box>
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
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={task.status === "COMPLETED"}
                        onChange={(e) => handleTaskCompletion(task.id, e.target.checked)}
                        color="primary"
                      />
                    }
                    label="Completed"
                  />
                </Box>
                <Box>
                  {hasAction("UPDATE_TASK") && (
                    <Tooltip title="Edit">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => {
                          setEditingTaskId(task.id);
                          setNewTask({
                            title: task.title,
                            description: task.description,
                            projectId: task.project.id,
                            assigneeId: task.assigneeId || "",
                            dueDate: task.dueDate ? task.dueDate.split("T")[0] : "",
                            priority: task.priority,
                            status: task.status,
                            estimatedHours: task.estimatedHours
                          });
                          setOpenModal(true);
                        }}
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}

                  {hasAction("DELETE_TASK") && (
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDeleteTask(task.id)}
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

      {/* Create Task Modal */}
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
            {editingTaskId ? "Edit Task" : "Create New Task"}
          </Typography>

          <Box sx={{ mb: 3 }}>
            <TextField
              fullWidth
              label="Task Title"
              variant="outlined"
              value={newTask.title}
              onChange={(e) =>
                setNewTask({ ...newTask, title: e.target.value })
              }
              sx={{ mb: 2 }}
              required
            />

            <FormControl fullWidth sx={{ mb: 2 }} required>
              <InputLabel id="project-select-label">Project</InputLabel>
              <Select
                labelId="project-select-label"
                value={newTask.projectId}
                label="Project"
                onChange={(e) => setNewTask({ ...newTask, projectId: e.target.value })}
              >
                {projects.map((project) => (
                  <MenuItem key={project.id} value={project.id}>
                    {project.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              fullWidth
              label="Description"
              multiline
              rows={3}
              value={newTask.description}
              onChange={(e) =>
                setNewTask({ ...newTask, description: e.target.value })
              }
              sx={{ mb: 2 }}
            />

            <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
              <TextField
                fullWidth
                label="Due Date"
                type="date"
                value={newTask.dueDate}
                onChange={(e) =>
                  setNewTask({ ...newTask, dueDate: e.target.value })
                }
                InputLabelProps={{ shrink: true }}
                inputProps={{
                  min: new Date().toISOString().split('T')[0]
                }}
              />
              <TextField
                fullWidth
                label="Estimated Hours"
                type="number"
                value={newTask.estimatedHours}
                onChange={(e) =>
                  setNewTask({ ...newTask, estimatedHours: Math.max(0, e.target.value) })
                }
                inputProps={{
                  min: 0
                }}
              />
            </Box>

            <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select
                  label="Priority"
                  value={newTask.priority}
                  onChange={(e) =>
                    setNewTask({ ...newTask, priority: e.target.value })
                  }
                >
                  <MenuItem value="HIGH">High</MenuItem>
                  <MenuItem value="MEDIUM">Medium</MenuItem>
                  <MenuItem value="LOW">Low</MenuItem>
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                  label="Status"
                  value={newTask.status}
                  onChange={(e) =>
                    setNewTask({ ...newTask, status: e.target.value })
                  }
                >
                  <MenuItem value="TODO">To Do</MenuItem>
                  <MenuItem value="IN_PROGRESS">In Progress</MenuItem>
                  <MenuItem value="IN_REVIEW">In Review</MenuItem>
                  <MenuItem value="COMPLETED">Completed</MenuItem>
                </Select>
              </FormControl>
            </Box>
          </Box>

          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between"
            }}
          >
            <Button variant="contained" color="primary" onClick={handleCreateTask}>
              {editingTaskId ? "Update Task" : "Create Task"}
            </Button>
            <Button variant="outlined" color="secondary" onClick={handleCloseModal}>
              Cancel
            </Button>
          </Box>
        </Box>
      </Modal>

      {/* Employee Dialog */}
      <Dialog open={openEmployeeDialog} onClose={handleCloseEmployeeDialog}>
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">Assigned Employees</Typography>
            <IconButton onClick={handleCloseEmployeeDialog}>
              <Close />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <List>
            {selectedTaskEmployees.map((employee) => (
              <ListItem key={employee.empId}>
                <ListItemAvatar>
                  <Avatar>
                    <Person />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={employee.empName}
                  secondary={`Employee ID: ${employee.empId}`}
                />
              </ListItem>
            ))}
          </List>
        </DialogContent>
      </Dialog>

      {/* Snackbar for notifications */}
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
    </Box>
  );
};

export default Task;