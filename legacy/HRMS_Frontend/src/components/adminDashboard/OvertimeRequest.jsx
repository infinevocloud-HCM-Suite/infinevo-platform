import React, { useEffect, useState, useContext } from 'react';
import axios from 'axios';
import {
  Container,
  Typography,
  TextField,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Grid,
  Chip,
  Box,
  CircularProgress,
  Alert,
  Tooltip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextareaAutosize
} from '@mui/material';
import {
  Search,
  Check,
  Close,
  Refresh,
  ChevronLeft,
  ChevronRight,
  Visibility
} from '@mui/icons-material';
import API_BASE_URL from '../config/apiConfig';
import { userContext } from '../context/ContextProvider';

const OvertimeRequest = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const [overtimeRequests, setOvertimeRequests] = useState([]);
  const [searchId, setSearchId] = useState('');
  const [filterStatus, setFilterStatus] = useState('All');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [selectedAction, setSelectedAction] = useState(null);
  const [comment, setComment] = useState('');
  const [employeeId, setEmployeeId] = useState('');

  const hasAction = (actionName) => actions.includes(actionName);
  const recordsPerPage = 5;

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        setEmployeeId(payload.empId);
        fetchOvertimeRequests(payload.empId);
      } catch (error) {
        console.error('Error parsing token:', error);
        setError('Failed to authenticate. Please login again.');
      }
    }
  }, []);

  const fetchOvertimeRequests = async (empId) => {
    setLoading(true);
    setError(null);
    try {
      let endpoint;
      if (role === 'employee') {
        endpoint = '/overtime'; // Gets requests for logged-in employee
      } else if (role === 'manager') {
        endpoint = '/overtime/manager'; // Gets requests assigned to manager
      } else {
        endpoint = '/overtime/all'; // Gets all requests for admin/hr
      }

      const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });
      
      const processedData = response.data.map(request => ({
        ...request,
        id: request.id,
        employeeId: request.employeeId || 'N/A',
        employeeName: request.employeeName || 'N/A',
        category: request.category || 'N/A',
        project: request.project || 'N/A',
        notes: request.notes || 'N/A',
        managerStatus: request.managerStatus || 'PENDING',
        hrStatus: request.hrStatus || 'PENDING',
        managerComment: request.managerComment || '',
        hrComment: request.hrComment || '',
        createdAt: request.createdAt,
        startTime: request.startTime,
        endTime: request.endTime,
        managerEmployeeId: request.managerEmployeeId || '',
        managerUpdatedAt: request.managerUpdatedAt,
        hrUpdatedAt: request.hrUpdatedAt,
        durationHours: request.durationHours || 0
      })).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      setOvertimeRequests(processedData);
    } catch (error) {
      console.error('Error fetching overtime requests:', error);
      setError(error.response?.data?.message || 'Failed to load overtime requests. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

  const handleViewRequest = async (id) => {
    try {
      const response = await axios.get(`${API_BASE_URL}/overtime/${id}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });
      setSelectedRequest(response.data);
      setViewDialogOpen(true);
    } catch (error) {
      console.error('Error fetching overtime request:', error);
      setError(error.response?.data?.message || 'Failed to load request details. Please try again.');
    }
  };

  const handleActionClick = (request, action) => {
    setSelectedRequest(request);
    setSelectedAction(action);
    setComment('');
    setActionDialogOpen(true);
  };

  const handleStatusUpdate = async () => {
    if (!comment.trim()) {
      setError('Please enter a comment before submitting');
      return;
    }

    try {
      await axios.put(`${API_BASE_URL}/overtime/${selectedRequest.id}/status`, null, {
        params: { 
          status: selectedAction,
          comment: comment 
        },
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });
      
      setSuccess(`Request ${selectedAction.toLowerCase()} successfully!`);
      fetchOvertimeRequests(employeeId);
      setActionDialogOpen(false);
    } catch (error) {
      console.error('Error updating status:', error);
      setError(error.response?.data?.message || 'Failed to update status. Please try again.');
    }
  };

  const filteredRequests = overtimeRequests.filter(req => {
    const matchesId = req.employeeId?.toString().toLowerCase().includes(searchId.toLowerCase());
    const matchesStatus = filterStatus === 'All' || 
      (req.managerStatus && req.managerStatus.toLowerCase() === filterStatus.toLowerCase()) || 
      (req.hrStatus && req.hrStatus.toLowerCase() === filterStatus.toLowerCase());
    return matchesId && matchesStatus;
  });

  const totalPages = Math.ceil(filteredRequests.length / recordsPerPage);
  const paginatedRequests = filteredRequests.slice(
    currentPage * recordsPerPage,
    (currentPage + 1) * recordsPerPage
  );

  const handlePrevPage = () => {
    if (currentPage > 0) setCurrentPage(currentPage - 1);
  };

  const handleNextPage = () => {
    if (currentPage < totalPages - 1) setCurrentPage(currentPage + 1);
  };

  const getStatusColor = (status) => {
    switch ((status || '').toLowerCase()) {
      case 'approved': return 'success';
      case 'rejected': return 'error';
      case 'pending': return 'warning';
      default: return 'default';
    }
  };

  const formatDateTime = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return 'Invalid Date';
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
    } catch {
      return 'Invalid Date';
    }
  };

  const handleCloseSnackbar = () => {
    setError(null);
    setSuccess(null);
  };

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
        <Typography variant="h4" fontWeight={700} color="primary.main">
          {role === 'employee' ? 'My Overtime Requests' : 
           role === 'manager' ? 'Overtime Approvals' : 'All Overtime Requests'}
        </Typography>
        <Button
          variant="outlined"
          startIcon={<Refresh />}
          onClick={() => fetchOvertimeRequests(employeeId)}
          disabled={loading}
          sx={{ borderRadius: '20px', fontWeight: 600 }}
        >
          {loading ? 'Refreshing...' : 'Refresh'}
        </Button>
      </Box>

      <Grid container spacing={3} mb={4}>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            variant="outlined"
            placeholder="Search by Employee ID"
            value={searchId}
            onChange={(e) => {
              setSearchId(e.target.value);
              setCurrentPage(0);
            }}
            InputProps={{
              startAdornment: <Search sx={{ color: 'primary.main', mr: 1 }} />
            }}
            sx={{ borderRadius: 2 }}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <FormControl fullWidth>
            <InputLabel>Status Filter</InputLabel>
            <Select
              value={filterStatus}
              onChange={(e) => {
                setFilterStatus(e.target.value);
                setCurrentPage(0);
              }}
              label="Status Filter"
              sx={{ borderRadius: 2 }}
            >
              <MenuItem value="All">All</MenuItem>
              <MenuItem value="Pending">Pending</MenuItem>
              <MenuItem value="Approved">Approved</MenuItem>
              <MenuItem value="Rejected">Rejected</MenuItem>
            </Select>
          </FormControl>
        </Grid>
      </Grid>

      {error && (
        <Alert severity="error" onClose={handleCloseSnackbar} sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" onClose={handleCloseSnackbar} sx={{ mb: 3 }}>
          {success}
        </Alert>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TableContainer component={Paper} elevation={3} sx={{ borderRadius: 3 }}>
            <Table>
              <TableHead sx={{ backgroundColor: '#1976d2' }}>
                <TableRow>
                  {['Employee ID', 'Name', 'Category', 'Start Time', 'End Time', 'Project', 
                    'Manager Status', 'HR Status', 'Manager Comment', 'HR Comment', 'Actions'].map((head, idx) => (
                    <TableCell key={idx} sx={{ color: 'white', fontWeight: 600 }}>
                      {head}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {paginatedRequests.length > 0 ? paginatedRequests.map((request) => (
                  <TableRow key={request.id} hover>
                    <TableCell>{request.employeeId}</TableCell>
                    <TableCell>{request.employeeName}</TableCell>
                    <TableCell>{request.category}</TableCell>
                    <TableCell>{formatDateTime(request.startTime)}</TableCell>
                    <TableCell>{formatDateTime(request.endTime)}</TableCell>
                    <TableCell>{request.project}</TableCell>
                    <TableCell>
                      <Chip
                        label={request.managerStatus}
                        color={getStatusColor(request.managerStatus)}
                        variant="soft"
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={request.hrStatus}
                        color={getStatusColor(request.hrStatus)}
                        variant="soft"
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      {request.managerComment || 'N/A'}
                    </TableCell>
                    <TableCell>
                      {request.hrComment || 'N/A'}
                    </TableCell>
                    <TableCell>
                      <Box display="flex" gap={1}>
                        {hasAction("VIEW_EMPLOYEES_OVERTIME_REQUESTS") && (
                          <Tooltip title="View Details">
                            <IconButton
                              color="primary"
                              onClick={() => handleViewRequest(request.id)}
                              aria-label="view details"
                            >
                              <Visibility />
                            </IconButton>
                          </Tooltip>
                        )}

                        {/* Manager Actions */}
                        {role === 'manager' && request.managerStatus === 'PENDING' && (
                          <>
                            <Tooltip title="Approve">
                              <IconButton
                                color="success"
                                onClick={() => handleActionClick(request, 'APPROVED')}
                                aria-label="approve"
                              >
                                <Check />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Reject">
                              <IconButton
                                color="error"
                                onClick={() => handleActionClick(request, 'REJECTED')}
                                aria-label="reject"
                              >
                                <Close />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}

                        {/* HR Actions */}
                        {role === 'hr' && request.managerStatus === 'APPROVED' && request.hrStatus === 'PENDING' && (
                          <>
                            <Tooltip title="Approve">
                              <IconButton
                                color="success"
                                onClick={() => handleActionClick(request, 'APPROVED')}
                                aria-label="approve"
                              >
                                <Check />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Reject">
                              <IconButton
                                color="error"
                                onClick={() => handleActionClick(request, 'REJECTED')}
                                aria-label="reject"
                              >
                                <Close />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                )) : (
                  <TableRow>
                    <TableCell colSpan={11} align="center" sx={{ py: 4 }}>
                      <Typography variant="body1" color="textSecondary">
                        No overtime requests found
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {filteredRequests.length > recordsPerPage && (
            <Box display="flex" justifyContent="center" alignItems="center" mt={3} gap={2}>
              <IconButton
                onClick={handlePrevPage}
                disabled={currentPage === 0}
                aria-label="previous page"
              >
                <ChevronLeft />
              </IconButton>
              <Typography variant="body2" fontWeight={500}>
                Page {currentPage + 1} of {totalPages}
              </Typography>
              <IconButton
                onClick={handleNextPage}
                disabled={currentPage === totalPages - 1}
                aria-label="next page"
              >
                <ChevronRight />
              </IconButton>
            </Box>
          )}
        </>
      )}

      {/* View Dialog */}
      <Dialog
        open={viewDialogOpen}
        onClose={() => setViewDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Overtime Request Details</DialogTitle>
        <DialogContent dividers>
          {selectedRequest && (
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Employee ID:</Typography>
                <Typography>{selectedRequest.employeeId}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Employee Name:</Typography>
                <Typography>{selectedRequest.employeeName}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Category:</Typography>
                <Typography>{selectedRequest.category}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Manager Status:</Typography>
                <Chip
                  label={selectedRequest.managerStatus}
                  color={getStatusColor(selectedRequest.managerStatus)}
                  sx={{ fontWeight: 600 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>HR Status:</Typography>
                <Chip
                  label={selectedRequest.hrStatus}
                  color={getStatusColor(selectedRequest.hrStatus)}
                  sx={{ fontWeight: 600 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Start Time:</Typography>
                <Typography>{formatDateTime(selectedRequest.startTime)}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>End Time:</Typography>
                <Typography>{formatDateTime(selectedRequest.endTime)}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Project:</Typography>
                <Typography>{selectedRequest.project}</Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle1" fontWeight={600}>Manager ID:</Typography>
                <Typography>{selectedRequest.managerEmployeeId || 'N/A'}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle1" fontWeight={600}>Manager Comment:</Typography>
                <TextareaAutosize
                  minRows={2}
                  value={selectedRequest.managerComment || 'No comment provided'}
                  readOnly
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid #ccc',
                    borderRadius: '4px',
                    fontFamily: 'inherit',
                    fontSize: '0.875rem'
                  }}
                />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle1" fontWeight={600}>HR Comment:</Typography>
                <TextareaAutosize
                  minRows={2}
                  value={selectedRequest.hrComment || 'No comment provided'}
                  readOnly
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid #ccc',
                    borderRadius: '4px',
                    fontFamily: 'inherit',
                    fontSize: '0.875rem'
                  }}
                />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle1" fontWeight={600}>Notes:</Typography>
                <TextareaAutosize
                  minRows={3}
                  value={selectedRequest.notes}
                  readOnly
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid #ccc',
                    borderRadius: '4px',
                    fontFamily: 'inherit',
                    fontSize: '0.875rem'
                  }}
                />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle1" fontWeight={600}>Created At:</Typography>
                <Typography>{formatDate(selectedRequest.createdAt)}</Typography>
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setViewDialogOpen(false)}
            variant="contained"
            color="primary"
          >
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Action Dialog */}
      <Dialog
        open={actionDialogOpen}
        onClose={() => setActionDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{`${selectedAction === 'APPROVED' ? 'Approve' : 'Reject'} Request`}</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body1" gutterBottom>
            Please provide a comment for your {selectedAction === 'APPROVED' ? 'approval' : 'rejection'}:
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={4}
            variant="outlined"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder={`Enter your comment for ${selectedAction === 'APPROVED' ? 'approval' : 'rejection'}...`}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setActionDialogOpen(false)}
            color="inherit"
          >
            Cancel
          </Button>
          <Button
            onClick={handleStatusUpdate}
            color="primary"
            variant="contained"
            disabled={!comment.trim()}
          >
            Submit
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default OvertimeRequest;