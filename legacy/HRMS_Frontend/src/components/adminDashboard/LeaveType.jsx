import React, { useState, useEffect } from "react";
import {
  Container,
  Typography,
  TextField,
  Button,
  Paper,
  Grid,
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Checkbox,
  FormControlLabel,
  Autocomplete,
} from "@mui/material";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { Visibility, Delete, Search } from "@mui/icons-material";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";

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

const LeaveType = () => {
  // Form state
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    defaultDays: 0,
    employeeIds: [],
    endDate: null,
    leaveCarriedForward: "no",
    applyToAllEmployees: false,
  });

  // Validation state
  const [formErrors, setFormErrors] = useState({
    name: false,
    defaultDays: false,
    employees: false,
    endDate: false,
  });

  // UI state
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [projects, setProjects] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedProject, setSelectedProject] = useState(null);
  const [tempSelectedEmployees, setTempSelectedEmployees] = useState([]);
  const [projectData, setProjectData] = useState(null);
  const [autocompleteInput, setAutocompleteInput] = useState("");

  // Fetch initial data
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const projectsResponse = await api.get("/projects");
        setProjects(projectsResponse.data);

        const employeesResponse = await api.get("/employees/search?query=");
        setEmployees(employeesResponse.data.data);
      } catch (err) {
        console.error("Error fetching data:", err);
        setError("Failed to fetch initial data");
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  // Handle form field changes
  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
    
    // Clear error when field changes
    if (name === "applyToAllEmployees" || name === "employeeIds") {
      setFormErrors(prev => ({ ...prev, employees: false }));
    } else if (name === "endDate") {
      setFormErrors(prev => ({ ...prev, endDate: false }));
    } else {
      setFormErrors(prev => ({ ...prev, [name]: false }));
    }
  };

  const handleDateChange = (date) => {
    setFormData((prev) => ({ ...prev, endDate: date }));
    setFormErrors(prev => ({ ...prev, endDate: false }));
  };

  // Project selection handler
  const handleProjectChange = (e) => {
    const projectId = e.target.value;
    const project = projects.find((p) => p._id === projectId);
    setSelectedProject(project);
    setFormData((prev) => ({ ...prev, projectId, employeeIds: [] }));
    setFormErrors(prev => ({ ...prev, employees: false }));
  };

  // Employee selection dialog handlers
  const handleOpenDialog = (projectName) => {
    setProjectData(projectName);
    setTempSelectedEmployees([...formData.employeeIds]);
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
  };

  const handleEmployeeToggle = (employeeId) => () => {
    setTempSelectedEmployees((prev) =>
      prev.includes(employeeId)
        ? prev.filter((id) => id !== employeeId)
        : [...prev, employeeId]
    );
  };

  const handleConfirmSelection = () => {
    setFormData((prev) => ({
      ...prev,
      employeeIds: tempSelectedEmployees,
    }));
    setFormErrors(prev => ({ ...prev, employees: false }));
    setDialogOpen(false);
  };

  // Remove selected employee
  const handleRemoveEmployee = (employeeId) => {
    setFormData((prev) => ({
      ...prev,
      employeeIds: prev.employeeIds.filter((id) => id !== employeeId),
    }));
    setFormErrors(prev => ({ ...prev, employees: false }));
  };

  // Get employee name by ID
  const getEmployeeName = (employeeId) => {
    const employee = employees.find((e) => e._id === employeeId);
    return employee ? `${employee.empId} - ${employee.name}` : employeeId;
  };

  // Get project assignments
  const handleSelect = (projectName) => {
    if (!projectName) return [];
    const project = projects.find((p) => p.name === projectName);
    return project?.assignments || [];
  };

  // Handle employee selection from Autocomplete
  const handleEmployeeSelect = (event, newValue) => {
    if (newValue && !formData.employeeIds.includes(newValue.empId)) {
      setFormData((prev) => ({
        ...prev,
        employeeIds: [...prev.employeeIds, newValue.empId],
      }));
      setFormErrors(prev => ({ ...prev, employees: false }));
      setAutocompleteInput("");
    }
  };

  // Filter employees based on search and selected status
  const filteredEmployees = employees.filter(
    (emp) =>
      !formData.employeeIds.includes(emp.empId) &&
      (emp.name.toLowerCase().includes(autocompleteInput.toLowerCase()) ||
       emp.empId.toLowerCase().includes(autocompleteInput.toLowerCase()))
  );

  // Validate form before submission
  const validateForm = () => {
    const errors = {
      name: !formData.name,
      defaultDays: !formData.defaultDays || 
                  formData.defaultDays < 0 || 
                  (formData.defaultDays % 0.5 !== 0), // Ensure it's a multiple of 0.5
      employees: !formData.applyToAllEmployees && formData.employeeIds.length === 0,
      endDate: !formData.endDate,
    };
    
    setFormErrors(errors);
    return !Object.values(errors).some(error => error);
  };

  // Form submission
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      setError("Please fill all required fields including end date and select at least one employee");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await api.post("/leave-types", {
        ...formData,
        leaveCarriedForward: formData.leaveCarriedForward === "yes",
        employeeIds: formData.applyToAllEmployees
          ? employees.map((emp) => emp._id)
          : formData.employeeIds,
      });
      setSuccess("Leave type created successfully!");
      // Reset form
      setFormData({
        name: "",
        description: "",
        defaultDays: 0,
        projectId: "",
        employeeIds: [],
        endDate: null,
        leaveCarriedForward: "no",
        applyToAllEmployees: false,
      });
      setSelectedProject(null);
      setAutocompleteInput("");
    } catch (err) {
      console.error("Error creating leave type:", err);
      setError(err.response?.data?.message || "Failed to create leave type");
    } finally {
      setLoading(false);
    }
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Container maxWidth="md" sx={{ py: 4 }}>
        <Paper elevation={3} sx={{ p: 4 }}>
          <Typography variant="h4" gutterBottom sx={{ mb: 3, fontWeight: 700 }}>
            Create New Leave Type
          </Typography>

          <form onSubmit={handleSubmit}>
            <Grid container spacing={3}>
              {/* Leave Type Name */}
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Leave Type Name *"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  required
                  error={formErrors.name}
                  helperText={formErrors.name ? "Leave type name is required" : ""}
                />
              </Grid>

              {/* Description */}
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Description"
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  multiline
                  rows={3}
                />
              </Grid>

              {/* Default Days - Updated for fractional values */}
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  label="Number of Days Allocation *"
                  name="defaultDays"
                  type="number"
                  value={formData.defaultDays}
                  onChange={handleChange}
                  required
                  inputProps={{
                    min: 0,
                    step: 0.5
                  }}
                  error={formErrors.defaultDays}
                  helperText={
                    formErrors.defaultDays ? 
                    "Please enter a valid number of days (must be in 0.5 increments, e.g., 0.5, 1, 1.5)" : 
                    ""
                  }
                />
              </Grid>

              {/* Carry Forward Dropdown */}
              <Grid item xs={12} sm={6}>
                <FormControl fullWidth>
                  <InputLabel id="carry-forward-label">
                    Carry Forwardable
                  </InputLabel>
                  <Select
                    labelId="carry-forward-label"
                    id="carry-forward-select"
                    name="leaveCarriedForward"
                    value={formData.leaveCarriedForward}
                    label="Carry Forwardable"
                    onChange={handleChange}
                  >
                    <MenuItem value="no">No</MenuItem>
                    <MenuItem value="yes">Yes</MenuItem>
                  </Select>
                </FormControl>
              </Grid>

              {/* Apply to All Checkbox */}
              <Grid item xs={12}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={formData.applyToAllEmployees}
                      onChange={handleChange}
                      name="applyToAllEmployees"
                      color="primary"
                    />
                  }
                  label="Apply to All Employees"
                />
              </Grid>

              {/* Project Selection */}
              <Grid item xs={12} sm={6}>
                <FormControl fullWidth disabled={formData.applyToAllEmployees}>
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
                            display: "flex",
                            justifyContent: "space-between",
                            width: "100%",
                          }}
                        >
                          <span>{project.name}</span>
                          <IconButton
                            onClick={(e) => {
                              e.stopPropagation();
                              handleOpenDialog(project.name);
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
              </Grid>

              {/* Fixed Autocomplete Component */}
              <Grid item xs={12}>
                <Autocomplete
                  fullWidth
                  disabled={formData.applyToAllEmployees}
                  options={filteredEmployees}
                  getOptionLabel={(option) => `${option.empId} - ${option.name}`}
                  value={null}
                  inputValue={autocompleteInput}
                  onInputChange={(event, newValue) => {
                    setAutocompleteInput(newValue);
                  }}
                  onChange={handleEmployeeSelect}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Select Individual Employee"
                      variant="outlined"
                      error={formErrors.employees && !formData.applyToAllEmployees}
                      helperText={formErrors.employees && !formData.applyToAllEmployees ? 
                        "Please select at least one employee (via project or individually)" : ""}
                      InputProps={{
                        ...params.InputProps,
                        startAdornment: <Search sx={{ mr: 1 }} />,
                      }}
                    />
                  )}
                  noOptionsText={
                    employees.length === 0
                      ? "Loading employees..."
                      : filteredEmployees.length === 0
                      ? formData.employeeIds.length === employees.length
                        ? "All employees selected"
                        : "No matching employees"
                      : "Start typing to search"
                  }
                  filterOptions={(options, state) => options}
                />
              </Grid>

              {/* Selected Employees Display */}
              {!formData.applyToAllEmployees && formData.employeeIds.length > 0 && (
                <Grid item xs={12}>
                  <Typography variant="subtitle1" gutterBottom>
                    Selected Employees:
                  </Typography>
                  <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5, mt: 1 }}>
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
                </Grid>
              )}

              {/* End Date */}
              <Grid item xs={12} sm={6}>
                <DatePicker
                  label="End Date *"
                  value={formData.endDate}
                  onChange={handleDateChange}
                  renderInput={(params) => (
                    <TextField 
                      {...params} 
                      fullWidth 
                      error={formErrors.endDate}
                      helperText={formErrors.endDate ? "End date is required" : ""}
                    />
                  )}
                />
              </Grid>

              {/* Submit Button */}
              <Grid item xs={12}>
                <Box display="flex" justifyContent="flex-end" mt={2}>
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    disabled={loading}
                    sx={{ px: 4, py: 1.5 }}
                  >
                    {loading ? (
                      <CircularProgress size={24} />
                    ) : (
                      "Create Leave Type"
                    )}
                  </Button>
                </Box>
              </Grid>
            </Grid>
          </form>
        </Paper>

        {/* Employee Selection Dialog */}
        <Dialog
          open={dialogOpen}
          onClose={handleCloseDialog}
          fullWidth
          maxWidth="md"
        >
          <DialogTitle>
            {projectData
              ? `Select Employees from ${projectData}`
              : "Select Employees"}
          </DialogTitle>
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
                  {handleSelect(projectData).map((employee) => (
                    <TableRow key={employee.empId}>
                      <TableCell>
                        <Checkbox
                          checked={tempSelectedEmployees.includes(employee.empId)}
                          onChange={handleEmployeeToggle(employee.empId)}
                        />
                      </TableCell>
                      <TableCell>{employee.empId}</TableCell>
                      <TableCell>{employee.empName}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog}>Cancel</Button>
            <Button
              onClick={handleConfirmSelection}
              variant="contained"
              color="primary"
            >
              Confirm ({tempSelectedEmployees.length} selected)
            </Button>
          </DialogActions>
        </Dialog>

        {/* Notification Snackbars */}
        <Snackbar
          open={!!error}
          autoHideDuration={6000}
          onClose={() => setError(null)}
        >
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        </Snackbar>

        <Snackbar
          open={!!success}
          autoHideDuration={6000}
          onClose={() => setSuccess(null)}
        >
          <Alert severity="success" onClose={() => setSuccess(null)}>
            {success}
          </Alert>
        </Snackbar>
      </Container>
    </LocalizationProvider>
  );
};

export default LeaveType;