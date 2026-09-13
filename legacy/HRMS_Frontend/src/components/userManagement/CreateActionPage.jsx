import React, { useState } from 'react';
import {
  Box,
  Typography,
  TextField,
  Button,
  Paper,
  Grid,
  Container,
  InputAdornment,
  IconButton,
  Tooltip,
  Divider,
  Snackbar,
  Alert
} from '@mui/material';
import { AddCircleOutline, HelpOutline, ArrowBack } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import  { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const CreateActionPage = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    actionName: '',
    description: ''
  });
  const [errors, setErrors] = useState({
    actionName: false,
    description: false
  });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'success'
  });
 
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
   
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: false
      }));
    }
  };
 
  const validateForm = () => {
    const newErrors = {
      actionName: formData.actionName.trim() === '',
      description: formData.description.trim() === ''
    };
 
    setErrors(newErrors);
    return !newErrors.actionName && !newErrors.description;
  };

  const token = localStorage.getItem("token");
 
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitAttempted(true);
   
    if (!validateForm()) {
      setNotification({
        open: true,
        message: 'Please fill all required fields',
        severity: 'error'
      });
      return;
    }
 
    try {
      const response = await axios.post(`${API_BASE_URL}/auth/actions`, {
        actionName: formData.actionName.trim(),
        description: formData.description.trim()
      },{
        headers: { Authorization: `Bearer ${token}` },
        withCredentials: true,
    });
 
      if (response.status === 201) {
        setNotification({
          open: true,
          message: 'Action created successfully!',
          severity: 'success'
        });
       
        // Reset form
        setFormData({
          actionName: '',
          description: ''
        });
       
        // Navigate back after a short delay
        setTimeout(() => {
          navigate(`/${role}/list-actions`, { state: { success: 'Action created successfully!' } });
        }, 500);
      }
    } catch (error) {
      console.error('Error saving action:', error);
      let errorMessage = 'Failed to create action';
     
      if (error.response) {
        if (error.response.status === 400) {
          errorMessage = error.response.data || 'Validation error';
        } else if (error.response.status === 409) {
          errorMessage = 'Action with this name already exists';
        }
      }
     
      setNotification({
        open: true,
        message: errorMessage,
        severity: 'error'
      });
    }
  };
 
  const handleCloseNotification = () => {
    setNotification(prev => ({ ...prev, open: false }));
  };
 
  return (
    <Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
      <Paper elevation={3} sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
          <IconButton onClick={() => navigate(`/${role}/list-actions`)} sx={{ mr: 2 }}>
            <ArrowBack />
          </IconButton>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 'bold' }}>
            Create Action
          </Typography>
        </Box>
       
        <Divider sx={{ mb: 3 }} />
       
        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Typography variant="h6" component="h2" sx={{ mb: 2, display: 'flex', alignItems: 'center' }}>
                <AddCircleOutline color="primary" sx={{ mr: 1 }} />
                Action Details
              </Typography>
            </Grid>
           
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Action Name "
                name="actionName"
                required
                autoFocus
                value={formData.actionName}
                onChange={handleChange}
                error={submitAttempted && errors.actionName}
                helperText={
                  submitAttempted && errors.actionName
                    ? 'Action name is required'
                    : 'Enter a unique name for this action'
                }
                variant="outlined"
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <Tooltip title="Must be unique">
                        <HelpOutline color="action" />
                      </Tooltip>
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
           
            <Grid item xs={12}>
            <TextField
  fullWidth
  label="Description"
  name="description"
  value={formData.description}
  onChange={handleChange}
  variant="outlined"
  multiline
  rows={4}
  helperText="Provide a detailed description of this action (optional)"
/>
            </Grid>
           
            <Grid item xs={12}>
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
                <Button
                  variant="outlined"
                  onClick={() => navigate(`/${role}/list-actions`)}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  sx={{ px: 4 }}
                  startIcon={<AddCircleOutline />}
                >
                  Create Action
                </Button>
              </Box>
            </Grid>
          </Grid>
        </form>
      </Paper>
 
      <Snackbar
        open={notification.open}
        autoHideDuration={6000}
        onClose={handleCloseNotification}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert
          onClose={handleCloseNotification}
          severity={notification.severity}
          sx={{ width: '100%' }}
        >
          {notification.message}
        </Alert>
      </Snackbar>
    </Container>
  );
};
   
export default CreateActionPage;