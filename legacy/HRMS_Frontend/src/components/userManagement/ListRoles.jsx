import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Container,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  IconButton,
  Tooltip,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Snackbar,
  Alert,
  CircularProgress
} from '@mui/material';
import { EditOutlined, DeleteOutlined, AddCircleOutline, SettingsInputComponentOutlined } from '@mui/icons-material';
import { useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const ListRoles = () => {
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const location = useLocation();
  const [roles, setRoles] = useState([]);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState(null);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarSeverity, setSnackbarSeverity] = useState('success');
  const [deleting, setDeleting] = useState(false);

  const hasAction = (actionName) => actions.includes(actionName);

  const fetchRoles = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${API_BASE_URL}/roles`);
      setRoles(Array.isArray(response?.data) ? response.data : []);
      setError(null);
    } catch (err) {
      setError('Failed to fetch roles. Please try again later.');
      console.error('Error fetching roles:', err);
      setRoles([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
  }, []);

  useEffect(() => {
    if (location.state?.success) {
      setSnackbarMessage(location.state.success);
      setSnackbarSeverity('success');
      setSnackbarOpen(true);
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state, navigate, location.pathname]);

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleEdit = (id) => {
    navigate(`/${role}/edit-role/${id}`);
  };

  const handleDeleteClick = (id) => {
    setRoleToDelete(id);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    try {
      setDeleting(true);
      await axios.delete(`${API_BASE_URL}/roles/${roleToDelete}`);
      await fetchRoles(); // Refresh the list
      setSnackbarMessage('Role deleted successfully!');
      setSnackbarSeverity('success');
      setSnackbarOpen(true);
    } catch (err) {
      setSnackbarMessage('Failed to delete role. Please try again.');
      setSnackbarSeverity('error');
      setSnackbarOpen(true);
      console.error('Error deleting role:', err);
    } finally {
      setDeleteDialogOpen(false);
      setRoleToDelete(null);
      setDeleting(false);
    }
  };

  const handleDeleteCancel = () => {
    setDeleteDialogOpen(false);
    setRoleToDelete(null);
  };

  const handleMapping = (roleId) => {
    navigate(`/${role}/roles/mapping/${roleId}`);
  };

  const handleCreateNew = () => {
    navigate(`/${role}/create-role`);
  };

  const handleCloseSnackbar = () => {
    setSnackbarOpen(false);
  };

  const getCurrentPageRoles = () => {
    if (!Array.isArray(roles)) return [];
    return roles.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Paper elevation={3} sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 'bold' }}>
            Roles
          </Typography>
          {hasAction("CREATE_ROLE") && (
            <Button
              variant="contained"
              startIcon={<AddCircleOutline />}
              onClick={handleCreateNew}
            >
              Create New Role
            </Button>
          )}
        </Box>
        <Divider sx={{ mb: 3 }} />
        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <TableContainer component={Paper} elevation={0}>
              <Table>
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell sx={{ fontWeight: 'bold' }}>Role Name</TableCell>
                    <TableCell sx={{ fontWeight: 'bold' }}>Description</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', width: '180px' }} align="center">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {getCurrentPageRoles().length > 0 ? (
                    getCurrentPageRoles().map((role) => (
                      <TableRow key={role.roleId} hover>
                        <TableCell>
                          <Typography fontWeight="medium">{role.roleName}</Typography>
                        </TableCell>
                        <TableCell>{role.description}</TableCell>
                        <TableCell align="center">
                          {hasAction("UPDATE_ROLE") && (
                            <Tooltip title="Edit">
                              <IconButton
                                color="primary"
                                onClick={() => handleEdit(role.roleId)}
                              >
                                <EditOutlined />
                              </IconButton>
                            </Tooltip>
                          )}
                          {hasAction("PERMISSION_MAPPING") && (
                            <Tooltip title="Permissions Mapping">
                              <IconButton
                                color="secondary"
                                onClick={() => handleMapping(role.roleId)}
                              >
                                <SettingsInputComponentOutlined />
                              </IconButton>
                            </Tooltip>
                          )}
                          {hasAction("DELETE_ROLE") && (
                            <Tooltip title="Delete">
                              <IconButton
                                color="error"
                                onClick={() => handleDeleteClick(role.roleId)}
                              >
                                <DeleteOutlined />
                              </IconButton>
                            </Tooltip>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={3} align="center" sx={{ py: 4 }}>
                        <Typography variant="body1">No roles found</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={[5, 10, 25]}
              component="div"
              count={roles.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
              sx={{ mt: 2 }}
            />
          </>
        )}
      </Paper>

      <Dialog
        open={deleteDialogOpen}
        onClose={handleDeleteCancel}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">Confirm Role Deletion</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            Are you sure you want to delete this role? This action cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDeleteCancel} disabled={deleting}>
            Cancel
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" autoFocus disabled={deleting}>
            {deleting ? <CircularProgress size={24} /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert onClose={handleCloseSnackbar} severity={snackbarSeverity} sx={{ width: '100%' }}>
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Container>
  );
};

export default ListRoles;