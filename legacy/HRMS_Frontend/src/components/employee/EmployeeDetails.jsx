import React, { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
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
  Divider,
  Avatar,
  Grid,
  Chip,
  Paper,
  useTheme,
  Tabs,
  Tab,
  IconButton,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TableHead,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from "@mui/material";
import {
  Person as PersonIcon,
  Email as EmailIcon,
  Badge as BadgeIcon,
  Work as WorkIcon,
  ContactPhone as ContactIcon,
  AssignmentInd as AssignmentIcon,
  Report as ReportIcon,
  PictureAsPdf as PictureAsPdfIcon,
  CalendarToday as CalendarIcon,
  Home as HomeIcon,
  Phone as PhoneIcon,
  LocalHospital as HospitalIcon,
  SupervisorAccount as SupervisorIcon,
  AccessTime as TimeIcon,
  Public as CountryIcon,
  Fingerprint as FingerprintIcon,
  Print as PrintIcon,
  Close as CloseIcon,
  ExpandMore as ExpandMoreIcon,
  Visibility as VisibilityIcon,
  Image as ImageIcon,
} from "@mui/icons-material";
import API_BASE_URL from "../config/apiConfig";

const EmployeeDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [employee, setEmployee] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState(0);
  const [openPrintDialog, setOpenPrintDialog] = useState(false);
  const theme = useTheme();
  const printRef = useRef();

  const [documentViewerOpen, setDocumentViewerOpen] = useState(false);
  const [currentDocument, setCurrentDocument] = useState(null);
  const [documentUrl, setDocumentUrl] = useState(null);
  const [documentLoading, setDocumentLoading] = useState(false);

  const handleViewDocument = (doc) => {
    setCurrentDocument(doc);
    setDocumentViewerOpen(true);
    setDocumentLoading(true);
  };

  const handleCloseDocumentViewer = () => {
    setDocumentViewerOpen(false);
    setCurrentDocument(null);
    setDocumentUrl(null);
  };

  useEffect(() => {
    const fetchEmployee = async () => {
      try {
        setLoading(true);
        const token = localStorage.getItem("token");
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
        } else {
          throw new Error("Invalid employee data format");
        }
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

    fetchEmployee();
  }, [id, navigate]);

  const renderDocument = () => {
  if (!currentDocument?.fileUrl) {
    return (
      <Typography variant="h6" color="error" sx={{ textAlign: 'center', p: 4 }}>
        Failed to load document
      </Typography>
    );
  }

  const fileType = currentDocument.fileType?.toLowerCase() || '';

  if (fileType.includes('pdf')) {
    return (
      <iframe
        src={currentDocument.fileUrl}
        width="100%"
        height="600px"
        title={currentDocument.fileName}
        style={{ border: 'none' }}
      />
    );
  } else if (fileType.includes('jpeg') || fileType.includes('jpg') || fileType.includes('png')) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
        <img
          src={currentDocument.fileUrl}
          alt={currentDocument.fileName}
          style={{ maxWidth: '100%', maxHeight: '80vh', objectFit: 'contain' }}
        />
      </Box>
    );
  } else {
    return (
      <Box sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Document cannot be previewed
        </Typography>
        <Button
          variant="contained"
          onClick={() => handleDownload(currentDocument.fileUrl, currentDocument.fileName)}
        >
          Download File
        </Button>
      </Box>
    );
  }
};

const handleDownload = async (url, filename) => {
  try {
    const response = await fetch(url);
    const blob = await response.blob();
    const blobUrl = window.URL.createObjectURL(blob);
    
    const anchor = document.createElement('a');
    anchor.href = blobUrl;
    anchor.download = filename || 'document';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    
    // Clean up
    window.URL.revokeObjectURL(blobUrl);
  } catch (error) {
    console.error('Download failed:', error);
    // Fallback to direct download
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename || 'document';
    anchor.target = '_blank';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  }
};

  const DocumentViewerDialog = () => (
  <Dialog
    open={documentViewerOpen}
    onClose={handleCloseDocumentViewer}
    maxWidth="lg"
    fullWidth
    fullScreen={currentDocument?.fileType?.toLowerCase().includes('pdf')}
  >
    <DialogTitle>
      {currentDocument?.fileName || "Document Viewer"}
      <IconButton
        aria-label="close"
        onClick={handleCloseDocumentViewer}
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
      {renderDocument()}
    </DialogContent>
    <DialogActions>
      {currentDocument?.fileUrl && (
        <Button
          variant="contained"
          onClick={() => handleDownload(currentDocument.fileUrl, currentDocument.fileName)}
          sx={{ mr: 2 }}
        >
          Download
        </Button>
      )}
      <Button
        variant="outlined"
        onClick={handleCloseDocumentViewer}
      >
        Close
      </Button>
    </DialogActions>
  </Dialog>
);

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
  };

  const handlePrint = () => {
    setOpenPrintDialog(true);
  };

  const handleClosePrintDialog = () => {
    setOpenPrintDialog(false);
  };

  const handlePrintConfirm = () => {
    const printContent = printRef.current.innerHTML;
    const originalContent = document.body.innerHTML;

    document.body.innerHTML = printContent;
    window.print();
    document.body.innerHTML = originalContent;

    // Re-initialize any necessary scripts
    window.location.reload();
    setOpenPrintDialog(false);
  };

  const PrintView = () => (
    <div style={{ display: 'none' }}>
      <div ref={printRef}>
        <Box sx={{ p: 4, maxWidth: 800, margin: '0 auto' }}>
          {/* Print Header */}
          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
              Employee Details
            </Typography>
            <Typography variant="subtitle1" sx={{ color: theme.palette.text.secondary }}>
              {new Date().toLocaleDateString()}
            </Typography>
          </Box>

          {/* Employee Profile */}
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 4, p: 3, borderBottom: `1px solid ${theme.palette.divider}` }}>
            <Avatar
              alt={employee.personal?.firstName}
              sx={{
                width: 80,
                height: 80,
                mr: 3,
                fontSize: '2rem',
                bgcolor: theme.palette.primary.main,
                color: theme.palette.primary.contrastText
              }}
            >
              {employee.personal?.firstName?.charAt(0)}{employee.personal?.lastName?.charAt(0)}
            </Avatar>
            <Box>
              <Typography variant="h5" fontWeight="bold">
                {employee.personal?.firstName} {employee.personal?.lastName}
              </Typography>
              <Typography variant="body1" sx={{ mt: 1 }}>
                <strong>Employee ID:</strong> {employee.personal?.empId || '-'}
              </Typography>
              <Typography variant="body1">
                <strong>Department:</strong> {employee.work?.department || '-'}
              </Typography>
              <Typography variant="body1">
                <strong>Job Title:</strong> {employee.work?.jobTitle || '-'}
              </Typography>
            </Box>
          </Box>

          {/* Personal Information */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
              Personal Information
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography><strong>First Name:</strong> {employee.personal?.firstName || '-'}</Typography>
                <Typography><strong>Middle Name:</strong> {employee.personal?.middleName || '-'}</Typography>
                <Typography><strong>Last Name:</strong> {employee.personal?.lastName || '-'}</Typography>
                <Typography><strong>Date of Birth:</strong> {employee.personal?.dateOfBirth || '-'}</Typography>
                <Typography><strong>Employment Status:</strong> {employee.personal?.employmentStatus || '-'}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography><strong>Gender:</strong> {employee.personal?.gender || '-'}</Typography>
                <Typography><strong>Marital Status:</strong> {employee.personal?.maritalStatus || '-'}</Typography>
                <Typography><strong>Nationality:</strong> {employee.personal?.nationality || '-'}</Typography>
                <Typography><strong>Ethnicity:</strong> {employee.personal?.ethnicity || '-'}</Typography>
              </Grid>
            </Grid>
          </Box>

          {/* Work Information */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
              Work Information
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography><strong>Department:</strong> {employee.work?.department || '-'}</Typography>
                <Typography><strong>Job Title:</strong> {employee.work?.jobTitle || '-'}</Typography>
                <Typography><strong>Pay Grade:</strong> {employee.work?.payGrade || '-'}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography><strong>Date of Joining:</strong> {employee.work?.doj || '-'}</Typography>
                <Typography><strong>Termination Date:</strong> {employee.work?.terminationDate || '-'}</Typography>
                <Typography><strong>Workstation ID:</strong> {employee.work?.workstationId || '-'}</Typography>
                <Typography><strong>Shift:</strong> {employee.work?.shiftStartTime || '-'} to {employee.work?.shiftEndTime || '-'}</Typography>
              </Grid>
            </Grid>
          </Box>

          {/* Contact Information */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
              Contact Information
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography><strong>Work Email:</strong> {employee.contact?.workEmail || '-'}</Typography>
                <Typography><strong>Personal Email:</strong> {employee.contact?.personalEmail || '-'}</Typography>
                <Typography><strong>Mobile:</strong> {employee.contact?.mobileNumber || '-'}</Typography>
                <Typography><strong>Residential Address:</strong> {employee.contact?.residentialAddress || '-'}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography><strong>City:</strong> {employee.contact?.city || '-'}</Typography>
                <Typography><strong>State:</strong> {employee.contact?.state || '-'}</Typography>
                <Typography><strong>Country:</strong> {employee.contact?.country || '-'}</Typography>
                <Typography><strong>Postal Code:</strong> {employee.contact?.postalCode || '-'}</Typography>
              </Grid>
            </Grid>
          </Box>

          {/* Emergency Contacts */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
              Emergency Contacts
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography><strong>Primary Contact:</strong> {employee.contact?.primaryEmergencyContactName || '-'}</Typography>
                <Typography><strong>Phone:</strong> {employee.contact?.primaryEmergencyContactNumber || '-'}</Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography><strong>Secondary Contact:</strong> {employee.contact?.secondaryEmergencyContactName || '-'}</Typography>
                <Typography><strong>Phone:</strong> {employee.contact?.secondaryEmergencyContactNumber || '-'}</Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography><strong>Family Doctor:</strong> {employee.contact?.familyDoctorName || '-'}</Typography>
                <Typography><strong>Phone:</strong> {employee.contact?.familyDoctorContactNumber || '-'}</Typography>
              </Grid>
            </Grid>
          </Box>

          {/* Add Documents section */}
          {employee.documents && employee.documents.length > 0 && (
            <Box sx={{ mb: 4 }}>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
                Documents
              </Typography>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 'bold' }}>Type</TableCell>
                    <TableCell sx={{ fontWeight: 'bold' }}>File Name</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {employee.documents.map((doc, index) => (
                    <TableRow key={index}>
                      <TableCell>{doc.documentType.replace(/_/g, ' ')}</TableCell>
                      <TableCell>{doc.fileName}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}

          {/* Footer */}
          <Box sx={{ mt: 4, pt: 2, borderTop: `1px solid ${theme.palette.divider}`, textAlign: 'center' }}>
            <Typography variant="body2" sx={{ color: theme.palette.text.secondary }}>
              Generated on {new Date().toLocaleString()}
            </Typography>
          </Box>
        </Box>
      </div>
    </div>
  );

  if (loading) {
    return (
      <Box sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '80vh'
      }}>
        <CircularProgress size={60} thickness={4} sx={{ color: theme.palette.primary.main }} />
      </Box>
    );
  }

  if (error) {
    return (
      <Container maxWidth="md" sx={{ mt: 4, textAlign: 'center' }}>
        <Paper elevation={3} sx={{ p: 4, borderRadius: 3 }}>
          <Typography variant="h5" color="error" gutterBottom>
            {error}
          </Typography>
          <Button
            variant="contained"
            onClick={() => navigate(-1)}
            sx={{ mt: 3, px: 4, py: 1.5, borderRadius: 2 }}
          >
            Go Back
          </Button>
        </Paper>
      </Container>
    );
  }

  if (!employee) {
    return (
      <Container maxWidth="md" sx={{ mt: 4, textAlign: 'center' }}>
        <Paper elevation={3} sx={{ p: 4, borderRadius: 3 }}>
          <Typography variant="h5" gutterBottom>
            Employee not found
          </Typography>
          <Button
            variant="contained"
            onClick={() => navigate(-1)}
            sx={{ mt: 3, px: 4, py: 1.5, borderRadius: 2 }}
          >
            Go Back
          </Button>
        </Paper>
      </Container>
    );
  }

  const renderPersonalInfo = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <PersonIcon color="primary" sx={{ mr: 1 }} /> Basic Information
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Employment Status</TableCell>
                  <TableCell>
                    <Chip
                      label={employee.personal?.employmentStatus || "-"}
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Employee ID</TableCell>
                  <TableCell>
                    <Chip label={employee.personal?.empId || "-"} variant="outlined" />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>First Name</TableCell>
                  <TableCell>{employee.personal?.firstName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Middle Name</TableCell>
                  <TableCell>{employee.personal?.middleName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Last Name</TableCell>
                  <TableCell>{employee.personal?.lastName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Date of Birth</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CalendarIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.personal?.dateOfBirth || "-"}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <FingerprintIcon color="primary" sx={{ mr: 1 }} /> Demographic Information
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Gender</TableCell>
                  <TableCell>
                    <Chip
                      label={employee.personal?.gender || "-"}
                      color={employee.personal?.gender?.toLowerCase() === 'male' ? 'primary' : 'secondary'}
                      size="small"
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Marital Status</TableCell>
                  <TableCell>{employee.personal?.maritalStatus || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Nationality</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CountryIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.personal?.nationality || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Ethnicity</TableCell>
                  <TableCell>{employee.personal?.ethnicity || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderIdentificationInfo = () => (
    <Card elevation={3} sx={{ borderRadius: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <AssignmentIcon color="primary" sx={{ mr: 1 }} /> Identification Details
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Immigration Status</TableCell>
                  <TableCell>
                    <Chip
                      label={employee.identification?.immigrationStatus || "-"}
                      color={employee.identification?.immigrationStatus === 'Citizen' ? 'success' : 'warning'}
                      size="small"
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Aadhar Card</TableCell>
                  <TableCell>{employee.identification?.aadharCardNumber || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Address Proof</TableCell>
                  <TableCell>{employee.identification?.addressProof || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Address Document Name</TableCell>
                  <TableCell>{employee.identification?.addressDocumentName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Address Document Number</TableCell>
                  <TableCell>{employee.identification?.addressDocumentNumber || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
          <Grid item xs={12} md={6}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Pan Card</TableCell>
                  <TableCell>{employee.identification?.panCardNumber || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>ID Proof</TableCell>
                  <TableCell>{employee.identification?.idProof || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Document Name</TableCell>
                  <TableCell>{employee.identification?.documentName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Document Number</TableCell>
                  <TableCell>{employee.identification?.documentNumber || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

  const renderWorkInfo = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <WorkIcon color="primary" sx={{ mr: 1 }} /> Employment Details
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Department</TableCell>
                  <TableCell>
                    <Chip label={employee.work?.department || "-"} color="info" variant="outlined" />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Job Title</TableCell>
                  <TableCell>{employee.work?.jobTitle || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Pay Grade</TableCell>
                  <TableCell>{employee.work?.payGrade || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <TimeIcon color="primary" sx={{ mr: 1 }} /> Work Schedule
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Date of Joining</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CalendarIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.work?.doj || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Termination Date</TableCell>
                  <TableCell>
                    {employee.work?.terminationDate ? (
                      <Box sx={{ display: 'flex', alignItems: 'center' }}>
                        <CalendarIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                        {employee.work?.terminationDate}
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Workstation ID</TableCell>
                  <TableCell>{employee.work?.workstationId || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Time Zone</TableCell>
                  <TableCell>{employee.work?.timeZone || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Shift</TableCell>
                  <TableCell>
                    {employee.work?.shiftStartTime && employee.work?.shiftEndTime ?
                      `${employee.work.shiftStartTime} - ${employee.work.shiftEndTime}` : "-"}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderContactInfo = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <HomeIcon color="primary" sx={{ mr: 1 }} /> Address Information
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Residential</TableCell>
                  <TableCell>{employee.contact?.residentialAddress || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Permanent</TableCell>
                  <TableCell>{employee.contact?.permanentAddress || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>City</TableCell>
                  <TableCell>{employee.contact?.city || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>State</TableCell>
                  <TableCell>{employee.contact?.state || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Country</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CountryIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.contact?.country || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Postal Code</TableCell>
                  <TableCell>{employee.contact?.postalCode || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={12} md={6}>
        <Card elevation={3} sx={{ height: '100%', borderRadius: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
              <ContactIcon color="primary" sx={{ mr: 1 }} /> Contact Details
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Work Email</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <EmailIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.contact?.workEmail || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Personal Email</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <EmailIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.contact?.personalEmail || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Mobile</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {employee.contact?.mobileNumber || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Primary Emergency</TableCell>
                  <TableCell>
                    {employee.contact?.primaryEmergencyContactName ? (
                      <Box>
                        {employee.contact.primaryEmergencyContactName}
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                          <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                          {employee.contact.primaryEmergencyContactNumber || "-"}
                        </Box>
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Secondary Emergency</TableCell>
                  <TableCell>
                    {employee.contact?.secondaryEmergencyContactName ? (
                      <Box>
                        {employee.contact.secondaryEmergencyContactName}
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                          <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                          {employee.contact.secondaryEmergencyContactNumber || "-"}
                        </Box>
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Family Doctor</TableCell>
                  <TableCell>
                    {employee.contact?.familyDoctorName ? (
                      <Box>
                        {employee.contact.familyDoctorName}
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                          <HospitalIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                          {employee.contact.familyDoctorContactNumber || "-"}
                        </Box>
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderDocumentInfo = () => {
    if (!employee.documents || employee.documents.length === 0) {
      return (
        <Paper elevation={3} sx={{ p: 3, textAlign: 'center', borderRadius: 3 }}>
          <Typography variant="h6" color="textSecondary">
            No documents available for this employee
          </Typography>
        </Paper>
      );
    }

    // Document categories from AddEmployee.jsx
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

    // Group documents by company for Previous Company Documents
    const groupDocumentsByCompany = () => {
      const companyDocs = employee.documents.filter(doc =>
        documentCategories.find(cat =>
          cat.title === "Previous Company Documents" &&
          cat.documents.some(d => d.type === doc.documentType)
        ));

      // Extract unique company indexes
      const companyIndexes = [...new Set(companyDocs.map(doc => doc.companyIndex || 0))];

      return companyIndexes.map(index => ({
        companyIndex: index,
        companyName: `Company ${index + 1}`,
        documents: companyDocs.filter(doc => (doc.companyIndex || 0) === index)
      }));
    };

    // Group documents for all other categories
    const groupedDocuments = documentCategories.map(category => {
      if (category.title === "Previous Company Documents") {
        return {
          ...category,
          companyGroups: groupDocumentsByCompany()
        };
      }

      const categoryDocs = employee.documents.filter(doc =>
        category.documents.some(d => d.type === doc.documentType)
      );

      return {
        ...category,
        documents: category.documents.map(docType => {
          const docsForType = categoryDocs.filter(doc =>
            doc.documentType === docType.type
          );
          return {
            ...docType,
            uploadedDocuments: docsForType.map(doc => ({
              ...doc,
              documentType: doc.documentType.replace(/_/g, ' ')
            }))
          };
        })
      };
    });

    return (
      <Card elevation={3} sx={{ borderRadius: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <PictureAsPdfIcon color="primary" sx={{ mr: 1 }} /> Employee Documents
          </Typography>
          <Divider sx={{ mb: 2 }} />

          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle1" color="text.secondary">
              Documents are organized by category. Mandatory documents are marked with *
            </Typography>
          </Box>

          {groupedDocuments.map((category) => (
            <Accordion key={category.title} sx={{ mb: 2 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                  <Typography sx={{ fontWeight: 'bold' }}>{category.title}</Typography>
                  <Box sx={{ flexGrow: 1 }} />
                  <Chip
                    label={`${category.title === "Previous Company Documents"
                        ? category.companyGroups.reduce((total, group) => total + group.documents.length, 0)
                        : category.documents.reduce((total, doc) => total + doc.uploadedDocuments.length, 0)
                      }/${category.documents.filter(d => d.mandatory).length
                      } mandatory`}
                    size="small"
                    color={
                      category.documents.filter(d => d.mandatory).length ===
                        category.documents.filter(d => d.mandatory &&
                          (category.title === "Previous Company Documents"
                            ? category.companyGroups.some(group =>
                              group.documents.some(doc => doc.documentType === d.type))
                            : d.uploadedDocuments.length > 0)
                        ).length
                        ? "success"
                        : "error"
                    }
                    sx={{ ml: 2 }}
                  />
                </Box>
              </AccordionSummary>
              <AccordionDetails>
                {category.title === "Previous Company Documents" ? (
                  // Special rendering for Previous Company Documents
                  category.companyGroups.map((companyGroup) => (
                    <Box key={companyGroup.companyIndex} sx={{ mb: 4, p: 2, border: '1px solid #eee', borderRadius: 1 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 'bold', mb: 2 }}>
                        {companyGroup.companyName}
                      </Typography>

                      {category.documents.map((docType) => {
                        const docsForType = companyGroup.documents.filter(
                          doc => doc.documentType === docType.type
                        );

                        return (
                          <Box key={docType.type} sx={{ mb: 3 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                                {docType.type.replace(/_/g, ' ')}
                                {docType.mandatory && ' *'}
                              </Typography>
                              <Chip
                                label={`${docsForType.length} uploaded`}
                                size="small"
                                color={docType.mandatory && docsForType.length === 0 ? "error" : "default"}
                                variant="outlined"
                                sx={{ ml: 2 }}
                              />
                            </Box>

                            {docsForType.length > 0 ? (
                              <Table size="small">
                                <TableHead>
                                  <TableRow>
                                    <TableCell>File Name</TableCell>
                                    <TableCell>Uploaded At</TableCell>
                                    <TableCell>Actions</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {docsForType.map((doc, idx) => (
                                    <TableRow key={idx}>
                                      <TableCell>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                          {doc.fileName?.endsWith('.pdf') ? (
                                            <PictureAsPdfIcon color="error" />
                                          ) : (
                                            <ImageIcon color="primary" />
                                          )}
                                          {doc.fileName}
                                        </Box>
                                      </TableCell>
                                      <TableCell>
                                        {doc.createdAt ? new Date(doc.createdAt).toLocaleString() : 'N/A'}
                                      </TableCell>
                                      <TableCell>
                                        <Button
                                          variant="outlined"
                                          startIcon={<VisibilityIcon />}
                                          onClick={() => handleViewDocument(doc)}
                                          sx={{ mr: 1 }}
                                        >
                                          View
                                        </Button>
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            ) : (
                              <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                                No documents uploaded for this type{docType.mandatory && ' (required)'}
                              </Typography>
                            )}
                          </Box>
                        );
                      })}
                    </Box>
                  ))
                ) : (
                  // Standard rendering for other categories
                  category.documents.map((docType) => (
                    <Box key={docType.type} sx={{ mb: 3 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                          {docType.type.replace(/_/g, ' ')}
                          {docType.mandatory && ' *'}
                        </Typography>
                        <Chip
                          label={`${docType.uploadedDocuments.length} uploaded`}
                          size="small"
                          color={docType.mandatory && docType.uploadedDocuments.length === 0 ? "error" : "default"}
                          variant="outlined"
                          sx={{ ml: 2 }}
                        />
                      </Box>

                      {docType.uploadedDocuments.length > 0 ? (
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>File Name</TableCell>
                              <TableCell>Uploaded At</TableCell>
                              <TableCell>Actions</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {docType.uploadedDocuments.map((doc, idx) => (
                              <TableRow key={idx}>
                                <TableCell>
                                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    {doc.fileName?.endsWith('.pdf') ? (
                                      <PictureAsPdfIcon color="error" />
                                    ) : (
                                      <ImageIcon color="primary" />
                                    )}
                                    {doc.fileName}
                                  </Box>
                                </TableCell>
                                <TableCell>
                                  {doc.createdAt ? new Date(doc.createdAt).toLocaleString() : 'N/A'}
                                </TableCell>
                                <TableCell>
                                  <Button
                                    variant="outlined"
                                    startIcon={<VisibilityIcon />}
                                    onClick={() => handleViewDocument(doc)}
                                    sx={{ mr: 1 }}
                                  >
                                    View
                                  </Button>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      ) : (
                        <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                          No documents uploaded for this type{docType.mandatory && ' (required)'}
                        </Typography>
                      )}
                    </Box>
                  ))
                )}

                {category.allowMultipleSets && category.title === "Previous Company Documents" && (
                  <Box sx={{ mt: 2, p: 2, backgroundColor: '#f5f5f5', borderRadius: 1 }}>
                    <Typography variant="body2">
                      This category contains documents from multiple previous companies
                    </Typography>
                  </Box>
                )}
              </AccordionDetails>
            </Accordion>
          ))}
        </CardContent>
      </Card>
    );
  };

  const renderReportInfo = () => (
    <Card elevation={3} sx={{ borderRadius: 3 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <SupervisorIcon color="primary" sx={{ mr: 1 }} /> Reporting Structure
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Reporting Manager</TableCell>
                  <TableCell>
                    {employee.report?.reportingManagerId && employee.report?.reportingManagerName ? (
                      <Chip
                        label={`${employee.report.reportingManagerId} - ${employee.report.reportingManagerName}`}
                        color="primary"
                      />
                    ) : (
                      "-"
                    )}
                  </TableCell>
                </TableRow>

                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Indirect Manager</TableCell>
                  <TableCell>
                    {employee.report?.indirectManager ? (
                      <Chip label={employee.report.indirectManager} color="secondary" variant="outlined" />
                    ) : "-"}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
          <Grid item xs={12} md={6}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>First Level Approver</TableCell>
                  <TableCell>{employee.report?.firstLevelApprover || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Second Level Approver</TableCell>
                  <TableCell>{employee.report?.secondLevelApprover || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Third Level Approver</TableCell>
                  <TableCell>{employee.report?.thirdLevelApprover || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
          <Grid item xs={12}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '20%' }}>Notes</TableCell>
                  <TableCell>
                    {employee.report?.note ? (
                      <Paper elevation={0} sx={{ p: 2, backgroundColor: theme.palette.grey[100], borderRadius: 1 }}>
                        {employee.report.note}
                      </Paper>
                    ) : "-"}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

  return (
    <>
      <PrintView />
      <Container maxWidth="lg" sx={{ py: 4 }}>
        {/* Employee Profile Header */}
        <Paper elevation={4} sx={{ mb: 4, p: 4, borderRadius: 3, background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)', position: 'relative' }}>
          <Box sx={{ position: 'absolute', top: 16, right: 16 }}>
            <IconButton
              onClick={handlePrint}
              sx={{
                backgroundColor: theme.palette.primary.main,
                color: theme.palette.primary.contrastText,
                '&:hover': {
                  backgroundColor: theme.palette.primary.dark
                }
              }}
            >
              <PrintIcon />
            </IconButton>
          </Box>
          <Box sx={{ display: "flex", alignItems: "center", flexDirection: { xs: 'column', sm: 'row' } }}>
            <Avatar
              alt={employee.personal?.firstName}
              sx={{
                width: 120,
                height: 120,
                mr: { sm: 4 },
                mb: { xs: 3, sm: 0 },
                fontSize: '3rem',
                bgcolor: theme.palette.primary.main
              }}
            >
              {employee.personal?.firstName?.charAt(0)}{employee.personal?.lastName?.charAt(0)}
            </Avatar>
            <Box sx={{ textAlign: { xs: 'center', sm: 'left' } }}>
              <Typography variant="h3" fontWeight="bold" gutterBottom>
                {employee.personal?.firstName} {employee.personal?.lastName}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: { xs: 'center', sm: 'flex-start' }, gap: 2 }}>
                <Chip
                  icon={<BadgeIcon />}
                  label={employee.personal?.empId}
                  color="primary"
                  variant="outlined"
                />
                <Chip
                  icon={<WorkIcon />}
                  label={employee.work?.jobTitle || 'No title'}
                  sx={{ backgroundColor: theme.palette.success.light }}
                />
                <Chip
                  icon={<WorkIcon />}
                  label={employee.work?.department || 'No department'}
                  sx={{ backgroundColor: theme.palette.info.light }}
                />
                <Chip
                  icon={<EmailIcon />}
                  label={employee.contact?.workEmail}
                  sx={{ backgroundColor: theme.palette.warning.light }}
                />
              </Box>
            </Box>
          </Box>
        </Paper>

        {/* Tabs Navigation */}
        <Paper elevation={2} sx={{ mb: 3, borderRadius: 3 }}>
          <Tabs
            value={activeTab}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            sx={{
              '& .MuiTabs-indicator': {
                height: 4,
                borderRadius: 2
              }
            }}
          >
            <Tab label="Personal" icon={<PersonIcon />} iconPosition="start" />
            <Tab label="Identification" icon={<AssignmentIcon />} iconPosition="start" />
            <Tab label="Work" icon={<WorkIcon />} iconPosition="start" />
            <Tab label="Contact" icon={<ContactIcon />} iconPosition="start" />
            <Tab label="Documents" icon={<PictureAsPdfIcon />} iconPosition="start" />
            <Tab label="Reporting" icon={<ReportIcon />} iconPosition="start" />
          </Tabs>
        </Paper>

        {/* Tab Content */}
        <Box sx={{ mb: 4 }}>
          {activeTab === 0 && renderPersonalInfo()}
          {activeTab === 1 && renderIdentificationInfo()}
          {activeTab === 2 && renderWorkInfo()}
          {activeTab === 3 && renderContactInfo()}
          {activeTab === 4 && renderDocumentInfo()}
          {activeTab === 5 && renderReportInfo()}
        </Box>

        {/* Back Button */}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            variant="contained"
            onClick={() => navigate(-1)}
            sx={{
              px: 4,
              py: 1.5,
              borderRadius: 2,
              fontWeight: 'bold',
              boxShadow: 2,
              '&:hover': {
                boxShadow: 4
              }
            }}
          >
            Back to Employee List
          </Button>
        </Box>
      </Container>

      {/* Print Dialog */}
      <Dialog
        open={openPrintDialog}
        onClose={handleClosePrintDialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">Print Employee Details</Typography>
          <IconButton onClick={handleClosePrintDialog}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Box sx={{ p: 2 }}>
            <Typography variant="body1" gutterBottom>
              You are about to print the details of <strong>{employee.personal?.firstName} {employee.personal?.lastName}</strong>.
            </Typography>
            <Typography variant="body1">
              Please ensure your printer is ready and configured properly.
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClosePrintDialog} sx={{ color: theme.palette.text.secondary }}>
            Cancel
          </Button>
          <Button
            onClick={handlePrintConfirm}
            variant="contained"
            startIcon={<PrintIcon />}
            sx={{ borderRadius: 2 }}
          >
            Print
          </Button>
        </DialogActions>
      </Dialog>
      {/* Document Viewer Dialog */}
      <DocumentViewerDialog />
    </>
  );
};

export default EmployeeDetails;