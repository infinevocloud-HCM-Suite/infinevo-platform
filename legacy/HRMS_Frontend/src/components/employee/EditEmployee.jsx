import React, { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { getNames } from "country-list";
import axios from "axios";
import {
  Container,
  Typography,
  Box,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Button,
  CircularProgress,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Avatar,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Snackbar,
  Alert,
  Autocomplete,
  IconButton,
  Paper,
  Grid,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Tabs,
  Tab,
  Badge,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TableHead
} from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import CancelIcon from "@mui/icons-material/Cancel";
import EditIcon from "@mui/icons-material/Edit";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import PersonIcon from "@mui/icons-material/Person";
import WorkIcon from "@mui/icons-material/Work";
import ContactMailIcon from "@mui/icons-material/ContactMail";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import ReportIcon from "@mui/icons-material/Report";
import PaletteIcon from "@mui/icons-material/Palette";
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import ImageIcon from '@mui/icons-material/Image';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const documentCategories = [
  {
    title: "Identification Documents",
    documents: [
      { type: "AADHAR_CARD", mandatory: true },
      { type: "PHOTO", mandatory: true },
      { type: "OTHER_IDENTIFICATION", mandatory: false }
    ]
  },
  {
    title: "Government IDs",
    documents: [
      { type: "PAN_CARD", mandatory: true },
      { type: "EPIC_CARD", mandatory: false },
      { type: "OTHER_GOVERNMENT_ID", mandatory: false }
    ]
  },
  {
    title: "Address Proof Documents",
    documents: [
      { type: "RENT_AGREEMENT", mandatory: false },
      { type: "LIGHT_BILL", mandatory: false },
      { type: "BANK_STATEMENT", mandatory: false },
      { type: "OTHER_ADDRESS_PROOF", mandatory: false }
    ]
  },
  {
    title: "Qualification Documents",
    documents: [
      { type: "TENTH_CERTIFICATE", mandatory: true },
      { type: "TWELFTH_CERTIFICATE_DIPLOMA", mandatory: true },
      { type: "DEGREE_CERTIFICATE", mandatory: false },
      { type: "POST_GRADUATION_CERTIFICATE", mandatory: false },
      { type: "OTHER_QUALIFICATION", mandatory: false }
    ]
  },
  {
    title: "Previous Company Documents",
    documents: [
      { type: "APPOINTMENT_LETTER", mandatory: true, multiple: true },
      { type: "RELIEVING_LETTER", mandatory: true, multiple: true },
      { type: "EXPERIENCE_LETTER", mandatory: true, multiple: true },
      { type: "SALARY_SLIP_LAST_3_MONTHS", mandatory: true, multiple: true },
      { type: "OTHER_PREVIOUS_COMPANY_DOC", mandatory: false, multiple: true }
    ],
    allowMultipleSets: true
  },
  {
    title: "Passport Documents",
    documents: [
      { type: "PASSPORT", mandatory: false }
    ],
    allowMultipleSets: false
  }
];

const EditEmployee = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [countrySearch, setCountrySearch] = useState("");
  const [nationalitySearch, setNationalitySearch] = useState("");
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success"
  });
  const [activeTab, setActiveTab] = useState(0);
  const [existingEmpIds, setExistingEmpIds] = useState([]);
  const [errors, setErrors] = useState({});
  const [originalEmpId, setOriginalEmpId] = useState("");
  const [reportingManagers, setReportingManagers] = useState([]);
  const [isLoadingManagers, setIsLoadingManagers] = useState(false);

  // Document related states
  const [documents, setDocuments] = useState({
    currentCategory: "",
    currentDocumentType: "",
    customDocumentName: "",
    file: null,
    fileName: "",
    uploadedDocuments: [],
    currentCompanyIndex: 0,
    companyDocuments: [
      {
        companyName: "Current Company",
        documents: []
      }
    ],
    fileInputKey: Date.now()
  });
  const [previewOpen, setPreviewOpen] = useState(false);
  const [currentPreview, setCurrentPreview] = useState(null);
  const [expandedCategory, setExpandedCategory] = useState("");

  const timeZones = [
    "UTC - Coordinated Universal Time",
    "GMT - Greenwich Mean Time",
    "IST - Indian Standard Time (UTC+5:30)",
    "EST - Eastern Standard Time (UTC-5)",
    "PST - Pacific Standard Time (UTC-8)",
    "CET - Central European Time (UTC+1)",
    "JST - Japan Standard Time (UTC+9)",
    "AEST - Australian Eastern Standard Time (UTC+10)",
    "CST - China Standard Time (UTC+8)",
    "MST - Mountain Standard Time (UTC-7)",
    "BST - British Summer Time (UTC+1)",
    "CST - Central Standard Time (UTC-6)",
    "EET - Eastern European Time (UTC+2)",
    "AKST - Alaska Standard Time (UTC-9)",
    "HST - Hawaii Standard Time (UTC-10)",
  ];

  const [employee, setEmployee] = useState({
    personal: {
      empId: "",
      firstName: "",
      middleName: "",
      lastName: "",
      dateOfBirth: "",
      gender: "",
      maritalStatus: "",
      nationality: "",
      ethnicity: "",
      employmentStatus: "",
    },
    identification: {
      immigrationStatus: "",
      panCardNumber: "",
      addressProof: "",
      addressDocumentName: "",
      addressDocumentNumber: "",
      personalTaxId: "",
      socialInsurance: "",
      idProof: "",
      documentName: "",
      documentNumber: ""
    },
    work: {
      department: "",
      jobTitle: "",
      payGrade: "",
      doj: "",
      terminationDate: "",
      workstationId: "",
      timeZone: "",
      shiftStartTime: "",
      shiftEndTime: ""
    },
    contact: {
      residentialAddress: "",
      permanentAddress: "",
      city: "",
      state: "",
      country: "",
      postalCode: "",
      workEmail: "",
      personalEmail: "",
      mobileNumber: "",
      primaryEmergencyContactName: "",
      primaryEmergencyContactNumber: "",
      relationshipToPrimaryEmergencyContact: "",
      secondaryEmergencyContactName: "",
      secondaryEmergencyContactNumber: "",
      relationshipToSecondaryEmergencyContact: "",
      familyDoctorName: "",
      familyDoctorContactNumber: ""
    },
    report: {
      reportingManagerId: "",
      reportingManagerName: "",
      indirectManager: "",
      firstLevelApprover: "",
      secondLevelApprover: "",
      thirdLevelApprover: "",
      note: ""
    }
  });

  const countryOptions = useMemo(() => {
    return getNames().map((country) => ({
      label: country,
      value: country,
    }));
  }, []);

  const [filteredCountries, setFilteredCountries] = useState(countryOptions);
  const [filteredNationalities, setFilteredNationalities] = useState(countryOptions);

  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const token = localStorage.getItem("token");

        // Fetch existing employee IDs
        const idsResponse = await axios.get(`${API_BASE_URL}/employees/ids`, {
          headers: {
            "Authorization": `Bearer ${token}`
          }
        });

        if (idsResponse.data && idsResponse.data.data) {
          setExistingEmpIds(idsResponse.data.data.map(id => id.toUpperCase()));
        }

        // Fetch employee data
        const response = await axios.get(
          `${API_BASE_URL}/employees/${id}`,
          {
            headers: {
              Authorization: `Bearer ${token}`
            }
          }
        );

        if (response.data && response.data.data) {
          setEmployee(response.data.data);
          setOriginalEmpId(response.data.data.personal?.empId || "");

          // Initialize documents if they exist
          const employeeDocuments = response.data.data.documents || [];
          const groupedDocuments = groupDocumentsByCompany(employeeDocuments);

          setDocuments(prev => ({
            ...prev,
            uploadedDocuments: employeeDocuments.map(doc => ({
              ...doc,
              type: doc.documentType,
              category: getDocumentCategory(doc.documentType),
              companyIndex: 0
            })),
            companyDocuments: groupedDocuments
          }));

          setFilteredCountries(countryOptions);
          setFilteredNationalities(countryOptions);
        } else {
          throw new Error("Invalid employee data format");
        }

        // Fetch reporting managers
        setIsLoadingManagers(true);
        const managersResponse = await axios.get(`${API_BASE_URL}/reporting-managers`, {
          headers: {
            "Authorization": `Bearer ${token}`
          }
        });
        setReportingManagers(managersResponse.data.data || managersResponse.data.managers || managersResponse.data);
      } catch (err) {
        console.error("Error fetching employee:", err);
        setError(err.response?.data?.message || err.message);
        if (err.response?.status === 401) {
          navigate("/login");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchInitialData();
  }, [id, navigate, countryOptions]);

  // Group existing documents by company
  const groupDocumentsByCompany = (docs) => {
    if (!docs || !Array.isArray(docs)) {
      return [{
        companyName: "Current Company",
        documents: []
      }];
    }

    // Separate previous company documents from others
    const previousCompanyDocs = docs.filter(doc =>
      doc.documentType === "APPOINTMENT_LETTER" ||
      doc.documentType === "RELIEVING_LETTER" ||
      doc.documentType === "EXPERIENCE_LETTER" ||
      doc.documentType === "SALARY_SLIP_LAST_3_MONTHS" ||
      doc.documentType === "OTHER_PREVIOUS_COMPANY_DOC"
    );

    const otherDocs = docs.filter(doc =>
      !previousCompanyDocs.includes(doc)
    );

    // Group previous company documents by company index
    const companiesMap = {};

    previousCompanyDocs.forEach(doc => {
      const companyIndex = doc.companyIndex || 0;
      if (!companiesMap[companyIndex]) {
        companiesMap[companyIndex] = {
          companyName: companyIndex === 0 ? "Current Company" : `Company ${companyIndex + 1}`,
          documents: []
        };
      }
      companiesMap[companyIndex].documents.push({
        ...doc,
        type: doc.documentType,
        category: "Previous Company Documents",
        companyIndex
      });
    });

    // Add other documents to current company (index 0)
    if (!companiesMap[0]) {
      companiesMap[0] = {
        companyName: "Current Company",
        documents: []
      };
    }

    otherDocs.forEach(doc => {
      companiesMap[0].documents.push({
        ...doc,
        type: doc.documentType,
        category: getDocumentCategory(doc.documentType),
        companyIndex: 0
      });
    });

    // Convert to array and sort by company index
    return Object.values(companiesMap).sort((a, b) =>
      a.companyName === "Current Company" ? -1 :
        b.companyName === "Current Company" ? 1 :
          parseInt(a.companyName.split(' ')[1]) - parseInt(b.companyName.split(' ')[1])
    );
  };

  // Helper function to determine document category
  const getDocumentCategory = (documentType) => {
    for (const category of documentCategories) {
      if (category.documents.some(doc => doc.type === documentType)) {
        return category.title;
      }
    }
    return "Other Documents";
  };

  const handleCountrySearch = (event) => {
    const query = event.target.value;
    setCountrySearch(query);
    const filtered = countryOptions.filter((option) =>
      option.label.toLowerCase().includes(query.toLowerCase())
    );
    setFilteredCountries(filtered);
  };

  const handleNationalitySearch = (event) => {
    const query = event.target.value;
    setNationalitySearch(query);
    const filtered = countryOptions.filter((option) =>
      option.label.toLowerCase().includes(query.toLowerCase())
    );
    setFilteredNationalities(filtered);
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    const [section, field] = name.includes('.') ? name.split('.') : ['personal', name];

    // Auto-uppercase empId
    const processedValue = (section === 'personal' && field === 'empId') ? value.toUpperCase() : value;

    setEmployee(prev => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: processedValue
      }
    }));

    // Clear error for the field being edited
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: "" }));
    }
  };

  const validateFields = () => {
    const newErrors = {};

    // Validate employee ID
    if (employee.personal.empId) {
      if (!/^[A-Z0-9]+$/.test(employee.personal.empId)) {
        newErrors["personal.empId"] = "Must contain only uppercase letters and numbers";
      } else if (existingEmpIds.includes(employee.personal.empId.toUpperCase()) &&
        employee.personal.empId.toUpperCase() !== originalEmpId.toUpperCase()) {
        newErrors["personal.empId"] = "Employee ID must be unique";
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = async () => {
    if (!validateFields()) {
      setSnackbar({
        open: true,
        message: "Please fix the errors before saving",
        severity: "error"
      });
      return;
    }

    try {
      const token = localStorage.getItem("token");
      const formData = new FormData();

      // Append employee data as JSON string
      formData.append("employee", new Blob([JSON.stringify(employee)], {
        type: "application/json"
      }));

      const response = await axios.put(
        `${API_BASE_URL}/employees/${id}`,
        formData,
        {
          headers: {
            "Authorization": `Bearer ${token}`,
            // Let the browser set the Content-Type with boundary
            "Content-Type": "multipart/form-data"
          }
        }
      );

      setSnackbar({
        open: true,
        message: "Employee updated successfully!",
        severity: "success"
      });

      setTimeout(() => {
        navigate(`/${role}/employees`);
      }, 2000);
    } catch (error) {
      console.error("Error updating employee:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to update employee",
        severity: "error"
      });
    }
  };

  const handleCloseSnackbar = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
  };

  const getError = (fieldName) => {
    return errors[fieldName] ||
      errors[`personal.${fieldName}`] ||
      errors[`identification.${fieldName}`] ||
      errors[`work.${fieldName}`] ||
      errors[`contact.${fieldName}`] ||
      errors[`report.${fieldName}`];
  };

  // Document related functions
  const handleFileChange = (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const validTypes = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!validTypes.includes(file.type)) {
      setSnackbar({
        open: true,
        message: "Only PDF, JPG, and PNG files are allowed",
        severity: "error"
      });
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      setSnackbar({
        open: true,
        message: "File size must be less than 2MB",
        severity: "error"
      });
      return;
    }

    setDocuments(prev => ({
      ...prev,
      file,
      fileName: file.name
    }));
  };

  const handleUploadDocument = async () => {
    if (!documents.currentCategory) {
      setSnackbar({
        open: true,
        message: "Please select a document category",
        severity: "error"
      });
      return;
    }

    if (!documents.currentDocumentType) {
      setSnackbar({
        open: true,
        message: "Please select a document type",
        severity: "error"
      });
      return;
    }

    if (!documents.file) {
      setSnackbar({
        open: true,
        message: "Please select a file to upload",
        severity: "error"
      });
      return;
    }

    if (documents.currentDocumentType.includes("Other") && !documents.customDocumentName) {
      setSnackbar({
        open: true,
        message: "Please enter document name for 'Other' type",
        severity: "error"
      });
      return;
    }

    try {
      const token = localStorage.getItem("token");
      const formData = new FormData();

      // Include the existing employee data
      formData.append("employee", new Blob([JSON.stringify({
        id: id, // Include the employee ID
        personal: employee.personal,
        identification: employee.identification,
        work: employee.work,
        contact: employee.contact,
        report: employee.report
      })], {
        type: "application/json"
      }));

      // Append the document with the correct key format
      const documentKey = `${documents.currentDocumentType}_COMPANY_${documents.currentCompanyIndex}`;
      formData.append(documentKey, documents.file);

      const response = await axios.put(
        `${API_BASE_URL}/employees/${id}`,
        formData,
        {
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "multipart/form-data"
          }
        }
      );

      if (response.data && response.data.status === "success") {
        setSnackbar({
          open: true,
          message: "Document uploaded successfully!",
          severity: "success"
        });

        // Refresh the employee data to get the new documents
        const updatedResponse = await axios.get(
          `${API_BASE_URL}/employees/${id}`,
          {
            headers: {
              Authorization: `Bearer ${token}`
            }
          }
        );

        if (updatedResponse.data && updatedResponse.data.data) {
          const groupedDocuments = groupDocumentsByCompany(updatedResponse.data.data.documents || []);
          setDocuments(prev => ({
            ...prev,
            uploadedDocuments: updatedResponse.data.data.documents || [],
            companyDocuments: groupedDocuments,
            currentDocumentType: "",
            customDocumentName: "",
            file: null,
            fileName: "",
            fileInputKey: Date.now()
          }));
        }
      }
    } catch (error) {
      console.error("Error uploading document:", error);
      setSnackbar({
        open: true,
        message: error.response?.data?.message || "Failed to upload document",
        severity: "error"
      });
    }
  };

  const handleRemoveDocument = async (docToRemove) => {
  try {
    const token = localStorage.getItem("token");
    
    const response = await axios.delete(
      `${API_BASE_URL}/employees/${id}/documents/${docToRemove.id}`,
      {
        headers: {
          "Authorization": `Bearer ${token}`
        }
      }
    );

    if (response.data && response.data.status === "success") {
      setSnackbar({
        open: true,
        message: "Document deleted successfully!",
        severity: "success"
      });

      // Update state to remove the deleted document
      setDocuments(prev => {
        const updatedCompanyDocuments = [...prev.companyDocuments];
        
        // Find and remove the document from the appropriate company
        updatedCompanyDocuments.forEach(company => {
          company.documents = company.documents.filter(
            doc => doc.id !== docToRemove.id
          );
        });

        // Filter out the document from uploadedDocuments
        const updatedUploadedDocuments = prev.uploadedDocuments.filter(
          doc => doc.id !== docToRemove.id
        );

        return {
          ...prev,
          companyDocuments: updatedCompanyDocuments,
          uploadedDocuments: updatedUploadedDocuments
        };
      });
    }
  } catch (error) {
    console.error("Error deleting document:", error);
    setSnackbar({
      open: true,
      message: error.response?.data?.message || "Failed to delete document",
      severity: "error"
    });
  }
};



  const handleCategoryChange = (category) => {
    setExpandedCategory(expandedCategory === category ? "" : category);
    setDocuments(prev => ({
      ...prev,
      currentCategory: category,
      currentDocumentType: ""
    }));
  };
  

  const handleDocumentTypeSelect = (docType) => {
  const fileInput = document.getElementById('file-upload-input');
  if (fileInput) fileInput.value = '';

  setDocuments(prev => ({
    ...prev,
    currentDocumentType: docType,
    customDocumentName: docType.includes("Other") ? "" : prev.customDocumentName,
    file: null,
    fileName: "",
    fileInputKey: Date.now()
  }));
};

  const addNewCompanyDocumentSet = () => {
    setDocuments(prev => ({
      ...prev,
      currentCompanyIndex: prev.companyDocuments.length,
      companyDocuments: [
        ...prev.companyDocuments,
        {
          companyName: `Company ${prev.companyDocuments.length + 1}`,
          documents: []
        }
      ],
      currentDocumentType: "",
      customDocumentName: "",
      file: null,
      fileName: ""
    }));
  };

  const DocumentPreviewModal = () => {
    if (!currentPreview) return null;

    return (
      <Dialog
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          {currentPreview.name}
          <IconButton
            aria-label="close"
            onClick={() => setPreviewOpen(false)}
            sx={{
              position: 'absolute',
              right: 8,
              top: 8,
              color: (theme) => theme.palette.grey[500],
            }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {currentPreview.type === 'application/pdf' ? (
            <iframe
              src={`${API_BASE_URL}/employees/${id}/documents/${currentPreview.id}`}
              width="100%"
              height="500px"
              title={currentPreview.name}
              style={{ border: 'none' }}
            />
          ) : (
            <img
              src={`${API_BASE_URL}/employees/${id}/documents/${currentPreview.id}`}
              alt={currentPreview.name}
              style={{ maxWidth: '100%', maxHeight: '80vh' }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            component="a"
            href={`${API_BASE_URL}/employees/${id}/documents/${currentPreview.id}`}
            download
          >
            Download
          </Button>
        </DialogActions>
      </Dialog>
    );
  };

  if (loading) {
    return (
      <Box sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '80vh',
        background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)'
      }}>
        <CircularProgress size={80} thickness={4} sx={{ color: '#3f51b5' }} />
      </Box>
    );
  }

  if (error) {
    return (
      <Container maxWidth="md" sx={{
        mt: 3,
        p: 6,
        background: 'linear-gradient(to right, #ffefba, #ffffff)',
        borderRadius: 3,
        boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.15)'
      }}>
        <Typography variant="h6" color="error" gutterBottom>
          {error}
        </Typography>
        <Button
          variant="contained"
          onClick={() => navigate(-1)}
          sx={{
            mt: 2,
            background: 'linear-gradient(45deg, #FE6B8B 30%, #FF8E53 90%)',
            boxShadow: '0 3px 5px 2px rgba(255, 105, 135, .3)',
            color: 'white',
            '&:hover': {
              background: 'linear-gradient(45deg, #FF8E53 30%, #FE6B8B 90%)',
            }
          }}
        >
          Go Back
        </Button>
      </Container>
    );
  }

  if (!employee) {
    return (
      <Container maxWidth="md" sx={{
        mt: 3,
        p: 6,
        background: 'linear-gradient(to right, #ffefba, #ffffff)',
        borderRadius: 3,
        boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.15)'
      }}>
        <Typography variant="h6" gutterBottom>
          Employee not found
        </Typography>
        <Button
          variant="contained"
          onClick={() => navigate(-1)}
          sx={{
            mt: 2,
            background: 'linear-gradient(45deg, #2196F3 30%, #21CBF3 90%)',
            boxShadow: '0 3px 5px 2px rgba(33, 203, 243, .3)',
            color: 'white',
            '&:hover': {
              background: 'linear-gradient(45deg, #21CBF3 30%, #2196F3 90%)',
            }
          }}
        >
          Go Back
        </Button>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{
      mt: 3,
      mb: 6,
      p: { xs: 2, md: 4 },
      borderRadius: 3,
      boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.15)',
      background: 'linear-gradient(to right, #f5f7fa 0%, #e4e8f0 100%)'
    }}>
      {/* Employee Profile Header */}
      <Paper elevation={4} sx={{
        mb: 4,
        p: 3,
        borderRadius: 3,
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white'
      }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={9}>
            <Box sx={{ display: "flex", alignItems: "center" }}>
              <Avatar
                alt={`${employee.personal?.firstName} ${employee.personal?.lastName}`}
                sx={{
                  width: 100,
                  height: 100,
                  mr: 3,
                  fontSize: '2.5rem',
                  background: 'linear-gradient(45deg, #ff9a9e 0%, #fad0c4 99%, #fad0c4 100%)'
                }}
              >
                {employee.personal?.firstName?.charAt(0)}{employee.personal?.lastName?.charAt(0)}
              </Avatar>
              <Box>
                <Typography variant="h3" fontWeight="bold" sx={{ textShadow: '1px 1px 3px rgba(0,0,0,0.2)' }}>
                  {employee.personal?.firstName} {employee.personal?.lastName}
                </Typography>
                <Box sx={{ display: "flex", alignItems: "center", mt: 1 }}>
                  <Chip
                    label={`ID: ${employee.personal?.empId}`}
                    color="primary"
                    variant="outlined"
                    sx={{
                      mr: 1,
                      color: 'white',
                      borderColor: 'white',
                      fontWeight: 'bold'
                    }}
                  />
                  <Chip
                    label={employee.work?.jobTitle || "No job title"}
                    color="secondary"
                    sx={{
                      color: 'white',
                      fontWeight: 'bold'
                    }}
                  />
                </Box>
              </Box>
            </Box>
          </Grid>
          <Grid item xs={12} md={3} sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Button
                variant="contained"
                color="primary"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                sx={{
                  background: 'linear-gradient(45deg, #4facfe 0%, #00f2fe 100%)',
                  boxShadow: '0 3px 5px 2px rgba(0, 242, 254, .3)',
                  '&:hover': {
                    background: 'linear-gradient(45deg, #00f2fe 0%, #4facfe 100%)',
                  }
                }}
              >
                Save Changes
              </Button>
              <Button
                variant="outlined"
                color="inherit"
                startIcon={<CancelIcon />}
                onClick={() => navigate(-1)}
                sx={{
                  color: 'white',
                  borderColor: 'white',
                  '&:hover': {
                    backgroundColor: 'rgba(255,255,255,0.1)',
                    borderColor: 'white'
                  }
                }}
              >
                Cancel
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* Navigation Tabs */}
      <Tabs
        value={activeTab}
        onChange={handleTabChange}
        variant="scrollable"
        scrollButtons="auto"
        sx={{
          mb: 3,
          '& .MuiTabs-indicator': {
            height: 4,
            borderRadius: '4px 4px 0 0',
            background: 'linear-gradient(45deg, #4facfe 0%, #00f2fe 100%)'
          }
        }}
      >
        <Tab
          label="Personal"
          icon={<PersonIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
        <Tab
          label="Identification"
          icon={<FingerprintIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
        <Tab
          label="Work"
          icon={<WorkIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
        <Tab
          label="Contact"
          icon={<ContactMailIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
        <Tab
          label="Reporting"
          icon={<ReportIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
        <Tab
          label="Documents"
          icon={<PictureAsPdfIcon />}
          iconPosition="start"
          sx={{
            minHeight: 60,
            '&.Mui-selected': { color: '#4facfe' }
          }}
        />
      </Tabs>

      {/* Personal Information Tab */}
      {activeTab === 0 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Personal Information"
            avatar={<PersonIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Employment Status</InputLabel>
                  <Select
                    name="personal.employmentStatus"
                    value={employee.personal?.employmentStatus || ""}
                    onChange={handleChange}
                    label="Employment Status"
                    variant="outlined"
                  >
                    <MenuItem value="Full-Time Permanent">Full-Time Permanent</MenuItem>
                    <MenuItem value="Contract">Contract</MenuItem>
                    <MenuItem value="Part-Time">Part-Time</MenuItem>
                    <MenuItem value="Internship">Internship</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Employee ID"
                  name="personal.empId"
                  value={employee.personal?.empId || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                  error={!!getError("personal.empId")}
                  helperText={getError("personal.empId")}
                  inputProps={{
                    style: { textTransform: 'uppercase' }
                  }}
                />
              </Grid>

              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="First Name"
                  name="personal.firstName"
                  value={employee.personal?.firstName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="Middle Name"
                  name="personal.middleName"
                  value={employee.personal?.middleName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="Last Name"
                  name="personal.lastName"
                  value={employee.personal?.lastName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Date of Birth"
                  type="date"
                  name="personal.dateOfBirth"
                  value={employee.personal?.dateOfBirth?.split('T')[0] || ""}
                  onChange={handleChange}
                  InputLabelProps={{ shrink: true }}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Gender</InputLabel>
                  <Select
                    name="personal.gender"
                    value={employee.personal?.gender || ""}
                    onChange={handleChange}
                    label="Gender"
                    variant="outlined"
                  >
                    <MenuItem value="Male">Male</MenuItem>
                    <MenuItem value="Female">Female</MenuItem>
                    <MenuItem value="Other">Other</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Marital Status</InputLabel>
                  <Select
                    name="personal.maritalStatus"
                    value={employee.personal?.maritalStatus || ""}
                    onChange={handleChange}
                    label="Marital Status"
                    variant="outlined"
                  >
                    <MenuItem value="Unmarried">Unmarried</MenuItem>
                    <MenuItem value="Married">Married</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Nationality</InputLabel>
                  <Select
                    name="personal.nationality"
                    value={employee.personal?.nationality || ""}
                    onChange={handleChange}
                    label="Nationality"
                    variant="outlined"
                    MenuProps={{
                      PaperProps: {
                        sx: {
                          maxHeight: 300,
                        },
                      },
                    }}
                  >
                    <MenuItem disabled>
                      <TextField
                        value={nationalitySearch}
                        onChange={handleNationalitySearch}
                        label="Search country"
                        fullWidth
                        variant="standard"
                        sx={{ p: 1 }}
                      />
                    </MenuItem>
                    {filteredNationalities.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Ethnicity</InputLabel>
                  <Select
                    name="personal.ethnicity"
                    value={employee.personal?.ethnicity || ""}
                    onChange={handleChange}
                    label="Ethnicity"
                    variant="outlined"
                  >
                    <MenuItem value="Asian">Asian</MenuItem>
                    <MenuItem value="Hispanic">Hispanic</MenuItem>
                    <MenuItem value="African">African</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Identification Information Tab */}
      {activeTab === 1 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Identification Information"
            avatar={<FingerprintIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Immigration Status</InputLabel>
                  <Select
                    name="identification.immigrationStatus"
                    value={employee.identification?.immigrationStatus || ""}
                    onChange={handleChange}
                    label="Immigration Status"
                    variant="outlined"
                  >
                    <MenuItem value="Citizen">Citizen</MenuItem>
                    <MenuItem value="Permanent Resident">Permanent Resident</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Aadhar Card Number"
                  name="identification.aadharCardNumber"
                  value={employee.identification?.aadharCardNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Pan Card Number"
                  name="identification.panCardNumber"
                  value={employee.identification?.panCardNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Address Proof</InputLabel>
                  <Select
                    name="identification.addressProof"
                    value={employee.identification?.addressProof || ""}
                    onChange={handleChange}
                    label="Address Proof"
                    variant="outlined"
                  >
                    <MenuItem value="Driving Licence">Driving Licence</MenuItem>
                    <MenuItem value="Passport">Passport</MenuItem>
                    <MenuItem value="Other">Other</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              {employee.identification?.addressProof && employee.identification?.addressProof !== "Other" && (
                <Grid item xs={12} md={6}>
                  <TextField
                    fullWidth
                    label="Address Document Number"
                    name="identification.addressDocumentNumber"
                    value={employee.identification?.addressDocumentNumber || ""}
                    onChange={handleChange}
                    variant="outlined"
                    sx={{ mb: 2 }}
                  />
                </Grid>
              )}
              {employee.identification?.addressProof === "Other" && (
                <>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Address Document Name"
                      name="identification.addressDocumentName"
                      value={employee.identification?.addressDocumentName || ""}
                      onChange={handleChange}
                      variant="outlined"
                      sx={{ mb: 2 }}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Address Document Number"
                      name="identification.addressDocumentNumber"
                      value={employee.identification?.addressDocumentNumber || ""}
                      onChange={handleChange}
                      variant="outlined"
                      sx={{ mb: 2 }}
                    />
                  </Grid>
                </>
              )}

              <Grid item xs={12} md={6}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>ID Proof</InputLabel>
                  <Select
                    name="identification.idProof"
                    value={employee.identification?.idProof || ""}
                    onChange={handleChange}
                    label="ID Proof"
                    variant="outlined"
                  >
                    <MenuItem value="Aadhar Card">Aadhar Card</MenuItem>
                    <MenuItem value="Pan Card">Pan Card</MenuItem>
                    <MenuItem value="Driving Licence">Driving Licence</MenuItem>
                    <MenuItem value="Passport">Passport</MenuItem>
                    <MenuItem value="Other">Other</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              {employee.identification?.idProof && employee.identification?.idProof !== "Other" && (
                <Grid item xs={12} md={6}>
                  <TextField
                    fullWidth
                    label="Document Number"
                    name="identification.documentNumber"
                    value={employee.identification?.documentNumber || ""}
                    onChange={handleChange}
                    variant="outlined"
                    sx={{ mb: 2 }}
                  />
                </Grid>
              )}
              {employee.identification?.idProof === "Other" && (
                <>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Document Name"
                      name="identification.documentName"
                      value={employee.identification?.documentName || ""}
                      onChange={handleChange}
                      variant="outlined"
                      sx={{ mb: 2 }}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Document Number"
                      name="identification.documentNumber"
                      value={employee.identification?.documentNumber || ""}
                      onChange={handleChange}
                      variant="outlined"
                      sx={{ mb: 2 }}
                    />
                  </Grid>
                </>
              )}
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Work Information Tab */}
      {activeTab === 2 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Work Information"
            avatar={<WorkIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Department"
                  name="work.department"
                  value={employee.work?.department || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Job Title"
                  name="work.jobTitle"
                  value={employee.work?.jobTitle || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Pay Grade"
                  name="work.payGrade"
                  value={employee.work?.payGrade || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Date of Joining"
                  type="date"
                  name="work.doj"
                  value={employee.work?.doj?.split('T')[0] || ""}
                  onChange={handleChange}
                  InputLabelProps={{ shrink: true }}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Termination Date"
                  type="date"
                  name="work.terminationDate"
                  value={employee.work?.terminationDate?.split('T')[0] || ""}
                  onChange={handleChange}
                  InputLabelProps={{ shrink: true }}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Workstation ID"
                  name="work.workstationId"
                  value={employee.work?.workstationId || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <Autocomplete
                  fullWidth
                  options={timeZones}
                  value={employee.work?.timeZone || ""}
                  onChange={(event, newValue) => {
                    setEmployee(prev => ({
                      ...prev,
                      work: {
                        ...prev.work,
                        timeZone: newValue
                      }
                    }));
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Time Zone"
                      variant="outlined"
                      sx={{ mb: 2 }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Shift Start Time"
                  type="time"
                  name="work.shiftStartTime"
                  value={employee.work?.shiftStartTime || ""}
                  onChange={handleChange}
                  InputLabelProps={{ shrink: true }}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Shift End Time"
                  type="time"
                  name="work.shiftEndTime"
                  value={employee.work?.shiftEndTime || ""}
                  onChange={handleChange}
                  InputLabelProps={{ shrink: true }}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Contact Information Tab */}
      {activeTab === 3 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Contact Information"
            avatar={<ContactMailIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12}>
                <Typography variant="h6" gutterBottom sx={{ color: '#3f51b5', mt: 1 }}>
                  Address Information
                </Typography>
                <Divider sx={{ mb: 2 }} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Residential Address"
                  multiline
                  rows={2}
                  name="contact.residentialAddress"
                  value={employee.contact?.residentialAddress || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Permanent Address"
                  multiline
                  rows={2}
                  name="contact.permanentAddress"
                  value={employee.contact?.permanentAddress || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="City"
                  name="contact.city"
                  value={employee.contact?.city || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="State"
                  name="contact.state"
                  value={employee.contact?.state || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Country</InputLabel>
                  <Select
                    name="contact.country"
                    value={employee.contact?.country || ""}
                    onChange={handleChange}
                    label="Country"
                    variant="outlined"
                    MenuProps={{
                      PaperProps: {
                        sx: {
                          maxHeight: 300,
                        },
                      },
                    }}
                  >
                    <MenuItem disabled>
                      <TextField
                        value={countrySearch}
                        onChange={handleCountrySearch}
                        label="Search country"
                        fullWidth
                        variant="standard"
                        sx={{ p: 1 }}
                      />
                    </MenuItem>
                    {filteredCountries.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Postal Code"
                  name="contact.postalCode"
                  value={employee.contact?.postalCode || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>

              <Grid item xs={12}>
                <Typography variant="h6" gutterBottom sx={{ color: '#3f51b5', mt: 1 }}>
                  Contact Details
                </Typography>
                <Divider sx={{ mb: 2 }} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Work Email"
                  type="email"
                  name="contact.workEmail"
                  value={employee.contact?.workEmail || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Personal Email"
                  type="email"
                  name="contact.personalEmail"
                  value={employee.contact?.personalEmail || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Mobile Number"
                  name="contact.mobileNumber"
                  value={employee.contact?.mobileNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>

              <Grid item xs={12}>
                <Typography variant="h6" gutterBottom sx={{ color: '#3f51b5', mt: 1 }}>
                  Emergency Contacts
                </Typography>
                <Divider sx={{ mb: 2 }} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Primary Emergency Contact Name"
                  name="contact.primaryEmergencyContactName"
                  value={employee.contact?.primaryEmergencyContactName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Primary Emergency Contact Number"
                  name="contact.primaryEmergencyContactNumber"
                  value={employee.contact?.primaryEmergencyContactNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Relationship to Primary Emergency Contact"
                  name="contact.relationshipToPrimaryEmergencyContact"
                  value={employee.contact?.relationshipToPrimaryEmergencyContact || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Secondary Emergency Contact Name"
                  name="contact.secondaryEmergencyContactName"
                  value={employee.contact?.secondaryEmergencyContactName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Secondary Emergency Contact Number"
                  name="contact.secondaryEmergencyContactNumber"
                  value={employee.contact?.secondaryEmergencyContactNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Relationship to Secondary Emergency Contact"
                  name="contact.relationshipToSecondaryEmergencyContact"
                  value={employee.contact?.relationshipToSecondaryEmergencyContact || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>

              <Grid item xs={12}>
                <Typography variant="h6" gutterBottom sx={{ color: '#3f51b5', mt: 1 }}>
                  Medical Information
                </Typography>
                <Divider sx={{ mb: 2 }} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Family Doctor Name"
                  name="contact.familyDoctorName"
                  value={employee.contact?.familyDoctorName || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Family Doctor Contact Number"
                  name="contact.familyDoctorContactNumber"
                  value={employee.contact?.familyDoctorContactNumber || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Reporting Information Tab */}
      {activeTab === 4 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Reporting Information"
            avatar={<ReportIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Autocomplete
                  fullWidth
                  options={reportingManagers}
                  getOptionLabel={(option) =>
                    option && typeof option === "object"
                      ? `${option.empId} - ${option.name}`
                      : ""
                  }
                  value={
                    reportingManagers.find(
                      (m) => m.empId === employee.report?.reportingManagerId
                    ) || null
                  }
                  onChange={(event, newValue) => {
                    setEmployee(prev => ({
                      ...prev,
                      report: {
                        ...prev.report,
                        reportingManagerId: newValue?.empId || "",
                        reportingManagerName: newValue?.name || ""
                      }
                    }));
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Reporting Manager"
                      variant="outlined"
                      fullWidth
                      sx={{ mb: 2 }}
                    />
                  )}
                  renderOption={(props, option) => (
                    <li {...props}>
                      <Box sx={{ display: "flex", flexDirection: "column" }}>
                        <Typography>{option.empId}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {option.name}
                        </Typography>
                      </Box>
                    </li>
                  )}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Indirect Manager"
                  name="report.indirectManager"
                  value={employee.report?.indirectManager || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="First Level Approver"
                  name="report.firstLevelApprover"
                  value={employee.report?.firstLevelApprover || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Second Level Approver"
                  name="report.secondLevelApprover"
                  value={employee.report?.secondLevelApprover || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Third Level Approver"
                  name="report.thirdLevelApprover"
                  value={employee.report?.thirdLevelApprover || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Notes"
                  multiline
                  rows={4}
                  name="report.note"
                  value={employee.report?.note || ""}
                  onChange={handleChange}
                  variant="outlined"
                  sx={{ mb: 2 }}
                />
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* Documents Information Tab */}
      {activeTab === 5 && (
        <Card sx={{ mb: 4, borderRadius: 3, boxShadow: 3 }}>
          <CardHeader
            title="Employee Documents"
            avatar={<PictureAsPdfIcon color="primary" />}
            sx={{
              background: 'linear-gradient(45deg, #f5f7fa 0%, #c3cfe2 100%)',
              borderBottom: '1px solid #e0e0e0'
            }}
          />
          <CardContent>
            <Box sx={{ p: 2 }}>
              <Typography variant="h5" gutterBottom sx={{ mb: 3 }}>
                Document Upload
              </Typography>
              <Typography variant="body1" color="text.secondary" gutterBottom sx={{ mb: 3 }}>
                Please upload all required documents (PDF, JPG, PNG only, max 2MB each)
              </Typography>

              <Box sx={{ mb: 4 }}>
                {documentCategories.map((category) => (
                  <Accordion
                    key={category.title}
                    expanded={expandedCategory === category.title}
                    onChange={() => handleCategoryChange(category.title)}
                    sx={{
                      mb: 2,
                      border: '1px solid',
                      borderColor: expandedCategory === category.title ? 'primary.main' : 'divider',
                      boxShadow: expandedCategory === category.title ? '0px 2px 8px rgba(0,0,0,0.1)' : 'none'
                    }}
                  >
                    <AccordionSummary
                      expandIcon={<ExpandMoreIcon />}
                      sx={{
                        backgroundColor: expandedCategory === category.title ? '#f5f9ff' : 'inherit',
                        borderBottom: expandedCategory === category.title ? '1px solid' : 'none',
                        borderColor: 'divider'
                      }}
                    >
                      <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                        <Typography variant="subtitle1" sx={{ flexGrow: 1, fontWeight: 'bold' }}>
                          {category.title}
                        </Typography>
                        <Badge
                          badgeContent={documents.companyDocuments.reduce(
                            (total, company) => total + company.documents.filter(doc => doc.category === category.title).length, 0
                          )}
                          color="primary"
                          sx={{ mr: 2 }}
                        />
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails sx={{ pt: 2 }}>
                      {category.title === "Previous Company Documents" ? (
                        // Render with company-specific UI for Previous Company Documents
                        documents.companyDocuments.map((company, companyIndex) => (
                          <Box key={companyIndex} sx={{ mb: 4, p: 2, border: '1px solid #eee', borderRadius: 1 }}>
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                                {company.companyName}
                              </Typography>
                              {companyIndex > 0 && (
                                <Button
                                  size="small"
                                  color="error"
                                  onClick={() => {
                                    setDocuments(prev => {
                                      const updatedCompanyDocuments = [...prev.companyDocuments];
                                      updatedCompanyDocuments.splice(companyIndex, 1);

                                      const updatedUploadedDocuments = prev.uploadedDocuments.filter(
                                        doc => !(doc.companyIndex === companyIndex && doc.category === category.title)
                                      );

                                      const adjustedUploadedDocuments = updatedUploadedDocuments.map(doc => {
                                        if (doc.companyIndex > companyIndex) {
                                          return { ...doc, companyIndex: doc.companyIndex - 1 };
                                        }
                                        return doc;
                                      });

                                      return {
                                        ...prev,
                                        companyDocuments: updatedCompanyDocuments,
                                        uploadedDocuments: adjustedUploadedDocuments,
                                        currentCompanyIndex: Math.min(
                                          prev.currentCompanyIndex,
                                          updatedCompanyDocuments.length - 1
                                        )
                                      };
                                    });
                                  }}
                                >
                                  Remove Company
                                </Button>
                              )}
                            </Box>

                            {/* Document selection and upload UI */}
                            <Box sx={{ mb: 3 }}>
                              <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold', mb: 2 }}>
                                Select Document Type:
                              </Typography>

                              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                                {category.documents.map((doc) => (
                                  <Chip
                                    key={doc.type}
                                    label={`${doc.type}${doc.mandatory ? ' *' : ''}`}
                                    onClick={() => handleDocumentTypeSelect(doc.type)}
                                    color={
                                      documents.currentDocumentType === doc.type &&
                                        documents.currentCompanyIndex === companyIndex
                                        ? "primary"
                                        : company.documents.some(d => d.type === doc.type && d.category === category.title)
                                          ? "success"
                                          : "default"
                                    }
                                    variant={
                                      documents.currentDocumentType === doc.type &&
                                        documents.currentCompanyIndex === companyIndex
                                        ? "filled"
                                        : "outlined"
                                    }
                                  />
                                ))}
                              </Box>
                            </Box>

                            {documents.currentCategory === category.title &&
                              documents.currentCompanyIndex === companyIndex && (
                                <>
                                  {documents.currentDocumentType && (
                                    <>
                                      <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                        Upload {documents.currentDocumentType} for {company.companyName}
                                      </Typography>

                                      {documents.currentDocumentType.includes("Other") && (
                                        <TextField
                                          fullWidth
                                          margin="normal"
                                          label="Document Name"
                                          name="customDocumentName"
                                          value={documents.customDocumentName}
                                          onChange={(e) => setDocuments(prev => ({
                                            ...prev,
                                            customDocumentName: e.target.value
                                          }))}
                                          required
                                          sx={{ mb: 2 }}
                                        />
                                      )}

                                      <Box sx={{ mt: 2 }}>
                                        <input
                                          accept=".pdf,.jpg,.jpeg,.png"
                                          style={{ display: 'none' }}
                                          id="file-upload-input"
                                          key={documents.fileInputKey}
                                          type="file"
                                          onChange={handleFileChange}
                                        />
                                        <label htmlFor="file-upload-input">
                                          <Button
                                            variant="contained"
                                            component="span"
                                            startIcon={<CloudUploadIcon />}
                                            fullWidth
                                            sx={{ mb: 2 }}
                                          >
                                            Choose File
                                          </Button>
                                        </label>
                                        {documents.fileName && (
                                          <Typography variant="body2" sx={{ mb: 2 }}>
                                            Selected file: {documents.fileName}
                                          </Typography>
                                        )}
                                      </Box>

                                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                                        <Button
                                          variant="contained"
                                          color="primary"
                                          onClick={handleUploadDocument}
                                          disabled={!documents.file ||
                                            (documents.currentDocumentType.includes("Other") &&
                                              !documents.customDocumentName)}
                                        >
                                          Upload
                                        </Button>
                                        <Button
                                          variant="outlined"
                                          onClick={() => {
                                            setDocuments(prev => ({
                                              ...prev,
                                              currentDocumentType: "",
                                              customDocumentName: "",
                                              file: null,
                                              fileName: ""
                                            }));
                                          }}
                                        >
                                          Cancel
                                        </Button>
                                      </Box>
                                    </>
                                  )}
                                </>
                              )}

                            {company.documents.filter(doc => doc.category === category.title).length > 0 && (
                              <>
                                <Divider sx={{ my: 3 }} />
                                <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                  Uploaded Documents for {company.companyName}
                                </Typography>
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>Type</TableCell>
                                      <TableCell>File</TableCell>
                                      <TableCell>Actions</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {company.documents
                                      .filter(doc => doc.category === category.title)
                                      .map((doc, index) => (
                                        <TableRow key={index}>
                                          <TableCell>{doc.documentType}</TableCell>
                                          <TableCell>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                              {doc.fileName && doc.fileName.endsWith('.pdf') ? (
                                                <PictureAsPdfIcon color="error" />
                                              ) : (
                                                <ImageIcon color="primary" />
                                              )}
                                              {doc.fileName}
                                            </Box>
                                          </TableCell>
                                          <TableCell>
                                            <IconButton
                                              color="error"
                                              onClick={() => handleRemoveDocument(doc)}
                                            >
                                              <DeleteIcon />
                                            </IconButton>
                                          </TableCell>
                                        </TableRow>
                                      ))}
                                  </TableBody>
                                </Table>
                              </>
                            )}
                          </Box>
                        ))
                      ) : (
                        // Render standard UI for other categories (single company)
                        <Box sx={{ mb: 4, p: 2, border: '1px solid #eee', borderRadius: 1 }}>
                          {/* Standard document upload UI for non-company categories */}
                          <Box sx={{ mb: 3 }}>
                            <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold', mb: 2 }}>
                              Select Document Type:
                            </Typography>

                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                              {category.documents.map((doc) => (
                                <Chip
                                  key={doc.type}
                                  label={`${doc.type}${doc.mandatory ? ' *' : ''}`}
                                  onClick={() => handleDocumentTypeSelect(doc.type)}
                                  color={
                                    documents.currentDocumentType === doc.type &&
                                      documents.currentCategory === category.title
                                      ? "primary"
                                      : documents.companyDocuments[0].documents.some(d => d.type === doc.type && d.category === category.title)
                                        ? "success"
                                        : "default"
                                  }
                                  variant={
                                    documents.currentDocumentType === doc.type &&
                                      documents.currentCategory === category.title
                                      ? "filled"
                                      : "outlined"
                                  }
                                />
                              ))}
                            </Box>
                          </Box>

                          {documents.currentCategory === category.title && (
                            <>
                              {documents.currentDocumentType && (
                                <>
                                  <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                    Upload {documents.currentDocumentType}
                                  </Typography>

                                  {documents.currentDocumentType.includes("Other") && (
                                    <TextField
                                      fullWidth
                                      margin="normal"
                                      label="Document Name"
                                      name="customDocumentName"
                                      value={documents.customDocumentName}
                                      onChange={(e) => setDocuments(prev => ({
                                        ...prev,
                                        customDocumentName: e.target.value
                                      }))}
                                      required
                                      sx={{ mb: 2 }}
                                    />
                                  )}

                                  <Box sx={{ mt: 2 }}>
                                    <input
                                      accept=".pdf,.jpg,.jpeg,.png"
                                      style={{ display: 'none' }}
                                      id="file-upload-input"
                                      key={documents.fileInputKey}
                                      type="file"
                                      onChange={handleFileChange}
                                    />
                                    <label htmlFor="file-upload-input">
                                      <Button
                                        variant="contained"
                                        component="span"
                                        startIcon={<CloudUploadIcon />}
                                        fullWidth
                                        sx={{ mb: 2 }}
                                      >
                                        Choose File
                                      </Button>
                                    </label>
                                    {documents.fileName && (
                                      <Typography variant="body2" sx={{ mb: 2 }}>
                                        Selected file: {documents.fileName}
                                      </Typography>
                                    )}
                                  </Box>

                                  <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                                    <Button
                                      variant="contained"
                                      color="primary"
                                      onClick={handleUploadDocument}
                                      disabled={!documents.file ||
                                        (documents.currentDocumentType.includes("Other") &&
                                          !documents.customDocumentName)}
                                    >
                                      Upload
                                    </Button>
                                    <Button
                                      variant="outlined"
                                      onClick={() => {
                                        setDocuments(prev => ({
                                          ...prev,
                                          currentDocumentType: "",
                                          customDocumentName: "",
                                          file: null,
                                          fileName: ""
                                        }));
                                      }}
                                    >
                                      Cancel
                                    </Button>
                                  </Box>
                                </>
                              )}
                            </>
                          )}

                          {documents.companyDocuments[0].documents.filter(doc => doc.category === category.title).length > 0 && (
                            <>
                              <Divider sx={{ my: 3 }} />
                              <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                Uploaded Documents
                              </Typography>
                              <Table size="small">
                                <TableHead>
                                  <TableRow>
                                    <TableCell>Type</TableCell>
                                    <TableCell>File</TableCell>
                                    <TableCell>Actions</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {documents.companyDocuments[0].documents
                                    .filter(doc => doc.category === category.title)
                                    .map((doc, index) => (
                                      <TableRow key={index}>
                                        <TableCell>{doc.documentType}</TableCell>
                                        <TableCell>
                                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            {doc.fileName && doc.fileName.endsWith('.pdf') ? (
                                              <PictureAsPdfIcon color="error" />
                                            ) : (
                                              <ImageIcon color="primary" />
                                            )}
                                            {doc.fileName}
                                          </Box>
                                        </TableCell>
                                        <TableCell>
                                          {/* <IconButton
                                            color="primary"
                                            onClick={() => handleViewDocument(doc)}
                                            sx={{ mr: 1 }}
                                          >
                                            <VisibilityIcon />
                                          </IconButton> */}
                                          <IconButton
                                            color="error"
                                            onClick={() => handleRemoveDocument(doc)}
                                          >
                                            <DeleteIcon />
                                          </IconButton>
                                        </TableCell>
                                      </TableRow>
                                    ))}
                                </TableBody>
                              </Table>
                            </>
                          )}
                        </Box>
                      )}

                      {category.title === "Previous Company Documents" && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
                          <Button
                            variant="outlined"
                            startIcon={<AddIcon />}
                            onClick={addNewCompanyDocumentSet}
                          >
                            Add Another Company
                          </Button>
                        </Box>
                      )}
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Box>

              <Box sx={{ mt: 4, p: 3, backgroundColor: '#f8f9fa', borderRadius: 2 }}>
                <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>
                  Document Upload Summary
                </Typography>

                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                  {documentCategories.map(category => (
                    <Box
                      key={category.title}
                      sx={{
                        p: 2,
                        border: '1px solid',
                        borderColor: 'divider',
                        borderRadius: 1,
                        minWidth: '200px',
                        backgroundColor: 'white'
                      }}
                    >
                      <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
                        {category.title}
                        {category.allowMultipleSets && documents.companyDocuments.length > 1 && (
                          <Typography variant="caption" color="text.secondary" display="block">
                            ({documents.companyDocuments.length} companies)
                          </Typography>
                        )}
                      </Typography>
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        {category.documents.map(doc => {
                          const count = documents.companyDocuments.reduce(
                            (total, company) => total +
                              company.documents.filter(d => d.type === doc.type && d.category === category.title).length,
                            0
                          );
                          return (
                            <Box key={doc.type} sx={{ display: 'flex', justifyContent: 'space-between' }}>
                              <Typography variant="body2">
                                {doc.type}{doc.mandatory ? ' *' : ''}
                              </Typography>

                            </Box>
                          );
                        })}
                      </Box>
                    </Box>
                  ))}
                </Box>

                <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
                  * indicates mandatory documents
                </Typography>
              </Box>
            </Box>
          </CardContent>
        </Card>
      )}

      {/* Snackbar for notifications */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{
            width: "100%",
            boxShadow: '0 4px 20px 0 rgba(0,0,0,0.12)',
            borderRadius: '8px'
          }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
      <DocumentPreviewModal />
    </Container>
  );
};

export default EditEmployee;