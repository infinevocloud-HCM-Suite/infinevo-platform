import React, { useEffect, useState, useContext } from "react";
import {
  TextField,
  MenuItem,
  Select,
  Button,
  Grid,
  Typography,
  Paper,
  FormControl,
  InputLabel,
  IconButton,
  Chip,
  Box,
  CircularProgress,
  Snackbar,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Divider,
  Tooltip,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import {
  format,
  addDays,
  startOfWeek,
  endOfWeek,
  isWeekend,
} from "date-fns";
import { useNavigate } from "react-router-dom";
import ArrowBackIosIcon from "@mui/icons-material/ArrowBackIos";
import ArrowForwardIosIcon from "@mui/icons-material/ArrowForwardIos";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { userContext } from '../context/ContextProvider';

const TimesheetForm = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  
  const [weekStart, setWeekStart] = useState(
    startOfWeek(new Date(), { weekStartsOn: 1 })
  );

  const [projects, setProjects] = useState([
    {
      projectId: "",
      tasks: [
        {
          taskId: "",
          hours: Array(7).fill(0),
          descriptions: Array(7).fill(""),
        },
      ],
    },
  ]);

  const [initialData, setInitialData] = useState({
    employeeId: "",
    employeeName: "",
    availableProjects: [],
    availableTasks: {},
  });

  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success",
  });
  const [dataLoaded, setDataLoaded] = useState(false);

  const daysOfWeek = Array.from({ length: 7 }).map((_, index) => {
    const dayDate = addDays(weekStart, index);
    return {
      date: dayDate,
      label: format(dayDate, "EEE dd/MM"),
      dayName: format(dayDate, "EEEE").toUpperCase(),
      isWeekend: isWeekend(dayDate),
      dateString: format(dayDate, "yyyy-MM-dd"),
    };
  });

  const selectedProjectIds = projects.map(project => project.projectId).filter(id => id);

  const getAvailableProjectsForDropdown = () => {
    return initialData.availableProjects.filter(
      project => !selectedProjectIds.includes(project.projectId)
    );
  };

  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        setLoading(true);
        const response = await axios.get(`${API_BASE_URL}/api/timesheets/get-initial-data`);
        
        const tasksData = {};
        for (const project of response.data.projectNameIds) {
          const tasksResponse = await axios.get(
            `${API_BASE_URL}/api/timesheets/get-tasks/${project.projectId}`
          );
          tasksData[project.projectId] = tasksResponse.data;
        }

        setInitialData({
          employeeId: response.data.employeeId,
          employeeName: response.data.employeeName,
          availableProjects: response.data.projectNameIds,
          availableTasks: tasksData,
        });

        setDataLoaded(true);
      } catch (error) {
        console.error("Error fetching initial data:", error);
        setSnackbar({
          open: true,
          message: "Failed to load initial data",
          severity: "error",
        });
      } finally {
        setLoading(false);
      }
    };

    if (!dataLoaded) {
      fetchInitialData();
    }
  }, [dataLoaded]);

  const handleWeekNavigation = (direction) => {
    setWeekStart(addDays(weekStart, direction * 7));
  };

  const addNewProject = () => {
    setProjects([
      ...projects,
      {
        projectId: "",
        tasks: [
          {
            taskId: "",
            hours: Array(7).fill(0),
            descriptions: Array(7).fill(""),
          },
        ],
      },
    ]);
  };

  const addNewTask = (projectIndex) => {
    const newProjects = [...projects];
    newProjects[projectIndex].tasks.push({
      taskId: "",
      hours: Array(7).fill(0),
      descriptions: Array(7).fill(""),
    });
    setProjects(newProjects);
  };

  const removeProject = (projectIndex) => {
    if (projects.length <= 1) return;
    const newProjects = [...projects];
    newProjects.splice(projectIndex, 1);
    setProjects(newProjects);
  };

  const removeTask = (projectIndex, taskIndex) => {
    const newProjects = [...projects];
    if (newProjects[projectIndex].tasks.length <= 1) return;
    newProjects[projectIndex].tasks.splice(taskIndex, 1);
    setProjects(newProjects);
  };

  const handleProjectChange = (projectIndex, projectId) => {
    const newProjects = [...projects];
    newProjects[projectIndex] = {
      projectId: projectId,
      tasks: [
        {
          taskId: "",
          hours: Array(7).fill(0),
          descriptions: Array(7).fill(""),
        },
      ],
    };
    setProjects(newProjects);
  };

  const handleTaskChange = (projectIndex, taskIndex, taskId) => {
    const newProjects = [...projects];
    newProjects[projectIndex].tasks[taskIndex].taskId = taskId;
    setProjects(newProjects);
  };

  const handleHoursChange = (projectIndex, taskIndex, dayIndex, value) => {
    const newProjects = [...projects];
    const numValue = Number(value);

    if (isNaN(numValue)) {
      newProjects[projectIndex].tasks[taskIndex].hours[dayIndex] = "";
    } else if (numValue < 0) {
      newProjects[projectIndex].tasks[taskIndex].hours[dayIndex] = 0;
    } else if (numValue > 24) {
      newProjects[projectIndex].tasks[taskIndex].hours[dayIndex] = 24;
    } else {
      newProjects[projectIndex].tasks[taskIndex].hours[dayIndex] = Math.round(numValue * 4) / 4;
    }

    setProjects(newProjects);
  };

  const handleDescriptionChange = (projectIndex, taskIndex, dayIndex, value) => {
    const newProjects = [...projects];
    newProjects[projectIndex].tasks[taskIndex].descriptions[dayIndex] = value;
    setProjects(newProjects);
  };

  const validateForm = () => {
    let newErrors = {};
    let isValid = true;

    projects.forEach((project, projectIndex) => {
      if (!project.projectId) {
        newErrors[`project-${projectIndex}`] = "Project is required";
        isValid = false;
      }

      project.tasks.forEach((task, taskIndex) => {
        if (!task.taskId) {
          newErrors[`task-${projectIndex}-${taskIndex}`] = "Task is required";
          isValid = false;
        }

        if (task.hours.every((hour) => !hour || hour === 0)) {
          newErrors[`hours-${projectIndex}-${taskIndex}`] = "At least one day must have hours entered";
          isValid = false;
        } else if (task.hours.some((hour) => hour < 0 || hour > 24)) {
          newErrors[`hours-${projectIndex}-${taskIndex}`] = "Hours must be between 0 and 24";
          isValid = false;
        }

        // Validate descriptions when hours are entered
        task.hours.forEach((hour, dayIndex) => {
          if (hour > 0 && !task.descriptions[dayIndex]) {
            newErrors[`desc-${projectIndex}-${taskIndex}-${dayIndex}`] = "Description is required when hours are entered";
            isValid = false;
          }
        });
      });
    });

    setErrors(newErrors);
    return isValid;
  };

  const handleAddTimesheet = async () => {
  if (!validateForm()) return;

  try {
    setLoading(true);
    
    // Prepare the complete timesheet payload
    const timesheetPayload = {
      employeeId: initialData.employeeId,
      employeeName: initialData.employeeName,
      weekStartDate: format(weekStart, "yyyy-MM-dd"),
      weekEndDate: format(addDays(weekStart, 6), "yyyy-MM-dd"), // Added weekEndDate
      status: "DRAFT",
      projects: projects.map(project => {
        const selectedProject = initialData.availableProjects.find(
          p => p.projectId === project.projectId
        );
        
        return {
          projectId: project.projectId,
          projectName: selectedProject?.projectName || "",
          tasks: project.tasks.map(task => {
            const selectedTask = initialData.availableTasks[project.projectId]?.find(
              t => t.taskId === task.taskId
            );
            
            return {
              taskId: task.taskId,
              taskName: selectedTask?.taskName || "",
              days: daysOfWeek.map((day, dayIndex) => ({
                date: day.dateString,
                dayName: day.dayName.toUpperCase(),
                hours: task.hours[dayIndex] || 0,
                description: task.descriptions[dayIndex] || ""
              }))
            };
          })
        };
      })
    };

    // Send the complete timesheet in one request
    await axios.post(`${API_BASE_URL}/api/timesheets`, timesheetPayload);

    navigate(`/${role}/my-timesheet-detail`, {
      state: {
        refresh: true,
        weekRange: `${format(weekStart, "dd MMM")} - ${format(
          addDays(weekStart, 6),
          "dd MMM yyyy"
        )}`,
      },
    });
  } catch (error) {
    console.error("Error adding timesheet:", error);
    setSnackbar({
      open: true,
      message: error.response?.data?.message || "Failed to add timesheet",
      severity: "error",
    });
  } finally {
    setLoading(false);
  }
};

  const handleCloseSnackbar = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  return (
    <>
      <Paper elevation={2} sx={{ 
        p: 3, 
        width: "100%", 
        maxWidth: "95vw", 
        mx: "auto", 
        overflowX: "auto",
        borderRadius: '12px',
        backgroundColor: '#fafafa'
      }}>
        <Grid container justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
          <Typography variant="h5" sx={{ 
            color: "#2c3e50", 
            fontWeight: "600",
            fontSize: '1.5rem'
          }}>
            Add New Timesheet
          </Typography>

          <Box sx={{ 
            display: "flex", 
            alignItems: "center",
            backgroundColor: '#f0f4f8',
            borderRadius: '8px',
            p: 1,
            boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
          }}>
            <Tooltip title="Previous week">
              <IconButton
                onClick={() => handleWeekNavigation(-1)}
                size="small"
                disabled={loading}
                sx={{
                  '&:hover': {
                    backgroundColor: '#e0e6ed'
                  }
                }}
              >
                <ArrowBackIosIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            
            <Typography variant="subtitle1" sx={{ 
              mx: 2,
              fontWeight: '500',
              color: '#3a4a63'
            }}>
              {format(weekStart, "dd MMM")} - {format(
                addDays(weekStart, 6),
                "dd MMM yyyy"
              )}
            </Typography>
            
            <Tooltip title="Next week">
              <IconButton
                onClick={() => handleWeekNavigation(1)}
                size="small"
                disabled={loading}
                sx={{
                  '&:hover': {
                    backgroundColor: '#e0e6ed'
                  }
                }}
              >
                <ArrowForwardIosIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Chip
              label="Draft"
              color="info"
              sx={{ 
                ml: 2,
                fontWeight: '500',
                fontSize: '0.75rem'
              }}
            />
          </Box>
        </Grid>

        <Divider sx={{ my: 2 }} />

        {loading && !dataLoaded ? (
          <Box sx={{ 
            display: "flex", 
            justifyContent: "center", 
            p: 4,
            backgroundColor: '#ffffff',
            borderRadius: '8px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
          }}>
            <CircularProgress />
          </Box>
        ) : (
          <Grid container spacing={2}>
            <Grid item xs={12} md={2}>
              <TextField
                label="Employee ID"
                value={initialData.employeeId}
                fullWidth
                disabled
                size="small"
                sx={{
                  '& .MuiOutlinedInput-root': {
                    backgroundColor: '#f5f7fa',
                    borderRadius: '6px'
                  }
                }}
              />
            </Grid>

            <Grid item xs={12} sx={{ mt: 2 }}>
              <TableContainer component={Paper} sx={{ 
                width: "100%", 
                overflowX: "auto",
                borderRadius: '8px',
                border: '1px solid #e0e6ed',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
              }}>
                <Table sx={{ minWidth: 1200 }}>
                  <TableHead>
                    <TableRow sx={{ backgroundColor: '#f0f4f8' }}>
                      <TableCell sx={{ 
                        width: '220px', 
                        minWidth: '220px', 
                        fontWeight: '600',
                        color: '#3a4a63'
                      }}>Projects</TableCell>
                      <TableCell sx={{ 
                        width: '220px', 
                        minWidth: '220px', 
                        fontWeight: '600',
                        color: '#3a4a63'
                      }}>Tasks</TableCell>
                      {daysOfWeek.map((day, idx) => (
                        <TableCell key={idx} sx={{ 
                          color: day.isWeekend ? "#e74c3c" : "#3a4a63",
                          textAlign: 'center',
                          width: '180px',
                          minWidth: '180px',
                          padding: '8px',
                          fontWeight: '600'
                        }}>
                          {day.label}
                        </TableCell>
                      ))}
                      <TableCell sx={{ 
                        width: '60px', 
                        minWidth: '60px', 
                        padding: '8px'
                      }}></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {projects.map((project, projectIndex) => (
                      <React.Fragment key={projectIndex}>
                        {project.tasks.map((task, taskIndex) => (
                          <TableRow 
                            key={`${projectIndex}-${taskIndex}`}
                            sx={{ 
                              '&:nth-of-type(even)': {
                                backgroundColor: '#f9fbfd'
                              },
                              '&:hover': {
                                backgroundColor: '#f0f4f8'
                              }
                            }}
                          >
                            {taskIndex === 0 ? (
                              <TableCell 
                                rowSpan={project.tasks.length} 
                                sx={{ 
                                  width: '220px', 
                                  minWidth: '220px',
                                  borderRight: '1px solid #e0e6ed',
                                  verticalAlign: 'top',
                                  position: 'relative'
                                }}
                              >
                                <Box sx={{ 
                                  position: 'sticky',
                                  top: 0,
                                  display: 'flex', 
                                  alignItems: 'flex-start',
                                  gap: 1,
                                  pt: 1
                                }}>
                                  <FormControl 
                                    fullWidth 
                                    error={!!errors[`project-${projectIndex}`]} 
                                    size="small"
                                    sx={{ flex: 1 }}
                                  >
                                    <InputLabel sx={{ fontSize: '0.875rem' }}>Project</InputLabel>
                                    <Select
                                      value={project.projectId || ""}
                                      onChange={(e) => handleProjectChange(projectIndex, e.target.value)}
                                      label="Project"
                                      disabled={loading}
                                      sx={{
                                        backgroundColor: '#ffffff',
                                        borderRadius: '6px',
                                        '& .MuiSelect-select': {
                                          padding: '8px 12px'
                                        }
                                      }}
                                    >
                                      <MenuItem value="" sx={{ fontSize: '0.875rem' }}>Select Project</MenuItem>
                                      {getAvailableProjectsForDropdown().concat(
                                        initialData.availableProjects.filter(
                                          p => p.projectId === project.projectId
                                        )
                                      ).map((proj) => (
                                        <MenuItem 
                                          key={proj.projectId} 
                                          value={proj.projectId}
                                          sx={{ fontSize: '0.875rem' }}
                                        >
                                          {proj.projectName}
                                        </MenuItem>
                                      ))}
                                    </Select>
                                    {errors[`project-${projectIndex}`] && (
                                      <Typography variant="caption" color="error" sx={{ fontSize: '0.75rem' }}>
                                        {errors[`project-${projectIndex}`]}
                                      </Typography>
                                    )}
                                  </FormControl>
                                  {projectIndex === projects.length - 1 && (
                                    <Tooltip title="Add project">
                                      <IconButton 
                                        onClick={addNewProject} 
                                        color="primary"
                                        disabled={loading}
                                        size="small"
                                        sx={{ 
                                          backgroundColor: '#3498db',
                                          color: 'white',
                                          '&:hover': {
                                            backgroundColor: '#2980b9'
                                          },
                                          mt: 0.5
                                        }}
                                      >
                                        <AddIcon fontSize="small" />
                                      </IconButton>
                                    </Tooltip>
                                  )}
                                </Box>
                              </TableCell>
                            ) : null}
                            <TableCell sx={{ 
                              width: '220px', 
                              minWidth: '220px',
                              borderRight: '1px solid #e0e6ed',
                              verticalAlign: 'top',
                              position: 'relative'
                            }}>
                              <Box sx={{ 
                                position: 'sticky',
                                top: 0,
                                display: 'flex', 
                                alignItems: 'flex-start',
                                gap: 1,
                                pt: 1
                              }}>
                                <FormControl 
                                  fullWidth 
                                  error={!!errors[`task-${projectIndex}-${taskIndex}`]} 
                                  size="small"
                                  sx={{ flex: 1 }}
                                >
                                  <InputLabel sx={{ fontSize: '0.875rem' }}>Task</InputLabel>
                                  <Select
                                    value={task.taskId || ""}
                                    onChange={(e) => handleTaskChange(projectIndex, taskIndex, e.target.value)}
                                    label="Task"
                                    disabled={!project.projectId || loading}
                                    sx={{
                                      backgroundColor: '#ffffff',
                                      borderRadius: '6px',
                                      '& .MuiSelect-select': {
                                        padding: '8px 12px'
                                      }
                                    }}
                                  >
                                    <MenuItem value="" sx={{ fontSize: '0.875rem' }}>Select Task</MenuItem>
                                    {initialData.availableTasks[project.projectId]?.map((t) => (
                                      <MenuItem 
                                        key={t.taskId} 
                                        value={t.taskId}
                                        sx={{ fontSize: '0.875rem' }}
                                      >
                                        {t.taskName}
                                      </MenuItem>
                                    ))}
                                  </Select>
                                  {errors[`task-${projectIndex}-${taskIndex}`] && (
                                    <Typography variant="caption" color="error" sx={{ fontSize: '0.75rem' }}>
                                      {errors[`task-${projectIndex}-${taskIndex}`]}
                                    </Typography>
                                  )}
                                </FormControl>
                                {taskIndex === project.tasks.length - 1 && (
                                  <Tooltip title="Add task">
                                    <IconButton
                                      onClick={() => addNewTask(projectIndex)}
                                      color="primary"
                                      size="small"
                                      disabled={loading || !project.projectId}
                                      sx={{ 
                                        backgroundColor: '#3498db',
                                        color: 'white',
                                        '&:hover': {
                                          backgroundColor: '#2980b9'
                                        },
                                        mt: 0.5
                                      }}
                                    >
                                      <AddIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                )}
                              </Box>
                            </TableCell>
                            {task.hours.map((hour, dayIndex) => (
                              <TableCell 
                                key={dayIndex} 
                                sx={{ 
                                  width: '180px', 
                                  minWidth: '180px', 
                                  padding: '8px',
                                  borderRight: dayIndex < 6 ? '1px solid #e0e6ed' : 'none'
                                }}
                              >
                                <Box sx={{ 
                                  display: 'flex', 
                                  flexDirection: 'column', 
                                  gap: '8px',
                                  border: '1px solid #e0e6ed',
                                  borderRadius: '6px',
                                  p: 1,
                                  backgroundColor: daysOfWeek[dayIndex].isWeekend ? '#fff5f5' : '#ffffff'
                                }}>
                                  <TextField
                                    type="number"
                                    value={hour}
                                    onChange={(e) => handleHoursChange(projectIndex, taskIndex, dayIndex, e.target.value)}
                                    inputProps={{
                                      min: 0,
                                      max: 24,
                                      step: 0.25,
                                    }}
                                    size="small"
                                    disabled={loading}
                                    sx={{
                                      width: '100%',
                                      "& .MuiInputBase-input": {
                                        textAlign: "center",
                                        padding: '6px',
                                        fontWeight: '500',
                                        color: '#2c3e50'
                                      },
                                      "& .MuiOutlinedInput-root": {
                                        "& fieldset": {
                                          borderColor: '#dfe6f0',
                                        },
                                        "&:hover fieldset": {
                                          borderColor: '#b8c4d4',
                                        },
                                      }
                                    }}
                                    variant="outlined"
                                  />
                                  <TextField
                                    value={task.descriptions[dayIndex]}
                                    onChange={(e) => handleDescriptionChange(projectIndex, taskIndex, dayIndex, e.target.value)}
                                    placeholder="Description"
                                    size="small"
                                    multiline
                                    rows={2}
                                    error={!!errors[`desc-${projectIndex}-${taskIndex}-${dayIndex}`]}
                                    helperText={errors[`desc-${projectIndex}-${taskIndex}-${dayIndex}`]}
                                    sx={{
                                      width: '100%',
                                      "& .MuiInputBase-input": {
                                        padding: '6px',
                                        fontSize: '0.75rem',
                                        color: '#4a5568'
                                      },
                                      "& .MuiOutlinedInput-root": {
                                        "& fieldset": {
                                          borderColor: hour > 0 ? (task.descriptions[dayIndex] ? '#dfe6f0' : '#f44336') : '#dfe6f0',
                                        },
                                        "&:hover fieldset": {
                                          borderColor: hour > 0 ? (task.descriptions[dayIndex] ? '#b8c4d4' : '#f44336') : '#b8c4d4',
                                        },
                                      }
                                    }}
                                    variant="outlined"
                                  />
                                </Box>
                              </TableCell>
                            ))}
                            <TableCell sx={{ 
                              width: '60px', 
                              minWidth: '60px', 
                              padding: '8px'
                            }}>
                              <Box sx={{ 
                                display: 'flex', 
                                justifyContent: 'center' 
                              }}>
                                {taskIndex === 0 && projects.length > 1 ? (
                                  <Tooltip title="Remove project">
                                    <IconButton 
                                      onClick={() => removeProject(projectIndex)}
                                      disabled={loading}
                                      color="error"
                                      size="small"
                                      sx={{ 
                                        backgroundColor: '#fee2e2',
                                        color: '#dc2626',
                                        '&:hover': {
                                          backgroundColor: '#fecaca'
                                        }
                                      }}
                                    >
                                      <DeleteIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                ) : project.tasks.length > 1 ? (
                                  <Tooltip title="Remove task">
                                    <IconButton 
                                      onClick={() => removeTask(projectIndex, taskIndex)}
                                      disabled={loading}
                                      color="error"
                                      size="small"
                                      sx={{ 
                                        backgroundColor: '#fee2e2',
                                        color: '#dc2626',
                                        '&:hover': {
                                          backgroundColor: '#fecaca'
                                        }
                                      }}
                                    >
                                      <DeleteIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                ) : null}
                              </Box>
                            </TableCell>
                          </TableRow>
                        ))}
                      </React.Fragment>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Grid>

            <Grid item xs={12} sx={{ mt: 3 }}>
              <Box sx={{ 
                display: 'flex', 
                justifyContent: 'flex-end',
                gap: 2,
                pt: 2,
                borderTop: '1px solid #e0e6ed'
              }}>
                <Button
                  variant="outlined"
                  color="secondary"
                  onClick={() => navigate(-1)}
                  disabled={loading}
                  sx={{
                    px: 4,
                    py: 1,
                    fontSize: '0.875rem',
                    fontWeight: '500',
                    textTransform: 'none',
                    borderRadius: '6px',
                    borderColor: '#d1d9e6',
                    color: '#4a5568',
                    '&:hover': {
                      backgroundColor: '#f0f4f8',
                      borderColor: '#b8c4d4'
                    }
                  }}
                >
                  Cancel
                </Button>
                <Button
                  variant="contained"
                  color="primary"
                  onClick={handleAddTimesheet}
                  sx={{ 
                    px: 4,
                    py: 1,
                    fontSize: '0.875rem',
                    fontWeight: '500',
                    textTransform: 'none',
                    borderRadius: '6px',
                    backgroundColor: '#3498db',
                    '&:hover': {
                      backgroundColor: '#2980b9'
                    }
                  }}
                  disabled={loading}
                >
                  {loading ? (
                    <CircularProgress size={20} color="inherit" />
                  ) : (
                    "Add Timesheet"
                  )}
                </Button>
              </Box>
            </Grid>
          </Grid>
        )}
      </Paper>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ 
            width: "100%",
            borderRadius: '8px',
            boxShadow: '0 2px 10px rgba(0,0,0,0.1)'
          }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export default TimesheetForm;