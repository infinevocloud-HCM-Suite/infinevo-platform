import React, { useEffect, useState, useContext } from "react";
import axios from "axios";
import UserService from "../service/UserService";
import {addDays, isEqual } from 'date-fns';

import {
  Box,
  Typography,
  TextField,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Button,
  Divider,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Paper,
  Grid,
  Alert,
  Snackbar,
  IconButton,
  Chip,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  Checkbox,
  FormControlLabel,
  RadioGroup,
  Radio,
  Tooltip
} from "@mui/material";
import {
  Add,
  Remove,
  ChevronLeft,
  ChevronRight,
  CloudUpload,
  Delete,
  Visibility,
  PictureAsPdf,
  Image,
  InsertDriveFile,
  Download,
  Close
} from "@mui/icons-material";
import { format, parseISO, differenceInDays } from 'date-fns';
import API_BASE_URL from "../config/apiConfig";
import { userContext } from '../context/ContextProvider';

const Leave = () => {
  const [leaveData, setLeaveData] = useState({
    employeeId: "",
    employeeName: "",
    fromDate: "",
    toDate: "",
    reason: "",
    isHalfDay: false,
    halfDayPeriod: "first",
    selectedLeaveTypes: [],
    leaveDistribution: [],
    manualDaysAllocation: {},
    lateApplicationReason: "",
    medicalCertificate: null,
    medicalCertificateName: ""
  });

  const [leaveHistory, setLeaveHistory] = useState([]);
  const [filterStatus, setFilterStatus] = useState("All");
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success",
  });
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [profileData, setProfileData] = useState(null);
  const recordsPerPage = 5;
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [loadingLeaveTypes, setLoadingLeaveTypes] = useState(false);
  const [selectedLeave, setSelectedLeave] = useState(null);
  const [openDialog, setOpenDialog] = useState(false);
  const [today] = useState(new Date());
  const [totalRequestedDays, setTotalRequestedDays] = useState(0);
  const [showMedicalCertificateMessage, setShowMedicalCertificateMessage] = useState(false);
  const [isLateSubmission, setIsLateSubmission] = useState(false);
  const [sickLeaveDays, setSickLeaveDays] = useState(0);
  const [loadingFile, setLoadingFile] = useState(null);
  const [filePreview, setFilePreview] = useState({
    open: false,
    url: null,
    type: null,
    name: null
  });

  const { actions } = useContext(userContext);
  const hasAction = (actionName) => actions.includes(actionName);

  // UI for selecting specific days in range
const [selectDaysDialogOpen, setSelectDaysDialogOpen] = useState(false);
// holds strings like "2025-09-15" for selected actual leave days inside range
const [selectedDatesInRange, setSelectedDatesInRange] = useState([]);

// optionally show a preview count
// (you already have totalRequestedDays; we'll update calculateRequestedDays to use selectedDatesInRange)

const openSelectDaysDialog = () => {
  const rangeDates = getDatesArrayBetween(leaveData.fromDate, leaveData.toDate);
  const prevDisplayed = selectedDatesInRange || [];
  // previous user-chosen are those not weekends
  const prevUser = prevDisplayed.filter(d => !isWeekend(d));

  // keep only user-chosen within current range
  const initialUser = prevUser.filter(d => rangeDates.includes(d));

  // compute auto weekends only if there are >= 2 user-chosen endpoints
  let initialAuto = [];
  if (initialUser.length >= 2) {
    const sortedUser = initialUser.slice().sort();
    const min = sortedUser[0];
    const max = sortedUser[sortedUser.length - 1];
    //initialAuto = getDatesArrayBetween(min, max).filter(isWeekend);


    const minDay = parseISO(min).getDay();
const maxDay = parseISO(max).getDay();
initialAuto =
  (minDay === 1 && maxDay === 5)
    ? getDatesArrayBetween(min, max).filter(isWeekend)
    : [];


  }

  // set explicit user-selected state and displayed set
  setUserSelectedDates(initialUser.slice().sort());
  setAutoAddedDates(initialAuto.slice().sort());
  setSelectedDatesInRange([...new Set([...initialUser, ...initialAuto])].sort());
  setSelectDaysDialogOpen(true);
};




const getRangeDays = () => {
  if (!leaveData.fromDate || !leaveData.toDate) return 0;
  const from = parseISO(leaveData.fromDate);
  const to = parseISO(leaveData.toDate);
  if (to < from) return 0;
  let days = differenceInDays(to, from) + 1;
  if (leaveData.isHalfDay) days -= 0.5;
  return days;
};


const closeSelectDaysDialog = () => {
  setSelectDaysDialogOpen(false);
};

const toggleDateInSelection = (dateStr) => {
  // update userSelectedDates first (user intent)
  setUserSelectedDates(prevUser => {
    const user = Array.isArray(prevUser) ? [...prevUser] : [];
    const exists = user.includes(dateStr);

    if (exists) {
      // user clicked to deselect a user-selected date
      const newUser = user.filter(d => d !== dateStr);

      // recompute weekends only if >=2 user-selected remain
      if (newUser.length >= 2) {
        const sortedUser = newUser.slice().sort();
        const min = sortedUser[0];
        const max = sortedUser[sortedUser.length - 1];
       // const weekends = getDatesArrayBetween(min, max).filter(isWeekend);


       // Only auto-add weekends if user selected Monday (min) AND Friday (max)
const minDay = parseISO(min).getDay(); // Monday = 1
const maxDay = parseISO(max).getDay(); // Friday = 5

const weekends =
  (minDay === 1 && maxDay === 5)
    ? getDatesArrayBetween(min, max).filter(isWeekend)
    : [];




        setAutoAddedDates(weekends);
        setSelectedDatesInRange(Array.from(new Set([...newUser, ...weekends])).sort());
      } else {
        // fewer than 2 endpoints -> no auto weekends
        setAutoAddedDates([]);
        setSelectedDatesInRange(newUser.slice().sort());
      }

      return newUser;
    } else {
      // user clicked to add date
      const newUser = [...user, dateStr];
      // if <2 user-selected, do not auto-add weekends
      if (newUser.length < 2) {
        setAutoAddedDates([]);
        setSelectedDatesInRange(newUser.slice().sort());
        return newUser;
      }
      // >=2 user-selected -> compute weekends between min & max
      const sortedUser = newUser.slice().sort();
      const min = sortedUser[0];
      const max = sortedUser[sortedUser.length - 1];
     // const weekends = getDatesArrayBetween(min, max).filter(isWeekend);


     const minDay = parseISO(min).getDay();
const maxDay = parseISO(max).getDay();
const weekends =
  (minDay === 1 && maxDay === 5)
    ? getDatesArrayBetween(min, max).filter(isWeekend)
    : [];




      setAutoAddedDates(weekends);
      setSelectedDatesInRange(Array.from(new Set([...newUser, ...weekends])).sort());
      return newUser;
    }
  });
};






// track which dates were auto-added (weekends) vs user-selected
const [autoAddedDates, setAutoAddedDates] = useState([]);
const [userSelectedDates, setUserSelectedDates] = useState([]);



const isWeekend = (dateStr) => {
  if (!dateStr) return false;
  const d = parseISO(dateStr); // parseISO is already used in file
  const day = d.getDay(); // 0 = Sun, 6 = Sat
  return day === 0 || day === 6;
};


const lopDays = Object.entries(leaveData.manualDaysAllocation || {}).reduce(
  (total, [leaveTypeId, allocated]) => {
    const leaveType = leaveTypes.find(lt => lt.id === Number(leaveTypeId));
    if (!leaveType) return total;

    const remaining = leaveType.remainingDays - allocated;
    return remaining < 0 ? total + Math.abs(remaining) : total;
  },
  0
);



const applySelectedDates = () => {
  setSelectDaysDialogOpen(false);

  // If half-day, treat selected as single half-day (business rule)
  if (leaveData.isHalfDay) {
    const val = selectedDatesInRange.length >= 1 ? 0.5 : 0;
    setTotalRequestedDays(val);
  } else {
    setTotalRequestedDays(selectedDatesInRange.length * 1.0);
  }

  // OPTIONAL: you might want to auto-adjust manualDaysAllocation here.
  // e.g. if exactly one leave type selected, allocate all days to that type:
  // if (leaveData.selectedLeaveTypes.length === 1) {
  //   const typeId = leaveData.selectedLeaveTypes[0];
  //   setLeaveData(prev => ({ ...prev, manualDaysAllocation: { [typeId]: selectedDatesInRange.length } }));
  // }
};

const handleClearSelectedDates = () => {
  setSelectedDatesInRange([]);
  setAutoAddedDates([]);
  setUserSelectedDates([]);
  setSelectDaysDialogOpen(false);

  const rangeDays = getRangeDays();
  setTotalRequestedDays(rangeDays);
  console.log('Cleared selectedDates. rangeDays:', rangeDays);
};







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


  


  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const token = localStorage.getItem("token");
        const response = await UserService.getCompleteProfile(token);
        setProfileData(response.employeeData);
        setLeaveData(prev => ({
          ...prev,
          employeeId: response.employeeData?.personal?.empId,
          employeeName: response.employeeData?.personal?.firstName
        }));
      } catch (error) {
        console.error("Failed to fetch profile:", error);
        setSnackbar({
          open: true,
          message: error.message || "Failed to load profile data",
          severity: "error",
        });
      }
    };

    fetchProfile();
    fetchLeaveTypes();
    fetchLeaveHistory();
  }, []);

  useEffect(() => {
    if (leaveData.fromDate) {
      const fromDate = new Date(leaveData.fromDate);
      const isLate = fromDate < new Date();
      setIsLateSubmission(isLate);
    }
  }, [leaveData.fromDate]);

  useEffect(() => {
    const days = calculateRequestedDays();
    const sickDays = leaveData.selectedLeaveTypes.reduce((total, typeId) => {
      const type = leaveTypes.find(t => t.id === typeId);
      if (type && isSickLeave(type)) {
        return total + (leaveData.manualDaysAllocation[typeId] || 0);
      }
      return total;
    }, 0);
    
    setSickLeaveDays(sickDays);
    setShowMedicalCertificateMessage(sickDays >= 2);
}, [leaveData.selectedLeaveTypes, leaveData.fromDate, leaveData.toDate, leaveData.isHalfDay, leaveData.manualDaysAllocation, selectedDatesInRange]);

  const getDatesArrayBetween = (fromDateStr, toDateStr) => {
  if (!fromDateStr || !toDateStr) return [];
  const from = parseISO(fromDateStr);
  const to = parseISO(toDateStr);
  if (to < from) return [];
  const arr = [];
  let cursor = from;
  while (cursor <= to) {
    arr.push(format(cursor, 'yyyy-MM-dd'));
    cursor = addDays(cursor, 1);
  }
  return arr;
};

  const fetchLeaveTypes = async () => {
    setLoadingLeaveTypes(true);
    try {
      const response = await api.get("/my-leave-types");
      if (!Array.isArray(response.data)) {
        throw new Error("Invalid leave types data format");
      }
      setLeaveTypes(response.data);
    } catch (error) {
      console.error("Error fetching leave types:", error);
      setSnackbar({
        open: true,
        message: "Failed to load leave types",
        severity: "error",
      });
      setLeaveTypes([]);
    } finally {
      setLoadingLeaveTypes(false);
    }
  };

  const fetchLeaveHistory = async () => {
    setLoading(true);
    try {
      const [leavesResponse, leaveTypesResponse] = await Promise.all([
        api.get("/leaves"),
        api.get("/my-leave-types")
      ]);

      const sortedData = leavesResponse.data.sort(
        (a, b) => new Date(b.createdDate) - new Date(a.createdDate)
      );

          const enhancedData = sortedData.map(leave => {
        const selectedDates = Array.isArray(leave.selectedDates) ? leave.selectedDates : [];

        return {
          ...leave,
          leaveDistribution: Object.entries(leave.manualDaysAllocation || {}).map(([typeId, days]) => {
            const type = leaveTypesResponse.data.find(lt => lt.id === parseInt(typeId));
            return {
              leaveTypeId: typeId,
              leaveTypeName: type ? type.name : "Unknown Type",
              allocatedDays: days,
              remainingDaysAfterAllocation: type ? (type.remainingDays - days) : 0
            };
          }),
          // hasMedicalCertificate: leave.medicalCertificateUrl !== null,
          hasMedicalCertificate: leave.hasMedicalCertificate === true,
          medicalCertificateName: leave.medicalCertificateName || "medical_certificate.pdf",
          // pass-through the selectedDates array from backend
          selectedDates: selectedDates,
          // optional short preview string (first 3 dates) for table cell
          selectedDatesPreview: selectedDates.length > 0
            ? selectedDates.slice(0, 3).map(d => format(parseISO(d), 'dd MMM')).join(', ') + (selectedDates.length > 3 ? ` (+${selectedDates.length-3})` : '')
            : ''
        };
      });

      setLeaveHistory(enhancedData);
    } catch (error) {
      console.error("Error fetching leave history:", error);
      setSnackbar({
        open: true,
        message: "Failed to load leave history",
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  // const isLeaveTypeValid = (type) => {
  //   if (!type || typeof type !== 'object') return false;
  //   if (typeof type.remainingDays !== 'number' || isNaN(type.remainingDays)) return false;
    
  //   const endDate = type.endDate ? new Date(type.endDate) : null;
  //   return (
  //     type.remainingDays > 0 &&
  //     (!endDate || endDate >= today)
  //   );
  // };

const isLeaveTypeValid = (type) => {
  if (!type.endDate) return true; // no expiry → valid
  return new Date(type.endDate) >= today;
};




  const isCompOffLeave = (leaveType) => {
    return leaveType.name.toLowerCase().includes('comp') || 
           leaveType.name.toLowerCase().includes('compensatory');
  };

  const isEarnedLeave = (leaveType) => {
    return leaveType.name.toLowerCase().includes('earned');
  };

  const isSickLeave = (leaveType) => {
    return leaveType.name.toLowerCase().includes('sick');
  };

  const isCasualLeave = (leaveType) => {
    return leaveType.name.toLowerCase().includes('casual');
  };

  const canCombineWithCurrentSelection = (type) => {
    if (leaveData.selectedLeaveTypes.length === 0) return true;
    
    const selectedTypes = leaveTypes.filter(lt => 
      leaveData.selectedLeaveTypes.includes(lt.id)
    );
    
    if (isCompOffLeave(type)) return true;
    
    const hasSickLeave = selectedTypes.some(isSickLeave);
    if (hasSickLeave && (isEarnedLeave(type) || isCompOffLeave(type))) {
      return true;
    }
    
    if (isSickLeave(type)) {
      const hasEarnedLeave = selectedTypes.some(isEarnedLeave);
      return hasEarnedLeave || selectedTypes.every(isCompOffLeave);
    }
    
    return selectedTypes.every(isCompOffLeave);
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setLeaveData(prev => ({ ...prev, [name]: value }));
  };

  const calculateRequestedDays = () => {
  // If user has explicitly selected days inside the range, compute from that list
  if (selectedDatesInRange && selectedDatesInRange.length > 0) {
    // If half day is selected — we expect the user to have selected only 1 date logically.
    if (leaveData.isHalfDay) {
      // If half-day and selectedDatesInRange includes 1 date -> 0.5
      const val = selectedDatesInRange.length >= 1 ? 0.5 : 0;
      setTotalRequestedDays(val);
      return val;
    }

    const val = selectedDatesInRange.length * 1.0; // each selected date = 1 day (no per-date half-day UI for simplicity)
    setTotalRequestedDays(val);
    return val;
  }

  // fallback: previous behavior — continuous range calculation
  if (!leaveData.fromDate || !leaveData.toDate) {
    setTotalRequestedDays(0);
    return 0;
  }
  
  const fromDate = new Date(leaveData.fromDate);
  const toDate = new Date(leaveData.toDate);
  
  let days = differenceInDays(toDate, fromDate) + 1;
  
  if (leaveData.isHalfDay) {
    days -= 0.5;
  }
  
  setTotalRequestedDays(days);
  return days;
};


  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setSnackbar({
        open: true,
        message: "File size should be less than 5MB",
        severity: "error",
      });
      return;
    }

    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
      setSnackbar({
        open: true,
        message: "Only PDF, JPEG, and PNG files are allowed",
        severity: "error",
      });
      return;
    }

    setLeaveData(prev => ({
      ...prev,
      medicalCertificate: file,
      medicalCertificateName: file.name
    }));
  };

  const handleRemoveFile = () => {
    setLeaveData(prev => ({
      ...prev,
      medicalCertificate: null,
      medicalCertificateName: ""
    }));
  };

  const handleDownloadFile = async (leaveId, fileName) => {
    setLoadingFile(leaveId);
    try {
      const response = await api.get(`/leaves/${leaveId}/medical-certificate`, {
        responseType: 'blob'
      });
      
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
      
      link.parentNode.removeChild(link);
      setTimeout(() => {
        window.URL.revokeObjectURL(fileURL);
      }, 100);
    } catch (error) {
      console.error("Error downloading file:", error);
      setSnackbar({
        open: true,
        message: error.response?.status === 403 
          ? "You don't have permission to view this file" 
          : "Failed to download document",
        severity: "error",
      });
    } finally {
      setLoadingFile(null);
    }
  };

  const handleViewFile = async (leaveId, fileName) => {
    setLoadingFile(leaveId);
    try {
      const response = await api.get(`/leaves/${leaveId}/medical-certificate`, {
        responseType: 'blob'
      });
      
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
      setSnackbar({
        open: true,
        message: error.response?.status === 403 
          ? "You don't have permission to view this file" 
          : "Failed to load document",
        severity: "error",
      });
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

  const renderMedicalCertificateCell = (leave) => {
    if (!leave.hasMedicalCertificate) {
      return <Typography variant="body2">N/A</Typography>;
    }

    return (
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <Tooltip title={leave.medicalCertificateName || "Medical Certificate"}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            {getFileIcon(leave.medicalCertificateName)}
          </Box>
        </Tooltip>
        <Tooltip title="View Certificate">
          <IconButton 
  onClick={() => {
    setFilePreview({
      open: true,
      url: leave.fileUrl,
      type: 'image', // optional if always image, or detect by extension
      name: leave.medicalCertificateName
    });
  }}

            disabled={loadingFile === leave.id}
            size="small"
          >
            {loadingFile === leave.id ? <CircularProgress size={24} /> : <Visibility color="primary" />}
          </IconButton>
        </Tooltip>
        <Tooltip title="Download Certificate">
          {/* <IconButton 
            onClick={() => handleDownloadFile(leave.id, leave.medicalCertificateName)}
            disabled={loadingFile === leave.id}
            size="small"
          >
            {loadingFile === leave.id ? <CircularProgress size={24} /> : <Download color="secondary" />}
          </IconButton> */}
          <IconButton
  onClick={() => {
    if (!leave?.fileUrl) return;

    const downloadUrl = leave.fileUrl.replace('/upload/', '/upload/fl_attachment/');
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', leave.medicalCertificateName || 'medical_certificate');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }}
  size="small"
>
  <Download />
</IconButton>

        </Tooltip>
      </Box>
    );
  };

  const handleLeaveTypeSelection = (typeId, isChecked) => {
    if (!typeId) return;
    
    const type = leaveTypes.find(t => t.id === typeId);
    if (!type) return;
    
    if (isChecked && !canCombineWithCurrentSelection(type)) {
      setSnackbar({
        open: true,
        message: "You can only combine this leave type with compensatory off",
        severity: "error",
      });
      return;
    }
    
    setLeaveData(prev => {
      const newSelectedTypes = isChecked
        ? [...prev.selectedLeaveTypes, typeId]
        : prev.selectedLeaveTypes.filter(id => id !== typeId);

      const newManualAllocation = { ...prev.manualDaysAllocation };
      if (isChecked) {
        newManualAllocation[typeId] = 0.5;
      } else {
        delete newManualAllocation[typeId];
      }

      const distribution = Object.entries(newManualAllocation).map(([id, days]) => {
        const lt = leaveTypes.find(t => t.id === id);
        return lt ? {
          leaveTypeId: id,
          leaveTypeName: lt.name,
          allocatedDays: days,
          remainingDays: lt.remainingDays - days,
          totalAvailableDays: lt.remainingDays
        } : null;
      }).filter(Boolean);

      return {
        ...prev,
        selectedLeaveTypes: newSelectedTypes,
        manualDaysAllocation: newManualAllocation,
        leaveDistribution: distribution
      };
    });
  };

  const adjustManualAllocation = (typeId, operation) => {
    setLeaveData(prev => {
      const currentValue = prev.manualDaysAllocation[typeId] || 0;
      const leaveType = leaveTypes.find(lt => lt.id === typeId);
      const maxAvailable = leaveType?.remainingDays || 0;

      let newValue = currentValue;
      // if (operation === 'increment' && currentValue < maxAvailable) {
      //   newValue = parseFloat((currentValue + 0.5).toFixed(1));
      if (operation === 'increment') {
    newValue = parseFloat((currentValue + 0.5).toFixed(1));
      } else if (operation === 'decrement' && currentValue > 0) {
        newValue = parseFloat((currentValue - 0.5).toFixed(1));
      }

      const newManualAllocation = {
        ...prev.manualDaysAllocation,
        [typeId]: newValue
      };

      const distribution = Object.entries(newManualAllocation)
        .map(([id, days]) => {
          const lt = leaveTypes.find(t => t.id === id);
          return lt ? {
            leaveTypeId: id,
            leaveTypeName: lt.name,
            allocatedDays: days,
            remainingDays: lt.remainingDays - days,
            totalAvailableDays: lt.remainingDays
          } : null;
        })
        .filter(Boolean);

      return {
        ...prev,
        manualDaysAllocation: newManualAllocation,
        leaveDistribution: distribution
      };
    });
  };

  const calculateTotalAllocatedDays = () => {
    return Object.values(leaveData.manualDaysAllocation).reduce((sum, days) => sum + days, 0);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);

    if (new Date(leaveData.toDate) < new Date(leaveData.fromDate)) {
      setSnackbar({
        open: true,
        message: "'To Date' cannot be earlier than 'From Date'",
        severity: "error",
      });
      setSubmitting(false);
      return;
    }

    if (leaveData.selectedLeaveTypes.length === 0) {
      setSnackbar({
        open: true,
        message: "Please select at least one leave type",
        severity: "error",
      });
      setSubmitting(false);
      return;
    }

    if (isLateSubmission && !leaveData.lateApplicationReason) {
      setSnackbar({
        open: true,
        message: "Please provide a reason for late application",
        severity: "error",
      });
      setSubmitting(false);
      return;
    }

    const allocatedDays = calculateTotalAllocatedDays();
    const requestedDays = calculateRequestedDays();
    
    if (Math.abs(allocatedDays - requestedDays) > 0.01) {
      setSnackbar({
        open: true,
        message: `Please allocate exactly ${requestedDays.toFixed(1)} days (currently allocated ${allocatedDays.toFixed(1)} days)`,
        severity: "error",
      });
      setSubmitting(false);
      return;
    }

    if (sickLeaveDays >= 2 && !leaveData.medicalCertificate) {
      setSnackbar({
        open: true,
        message: "Medical certificate is required for 2 or more sick leave days",
        severity: "error",
      });
      setSubmitting(false);
      return;
    }

    try {
      const formData = new FormData();
      
      const leaveRequest = {
        employeeId: leaveData.employeeId,
        employeeName: leaveData.employeeName,
        fromDate: leaveData.fromDate,
        toDate: leaveData.toDate,
        reason: leaveData.reason,
        isHalfDay: leaveData.isHalfDay,
        halfDayPeriod: leaveData.halfDayPeriod,
        selectedLeaveTypes: leaveData.selectedLeaveTypes,
        manualDaysAllocation: leaveData.manualDaysAllocation,
        totalDays: requestedDays,
          selectedDates: selectedDatesInRange, // NEW: list of yyyy-MM-dd strings
        isLateSubmission: isLateSubmission,
        sickLeaveDays: sickLeaveDays,
        lateApplicationReason: isLateSubmission ? leaveData.lateApplicationReason : undefined
      };

      formData.append('leaveRequest', new Blob([JSON.stringify(leaveRequest)], {
        type: 'application/json'
      }));

      if (leaveData.medicalCertificate) {
        formData.append('file', leaveData.medicalCertificate);
      }

      const response = await api.post("/leaves/add", formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });

      setSnackbar({
        open: true,
        message: "Leave request submitted successfully!",
        severity: "success",
      });

      setLeaveData(prev => ({
        ...prev,
        fromDate: "",
        toDate: "",
        reason: "",
        isHalfDay: false,
        halfDayPeriod: "first",
        selectedLeaveTypes: [],
        leaveDistribution: [],
        manualDaysAllocation: {},
        lateApplicationReason: "",
        medicalCertificate: null,
        medicalCertificateName: ""
      }));

      setIsLateSubmission(false);
      setSickLeaveDays(0);
      await Promise.all([fetchLeaveHistory(), fetchLeaveTypes()]);
    } catch (error) {
      console.error("Error submitting leave:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to submit leave request",
        severity: "error",
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleViewLeave = (leave) => {
    setSelectedLeave(leave);
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setSelectedLeave(null);
  };

  const filteredLeaves = leaveHistory.filter((leave) => {
    if (filterStatus === "All") return true;
    return leave.status.toLowerCase() === filterStatus.toLowerCase();
  });

  const totalPages = Math.ceil(filteredLeaves.length / recordsPerPage);
  const paginatedLeaves = filteredLeaves.slice(
    currentPage * recordsPerPage,
    (currentPage + 1) * recordsPerPage
  );

  const handlePrevPage = () => currentPage > 0 && setCurrentPage(currentPage - 1);
  const handleNextPage = () =>
    currentPage < totalPages - 1 && setCurrentPage(currentPage + 1);

  const handleCloseSnackbar = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  const statusColor = {
    Approved: "success",
    Rejected: "error",
    Pending: "warning",
    APPROVED: "success",
    REJECTED: "error",
    PENDING: "warning"
  };

  const formatDisplayDate = (dateString) => {
    if (!dateString) return "N/A";
    return format(parseISO(dateString), 'MMM d, yyyy');
  };

  const formatLeaveDays = (leave) => {
    if (leave.isHalfDay) {
      return `${leave.totalDays.toFixed(1)} days (${leave.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'})`;
    }
    return `${leave.totalDays} day(s)`;
  };

  const formatLeaveTypesWithDays = (leave) => {
    if (!leave.leaveDistribution || leave.leaveDistribution.length === 0) {
      return "N/A";
    }
    
    return leave.leaveDistribution.map(dist => 
      `${dist.leaveTypeName} (${dist.allocatedDays} days)`
    ).join(" + ");
  };

  return (
    <Box sx={{ p: 4, maxWidth: 1200, mx: "auto" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: 700 }}>
        Leave Management
      </Typography>

      <Paper elevation={4} sx={{ p: 4, mb: 5, borderRadius: 3 }}>
        <Typography variant="h6" gutterBottom sx={{ mb: 3 }}>
          New Leave Request
        </Typography>
        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Employee ID"
                value={leaveData.employeeId}
                InputProps={{ readOnly: true }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Employee Name"
                value={leaveData.employeeName}
                InputProps={{ readOnly: true }}
              />
            </Grid>
            
            <Grid item xs={12}>
              <Typography variant="subtitle2" gutterBottom>
                Select Leave Types:
              </Typography>
              {loadingLeaveTypes ? (
                <CircularProgress size={24} />
              ) : leaveTypes.length === 0 ? (
                <Alert severity="warning">No leave types available</Alert>
              ) : (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                  {leaveTypes.filter(isLeaveTypeValid).map((type) => {
                    const canSelect = canCombineWithCurrentSelection(type);
                    const isSelected = leaveData.selectedLeaveTypes.includes(type.id);
                    
                    return (
                      <Box 
                        key={type.id} 
                        sx={{ 
                          display: 'flex', 
                          alignItems: 'center',
                          opacity: isSelected ? 1 : (canSelect ? 1 : 0.5)
                        }}
                      >
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={isSelected}
                              onChange={(e) => handleLeaveTypeSelection(type.id, e.target.checked)}
                              disabled={!isSelected && !canSelect}
                            />
                          }
                          label={`${type.name} (${type.remainingDays} days)`}
                        />
                        {isSelected && (
                          <Box sx={{ display: 'flex', alignItems: 'center', ml: 1 }}>
                            <IconButton 
                              size="small" 
                              onClick={() => adjustManualAllocation(type.id, 'decrement')}
                              disabled={(leaveData.manualDaysAllocation[type.id] || 0) <= 0}
                            >
                              <Remove fontSize="small" />
                            </IconButton>
                            <Typography sx={{ minWidth: '40px', textAlign: 'center' }}>
                              {leaveData.manualDaysAllocation[type.id] || 0}
                            </Typography>
                            <IconButton 
                              size="small" 
                              onClick={() => adjustManualAllocation(type.id, 'increment')}
                              // disabled={(leaveData.manualDaysAllocation[type.id] || 0) >= type.remainingDays}
                              disabled={false}

                            >
                              <Add fontSize="small" />
                            </IconButton>
                          </Box>
                        )}
                      </Box>
                    );
                  })}
                </Box>
              )}
            </Grid>


            {lopDays > 0 && (
  <div
    style={{
      marginTop: "10px",
      padding: "8px 12px",
      backgroundColor: "#fff3cd",
      border: "1px solid #ffeeba",
      borderRadius: "6px",
      color: "#856404",
      fontSize: "13px"
    }}
  >
    ⚠️ <strong>{lopDays.toFixed(1)}</strong> day(s) will be treated as
    <strong> Leave Without Pay (LOP)</strong>.
  </div>
)}


            {leaveData.selectedLeaveTypes.length > 0 && (
              <Grid item xs={12}>
                <Typography variant="subtitle1" gutterBottom>
                  Selected Leave Types:
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
                  {leaveData.selectedLeaveTypes.map(typeId => {
                    const type = leaveTypes.find(t => t.id === typeId);
                    if (!type) return null;
                    
                    return (
                      <Paper 
                        key={typeId} 
                        elevation={2} 
                        sx={{ 
                          p: 2, 
                          minWidth: 200,
                          border: '1px solid',
                          borderColor: 'primary.main',
                          borderRadius: 1
                        }}
                      >
                        <Typography variant="body1" fontWeight="bold">
                          {type.name}
                        </Typography>
                        <Typography variant="body2">
                          Allocated: {leaveData.manualDaysAllocation[typeId] || 0} day(s)
                        </Typography>
                        <Typography variant="body2">
                          Remaining: {(type.remainingDays - (leaveData.manualDaysAllocation[typeId] || 0)).toFixed(1)} day(s)
                        </Typography>
                      </Paper>
                    );
                  })}
                </Box>
                <Box sx={{ mt: 2 }}>
                  <Typography variant="body1" fontWeight="bold">
                    Total Allocated Days: {calculateTotalAllocatedDays().toFixed(1)}
                  </Typography>
                  <Typography variant="body1" fontWeight="bold">
                    Total Requested Days: {totalRequestedDays.toFixed(1)}
                  </Typography>
                  {Math.abs(calculateTotalAllocatedDays() - totalRequestedDays) > 0.01 && (
                    <Typography variant="body2" color="error">
                      Please allocate exactly {totalRequestedDays.toFixed(1)} days
                    </Typography>
                  )}
                </Box>
              </Grid>
            )}

            {showMedicalCertificateMessage && (
              <Grid item xs={12}>
                {sickLeaveDays > 2 ? (
                  <Paper elevation={2} sx={{ p: 2, mb: 2 }}>
                    <Typography variant="subtitle1" gutterBottom>
                      Medical Certificate Upload (Required for 2+ sick leave days)
                    </Typography>
                    {leaveData.medicalCertificate ? (
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                          {getFileIcon(leaveData.medicalCertificateName)}
                          <Typography sx={{ ml: 1 }}>
                            {leaveData.medicalCertificateName}
                          </Typography>
                        </Box>
                        <IconButton onClick={handleRemoveFile} color="error">
                          <Delete />
                        </IconButton>
                      </Box>
                    ) : (
                      <>
                        <input
                          accept=".pdf,.jpg,.jpeg,.png"
                          style={{ display: 'none' }}
                          id="medical-certificate-upload"
                          type="file"
                          onChange={handleFileUpload}
                        />
                        <label htmlFor="medical-certificate-upload">
                          <Button
                            variant="outlined"
                            component="span"
                            startIcon={<CloudUpload />}
                          >
                            Upload Medical Certificate
                          </Button>
                        </label>
                        <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                          Accepted formats: PDF, JPG, PNG (Max 5MB)
                        </Typography>
                      </>
                    )}
                  </Paper>
                ) : (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Note: Medical certificate will be required if sick leave reaches more than 2 days.
                  </Alert>
                )}
              </Grid>
            )}

            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type="date"
                name="fromDate"
                label="From Date"
                value={leaveData.fromDate}
                onChange={handleChange}
                required
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type="date"
                name="toDate"
                label="To Date"
                value={leaveData.toDate}
                onChange={handleChange}
                required
                InputLabelProps={{ shrink: true }}
                inputProps={{
                  min: leaveData.fromDate || undefined
                }}
              />
            </Grid>

            {isLateSubmission && (
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  multiline
                  rows={3}
                  name="lateApplicationReason"
                  label="Reason for late application"
                  value={leaveData.lateApplicationReason}
                  onChange={handleChange}
                  required
                  helperText="Please explain why you couldn't apply for leave in advance"
                />
              </Grid>
            )}

            <Grid item xs={12}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={leaveData.isHalfDay}
                      onChange={(e) => setLeaveData({...leaveData, isHalfDay: e.target.checked})}
                    />
                  }
                  label="Half Day"
                />
                {leaveData.isHalfDay && (
                  <FormControl component="fieldset">
                    <RadioGroup
                      row
                      name="halfDayPeriod"
                      value={leaveData.halfDayPeriod}
                      onChange={handleChange}
                    >
                      <FormControlLabel
                        value="first"
                        control={<Radio />}
                        label="First Half"
                      />
                      <FormControlLabel
                        value="second"
                        control={<Radio />}
                        label="Second Half"
                      />
                    </RadioGroup>
                  </FormControl>
                )}
              </Box>
            </Grid>

            <Grid item xs={12} sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
  <Button
    variant="outlined"
    onClick={openSelectDaysDialog}
    disabled={!leaveData.fromDate || !leaveData.toDate}
  >
    Select specific days
  </Button>
  <Typography variant="body2" color="textSecondary">
    {selectedDatesInRange.length > 0 ? `${selectedDatesInRange.length} day(s) selected` : 'No specific days selected'}
  </Typography>
</Grid>


            <Grid item xs={12}>
              <Paper elevation={2} sx={{ p: 2 }}>
                <Typography variant="subtitle1" gutterBottom>
                  Date Range Calculation
                </Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography>
                    Selected Dates: {leaveData.fromDate ? formatDisplayDate(leaveData.fromDate) : 'N/A'} to {leaveData.toDate ? formatDisplayDate(leaveData.toDate) : 'N/A'}
                  </Typography>
                  <Typography fontWeight="bold">
                    Total Days Requested: {totalRequestedDays.toFixed(1)}
                  </Typography>
                </Box>
                {leaveData.isHalfDay && (
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    (Half day selected: {leaveData.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'})
                  </Typography>
                )}
                {isLateSubmission && (
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    You are applying for leave with past dates. Please ensure this is for emergency situations only.
                  </Alert>
                )}
              </Paper>
            </Grid>

            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={4}
                name="reason"
                label="Reason for Leave"
                value={leaveData.reason}
                onChange={handleChange}
                required
              />
            </Grid>
          </Grid>
          <Box sx={{ mt: 4, display: "flex", justifyContent: "flex-end" }}>
            <Button
              type="submit"
              variant="contained"
              size="large"
              disabled={
                submitting || 
                leaveData.selectedLeaveTypes.length === 0 ||
                Math.abs(calculateTotalAllocatedDays() - totalRequestedDays) > 0.01 ||
                (isLateSubmission && !leaveData.lateApplicationReason) ||
                (sickLeaveDays >= 2 && !leaveData.medicalCertificate)
              }
            >
              {submitting ? (
                <>
                  <CircularProgress size={24} sx={{ mr: 1 }} />
                  Submitting...
                </>
              ) : "Submit Request"}
            </Button>
          </Box>
        </form>
      </Paper>

      <Divider sx={{ mb: 5 }} />

      <Box>
        <Typography variant="h5" gutterBottom sx={{ fontWeight: 700 }}>
          Leave History
        </Typography>

        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
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
              {["All", "Pending", "Approved", "Rejected"].map((status) => (
                <MenuItem key={status} value={status}>
                  {status}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            onClick={fetchLeaveHistory}
            disabled={loading}
          >
            {loading ? "Refreshing..." : "Refresh"}
          </Button>
        </Box>

        {loading ? (
          <Box display="flex" justifyContent="center" my={4}>
            <CircularProgress />
          </Box>
        ) : paginatedLeaves.length > 0 ? (
          <>
            <Paper elevation={3} sx={{ mb: 2 }}>
              <Table>
                <TableHead sx={{ bgcolor: 'primary.main' }}>
                  <TableRow>
                    <TableCell sx={{ color: 'white', fontWeight: 600 }}>ID</TableCell>
                    <TableCell sx={{ color: 'white', fontWeight: 600 }}>Employee ID</TableCell>
<TableCell sx={{ color: 'white', fontWeight: 600 }}>Employee Name</TableCell>

                                       <TableCell sx={{ color: 'white', fontWeight: 600 }}>Leave Type</TableCell>
                    <TableCell sx={{ color: 'white', fontWeight: 600 }}>Selected Days</TableCell>
                    <TableCell sx={{ color: 'white', fontWeight: 600 }}>Period</TableCell>

                    <TableCell sx={{ color: 'white', fontWeight: 600 }}>Days</TableCell>
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
                  {paginatedLeaves.map((leave) => (
                    <TableRow key={leave.id} hover>
                      <TableCell>#{leave.id}</TableCell>
                      <TableCell>{leave.employeeId || 'N/A'}</TableCell>
<TableCell>{leave.employeeName || 'N/A'}</TableCell>

                      <TableCell>{formatLeaveTypesWithDays(leave)}</TableCell>


                      <TableCell>
  {leave.selectedDates && leave.selectedDates.length > 0 ? (
    <Tooltip
      title={leave.selectedDates
        .map(d => format(parseISO(d), 'EEE, MMM d, yyyy'))
        .join('\n')}
    >
      <Typography variant="body2">
        {leave.selectedDatesPreview ||
          leave.selectedDates
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
                        {formatDisplayDate(leave.fromDate)} - {formatDisplayDate(leave.toDate)}
                        {leave.isHalfDay && (
                          <Typography variant="caption" display="block">
                            {leave.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{formatLeaveDays(leave)}</TableCell>
                      <TableCell>
                        <Chip
                          label={leave.reportingManagerStatus || 'N/A'}
                          color={statusColor[leave.reportingManagerStatus] || 'default'}
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={leave.hrStatus || 'N/A'}
                          color={statusColor[leave.hrStatus] || 'default'}
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell>{leave.reportingManagerComment || 'N/A'}</TableCell>
                      <TableCell>{leave.hrComment || 'N/A'}</TableCell>
                      <TableCell>{formatDisplayDate(leave.createdDate)}</TableCell>
                      <TableCell>
                        {renderMedicalCertificateCell(leave)}
                      </TableCell>
                      <TableCell>
                        {hasAction("VIEW_LEAVE") && (
                          <Button size="small" onClick={() => handleViewLeave(leave)}>
                            Details
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>

            <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 3 }}>
              <IconButton
                onClick={handlePrevPage}
                disabled={currentPage === 0}
                sx={{
                  bgcolor: currentPage === 0 ? 'action.disabledBackground' : 'primary.main',
                  color: currentPage === 0 ? 'text.disabled' : 'primary.contrastText',
                }}
              >
                <ChevronLeft />
              </IconButton>
              <Typography variant="body1">
                Page {currentPage + 1} of {totalPages}
              </Typography>
              <IconButton
                onClick={handleNextPage}
                disabled={currentPage === totalPages - 1}
                sx={{
                  bgcolor: currentPage === totalPages - 1 ? 'action.disabledBackground' : 'primary.main',
                  color: currentPage === totalPages - 1 ? 'text.disabled' : 'primary.contrastText',
                }}
              >
                <ChevronRight />
              </IconButton>
            </Box>
          </>
        ) : (
          <Alert severity="info" sx={{ mt: 2 }}>
            No leave records found
          </Alert>
        )}
      </Box>

      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
        <DialogTitle>Leave Details</DialogTitle>
        <DialogContent>
          {selectedLeave && (
            <List>
              <ListItem>
                <ListItemText primary="Request ID" secondary={`#${selectedLeave.id}`} />
              </ListItem>
        <ListItem>
  <ListItemText 
    primary="Employee" 
    secondary={`${selectedLeave.employeeName || 'N/A'} (${selectedLeave.employeeId || 'N/A'})`} 
  />
</ListItem>

              <ListItem>
                <ListItemText 
                  primary="Leave Types" 
                  secondary={
                    selectedLeave.leaveDistribution?.map((dist, index) => (
                      <Typography key={index} variant="body2">
                        {dist.leaveTypeName} ({dist.allocatedDays} day{dist.allocatedDays !== 1 ? 's' : ''})
                      </Typography>
                    )) || 'N/A'
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemText 
                  primary="Period"
                  secondary={
                    <>
                      {formatDisplayDate(selectedLeave.fromDate)} to {formatDisplayDate(selectedLeave.toDate)}
                      {selectedLeave.isHalfDay && (
                        <Typography variant="caption" display="block">
                          {selectedLeave.halfDayPeriod === 'first' ? 'First Half' : 'Second Half'}
                        </Typography>
                      )}
                    </>
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemText primary="Total Days" secondary={formatLeaveDays(selectedLeave)} />
              </ListItem>
              <ListItem>
                <ListItemText
                  primary="Manager Status"
                  secondary={
                    <Chip
                      label={selectedLeave.reportingManagerStatus || 'N/A'}
                      color={statusColor[selectedLeave.reportingManagerStatus] || 'default'}
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
                      label={selectedLeave.hrStatus || 'N/A'}
                      color={statusColor[selectedLeave.hrStatus] || 'default'}
                      sx={{ fontWeight: 600 }}
                    />
                  }
                />
              </ListItem>
              {selectedLeave.reportingManagerComment && (
                <ListItem>
                  <ListItemText
                    primary="Manager's Comment"
                    secondary={selectedLeave.reportingManagerComment}
                  />
                </ListItem>
              )}
              {selectedLeave.hrComment && (
                <ListItem>
                  <ListItemText
                    primary="HR's Comment"
                    secondary={selectedLeave.hrComment}
                  />
                </ListItem>
              )}
              <ListItem>
                <ListItemText primary="Submitted On" secondary={formatDisplayDate(selectedLeave.createdDate)} />
              </ListItem>
              <ListItem>
                <ListItemText primary="Reason" secondary={selectedLeave.reason} />
              </ListItem>
              {selectedLeave.isLateSubmission && selectedLeave.lateApplicationReason && (
                <ListItem>
                  <ListItemText
                    primary="Late Application Reason"
                    secondary={selectedLeave.lateApplicationReason}
                  />
                </ListItem>
              )}
              {/* {selectedLeave.hasMedicalCertificate && (
                <ListItem>
                  <ListItemText
                    primary="Medical Certificate"
                    secondary={
                      <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                        <Button
                          variant="outlined"
                          onClick={() => handleViewFile(selectedLeave.id, selectedLeave.medicalCertificateName)}
                          startIcon={<Visibility />}
                        >
                          View
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => handleDownloadFile(selectedLeave.id, selectedLeave.medicalCertificateName)}
                          startIcon={<Download />}
                          disabled={loadingFile === selectedLeave.id}
                        >
                          {loadingFile === selectedLeave.id ? 'Downloading...' : 'Download'}
                        </Button>
                      </Box>
                    }
                  />
                </ListItem> */}
              {/* )} */}
              <Button
  variant="outlined"
  onClick={() =>
    setFilePreview({
      open: true,
      url: selectedLeave.fileUrl,
      type: 'image',
      name: selectedLeave.medicalCertificateName
    })
  }
  startIcon={<Visibility />}>
  View
</Button>

<Button
  variant="outlined"
  onClick={() => {
    if (!selectedLeave?.fileUrl) return;

    const downloadUrl = selectedLeave.fileUrl.replace('/upload/', '/upload/fl_attachment/');
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', selectedLeave.medicalCertificateName || 'medical_certificate');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }}
  startIcon={<Download />}
>
  Download
</Button>


            </List>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Close</Button>
        </DialogActions>
      </Dialog>


<Dialog open={selectDaysDialogOpen} onClose={closeSelectDaysDialog} maxWidth="xs" fullWidth>
  <DialogTitle>Select days within range</DialogTitle>
  <DialogContent dividers>
    <Typography variant="body2" sx={{ mb: 1 }}>
      Select the exact dates within {leaveData.fromDate ? formatDisplayDate(leaveData.fromDate) : 'N/A'} to {leaveData.toDate ? formatDisplayDate(leaveData.toDate) : 'N/A'} when you were not available.
    </Typography>

    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, maxHeight: 320, overflow: 'auto', mt: 1 }}>
      {getDatesArrayBetween(leaveData.fromDate, leaveData.toDate).map(dateStr => {
         const checked = selectedDatesInRange.includes(dateStr);
         return (
           <FormControlLabel
             key={dateStr}
             control={<Checkbox checked={checked} onChange={() => toggleDateInSelection(dateStr)} />}
             label={format(parseISO(dateStr), 'EEE, MMM d, yyyy')}
           />
         );
      })}
    </Box>

    <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
      Tip: select only the dates you were absent. You can select multiple non-contiguous dates.
    </Typography>
  </DialogContent>
<DialogActions>
  <Button onClick={handleClearSelectedDates}>Clear</Button>
  <Button onClick={closeSelectDaysDialog}>Cancel</Button>
  <Button variant="contained" onClick={applySelectedDates}>Apply ({selectedDatesInRange.length})</Button>
</DialogActions>

</Dialog>



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

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default Leave;