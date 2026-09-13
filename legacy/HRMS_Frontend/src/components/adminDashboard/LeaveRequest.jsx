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
  Snackbar,
  List,
  ListItem,
  ListItemText,
  Avatar,
  TextareaAutosize,
  DialogContentText
} from '@mui/material';
import {
  Search,
  Check,
  Close,
  Refresh,
  ChevronLeft,
  ChevronRight,
  Visibility,
  Comment as CommentIcon,
  Person,
  PictureAsPdf,
  Image,
  InsertDriveFile,
  Download
} from '@mui/icons-material';
import { format, parseISO } from 'date-fns';
import API_BASE_URL from '../config/apiConfig';
import { userContext } from '../context/ContextProvider';
import { useLocation, useParams } from 'react-router-dom';

const LeaveRequest = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const [leaveRequests, setLeaveRequests] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [viewDialogOpen, setViewDialogOpen] = useState(false);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [selectedAction, setSelectedAction] = useState('');
  const [comment, setComment] = useState('');
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [loadingFile, setLoadingFile] = useState(null);

  const location = useLocation();
  const { id: leaveIdFromUrl } = useParams();
  
  // Initialize filter status based on navigation source
  const initialFilter = () => {
    if (location.state?.statusFilter === 'PENDING_HR_APPROVAL') {
      return 'Pending by HR';
    } else if (location.state?.statusFilter === 'PENDING_MANAGER_APPROVAL') {
      return 'Pending by Reporting Manager';
    }
    return 'All';
  };

  const [filterStatus, setFilterStatus] = useState(initialFilter());

  const [filePreview, setFilePreview] = useState({
    open: false,
    url: null,
    type: null,
    name: null
  });
  const recordsPerPage = 5;

  useEffect(() => {
    fetchLeaveRequests();
    fetchLeaveTypes();
  }, []);

  useEffect(() => {
    if (leaveIdFromUrl && !viewDialogOpen) {
      handleViewRequest(leaveIdFromUrl);
    }
  }, [leaveIdFromUrl]);

  const fetchLeaveRequests = async () => {
    setLoading(true);
    setError(null);
    try {
      let endpoint;
      if (role === 'employee') {
        endpoint = '/leaves/my-leaves';
      } else if (role === 'reporting manager') {
        endpoint = '/leaves/reporting-manager';
      } else if (role === 'hr') {
        endpoint = '/leaves/all';
      } else {
        throw new Error('Invalid user role');
      }

      const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });

      // const processedData = response.data.map(request => ({
      //   ...request,
      //   createdAt: request.createdDate,
      //   employeeName: request.employeeName || 'N/A',
      //   employeeId: request.employeeId || 'N/A',
      //   reportingManagerStatus: request.reportingManagerStatus || 'PENDING',
      //   hrStatus: request.hrStatus || 'PENDING',
      //   reportingManagerComment: request.reportingManagerComment || '',
      //   hrComment: request.hrComment || '',
      //   hasMedicalCertificate: request.hasMedicalCertificate === true,
      //   fileUrl: request.fileUrl || null,

      //   medicalCertificateName: request.medicalCertificateName || 'medical_certificate.pdf'
        
      // })).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));


      const processedData = response.data.map(request => ({
        ...request,
        createdAt: request.createdDate,
        employeeName: request.employeeName || 'N/A',
        employeeId: request.employeeId || 'N/A',
        reportingManagerStatus: request.reportingManagerStatus || 'PENDING',
        hrStatus: request.hrStatus || 'PENDING',
        reportingManagerComment: request.reportingManagerComment || '',
        hrComment: request.hrComment || '',
        hasMedicalCertificate: request.hasMedicalCertificate === true,
        fileUrl: request.fileUrl || null,
        medicalCertificateName: request.medicalCertificateName || 'medical_certificate.pdf',
        // Prefer server-provided distribution; if missing, build a tiny distribution from manualDaysAllocationByName or manualDaysAllocation
        leaveDistribution: request.leaveDistribution || (
          request.manualDaysAllocationByName
            ? Object.entries(request.manualDaysAllocationByName).map(([name, days]) => ({ leaveTypeId: name, leaveTypeName: name, allocatedDays: days }))
            : (request.manualDaysAllocation
                ? Object.entries(request.manualDaysAllocation).map(([id, days]) => ({ leaveTypeId: id, leaveTypeName: null, allocatedDays: days }))
                : [])
        )
      })).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));





      setLeaveRequests(processedData);
    } catch (error) {
      console.error('Error fetching leave requests:', error);
      setError(error.response?.data?.message || 'Failed to load leave requests. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const fetchLeaveTypes = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/my-leave-types`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });
      setLeaveTypes(response.data || []);
    } catch (error) {
      console.error('Error fetching leave types:', error);
    }
  };

  const getCombinedStatus = (request) => {
    if (request.reportingManagerStatus === 'REJECTED' && request.hrStatus === 'PENDING') {
      return 'PENDING_HR_REVIEW';
    }
    if (request.reportingManagerStatus === 'REJECTED' && request.hrStatus === 'REJECTED') {
      return 'REJECTED_BY_HR';
    }
    if (request.reportingManagerStatus === 'REJECTED' && request.hrStatus === 'APPROVED') {
      return 'APPROVED_BY_HR';
    }
    if (request.reportingManagerStatus === 'PENDING') {
      return 'PENDING_MANAGER_APPROVAL';
    }
    if (request.reportingManagerStatus === 'APPROVED' && request.hrStatus === 'PENDING') {
      return 'PENDING_HR_APPROVAL';
    }
    if (request.reportingManagerStatus === 'APPROVED' && request.hrStatus === 'APPROVED') {
      return 'APPROVED';
    }
    return 'UNKNOWN_STATUS';
  };

  const canTakeAction = (request) => {
    if (role === 'reporting manager') {
      return request.reportingManagerStatus === 'PENDING';
    }
    if (role === 'hr') {
      return (request.hrStatus === 'PENDING') && 
             (request.reportingManagerStatus === 'APPROVED' || 
              request.reportingManagerStatus === 'REJECTED');
    }
    return false;
  };

  const handleViewRequest = async (id) => {
    try {
      setLoading(true);
      const response = await axios.get(`${API_BASE_URL}/leaves/${id}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`
        }
      });
      setSelectedRequest(response.data);
      setViewDialogOpen(true);
    } catch (error) {
      console.error('Error fetching leave request:', error);
      setError(error.response?.data?.message || 'Failed to load request details. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenActionDialog = (request, action) => {
    setSelectedRequest(request);
    setSelectedAction(action);
    setComment('');
    setActionDialogOpen(true);
  };

  const handleStatusUpdate = async () => {
    try {
      let endpoint;
      if (role === 'hr') {
        endpoint = 'hr';
      } else if (role === 'reporting manager') {
        endpoint = 'reporting-manager';
      } else {
        throw new Error('Invalid role for this action');
      }

      await axios.put(
        `${API_BASE_URL}/leaves/${selectedRequest.id}/status`,
        null,
        {
          params: { 
            status: selectedAction.toUpperCase(),
            comment: comment 
          },
          headers: {
            Authorization: `Bearer ${localStorage.getItem('token')}`
          }
        }
      );
      
      setSuccess(`Request ${selectedAction.toLowerCase()} successfully!`);
      setActionDialogOpen(false);
      setComment('');
      fetchLeaveRequests();
    } catch (error) {
      console.error('Error updating status:', error);
      setError(error.response?.data?.message || 'Failed to update status. Please try again.');
    }
  };

  const getFileIcon = (fileName) => {
    if (!fileName) return <InsertDriveFile />;
    const ext = fileName.split('.').pop().toLowerCase();
    switch(ext) {
      case 'pdf': return <PictureAsPdf color="error" />;
      case 'jpg':
      case 'jpeg':
      case 'png': return <Image color="primary" />;
      default: return <InsertDriveFile color="action" />;
    }
  };

  const handleDownloadFile = async (leaveId, fileName) => {
    setLoadingFile(leaveId);
    try {
      const response = await axios.get(`${API_BASE_URL}/leaves/${leaveId}/medical-certificate`, {
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.data) {
        throw new Error('No file data received');
      }

      const fileURL = window.URL.createObjectURL(new Blob([response.data]));
      let finalFileName = fileName;

      if (!finalFileName) {
        const contentDisposition = response.headers['content-disposition'];
        if (contentDisposition) {
          const filenameMatch = contentDisposition.match(/filename="?(.+)"?/);
          if (filenameMatch && filenameMatch.length === 2) {
            finalFileName = filenameMatch[1];
          }
        }
        finalFileName = finalFileName || `medical_certificate_${leaveId}`;
      }

      const link = document.createElement('a');
      link.href = fileURL;
      link.setAttribute('download', finalFileName);
      document.body.appendChild(link);
      link.click();

      // Cleanup
      link.parentNode.removeChild(link);
      setTimeout(() => {
        window.URL.revokeObjectURL(fileURL);
      }, 100);

    } catch (error) {
      console.error("Error downloading file:", error);
      setError(error.response?.data?.message || 
        error.message || 
        "Failed to download document. Please try again or contact support.");
    } finally {
      setLoadingFile(null);
    }
  };

  const handleViewFile = async (leaveId, fileName) => {
    setLoadingFile(leaveId);
    try {
      const response = await axios.get(`${API_BASE_URL}/leaves/${leaveId}/medical-certificate`, {
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.data) {
        throw new Error('No file data received');
      }

      const fileURL = URL.createObjectURL(new Blob([response.data]));
      const fileType = response.headers['content-type'];
      
      setFilePreview({
        open: true,
        url: fileURL,
        type: fileType,
        name: fileName || `medical_certificate_${leaveId}`
      });
    } catch (error) {
      console.error("Error viewing file:", error);
      setError(error.response?.data?.message || 
        error.message || 
        "Failed to load document. Please try again or contact support.");
    } finally {
      setLoadingFile(null);
    }
  };

  const handleClosePreview = () => {
    if (filePreview.url) {
      URL.revokeObjectURL(filePreview.url);
    }
    setFilePreview({
      open: false,
      url: null,
      type: null,
      name: null
    });
  };

  const renderMedicalCertificateCell = (request) => {
    if (!request.hasMedicalCertificate) {
      return <Typography variant="body2">N/A</Typography>;
    }

    return (
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <Tooltip title={request.medicalCertificateName || "Medical Certificate"}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            {getFileIcon(request.medicalCertificateName)}
          </Box>
        </Tooltip>
        <Tooltip title="View Certificate">
          <IconButton 
            onClick={() => {
  setFilePreview({
    open: true,
    url: request.fileUrl,
    type: 'image', // or determine by extension
    name: request.medicalCertificateName
  });
}}

            disabled={loadingFile === request.id}
            size="small"
          >
            {loadingFile === request.id ? <CircularProgress size={24} /> : <Visibility color="primary" />}
          </IconButton>
        </Tooltip>
        <Tooltip title="Download Certificate">
          <IconButton 
//             onClick={() => {
//   const link = document.createElement('a');
//   link.href = request.fileUrl;
//   link.setAttribute('download', request.medicalCertificateName || 'medical_certificate');
//   document.body.appendChild(link);
//   link.click();
//   document.body.removeChild(link);
// }}

onClick={() => {
  if (!request?.fileUrl) return;

  const downloadUrl = request.fileUrl.replace('/upload/', '/upload/fl_attachment/');
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.setAttribute('download', request.medicalCertificateName || 'medical_certificate');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}}

            disabled={loadingFile === request.id}
            size="small"
          >
            {loadingFile === request.id ? <CircularProgress size={24} /> : <Download color="secondary" />}
          </IconButton>
        </Tooltip>
      </Box>
    );
  };

  const formatDisplayDate = (dateString) => {
    if (!dateString) return 'N/A';
    return format(parseISO(dateString), 'MMM d, yyyy');
  };

  const formatLeaveDays = (request) => {
    if (request.isHalfDay) {
      return `${request.totalDays.toFixed(1)} days (${request.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'})`;
    }
    return `${request.totalDays} day(s)`;
  };

const formatLeaveTypesWithDays = (request) => {
  // 1) prefer structured distribution from backend
  if (request.leaveDistribution && request.leaveDistribution.length > 0) {
    return request.leaveDistribution
      .map(d => {
        const name = d.leaveTypeName || d.leaveTypeId || `Type ${d.leaveTypeId}`;
        const days = d.allocatedDays ?? d.allocatedDays === 0 ? d.allocatedDays : (request.manualDaysAllocation?.[d.leaveTypeId] ?? request.manualDaysAllocationByName?.[name]);
        return `${name} (${days} day${days !== 1 ? 's' : ''})`;
      })
      .join(' + ');
  }

  // 2) next, if server provided a name->days map, use it
  if (request.manualDaysAllocationByName && Object.keys(request.manualDaysAllocationByName).length > 0) {
    return Object.entries(request.manualDaysAllocationByName)
      .map(([name, days]) => `${name} (${days} day${days !== 1 ? 's' : ''})`)
      .join(' + ');
  }

  // 3) fallback to older behavior: try client-side lookup using leaveTypes array
  if (request.manualDaysAllocation && Object.keys(request.manualDaysAllocation).length > 0) {
    return Object.entries(request.manualDaysAllocation)
      .filter(([_, days]) => days > 0)
      .map(([typeId, days]) => {
        // try to resolve name from leaveTypes if available (backwards-compat)
        const found = leaveTypes.find(t => String(t.id) === String(typeId) || String(t.name) === String(typeId));
        const displayName = found ? (found.name || found.typeName) : `Type ${typeId}`;
        return `${displayName} (${days} day${days !== 1 ? 's' : ''})`;
      })
      .join(' + ');
  }

  // last fallback
  return request.leaveType || 'N/A';
};


  const statusColor = {
    Approved: "success",
    Rejected: "error",
    Pending: "warning",
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning",
    PENDING_HR_APPROVAL: "info",
    PENDING_MANAGER_APPROVAL: "warning"
  };

  const filteredRequests = leaveRequests.filter(request => {
    const matchesSearch =
      request.employeeId?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.employeeName?.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus = 
      filterStatus === 'All' ||
      (filterStatus === 'Pending' && 
        (request.reportingManagerStatus === 'PENDING' || request.hrStatus === 'PENDING')) ||
      (filterStatus === 'Approved' && 
        request.reportingManagerStatus === 'APPROVED' && request.hrStatus === 'APPROVED') ||
      (filterStatus === 'Rejected' && 
        (request.reportingManagerStatus === 'REJECTED' || request.hrStatus === 'REJECTED')) ||
      (filterStatus === 'Pending by HR' && 
        request.hrStatus === 'PENDING' && request.reportingManagerStatus === 'APPROVED') ||
      (filterStatus === 'Pending by Reporting Manager' && 
        request.reportingManagerStatus === 'PENDING' && request.hrStatus === 'PENDING');

    return matchesSearch && matchesStatus;
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

  const handleCloseSnackbar = () => {
    setError(null);
    setSuccess(null);
  };

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
        <Typography variant="h4" fontWeight={700} color="primary.main">
          Leave History
        </Typography>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <FormControl sx={{ minWidth: 250 }}>
            <InputLabel>Filter by Status</InputLabel>
            <Select
              value={filterStatus}
              onChange={(e) => {
                setFilterStatus(e.target.value);
                setCurrentPage(0);
              }}
              label="Filter by Status"
            >
              {["All", "Pending", "Approved", "Rejected", "Pending by HR", "Pending by Reporting Manager"].map((status) => (
                <MenuItem key={status} value={status}>
                  {status}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            onClick={fetchLeaveRequests}
            disabled={loading}
            startIcon={<Refresh />}
          >
            {loading ? "Refreshing..." : "Refresh"}
          </Button>
        </Box>
      </Box>

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
          <TableContainer component={Paper} elevation={3}>
            <Table>
              <TableHead sx={{ backgroundColor: '#1976d2' }}>
                <TableRow>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>ID</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Employee ID</TableCell>
<TableCell sx={{ color: 'white', fontWeight: 600 }}>Employee Name</TableCell>

                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Leave Type</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Period</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Days</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Selected Dates</TableCell>

                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Manager Status</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>HR Status</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Manager Comment</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>HR Comment</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Submitted On</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Medical Cert.</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 600 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {paginatedRequests.length > 0 ? (
                  paginatedRequests.map((request) => (
                    <TableRow key={request.id} hover>
                      <TableCell>#{request.id}</TableCell>
                      <TableCell>{request.employeeId || 'N/A'}</TableCell>
<TableCell>{request.employeeName || 'N/A'}</TableCell>

                      <TableCell>{formatLeaveTypesWithDays(request)}</TableCell>
                      <TableCell>
                        {formatDisplayDate(request.fromDate)} - {formatDisplayDate(request.toDate)}
                        {request.isHalfDay && (
                          <Typography variant="caption" display="block">
                            {request.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{formatLeaveDays(request)}</TableCell>



                      <TableCell>
  {request.selectedDates && request.selectedDates.length > 0 ? (
    <Tooltip
      title={request.selectedDates
        .map(d => format(parseISO(d), 'EEE, MMM d, yyyy'))
        .join('\n')}
    >
      <Typography variant="body2">
        {request.selectedDates
          .map(d => format(parseISO(d), 'dd MMM'))
          .join(', ')
        }
      </Typography>
    </Tooltip>
  ) : (
    <Typography variant="body2">N/A</Typography>
  )}
</TableCell>




                      <TableCell>
                        <Chip
                          label={request.reportingManagerStatus || 'N/A'}
                          color={statusColor[request.reportingManagerStatus] || 'default'}
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={request.hrStatus || 'N/A'}
                          color={statusColor[request.hrStatus] || 'default'}
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell>{request.reportingManagerComment || 'N/A'}</TableCell>
                      <TableCell>{request.hrComment || 'N/A'}</TableCell>
                      <TableCell>{formatDisplayDate(request.createdAt)}</TableCell>
                      <TableCell>
                        {renderMedicalCertificateCell(request)}
                      </TableCell>
                      <TableCell>
               <Button 
  size="small" 
  onClick={() => handleViewRequest(request.id)}
>
  Details
</Button>

                        {canTakeAction(request) && (
                          <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                            <Button
                              size="small"
                              color="success"
                              onClick={() => handleOpenActionDialog(request, 'APPROVED')}
                            >
                              Approve
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              onClick={() => handleOpenActionDialog(request, 'REJECTED')}
                            >
                              Reject
                            </Button>
                          </Box>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={11} align="center" sx={{ py: 4 }}>
                      <Typography variant="body1" color="textSecondary">
                        No leave requests found
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {filteredRequests.length > recordsPerPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 3 }}>
              <IconButton
                onClick={handlePrevPage}
                disabled={currentPage === 0}
              >
                <ChevronLeft />
              </IconButton>
              <Typography variant="body1">
                Page {currentPage + 1} of {totalPages}
              </Typography>
              <IconButton
                onClick={handleNextPage}
                disabled={currentPage === totalPages - 1}
              >
                <ChevronRight />
              </IconButton>
            </Box>
          )}
        </>
      )}

      {/* View Details Dialog */}
      <Dialog 
        open={viewDialogOpen} 
        onClose={() => setViewDialogOpen(false)} 
        maxWidth="md" 
        fullWidth
      >
        <DialogTitle>Leave Request Details</DialogTitle>
        <DialogContent dividers>
          {selectedRequest && (
            <List>
              <ListItem>
                <ListItemText primary="Request ID" secondary={`#${selectedRequest.id}`} />
              </ListItem>
   <ListItem>
  <ListItemText 
    primary="Employee" 
    secondary={`${selectedRequest.employeeName || 'N/A'} (${selectedRequest.employeeId || 'N/A'})`} 
  />
</ListItem>

              <ListItem>
            <ListItemText 
  primary="Leave Types" 
  secondary={
    selectedRequest.leaveDistribution && selectedRequest.leaveDistribution.length > 0 ? (
      selectedRequest.leaveDistribution.map((d, idx) => (
        <Typography key={`${d.leaveTypeId}-${idx}`} variant="body2">
          {d.leaveTypeName ? d.leaveTypeName : `Type ${d.leaveTypeId}`} ({d.allocatedDays} day{d.allocatedDays !== 1 ? 's' : ''})
        </Typography>
      ))
    ) : (
      // fallback to manualDaysAllocation rendering (keeps your previous behavior)
      selectedRequest.manualDaysAllocation && Object.entries(selectedRequest.manualDaysAllocation)
        .filter(([_, days]) => days > 0)
        .map(([typeId, days]) => {
          const type = leaveTypes.find(t => String(t.id) === String(typeId) || String(t.name) === String(typeId));
          return (
            <Typography key={typeId} variant="body2">
              {type ? type.name : `Type ${typeId}`} ({days} day{days !== 1 ? 's' : ''})
            </Typography>
          );
        }) || 'N/A'
    )
  }
/>

              </ListItem>
              <ListItem>
                <ListItemText 
                  primary="Period"
                  secondary={
                    <>
                      {formatDisplayDate(selectedRequest.fromDate)} to {formatDisplayDate(selectedRequest.toDate)}
                      {selectedRequest.isHalfDay && (
                        <Typography variant="caption" display="block">
                          {selectedRequest.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'}
                        </Typography>
                      )}
                    </>
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemText primary="Total Days" secondary={formatLeaveDays(selectedRequest)} />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="Manager Status"
                  secondary={
                    <Chip
                      label={selectedRequest.reportingManagerStatus || 'N/A'}
                      color={statusColor[selectedRequest.reportingManagerStatus] || 'default'}
                      sx={{ fontWeight: 600 }}
                    />
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="HR Status"
                  secondary={
                    <Chip
                      label={selectedRequest.hrStatus || 'N/A'}
                      color={statusColor[selectedRequest.hrStatus] || 'default'}
                      sx={{ fontWeight: 600 }}
                    />
                  }
                />
              </ListItem>
              {selectedRequest.reportingManagerComment && (
                <ListItem>
                  <ListItemText
                    primary="Manager's Comment"
                    secondary={selectedRequest.reportingManagerComment}
                  />
                </ListItem>
              )}
              {selectedRequest.hrComment && (
                <ListItem>
                  <ListItemText
                    primary="HR's Comment"
                    secondary={selectedRequest.hrComment}
                  />
                </ListItem>
              )}
              <ListItem>
                <ListItemText primary="Submitted On" secondary={formatDisplayDate(selectedRequest.createdAt)} />
              </ListItem>
              <ListItem>
                <ListItemText primary="Reason" secondary={selectedRequest.reason} />
              </ListItem>
              {selectedRequest.lateReason && (
                <ListItem>
                  <ListItemText
                    primary="Late Application Reason"
                    secondary={selectedRequest.lateReason}
                  />
                </ListItem>
              )}
              {selectedRequest.hasMedicalCertificate && (
                <ListItem>
                  <ListItemText
                    primary="Medical Certificate"
                    secondary={
                      <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                        <Button
  variant="outlined"
  onClick={() =>
    setFilePreview({
      open: true,
      url: selectedRequest.fileUrl,
      type: 'image',
      name: selectedRequest.medicalCertificateName
    })
  }

                          startIcon={<Visibility />}
                          disabled={loadingFile === selectedRequest.id}
                        >
                          {loadingFile === selectedRequest.id ? 'Loading...' : 'View'}
                        </Button>
                        {/* <Button
                          variant="outlined"
                          onClick={() => handleDownloadFile(selectedRequest.id, selectedRequest.medicalCertificateName)}
                          startIcon={<Download />}
                          disabled={loadingFile === selectedRequest.id}
                        >
                          {loadingFile === selectedRequest.id ? 'Downloading...' : 'Download'}
                        </Button> */}
                        {/* <Button
  variant="outlined"
  onClick={() => {
    const link = document.createElement('a');
    link.href = selectedRequest.fileUrl;
    link.setAttribute('download', selectedRequest.medicalCertificateName || 'medical_certificate');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }}
  startIcon={<Download />}
>
  Download
</Button> */}
<Button
  variant="outlined"
  onClick={() => {
    if (!selectedRequest?.fileUrl) return;

    const downloadUrl = selectedRequest.fileUrl.replace('/upload/', '/upload/fl_attachment/');
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', selectedRequest.medicalCertificateName || 'medical_certificate');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }}
  startIcon={<Download />}
>
  Download
</Button>


                      </Box>
                    }
                  />
                </ListItem>
              )}
            </List>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Approval/Rejection Dialog */}
      <Dialog
        open={actionDialogOpen}
        onClose={() => {
          setActionDialogOpen(false);
          setComment('');
        }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {selectedAction === 'APPROVED' ? 'Approve Leave Request' : 'Reject Leave Request'}
        </DialogTitle>
        <DialogContent dividers>
          <DialogContentText sx={{ mb: 2 }}>
            You are about to {selectedAction.toLowerCase()} this leave request from {selectedRequest?.employeeName}.
          </DialogContentText>
          <TextField
            fullWidth
            multiline
            rows={4}
            variant="outlined"
            label="Add a comment (required)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            sx={{ mt: 2 }}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button 
            onClick={() => {
              setActionDialogOpen(false);
              setComment('');
            }}
          >
            Cancel
          </Button>
          <Button
            onClick={handleStatusUpdate}
            color={selectedAction === 'APPROVED' ? 'success' : 'error'}
            variant="contained"
            disabled={!comment.trim()}
          >
            {selectedAction === 'APPROVED' ? 'Approve' : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* File Preview Dialog */}
      <Dialog
        open={filePreview.open}
        onClose={handleClosePreview}
        maxWidth="md"
        fullWidth
        PaperProps={{
          sx: {
            height: '80vh',
            display: 'flex',
            flexDirection: 'column'
          }
        }}
      >
        <DialogTitle>
          {filePreview.name || "Medical Certificate Preview"}
          <IconButton
            onClick={handleClosePreview}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers sx={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
          {filePreview.type?.startsWith('image/') ? (
            <img 
              src={filePreview.url} 
              alt="Medical Certificate" 
              style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
            />
          ) : (
            <iframe 
              src={filePreview.url} 
              title="Medical Certificate"
              style={{ width: '100%', height: '100%', border: 'none' }}
            />
          )}
        </DialogContent>
        <DialogActions>
          {/* <Button 
            onClick={() => handleDownloadFile(
              selectedRequest?.id || leaveRequests.find(l => l.medicalCertificateUrl)?.id,
              selectedRequest?.medicalCertificateName || leaveRequests.find(l => l.medicalCertificateUrl)?.medicalCertificateName
            )}
            startIcon={<Download />}
          >
            Download
          </Button> */}
          <Button 
  onClick={() => {
    if (!filePreview?.url) return;

    const downloadUrl = filePreview.url.replace('/upload/', '/upload/fl_attachment/');
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', filePreview.name || 'medical_certificate');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }}
  startIcon={<Download />}
>
  Download
</Button>

        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default LeaveRequest;