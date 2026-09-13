import { useState, useEffect } from "react";
import {
  Paper,
  Typography,
  Grid,
  Box,
  Button,
  TextField,
  Chip,
  FormControlLabel,
  Switch,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Checkbox,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
  Alert,
  Autocomplete
} from "@mui/material";
import { ArrowBack, Send } from "@mui/icons-material";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig";
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const API = {
  GET_EMPLOYEES: "/api/employees/basic-info",
  GET_DRAFT_EMPLOYEES: "/api/timesheets/employees/with-draft-timesheets",
  SEND_EMAILS: "/api/emails/employee-reminders",
  GET_SETTINGS: "/api/notification-settings",
  SAVE_SETTINGS: "/api/notification-settings",
  GET_ROLES: "/employees/excludes-user-role"
};

const DAYS_OF_WEEK = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

const REMINDER_LEVELS = {
  1: "LEVEL_1",
  2: "LEVEL_2",
  3: "LEVEL_3"
};

const NotificationSettings = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const [employees, setEmployees] = useState([]);
  const [draftEmployees, setDraftEmployees] = useState([]);
  const [roles, setRoles] = useState([]);
  const [settings, setSettings] = useState({
    employeeReminders: [
      {
        enabled: true,
        level: "LEVEL_1",
        day: "SATURDAY",
        time: "23:59"      
      },
      {
        enabled: true,
        level: "LEVEL_2",
        day: "SUNDAY",
        time: "23:59"
      }
    ],
    supervisorReminders: [
      {
        enabled: true,
        level: "LEVEL_1",
        day: "TUESDAY",
        time: "14:00"
      },
      {
        enabled: true,
        level: "LEVEL_2",
        day: "WEDNESDAY",
        time: "14:00"
      }
    ],
    hrReminders: [
      {
        enabled: true,
        level: "LEVEL_1",
        day: "TUESDAY",
        time: "14:00",
        recipients: ["hr@infinevocloud.com"]
      },
      {
        enabled: true,
        level: "LEVEL_2",
        day: "WEDNESDAY",
        time: "14:00",
        recipients: ["hr@infinevocloud.com"]
      }
    ],
    escalationSettings: {
      enabled: true,
      day: "THURSDAY",
      time: "14:00",
      recipients: ["ceo@infinevocloud.com", "hr@infinevocloud.com"],
    },
    approvalReminders: [
      {
        enabled: true,
        level: "LEVEL_1",
        day: "TUESDAY",
        time: "12:00"
      },
      {
        enabled: true,
        level: "LEVEL_2",
        day: "WEDNESDAY",
        time: "12:00"
      },
      {
        enabled: true,
        level: "LEVEL_3",
        day: "FRIDAY",
        time: "12:00"
      }
    ]
  });

  const [newEscalationEmail, setNewEscalationEmail] = useState("");
  const [selectedRoles, setSelectedRoles] = useState(["ceo@infinevocloud.com", "hr@infinevocloud.com"]);
  const [sendDialogOpen, setSendDialogOpen] = useState(false);
  const [selectedEmployees, setSelectedEmployees] = useState([]);
  const [selectAllEmployees, setSelectAllEmployees] = useState(false);
  const [customEmail, setCustomEmail] = useState("");
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success"
  });
  const [hrNewEmails, setHrNewEmails] = useState({
    0: "", // For Level 1 HR reminder
    1: ""  // For Level 2 HR reminder
  });

  useEffect(() => {
    const fetchEmployees = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}${API.GET_EMPLOYEES}`);
        setEmployees(response.data);
        showSnackbar("Employees loaded successfully", "success");
      } catch (error) {
        console.error("Error fetching employees:", error);
        showSnackbar("Failed to load employees", "error");
      }
    };
    fetchEmployees();
  }, []);

  useEffect(() => {
    const fetchDraftEmployees = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}${API.GET_DRAFT_EMPLOYEES}`);
        const transformedData = response.data.map(user => ({
          name: user.name,
          email: user.email,
          empId: user.email // Using email as empId since UserDTO doesn't have empId
        }));
        setDraftEmployees(transformedData);
        showSnackbar("Employees with draft timesheets loaded successfully", "success");
      } catch (error) {
        console.error("Error fetching employees with draft timesheets:", error);
        showSnackbar("Failed to load employees with draft timesheets", "error");
      }
    };
    fetchDraftEmployees();
  }, []);

  useEffect(() => {
    const fetchNotificationSettings = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}${API.GET_SETTINGS}`);
        setSettings(response.data);
        showSnackbar("Fetching notification settings successfully", "success");
      } catch (error) {
        console.error("Error fetching notification settings:", error);
        showSnackbar("Error fetching notification settings", "error");
      }
    };
    fetchNotificationSettings();
  }, []);

  useEffect(() => {
    const fetchRoles = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}${API.GET_ROLES}`);
        const transformedRoles = response.data.map(user => ({
          name: user.role,
          email: user.email
        }));
        setRoles(transformedRoles);
        showSnackbar("Roles loaded successfully", "success");
      } catch (error) {
        console.error("Error fetching roles:", error);
        showSnackbar("Failed to load roles", "error");
      }
    };
    fetchRoles();
  }, []);

  const handleSettingChange = (section, field) => (event) => {
    setSettings((prev) => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: event.target.checked,
      },
    }));
  };

  const handleTimeChange = (field, value) => {
    setSettings(prev => ({
      ...prev,
      escalationSettings: {
        ...prev.escalationSettings,
        [field]: value
      }
    }));
  };
 
  const updateReminder = (type, index, field, value) => {
    const updatedReminders = [...settings[type]];
    updatedReminders[index][field] = value;
    setSettings({
      ...settings,
      [type]: updatedReminders
    });
  };

  const handleHrEmailChange = (index, value) => {
    setHrNewEmails(prev => ({ ...prev, [index]: value }));
  };

  const handleAddHrEmail = (type, index) => {
    const newEmail = hrNewEmails[index];
    if (newEmail && !settings[type][index].recipients.includes(newEmail)) {
      updateReminder(type, index, 'recipients', [...settings[type][index].recipients, newEmail]);
      setHrNewEmails(prev => ({ ...prev, [index]: "" }));
    }
  };

  const handleRemoveHrEmail = (type, index, email) => {
    updateReminder(
      type,
      index,
      'recipients',
      settings[type][index].recipients.filter(e => e !== email)
    );
  };

  const handleSelectAllEmployees = () => {
    if (selectAllEmployees) {
      // Unselect all
      setSelectedEmployees([]);
    } else {
      // Select all
      const allEmployeeIds = draftEmployees.map(emp => emp.empId);
      setSelectedEmployees(allEmployeeIds);
    }
    setSelectAllEmployees(!selectAllEmployees);
  };

  const renderRecipientsSelector = (type, index) => {
    return (
      <Box sx={{ mt: 2, mb: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Recipients:
        </Typography>
       
        <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
          {settings[type][index].recipients.map((email) => (
            <Chip
              key={email}
              label={email}
              onDelete={() => handleRemoveHrEmail(type, index, email)}
              disabled={!settings[type][index].enabled}
            />
          ))}
        </Box>
       
        <Box sx={{ display: "flex", gap: 2 }}>
          <TextField
            size="small"
            placeholder="Add custom email address"
            value={hrNewEmails[index] || ''}
            onChange={(e) => handleHrEmailChange(index, e.target.value)}
            disabled={!settings[type][index].enabled}
            sx={{ flexGrow: 1 }}
          />
          <Button
            variant="outlined"
            onClick={() => handleAddHrEmail(type, index)}
            disabled={!settings[type][index].enabled || !hrNewEmails[index]}
          >
            Add
          </Button>
        </Box>
      </Box>
    );
  };

  const handleAddEscalationEmail = () => {
    if (newEscalationEmail && !settings.escalationSettings.recipients.includes(newEscalationEmail)) {
      setSettings(prev => ({
        ...prev,
        escalationSettings: {
          ...prev.escalationSettings,
          recipients: [...prev.escalationSettings.recipients, newEscalationEmail],
        },
      }));
      setNewEscalationEmail("");
    }
  };

  const handleRemoveEscalationEmail = (email) => {
    setSettings(prev => ({
      ...prev,
      escalationSettings: {
        ...prev.escalationSettings,
        recipients: prev.escalationSettings.recipients.filter(e => e !== email),
      },
    }));
    setSelectedRoles(prev => prev.filter(e => e !== email));
  };

  const handleRoleSelectionChange = (event) => {
    const value = event.target.value;
    const updatedRoles = typeof value === 'string' ? value.split(',') : value;
    setSelectedRoles(updatedRoles);
    setSettings(prev => ({
      ...prev,
      escalationSettings: {
        ...prev.escalationSettings,
        recipients: updatedRoles,
      }
    }));
  };

  const handleSendEscalationEmail = () => {
    setSendDialogOpen(true);
  };

  const handleCloseSendDialog = () => {
    setSendDialogOpen(false);
    setSelectedEmployees([]);
    setSelectAllEmployees(false);
    setCustomEmail("");
  };

  const handleSendEmail = async () => {
    try {
      const selectedEmails = draftEmployees
        .filter(emp => selectedEmployees.includes(emp.empId))
        .map(emp => ({
          email: emp.email,
          empId: emp.empId,
          name: emp.name
        }));

      if (customEmail) {
        selectedEmails.push({
          email: customEmail,
          empId: `custom-${Date.now()}`,
          name: customEmail.split('@')[0]
        });
      }

      if (selectedEmails.length === 0) {
        showSnackbar("Please select at least one recipient", "error");
        return;
      }

      const response = await axios.post(`${API_BASE_URL}${API.SEND_EMAILS}`, {
        to: selectedEmails,
        cc: settings.escalationSettings.recipients,
        subject: "Timesheet Reminder",
        messageBody: escalationEmailTemplate(selectedEmails[0].name)
      });

      if (response.status === 200) {
        showSnackbar(`Escalation emails sent to ${selectedEmails.length} recipient(s)`, "success");
        handleCloseSendDialog();
      } else {
        throw new Error("Failed to send emails");
      }
    } catch (error) {
      console.error("Error sending escalation email:", error);
      showSnackbar("Failed to send escalation emails", "error");
    }
  };

  const handleSave = async () => {
    try {
      const payload = {
        employeeReminders: settings.employeeReminders,
        supervisorReminders: settings.supervisorReminders,
        hrReminders: settings.hrReminders,
        approvalReminders: settings.approvalReminders,
        escalationSettings: settings.escalationSettings
      };

      const response = await axios.post(`${API_BASE_URL}${API.SAVE_SETTINGS}`, payload);

      if (response.status === 200) {
        showSnackbar("Settings saved successfully", "success");
      } else {
        throw new Error("Failed to save settings");
      }
    } catch (error) {
      console.error("Error saving settings:", error);
      showSnackbar("Failed to save settings", "error");
    }
  };

  const showSnackbar = (message, severity) => {
    setSnackbar({ open: true, message, severity });
  };

  const handleSnackbarClose = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  const escalationEmailTemplate = () => `
Dear Team,

This is an urgent notification regarding your overdue timesheet submission.

This matter has been escalated to the following management team members:
${settings.escalationSettings.recipients.map(email => `- ${email}`).join('\n')}

Required Actions:
1. Submit your timesheet immediately through the employee portal
2. Reply to this email to confirm submission
3. Contact your supervisor if you encounter any issues

Consequences of non-compliance:
- Immediate payroll processing delays
- Formal disciplinary action
- Further escalation to senior leadership

The deadline for resolution is ${settings.escalationSettings.day} at ${settings.escalationSettings.time}.

Sincerely,  
Timesheet Compliance Team
`;

  const renderDateTimeSelectors = (type, index, defaultDay, defaultTime) => (
    <Box sx={{ display: "flex", gap: 2, mt: 2, mb: 2, flexWrap: 'wrap' }}>
      <FormControl size="small" sx={{ minWidth: 120 }}>
        <InputLabel>Day</InputLabel>
        <Select
          value={settings[type][index].day}
          onChange={(e) => updateReminder(type, index, 'day', e.target.value)}
          disabled={!settings[type][index].enabled}
        >
          {DAYS_OF_WEEK.map(day => (
            <MenuItem key={day} value={day}>{day}</MenuItem>
          ))}
        </Select>
      </FormControl>
      <TextField
        size="small"
        label="Time"
        value={settings[type][index].time}
        onChange={(e) => updateReminder(type, index, 'time', e.target.value)}
        disabled={!settings[type][index].enabled}
        sx={{ minWidth: 120 }}
        placeholder="HH:mm"
      />
    </Box>
  );

  return (
    <Paper elevation={0} sx={{ p: 4, width: "100%", maxWidth: 1200, mx: "auto", borderRadius: 3 }}>
      <Button
        startIcon={<ArrowBack />}
        onClick={() => navigate(`/${role}/timesheets`)}
        sx={{ mb: 3 }}
      >
        Back to Timesheets
      </Button>

      <Typography variant="h5" gutterBottom sx={{ fontWeight: "bold" }}>
        Timesheet Notification Settings
      </Typography>

      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Configure automatic reminders and escalation emails for timesheet submission and approval
      </Typography>

      {/* Employee Reminders Section */}
      <Typography variant="h6" gutterBottom>
        Employee Reminders
      </Typography>
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {settings.employeeReminders.map((reminder, index) => (
          <Grid item xs={12} md={6} key={`employee-reminder-${index}`}>
            <FormControlLabel
              control={
                <Switch
                  checked={reminder.enabled}
                  onChange={(e) => updateReminder('employeeReminders', index, 'enabled', e.target.checked)}
                />
              }
              label={`Employee Reminder ${reminder.level.replace('_', ' ')}`}
            />
            <Box sx={{ ml: 4 }}>
              {renderDateTimeSelectors(
                'employeeReminders',
                index,
                index === 0 ? "SATURDAY" : "SUNDAY",
                "23:59"
              )}
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* Supervisor Reminders Section */}
      <Typography variant="h6" gutterBottom>
        Respective Project Manager Reminders
      </Typography>
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {settings.supervisorReminders.map((reminder, index) => (
          <Grid item xs={12} md={6} key={`supervisor-reminder-${index}`}>
            <FormControlLabel
              control={
                <Switch
                  checked={reminder.enabled}
                  onChange={(e) => updateReminder('supervisorReminders', index, 'enabled', e.target.checked)}
                />
              }
              label={`Manager Reminder ${reminder.level.replace('_', ' ')}`}
            />
            <Box sx={{ ml: 4 }}>
              {renderDateTimeSelectors(
                'supervisorReminders',
                index,
                index === 0 ? "Tuesday" : "Wednesday",
                "14:00"
              )}
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* HR Reminders Section - Only shows 2 reminders */}
      <Typography variant="h6" gutterBottom>
        HR Reminders
      </Typography>
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {settings.hrReminders.slice(0, 2).map((reminder, index) => (
          <Grid item xs={12} md={6} key={`hr-reminder-${index}`}>
            <FormControlLabel
              control={
                <Switch
                  checked={reminder.enabled}
                  onChange={(e) => updateReminder('hrReminders', index, 'enabled', e.target.checked)}
                />
              }
              label={`HR Reminder ${reminder.level.replace('_', ' ')}`}
            />
            <Box sx={{ ml: 4 }}>
              {renderDateTimeSelectors(
                'hrReminders',
                index,
                index === 0 ? "Tuesday" : "Wednesday",
                "14:00"
              )}
              {renderRecipientsSelector('hrReminders', index)}
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* Approval Reminders Section */}
      <Typography variant="h6" gutterBottom>
        Timesheet Approval Reminders
      </Typography>
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {settings.approvalReminders.map((reminder, index) => (
          <Grid item xs={12} md={4} key={`approval-reminder-${index}`}>
            <FormControlLabel
              control={
                <Switch
                  checked={reminder.enabled}
                  onChange={(e) => updateReminder('approvalReminders', index, 'enabled', e.target.checked)}
                />
              }
              label={`Approval ${reminder.level.replace('_', ' ')}`}
            />
            <Box sx={{ ml: 4 }}>
              {renderDateTimeSelectors(
                'approvalReminders',
                index,
                index === 0 ? "Tuesday" : index === 1 ? "Wednesday" : "Friday",
                "12:00"
              )}
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* Escalation Settings Section */}
      <Typography variant="h6" gutterBottom>
        Escalation Settings
      </Typography>
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12}>
          <FormControlLabel
            control={
              <Switch
                checked={settings.escalationSettings.enabled}
                onChange={handleSettingChange("escalationSettings", "enabled")}
              />
            }
            label="Enable Escalation"
          />
          <Box sx={{ display: "flex", gap: 2, mt: 2, ml: 4, flexWrap: 'wrap' }}>
            <FormControl size="small" sx={{ minWidth: 120, mb: 2 }}>
              <InputLabel>Day</InputLabel>
              <Select
                value={settings.escalationSettings.day}
                onChange={(e) => handleTimeChange("day", e.target.value)}
                disabled={!settings.escalationSettings.enabled}
              >
                {DAYS_OF_WEEK.map(day => (
                  <MenuItem key={day} value={day}>{day}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="Time"
              value={settings.escalationSettings.time}
              onChange={(e) => handleTimeChange("time", e.target.value)}
              disabled={!settings.escalationSettings.enabled}
              sx={{ minWidth: 120, mb: 2 }}
              placeholder="HH:mm"
            />
          </Box>
        </Grid>
        <Grid item xs={12}>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            Escalation Recipients (CC)
          </Typography>
         
          <FormControl fullWidth size="small" sx={{ mb: 2 }}>
            <InputLabel>Select Roles</InputLabel>
            <Select
              multiple
              value={selectedRoles}
              onChange={handleRoleSelectionChange}
              disabled={!settings.escalationSettings.enabled}
              renderValue={(selected) => selected.map(email => {
                const role = roles.find(r => r.email === email);
                return role ? role.name : email;
              }).join(', ')}
            >
              {roles.map((role) => (
                <MenuItem key={role.email} value={role.email}>
                  <Checkbox checked={selectedRoles.indexOf(role.email) > -1} />
                  <ListItemText primary={`${role.name} (${role.email})`} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>
         
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
            {settings.escalationSettings.recipients.map((email) => {
              const role = roles.find(r => r.email === email);
              return (
                <Chip
                  key={email}
                  label={role ? `${role.name} (${email})` : email}
                  onDelete={() => handleRemoveEscalationEmail(email)}
                  disabled={!settings.escalationSettings.enabled}
                />
              );
            })}
          </Box>
         
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Or add custom email:
          </Typography>
          <Box sx={{ display: "flex", gap: 2 }}>
            <TextField
              size="small"
              placeholder="Add custom email address"
              value={newEscalationEmail}
              onChange={(e) => setNewEscalationEmail(e.target.value)}
              disabled={!settings.escalationSettings.enabled}
              sx={{ flexGrow: 1 }}
            />
            <Button
              variant="outlined"
              onClick={handleAddEscalationEmail}
              disabled={!settings.escalationSettings.enabled || !newEscalationEmail}
            >
              Add
            </Button>
          </Box>
        </Grid>
      </Grid>

      {/* Escalation Email Section */}
      <Typography variant="h6" gutterBottom>
        Send Escalation Email
      </Typography>
      <Box sx={{ mb: 4 }}>
        <Button
          variant="contained"
          startIcon={<Send />}
          onClick={handleSendEscalationEmail}
          disabled={!settings.escalationSettings.enabled}
        >
          Send Escalation Email
        </Button>
      </Box>

      {/* Save Settings Button */}
      <Divider sx={{ my: 3 }} />
      <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="contained"
          onClick={handleSave}
          sx={{ minWidth: 120 }}
        >
          Save Settings
        </Button>
      </Box>

      {/* Send Escalation Email Dialog */}
      <Dialog open={sendDialogOpen} onClose={handleCloseSendDialog} maxWidth="md" fullWidth>
        <DialogTitle>Send Escalation Email</DialogTitle>
        <DialogContent>
          <Typography variant="subtitle1" sx={{ mb: 2 }}>
            Select employees with not-submitted timesheets to escalate:
          </Typography>
         
          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Employees</InputLabel>
            <Select
              multiple
              value={selectedEmployees}
              onChange={(e) => {
                // Check if the "Select All" option was clicked
                if (e.target.value.includes("select-all")) {
                  handleSelectAllEmployees();
                } else {
                  setSelectedEmployees(e.target.value);
                  setSelectAllEmployees(e.target.value.length === draftEmployees.length);
                }
              }}
              renderValue={(selected) => {
                // Don't show "select-all" in the rendered value
                const filteredSelected = selected.filter(id => id !== "select-all");
                return filteredSelected.map(id => {
                  const emp = draftEmployees.find(e => e.empId === id);
                  return emp ? emp.name : id;
                }).join(', ');
              }}
            >
              {/* Add the Select All option at the top */}
              <MenuItem value="select-all">
                <Checkbox
                  checked={selectAllEmployees}
                  indeterminate={
                    selectedEmployees.length > 0 && 
                    selectedEmployees.length < draftEmployees.length
                  }
                />
                <ListItemText primary={selectAllEmployees ? "Unselect All" : "Select All"} />
              </MenuItem>
              
              {/* Regular employee list */}
              {Array.isArray(draftEmployees) &&
                draftEmployees.map((employee) => (
                  <MenuItem key={employee.empId} value={employee.empId}>
                    <Checkbox checked={selectedEmployees.indexOf(employee.empId) > -1} />
                    <ListItemText primary={`${employee.name} (${employee.email})`} />
                  </MenuItem>
                ))}
            </Select>
          </FormControl>

          <Typography variant="subtitle1" sx={{ mb: 2, mt: 3 }}>
            Or enter custom email:
          </Typography>
          <TextField
            fullWidth
            size="small"
            placeholder="Enter email address"
            value={customEmail}
            onChange={(e) => setCustomEmail(e.target.value)}
            sx={{ mb: 3 }}
          />
         
          <Paper elevation={2} sx={{ p: 3, backgroundColor: '#f9f9f9' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Email Preview
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Typography variant="body2" sx={{ mb: 1 }}>
              <strong>To:</strong> {selectedEmployees.length > 0
                ? draftEmployees.find(e => e.empId === selectedEmployees[0])?.email || customEmail
                : customEmail || "No recipient selected"}
            </Typography>
            <Typography variant="body2" sx={{ mb: 2 }}>
              <strong>CC:</strong> {settings.escalationSettings.recipients.join(', ')}
            </Typography>
            <Typography variant="body2" whiteSpace="pre-wrap">
              {selectedEmployees.length > 0 || customEmail
                ? escalationEmailTemplate(
                    selectedEmployees.length > 0
                      ? draftEmployees.find(e => e.empId === selectedEmployees[0])?.name
                      : customEmail.split('@')[0]
                  )
                : "Select an employee or enter email to preview"}
            </Typography>
          </Paper>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseSendDialog}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSendEmail}
            disabled={selectedEmployees.length === 0 && !customEmail}
            startIcon={<Send />}
          >
            Send Escalation
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar for notifications */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert
          onClose={handleSnackbarClose}
          severity={snackbar.severity}
          sx={{ width: "100%" }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Paper>
  );
};

export default NotificationSettings;