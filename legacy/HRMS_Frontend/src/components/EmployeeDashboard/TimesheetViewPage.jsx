import React, { useEffect, useState } from "react";
import {
  Paper,
  Typography,
  Grid,
  Box,
  Button,
  Divider,
  Chip,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Card,
  CardContent,
  Avatar,
  Stack,
} from "@mui/material";
import { format, parseISO } from "date-fns";
import { useNavigate, useLocation } from "react-router-dom";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import {
  CheckCircle as CheckCircleIcon,
  Pending as PendingIcon,
  Drafts as DraftsIcon,
  Cancel as CancelIcon,
  Assignment as AssignmentIcon,
  Work as WorkIcon,
  Task as TaskIcon,
  AccessTime as AccessTimeIcon,
  CalendarToday as CalendarIcon,
  Person as PersonIcon,
  Description as DescriptionIcon,
  NoteAdd as NoteAddIcon,
} from "@mui/icons-material";

const TimesheetViewPage = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const location = useLocation();
  const [timesheet, setTimesheet] = useState(location.state?.entry || null);
  const [loading, setLoading] = useState(false);

  const timesheetId = location.state?.timesheetId;

  useEffect(() => {
    if (!timesheet && timesheetId) {
      setLoading(true);
      axios
        .get(`${API_BASE_URL}/api/timesheets/${timesheetId}`)
        .then((res) => {
          const transformedData = transformTimesheetData(res.data);
          setTimesheet(transformedData);
        })
        .catch((err) => {
          console.error("Failed to fetch timesheet data", err);
        })
        .finally(() => setLoading(false));
    }
  }, [timesheet, timesheetId]);

  const transformTimesheetData = (data) => {
    if (!data) return null;
    
    if (data.projects && Array.isArray(data.projects)) {
      return data;
    }
    
    return {
      ...data,
      projects: [
        {
          projectId: data.projectId,
          projectName: data.projectName,
          status: data.status,
          tasks: [
            {
              taskId: data.taskId,
              taskName: data.taskName,
              days: [
                { dayName: 'MON', date: data.weekStart, hours: data.mondayHours || 0, description: data.comments },
                { dayName: 'TUE', date: addDays(data.weekStart, 1), hours: data.tuesdayHours || 0, description: data.comments },
                { dayName: 'WED', date: addDays(data.weekStart, 2), hours: data.wednesdayHours || 0, description: data.comments },
                { dayName: 'THU', date: addDays(data.weekStart, 3), hours: data.thursadyHours || 0, description: data.comments },
                { dayName: 'FRI', date: addDays(data.weekStart, 4), hours: data.fridayHours || 0, description: data.comments },
                { dayName: 'SAT', date: addDays(data.weekStart, 5), hours: data.saturdayHours || 0, description: data.comments },
                { dayName: 'SUN', date: addDays(data.weekStart, 6), hours: data.sundayHours || 0, description: data.comments }
              ]
            }
          ]
        }
      ]
    };
  };

  const addDays = (dateString, days) => {
    const date = new Date(dateString);
    date.setDate(date.getDate() + days);
    return date;
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case "APPROVED":
        return <CheckCircleIcon color="success" sx={{ mr: 1 }} />;
      case "SUBMITTED":
        return <PendingIcon color="info" sx={{ mr: 1 }} />;
      case "DRAFT":
        return <DraftsIcon color="warning" sx={{ mr: 1 }} />;
      case "REJECTED":
        return <CancelIcon color="error" sx={{ mr: 1 }} />;
      case "CANCELLED":
        return <CancelIcon color="error" sx={{ mr: 1 }} />;
      default:
        return <PendingIcon color="info" sx={{ mr: 1 }} />;
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case "APPROVED":
        return "success.main";
      case "SUBMITTED":
        return "info.main";
      case "DRAFT":
        return "warning.main";
      case "REJECTED":
        return "error.main";
      case "CANCELLED":
        return "error.main";
      default:
        return "text.secondary";
    }
  };

  if (loading) {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!timesheet) {
    return (
      <Box sx={{ p: 3, textAlign: "center" }}>
        <Typography variant="h6">No timesheet data found</Typography>
        <Button
          variant="contained"
          sx={{ mt: 2 }}
          onClick={() => navigate(`/${role}/my-timesheet-detail`)}
        >
          Back to Timesheets
        </Button>
      </Box>
    );
  }

  const calculateTotalHours = () => {
    if (!timesheet.projects) return 0;
    
    return timesheet.projects.reduce((total, project) => {
      return total + (project.tasks?.reduce((projectTotal, task) => {
        return projectTotal + (task.days?.reduce((taskTotal, day) => taskTotal + (day.hours || 0), 0) || 0);
      }, 0) || 0);
    }, 0);
  };

  const totalTimesheetHours = calculateTotalHours();

  const daysOfWeek = [
    { id: 'MON', name: 'Monday' },
    { id: 'TUE', name: 'Tuesday' },
    { id: 'WED', name: 'Wednesday' },
    { id: 'THU', name: 'Thursday' },
    { id: 'FRI', name: 'Friday' },
    { id: 'SAT', name: 'Saturday' },
    { id: 'SUN', name: 'Sunday' },
  ];

  return (
    <Paper elevation={3} sx={{ p: 3, maxWidth: 1200, mx: "auto", borderRadius: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: "bold", color: "primary.main" }}>
          Timesheet Details
        </Typography>
        <Button
          variant="outlined"
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate(`/${role}/my-timesheet-detail`)}
          sx={{ borderRadius: 2 }}
        >
          Back to List
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Basic Information */}
        <Grid item xs={12}>
          <Card elevation={2} sx={{ borderRadius: 3 }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Avatar sx={{ bgcolor: 'primary.main', mr: 2 }}>
                  <DescriptionIcon />
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: "bold" }}>
                  Basic Information
                </Typography>
              </Box>
              <Divider sx={{ mb: 3 }} />
              
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <CalendarIcon color="primary" fontSize="small" />
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Timesheet ID
                      </Typography>
                      <Typography variant="body1" fontWeight="medium">
                        {timesheet.timesheetId}
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <PersonIcon color="primary" fontSize="small" />
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Employee
                      </Typography>
                      <Typography variant="body1" fontWeight="medium">
                        {timesheet.employeeName || timesheet.empName} ({timesheet.employeeId || timesheet.empId})
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <AccessTimeIcon color="primary" fontSize="small" />
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Week Range
                      </Typography>
                      <Typography variant="body1" fontWeight="medium">
                        {format(new Date(timesheet.weekStartDate || timesheet.weekStart), "dd MMM yyyy")} -{" "}
                        {format(new Date(timesheet.weekEndDate || timesheet.weekEnd), "dd MMM yyyy")}
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    {getStatusIcon(timesheet.status)}
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Timesheet Status
                      </Typography>
                      <Chip
                        label={timesheet.status}
                        color={
                          timesheet.status === "SUBMITTED"
                            ? "info"
                            : timesheet.status === "DRAFT"
                            ? "warning"
                            : timesheet.status === "APPROVED"
                            ? "success"
                            : timesheet.status === "REJECTED"
                            ? "error"
                            : timesheet.status === "CANCELLED"
                            ? "error"
                            : "default"
                        }
                        size="medium"
                        sx={{ fontWeight: 'bold' }}
                      />
                    </Box>
                  </Stack>
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <NoteAddIcon color="primary" fontSize="small" />
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Created At
                      </Typography>
                      <Typography variant="body1" fontWeight="medium">
                        {timesheet.createdAt ? format(new Date(timesheet.createdAt), "dd MMM yyyy, h:mm a") : 'N/A'}
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <CheckCircleIcon color="primary" fontSize="small" />
                    <Box>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Submitted At
                      </Typography>
                      <Typography variant="body1" fontWeight="medium">
                        {timesheet.submitted_at ? format(new Date(timesheet.submitted_at), "dd MMM yyyy, h:mm a") : 'Not submitted'}
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Projects and Tasks */}
        <Grid item xs={12}>
          <Card elevation={2} sx={{ borderRadius: 3 }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Avatar sx={{ bgcolor: 'secondary.main', mr: 2 }}>
                  <WorkIcon />
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: "bold" }}>
                  Projects & Tasks
                </Typography>
              </Box>
              <Divider sx={{ mb: 3 }} />
              
              {timesheet.projects?.length > 0 ? (
                timesheet.projects.map((project, projectIndex) => {
                  const projectTotalHours = project.tasks?.reduce((total, task) => {
                    return total + (task.days?.reduce((sum, day) => sum + (day.hours || 0), 0) || 0);
                  }, 0) || 0;

                  return (
                    <Accordion 
                      key={projectIndex} 
                      defaultExpanded 
                      sx={{ 
                        mb: 2, 
                        borderRadius: 2,
                        '&:before': { display: 'none' }
                      }}
                    >
                      <AccordionSummary 
                        expandIcon={<ExpandMoreIcon />}
                        sx={{
                          backgroundColor: '#f5f5f5',
                          borderRadius: 2,
                          '&.Mui-expanded': {
                            borderBottomLeftRadius: 0,
                            borderBottomRightRadius: 0,
                          }
                        }}
                      >
                        <Box sx={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <AssignmentIcon color="action" sx={{ mr: 1 }} />
                            <Box>
                              <Typography variant="caption" display="block" color="text.secondary">
                                Project Name
                              </Typography>
                              <Typography variant="subtitle1" fontWeight="medium">
                                {project.projectName} (ID: {project.projectId})
                              </Typography>
                            </Box>
                          </Box>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Typography variant="subtitle1" fontWeight="bold" sx={{ mr: 3 }}>
                              Total: {projectTotalHours} hours
                            </Typography>
                            <Chip
                              label={project.status || timesheet.status}
                              color={
                                project.status === "SUBMITTED" || (!project.status && timesheet.status === "SUBMITTED")
                                  ? "info"
                                  : project.status === "DRAFT" || (!project.status && timesheet.status === "DRAFT")
                                  ? "warning"
                                  : project.status === "APPROVED" || (!project.status && timesheet.status === "APPROVED")
                                  ? "success"
                                  : project.status === "REJECTED" || (!project.status && timesheet.status === "REJECTED")
                                  ? "error"
                                  : project.status === "CANCELLED" || (!project.status && timesheet.status === "CANCELLED")
                                  ? "error"
                                  : "default"
                              }
                              size="small"
                              sx={{ fontWeight: 'bold', minWidth: 100 }}
                            />
                          </Box>
                        </Box>
                      </AccordionSummary>
                      <AccordionDetails sx={{ pt: 0, backgroundColor: '#fafafa' }}>
                        {project.tasks?.length > 0 ? (
                          project.tasks.map((task, taskIndex) => {
                            const taskTotalHours = task.days?.reduce((sum, day) => sum + (day.hours || 0), 0) || 0;

                            return (
                              <Box key={taskIndex} sx={{ mb: 3, backgroundColor: 'white', p: 2, borderRadius: 2, boxShadow: 1 }}>
                                <Box sx={{ mb: 2 }}>
                                  <Typography variant="caption" display="block" color="text.secondary">
                                    Task Name
                                  </Typography>
                                  <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                    <TaskIcon color="action" sx={{ mr: 1 }} />
                                    <Typography variant="subtitle1" fontWeight="medium">
                                      {task.taskName} (ID: {task.taskId})
                                    </Typography>
                                  </Box>
                                  <Typography variant="body2" sx={{ mt: 1, ml: 4 }}>
                                    <strong>Total Hours:</strong> {taskTotalHours} hours
                                  </Typography>
                                </Box>
                                
                                <TableContainer component={Paper} sx={{ mb: 2, borderRadius: 2 }}>
                                  <Table size="small">
                                    <TableHead>
                                      <TableRow sx={{ 
                                        backgroundColor: '#3f51b5',
                                        '& th': { 
                                          color: 'white',
                                          fontWeight: 'bold',
                                          fontSize: '0.875rem'
                                        }
                                      }}>
                                        <TableCell>Day</TableCell>
                                        <TableCell>Date</TableCell>
                                        <TableCell align="right">Hours</TableCell>
                                        <TableCell>Description</TableCell>
                                      </TableRow>
                                    </TableHead>
                                    <TableBody>
                                      {task.days?.length > 0 ? (
                                        task.days.map((day, dayIndex) => (
                                          <TableRow 
                                            key={dayIndex}
                                            sx={{ 
                                              '&:nth-of-type(odd)': { backgroundColor: '#f9f9f9' },
                                              '&:last-child td, &:last-child th': { border: 0 },
                                              '&:hover': { backgroundColor: '#f0f0f0' }
                                            }}
                                          >
                                            <TableCell>
                                              {daysOfWeek.find(d => d.id === day.dayName)?.name || day.dayName}
                                            </TableCell>
                                            <TableCell>
                                              {day.date ? format(new Date(day.date), "dd MMM yyyy") : '-'}
                                            </TableCell>
                                            <TableCell align="right" sx={{ 
                                              fontWeight: day.hours > 0 ? 'bold' : 'normal',
                                              color: day.hours > 0 ? '#3f51b5' : 'inherit'
                                            }}>
                                              {day.hours > 0 ? day.hours : '-'}
                                            </TableCell>
                                            <TableCell sx={{ 
                                              color: day.description ? 'text.primary' : 'text.secondary',
                                              fontStyle: day.description ? 'normal' : 'italic'
                                            }}>
                                              {day.description || '-'}
                                            </TableCell>
                                          </TableRow>
                                        ))
                                      ) : (
                                        <TableRow>
                                          <TableCell colSpan={4} align="center" sx={{ color: 'text.secondary', py: 3 }}>
                                            No days data available
                                          </TableCell>
                                        </TableRow>
                                      )}
                                    </TableBody>
                                  </Table>
                                </TableContainer>
                              </Box>
                            );
                          })
                        ) : (
                          <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                            No tasks available for this project
                          </Typography>
                        )}
                      </AccordionDetails>
                    </Accordion>
                  );
                })
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                  No projects available for this timesheet
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Timesheet Summary */}
        <Grid item xs={12}>
          <Card elevation={2} sx={{ borderRadius: 3 }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Avatar sx={{ bgcolor: 'info.main', mr: 2 }}>
                  <AccessTimeIcon />
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: "bold" }}>
                  Timesheet Summary
                </Typography>
              </Box>
              <Divider sx={{ mb: 3 }} />
              
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <Card elevation={0} sx={{ backgroundColor: '#e3f2fd', p: 2, borderRadius: 2 }}>
                    <Typography variant="subtitle2" color="text.secondary">Total Projects</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Typography variant="h4" sx={{ fontWeight: 'bold', mr: 1 }}>
                        {timesheet.projects?.length || 0}
                      </Typography>
                      <WorkIcon color="primary" />
                    </Box>
                  </Card>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Card elevation={0} sx={{ backgroundColor: '#e8f5e9', p: 2, borderRadius: 2 }}>
                    <Typography variant="subtitle2" color="text.secondary">Total Tasks</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Typography variant="h4" sx={{ fontWeight: 'bold', mr: 1 }}>
                        {timesheet.projects?.reduce((sum, project) => sum + (project.tasks?.length || 0), 0) || 0}
                      </Typography>
                      <TaskIcon color="success" />
                    </Box>
                  </Card>
                </Grid>
                <Grid item xs={12}>
                  <Card elevation={0} sx={{ backgroundColor: '#fff3e0', p: 3, borderRadius: 2 }}>
                    <Typography variant="subtitle2" color="text.secondary">Total Hours for Timesheet</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Typography variant="h3" sx={{ fontWeight: 'bold', mr: 2, color: 'warning.dark' }}>
                        {totalTimesheetHours} hours
                      </Typography>
                      <AccessTimeIcon sx={{ fontSize: 40, color: 'warning.dark' }} />
                    </Box>
                  </Card>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* Comments */}
        {timesheet.comments && (
          <Grid item xs={12}>
            <Card elevation={2} sx={{ borderRadius: 3 }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Avatar sx={{ bgcolor: 'success.main', mr: 2 }}>
                    <NoteAddIcon />
                  </Avatar>
                  <Typography variant="h6" sx={{ fontWeight: "bold" }}>
                    Comments
                  </Typography>
                </Box>
                <Divider sx={{ mb: 3 }} />
                <Paper elevation={0} sx={{ p: 2, backgroundColor: '#f5f5f5', borderRadius: 2 }}>
                  <Typography>{timesheet.comments}</Typography>
                </Paper>
              </CardContent>
            </Card>
          </Grid>
        )}

        {/* Rejection Reason */}
        {timesheet.status === "REJECTED" && timesheet.rejectionReason && (
          <Grid item xs={12}>
            <Card elevation={2} sx={{ borderRadius: 3, borderLeft: '4px solid', borderColor: 'error.main' }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Avatar sx={{ bgcolor: 'error.main', mr: 2 }}>
                    <CancelIcon />
                  </Avatar>
                  <Typography variant="h6" sx={{ fontWeight: "bold", color: "error.main" }}>
                    Rejection Reason
                  </Typography>
                </Box>
                <Divider sx={{ mb: 3 }} />
                <Paper elevation={0} sx={{ p: 2, backgroundColor: '#ffebee', borderRadius: 2 }}>
                  <Typography color="error">{timesheet.rejectionReason}</Typography>
                </Paper>
              </CardContent>
            </Card>
          </Grid>
        )}
      </Grid>
    </Paper>
  );
};

export default TimesheetViewPage;