import React, { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Container,
  Typography,
  Box,
  Chip,
  Avatar,
  Button,
  Paper,
  Grid,
  Divider,
  Alert,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField
} from '@mui/material';
import { ArrowBack, Check, Close } from '@mui/icons-material';
import { format } from 'date-fns';
import API_BASE_URL from '../config/apiConfig';
import { userContext } from '../context/ContextProvider';

const ViewLeaveRequestPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();

  const [leaveRequest, setLeaveRequest] = useState(null);
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [selectedAction, setSelectedAction] = useState('');
  const [comment, setComment] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        const [requestRes, typesRes] = await Promise.all([
          axios.get(`${API_BASE_URL}/leaves/${id}`, {
            headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
          }),
          axios.get(`${API_BASE_URL}/my-leave-types`, {
            headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
          })
        ]);

        setLeaveRequest(requestRes.data);
        setLeaveTypes(typesRes.data || []);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch leave details.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id]);

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      return format(new Date(dateString), 'MMM d, yyyy');
    } catch {
      return 'Invalid Date';
    }
  };

  const getStatusColor = (status) => {
    switch ((status || '').toUpperCase()) {
      case 'APPROVED': return 'success';
      case 'REJECTED': return 'error';
      case 'PENDING': return 'warning';
      default: return 'default';
    }
  };

  const renderLeaveTypes = () => {
    if (!leaveRequest?.manualDaysAllocation) return 'N/A';
    return Object.entries(leaveRequest.manualDaysAllocation)
      .map(([typeId, days]) => {
        const match = leaveTypes.find(t => t.id === parseInt(typeId));
        return match
          ? `${match.name} (${days} ${days > 1 ? 'days' : 'day'})`
          : `Type ${typeId} (${days} day${days > 1 ? 's' : ''})`;
      }).join(' + ');
  };

  const canTakeAction = () => {
    if (!leaveRequest) return false;
    if (role === 'reporting manager') {
      return leaveRequest.reportingManagerStatus === 'PENDING';
    }
    if (role === 'hr') {
      return leaveRequest.hrStatus === 'PENDING' &&
        (leaveRequest.reportingManagerStatus === 'APPROVED' || leaveRequest.reportingManagerStatus === 'REJECTED');
    }
    return false;
  };

  const handleStatusUpdate = async () => {
    try {
      const endpointRole = role === 'hr' ? 'hr' : 'reporting-manager';

      await axios.put(
        `${API_BASE_URL}/leaves/${leaveRequest.id}/status`,
        null,
        {
          params: {
            status: selectedAction,
            comment
          },
          headers: {
            Authorization: `Bearer ${localStorage.getItem('token')}`
          }
        }
      );

      // Update local state for instant feedback
      if (role === 'hr') {
        setLeaveRequest(prev => ({
          ...prev,
          hrStatus: selectedAction,
          hrComment: comment
        }));
      } else {
        setLeaveRequest(prev => ({
          ...prev,
          reportingManagerStatus: selectedAction,
          reportingManagerComment: comment
        }));
      }

      setActionDialogOpen(false);
      setComment('');
    } catch (err) {
      console.error('Failed to update leave status:', err);
      alert('Failed to update status.');
    }
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="80vh">
        <CircularProgress size={60} />
      </Box>
    );
  }

  if (error) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <Alert severity="error">{error}</Alert>
        <Button variant="outlined" startIcon={<ArrowBack />} onClick={() => navigate(-1)}>
          Back
        </Button>
      </Container>
    );
  }

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" fontWeight="bold">Leave Request Details</Typography>
        <Button variant="outlined" startIcon={<ArrowBack />} onClick={() => navigate(-1)}>
          Back
        </Button>
      </Box>

      <Paper elevation={3} sx={{ p: 3 }}>
        <Box display="flex" alignItems="center" mb={3}>
          <Avatar sx={{ width: 56, height: 56, mr: 2, bgcolor: 'primary.main' }}>
            {leaveRequest.employeeName.charAt(0)}
          </Avatar>
          <Box>
            <Typography variant="h5" fontWeight="bold">{leaveRequest.employeeName}</Typography>
            <Typography color="textSecondary">ID: {leaveRequest.employeeId}</Typography>
          </Box>
          <Box ml="auto">
            <Chip
              label={leaveRequest.reportingManagerStatus}
              color={getStatusColor(leaveRequest.reportingManagerStatus)}
              sx={{ fontWeight: 'bold' }}
            />
          </Box>
        </Box>

        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Typography fontWeight="bold">Request ID</Typography>
            <Typography mb={2}>#{leaveRequest.id}</Typography>

            <Typography fontWeight="bold">Leave Type</Typography>
            <Typography mb={2}>{renderLeaveTypes()}</Typography>

            <Typography fontWeight="bold">Duration</Typography>
            <Typography>
              {formatDate(leaveRequest.fromDate)} to {formatDate(leaveRequest.toDate)}
              {leaveRequest.isHalfDay && ` (${leaveRequest.halfDayPeriod} Half)`}
            </Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography fontWeight="bold">Total Days</Typography>
            <Typography mb={2}>{leaveRequest.totalDays}</Typography>

            <Typography fontWeight="bold">Submitted On</Typography>
            <Typography mb={2}>{formatDate(leaveRequest.createdDate)}</Typography>

            <Typography fontWeight="bold">Status</Typography>
            <Box display="flex" gap={1} flexWrap="wrap">
              <Chip label={`Manager: ${leaveRequest.reportingManagerStatus}`} color={getStatusColor(leaveRequest.reportingManagerStatus)} />
              <Chip label={`HR: ${leaveRequest.hrStatus}`} color={getStatusColor(leaveRequest.hrStatus)} />
            </Box>
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />

        <Typography variant="h6" fontWeight="bold" mb={1}>Reason for Leave</Typography>
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Typography whiteSpace="pre-wrap">{leaveRequest.reason}</Typography>
        </Paper>

        {leaveRequest.lateReason && (
          <>
            <Typography variant="h6" fontWeight="bold" mb={1}>Late Submission Reason</Typography>
            <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
              <Typography whiteSpace="pre-wrap">{leaveRequest.lateReason}</Typography>
            </Paper>
          </>
        )}

        {leaveRequest.reportingManagerComment && (
          <>
            <Typography fontWeight="bold">Manager's Comment</Typography>
            <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
              <Typography whiteSpace="pre-wrap">{leaveRequest.reportingManagerComment}</Typography>
            </Paper>
          </>
        )}

        {leaveRequest.hrComment && (
          <>
            <Typography fontWeight="bold">HR's Comment</Typography>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography whiteSpace="pre-wrap">{leaveRequest.hrComment}</Typography>
            </Paper>
          </>
        )}

        {/* Approve/Reject Buttons */}
        {canTakeAction() && (
          <Box mt={4} display="flex" justifyContent="flex-end" gap={2}>
            <Button
              variant="contained"
              color="error"
              startIcon={<Close />}
              onClick={() => {
                setSelectedAction('REJECTED');
                setActionDialogOpen(true);
              }}
            >
              Reject
            </Button>
            <Button
              variant="contained"
              color="success"
              startIcon={<Check />}
              onClick={() => {
                setSelectedAction('APPROVED');
                setActionDialogOpen(true);
              }}
            >
              Approve
            </Button>
          </Box>
        )}
      </Paper>

      {/* Action Dialog */}
      <Dialog
        open={actionDialogOpen}
        onClose={() => setActionDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {selectedAction === 'APPROVED' ? 'Approve Leave Request' : 'Reject Leave Request'}
        </DialogTitle>
        <DialogContent>
          <Typography mb={2}>
            You are about to {selectedAction.toLowerCase()} this request. Please add a comment.
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={4}
            label="Comment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleStatusUpdate}
            variant="contained"
            color={selectedAction === 'APPROVED' ? 'success' : 'error'}
            disabled={!comment.trim()}
          >
            {selectedAction === 'APPROVED' ? 'Approve' : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default ViewLeaveRequestPage;