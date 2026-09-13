import React, { useState, useEffect, useContext, useRef } from 'react';
import {
  Box, Typography, Paper, Tabs, Tab, Divider,
  List, ListItem, ListItemText, Avatar, Chip,
  CircularProgress, Alert, Grid, Button,
  Container, Card, CardContent, Table, TableBody, TableCell, TableRow,
  IconButton, Dialog, DialogActions, DialogContent, DialogTitle,
  useTheme
} from '@mui/material';
import { userContext } from './context/ContextProvider';
import UserService from './service/UserService';
import axios from 'axios';
import {
  Person as PersonIcon,
  Email as EmailIcon,
  Badge as BadgeIcon,
  Work as WorkIcon,
  ContactPhone as ContactIcon,
  AssignmentInd as AssignmentIcon,
  Report as ReportIcon,
  CalendarToday as CalendarIcon,
  Home as HomeIcon,
  Phone as PhoneIcon,
  LocalHospital as HospitalIcon,
  SupervisorAccount as SupervisorIcon,
  AccessTime as TimeIcon,
  Public as CountryIcon,
  Fingerprint as FingerprintIcon,
  Print as PrintIcon,
  Close as CloseIcon
} from '@mui/icons-material';
import API_BASE_URL from './config/apiConfig';

const Profile = () => {
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState(0);
  const [openPrintDialog, setOpenPrintDialog] = useState(false);
  const theme = useTheme();
  const printRef = useRef();
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        setLoading(true);
        setError(null);

        const token = localStorage.getItem('token');
        const response = await axios.get(
          `${API_BASE_URL}/get-complete-profile`,
          {
            headers: { Authorization: `Bearer ${token}` },
            withCredentials: true,
          }
        );

        if (response.data.statusCode !== 200) {
          throw new Error(response.data.message || 'Failed to fetch profile data');
        }

        if (response.data.user?.empId !== response.data.employeeData?.personal?.empId) {
          throw new Error('Employee data mismatch detected');
        }

        setProfileData(response.data);
      } catch (err) {
        console.error('Profile fetch error:', err);
        setError(err.response?.data?.message || err.message || 'Failed to load profile');
      } finally {
        setLoading(false);
      }
    };

    fetchProfileData();
  }, []);

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

    window.location.reload();
    setOpenPrintDialog(false);
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      return new Date(dateString).toLocaleDateString();
    } catch {
      return dateString;
    }
  };

  const PrintView = () => (
    <div style={{ display: 'none' }}>
      <div ref={printRef}>
        <Box sx={{ p: 4, maxWidth: 800, margin: '0 auto' }}>
          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Typography variant="h4" sx={{ fontWeight: 'bold', mb: 1 }}>
              Employee Profile
            </Typography>
            <Typography variant="subtitle1" sx={{ color: theme.palette.text.secondary }}>
              {new Date().toLocaleDateString()}
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', mb: 4, p: 3, borderBottom: `1px solid ${theme.palette.divider}` }}>
            <Avatar
              sx={{
                width: 80,
                height: 80,
                mr: 3,
                fontSize: '2rem',
                bgcolor: theme.palette.primary.main,
                color: theme.palette.primary.contrastText
              }}
            >
              {profileData.user.name?.split(' ').map(n => n[0]).join('')}
            </Avatar>
            <Box>
              <Typography variant="h5" fontWeight="bold">
                {profileData.user.name}
              </Typography>
              <Typography variant="body1" sx={{ mt: 1 }}>
                <strong>Employee ID:</strong> {profileData.user.empId || '-'}
              </Typography>
              <Typography variant="body1">
                <strong>Email:</strong> {profileData.user.email || '-'}
              </Typography>
              <Typography variant="body1">
                <strong>Role:</strong> {profileData.user.role || '-'}
              </Typography>
            </Box>
          </Box>

          {profileData.employeeData?.personal && (
            <Box sx={{ mb: 4 }}>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
                Personal Information
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography><strong>First Name:</strong> {profileData.employeeData.personal.firstName || '-'}</Typography>
                  <Typography><strong>Middle Name:</strong> {profileData.employeeData.personal.middleName || '-'}</Typography>
                  <Typography><strong>Last Name:</strong> {profileData.employeeData.personal.lastName || '-'}</Typography>
                  <Typography><strong>Date of Birth:</strong> {formatDate(profileData.employeeData.personal.dateOfBirth) || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography><strong>Gender:</strong> {profileData.employeeData.personal.gender || '-'}</Typography>
                  <Typography><strong>Marital Status:</strong> {profileData.employeeData.personal.maritalStatus || '-'}</Typography>
                  <Typography><strong>Nationality:</strong> {profileData.employeeData.personal.nationality || '-'}</Typography>
                  <Typography><strong>Ethnicity:</strong> {profileData.employeeData.personal.ethnicity || '-'}</Typography>
                </Grid>
              </Grid>
            </Box>
          )}

          {profileData.employeeData?.work && (
            <Box sx={{ mb: 4 }}>
              <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
                Work Information
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography><strong>Employment Status:</strong> {profileData.employeeData.work.employmentStatus || '-'}</Typography>
                  <Typography><strong>Department:</strong> {profileData.employeeData.work.department || '-'}</Typography>
                  <Typography><strong>Job Title:</strong> {profileData.employeeData.work.jobTitle || '-'}</Typography>
                  <Typography><strong>Pay Grade:</strong> {profileData.employeeData.work.payGrade || '-'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography><strong>Date of Joining:</strong> {formatDate(profileData.employeeData.work.doj) || '-'}</Typography>
                  <Typography><strong>Termination Date:</strong> {formatDate(profileData.employeeData.work.terminationDate) || '-'}</Typography>
                  <Typography><strong>Time Zone:</strong> {profileData.employeeData.work.timeZone || '-'}</Typography>
                  <Typography><strong>Shift:</strong> {profileData.employeeData.work.shiftStartTime || '-'} to {profileData.employeeData.work.shiftEndTime || '-'}</Typography>
                </Grid>
              </Grid>
            </Box>
          )}

          {profileData.employeeData?.contact && (
            <>
              <Box sx={{ mb: 4 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
                  Contact Information
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography><strong>Work Email:</strong> {profileData.employeeData.contact.workEmail || '-'}</Typography>
                    <Typography><strong>Personal Email:</strong> {profileData.employeeData.contact.personalEmail || '-'}</Typography>
                    <Typography><strong>Mobile:</strong> {profileData.employeeData.contact.mobileNumber || '-'}</Typography>
                    <Typography><strong>Residential Address:</strong> {profileData.employeeData.contact.residentialAddress || '-'}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography><strong>City:</strong> {profileData.employeeData.contact.city || '-'}</Typography>
                    <Typography><strong>State:</strong> {profileData.employeeData.contact.state || '-'}</Typography>
                    <Typography><strong>Country:</strong> {profileData.employeeData.contact.country || '-'}</Typography>
                    <Typography><strong>Postal Code:</strong> {profileData.employeeData.contact.postalCode || '-'}</Typography>
                  </Grid>
                </Grid>
              </Box>

              <Box sx={{ mb: 4 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2, borderBottom: `2px solid ${theme.palette.primary.main}`, pb: 1 }}>
                  Emergency Contacts
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography><strong>Primary Contact:</strong> {profileData.employeeData.contact.primaryEmergencyContactName || '-'}</Typography>
                    <Typography><strong>Phone:</strong> {profileData.employeeData.contact.primaryEmergencyContactNumber || '-'}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography><strong>Secondary Contact:</strong> {profileData.employeeData.contact.secondaryEmergencyContactName || '-'}</Typography>
                    <Typography><strong>Phone:</strong> {profileData.employeeData.contact.secondaryEmergencyContactNumber || '-'}</Typography>
                  </Grid>
                </Grid>
              </Box>
            </>
          )}

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
            onClick={() => window.location.reload()}
            sx={{ mt: 3, px: 4, py: 1.5, borderRadius: 2 }}
          >
            Retry
          </Button>
        </Paper>
      </Container>
    );
  }

  if (!profileData) {
    return (
      <Container maxWidth="md" sx={{ mt: 4, textAlign: 'center' }}>
        <Paper elevation={3} sx={{ p: 4, borderRadius: 3 }}>
          <Typography variant="h5" gutterBottom>
            No profile data available
          </Typography>
          <Button
            variant="contained"
            onClick={() => window.location.reload()}
            sx={{ mt: 3, px: 4, py: 1.5, borderRadius: 2 }}
          >
            Retry
          </Button>
        </Paper>
      </Container>
    );
  }

  const { user, employeeData } = profileData;
  const { personal, identification, contact, work, report } = employeeData || {};

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
                      label={personal?.employmentStatus || "-"}
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Employee ID</TableCell>
                  <TableCell>
                    <Chip label={personal?.empId || "-"} variant="outlined" />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>First Name</TableCell>
                  <TableCell>{personal?.firstName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Middle Name</TableCell>
                  <TableCell>{personal?.middleName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Last Name</TableCell>
                  <TableCell>{personal?.lastName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Date of Birth</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CalendarIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {formatDate(personal?.dateOfBirth)}
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
                      label={personal?.gender || "-"}
                      color={personal?.gender?.toLowerCase() === 'male' ? 'primary' : 'secondary'}
                      size="small"
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Marital Status</TableCell>
                  <TableCell>{personal?.maritalStatus || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Nationality</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CountryIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {personal?.nationality || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Ethnicity</TableCell>
                  <TableCell>{personal?.ethnicity || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
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
                    <Chip label={work?.department || "-"} color="info" variant="outlined" />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Job Title</TableCell>
                  <TableCell>{work?.jobTitle || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Pay Grade</TableCell>
                  <TableCell>{work?.payGrade || "-"}</TableCell>
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
                    {formatDate(work?.doj)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Termination Date</TableCell>
                  <TableCell>
                    {work?.terminationDate ? (
                      <Box sx={{ display: 'flex', alignItems: 'center' }}>
                        <CalendarIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                        {formatDate(work?.terminationDate)}
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Time Zone</TableCell>
                  <TableCell>{work?.timeZone || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Shift</TableCell>
                  <TableCell>
                    {work?.shiftStartTime && work?.shiftEndTime ?
                      `${work.shiftStartTime} - ${work.shiftEndTime}` : "-"}
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
                  <TableCell>{contact?.residentialAddress || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Permanent</TableCell>
                  <TableCell>{contact?.permanentAddress || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>City</TableCell>
                  <TableCell>{contact?.city || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>State</TableCell>
                  <TableCell>{contact?.state || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Country</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <CountryIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {contact?.country || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Postal Code</TableCell>
                  <TableCell>{contact?.postalCode || "-"}</TableCell>
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
                    {contact?.workEmail || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Personal Email</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <EmailIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {contact?.personalEmail || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Mobile</TableCell>
                  <TableCell sx={{ display: 'flex', alignItems: 'center' }}>
                    <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                    {contact?.mobileNumber || "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Primary Emergency</TableCell>
                  <TableCell>
                    {contact?.primaryEmergencyContactName ? (
                      <Box>
                        {contact.primaryEmergencyContactName}
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                          <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                          {contact.primaryEmergencyContactNumber || "-"}
                        </Box>
                      </Box>
                    ) : "-"}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Secondary Emergency</TableCell>
                  <TableCell>
                    {contact?.secondaryEmergencyContactName ? (
                      <Box>
                        {contact.secondaryEmergencyContactName}
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                          <PhoneIcon color="action" sx={{ mr: 1, fontSize: '1rem' }} />
                          {contact.secondaryEmergencyContactNumber || "-"}
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
                      label={identification?.immigrationStatus || "-"}
                      color={identification?.immigrationStatus === 'Citizen' ? 'success' : 'warning'}
                      size="small"
                    />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Aadhar Card</TableCell>
                  <TableCell>{identification?.aadharCardNumber || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>Address Proof</TableCell>
                  <TableCell>{identification?.addressProof || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Address Document Name</TableCell>
                  <TableCell>{identification?.addressDocumentName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Address Document Number</TableCell>
                  <TableCell>{identification?.addressDocumentNumber || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
          <Grid item xs={12} md={6}>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Pan Card</TableCell>
                  <TableCell>{identification?.panCardNumber || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold", width: '40%' }}>ID Proof</TableCell>
                  <TableCell>{identification?.idProof || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Document Name</TableCell>
                  <TableCell>{identification?.documentName || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Document Number</TableCell>
                  <TableCell>{identification?.documentNumber || "-"}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

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
                    {report?.reportingManagerId && report?.reportingManagerName ? (
                      <Chip
                        label={`${report.reportingManagerId} - ${report.reportingManagerName}`}
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
                    {report?.indirectManager ? (
                      <Chip label={report.indirectManager} color="secondary" variant="outlined" />
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
                  <TableCell>{report?.firstLevelApprover || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Second Level Approver</TableCell>
                  <TableCell>{report?.secondLevelApprover || "-"}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>Third Level Approver</TableCell>
                  <TableCell>{report?.thirdLevelApprover || "-"}</TableCell>
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
                    {report?.note ? (
                      <Paper elevation={0} sx={{ p: 2, backgroundColor: theme.palette.grey[100], borderRadius: 1 }}>
                        {report.note}
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
        {/* Profile Header */}
        <Paper elevation={4} sx={{
          mb: 4,
          p: 4,
          borderRadius: 3,
          background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)',
          position: 'relative'
        }}>
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
              alt={user.name}
              sx={{
                width: 120,
                height: 120,
                mr: { sm: 4 },
                mb: { xs: 3, sm: 0 },
                fontSize: '3rem',
                bgcolor: theme.palette.primary.main
              }}
            >
              {user.name?.split(' ').map(n => n[0]).join('')}
            </Avatar>
            <Box sx={{ textAlign: { xs: 'center', sm: 'left' } }}>
              <Typography variant="h3" fontWeight="bold" gutterBottom>
                {user.name}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: { xs: 'center', sm: 'flex-start' }, gap: 2 }}>
                <Chip
                  icon={<BadgeIcon />}
                  label={user.empId}
                  color="primary"
                  variant="outlined"
                />
                <Chip
                  icon={<WorkIcon />}
                  label={work?.jobTitle || 'No title'}
                  sx={{ backgroundColor: theme.palette.success.light }}
                />
                <Chip
                  icon={<WorkIcon />}
                  label={work?.department || 'No department'}
                  sx={{ backgroundColor: theme.palette.info.light }}
                />
                <Chip
                  icon={<EmailIcon />}
                  label={user.email}
                  sx={{ backgroundColor: theme.palette.warning.light }}
                />
                {/* <Chip
                  label={user.role.toUpperCase()}
                  color={
                    user.role === 'admin' ? 'primary' :
                      user.role === 'hr' ? 'secondary' :
                        user.role === 'manager' ? 'success' :
                          user.role === 'supervisor' ? 'warning' : 'info'
                  }
                /> */}
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
            <Tab label="Work" icon={<WorkIcon />} iconPosition="start" />
            <Tab label="Contact" icon={<ContactIcon />} iconPosition="start" />
            <Tab label="Identification" icon={<AssignmentIcon />} iconPosition="start" />
            <Tab label="Reporting" icon={<ReportIcon />} iconPosition="start" />
          </Tabs>
        </Paper>

        {/* Tab Content */}
        <Box sx={{ mb: 4 }}>
          {activeTab === 0 && renderPersonalInfo()}
          {activeTab === 1 && renderWorkInfo()}
          {activeTab === 2 && renderContactInfo()}
          {activeTab === 3 && renderIdentificationInfo()}
          {activeTab === 4 && renderReportInfo()}
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
          <Typography variant="h6">Print Profile Details</Typography>
          <IconButton onClick={handleClosePrintDialog}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Box sx={{ p: 2 }}>
            <Typography variant="body1" gutterBottom>
              You are about to print the profile details of <strong>{user.name}</strong>.
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
    </>
  );
};

export default Profile;