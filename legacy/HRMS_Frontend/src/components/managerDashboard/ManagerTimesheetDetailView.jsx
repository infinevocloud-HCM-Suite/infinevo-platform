import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  Paper,
  Grid,
  Divider,
  Button,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Snackbar,
  Alert,
  CircularProgress,
  IconButton,
  TextField,
  useTheme,
  styled
} from "@mui/material";
import {
  ArrowBack,
  CheckCircle,
  Cancel,
  Person,
  Work,
  AccessTime,
  CalendarToday,
  Description,
  HourglassTop,
  Task
} from "@mui/icons-material";
import { useNavigate, useLocation } from "react-router-dom";
import { parseISO, format } from 'date-fns';
import axios from 'axios';
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

// Styled components for better UI
const StyledPaper = styled(Paper)(({ theme }) => ({
  padding: theme.spacing(3),
  marginBottom: theme.spacing(3),
  borderRadius: '12px',
  boxShadow: '0px 4px 20px rgba(0, 0, 0, 0.08)',
  transition: 'box-shadow 0.3s ease',
  '&:hover': {
    boxShadow: '0px 6px 24px rgba(0, 0, 0, 0.12)'
  }
}));

const StatusChip = styled(Chip)(({ theme, status }) => ({
  fontWeight: 600,
  textTransform: 'capitalize',
  ...(status === 'approved' && {
    backgroundColor: theme.palette.success.light,
    color: theme.palette.success.dark
  }),
  ...(status === 'rejected' && {
    backgroundColor: theme.palette.error.light,
    color: theme.palette.error.dark
  }),
  ...(status === 'pending' && {
    backgroundColor: theme.palette.warning.light,
    color: theme.palette.warning.dark
  }),
  ...(status === 'draft' && {
    backgroundColor: theme.palette.info.light,
    color: theme.palette.info.dark
  })
}));

const HighlightBox = styled(Box)(({ theme }) => ({
  padding: theme.spacing(2),
  borderRadius: '8px',
  backgroundColor: theme.palette.grey[100],
  marginBottom: theme.spacing(2)
}));

const TaskTable = styled(Table)(({ theme }) => ({
  '& .MuiTableCell-root': {
    borderBottom: `1px solid ${theme.palette.divider}`,
    padding: theme.spacing(1.5),
    '&:first-of-type': {  // Day column
      width: '15%',
      minWidth: '100px'
    },
    '&:nth-of-type(2)': {  // Date column
      width: '15%',
      minWidth: '120px'
    },
    '&:nth-of-type(3)': {  // Hours column
      width: '10%',
      minWidth: '80px'
    },
    '&:last-of-type': {  // Description column
      width: '60%',
      minWidth: '200px'
    }
  },
  '& .MuiTableHead-root': {
    '& .MuiTableCell-root': {
      fontWeight: 600,
      backgroundColor: theme.palette.grey[50],
      color: theme.palette.text.primary
    }
  },
  '& .MuiTableBody-root': {
    '& .MuiTableRow-root:last-child': {
      '& .MuiTableCell-root': {
        borderBottom: 'none',
        backgroundColor: theme.palette.grey[50]
      }
    }
  }
}));

const ActionButton = styled(Button)(({ theme }) => ({
  borderRadius: '8px',
  padding: theme.spacing(1, 2),
  fontWeight: 600,
  textTransform: 'none',
  boxShadow: 'none',
  '&:hover': {
    boxShadow: 'none'
  }
}));

const ManagerTimesheetDetailView = () => {
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const [timesheet, setTimesheet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState("success");
  const [openDialog, setOpenDialog] = useState(false);
  const [dialogType, setDialogType] = useState("");
  const [rejectionReason, setRejectionReason] = useState("");
  const [currentProjectId, setCurrentProjectId] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const hasAction = (actionName) => actions.includes(actionName);

  useEffect(() => {
    const fetchTimesheetDetails = async () => {
      try {
        setLoading(true);
        const timesheetId = location.pathname.split('/').pop();
        const projectId = location.state?.timesheet?.projectId;
        const response = await axios.get(`${API_BASE_URL}/api/timesheets/manager/${timesheetId}?projectId=${projectId}`);
        
        if (!response.data) {
          throw new Error("Timesheet not found");
        }
        
        const ts = response.data;
        const totalHours = ts.projects.flatMap(project => 
          project.tasks.flatMap(task => 
            task.days.map(day => day.hours)
          )
        ).reduce((sum, hour) => sum + (hour || 0), 0);
        
        const formattedTimesheet = {
          id: ts.timesheetId,
          employeeId: ts.employeeId,
          employeeName: ts.employeeName,
          employeeEmail: `${ts.employeeName?.toLowerCase()?.replace(/\s+/g, '')}@company.com`,
          projects: ts.projects.map(project => ({
            projectId: project.projectId,
            projectName: project.projectName,
            status: project.status,
            rejectionReason: project.rejectionReason || "",
            tasks: project.tasks.map(task => ({
              taskId: task.taskId,
              taskName: task.taskName,
              days: task.days.map(day => ({
                date: day.date,
                hours: day.hours,
                description: day.description || ""
              }))
            }))
          })),
          weekStartDate: ts.weekStartDate,
          weekEndDate: ts.weekEndDate,
          status: ts.status,
          totalHours: totalHours,
          submittedAt: ts.submitted_at
        };
        
        setTimesheet(formattedTimesheet);
      } catch (err) {
        console.error("Error fetching timesheet details:", err);
        setError(err.message || "Failed to load timesheet details");
      } finally {
        setLoading(false);
      }
    };
    
    fetchTimesheetDetails();
  }, [location]);

  const showSnackbar = (message, severity) => {
    setSnackbarMessage(message);
    setSnackbarSeverity(severity);
    setSnackbarOpen(true);
  };

  const handleSnackbarClose = () => {
    setSnackbarOpen(false);
  };

  const handleDialogOpen = (type, projectId = null) => {
    setDialogType(type);
    setCurrentProjectId(projectId);
    setOpenDialog(true);
  };

  const handleDialogClose = () => {
    setOpenDialog(false);
    setRejectionReason("");
    setCurrentProjectId(null);
  };

  const approveTimesheet = async () => {
    setIsProcessing(true);
    try {
      const response = await axios.put(
        `${API_BASE_URL}/api/timesheets/${timesheet.id}/projects/${currentProjectId}/status`,
        null,
        { params: { status: "APPROVED" } }
      );
      
      setTimesheet(prev => ({
        ...prev,
        status: response.data.newTimesheetStatus,
        projects: prev.projects.map(project => 
          project.projectId === currentProjectId 
            ? { ...project, status: "APPROVED", rejectionReason: "" } 
            : project
        )
      }));
      
      showSnackbar(response.data?.message || "Timesheet approved successfully", "success");
      handleDialogClose();
    } catch (error) {
      console.error("Error approving Timesheet:", error);
      showSnackbar(error.response?.data?.message || "Failed to approve Timesheet", "error");
    } finally {
      setIsProcessing(false);
    }
  };

  const rejectTimesheet = async () => {
    if (!rejectionReason) {
      showSnackbar("Please provide a rejection reason", "warning");
      return;
    }

    setIsProcessing(true);
    try {
      const response = await axios.put(
        `${API_BASE_URL}/api/timesheets/${timesheet.id}/projects/${currentProjectId}/status`,
        null,
        { 
          params: { 
            status: "REJECTED",
            rejectionReason: rejectionReason 
          } 
        }
      );
      
      setTimesheet(prev => ({
        ...prev,
        status: response.data.newTimesheetStatus,
        projects: prev.projects.map(project => 
          project.projectId === currentProjectId 
            ? { 
                ...project, 
                status: "REJECTED", 
                rejectionReason: rejectionReason 
              } 
            : project
        )
      }));
      
      showSnackbar(response.data?.message || "Timesheet rejected", "warning");
      handleDialogClose();
    } catch (error) {
      console.error("Error rejecting Timesheet:", error);
      showSnackbar(error.response?.data?.message || "Failed to reject Timesheet", "error");
    } finally {
      setIsProcessing(false);
    }
  };

  const getStatusChip = (status) => {
    const statusLower = status.toLowerCase();
    return (
      <StatusChip 
        label={statusLower === 'submitted' ? 'pending' : statusLower} 
        status={statusLower === 'submitted' ? 'pending' : statusLower} 
        size="small" 
      />
    );
  };

  if (loading) {
    return (
      <Box sx={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        backgroundColor: theme.palette.background.default
      }}>
        <CircularProgress size={60} thickness={4} />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ 
        p: 4, 
        maxWidth: 600, 
        mx: 'auto', 
        textAlign: 'center',
        mt: 10
      }}>
        <Typography variant="h5" color="error" gutterBottom>
          Error Loading Timesheet
        </Typography>
        <Typography variant="body1" color="textSecondary" paragraph>
          {error}
        </Typography>
        <Button 
          variant="contained" 
          onClick={() => navigate(-1)}
          startIcon={<ArrowBack />}
          sx={{ 
            mt: 3,
            px: 4,
            py: 1.5,
            borderRadius: '8px'
          }}
        >
          Back to Timesheets
        </Button>
      </Box>
    );
  }

  if (!timesheet) {
    return (
      <Box sx={{ 
        p: 4, 
        maxWidth: 600, 
        mx: 'auto', 
        textAlign: 'center',
        mt: 10
      }}>
        <Typography variant="h5" gutterBottom>
          No Timesheet Data Available
        </Typography>
        <Typography variant="body1" color="textSecondary" paragraph>
          The requested timesheet could not be found or has no data.
        </Typography>
        <Button 
          variant="contained" 
          onClick={() => navigate(-1)}
          startIcon={<ArrowBack />}
          sx={{ 
            mt: 3,
            px: 4,
            py: 1.5,
            borderRadius: '8px'
          }}
        >
          Back to Timesheets
        </Button>
      </Box>
    );
  }

  return (
    <Box sx={{ 
      p: { xs: 2, md: 4 }, 
      maxWidth: 1400, 
      mx: 'auto',
      backgroundColor: theme.palette.background.default,
      minHeight: '100vh'
    }}>
      {/* Header Section */}
      <Box sx={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center', 
        mb: 4,
        flexWrap: 'wrap',
        gap: 2
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <IconButton 
            onClick={() => navigate(-1)}
            sx={{
              backgroundColor: theme.palette.grey[100],
              '&:hover': {
                backgroundColor: theme.palette.grey[200]
              }
            }}
          >
            <ArrowBack />
          </IconButton>
          <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
            Timesheet Details
          </Typography>
        </Box>
      </Box>

      {/* Timesheet Info Section */}
      <StyledPaper>
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <List disablePadding>
              <ListItem sx={{ px: 0 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                    <Person />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      {timesheet.employeeName}
                    </Typography>
                  }
                  secondary={
                    <>
                      <Typography variant="body2" color="textSecondary">
                        Employee ID: {timesheet.employeeId}
                      </Typography>
                      <Typography variant="body2" color="textSecondary">
                        Timesheet ID: {timesheet.id}
                      </Typography>
                    </>
                  }
                />
              </ListItem>
              
              <ListItem sx={{ px: 0 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: theme.palette.secondary.main }}>
                    <Work />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      Projects
                    </Typography>
                  }
                  secondary={
                    <Typography variant="body2" color="textSecondary">
                      {timesheet.projects.length} project(s) recorded
                    </Typography>
                  }
                />
              </ListItem>
            </List>
          </Grid>
          
          <Grid item xs={12} md={6}>
            <List disablePadding>
              <ListItem sx={{ px: 0 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: theme.palette.info.main }}>
                    <AccessTime />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      Total Hours
                    </Typography>
                  }
                  secondary={
                    <Typography variant="body2" color="textSecondary">
                      {timesheet.totalHours} hours worked this week
                    </Typography>
                  }
                />
              </ListItem>
              
              <ListItem sx={{ px: 0 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: theme.palette.success.main }}>
                    <CalendarToday />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      Week Period
                    </Typography>
                  }
                  secondary={
                    <Typography variant="body2" color="textSecondary">
                      {format(parseISO(timesheet.weekStartDate), "MMM dd, yyyy")} - {format(parseISO(timesheet.weekEndDate), "MMM dd, yyyy")}
                    </Typography>
                  }
                />
              </ListItem>
            </List>
          </Grid>
        </Grid>
      </StyledPaper>

      {/* Projects and Tasks Section */}
      {timesheet.projects.length > 0 ? (
        timesheet.projects.map((project, pIndex) => (
          <StyledPaper key={pIndex}>
            <Box sx={{ 
              display: 'flex', 
              justifyContent: 'space-between', 
              alignItems: 'center', 
              mb: 2,
              flexWrap: 'wrap',
              gap: 2
            }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Work color="primary" />
                <Typography variant="h5" component="h2" sx={{ fontWeight: 600 }}>
                  {project.projectName}
                </Typography>
              </Box>
              {getStatusChip(project.status)}
            </Box>
            
            {project.status === "REJECTED" && project.rejectionReason && (
              <HighlightBox sx={{ backgroundColor: theme.palette.error.light + '20' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="subtitle1" color="error" sx={{ fontWeight: 600 }}>
                    Rejection Reason:
                  </Typography>
                </Box>
                <Typography variant="body2" sx={{ pl: 4, color: theme.palette.error.dark }}>
                  {project.rejectionReason}
                </Typography>
              </HighlightBox>
            )}
            
            <Divider sx={{ my: 3, borderColor: theme.palette.divider }} />
            
            {project.tasks.map((task, tIndex) => (
              <Box key={tIndex} sx={{ mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <Task color="secondary" />
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {task.taskName}
                  </Typography>
                </Box>
                
                <TableContainer>
                  <TaskTable size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Day</TableCell>
                        <TableCell>Date</TableCell>
                        <TableCell align="right">Hours</TableCell>
                        <TableCell>Description</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {task.days.map((day, dIndex) => (
                        <TableRow key={dIndex} hover>
                          <TableCell sx={{ fontWeight: 500 }}>
                            {format(parseISO(day.date), "EEEE")}
                          </TableCell>
                          <TableCell>
                            {format(parseISO(day.date), "MMM dd, yyyy")}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 500 }}>
                            {day.hours || "0"}
                          </TableCell>
                          <TableCell sx={{ 
                            color: day.description ? 'inherit' : theme.palette.text.disabled,
                            wordBreak: 'break-word'
                          }}>
                            {day.description || "-"}
                          </TableCell>
                        </TableRow>
                      ))}
                      <TableRow>
                        <TableCell colSpan={2} align="right" sx={{ 
                          fontWeight: 700,
                          color: theme.palette.text.primary
                        }}>
                          Task Total:
                        </TableCell>
                        <TableCell align="right" sx={{ 
                          fontWeight: 700,
                          color: theme.palette.text.primary
                        }}>
                          {task.days.reduce((sum, day) => sum + (day.hours || 0), 0)}
                        </TableCell>
                        <TableCell></TableCell>
                      </TableRow>
                    </TableBody>
                  </TaskTable>
                </TableContainer>
              </Box>
            ))}

            {/* Project-specific action buttons */}
            {(project.status === "SUBMITTED" || project.status === "Pending") && (
              <Box sx={{ 
                display: 'flex', 
                justifyContent: 'flex-end', 
                gap: 2, 
                mt: 3,
                flexWrap: 'wrap'
              }}>
                {hasAction("REJECT_TIMESHEET") && (
                  <ActionButton
                    variant="contained"
                    color="error"
                    startIcon={<Cancel />}
                    onClick={() => handleDialogOpen("rejectTimesheet", project.projectId)}
                    disabled={isProcessing}
                  >
                    {isProcessing ? <CircularProgress size={24} /> : "Reject Timesheet"}
                  </ActionButton>
                )}
                {hasAction("APPROVE_TIMESHEET") && (
                  <ActionButton
                    variant="contained"
                    color="success"
                    startIcon={<CheckCircle />}
                    onClick={() => handleDialogOpen("approveTimesheet", project.projectId)}
                    disabled={isProcessing}
                  >
                    {isProcessing ? <CircularProgress size={24} /> : "Approve Timesheet"}
                  </ActionButton>
                )}
              </Box>
            )}
          </StyledPaper>
        ))
      ) : (
        <StyledPaper>
          <Box sx={{ 
            display: 'flex', 
            flexDirection: 'column', 
            alignItems: 'center', 
            py: 4,
            textAlign: 'center'
          }}>
            <HourglassTop color="disabled" sx={{ fontSize: 60, mb: 2 }} />
            <Typography variant="h6" color="textSecondary">
              No projects found in this timesheet
            </Typography>
            <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
              This timesheet doesn't contain any project entries that you manage.
            </Typography>
          </Box>
        </StyledPaper>
      )}

      {/* Approval Dialog for Project */}
      <Dialog 
        open={openDialog && dialogType === "approveTimesheet"} 
        onClose={handleDialogClose}
        PaperProps={{
          sx: {
            borderRadius: '12px',
            minWidth: '400px'
          }
        }}
      >
        <DialogTitle sx={{ fontWeight: 600 }}>Approve Timesheet</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to approve the timesheet submitted by{" "}
            <strong>{timesheet.employeeName}</strong> for the week of{" "}
            {format(parseISO(timesheet.weekStartDate), "MMM dd")}?
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button 
            onClick={handleDialogClose} 
            disabled={isProcessing}
            sx={{ borderRadius: '8px' }}
          >
            Cancel
          </Button>
          <ActionButton 
            onClick={approveTimesheet} 
            color="success" 
            variant="contained"
            disabled={isProcessing}
          >
            {isProcessing ? <CircularProgress size={24} /> : "Approve Timesheet"}
          </ActionButton>
        </DialogActions>
      </Dialog>

      {/* Rejection Dialog for Project */}
      <Dialog 
        open={openDialog && dialogType === "rejectTimesheet"} 
        onClose={handleDialogClose}
        PaperProps={{
          sx: {
            borderRadius: '12px',
            minWidth: '400px'
          }
        }}
      >
        <DialogTitle sx={{ fontWeight: 600 }}>Reject Timesheet</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to reject this timesheet submitted by{" "}
            <strong>{timesheet.employeeName}</strong>?
          </DialogContentText>
          <TextField
            autoFocus
            margin="dense"
            label="Reason for rejection"
            type="text"
            fullWidth
            variant="outlined"
            multiline
            rows={3}
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions sx={{ p: 3 }}>
          <Button 
            onClick={handleDialogClose} 
            disabled={isProcessing}
            sx={{ borderRadius: '8px' }}
          >
            Cancel
          </Button>
          <ActionButton 
            onClick={rejectTimesheet} 
            color="error" 
            variant="contained"
            disabled={!rejectionReason || isProcessing}
          >
            {isProcessing ? <CircularProgress size={24} /> : "Reject Timesheet"}
          </ActionButton>
        </DialogActions>
      </Dialog>

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
          sx={{ 
            width: "100%", 
            borderRadius: '8px',
            boxShadow: theme.shadows[3]
          }}
          variant="filled"
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default ManagerTimesheetDetailView;