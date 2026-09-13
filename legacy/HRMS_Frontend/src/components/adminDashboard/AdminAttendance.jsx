import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Container,
  Typography,
  TextField,
  Button,
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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Pagination,
  Collapse,
  IconButton
} from '@mui/material';
import { Search, Refresh, ExpandMore, ExpandLess } from '@mui/icons-material';
import API_BASE_URL from '../config/apiConfig';
import { useContext } from "react";
import { userContext } from "../context/ContextProvider";
import { useLocation } from 'react-router-dom';

const AdminAttendance = () => {
  const location = useLocation();
  const [attendance, setAttendance] = useState([]);
  const [searchId, setSearchId] = useState('');
  const [filterDate, setFilterDate] = useState(location.state?.dateFilter || '');
  const [filterStatus, setFilterStatus] = useState(location.state?.statusFilter || 'all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [expandedSessions, setExpandedSessions] = useState({});
  const recordsPerPage = 10;
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();

  const statusOptions = [
    { value: 'all', label: 'All Statuses' },
    { value: 'Present', label: 'Present' },
    { value: 'Absent', label: 'Absent' },
    { value: 'Half-day', label: 'Half-day' }
  ];

  const fetchAttendance = async () => {
    setLoading(true);
    try {
      let response;

      if (role === 'reporting manager') {
        response = await axios.get(`${API_BASE_URL}/attendance/my-reporting-team`);
      } else {
        let url = `${API_BASE_URL}/attendance/all`;
const params = new URLSearchParams();

if (searchId) params.append('employeeId', searchId);
if (filterDate) params.append('date', filterDate);
if (filterStatus !== 'all') params.append('status', filterStatus); // ✅ Add this line


        if (params.toString()) url += `?${params.toString()}`;

        response = await axios.get(url);
      }

      setAttendance(response.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to fetch attendance');
    } finally {
      setLoading(false);
    }
  };
useEffect(() => {
  fetchAttendance();
}, []);


  const toggleSessionDetails = (sessionId) => {
    setExpandedSessions(prev => ({
      ...prev,
      [sessionId]: !prev[sessionId]
    }));
  };

const filteredAttendance = attendance.filter(record => {
  const matchesId = record.employeeId?.toString().toLowerCase().includes(searchId.toLowerCase());

  const matchesDate = filterDate
    ? new Date(record.inTime).toISOString().split('T')[0] === filterDate
    : true;

  const recordStatus = record.status?.toLowerCase() || 'present'; // default fallback
  const selectedStatus = filterStatus.toLowerCase();
  const matchesStatus =
    filterStatus === 'all' ? true : recordStatus === selectedStatus;

  return matchesId && matchesDate && matchesStatus;
});


  const totalPages = Math.ceil(filteredAttendance.length / recordsPerPage);
  const paginatedAttendance = filteredAttendance.slice(
    (currentPage - 1) * recordsPerPage,
    currentPage * recordsPerPage
  );

  const formatDateTime = (dateTime) => {
    if (!dateTime) return 'N/A';
    const date = new Date(dateTime);
    return date.toLocaleString();
  };

  const calculateDuration = (inTime, outTime) => {
    if (!inTime) return 'N/A';
    const endTime = outTime || new Date();
    const diffMs = new Date(endTime) - new Date(inTime);
    const hours = Math.floor(diffMs / (1000 * 60 * 60));
    const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
    return `${hours}h ${minutes}m`;
  };

  const calculateTotalDuration = (session) => {
    if (!session || !session.clockSessions) return 'N/A';
    
    let totalMs = 0;
    
    for (const cs of session.clockSessions) {
      if (cs.inTime) {
        totalMs += new Date(cs.outTime || new Date()) - new Date(cs.inTime);
      }
    }
    
    const hours = Math.floor(totalMs / (1000 * 60 * 60));
    const minutes = Math.floor((totalMs % (1000 * 60 * 60)) / (1000 * 60));
    return `${hours}h ${minutes}m`;
  };

  const handleDateChange = (e) => {
    setFilterDate(e.target.value);
    setCurrentPage(1);
  };

  const handleStatusChange = (e) => {
    setFilterStatus(e.target.value);
    setCurrentPage(1);
  };

  const handlePageChange = (event, value) => {
    setCurrentPage(value);
  };

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h4" sx={{ mb: 4, fontWeight: 'bold', color: 'primary.main' }}>
        {role === 'reporting manager' ? 'Team Attendance' : 'Attendance Management'}
      </Typography>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} md={3}>
          <TextField
            fullWidth
            label="Search by Employee ID"
            value={searchId}
            onChange={(e) => {
              setSearchId(e.target.value);
              setCurrentPage(1);
            }}
            InputProps={{
              startAdornment: <Search sx={{ mr: 1 }} />
            }}
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <TextField
            fullWidth
            type="date"
            label="Filter by Date"
            InputLabelProps={{ shrink: true }}
            value={filterDate}
            onChange={handleDateChange}
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <FormControl fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
              value={filterStatus}
              label="Status"
              onChange={handleStatusChange}
            >
              {statusOptions.map(option => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={12} md={2}>
          <Button
            fullWidth
            variant="contained"
            startIcon={<Refresh />}
            onClick={fetchAttendance}
            disabled={loading}
            sx={{ height: '56px' }}
          >
            Refresh
          </Button>
        </Grid>
      </Grid>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TableContainer component={Paper} elevation={3} sx={{ borderRadius: 2 }}>
            <Table>
              <TableHead sx={{ backgroundColor: 'primary.main' }}>
                <TableRow>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Employee</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Date</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>In Time</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Out Time</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Total Duration</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Status</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 'bold' }}>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {paginatedAttendance.length > 0 ? (
                  paginatedAttendance.map((record) => (
                    <React.Fragment key={record.id}>
                      <TableRow hover>
                        <TableCell>
                          <Box>
                            <Typography fontWeight="bold">{record.employeeId}</Typography>
                            <Typography variant="body2">{record.employeeName}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell>{new Date(record.inTime).toLocaleDateString()}</TableCell>
                        <TableCell>{formatDateTime(record.inTime)}</TableCell>
                        <TableCell>{record.outTime ? formatDateTime(record.outTime) : 'Active'}</TableCell>
                        <TableCell>{calculateTotalDuration(record)}</TableCell>
                        <TableCell>
                          <Chip
                            label={record.status || 'Present'}
                            color={
                              record.status === 'Present' ? 'success' :
                              record.status === 'Half-day' ? 'warning' : 'error'
                            }
                            variant="outlined"
                            sx={{ fontWeight: 'bold' }}
                          />
                        </TableCell>
                        <TableCell>
                          <IconButton onClick={() => toggleSessionDetails(record.id)}>
                            {expandedSessions[record.id] ? <ExpandLess /> : <ExpandMore />}
                          </IconButton>
                        </TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell colSpan={7} sx={{ py: 0 }}>
                          <Collapse in={expandedSessions[record.id]} unmountOnExit>
                            <Box sx={{ p: 3 }}>
                              <Typography variant="h6" gutterBottom>
                                Clock Sessions
                              </Typography>
                              <Table size="small">
                                <TableHead>
                                  <TableRow>
                                    <TableCell>Type</TableCell>
                                    <TableCell>In Time</TableCell>
                                    <TableCell>Out Time</TableCell>
                                    <TableCell>Duration</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {record.clockSessions?.length > 0 ? (
                                    record.clockSessions.map((cs, index) => (
                                      <TableRow key={index}>
                                        <TableCell>
                                          {cs.outTime ? 'Completed Session' : 'Active Session'}
                                        </TableCell>
                                        <TableCell>{formatDateTime(cs.inTime)}</TableCell>
                                        <TableCell>{cs.outTime ? formatDateTime(cs.outTime) : 'Still clocked in'}</TableCell>
                                        <TableCell>
                                          {calculateDuration(cs.inTime, cs.outTime)}
                                        </TableCell>
                                      </TableRow>
                                    ))
                                  ) : (
                                    <TableRow>
                                      <TableCell colSpan={4} align="center">
                                        No clock sessions recorded
                                      </TableCell>
                                    </TableRow>
                                  )}
                                </TableBody>
                              </Table>
                            </Box>
                          </Collapse>
                        </TableCell>
                      </TableRow>
                    </React.Fragment>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                      <Typography variant="body1" color="textSecondary">
                        No attendance records found
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {filteredAttendance.length > recordsPerPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={totalPages}
                page={currentPage}
                onChange={handlePageChange}
                color="primary"
                shape="rounded"
                sx={{
                  '& .MuiPaginationItem-root': {
                    fontSize: '1rem',
                    minWidth: '32px',
                    height: '32px'
                  }
                }}
              />
            </Box>
          )}
        </>
      )}
    </Container>
  );
};

export default AdminAttendance;