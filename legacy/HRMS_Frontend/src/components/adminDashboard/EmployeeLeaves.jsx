import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  TextField,
  CircularProgress,
  Snackbar,
  Alert,
  TablePagination,
  InputAdornment,
  Container,
    FormControl,
  InputLabel,
  Select,
  MenuItem,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import VisibilityIcon from "@mui/icons-material/Visibility";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import { Checkbox } from "@mui/material";

 
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
 
const EmployeeLeaves = () => {
  const [employees, setEmployees] = useState([]);
  const [filteredEmployees, setFilteredEmployees] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  // const [selectedLeaveType, setSelectedLeaveType] = useState("");
  const [selectedLeaveTypes, setSelectedLeaveTypes] = useState([]);

const [leaveTypeOptions, setLeaveTypeOptions] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const navigate = useNavigate();
  
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();

  const [selectedEmployees, setSelectedEmployees] = useState([]);
  
 
  const hasAction = (actionName) => actions.includes(actionName);

  
 
 useEffect(() => {
  const fetchEmployees = async () => {
    try {
      setLoading(true);
      const response = await api.get("/employees/all");

      const data = response.data.data || response.data;
      if (Array.isArray(data)) {
        setEmployees(data);
        setFilteredEmployees(data);
      } else {
        throw new Error("Invalid data format");
      }
    } catch (err) {
      console.error("Error fetching employees:", err);
      setError(err.response?.data?.message || "Failed to fetch employees");
    } finally {
      setLoading(false);
    }
  };

  fetchEmployees();
}, []);

useEffect(() => {
  const fetchLeaveTypes = async () => {
    try {
      const response = await api.get("/leave-types");
      setLeaveTypeOptions(response.data.map((lt) => lt.name));
    } catch (err) {
      console.error("Failed to fetch leave types", err);
    }
  };

  fetchLeaveTypes();
}, []);

 
  useEffect(() => {
    if (searchTerm === "") {
      setFilteredEmployees(employees);
      setPage(0);
      return;
    }
 
    const filtered = employees.filter(emp => {
      const personal = emp.personal || {};
      return (
        personal.empId?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        personal.firstName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        personal.lastName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        `${personal.firstName || ""} ${personal.lastName || ""}`
          .toLowerCase()
          .includes(searchTerm.toLowerCase())
      );
    });
 
    setFilteredEmployees(filtered);
    setPage(0);
  }, [searchTerm, employees]);
 
  const handleViewLeaves = (employeeId) => {
    navigate(`/${role}/employee-leaves/${employeeId}`);
  };
 
  const handleCloseSnackbar = () => {
    setError(null);
  };

  const handleEmployeeSelect = (empId) => {
  setSelectedEmployees((prev) =>
    prev.includes(empId)
      ? prev.filter((id) => id !== empId)
      : [...prev, empId]
  );
};

const handleSelectAllClick = (event) => {
  if (event.target.checked) {
    const allVisible = filteredEmployees.map((e) => e.personal?.empId);
    setSelectedEmployees(allVisible);
  } else {
    setSelectedEmployees([]);
  }
};
 
  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };
 
  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };
 
  if (loading && employees.length === 0) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "80vh" }}>
        <CircularProgress />
      </Box>
    );
  }


//   const handleExportClick = async () => {
//   try {
//     const exportData = [];

//     for (const empId of selectedEmployees) {
//       const response = await api.get(`/employee-leave-types/${empId}`);
//       const allLeaves = response.data;

//       const filteredLeaves = selectedLeaveType
//         ? allLeaves.filter((lt) => lt.name === selectedLeaveType)
//         : allLeaves;

//       exportData.push({
//         empId,
//         leaves: filteredLeaves,
//       });
//     }

//     generatePrintableHTML(exportData);
//   } catch (err) {
//     console.error("Export failed:", err);
//     setError("Failed to export leave data");
//   }
// };

const handleExportClick = async () => {
  try {
    const exportData = [];

    for (const emp of filteredEmployees) {
      const personal = emp.personal || {};
      if (!selectedEmployees.includes(personal.empId)) continue;

      const response = await api.get(`/employee-leave-types/${personal.empId}`);
      const allLeaves = response.data;

      const filteredLeaves =
        selectedLeaveTypes.length > 0
          ? allLeaves.filter((lt) => selectedLeaveTypes.includes(lt.name))
          : allLeaves;

      exportData.push({
        empId: personal.empId,
        name: `${personal.firstName || ""} ${personal.lastName || ""}`,
        leaves: filteredLeaves,
      });
    }

    generatePrintableHTML(exportData);
  } catch (err) {
    console.error("Export failed:", err);
    setError("Failed to export leave data");
  }
};


const generatePrintableHTML = (data) => {
  const printWindow = window.open("", "_blank");
  if (!printWindow) return;

  const html = `
    <html>
      <head>
        <title>Leave Report</title>
        <style>
          body { font-family: Arial, sans-serif; padding: 20px; }
          h2 { margin-top: 40px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
          table { width: 100%; border-collapse: collapse; margin-top: 10px; }
          th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
          th { background-color: #f0f0f0; }
        </style>
      </head>
      <body>
        <h1>Selected Employee Leave Report</h1>
        ${data.map(emp => `
         
          <h2>${emp.name} (Employee ID: ${emp.empId})</h2>

          <table>
            <thead>
              <tr>
                <th>Leave Type</th>
                <th>Description</th>
                <th>Created On</th>
                <th>Allocation</th>
                <th>Carry Forward</th>
                <th>Remaining</th>
                <th>End Date</th>
              </tr>
            </thead>
            <tbody>
              ${emp.leaves.map(l => `
                <tr>
                  <td>${l.name}</td>
                  <td>${l.description || "-"}</td>
                  <td>${l.createdAt ? l.createdAt.slice(0, 10) : "-"}</td>
                  <td>${l.defaultDays} days</td>
                  <td>${l.carryForward ? "Yes" : "No"}</td>
                  <td>${l.remainingDays} days</td>
                  <td>${l.endDate ? l.endDate.slice(0, 10) : "No end date"}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        `).join("")}
      </body>
    </html>
  `;

  printWindow.document.write(html);
  printWindow.document.close();
  printWindow.print();
};

 
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 3 }}>
        Employee Leave Management
      </Typography>
 
   <Box sx={{ display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap", mb: 3 }}>

        <TextField
        
          size="small"
          placeholder="Search by ID or Name"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ width: 300 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
        />
        {/* <FormControl size="small" sx={{ minWidth: 200, ml: 2 }}>
  <InputLabel>Leave Type</InputLabel>
  <Select
    value={selectedLeaveType}
    onChange={(e) => setSelectedLeaveType(e.target.value)}
    label="Leave Type"
  >
    <MenuItem value="">All</MenuItem>
{Array.isArray(leaveTypeOptions) && leaveTypeOptions.map((type) => (
  <MenuItem key={type} value={type}>{type}</MenuItem>
))}

  </Select>
</FormControl> */}

<FormControl size="small" sx={{ minWidth: 250, ml: 2 }}>
  <InputLabel>Leave Types</InputLabel>
  <Select
    multiple
    value={selectedLeaveTypes}
    onChange={(e) => setSelectedLeaveTypes(e.target.value)}
    renderValue={(selected) => selected.join(', ')}
    label="Leave Types"
  >
    {Array.isArray(leaveTypeOptions) && leaveTypeOptions.map((type) => (
      <MenuItem key={type} value={type}>
        <Checkbox checked={selectedLeaveTypes.indexOf(type) > -1} />
        {type}
      </MenuItem>
    ))}
  </Select>
</FormControl>


      </Box>
 
      <TableContainer component={Paper}>
        <Table>
          <TableHead sx={{ backgroundColor: "primary.main" }}>
  <TableRow>
    <TableCell padding="checkbox">
      <Checkbox
        color="primary"
        indeterminate={
          selectedEmployees.length > 0 &&
          selectedEmployees.length < filteredEmployees.length
        }
        checked={
          filteredEmployees.length > 0 &&
          selectedEmployees.length === filteredEmployees.length
        }
        onChange={handleSelectAllClick}
      />
    </TableCell>
    <TableCell sx={{ color: "common.white" }}>Employee ID</TableCell>
    <TableCell sx={{ color: "common.white" }}>Name</TableCell>
    <TableCell sx={{ color: "common.white" }}>Department</TableCell>
    <TableCell sx={{ color: "common.white" }}>Job Title</TableCell>
    <TableCell sx={{ color: "common.white" }}>Actions</TableCell>
  </TableRow>
</TableHead>

          <TableBody>
            {filteredEmployees.length > 0 ? (
              filteredEmployees
                .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                .map((employee) => {
                  const personal = employee.personal || {};
                  const work = employee.work || {};
                  return (
           <TableRow key={employee.id} hover>
  <TableCell padding="checkbox">
    <Checkbox
      checked={selectedEmployees.includes(personal.empId)}
      onChange={() => handleEmployeeSelect(personal.empId)}
    />
  </TableCell>
  <TableCell>{personal.empId || "-"}</TableCell>
  <TableCell>{personal.firstName} {personal.lastName}</TableCell>
  <TableCell>{work.department || "-"}</TableCell>
  <TableCell>{work.jobTitle || "-"}</TableCell>
  <TableCell>
    {hasAction("VIEW_EMPLOYEES_LEAVES_BALANCES") && (
      <Button
        variant="outlined"
        startIcon={<VisibilityIcon />}
        onClick={() => handleViewLeaves(personal.empId)}
      >
        View Leaves
      </Button>
    )}
  </TableCell>
</TableRow>

                  );
                })
            ) : (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                  <Typography variant="body1">
                    {employees.length === 0
                      ? "No employees found"
                      : "No employees match your search criteria"}
                  </Typography>
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
      {selectedEmployees.length > 0 && (
  <Box mt={2} textAlign="right">
    <Button
      variant="contained"
      color="primary"
      onClick={handleExportClick}
    >
      Export Selected
    </Button>
  </Box>
)}

 
      <Snackbar
        open={!!error}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
      >
        <Alert severity="error" onClose={handleCloseSnackbar}>
          {error}
        </Alert>
      </Snackbar>
    </Container>
  );
};
 
export default EmployeeLeaves;






//with doc. report