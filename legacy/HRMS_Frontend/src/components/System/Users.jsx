import React, { useState } from "react";
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TextField,
  IconButton,
  Tabs,
  Tab,
  Box,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  Radio,
  RadioGroup,
  FormControlLabel,
  FormControl,
  Snackbar,
  Alert,
  InputAdornment,
  Autocomplete,
  InputLabel,
  Select,
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import SearchIcon from "@mui/icons-material/Search";
import countryList from "country-list";

const countryOptions = countryList.getNames(); // Get country names as an array

const Users = () => {
  const [tabIndex, setTabIndex] = useState(0);
  const [users, setUsers] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [openUserForm, setOpenUserForm] = useState(false);
  const [openInvitationForm, setOpenInvitationForm] = useState(false);
  const [openDeleteDialog, setOpenDeleteDialog] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedInvitation, setSelectedInvitation] = useState(null);
  const [selectedDesignation, setSelectedDesignation] = useState(null);
  const [passwordType, setPasswordType] = useState("system");
  const [snackbar, setSnackbar] = useState({ open: false, message: "", severity: "success" });

  // User Form Data
  const [userFormData, setUserFormData] = useState({
    userName: "",
    email: "",
    employee: "",
    userLevel: "",
    designation: "",
    defaultModule: "",
    password: "",
    confirmPassword: "",
  });

  // Invitation Form Data
  const [invitationFormData, setInvitationFormData] = useState({
    email: "",
    firstName: "",
    middleName: "",
    lastName: "",
    userLevel: "",
    country: "",
    linkStatus: "",
    expiryDate: "",
    notification: "",
  });

  // Designation State and Handlers
  const [openDesignationForm, setOpenDesignationForm] = useState(false);
  const [designations, setDesignations] = useState([]);
  const [designationFormData, setDesignationFormData] = useState({
    designation: "",
    description: "",
    table: [],
    permission: [],
  });

  // Open User Form
  const handleOpenUserForm = (user = null) => {
    setOpenUserForm(true);
    if (user) {
      setSelectedUser(user);
      setUserFormData({ ...user });
      setPasswordType(user.password ? "custom" : "system");
    } else {
      resetUserForm();
    }
  };

  // Close User Form
  const handleCloseUserForm = () => {
    setOpenUserForm(false);
    resetUserForm();
  };

  // Reset User Form
  const resetUserForm = () => {
    setSelectedUser(null);
    setPasswordType("system");
    setUserFormData({
      userName: "",
      email: "",
      employee: "",
      userLevel: "",
      designation: "",
      defaultModule: "",
      password: "",
      confirmPassword: "",
    });
  };

  // Handle User Form Change
  const handleUserFormChange = (e) => {
    setUserFormData({ ...userFormData, [e.target.name]: e.target.value });
  };

  // Handle User Form Submit
  const handleUserFormSubmit = async (e) => {
    e.preventDefault();

    if (
      !userFormData.userName ||
      !userFormData.email ||
      !userFormData.userLevel ||
      !userFormData.employee ||
      !userFormData.designation ||
      !userFormData.defaultModule
    ) {
      setSnackbar({ open: true, message: "Please fill in all required fields!", severity: "error" });
      return;
    }

    if (!/\S+@\S+\.\S+/.test(userFormData.email)) {
      setSnackbar({ open: true, message: "Enter a valid email address!", severity: "error" });
      return;
    }

    if (passwordType === "custom") {
      if (userFormData.password.length < 10 || userFormData.password.length > 16) {
        setSnackbar({ open: true, message: "Password must be 10-16 characters long!", severity: "error" });
        return;
      }
      if (userFormData.password !== userFormData.confirmPassword) {
        setSnackbar({ open: true, message: "Passwords do not match!", severity: "error" });
        return;
      }
    }

    if (selectedUser) {
      setUsers(users.map((user) => (user.email === selectedUser.email ? userFormData : user)));
      setSnackbar({ open: true, message: "User updated successfully!", severity: "success" });
    } else {
      setUsers([...users, { ...userFormData }]);
      setSnackbar({ open: true, message: "User created successfully!", severity: "success" });
    }

    handleCloseUserForm();
  };

  // Handle Delete User
  const handleDeleteUser = async () => {
    setUsers(users.filter((user) => user !== selectedUser));
    setOpenDeleteDialog(false);
    setSnackbar({ open: true, message: "User deleted successfully!", severity: "success" });
  };

  // Open Invitation Form
  const handleOpenInvitationForm = (invitation = null) => {
    setOpenInvitationForm(true);
    if (invitation) {
      setSelectedInvitation(invitation);
      setInvitationFormData({ ...invitation });
    } else {
      resetInvitationForm();
    }
  };

  // Reset Invitation Form
  const resetInvitationForm = () => {
    setSelectedInvitation(null);
    setInvitationFormData({
      email: "",
      firstName: "",
      middleName: "",
      lastName: "",
      userLevel: "",
      country: "",
      linkStatus: "",
      expiryDate: "",
      notification: "",
    });
  };

  // Close Invitation Form
  const handleCloseInvitationForm = () => {
    setOpenInvitationForm(false);
    resetInvitationForm();
  };

  // Handle Invitation Form Change
  const handleInvitationFormChange = (e) => {
    setInvitationFormData({ ...invitationFormData, [e.target.name]: e.target.value });
  };

  // Handle Invitation Form Submit
  const handleInvitationFormSubmit = (e) => {
    e.preventDefault();
    if (selectedInvitation) {
      // Update existing invitation
      setInvitations(invitations.map((inv) => (inv.email === selectedInvitation.email ? invitationFormData : inv)));
      setSnackbar({ open: true, message: "Invitation updated successfully!", severity: "success" });
    } else {
      // Add new invitation
      setInvitations([...invitations, invitationFormData]);
      setSnackbar({ open: true, message: "Invitation created successfully!", severity: "success" });
    }
    handleCloseInvitationForm();
  };

  // Handle Delete Invitation
  const handleDeleteInvitation = async () => {
    setInvitations(invitations.filter((inv) => inv.email !== selectedInvitation.email));
    setOpenDeleteDialog(false);
    setSnackbar({ open: true, message: "Invitation deleted successfully!", severity: "success" });
  };

  // Designation Handlers
  const handleOpenDesignationForm = (designation = null) => {
    setOpenDesignationForm(true);
    if (designation) {
      setSelectedDesignation(designation);
      setDesignationFormData({ ...designation });
    } else {
      resetDesignationForm();
    }
  };

  const resetDesignationForm = () => {
    setSelectedDesignation(null);
    setDesignationFormData({ designation: "", description: "", table: [], permission: [] });
  };

  const handleCloseDesignationForm = () => {
    setOpenDesignationForm(false);
    resetDesignationForm();
  };

  const handleDesignationChange = (e) => {
    const { name, value } = e.target;
    setDesignationFormData((prev) => ({
      ...prev,
      [name]: name === "table" || name === "permission" ? (typeof value === "string" ? value.split(",") : value) : value,
    }));
  };

  const handleDesignationSubmit = (e) => {
    e.preventDefault();
    if (selectedDesignation) {
      // Update existing designation
      setDesignations(designations.map((des) => (des.designation === selectedDesignation.designation ? designationFormData : des)));
      setSnackbar({ open: true, message: "Designation updated successfully!", severity: "success" });
    } else {
      // Add new designation
      setDesignations([...designations, designationFormData]);
      setSnackbar({ open: true, message: "Designation created successfully!", severity: "success" });
    }
    handleCloseDesignationForm();
  };

  // Handle Delete Designation
  const handleDeleteDesignation = async () => {
    setDesignations(designations.filter((des) => des.designation !== selectedDesignation.designation));
    setOpenDeleteDialog(false);
    setSnackbar({ open: true, message: "Designation deleted successfully!", severity: "success" });
  };

  return (
    <div style={{ padding: 20 }}>
      <Tabs value={tabIndex} onChange={(event, newValue) => setTabIndex(newValue)}>
        <Tab label="Users" />
        <Tab label="Designation" />
        <Tab label="User Invitations" />
      </Tabs>

      {/* Users Tab */}
      {tabIndex === 0 && (
        <div>
          <Box display="flex" justifyContent="space-between" alignItems="center" marginBottom={2}>
            <Button variant="contained" color="primary" startIcon={<AddIcon />} onClick={() => handleOpenUserForm()}>
              Create New User
            </Button>
            <TextField
              size="small"
              placeholder="Search User"
              variant="outlined"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell><b>ID</b></TableCell>
                  <TableCell><b>Username</b></TableCell>
                  <TableCell><b>Email</b></TableCell>
                  <TableCell><b>User Level</b></TableCell>
                  <TableCell><b>Actions</b></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {users
                  .filter((user) => user.userName.toLowerCase().includes(searchTerm.toLowerCase()))
                  .map((user, index) => (
                    <TableRow key={index}>
                      <TableCell>{index + 1}</TableCell>
                      <TableCell>{user.userName}</TableCell>
                      <TableCell>{user.email}</TableCell>
                      <TableCell>{user.userLevel}</TableCell>
                      <TableCell>
                        <IconButton color="primary" onClick={() => handleOpenUserForm(user)}>
                          <EditIcon />
                        </IconButton>
                        <IconButton
                          color="error"
                          onClick={() => {
                            setSelectedUser(user);
                            setOpenDeleteDialog(true);
                          }}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      )}

      {/* Designation Tab */}
      {tabIndex === 1 && (
        <div>
          <Box display="flex" justifyContent="space-between" alignItems="center" marginBottom={2}>
            <Button variant="contained" color="primary" startIcon={<AddIcon />} onClick={() => handleOpenDesignationForm()}>
              Add Designation
            </Button>
            <TextField
              size="small"
              placeholder="Search Designation"
              variant="outlined"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell><b>ID</b></TableCell>
                  <TableCell><b>Designation</b></TableCell>
                  <TableCell><b>Table</b></TableCell>
                  <TableCell><b>Permission</b></TableCell>
                  <TableCell><b>Actions</b></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {designations
                  .filter((designation) =>
                    designation.designation.toLowerCase().includes(searchTerm.toLowerCase())
                  )
                  .map((designation, index) => (
                    <TableRow key={index}>
                      <TableCell>{index + 1}</TableCell>
                      <TableCell>{designation.designation}</TableCell>
                      <TableCell>{designation.table.join(", ")}</TableCell>
                      <TableCell>{designation.permission.join(", ")}</TableCell>
                      <TableCell>
                        <IconButton color="primary" onClick={() => handleOpenDesignationForm(designation)}>
                          <EditIcon />
                        </IconButton>
                        <IconButton
                          color="error"
                          onClick={() => {
                            setSelectedDesignation(designation);
                            setOpenDeleteDialog(true);
                          }}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      )}

      {/* User Invitations Tab */}
      {tabIndex === 2 && (
        <div>
          <Box display="flex" justifyContent="space-between" alignItems="center" marginBottom={2}>
            <Button variant="contained" color="primary" startIcon={<AddIcon />} onClick={() => handleOpenInvitationForm()}>
              Add New
            </Button>
            <TextField
              size="small"
              placeholder="Search Invitation"
              variant="outlined"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell><b>ID</b></TableCell>
                  <TableCell><b>First Name</b></TableCell>
                  <TableCell><b>Last Name</b></TableCell>
                  <TableCell><b>Email</b></TableCell>
                  <TableCell><b>User Level</b></TableCell>
                  <TableCell><b>Link Status</b></TableCell>
                  <TableCell><b>Actions</b></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {invitations
                  .filter(
                    (invitation) =>
                      invitation.firstName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                      invitation.email.toLowerCase().includes(searchTerm.toLowerCase())
                  )
                  .map((invitation, index) => (
                    <TableRow key={index}>
                      <TableCell>{index + 1}</TableCell>
                      <TableCell>{invitation.firstName}</TableCell>
                      <TableCell>{invitation.lastName}</TableCell>
                      <TableCell>{invitation.email}</TableCell>
                      <TableCell>{invitation.userLevel}</TableCell>
                      <TableCell>{invitation.linkStatus}</TableCell>
                      <TableCell>
                        <IconButton color="primary" onClick={() => handleOpenInvitationForm(invitation)}>
                          <EditIcon />
                        </IconButton>
                        <IconButton
                          color="error"
                          onClick={() => {
                            setSelectedInvitation(invitation);
                            setOpenDeleteDialog(true);
                          }}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </TableContainer>
        </div>
      )}

      {/* User Form Dialog */}
      <Dialog open={openUserForm} onClose={handleCloseUserForm} maxWidth="sm" fullWidth>
        <DialogTitle>{selectedUser ? "Edit User" : "Create User"}</DialogTitle>
        <DialogContent>
          <Box component="form" display="flex" flexDirection="column" gap={2} mt={1} onSubmit={handleUserFormSubmit}>
            <TextField
              fullWidth
              required
              label="User Name"
              name="userName"
              value={userFormData.userName}
              onChange={handleUserFormChange}
            />
            <TextField
              fullWidth
              required
              label="Email"
              name="email"
              value={userFormData.email}
              onChange={handleUserFormChange}
            />
            <TextField
              fullWidth
              required
              label="Employee"
              name="employee"
              value={userFormData.employee}
              onChange={handleUserFormChange}
            />
            <TextField
              select
              label="User Level"
              name="userLevel"
              value={userFormData.userLevel}
              onChange={handleUserFormChange}
              required
              fullWidth
            >
              <MenuItem value="Admin">Admin</MenuItem>
              <MenuItem value="User">User</MenuItem>
            </TextField>
            <FormControl fullWidth required>
              <TextField
                select
                label="Designation"
                name="designation"
                value={userFormData.designation}
                onChange={handleUserFormChange}
              >
                <MenuItem value="Manager">Manager</MenuItem>
                <MenuItem value="Team Lead">Team Lead</MenuItem>
                <MenuItem value="Software Engineer">Software Engineer</MenuItem>
                <MenuItem value="Intern">Intern</MenuItem>
              </TextField>
            </FormControl>
            <FormControl fullWidth required>
              <TextField
                select
                label="Default Module"
                name="defaultModule"
                value={userFormData.defaultModule}
                onChange={handleUserFormChange}
              >
                <MenuItem value="Module 1">Module 1</MenuItem>
                <MenuItem value="Module 2">Module 2</MenuItem>
                <MenuItem value="Module 3">Module 3</MenuItem>
                <MenuItem value="Module 4">Module 4</MenuItem>
              </TextField>
            </FormControl>
            <FormControl>
              <RadioGroup row value={passwordType} onChange={(e) => setPasswordType(e.target.value)}>
                <FormControlLabel value="system" control={<Radio />} label="System Generated" />
                <FormControlLabel value="custom" control={<Radio />} label="Custom Password" />
              </RadioGroup>
            </FormControl>

            {passwordType === "custom" && (
              <>
                <TextField
                  fullWidth
                  required
                  type="password"
                  label="Password"
                  name="password"
                  value={userFormData.password}
                  onChange={handleUserFormChange}
                />
                <TextField
                  fullWidth
                  required
                  type="password"
                  label="Confirm Password"
                  name="confirmPassword"
                  value={userFormData.confirmPassword}
                  onChange={handleUserFormChange}
                />
              </>
            )}

            <DialogActions>
              <Button onClick={handleCloseUserForm} variant="outlined" color="error">
                Cancel
              </Button>
              <Button type="submit" variant="contained" color="primary">
                Save
              </Button>
            </DialogActions>
          </Box>
        </DialogContent>
      </Dialog>

      {/* Invitation Form Dialog */}
      <Dialog open={openInvitationForm} onClose={handleCloseInvitationForm} maxWidth="sm" fullWidth>
        <DialogTitle>{selectedInvitation ? "Edit Invitation" : "Create Invitation"}</DialogTitle>
        <DialogContent>
          <form
            onSubmit={handleInvitationFormSubmit}
            style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 10 }}
          >
            <TextField
              label="Email"
              name="email"
              value={invitationFormData.email}
              onChange={handleInvitationFormChange}
              required
              fullWidth
            />
            <TextField
              label="First Name"
              name="firstName"
              value={invitationFormData.firstName}
              onChange={handleInvitationFormChange}
              required
              fullWidth
            />
            <TextField
              label="Middle Name"
              name="middleName"
              value={invitationFormData.middleName}
              onChange={handleInvitationFormChange}
              fullWidth
            />
            <TextField
              label="Last Name"
              name="lastName"
              value={invitationFormData.lastName}
              onChange={handleInvitationFormChange}
              required
              fullWidth
            />
            <TextField
              select
              label="User Level"
              name="userLevel"
              value={invitationFormData.userLevel}
              onChange={handleInvitationFormChange}
              required
              fullWidth
            >
              <MenuItem value="Admin">Admin</MenuItem>
              <MenuItem value="User">User</MenuItem>
            </TextField>
            <Autocomplete
              options={countryOptions}
              value={invitationFormData.country}
              onChange={(event, newValue) =>
                handleInvitationFormChange({ target: { name: "country", value: newValue } })
              }
              renderInput={(params) => (
                <TextField {...params} label="Country" name="country" fullWidth required />
              )}
            />
            <TextField
              select
              label="Link Status"
              name="linkStatus"
              value={invitationFormData.linkStatus}
              onChange={handleInvitationFormChange}
              fullWidth
            >
              <MenuItem value="Active">Active</MenuItem>
              <MenuItem value="Expired">Expired</MenuItem>
            </TextField>
            <TextField
              type="date"
              label="Expiry Link Date"
              name="expiryDate"
              value={invitationFormData.expiryDate}
              onChange={handleInvitationFormChange}
              fullWidth
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="Notification"
              name="notification"
              value={invitationFormData.notification}
              onChange={handleInvitationFormChange}
              fullWidth
            />
            <DialogActions>
              <Button onClick={handleCloseInvitationForm} variant="outlined" color="error">
                Cancel
              </Button>
              <Button type="submit" variant="contained" color="primary">
                Save
              </Button>
            </DialogActions>
          </form>
        </DialogContent>
      </Dialog>

      {/* Designation Form Dialog */}
      <Dialog open={openDesignationForm} onClose={handleCloseDesignationForm} maxWidth="sm" fullWidth>
        <DialogTitle>{selectedDesignation ? "Edit Designation" : "Add Designation"}</DialogTitle>
        <DialogContent>
          <form
            onSubmit={handleDesignationSubmit}
            style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 10 }}
          >
            <TextField
              label="Designation"
              name="designation"
              value={designationFormData.designation}
              onChange={handleDesignationChange}
              required
              fullWidth
            />
            <TextField
              label="Description"
              name="description"
              value={designationFormData.description}
              onChange={handleDesignationChange}
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel>Table</InputLabel>
              <Select multiple name="table" value={designationFormData.table} onChange={handleDesignationChange}>
                <MenuItem value="Table1">Table1</MenuItem>
                <MenuItem value="Table2">Table2</MenuItem>
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Permission</InputLabel>
              <Select
                multiple
                name="permission"
                value={designationFormData.permission}
                onChange={handleDesignationChange}
              >
                <MenuItem value="View">View</MenuItem>
                <MenuItem value="Edit">Edit</MenuItem>
                <MenuItem value="Add">Add</MenuItem>
                <MenuItem value="Delete">Delete</MenuItem>
              </Select>
            </FormControl>
            <DialogActions>
              <Button onClick={handleCloseDesignationForm} variant="outlined" color="error">
                Cancel
              </Button>
              <Button type="submit" variant="contained" color="primary">
                Save
              </Button>
            </DialogActions>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={openDeleteDialog} onClose={() => setOpenDeleteDialog(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>Are you sure you want to delete this item?</DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenDeleteDialog(false)} color="secondary">
            Cancel
          </Button>
          <Button
            onClick={() => {
              if (selectedUser) handleDeleteUser();
              else if (selectedInvitation) handleDeleteInvitation();
              else if (selectedDesignation) handleDeleteDesignation();
            }}
            color="error"
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar for Notifications */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert onClose={() => setSnackbar({ ...snackbar, open: false })} severity={snackbar.severity}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </div>
  );
};

export default Users;