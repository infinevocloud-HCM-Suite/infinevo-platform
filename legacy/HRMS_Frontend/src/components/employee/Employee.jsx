import axios from "axios";
import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TablePagination,
  IconButton,
  TextField,
  Button,
  Box,
  Typography,
  Snackbar,
  Alert,
  CircularProgress,
  InputAdornment,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import VisibilityIcon from "@mui/icons-material/Visibility";
import DeleteIcon from "@mui/icons-material/Delete";
import SearchIcon from "@mui/icons-material/Search";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const Employee = () => {
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const [employees, setEmployees] = useState([]);
  const [filteredEmployees, setFilteredEmployees] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "info"
  });
  const [deleteDialog, setDeleteDialog] = useState({
    open: false,
    employeeId: null,
    employeeName: ""
  });
  const navigate = useNavigate();

  const hasAction = (actionName) => actions.includes(actionName);

  const token = localStorage.getItem("token");

  useEffect(() => {
    const fetchEmployees = async () => {
      try {
        setLoading(true);

        const response = await axios.get(`${API_BASE_URL}/employees/all`, {
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
          }
        });

        const data = response.data.data || response.data;

        if (Array.isArray(data)) {
          setEmployees(data);
          setFilteredEmployees(data);
        } else {
          throw new Error("Invalid data format received from server");
        }
      } catch (error) {
        console.error("Error fetching employees:", error);
        showNotification(
          error.response?.data?.message || "Failed to fetch employees. Please try again later.",
          "error"
        );

        if (error.response?.status === 401) {
          navigate("/login");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchEmployees();
  }, [token, navigate]);

  const handleDeleteClick = (employeeId, employeeName) => {
    setDeleteDialog({
      open: true,
      employeeId,
      employeeName
    });
  };

  const handleDeleteConfirm = async () => {
    try {
      const response = await axios.delete(
        `${API_BASE_URL}/employees/${deleteDialog.employeeId}`,
        {
          headers: {
            "Authorization": `Bearer ${token}`
          }
        }
      );

      if (response.status === 200) {
        showNotification(`Employee "${deleteDialog.employeeName}" deleted successfully`, "success");
        setEmployees(prev => prev.filter(emp => emp.id !== deleteDialog.employeeId));
        setFilteredEmployees(prev => prev.filter(emp => emp.id !== deleteDialog.employeeId));
      } else {
        throw new Error(response.data?.message || "Failed to delete employee");
      }
    } catch (error) {
      console.error("Error deleting employee:", error);
      showNotification(
        error.response?.data?.message || "Failed to delete employee",
        "error"
      );
    } finally {
      setDeleteDialog({
        open: false,
        employeeId: null,
        employeeName: ""
      });
    }
  };

  const handleDeleteCancel = () => {
    setDeleteDialog({
      open: false,
      employeeId: null,
      employeeName: ""
    });
  };

  const showNotification = (message, severity = "info") => {
    setSnackbar({
      open: true,
      message,
      severity
    });
  };

  const handleCloseSnackbar = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  const handleSearch = (e) => {
    const term = e.target.value.toLowerCase();
    setSearchTerm(term);

    if (term === "") {
      setFilteredEmployees(employees);
    } else {
      const filtered = employees.filter(emp => {
        const personal = emp.personal || {};
        const work = emp.work || {};
        return (
          personal.firstName?.toLowerCase().includes(term) ||
          personal.lastName?.toLowerCase().includes(term) ||
          personal.empId?.toLowerCase().includes(term) ||
          personal.employmentStatus?.toLowerCase().includes(term) ||
          work.department?.toLowerCase().includes(term) ||
          work.jobTitle?.toLowerCase().includes(term) ||
          emp.id.toString().includes(term)
        );
      });
      setFilteredEmployees(filtered);
    }
    setPage(0);
  };

  const handleExportCSV = () => {
    try {
      const headers = [
        // Personal Information
        "ID",
        "Employee Number",
        "First Name",
        "Middle Name",
        "Last Name",
        "Date of Birth",
        "Gender",
        "Marital Status",
        "Nationality",
        "Ethnicity",
        "Employment Status",

        // Identification Information
        "Immigration Status",
        "Personal Tax ID",
        "Social Insurance",
        "ID Proof",
        "Document Name",
        "Document Number",

        // Work Information
        "Department",
        "Job Title",
        "Pay Grade",
        "Date of Joining",
        "Termination Date",
        "Workstation ID",
        "Time Zone",
        "Shift Start Time",
        "Shift End Time",

        // Contact Information
        "Residential Address",
        "Permanent Address",
        "City",
        "State",
        "Country",
        "Postal Code",
        "Work Email",
        "Personal Email",
        "Mobile Number",
        "Primary Emergency Contact Name",
        "Primary Emergency Contact Number",
        "Relationship to Primary Emergency Contact",
        "Secondary Emergency Contact Name",
        "Secondary Emergency Contact Number",
        "Relationship to Secondary Emergency Contact",
        "Family Doctor Name",
        "Family Doctor Contact Number",

        // Report Information
        "Manager",
        "Indirect Manager",
        "First Level Approver",
        "Second Level Approver",
        "Third Level Approver",
        "Notes"
      ].join(",");

      const dataRows = filteredEmployees.map(emp => {
        const personal = emp.personal || {};
        const identification = emp.identification || {};
        const work = emp.work || {};
        const contact = emp.contact || {};
        const report = emp.report || {};

        return [
          // Personal Information
          emp.id,
          personal.empId || "",
          personal.firstName || "",
          personal.middleName || "",
          personal.lastName || "",
          personal.dateOfBirth ? new Date(personal.dateOfBirth).toISOString().split('T')[0] : "",
          personal.gender || "",
          personal.maritalStatus || "",
          personal.nationality || "",
          personal.ethnicity || "",
          personal.employmentStatus || "",

          // Identification Information
          identification.immigrationStatus || "",
          identification.personalTaxId || "",
          identification.socialInsurance || "",
          identification.idProof || "",
          identification.documentName || "",
          identification.documentNumber || "",

          // Work Information
          work.department || "",
          work.jobTitle || "",
          work.payGrade || "",
          work.doj ? new Date(work.doj).toISOString().split('T')[0] : "",
          work.terminationDate ? new Date(work.terminationDate).toISOString().split('T')[0] : "",
          work.workstationId || "",
          work.timeZone || "",
          work.shiftStartTime || "",
          work.shiftEndTime || "",

          // Contact Information
          contact.residentialAddress || "",
          contact.permanentAddress || "",
          contact.city || "",
          contact.state || "",
          contact.country || "",
          contact.postalCode || "",
          contact.workEmail || "",
          contact.personalEmail || "",
          contact.mobileNumber || "",
          contact.primaryEmergencyContactName || "",
          contact.primaryEmergencyContactNumber || "",
          contact.relationshipToPrimaryEmergencyContact || "",
          contact.secondaryEmergencyContactName || "",
          contact.secondaryEmergencyContactNumber || "",
          contact.relationshipToSecondaryEmergencyContact || "",
          contact.familyDoctorName || "",
          contact.familyDoctorContactNumber || "",

          // Report Information
          report.manager || "",
          report.indirectManager || "",
          report.firstLevelApprover || "",
          report.secondLevelApprover || "",
          report.thirdLevelApprover || "",
          report.note || ""
        ].map(field => {
          if (typeof field === 'string') {
            return `"${field.replace(/"/g, '""')}"`;
          }
          return `"${field}"`;
        }).join(",");
      });

      const csvContent = `data:text/csv;charset=utf-8,${headers}\n${dataRows.join("\n")}`;

      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `employees_${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      showNotification("CSV exported successfully with all employee data", "success");
    } catch (error) {
      console.error("Error exporting CSV:", error);
      showNotification("Failed to export employee data", "error");
    }
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  if (loading) {
    return (
      <Box sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "80vh"
      }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialog.open}
        onClose={handleDeleteCancel}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">Confirm Employee Deletion</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            Are you sure you want to permanently delete employee "{deleteDialog.employeeName}"?
            This action cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} color="primary">
            Cancel
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            autoFocus
            variant="contained"
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* Main Content */}
      <Box sx={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        mb: 3,
        gap: 2,
        flexWrap: 'wrap'
      }}>
        <Typography variant="h4" color="primary">
          Employee Management
        </Typography>

        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <TextField
            size="small"
            placeholder="Search..."
            value={searchTerm}
            onChange={handleSearch}
            sx={{
              width: 200,
              '& .MuiOutlinedInput-root': {
                height: 40
              }
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
          />

          {hasAction("ADD_EMPLOYEE") && (
            <Button
              component={Link}
              to={`/${role}/add-employee`}
              variant="contained"
              color="primary"
              size="small"
            >
              Add Employee
            </Button>
          )}

          {hasAction("EXPORT_EMPLOYEE_DATA") && (
            <Button
              onClick={handleExportCSV}
              variant="contained"
              color="secondary"
              size="small"
            >
              Export CSV
            </Button>
          )}


        </Box>
      </Box>

      <TableContainer component={Paper} sx={{ mb: 2 }}>
        <Table>
          <TableHead sx={{ backgroundColor: "primary.light" }}>
            <TableRow>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>#</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Employee ID</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>First Name</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Last Name</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Employement Status</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Department</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Job Title</TableCell>
              <TableCell sx={{ fontWeight: "bold", color: "common.white" }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredEmployees.length > 0 ? (
              filteredEmployees
                .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                .map((emp, index) => (
                  <TableRow key={emp.id} hover>
                    <TableCell>{page * rowsPerPage + index + 1}</TableCell>
                    <TableCell>{emp.personal?.empId || "-"}</TableCell>
                    <TableCell>{emp.personal?.firstName || "-"}</TableCell>
                    <TableCell>{emp.personal?.lastName || "-"}</TableCell>
                    <TableCell>{emp.personal?.employmentStatus || "-"}</TableCell>
                    <TableCell>{emp.work?.department || "-"}</TableCell>
                    <TableCell>{emp.work?.jobTitle || "-"}</TableCell>
                    <TableCell>
                      {hasAction("UPDATE_EMPLOYEE") && (
                        <IconButton
                          color="primary"
                          onClick={() => navigate(`/${role}/edit-employee/${emp.id}`)}
                          title="Edit"
                        >
                          <EditIcon />
                        </IconButton>
                      )}
                      {hasAction("VIEW_EMPLOYEE") && (
                        <IconButton
                          color="info"
                          onClick={() => navigate(`/${role}/employee-details/${emp.id}`)}
                          title="View Details"
                        >
                          <VisibilityIcon />
                        </IconButton>
                      )}
                      {hasAction("DELETE_EMPLOYEE") && (
                        <IconButton
                          color="error"
                          onClick={() => handleDeleteClick(emp.id, `${emp.personal?.firstName || ''} ${emp.personal?.lastName || ''}`.trim())}
                          title="Delete"
                        >
                          <DeleteIcon />
                        </IconButton>
                      )}
                    </TableCell>
                  </TableRow>
                ))
            ) : (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <Typography variant="body1" color="text.secondary">
                    {employees.length === 0
                      ? "No employees found in the system"
                      : "No employees match your search criteria"}
                  </Typography>
                  {searchTerm && (
                    <Button
                      variant="text"
                      color="primary"
                      onClick={() => setSearchTerm("")}
                      sx={{ mt: 1 }}
                    >
                      Clear search
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {filteredEmployees.length > 0 && (
        <TablePagination
          rowsPerPageOptions={[5, 10, 25]}
          component="div"
          count={filteredEmployees.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      )}

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ width: "100%" }}
          variant="filled"
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default Employee;