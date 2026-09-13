import React, { useState, useEffect } from "react";
import { getNames } from "country-list";
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import {
  Autocomplete,
  Container,
  TextField,
  MenuItem,
  Button,
  Stepper,
  Step,
  StepLabel,
  Box,
  Typography,
  Select,
  InputLabel,
  FormControl,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Snackbar,
  Alert,
  CircularProgress,
  Chip,
  Link,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Divider,
  Badge
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import ImageIcon from '@mui/icons-material/Image';
import VisibilityIcon from '@mui/icons-material/Visibility';
import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import AddIcon from '@mui/icons-material/Add';

const steps = ["Personal", "Identification", "Work", "Contact", "Documents", "Report"];

const Employee = () => {
  const countryOptions = getNames().map((country) => ({
    label: country,
    value: country,
  }));

  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();

  const [reportingManagers, setReportingManagers] = useState([]);
  const [isLoadingManagers, setIsLoadingManagers] = useState(false);

  const navigate = useNavigate();

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
      allowMultipleCompanies: true
    },
    {
      title: "Passport Documents",
      documents: [
        { type: "PASSPORT", mandatory: false }
      ],
      allowMultipleCompanies: false // Add this line

    }
  ];

  const [notes, setNotes] = useState([]);
  const [isNoteFormVisible, setIsNoteFormVisible] = useState(false);
  const [search, setSearch] = useState("");
  const [filteredOptions, setFilteredOptions] = useState(countryOptions);
  const [activeStep, setActiveStep] = useState(0);
  const [existingEmpIds, setExistingEmpIds] = useState([]);
  const [formData, setFormData] = useState({
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
      fileInputKey: Date.now()
    },
    identification: {
      immigrationStatus: "",
      aadharCardNumber: "",
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
    documents: {
      currentCategory: "",
      currentDocumentType: "",
      customDocumentName: "",
      file: null,
      fileName: "",
      uploadedDocuments: [],
      currentCompanyIndex: 0,
      companyDocuments: [
        {
          // companyName: "Current Company",
          documents: []
        }
      ]
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

  const [errors, setErrors] = useState({});
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState("success");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [currentPreview, setCurrentPreview] = useState(null);
  const [expandedCategory, setExpandedCategory] = useState("");

  const token = localStorage.getItem("token");

  useEffect(() => {
    const fetchInitialData = async () => {
      try {
        const idsResponse = await fetch(`${API_BASE_URL}/employees/ids`, {
          headers: {
            "Authorization": `Bearer ${token}`
          }
        });

        if (idsResponse.ok) {
          const idsData = await idsResponse.json();
          setExistingEmpIds(idsData.data.map(id => id.toUpperCase()));
        }
      } catch (error) {
        console.error("Failed to fetch initial data:", error);
      }
    };

    fetchInitialData();
  }, [token]);

  useEffect(() => {
    const fetchReportingManagers = async () => {
      setIsLoadingManagers(true);
      try {
        const response = await fetch(`${API_BASE_URL}/reporting-managers`, {
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
          }
        });

        const data = await response.json();

        // Handle different response structures
        const managers = data.data || data.managers || data;

        if (!Array.isArray(managers)) {
          console.error("Managers data is not an array:", managers);
          return;
        }

        setReportingManagers(managers);
      } catch (error) {
        console.error("Failed to fetch managers:", error);
      } finally {
        setIsLoadingManagers(false);
      }
    };

    fetchReportingManagers();
  }, [token]);

  const handleNext = () => {
    const requiredFields = getRequiredFieldsForStep(activeStep);
    const newErrors = validateFields(requiredFields);
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      showSnackbar("Please fill all required fields correctly.", "error");
    } else {
      setActiveStep((prevStep) => prevStep + 1);
      setErrors({});
    }
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    const [section, field] = name.includes('.') ? name.split('.') : [activeStep === 0 ? 'personal' :
      activeStep === 1 ? 'identification' :
        activeStep === 2 ? 'work' :
          activeStep === 3 ? 'contact' :
            activeStep === 4 ? 'documents' : 'report', name];

    const processedValue = (section === 'personal' && field === 'empId') ? value.toUpperCase() : value;

    setFormData(prev => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: processedValue
      }
    }));

    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: "" }));
    }
  };

  const handleFileChange = (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const validTypes = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!validTypes.includes(file.type)) {
      showSnackbar("Only PDF, JPG, and PNG files are allowed", "error");
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      showSnackbar("File size must be less than 2MB", "error");
      return;
    }

    setFormData(prev => ({
      ...prev,
      documents: {
        ...prev.documents,
        file,
        fileName: file.name
      }
    }));
  };

  const handleUploadDocument = () => {
    if (!formData.documents.currentCategory) {
      showSnackbar("Please select a document category", "error");
      return;
    }

    if (!formData.documents.currentDocumentType) {
      showSnackbar("Please select a document type", "error");
      return;
    }

    if (!formData.documents.file) {
      showSnackbar("Please select a file to upload", "error");
      return;
    }

    if (formData.documents.currentDocumentType.includes("Other") && !formData.documents.customDocumentName) {
      showSnackbar("Please enter document name for 'Other' type", "error");
      return;
    }

    const documentName = formData.documents.currentDocumentType.includes("Other")
      ? formData.documents.customDocumentName
      : formData.documents.currentDocumentType;

    const newDocument = {
      category: formData.documents.currentCategory,
      type: formData.documents.currentDocumentType,
      name: documentName,
      fileName: formData.documents.fileName,
      file: formData.documents.file,
      uploadedAt: new Date().toLocaleString(),
      companyIndex: formData.documents.currentCompanyIndex
    };

    // Reset file input after upload
    const fileInput = document.getElementById('file-upload-input');
    if (fileInput) fileInput.value = '';

    setFormData(prev => {
      const updatedCompanyDocuments = [...prev.documents.companyDocuments];
      if (!updatedCompanyDocuments[prev.documents.currentCompanyIndex]) {
        updatedCompanyDocuments[prev.documents.currentCompanyIndex] = {
          companyName: `Company ${prev.documents.currentCompanyIndex + 1}`,
          documents: []
        };
      }

      // Only add to the current company's documents
      updatedCompanyDocuments[prev.documents.currentCompanyIndex].documents = [
        ...updatedCompanyDocuments[prev.documents.currentCompanyIndex].documents,
        newDocument
      ];

      return {
        ...prev,
        documents: {
          ...prev.documents,
          uploadedDocuments: [...prev.documents.uploadedDocuments, newDocument],
          companyDocuments: updatedCompanyDocuments,
          currentDocumentType: "",
          customDocumentName: "",
          file: null,
          fileName: "",
          fileInputKey: Date.now()
        }
      };
    });

    showSnackbar("Document uploaded successfully", "success");
  };

  const handleRemoveDocument = (docToRemove) => {
    setFormData(prev => {
      // Remove from uploadedDocuments
      const updatedUploadedDocuments = prev.documents.uploadedDocuments.filter(
        doc => !(doc.category === docToRemove.category &&
          doc.type === docToRemove.type &&
          doc.fileName === docToRemove.fileName &&
          doc.companyIndex === docToRemove.companyIndex)
      );

      // Remove from companyDocuments only for the specific category
      const updatedCompanyDocuments = [...prev.documents.companyDocuments];
      if (docToRemove.companyIndex !== undefined && updatedCompanyDocuments[docToRemove.companyIndex]) {
        updatedCompanyDocuments[docToRemove.companyIndex].documents =
          updatedCompanyDocuments[docToRemove.companyIndex].documents.filter(
            doc => !(doc.category === docToRemove.category &&
              doc.type === docToRemove.type &&
              doc.fileName === docToRemove.fileName)
          );
      }

      return {
        ...prev,
        documents: {
          ...prev.documents,
          uploadedDocuments: updatedUploadedDocuments,
          companyDocuments: updatedCompanyDocuments
        }
      };
    });
  };

  const handleViewDocument = (doc) => {
    if (!doc.file) {
      showSnackbar("No file available for preview", "error");
      return;
    }

    setCurrentPreview({
      type: doc.file.type,
      name: doc.name || doc.fileName,
      file: doc.file
    });
    setPreviewOpen(true);
  };

  const handleSearchChange = (event) => {
    const query = event.target.value;
    setSearch(query);
    const filtered = countryOptions.filter((option) =>
      option.label.toLowerCase().includes(query.toLowerCase())
    );
    setFilteredOptions(filtered);
  };

  const handleAddNote = () => {
    if (formData.report.note) {
      setNotes(prev => [...prev, formData.report.note]);
      setFormData(prev => ({
        ...prev,
        report: { ...prev.report, note: '' }
      }));
      setIsNoteFormVisible(false);
    }
  };

  const handleDeleteNote = (index) => {
    const updatedNotes = notes.filter((_, i) => i !== index);
    setNotes(updatedNotes);
  };

  const handleEditNote = (index) => {
    setFormData(prev => ({
      ...prev,
      report: { ...prev.report, note: notes[index] }
    }));
    setIsNoteFormVisible(true);
    handleDeleteNote(index);
  };

  const addNewCompanyDocumentSet = () => {
    setFormData(prev => ({
      ...prev,
      documents: {
        ...prev.documents,
        currentCompanyIndex: prev.documents.companyDocuments.length,
        companyDocuments: [
          ...prev.documents.companyDocuments,
          {
            companyName: `Company ${prev.documents.companyDocuments.length + 1}`,
            documents: []
          }
        ],
        currentDocumentType: "",
        customDocumentName: "",
        file: null,
        fileName: ""
      }
    }));
  };

  const showSnackbar = (message, severity = "success") => {
    setSnackbarMessage(message);
    setSnackbarSeverity(severity);
    setSnackbarOpen(true);
  };

  const validateFields = (fields) => {
    const newErrors = {};
    fields.forEach(field => {
      const [section, fieldName] = field.includes('.') ? field.split('.') :
        [activeStep === 0 ? 'personal' :
          activeStep === 1 ? 'identification' :
            activeStep === 2 ? 'work' :
              activeStep === 3 ? 'contact' :
                activeStep === 4 ? 'documents' : 'report', field];

      const value = formData[section][fieldName];

      const optionalFields = [
        'middleName', 'personalEmail', 'secondaryEmergencyContactNumber',
        'familyDoctorContactNumber', 'personalTaxId', 'socialInsurance',
        'documentName', 'permanentAddress', 'ethnicity', 'payGrade',
        'workstationId', 'terminationDate', 'shiftStartTime', 'shiftEndTime',
        'indirectManager', 'firstLevelApprover', 'secondLevelApprover', 'thirdLevelApprover',
        'addressDocumentName', 'customDocumentName'
      ];

      if (!value && !optionalFields.includes(fieldName)) {
        newErrors[field] = "This field is required.";
        return;
      }

      switch (fieldName) {
        case "mobileNumber":
        case "primaryEmergencyContactNumber":
          if (!/^\d{10,15}$/.test(value)) {
            newErrors[field] = "Must be 10-15 digits";
          }
          break;
        case "secondaryEmergencyContactNumber":
        case "familyDoctorContactNumber":
          if (value && !/^\d{10,15}$/.test(value)) {
            newErrors[field] = "Must be 10-15 digits if provided";
          }
          break;
        case "workEmail":
        case "personalEmail":
          if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            newErrors[field] = "Invalid email format";
          }
          break;
        case "postalCode":
          if (!/^[a-zA-Z0-9\s-]{3,10}$/.test(value)) {
            newErrors[field] = "Invalid postal code";
          }
          break;
        case "documentNumber":
          validateDocumentNumber(newErrors, field, value, formData.identification.idProof);
          break;
        case "addressDocumentNumber":
          validateDocumentNumber(newErrors, field, value, formData.identification.addressProof);
          break;
        case "personalTaxId":
          if (value && !/^\d{10}$/.test(value)) {
            newErrors[field] = "Must be exactly 10 digits if provided";
          }
          break;
        case "aadharCardNumber":
          if (!/^\d{12}$/.test(value)) {
            newErrors[field] = "Aadhar must be exactly 12 digits";
          }
          break;
        case "panCardNumber":
          if (!/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/.test(value)) {
            newErrors[field] = "Invalid PAN format (AAAAA9999A)";
          }
          break;
      }
    });
    return newErrors;
  };

  const validateDocumentNumber = (errors, field, value, proofType) => {
    if (!proofType) return;

    switch (proofType) {
      case "Aadhar Card":
        if (!/^\d{12}$/.test(value)) {
          errors[field] = "Aadhar must be 12 digits";
        }
        break;
      case "Pan Card":
        if (!/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/.test(value)) {
          errors[field] = "Invalid PAN format (AAAAA9999A)";
        }
        break;
      case "Driving Licence":
        if (!/^[A-Za-z0-9]{16}$/.test(value)) {
          errors[field] = "DL must be 16 alphanumeric chars";
        }
        break;
      case "Passport":
        if (!/^[A-Za-z]{1}\d{7}$/.test(value)) {
          errors[field] = "Passport must be 1 letter + 7 digits";
        }
        break;
      case "Other":
      case "Other Address Proof":
        if (!value) {
          errors[field] = "Document number required";
        }
        break;
    }
  };

  const getRequiredFieldsForStep = (step) => {
    switch (step) {
      case 0:
        return [
          "personal.firstName",
          "personal.lastName",
          "personal.dateOfBirth",
          "personal.gender",
          "personal.maritalStatus",
          "personal.nationality",
          "personal.employmentStatus",
        ];
      case 1:
        return [
          "identification.immigrationStatus",
          "identification.aadharCardNumber",
          "identification.panCardNumber",
          "identification.addressProof",
          "identification.addressDocumentNumber",
          "identification.idProof",
          "identification.documentNumber"
        ];
      case 2:
        return [
          "work.department",
          "work.jobTitle",
          "work.doj",
          "work.timeZone"
        ];
      case 3:
        return [
          "contact.residentialAddress",
          "contact.city",
          "contact.state",
          "contact.country",
          "contact.postalCode",
          "contact.workEmail",
          "contact.mobileNumber",
          "contact.primaryEmergencyContactName",
          "contact.primaryEmergencyContactNumber",
          "contact.relationshipToPrimaryEmergencyContact"
        ];
      case 4:
        return [];
      case 5:
        return [];
      default:
        return [];
    }
  };

  const formatDateForBackend = (dateString) => {
    if (!dateString) return null;
    const date = new Date(dateString);
    return date.toISOString().split('T')[0];
  };

  const handleApiResponse = async (response) => {
    const text = await response.text();
    if (!text) {
      return {
        status: response.ok ? "success" : "error",
        message: response.ok ? "Operation successful" : "Empty response from server"
      };
    }

    try {
      const data = JSON.parse(text);
      if (!response.ok) {
        return {
          status: "error",
          message: data.message || "Request failed"
        };
      }
      return data;
    } catch (e) {
      return {
        status: "error",
        message: text || "Invalid response from server"
      };
    }
  };

  const handleSubmit = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    try {
      // 1. Prepare employee data with null checks
      const employeePayload = {
        personal: formData.personal ? {
          ...formData.personal,
          dateOfBirth: formData.personal.dateOfBirth ? formatDateForBackend(formData.personal.dateOfBirth) : null,
          employmentStatus: formData.personal.employmentStatus || null
        } : null,

        identification: formData.identification ? {
          ...formData.identification,
          personalTaxId: formData.identification.personalTaxId || null,
          socialInsurance: formData.identification.socialInsurance || null
        } : null,

        work: formData.work ? {
          ...formData.work,
          doj: formData.work.doj ? formatDateForBackend(formData.work.doj) : null,
          terminationDate: formData.work.terminationDate
            ? formatDateForBackend(formData.work.terminationDate)
            : null
        } : null,

        contact: formData.contact ? { ...formData.contact } : null,

        report: formData.report ? {
          ...formData.report,
          note: notes.join('\n') || null
        } : null
      };

      // 2. Prepare FormData
      const formDataPayload = new FormData();

      // Add employee JSON
      formDataPayload.append(
        "employee",
        new Blob([JSON.stringify(employeePayload)], { type: "application/json" })
      );

      // Add documents (with null check)
      // if (formData.documents && formData.documents.companyDocuments) {
      //   formData.documents.companyDocuments.forEach((company) => {
      //     if (company.documents) {
      //       company.documents.forEach(doc => {
      //         if (doc?.file) {
      //           // Send the document type as-is, backend will handle conversion
      //           formDataPayload.append(doc.type, doc.file);
      //         }
      //       });
      //     }
      //   });
      // }


      // In handleSubmit function, modify how documents are appended to formDataPayload
      if (formData.documents && formData.documents.companyDocuments) {
        formData.documents.companyDocuments.forEach((company, companyIndex) => {
          if (company.documents) {
            company.documents.forEach(doc => {
              if (doc?.file) {
                // Include company index in the document key
                formDataPayload.append(
                  `${doc.type}_COMPANY_${companyIndex}`,
                  doc.file
                );
              }
            });
          }
        });
      }

      // 3. Send request
      const response = await fetch(`${API_BASE_URL}/employees/add`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${token}`
        },
        body: formDataPayload
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
      }

      const responseData = await response.json();
      if (responseData.status === "success") {
        showSnackbar(`Employee added successfully! ID: ${responseData.data?.id || 'N/A'}`);
        navigate(`/${role}/employees`);
      } else {
        throw new Error(responseData.message || "Employee creation failed");
      }
    } catch (error) {
      console.error("Submission error:", error);
      showSnackbar(error.message || "Failed to add employee. Please try again.", "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSnackbarClose = () => {
    setSnackbarOpen(false);
  };

  const getError = (fieldName) => {
    return errors[fieldName] ||
      errors[`personal.${fieldName}`] ||
      errors[`identification.${fieldName}`] ||
      errors[`work.${fieldName}`] ||
      errors[`contact.${fieldName}`] ||
      errors[`documents.${fieldName}`] ||
      errors[`report.${fieldName}`];
  };

  const handleCategoryChange = (category) => {
    setExpandedCategory(expandedCategory === category ? "" : category);
    setFormData(prev => ({
      ...prev,
      documents: {
        ...prev.documents,
        currentCategory: category,
        currentDocumentType: ""
      }
    }));
  };

  const handleDocumentTypeSelect = (docType) => {
  const fileInput = document.getElementById('file-upload-input');
  if (fileInput) fileInput.value = '';

  setFormData(prev => ({
    ...prev,
    documents: {
      ...prev.documents,
      currentDocumentType: docType,
      customDocumentName: docType.includes("Other") ? "" : prev.documents.customDocumentName,
      file: null,          // Reset the file
      fileName: "",        // Reset the filename
      fileInputKey: Date.now() // Force re-render of file input
    }
  }));
};

  const getUploadedCountForCategory = (categoryTitle) => {
    return formData.documents.uploadedDocuments.filter(
      doc => doc.category === categoryTitle
    ).length;
  };

  const getUploadedCountForType = (docType) => {
    return formData.documents.uploadedDocuments.filter(
      doc => doc.type === docType
    ).length;
  };

  const renderFormSection = () => {
    switch (activeStep) {
      case 0:
        return (
          <>
            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("personal.employmentStatus")}
            >
              <InputLabel>Employment Status</InputLabel>
              <Select
                name="personal.employmentStatus"
                value={formData.personal.employmentStatus}
                onChange={(e) => {
                  handleChange(e);
                  if (e.target.value) {
                    fetch(`${API_BASE_URL}/employees/next-emp-id?employmentStatus=${e.target.value}`, {
                      headers: {
                        "Authorization": `Bearer ${token}`
                      }
                    })
                      .then(response => response.json())
                      .then(data => {
                        if (data.status === "success") {
                          setFormData(prev => ({
                            ...prev,
                            personal: {
                              ...prev.personal,
                              empId: data.data
                            }
                          }));
                        }
                      });
                  }
                }}
                label="Employment Status"
                required
              >
                <MenuItem value="Full-Time Permanent">Full-Time Permanent</MenuItem>
                <MenuItem value="Contract">Contract</MenuItem>
                <MenuItem value="Part-Time">Part-Time</MenuItem>
                <MenuItem value="Internship">Internship</MenuItem>
              </Select>
              {getError("personal.employmentStatus") &&
                <Typography variant="caption" color="error">
                  {getError("personal.employmentStatus")}
                </Typography>
              }
            </FormControl>
            <TextField
              fullWidth
              margin="normal"
              label="Employee ID"
              name="personal.empId"
              value={formData.personal.empId}
              onChange={handleChange}
              required
              error={!!getError("personal.empId")}
              helperText={getError("personal.empId")}
              InputProps={{
                readOnly: true,
                style: {
                  textTransform: 'uppercase',
                  backgroundColor: '#f5f5f5'
                }
              }}
            />
            <TextField
              fullWidth
              margin="normal"
              label="First Name"
              name="personal.firstName"
              value={formData.personal.firstName}
              onChange={handleChange}
              required
              error={!!getError("personal.firstName")}
              helperText={getError("personal.firstName")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Middle Name"
              name="personal.middleName"
              value={formData.personal.middleName}
              onChange={handleChange}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Last Name"
              name="personal.lastName"
              value={formData.personal.lastName}
              onChange={handleChange}
              required
              error={!!getError("personal.lastName")}
              helperText={getError("personal.lastName")}
            />
            <TextField
              fullWidth
              margin="normal"
              type="date"
              label="Date Of Birth"
              name="personal.dateOfBirth"
              value={formData.personal.dateOfBirth}
              onChange={handleChange}
              required
              InputLabelProps={{ shrink: true }}
              error={!!getError("personal.dateOfBirth")}
              helperText={getError("personal.dateOfBirth")}
            />
            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("personal.gender")}
            >
              <InputLabel>Gender</InputLabel>
              <Select
                name="personal.gender"
                value={formData.personal.gender}
                onChange={handleChange}
                label="Gender"
                required
              >
                <MenuItem value="Male">Male</MenuItem>
                <MenuItem value="Female">Female</MenuItem>
                <MenuItem value="Other">Other</MenuItem>
              </Select>
              {getError("personal.gender") && <Typography variant="caption" color="error">{getError("personal.gender")}</Typography>}
            </FormControl>
            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("personal.maritalStatus")}
            >
              <InputLabel>Marital Status</InputLabel>
              <Select
                name="personal.maritalStatus"
                value={formData.personal.maritalStatus}
                onChange={handleChange}
                label="Marital Status"
                required
              >
                <MenuItem value="Unmarried">Unmarried</MenuItem>
                <MenuItem value="Married">Married</MenuItem>
                <MenuItem value="Widow">Widow</MenuItem>
              </Select>
              {getError("personal.maritalStatus") && <Typography variant="caption" color="error">{getError("personal.maritalStatus")}</Typography>}
            </FormControl>
            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("personal.nationality")}
            >
              <InputLabel>Nationality</InputLabel>
              <Select
                name="personal.nationality"
                value={formData.personal.nationality || ""}
                onChange={handleChange}
                label="Nationality"
                required
              >
                <MenuItem disabled>
                  <TextField value={search} onChange={handleSearchChange} label="Search" fullWidth variant="standard" />
                </MenuItem>
                {filteredOptions.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
              {getError("personal.nationality") && <Typography variant="caption" color="error">{getError("personal.nationality")}</Typography>}
            </FormControl>
            <FormControl fullWidth margin="normal">
              <InputLabel>Ethnicity</InputLabel>
              <Select
                name="personal.ethnicity"
                value={formData.personal.ethnicity}
                onChange={handleChange}
                label="Ethnicity"
              >
                <MenuItem value="Asian">Asian</MenuItem>
                <MenuItem value="Hispanic">Hispanic</MenuItem>
                <MenuItem value="African">African</MenuItem>
              </Select>
            </FormControl>
          </>
        );
      case 1:
        return (
          <>
            <FormControl fullWidth margin="normal" error={!!getError("identification.immigrationStatus")}>
              <InputLabel>Immigration Status</InputLabel>
              <Select
                name="identification.immigrationStatus"
                value={formData.identification.immigrationStatus}
                onChange={handleChange}
                label="Immigration Status"
                required
              >
                <MenuItem value="Permanent Resident">Permanent Resident</MenuItem>
                <MenuItem value="Work Visa">Work Visa/Work Permit</MenuItem>
                <MenuItem value="Student Visa">Student Visa</MenuItem>
                <MenuItem value="Temporary Resident">Temporary Resident</MenuItem>
                <MenuItem value="Refugee">Refugee/Asylum Seeker</MenuItem>
                <MenuItem value="Diplomatic Visa">Diplomatic Visa</MenuItem>
                <MenuItem value="Dependent Visa">Dependent Visa</MenuItem>
                <MenuItem value="Business Visa">Business Visa</MenuItem>
                <MenuItem value="Tourist Visa">Tourist Visa</MenuItem>
                <MenuItem value="Undocumented">Undocumented/No Status</MenuItem>
                <MenuItem value="Other">Other</MenuItem>
              </Select>
              {getError("identification.immigrationStatus") &&
                <Typography variant="caption" color="error">
                  {getError("identification.immigrationStatus")}
                </Typography>
              }
            </FormControl>

            <TextField
              fullWidth
              margin="normal"
              label="Aadhar Card Number"
              name="identification.aadharCardNumber"
              value={formData.identification.aadharCardNumber || ''}
              onChange={handleChange}
              required
              error={!!getError("identification.aadharCardNumber")}
              helperText={getError("identification.aadharCardNumber") || "Must be 12 digits"}
              inputProps={{
                maxLength: 12,
                inputMode: 'numeric',
                pattern: '[0-9]*'
              }}
            />

            <TextField
              fullWidth
              margin="normal"
              label="PAN Card Number"
              name="identification.panCardNumber"
              value={formData.identification.panCardNumber || ''}
              onChange={handleChange}
              required
              error={!!getError("identification.panCardNumber")}
              helperText={getError("identification.panCardNumber") || "Must be in format AAAAA9999A"}
              inputProps={{
                maxLength: 10,
                pattern: '[A-Z]{5}[0-9]{4}[A-Z]{1}'
              }}
            />

            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("identification.addressProof")}
            >
              <InputLabel>Address Proof</InputLabel>
              <Select
                name="identification.addressProof"
                value={formData.identification.addressProof}
                onChange={handleChange}
                label="Address Proof"
                required
              >
                <MenuItem value="Aadhar Card">Aadhar Card</MenuItem>
                <MenuItem value="Driving Licence">Driving Licence</MenuItem>
                <MenuItem value="Passport">Passport</MenuItem>
                <MenuItem value="Other Address Proof">Other</MenuItem>
              </Select>
              {getError("identification.addressProof") &&
                <Typography variant="caption" color="error">
                  {getError("identification.addressProof")}
                </Typography>
              }
            </FormControl>

            {formData.identification.addressProof === "Other Address Proof" && (
              <>
                <TextField
                  fullWidth
                  margin="normal"
                  label="Address Document Name"
                  name="identification.addressDocumentName"
                  value={formData.identification.addressDocumentName}
                  onChange={handleChange}
                  required
                  error={!!getError("identification.addressDocumentName")}
                  helperText={getError("identification.addressDocumentName")}
                />
              </>
            )}

            <TextField
              fullWidth
              margin="normal"
              label={
                formData.identification.addressProof === "Aadhar Card" ? "Aadhar Number" :
                  formData.identification.addressProof === "Driving Licence" ? "Driving Licence Number" :
                    formData.identification.addressProof === "Passport" ? "Passport Number" :
                      formData.identification.addressProof === "Other Address Proof" ? "Address Document Number" :
                        "Address Proof Number"
              }
              name="identification.addressDocumentNumber"
              value={formData.identification.addressDocumentNumber}
              onChange={handleChange}
              required
              error={!!getError("identification.addressDocumentNumber")}
              helperText={getError("identification.addressDocumentNumber") ||
                (formData.identification.addressProof === "Aadhar Card" ? "Aadhar Number must be 12 digits" :
                  formData.identification.addressProof === "Driving Licence" ? "Driving Licence Number must be 16 characters" :
                    formData.identification.addressProof === "Passport" ? "Passport Number must be 8 characters" : "")
              }
              inputProps={{
                maxLength:
                  formData.identification.addressProof === "Aadhar Card" ? 12 :
                    formData.identification.addressProof === "Driving Licence" ? 16 :
                      formData.identification.addressProof === "Passport" ? 8 : 20,
              }}
            />

            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("identification.idProof")}
            >
              <InputLabel>ID Proof</InputLabel>
              <Select
                name="identification.idProof"
                value={formData.identification.idProof}
                onChange={handleChange}
                label="ID Proof"
                required
              >
                <MenuItem value="Aadhar Card">Aadhar Card</MenuItem>
                <MenuItem value="Pan Card">Pan Card</MenuItem>
                <MenuItem value="Driving Licence">Driving Licence</MenuItem>
                <MenuItem value="Passport">Passport</MenuItem>
                <MenuItem value="Other">Other</MenuItem>
              </Select>
              {getError("identification.idProof") && <Typography variant="caption" color="error">{getError("identification.idProof")}</Typography>}
            </FormControl>

            {formData.identification.idProof && formData.identification.idProof !== "Other" && (
              <TextField
                fullWidth
                margin="normal"
                label={`Enter ${formData.identification.idProof} Number`}
                name="identification.documentNumber"
                value={formData.identification.documentNumber}
                onChange={handleChange}
                required
                error={!!getError("identification.documentNumber")}
                helperText={getError("identification.documentNumber") ||
                  (formData.identification.idProof === "Aadhar Card" ? "Aadhar Number must be 12 digits" :
                    formData.identification.idProof === "Pan Card" ? "PAN Number must be 10 characters" :
                      formData.identification.idProof === "Driving Licence" ? "Driving Licence Number must be 16 characters" :
                        formData.identification.idProof === "Passport" ? "Passport Number must be 8 characters" : "")
                }
                inputProps={{
                  maxLength:
                    formData.identification.idProof === "Aadhar Card" ? 12 :
                      formData.identification.idProof === "Pan Card" ? 10 :
                        formData.identification.idProof === "Driving Licence" ? 16 :
                          formData.identification.idProof === "Passport" ? 8 : 20,
                }}
              />
            )}

            {formData.identification.idProof === "Other" && (
              <>
                <TextField
                  fullWidth
                  margin="normal"
                  label="Document Name"
                  name="identification.documentName"
                  value={formData.identification.documentName}
                  onChange={handleChange}
                  required
                />
                <TextField
                  fullWidth
                  margin="normal"
                  label="Document Number"
                  name="identification.documentNumber"
                  value={formData.identification.documentNumber}
                  onChange={handleChange}
                  required
                  error={!!getError("identification.documentNumber")}
                  helperText={getError("identification.documentNumber")}
                />
              </>
            )}
          </>
        );
      case 2:
        return (
          <>
            <TextField
              fullWidth
              margin="normal"
              label="Department"
              name="work.department"
              value={formData.work.department}
              onChange={handleChange}
              required
              error={!!getError("work.department")}
              helperText={getError("work.department")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Job Title"
              name="work.jobTitle"
              value={formData.work.jobTitle}
              onChange={handleChange}
              required
              error={!!getError("work.jobTitle")}
              helperText={getError("work.jobTitle")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Pay Grade"
              name="work.payGrade"
              value={formData.work.payGrade}
              onChange={handleChange}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Date Of Joining"
              name="work.doj"
              type="date"
              value={formData.work.doj}
              onChange={handleChange}
              required
              InputLabelProps={{ shrink: true }}
              error={!!getError("work.doj")}
              helperText={getError("work.doj")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Termination Date"
              name="work.terminationDate"
              type="date"
              value={formData.work.terminationDate}
              onChange={handleChange}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Workstation ID"
              name="work.workstationId"
              value={formData.work.workstationId}
              onChange={handleChange}
            />
            <Autocomplete
              fullWidth
              options={timeZones}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Time Zone"
                  margin="normal"
                  required
                  error={!!getError("work.timeZone")}
                  helperText={getError("work.timeZone")}
                />
              )}
              value={formData.work.timeZone}
              onChange={(event, newValue) =>
                setFormData(prev => ({
                  ...prev,
                  work: {
                    ...prev.work,
                    timeZone: newValue
                  }
                }))
              }
            />
            <TextField
              fullWidth
              margin="normal"
              label="Shift Start Time"
              name="work.shiftStartTime"
              type="time"
              value={formData.work.shiftStartTime}
              onChange={handleChange}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Shift End Time"
              name="work.shiftEndTime"
              type="time"
              value={formData.work.shiftEndTime}
              onChange={handleChange}
              InputLabelProps={{ shrink: true }}
            />
          </>
        );
      case 3:
        return (
          <>
            <TextField
              fullWidth
              margin="normal"
              label="Current Address"
              name="contact.residentialAddress"
              value={formData.contact.residentialAddress}
              onChange={handleChange}
              required
              error={!!getError("contact.residentialAddress")}
              helperText={getError("contact.residentialAddress")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Permanent Address"
              name="contact.permanentAddress"
              value={formData.contact.permanentAddress}
              onChange={handleChange}
            />

            <FormControl
              fullWidth
              margin="normal"
              error={!!getError("contact.country")}
            >
              <InputLabel>Country</InputLabel>
              <Select
                name="contact.country"
                value={formData.contact.country || ""}
                onChange={handleChange}
                label="Country"
                required
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
                    value={search}
                    onChange={handleSearchChange}
                    label="Search"
                    fullWidth
                    variant="standard"
                  />
                </MenuItem>

                {filteredOptions.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
              {getError("contact.country") && <Typography variant="caption" color="error">{getError("contact.country")}</Typography>}
            </FormControl>

            <TextField
              fullWidth
              margin="normal"
              label="State"
              name="contact.state"
              value={formData.contact.state}
              onChange={handleChange}
              required
              error={!!getError("contact.state")}
              helperText={getError("contact.state")}
            />

            <TextField
              fullWidth
              margin="normal"
              label="City"
              name="contact.city"
              value={formData.contact.city}
              onChange={handleChange}
              required
              error={!!getError("contact.city")}
              helperText={getError("contact.city")}
            />

            <TextField
              fullWidth
              margin="normal"
              label="Postal Code"
              name="contact.postalCode"
              value={formData.contact.postalCode}
              onChange={handleChange}
              required
              error={!!getError("contact.postalCode")}
              helperText={getError("contact.postalCode")}
            />

            <TextField
              fullWidth
              margin="normal"
              label="Work Email"
              name="contact.workEmail"
              value={formData.contact.workEmail}
              onChange={handleChange}
              required
              error={!!getError("contact.workEmail")}
              helperText={getError("contact.workEmail")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Personal Email"
              name="contact.personalEmail"
              value={formData.contact.personalEmail}
              onChange={handleChange}
              error={!!getError("contact.personalEmail")}
              helperText={getError("contact.personalEmail")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Mobile Number"
              name="contact.mobileNumber"
              value={formData.contact.mobileNumber}
              onChange={handleChange}
              required
              error={!!getError("contact.mobileNumber")}
              helperText={getError("contact.mobileNumber")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Primary Emergency Contact Name"
              name="contact.primaryEmergencyContactName"
              value={formData.contact.primaryEmergencyContactName}
              onChange={handleChange}
              required
              error={!!getError("contact.primaryEmergencyContactName")}
              helperText={getError("contact.primaryEmergencyContactName")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Primary Emergency Contact Number"
              name="contact.primaryEmergencyContactNumber"
              value={formData.contact.primaryEmergencyContactNumber}
              onChange={handleChange}
              required
              error={!!getError("contact.primaryEmergencyContactNumber")}
              helperText={getError("contact.primaryEmergencyContactNumber")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Relationship to Primary Emergency Contact"
              name="contact.relationshipToPrimaryEmergencyContact"
              value={formData.contact.relationshipToPrimaryEmergencyContact}
              onChange={handleChange}
              required
              error={!!getError("contact.relationshipToPrimaryEmergencyContact")}
              helperText={getError("contact.relationshipToPrimaryEmergencyContact")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Secondary Emergency Contact Name"
              name="contact.secondaryEmergencyContactName"
              value={formData.contact.secondaryEmergencyContactName}
              onChange={handleChange}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Secondary Emergency Contact Number"
              name="contact.secondaryEmergencyContactNumber"
              value={formData.contact.secondaryEmergencyContactNumber}
              onChange={handleChange}
              error={!!getError("contact.secondaryEmergencyContactNumber")}
              helperText={getError("contact.secondaryEmergencyContactNumber")}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Relationship to Secondary Emergency Contact"
              name="contact.relationshipToSecondaryEmergencyContact"
              value={formData.contact.relationshipToSecondaryEmergencyContact}
              onChange={handleChange}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Family Doctor Name"
              name="contact.familyDoctorName"
              value={formData.contact.familyDoctorName}
              onChange={handleChange}
            />
            <TextField
              fullWidth
              margin="normal"
              label="Family Doctor Contact Number"
              name="contact.familyDoctorContactNumber"
              value={formData.contact.familyDoctorContactNumber}
              onChange={handleChange}
              error={!!getError("contact.familyDoctorContactNumber")}
              helperText={getError("contact.familyDoctorContactNumber")}
            />
          </>
        );
      case 4:
        return (
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
                        badgeContent={formData.documents.companyDocuments.reduce(
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
                      formData.documents.companyDocuments.map((company, companyIndex) => (
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
                                  setFormData(prev => {
                                    const updatedCompanyDocuments = [...prev.documents.companyDocuments];
                                    updatedCompanyDocuments.splice(companyIndex, 1);

                                    const updatedUploadedDocuments = prev.documents.uploadedDocuments.filter(
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
                                      documents: {
                                        ...prev.documents,
                                        companyDocuments: updatedCompanyDocuments,
                                        uploadedDocuments: adjustedUploadedDocuments,
                                        currentCompanyIndex: Math.min(
                                          prev.documents.currentCompanyIndex,
                                          updatedCompanyDocuments.length - 1
                                        )
                                      }
                                    };
                                  });
                                }}
                              >
                                Remove Company
                              </Button>
                            )}
                          </Box>

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
                                    formData.documents.currentDocumentType === doc.type &&
                                      formData.documents.currentCompanyIndex === companyIndex
                                      ? "primary"
                                      : company.documents.some(d => d.type === doc.type && d.category === category.title)
                                        ? "success"
                                        : "default"
                                  }
                                  variant={
                                    formData.documents.currentDocumentType === doc.type &&
                                      formData.documents.currentCompanyIndex === companyIndex
                                      ? "filled"
                                      : "outlined"
                                  }
                                />
                              ))}
                            </Box>
                          </Box>

                          {formData.documents.currentCategory === category.title &&
                            formData.documents.currentCompanyIndex === companyIndex && (
                              <>
                                {formData.documents.currentDocumentType && (
                                  <>
                                    <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                      Upload {formData.documents.currentDocumentType} for {company.companyName}
                                    </Typography>

                                    {formData.documents.currentDocumentType.includes("Other") && (
                                      <TextField
                                        fullWidth
                                        margin="normal"
                                        label="Document Name"
                                        name="documents.customDocumentName"
                                        value={formData.documents.customDocumentName}
                                        onChange={handleChange}
                                        required
                                        sx={{ mb: 2 }}
                                      />
                                    )}

                                    <Box sx={{ mt: 2 }}>
                                      <input
                                        accept=".pdf,.jpg,.jpeg,.png"
                                        style={{ display: 'none' }}
                                        id="file-upload-input"
                                        key={`file-upload-${formData.documents.fileInputKey}`}
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
                                      {formData.documents.fileName && (
                                        <Typography variant="body2" sx={{ mb: 2 }}>
                                          Selected file: {formData.documents.fileName}
                                        </Typography>
                                      )}
                                    </Box>

                                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                                      <Button
                                        variant="contained"
                                        color="primary"
                                        onClick={handleUploadDocument}
                                        disabled={!formData.documents.file ||
                                          (formData.documents.currentDocumentType.includes("Other") &&
                                            !formData.documents.customDocumentName)}
                                      >
                                        Upload
                                      </Button>
                                      <Button
                                        variant="outlined"
                                        onClick={() => {
                                          setFormData(prev => ({
                                            ...prev,
                                            documents: {
                                              ...prev.documents,
                                              currentDocumentType: "",
                                              customDocumentName: "",
                                              file: null,
                                              fileName: ""
                                            }
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
                                    <TableCell>Uploaded</TableCell>
                                    <TableCell>Actions</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {company.documents
                                    .filter(doc => doc.category === category.title)
                                    .map((doc, index) => (
                                      <TableRow key={index}>
                                        <TableCell>{doc.type}</TableCell>
                                        <TableCell>
                                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            {doc.fileName.endsWith('.pdf') ? (
                                              <PictureAsPdfIcon color="error" />
                                            ) : (
                                              <ImageIcon color="primary" />
                                            )}
                                            {doc.fileName}
                                          </Box>
                                        </TableCell>
                                        <TableCell>{doc.uploadedAt}</TableCell>
                                        <TableCell>
                                          <IconButton
                                            color="primary"
                                            onClick={() => handleViewDocument(doc)}
                                            sx={{ mr: 1 }}
                                          >
                                            <VisibilityIcon />
                                          </IconButton>
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
                                  formData.documents.currentDocumentType === doc.type &&
                                    formData.documents.currentCategory === category.title
                                    ? "primary"
                                    : formData.documents.companyDocuments[0].documents.some(d => d.type === doc.type && d.category === category.title)
                                      ? "success"
                                      : "default"
                                }
                                variant={
                                  formData.documents.currentDocumentType === doc.type &&
                                    formData.documents.currentCategory === category.title
                                    ? "filled"
                                    : "outlined"
                                }
                              />
                            ))}
                          </Box>
                        </Box>

                        {formData.documents.currentCategory === category.title && (
                          <>
                            {formData.documents.currentDocumentType && (
                              <>
                                <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold' }}>
                                  Upload {formData.documents.currentDocumentType}
                                </Typography>

                                {formData.documents.currentDocumentType.includes("Other") && (
                                  <TextField
                                    fullWidth
                                    margin="normal"
                                    label="Document Name"
                                    name="documents.customDocumentName"
                                    value={formData.documents.customDocumentName}
                                    onChange={handleChange}
                                    required
                                    sx={{ mb: 2 }}
                                  />
                                )}

                                <Box sx={{ mt: 2 }}>
                                  <input
                                    accept=".pdf,.jpg,.jpeg,.png"
                                    style={{ display: 'none' }}
                                    id="file-upload-input"
                                    key={`file-upload-${formData.documents.fileInputKey}`}
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
                                  {formData.documents.fileName && (
                                    <Typography variant="body2" sx={{ mb: 2 }}>
                                      Selected file: {formData.documents.fileName}
                                    </Typography>
                                  )}
                                </Box>

                                <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                                  <Button
                                    variant="contained"
                                    color="primary"
                                    onClick={handleUploadDocument}
                                    disabled={!formData.documents.file ||
                                      (formData.documents.currentDocumentType.includes("Other") &&
                                        !formData.documents.customDocumentName)}
                                  >
                                    Upload
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    onClick={() => {
                                      setFormData(prev => ({
                                        ...prev,
                                        documents: {
                                          ...prev.documents,
                                          currentDocumentType: "",
                                          customDocumentName: "",
                                          file: null,
                                          fileName: ""
                                        }
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

                        {formData.documents.companyDocuments[0].documents.filter(doc => doc.category === category.title).length > 0 && (
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
                                  <TableCell>Uploaded</TableCell>
                                  <TableCell>Actions</TableCell>
                                </TableRow>
                              </TableHead>
                              <TableBody>
                                {formData.documents.companyDocuments[0].documents
                                  .filter(doc => doc.category === category.title)
                                  .map((doc, index) => (
                                    <TableRow key={index}>
                                      <TableCell>{doc.type}</TableCell>
                                      <TableCell>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                          {doc.fileName.endsWith('.pdf') ? (
                                            <PictureAsPdfIcon color="error" />
                                          ) : (
                                            <ImageIcon color="primary" />
                                          )}
                                          {doc.fileName}
                                        </Box>
                                      </TableCell>
                                      <TableCell>{doc.uploadedAt}</TableCell>
                                      <TableCell>
                                        <IconButton
                                          color="primary"
                                          onClick={() => handleViewDocument(doc)}
                                          sx={{ mr: 1 }}
                                        >
                                          <VisibilityIcon />
                                        </IconButton>
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
                      {category.title === "Previous Company Documents" && formData.documents.companyDocuments.length > 1 && (
                        <Typography variant="caption" color="text.secondary" display="block">
                          ({formData.documents.companyDocuments.length} companies)
                        </Typography>
                      )}
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                      {category.documents.map(doc => {
                        const count = formData.documents.companyDocuments.reduce(
                          (total, company) => total +
                            company.documents.filter(d => d.type === doc.type && d.category === category.title).length,
                          0
                        );
                        return (
                          <Box key={doc.type} sx={{ display: 'flex', justifyContent: 'space-between' }}>
                            <Typography variant="body2">
                              {doc.type}{doc.mandatory ? ' *' : ''}
                            </Typography>
                            <Typography
                              variant="body2"
                              color={doc.mandatory && count === 0 ? 'error' : 'text.secondary'}
                              sx={{ fontWeight: doc.mandatory && count === 0 ? 'bold' : 'normal' }}
                            >
                              {count > 0 ? `${count} uploaded` : 'Not uploaded'}
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
        );
      case 5:
        return (
          <Box sx={{ padding: 3 }}>
            <Autocomplete
              freeSolo
              options={reportingManagers || []}
              getOptionLabel={(option) =>
                option && typeof option === "object"
                  ? `${option.empId} - ${option.name}`
                  : ""
              }
              inputValue={
                formData.report.reportingManagerId
                  ? `${formData.report.reportingManagerId} - ${formData.report.reportingManagerName}`
                  : ""
              }
              onInputChange={(event, newInputValue) => {
                // Optional: handle input for manual search, not needed for fixed dropdown
              }}
              onChange={(event, newValue) => {
                if (newValue) {
                  setFormData((prev) => ({
                    ...prev,
                    report: {
                      ...prev.report,
                      reportingManagerId: newValue.empId,
                      reportingManagerName: newValue.name,
                    },
                  }));
                } else {
                  setFormData((prev) => ({
                    ...prev,
                    report: {
                      ...prev.report,
                      reportingManagerId: "",
                      reportingManagerName: "",
                    },
                  }));
                }
              }}
              value={
                reportingManagers.find(
                  (m) => m.empId === formData.report.reportingManagerId
                ) || null
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Reporting Manager"
                  variant="outlined"
                  fullWidth
                  margin="normal"
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

            <FormControl fullWidth margin="normal">
              <InputLabel>Indirect Manager</InputLabel>
              <Select
                name="report.indirectManager"
                value={formData.report.indirectManager}
                onChange={handleChange}
                label="Indirect Manager"
              >
                <MenuItem value="Indirect Manager 1">Indirect Manager 1</MenuItem>
                <MenuItem value="Indirect Manager 2">Indirect Manager 2</MenuItem>
                <MenuItem value="Indirect Manager 3">Indirect Manager 3</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth margin="normal">
              <InputLabel>First Level Approver</InputLabel>
              <Select
                name="report.firstLevelApprover"
                value={formData.report.firstLevelApprover}
                onChange={handleChange}
                label="First Level Approver"
              >
                <MenuItem value="Approver 1">Approver 1</MenuItem>
                <MenuItem value="Approver 2">Approver 2</MenuItem>
                <MenuItem value="Approver 3">Approver 3</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth margin="normal">
              <InputLabel>Second Level Approver</InputLabel>
              <Select
                name="report.secondLevelApprover"
                value={formData.report.secondLevelApprover}
                onChange={handleChange}
                label="Second Level Approver"
              >
                <MenuItem value="Approver 1">Approver 1</MenuItem>
                <MenuItem value="Approver 2">Approver 2</MenuItem>
                <MenuItem value="Approver 3">Approver 3</MenuItem>
              </Select>
            </FormControl>

            <FormControl fullWidth margin="normal">
              <InputLabel>Third Level Approver</InputLabel>
              <Select
                name="report.thirdLevelApprover"
                value={formData.report.thirdLevelApprover}
                onChange={handleChange}
                label="Third Level Approver"
              >
                <MenuItem value="Approver 1">Approver 1</MenuItem>
                <MenuItem value="Approver 2">Approver 2</MenuItem>
                <MenuItem value="Approver 3">Approver 3</MenuItem>
              </Select>
            </FormControl>

            <Button
              variant="contained"
              color="primary"
              onClick={() => setIsNoteFormVisible(true)}
              sx={{ mt: 2 }}
            >
              Add Note
            </Button>

            {isNoteFormVisible && (
              <Box sx={{ mt: 2 }}>
                <TextField
                  fullWidth
                  name="report.note"
                  value={formData.report.note}
                  onChange={handleChange}
                  label="Note"
                  multiline
                  rows={4}
                  margin="normal"
                />
                <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
                  <Button onClick={handleAddNote} variant="contained" color="secondary">
                    Save
                  </Button>
                  <Button onClick={() => setIsNoteFormVisible(false)} variant="outlined">
                    Cancel
                  </Button>
                </Box>
              </Box>
            )}

            <Table sx={{ mt: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Note</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {notes.map((note, index) => (
                  <TableRow key={index}>
                    <TableCell>{note}</TableCell>
                    <TableCell>
                      <IconButton onClick={() => handleEditNote(index)} color="primary">
                        <EditIcon />
                      </IconButton>
                      <IconButton onClick={() => handleDeleteNote(index)} color="secondary">
                        <DeleteIcon />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        );
      default:
        return null;
    }
  };

  const DocumentPreviewModal = () => {
    if (!currentPreview) return null;

    const renderPreview = () => {
      if (currentPreview.type === 'application/pdf') {
        return (
          <iframe
            src={URL.createObjectURL(currentPreview.file)}
            width="100%"
            height="500px"
            title={currentPreview.name}
            style={{ border: 'none' }}
          />
        );
      } else if (currentPreview.type.startsWith('image/')) {
        return (
          <img
            src={URL.createObjectURL(currentPreview.file)}
            alt={currentPreview.name}
            style={{ maxWidth: '100%', maxHeight: '80vh' }}
          />
        );
      } else {
        return <Typography>Preview not available for this file type</Typography>;
      }
    };

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
          {renderPreview()}
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            onClick={() => {
              const link = document.createElement('a');
              link.href = URL.createObjectURL(currentPreview.file);
              link.download = currentPreview.name;
              link.click();
            }}
          >
            Download
          </Button>
        </DialogActions>
      </Dialog>
    );
  };

  return (
    <Container maxWidth="md" sx={{ mt: 3, p: 6, borderRadius: 1, boxShadow: 2, bgcolor: "white", maxWidth: "500", height: "auto" }}>
      <Typography variant="h5" fontWeight="bold" gutterBottom textAlign="center" pb={3}>
        Employee Registration
      </Typography>

      <Stepper activeStep={activeStep} alternativeLabel>
        {steps.map((label, index) => (
          <Step key={index}>
            <StepLabel onClick={() => setActiveStep(index)}>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Box sx={{ mt: 2 }}>
        {renderFormSection()}

        <Box sx={{ mt: 3, display: "flex", justifyContent: "space-between" }}>
          {activeStep > 0 && (
            <Button variant="contained" color="primary" onClick={handleBack}>
              Previous
            </Button>
          )}
          {activeStep < steps.length - 1 ? (
            <Button variant="contained" color="primary" onClick={handleNext}>
              Next
            </Button>
          ) : (
            <Button
              variant="contained"
              color="success"
              onClick={handleSubmit}
              disabled={isSubmitting}
              startIcon={isSubmitting ? <CircularProgress size={20} /> : null}
            >
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          )}
        </Box>
      </Box>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert
          onClose={handleSnackbarClose}
          severity={snackbarSeverity}
          sx={{ width: '100%' }}
          variant="filled"
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
      <DocumentPreviewModal />
    </Container>
  );
};

export default Employee;