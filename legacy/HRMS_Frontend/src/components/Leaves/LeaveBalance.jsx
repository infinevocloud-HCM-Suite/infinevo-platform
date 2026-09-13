import React, { useState, useEffect } from "react";
import {
  Container,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  Chip,
} from "@mui/material";
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import axios from "axios";
import { differenceInDays, parseISO, format } from "date-fns";
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

const LeaveBalance = () => {
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [today, setToday] = useState(new Date());
  const [expandedLeaveTypes, setExpandedLeaveTypes] = useState({});

  useEffect(() => {
    fetchLeaveTypes();

    const updateDate = () => {
      const now = new Date();
      const millisecondsUntilMidnight =
        24 * 60 * 60 * 1000 -
        (now.getHours() * 60 * 60 * 1000 +
          now.getMinutes() * 60 * 1000 +
          now.getSeconds() * 1000);

      setTimeout(() => {
        setToday(new Date());
        const intervalId = setInterval(() => {
          setToday(new Date());
        }, 24 * 60 * 60 * 1000);

        return () => clearInterval(intervalId);
      }, millisecondsUntilMidnight);
    };

    updateDate();
  }, []);

  const fetchLeaveTypes = async () => {
    setLoading(true);
    try {
      const response = await api.get("/my-leave-types");
      setLeaveTypes(response.data);
    } catch (err) {
      console.error("Error fetching leave types:", err);
      setError("Failed to load leave types");
    } finally {
      setLoading(false);
    }
  };

  const handleCloseSnackbar = () => {
    setError(null);
    setSuccess(null);
  };

  const getDaysRemaining = (expirationDate) => {
    if (!expirationDate) return null;
    const expDate = parseISO(expirationDate);
    return differenceInDays(expDate, today);
  };

  const isLeaveExpired = (expirationDate) => {
    const daysRemaining = getDaysRemaining(expirationDate);
    return daysRemaining !== null && daysRemaining < 0;
  };

  const formatExpiration = (expirationDate) => {
    const daysRemaining = getDaysRemaining(expirationDate);

    if (daysRemaining === null) {
      return <Chip label="No expiration" color="default" size="small" />;
    }

    if (daysRemaining < 0) {
      return <Chip label="Expired" color="error" size="small" />;
    } else if (daysRemaining === 0) {
      return <Chip label="Expires today" color="warning" size="small" />;
    } else {
      return (
        <Chip
          label={`${daysRemaining} day${daysRemaining !== 1 ? "s" : ""} remaining`}
          color={daysRemaining <= 3 ? "warning" : "success"}
          size="small"
        />
      );
    }
  };

  const formatCarryForward = (carryForward) => {
    return carryForward ? (
      <Chip label="Yes" color="success" size="small" />
    ) : (
      <Chip label="No" color="default" size="small" />
    );
  };

  const groupLeaveTypes = () => {
    const grouped = {};
    leaveTypes.forEach(type => {
      // Simplify Comp Off names
      const displayName = type.name.includes("Comp Off") ? "Comp Off" : type.name;
      
      if (!grouped[displayName]) {
        grouped[displayName] = [];
      }
      grouped[displayName].push(type);
    });
    return grouped;
  };

  const calculateGroupTotals = (types) => {
    return {
      allocation: types.reduce((sum, type) => sum + (type.defaultDays || 0), 0),
      remaining: types.reduce((sum, type) => 
        isLeaveExpired(type.endDate) ? sum : sum + (type.remainingDays || 0), 0),
      expired: types.filter(type => isLeaveExpired(type.endDate))
    };
  };

  const totalAllocation = leaveTypes.reduce(
    (sum, type) => sum + (type.defaultDays || 0),
    0
  );

  const totalRemaining = leaveTypes.reduce((sum, type) => {
    return isLeaveExpired(type.endDate) ? sum : sum + (type.remainingDays || 0);
  }, 0);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box mb={4}>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>
          My Leave Balance
        </Typography>
        <Typography variant="subtitle1" color="text.secondary">
          As of {format(today, "MMMM do, yyyy")}
        </Typography>
      </Box>

      <Paper elevation={3} sx={{ p: 2, mb: 4 }}>
        <TableContainer>
          <Table sx={{ minWidth: 800 }}>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', minWidth: 120 }}>Leave Type</TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', minWidth: 150 }}>Description</TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', minWidth: 120 }}>Created On</TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right', minWidth: 120 }}>
                  Annual Allocation
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'center', minWidth: 120 }}>
                  Carry Forward
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right', minWidth: 120 }}>
                  Remaining
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', minWidth: 120 }}>End Date</TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', minWidth: 150 }}>Expiration Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {Object.entries(groupLeaveTypes()).map(([name, types]) => {
                const totals = calculateGroupTotals(types);
                const isExpanded = expandedLeaveTypes[name];
                const hasExpired = types.some(type => isLeaveExpired(type.endDate));
                
                return (
                  <React.Fragment key={name}>
                    <TableRow hover>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {types.length > 1 ? (
                          <Typography 
                            component="span" 
                            sx={{ 
                              color: 'primary.main', 
                              textDecoration: 'underline', 
                              cursor: 'pointer',
                              fontWeight: isExpanded ? 600 : 'inherit'
                            }}
                            onClick={() => setExpandedLeaveTypes(prev => ({
                              ...prev,
                              [name]: !prev[name]
                            }))}
                          >
                            {name} ({types.length})
                          </Typography>
                        ) : (
                          name
                        )}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'normal', maxWidth: 200 }}>
                        {types[0].description || "-"}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {types[0].createdAt
                          ? format(parseISO(types[0].createdAt), "MMM dd, yyyy")
                          : "-"}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'right' }}>
                        {totals.allocation} days
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'center' }}>
                        {formatCarryForward(types[0].carryForward)}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap', textAlign: 'right' }}>
                        {hasExpired ? (
                          <>
                            <Box
                              component="span"
                              sx={{
                                textDecoration: "line-through",
                                opacity: 0.6,
                                color: "red",
                                fontWeight: 500,
                              }}
                            >
                              {types.find(type => isLeaveExpired(type.endDate))?.remainingDays} days
                            </Box>
                            {totals.remaining > 0 && (
                              <Box component="span" sx={{ ml: 1 }}>
                                + {totals.remaining} days
                              </Box>
                            )}
                          </>
                        ) : (
                          `${totals.remaining} days`
                        )}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {types[0].endDate
                          ? format(parseISO(types[0].endDate), "MMM dd, yyyy")
                          : "No end date"}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {formatExpiration(types[0].endDate)}
                      </TableCell>
                    </TableRow>
                    
                    {isExpanded && types.map((type, index) => (
                      <TableRow key={`${type.id}-${index}`} sx={{ backgroundColor: '#f9f9f9' }}>
                        <TableCell></TableCell>
                        <TableCell>{type.description || "-"}</TableCell>
                        <TableCell>
                          {type.createdAt
                            ? format(parseISO(type.createdAt), "MMM dd, yyyy")
                            : "-"}
                        </TableCell>
                        <TableCell sx={{ textAlign: 'right' }}>{type.defaultDays} days</TableCell>
                        <TableCell sx={{ textAlign: 'center' }}>
                          {formatCarryForward(type.carryForward)}
                        </TableCell>
                        <TableCell sx={{ textAlign: 'right' }}>
                          {isLeaveExpired(type.endDate) ? (
                            <Box
                              component="span"
                              sx={{
                                textDecoration: "line-through",
                                opacity: 0.6,
                                color: "red",
                                fontWeight: 500,
                              }}
                            >
                              {type.remainingDays} days (Expired)
                            </Box>
                          ) : (
                            `${type.remainingDays} days`
                          )}
                        </TableCell>
                        <TableCell>
                          {type.endDate
                            ? format(parseISO(type.endDate), "MMM dd, yyyy")
                            : "No end date"}
                        </TableCell>
                        <TableCell>{formatExpiration(type.endDate)}</TableCell>
                      </TableRow>
                    ))}
                  </React.Fragment>
                );
              })}
              <TableRow sx={{ backgroundColor: "#f5f5f5", '&:hover': { backgroundColor: "#f5f5f5" } }}>
                <TableCell colSpan={3} sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                  Total
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}>
                  {totalAllocation} days
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'center' }}>
                  -
                </TableCell>
                <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}>
                  {totalRemaining} days <Box component="small">(Active Only)</Box>
                </TableCell>
                <TableCell colSpan={2}></TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Box display="flex" gap={2} mb={4}>
        <Paper
          elevation={1}
          sx={{
            p: 3,
            flex: 1,
            minWidth: 0,
            borderRadius: 3,
            bgcolor: "background.paper",
            display: "flex",
            alignItems: "center",
            gap: 2,
          }}
        >
          <CalendarMonthIcon color="primary" fontSize="large" />
          <Box>
            <Typography variant="subtitle2" color="text.secondary">
              Total Leave Allocation
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {totalAllocation} days
            </Typography>
          </Box>
        </Paper>

        <Paper
          elevation={1}
          sx={{
            p: 3,
            flex: 1,
            minWidth: 0,
            borderRadius: 3,
            bgcolor: "background.paper",
            display: "flex",
            alignItems: "center",
            gap: 2,
          }}
        >
          <AccessTimeIcon color="success" fontSize="large" />
          <Box>
            <Typography variant="subtitle2" color="text.secondary">
              Total Remaining (Active)
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {totalRemaining} days
            </Typography>
          </Box>
        </Paper>
      </Box>

      <Snackbar 
        open={!!error} 
        autoHideDuration={6000} 
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={handleCloseSnackbar} sx={{ width: '100%' }}>
          {error}
        </Alert>
      </Snackbar>

      <Snackbar 
        open={!!success} 
        autoHideDuration={6000} 
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={handleCloseSnackbar} sx={{ width: '100%' }}>
          {success}
        </Alert>
      </Snackbar>
    </Container>
  );
};

export default LeaveBalance;