// UserAttendance.jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import moment from 'moment';
import {
  Box,
  Typography,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Alert,
  Chip,
  Collapse,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions
} from '@mui/material';
import {
  Login as ClockInIcon,
  Logout as ClockOutIcon,
  EventAvailable as EndDayIcon,
  Refresh,
  ExpandMore,
  ExpandLess,
  Info
} from '@mui/icons-material';
import API_BASE_URL from '../config/apiConfig';

const UserAttendance = () => {
  const [attendance, setAttendance] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [activeSession, setActiveSession] = useState(null);
  const [expandedSessions, setExpandedSessions] = useState({});
  const [confirmEndDay, setConfirmEndDay] = useState(false);
  const [timeRemaining, setTimeRemaining] = useState(null);

  const [sessionStartTime, setSessionStartTime] = useState(null);
  const [dayEnded, setDayEnded] = useState(false);

  // ---- helper functions (UTC from API -> convert local) ----
  const parseApiDate = (dateString) => {
    if (!dateString) return null;
    const m = moment(dateString);
    if (!m.isValid()) return null;
    return m.toDate();
  };

  const formatLocalDateOnly = (dateString) => {
    if (!dateString) return "";
    return moment(dateString).format("DD-MMM-YYYY");
  };

  const formatLocalTimeOnly = (dateString) => {
    if (!dateString) return "";
    return moment(dateString).format("HH:mm");
  };

  const formatLocalDateTime = (dateString) => {
    if (!dateString) return "N/A";
    return moment(dateString).format("DD-MMM-YYYY HH:mm");
  };

  const formatDateTime = (dateTime) => formatLocalDateTime(dateTime);

  // Restore session start from localStorage on load
  useEffect(() => {
    const saved = localStorage.getItem('sessionStartTime');
    if (saved) {
      const parsedTime = parseApiDate(saved) || new Date(saved);
      setSessionStartTime(parsedTime);

      const sessionEnd = moment(parsedTime).add(9, 'hours');
      const remainingMinutes = Math.max(0, Math.floor(sessionEnd.diff(moment()) / (1000 * 60)));
      setTimeRemaining(remainingMinutes);
    }
  }, []);

  useEffect(() => {
    if (sessionStartTime) {
      localStorage.setItem('sessionStartTime', sessionStartTime.toISOString());
    }
  }, [sessionStartTime]);

  const api = axios.create({
    baseURL: API_BASE_URL,
    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
  });

  const fetchMyAttendance = async () => {
    setLoading(true);
    try {
      const response = await api.get('/attendance/my-attendance');
      const attendanceData = response.data;
      setAttendance(attendanceData);
      updateActiveSession(attendanceData);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to fetch attendance');
    } finally {
      setLoading(false);
    }
  };

  const updateActiveSession = (attendanceData) => {
    const active = attendanceData.find(session =>
      !session.outTime ||
      (session.clockSessions && session.clockSessions.some(cs => !cs.outTime))
    );

    setActiveSession(active || null);

    if (!active?.manuallyEnded) {
      setDayEnded(false);
    }

    if (active?.clockSessions?.length > 0) {
      const allClockIns = active.clockSessions
        .filter(cs => cs.inTime)
        .map(cs => parseApiDate(cs.inTime))
        .filter(Boolean);

      if (allClockIns.length > 0) {
        const earliestInTime = new Date(Math.min(...allClockIns.map(d => d.getTime())));
        setSessionStartTime(earliestInTime);

        const sessionEnd = moment(earliestInTime).add(9, 'hours');
        const remainingMinutes = Math.max(0, Math.floor(sessionEnd.diff(moment()) / (1000 * 60)));
        setTimeRemaining(remainingMinutes);
        return;
      }
    }

    setSessionStartTime(null);
    setTimeRemaining(null);
  };

  // const handleClockIn = async () => {
  //   try {
  //     await api.post('/attendance/clock-in');
  //     await fetchMyAttendance();
  //     setDayEnded(false);
  //   } catch (err) {
  //     setError(err.response?.data?.message || 'Failed to clock in');
  //   }
  // };

  const handleClockIn = async () => {
  try {
    await api.post('/attendance/clock-in', {
      date: moment().format("YYYY-MM-DD"),   // e.g. 2025-08-20
      time: moment().format("HH:mm:ss")      // e.g. 17:42:15
    });
    await fetchMyAttendance();
    setDayEnded(false);
  } catch (err) {
    setError(err.response?.data?.message || 'Failed to clock in');
  }
};



  // const handleClockOut = async () => {
  //   try {
  //     const activeClockSession = activeSession?.clockSessions?.find(cs => !cs.outTime);
  //     if (!activeClockSession) {
  //       throw new Error('No active clock session found');
  //     }

  //     await api.put(`/attendance/${activeSession.id}/clock-out`);
  //     await fetchMyAttendance();
  //   } catch (err) {
  //     setError(err.response?.data?.message || 'Failed to clock out');
  //   }
  // };


  const handleClockOut = async () => {
  try {
    const activeClockSession = activeSession?.clockSessions?.find(cs => !cs.outTime);
    if (!activeClockSession) {
      throw new Error('No active clock session found');
    }

    await api.put(`/attendance/${activeSession.id}/clock-out`, {
      date: moment().format("YYYY-MM-DD"),
      time: moment().format("HH:mm:ss")
    });
    await fetchMyAttendance();
  } catch (err) {
    setError(err.response?.data?.message || 'Failed to clock out');
  }
};


  // const handleEndDay = async () => {
  //   try {
  //     await api.post('/attendance/end-day');
  //     setDayEnded(true);
  //     localStorage.removeItem('sessionStartTime');
  //     setSessionStartTime(null);
  //     setConfirmEndDay(false);
  //     await fetchMyAttendance();
  //   } catch (err) {
  //     setError(err.response?.data?.message || 'Failed to end day');
  //   }
  // };


  const handleEndDay = async () => {
  try {
    await api.post('/attendance/end-day', {
      date: moment().format("YYYY-MM-DD"),
      time: moment().format("HH:mm:ss")
    });
    setDayEnded(true);
    localStorage.removeItem('sessionStartTime');
    setSessionStartTime(null);
    setConfirmEndDay(false);
    await fetchMyAttendance();
  } catch (err) {
    setError(err.response?.data?.message || 'Failed to end day');
  }
};


  const toggleSessionDetails = (sessionId) => {
    setExpandedSessions(prev => ({
      ...prev,
      [sessionId]: !prev[sessionId]
    }));
  };

  const calculateDuration = (inTime, outTime) => {
    if (!inTime) return 'N/A';
    const start = moment(inTime);
    const end = outTime ? moment(outTime) : moment();
    const totalMinutes = Math.max(0, Math.floor(end.diff(start) / (1000 * 60)));
    const hours = Math.floor(totalMinutes / 60);
    const mins = totalMinutes % 60;
    return `${hours}h ${mins}m`;
  };

  const calculateTotalDuration = (session) => {
    if (!session || !session.clockSessions) return 'N/A';
    let totalMinutes = 0;
    for (const cs of session.clockSessions) {
      if (!cs.inTime) continue;
      const start = moment(cs.inTime);
      const end = cs.outTime ? moment(cs.outTime) : moment();
      totalMinutes += Math.max(0, Math.floor(end.diff(start) / (1000 * 60)));
    }
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours}h ${minutes}m`;
  };

  const formatTimeRemaining = (minutes) => {
    if (minutes === null) return '';
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours}h ${mins}m remaining in session`;
  };

  const getStatus = (session) => {
    if (!session.outTime) return 'In Progress';

    let totalMinutes = 0;
    if (session.clockSessions?.length > 0) {
      for (const cs of session.clockSessions) {
        if (!cs.inTime) continue;
        const start = moment(cs.inTime);
        const end = cs.outTime ? moment(cs.outTime) : moment();
        totalMinutes += Math.max(0, Math.floor(end.diff(start) / (1000 * 60)));
      }
    }

    const totalHours = totalMinutes / 60;
    if (totalHours >= 6) return 'PRESENT';
    if (totalHours > 4) return 'HALF_DAY';
    return 'ABSENT';
  };

  useEffect(() => {
    fetchMyAttendance();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let interval;
    if (timeRemaining !== null) {
      interval = setInterval(() => {
        setTimeRemaining(prev => {
          if (prev <= 1) {
            clearInterval(interval);
            fetchMyAttendance();
            return 0;
          }
          return prev - 1;
        });
      }, 60000);
    }

    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeRemaining]);

  const hasActiveClockSession = activeSession?.clockSessions?.some(cs => !cs.outTime);
  const hasActiveDaySession = activeSession && !activeSession.outTime;

  return (
    <Box sx={{ p: 4 }}>
      <Typography variant="h4" gutterBottom>My Attendance</Typography>

      <Box sx={{ display: 'flex', gap: 2, mb: 4, alignItems: 'center' }}>
        <Button
          variant="contained"
          color="success"
          startIcon={<ClockInIcon />}
          onClick={handleClockIn}
          disabled={loading || hasActiveClockSession}
        >
          Clock In
        </Button>
        <Button
          variant="contained"
          color="error"
          startIcon={<ClockOutIcon />}
          onClick={handleClockOut}
          disabled={loading || !hasActiveClockSession}
        >
          Clock Out
        </Button>

        <Button
          variant="contained"
          color="secondary"
          startIcon={<EndDayIcon />}
          onClick={() => setConfirmEndDay(true)}
          disabled={loading || dayEnded}
        >
          End Day
        </Button>

        <Button
          variant="outlined"
          startIcon={<Refresh />}
          onClick={fetchMyAttendance}
          disabled={loading}
        >
          Refresh
        </Button>

        {timeRemaining !== null && (
          <Chip
            label={formatTimeRemaining(timeRemaining)}
            color={timeRemaining < 60 ? 'warning' : 'primary'}
            icon={<Info />}
          />
        )}
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Dialog open={confirmEndDay} onClose={() => setConfirmEndDay(false)}>
        <DialogTitle>Confirm End Day</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to end this work day?</Typography>
          <Typography variant="body2" color="text.secondary" mt={2}>
            This will finalize your attendance record for today and cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmEndDay(false)}>Cancel</Button>
          <Button onClick={handleEndDay} color="secondary" variant="contained">
            Confirm End Day
          </Button>
        </DialogActions>
      </Dialog>

      {loading ? (
        <CircularProgress />
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead sx={{ backgroundColor: '#1976d2' }}>
              <TableRow>
                <TableCell sx={{ color: 'white' }}>Date</TableCell>
                <TableCell sx={{ color: 'white' }}>Start Time</TableCell>
                <TableCell sx={{ color: 'white' }}>End Time</TableCell>
                <TableCell sx={{ color: 'white' }}>Total Duration</TableCell>
                <TableCell sx={{ color: 'white' }}>Status</TableCell>
                <TableCell sx={{ color: 'white' }}>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {attendance.length > 0 ? (
                attendance.map((session) => (
                  <React.Fragment key={session.id}>
                    <TableRow>
                      <TableCell>{formatLocalDateOnly(session.inTime)}</TableCell>
                      <TableCell>{formatLocalTimeOnly(session.inTime)}</TableCell>
                      <TableCell>{session.outTime ? formatDateTime(session.outTime) : 'Active'}</TableCell>
                      <TableCell>{calculateTotalDuration(session)}</TableCell>
                      <TableCell>
                        <Chip
                          label={getStatus(session)}
                          color={
                            getStatus(session) === 'PRESENT' ? 'success' :
                            getStatus(session) === 'HALF_DAY' ? 'warning' :
                            getStatus(session) === 'In Progress' ? 'info' :
                            'error'
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <IconButton onClick={() => toggleSessionDetails(session.id)}>
                          {expandedSessions[session.id] ? <ExpandLess /> : <ExpandMore />}
                        </IconButton>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={6} sx={{ py: 0 }}>
                        <Collapse in={expandedSessions[session.id]} unmountOnExit>
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
                                {session.clockSessions?.length > 0 ? (
                                  session.clockSessions.map((cs, index) => (
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
                  <TableCell colSpan={6} align="center">
                    No attendance records found
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
};

export default UserAttendance;
