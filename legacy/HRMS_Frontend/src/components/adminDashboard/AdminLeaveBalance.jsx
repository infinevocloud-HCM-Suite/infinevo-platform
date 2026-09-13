import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  IconButton,
  Switch,
  FormControlLabel,
  Chip,
  Badge,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Checkbox,
  ListItemText
} from '@mui/material';
import { Edit, Delete, Add, People, Visibility } from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import axios from 'axios';
import { differenceInDays, format } from 'date-fns';
import API_BASE_URL from '../config/apiConfig';
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error.response);
    return Promise.reject(error);
  }
);

const AdminLeaveBalance = () => {
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [currentLeaveType, setCurrentLeaveType] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    defaultDays: 0,
    leaveCarriedForward: false,
    endDate: null,
    applyToAllEmployees: false,
    employeeIds: [],
    projectId: ''
  });
  const [employees, setEmployees] = useState([]);
  const [projects, setProjects] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [employeeSelectionOpen, setEmployeeSelectionOpen] = useState(false);
  const [individualEmployee, setIndividualEmployee] = useState('');
  const [employeesLoading, setEmployeesLoading] = useState(false);

  const hasAction = (actionName) => actions.includes(actionName);

  // Helper function to simplify Comp Off names
  const simplifyLeaveName = (name) => {
    if (name.includes("Comp Off")) {
      return "Comp Off";
    }
    return name;
  };

  useEffect(() => {
    fetchLeaveTypes();
    fetchEmployees();
    fetchProjects();
  }, []);

  const fetchLeaveTypes = async () => {
    setLoading(true);
    try {
      const response = await api.get('/leave-types');
      setLeaveTypes(response.data);
    } catch (err) {
      console.error('Error fetching leave types:', err);
      setError('Failed to load leave types');
    } finally {
      setLoading(false);
    }
  };

  const fetchEmployees = async () => {
    setEmployeesLoading(true);
    try {
      const response = await api.get("/employees/search?query=");
      setEmployees(response.data.data);
    } catch (err) {
      console.error('Error fetching employees:', err);
      setError('Failed to load employees');
    } finally {
      setEmployeesLoading(false);
    }
  };

  const fetchProjects = async () => {
    try {
      const response = await api.get('/projects');
      setProjects(response.data);
    } catch (err) {
      console.error('Error fetching projects:', err);
      setError('Failed to load projects');
    }
  };

  const getDaysRemaining = (expirationDate) => {
    if (!expirationDate) return null;
    const expDate = new Date(expirationDate);
    const today = new Date();
    return differenceInDays(expDate, today);
  };

  const formatExpiration = (expirationDate) => {
    const daysRemaining = getDaysRemaining(expirationDate);

    if (daysRemaining === null) {
      return <Chip label="No expiration" color="default" size="small" />;
    }

    if (daysRemaining < 0) {
      return <Chip label="Expired" color="error" size="small" />;
    } else if (daysRemaining === 0) {
      return <Chip label="Expires today" color="warning" size="small" />;
    } else {
      return (
        <Chip
          label={`${daysRemaining} day${daysRemaining !== 1 ? 's' : ''} remaining`}
          color={daysRemaining <= 3 ? "warning" : "success"}
          size="small"
        />
      );
    }
  };

  const getEmployeeName = (employeeId) => {
    const employee = employees.find(e => e._id === employeeId);
    return employee ? `${employee.empId} - ${employee.name}` : employeeId;
  };

  const getProjectName = (id) => {
    const project = projects.find(p => p._id === id);
    return project ? project.name : id;
  };

  const getProjectAssignments = (projectId) => {
    if (!projectId) return [];
    const project = projects.find(p => p._id === projectId);
    return project?.assignments || [];
  };

  const handleEditClick = (leaveType) => {
    setCurrentLeaveType(leaveType);
    setFormData({
      name: leaveType.name,
      description: leaveType.description,
      defaultDays: leaveType.defaultDays || 0,
      leaveCarriedForward: leaveType.leaveCarriedForward || false,
      endDate: leaveType.endDate ? new Date(leaveType.endDate) : null,
      applyToAllEmployees: leaveType.applyToAllEmployees || false,
      employeeIds: leaveType.employeeIds || [],
      projectId: leaveType.projectId || ''
    });

    if (leaveType.projectId) {
      const project = projects.find(p => p._id === leaveType.projectId);
      setSelectedProject(project);
    }

    setEditDialogOpen(true);
  };

  const handleViewClick = (leaveType) => {
    setCurrentLeaveType(leaveType);
    setViewDialogOpen(true);
  };

  const handleDelete = async (id) => {
    if (!id) {
      setError('Leave type ID is missing');
      return;
    }

    if (window.confirm('Are you sure you want to delete this leave type?')) {
      try {
        await api.delete(`/leave-types/${id}`);
        setSuccess('Leave type deleted successfully');
        fetchLeaveTypes();
      } catch (err) {
        console.error('Error deleting leave type:', err);
        setError(err.response?.data?.message || 'Failed to delete leave type');
      }
    }
  };

  const handleFormChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleDateChange = (date) => {
    setFormData(prev => ({
      ...prev,
      endDate: date
    }));
  };

  const handleProjectChange = (e) => {
    const projectId = e.target.value;
    const project = projects.find(p => p._id === projectId);
    setSelectedProject(project);
    setFormData(prev => ({ ...prev, projectId, employeeIds: [] }));
  };

  const handleOpenEmployeeSelection = (projectName) => {
    setEmployeeSelectionOpen(true);
  };

  const handleIndividualEmployeeChange = (e) => {
    const employeeId = e.target.value;
    setIndividualEmployee(employeeId);
    if (employeeId && !formData.employeeIds.includes(employeeId)) {
      setFormData(prev => ({
        ...prev,
        employeeIds: [...prev.employeeIds, employeeId],
      }));
    }
  };

  const handleRemoveEmployee = (employeeId) => {
    setFormData(prev => ({
      ...prev,
      employeeIds: prev.employeeIds.filter(id => id !== employeeId),
    }));
  };

  const handleUpdate = async () => {
    if (!currentLeaveType?.id) {
      setError('Leave type ID is missing');
      return;
    }

    try {
      const payload = {
        name: formData.name,
        description: formData.description,
        defaultDays: formData.defaultDays,
        leaveCarriedForward: formData.leaveCarriedForward,
        endDate: formData.endDate ? format(formData.endDate, 'yyyy-MM-dd') : null,
        applyToAllEmployees: formData.applyToAllEmployees,
        employeeIds: formData.applyToAllEmployees
          ? employees.map(emp => emp._id)
          : formData.employeeIds,
        projectId: formData.projectId || null
      };

      await api.put(`/leave-types/${currentLeaveType.id}`, payload);
      setSuccess('Leave type updated successfully');
      setEditDialogOpen(false);
      fetchLeaveTypes();
    } catch (err) {
      console.error('Error updating leave type:', err);
      setError(err.response?.data?.message || 'Failed to update leave type');
    }
  };

  const handleCloseSnackbar = () => {
    setError(null);
    setSuccess(null);
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Manage Leave Types
          </Typography>

          {hasAction("MANAGE_LEAVE_TYPES") && (
             <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => navigate(`/${role}/leaves-type`)}
            sx={{ borderRadius: 2, textTransform: 'none', paddingY: 1.5, paddingX: 2 }}
          >
            <Box display="flex" flexDirection="column" alignItems="flex-start" lineHeight={1}>
              <Typography variant="subtitle1" sx={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
                ADD NEW
              </Typography>
              <Typography variant="caption" sx={{ fontSize: '0.8rem', color: 'white' }}>
                leave types
              </Typography>
            </Box>
          </Button>
          )}
        </Box>

        <Paper elevation={3} sx={{ p: 2 }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'normal',
                    minWidth: 120,
                    maxWidth: 200
                  }}>Leave Name</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'normal',
                    minWidth: 150,
                    maxWidth: 250
                  }}>Description</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    textAlign: 'center',
                    width: 120
                  }}>Carry Forward</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    textAlign: 'right',
                    width: 100
                  }}>Days/Year</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    minWidth: 120
                  }}>End Date</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    minWidth: 140
                  }}>Expiration Status</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    textAlign: 'center',
                    width: 120
                  }}>Employee Assignments</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    textAlign: 'center',
                    width: 120
                  }}>Apply to All</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    minWidth: 120
                  }}>Created At</TableCell>
                  <TableCell sx={{
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    textAlign: 'center',
                    width: 120
                  }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {leaveTypes.map((type) => (
                  <TableRow key={type.id}>
                    <TableCell sx={{
                      whiteSpace: 'normal',
                      wordBreak: 'break-word',
                      minWidth: 120,
                      maxWidth: 200
                    }}>
                      {simplifyLeaveName(type.name)} {/* Use simplified name here */}
                    </TableCell>
                    <TableCell sx={{
                      whiteSpace: 'normal',
                      wordBreak: 'break-word',
                      minWidth: 150,
                      maxWidth: 250
                    }}>{type.description || '-'}</TableCell>
                    <TableCell sx={{
                      whiteSpace: 'nowrap',
                      textAlign: 'center'
                    }}>
                      {type.leaveCarriedForward ? 'Yes' : 'No'}
                    </TableCell>
                    <TableCell sx={{
                      whiteSpace: 'nowrap',
                      textAlign: 'right'
                    }}>{type.defaultDays.toFixed(1)}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {type.endDate ? format(new Date(type.endDate), 'MMM dd, yyyy') : 'No expiration'}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {formatExpiration(type.endDate)}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'center' }}>
                      <Badge
                        badgeContent={type.employeeIds?.length || 0}
                        color="primary"
                        onClick={() => handleViewClick(type)}
                        sx={{ cursor: 'pointer' }}
                      >
                        <People />
                      </Badge>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'center' }}>
                      {type.applyToAllEmployees ? 'Yes' : 'No'}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {format(new Date(type.createdAt), 'MMM dd, yyyy')}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'center' }}>
                      {hasAction("EDIT_LEAVE_TYPES") && (
                        <IconButton color="primary" onClick={() => handleEditClick(type)}>
                          <Edit />
                        </IconButton>
                      )}
                      {hasAction("DELETE_LEAVE_TYPES") && (
                        <IconButton color="error" onClick={() => handleDelete(type.id)}>
                          <Delete />
                        </IconButton>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>

        {/* Edit Dialog */}
        <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="md" fullWidth>
          <DialogTitle>Edit Leave Type</DialogTitle>
          <DialogContent>
            <Box sx={{ mt: 2 }}>
              <TextField
                fullWidth
                label="Leave Name"
                name="name"
                value={formData.name}
                onChange={handleFormChange}
                margin="normal"
                required
              />
              <TextField
                fullWidth
                label="Description"
                name="description"
                value={formData.description}
                onChange={handleFormChange}
                margin="normal"
                multiline
                rows={3}
              />
              <TextField
                fullWidth
                label="Leaves Per Year"
                name="defaultDays"
                type="number"
                value={formData.defaultDays}
                onChange={handleFormChange}
                margin="normal"
                required
                inputProps={{ min: 0, step: "0.1" }}
                disabled
                helperText="This value cannot be modified"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={formData.leaveCarriedForward}
                    onChange={handleFormChange}
                    name="leaveCarriedForward"
                    color="primary"
                  />
                }
                label="Leave Can Be Carried Forward"
                sx={{ mt: 2, display: 'block' }}
              />
              <DatePicker
                label="End Date (Optional)"
                value={formData.endDate}
                onChange={handleDateChange}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    fullWidth
                    margin="normal"
                    sx={{ mt: 2 }}
                  />
                )}
              />

              {/* Project Selection */}
              <FormControl fullWidth margin="normal" disabled={formData.applyToAllEmployees}>
                <InputLabel id="project-select-label">Project</InputLabel>
                <Select
                  labelId="project-select-label"
                  id="project-select"
                  value={formData.projectId}
                  label="Project"
                  onChange={handleProjectChange}
                >
                  <MenuItem value="">
                    <em>None</em>
                  </MenuItem>
                  {projects.map((project) => (
                    <MenuItem key={project._id} value={project._id}>
                      <Box
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          width: '100%',
                        }}
                      >
                        <span>{project.name}</span>
                        <IconButton
                          onClick={(e) => {
                            e.stopPropagation();
                            handleOpenEmployeeSelection(project.name);
                          }}
                          size="small"
                          disabled={formData.applyToAllEmployees}
                        >
                          <Visibility fontSize="small" />
                        </IconButton>
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              {/* Apply to All Switch */}
              <FormControlLabel
                control={
                  <Switch
                    checked={formData.applyToAllEmployees}
                    onChange={handleFormChange}
                    name="applyToAllEmployees"
                    color="primary"
                  />
                }
                label="Apply to All Employees"
                sx={{ mt: 2, display: 'block' }}
              />

              {/* Employee Selection Dropdown */}
              {!formData.applyToAllEmployees && (
                <FormControl fullWidth margin="normal">
                  <InputLabel id="employee-select-label">Select Individual Employee</InputLabel>
                  <Select
                    labelId="employee-select-label"
                    id="employee-select"
                    value={individualEmployee}
                    label="Select Individual Employee"
                    onChange={handleIndividualEmployeeChange}
                    disabled={formData.applyToAllEmployees || employeesLoading}
                  >
                    <MenuItem value="">
                      <em>None</em>
                    </MenuItem>
                    {employeesLoading ? (
                      <MenuItem disabled>
                        <CircularProgress size={24} />
                      </MenuItem>
                    ) : (
                      employees.map((employee) => (
                        <MenuItem key={employee._id} value={employee._id}>
                          {`${employee.empId} - ${employee.name}`}
                        </MenuItem>
                      ))
                    )}
                  </Select>
                </FormControl>
              )}

              {/* Selected Employees Display */}
              {!formData.applyToAllEmployees && formData.employeeIds.length > 0 && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2">Selected Employees:</Typography>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1 }}>
                    {formData.employeeIds.map((employeeId) => (
                      <Chip
                        key={employeeId}
                        label={getEmployeeName(employeeId)}
                        onDelete={() => handleRemoveEmployee(employeeId)}
                        sx={{ mb: 1 }}
                        deleteIcon={<Delete />}
                      />
                    ))}
                  </Box>
                </Box>
              )}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
            <Button onClick={handleUpdate} color="primary" variant="contained">
              Update
            </Button>
          </DialogActions>
        </Dialog>

        {/* Employee Selection Dialog */}
        <Dialog
          open={employeeSelectionOpen}
          onClose={() => setEmployeeSelectionOpen(false)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>Select Employees from {selectedProject?.name}</DialogTitle>
          <DialogContent>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Select</TableCell>
                    <TableCell>Employee ID</TableCell>
                    <TableCell>Employee Name</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {getProjectAssignments(formData.projectId).map((assignment) => {
                    const employee = employees.find(e => e._id === assignment.empId);
                    return (
                      <TableRow key={assignment.empId}>
                        <TableCell>
                          <Checkbox
                            checked={formData.employeeIds.includes(assignment.empId)}
                            onChange={() => {
                              if (formData.employeeIds.includes(assignment.empId)) {
                                handleRemoveEmployee(assignment.empId);
                              } else {
                                setFormData(prev => ({
                                  ...prev,
                                  employeeIds: [...prev.employeeIds, assignment.empId]
                                }));
                              }
                            }}
                          />
                        </TableCell>
                        <TableCell>{employee ? employee.empId : assignment.empId}</TableCell>
                        <TableCell>
                          {employee ? employee.name : assignment.empName}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEmployeeSelectionOpen(false)}>Cancel</Button>
            <Button
              onClick={() => setEmployeeSelectionOpen(false)}
              variant="contained"
              color="primary"
            >
              Confirm
            </Button>
          </DialogActions>
        </Dialog>

        {/* View Employees Dialog */}
        <Dialog open={viewDialogOpen} onClose={() => setViewDialogOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle>
            Employee Assignments for {currentLeaveType?.name}
          </DialogTitle>
          <DialogContent>
            {currentLeaveType?.applyToAllEmployees ? (
              <Typography variant="body1" sx={{ py: 2 }}>
                This leave type applies to <strong>all employees</strong>.
              </Typography>
            ) : (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle1" gutterBottom>
                  Assigned Employees ({currentLeaveType?.employeeIds?.length || 0}):
                </Typography>
                {currentLeaveType?.employeeIds?.length > 0 ? (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                    {currentLeaveType.employeeIds.map(id => (
                      <Chip
                        key={id}
                        label={getEmployeeName(id)}
                        variant="outlined"
                      />
                    ))}
                  </Box>
                ) : (
                  <Typography variant="body2" color="textSecondary">
                    No employees assigned to this leave type.
                  </Typography>
                )}
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setViewDialogOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>

        <Snackbar
          open={!!error}
          autoHideDuration={6000}
          onClose={handleCloseSnackbar}
        >
          <Alert severity="error" onClose={handleCloseSnackbar}>
            {error}
          </Alert>
        </Snackbar>

        <Snackbar
          open={!!success}
          autoHideDuration={6000}
          onClose={handleCloseSnackbar}
        >
          <Alert severity="success" onClose={handleCloseSnackbar}>
            {success}
          </Alert>
        </Snackbar>
      </Container>
    </LocalizationProvider>
  );
};

export default AdminLeaveBalance;