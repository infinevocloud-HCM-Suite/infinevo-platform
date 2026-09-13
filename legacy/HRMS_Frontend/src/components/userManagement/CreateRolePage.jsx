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
  Alert,
  CircularProgress
} from '@mui/material';
import { BadgeOutlined, HelpOutline, ArrowBack } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import  { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
 
const CreateRolePage = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    roleName: '',
    description: ''
  });
  const [errors, setErrors] = useState({
    roleName: false,
    description: false
  });
  const [loading, setLoading] = useState(false);
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
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: false
      }));
    }
  };
 
  const validateForm = () => {
    const newErrors = {
      roleName: formData.roleName.trim() === '',
      description: false // Making description optional
    };
 
    setErrors(newErrors);
    return !newErrors.roleName;
  };
 
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) {
      setNotification({
        open: true,
        message: 'Role Name is required',
        severity: 'error'
      });
      return;
    }
 
    try {
      setLoading(true);
      const response = await axios.post(`${API_BASE_URL}/roles`, {
        roleName: formData.roleName.trim(),
        description: formData.description.trim()
      });
 
      if (response.status === 201) {
        setNotification({
          open: true,
          message: 'Role created successfully!',
          severity: 'success'
        });
        setFormData({
          roleName: '',
          description: ''
        });
        setTimeout(() => {
          navigate(`/${role}/list-roles`, { state: { success: 'Role created successfully!' } });
        }, 1500);
      }
    } catch (error) {
      console.error('Error creating role:', error);
      let errorMessage = 'Failed to create role';
      if (error.response) {
        if (error.response.status === 400) {
          errorMessage = error.response.data || 'Validation error';
        } else if (error.response.status === 409) {
          errorMessage = 'Role with this name already exists';
        }
      }
      setNotification({
        open: true,
        message: errorMessage,
        severity: 'error'
      });
    } finally {
      setLoading(false);
    }
  };
 
  const handleCloseNotification = () => {
    setNotification(prev => ({ ...prev, open: false }));
  };
 
  return (
<Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
<Paper elevation={3} sx={{ p: 3 }}>
<Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
<IconButton onClick={() => navigate(`/${role}/list-roles`)} sx={{ mr: 2 }}>
<ArrowBack />
</IconButton>
<Typography variant="h5" component="h1" sx={{ fontWeight: 'bold' }}>
            Create Role
</Typography>
</Box>
<Divider sx={{ mb: 3 }} />
<form onSubmit={handleSubmit}>
<Grid container spacing={3}>
<Grid item xs={12}>
<Typography variant="h6" component="h2" sx={{ mb: 2, display: 'flex', alignItems: 'center' }}>
<BadgeOutlined color="primary" sx={{ mr: 1 }} />
                Role Details
</Typography>
</Grid>
<Grid item xs={12} md={6}>
<TextField
                fullWidth
                label="Role name *"
                name="roleName"
                value={formData.roleName}
                onChange={handleChange}
                error={errors.roleName}
                helperText={errors.roleName ? 'Role name is required' : ''}
                variant="outlined"
                InputProps={{
                  endAdornment: (
<InputAdornment position="end">
<Tooltip title="Enter a unique name for this role">
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
                helperText="Describe the purpose and permissions of this role (optional)"
              />
</Grid>
<Grid item xs={12}>
<Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
<Button
                  variant="outlined"
                  onClick={() => navigate(`/${role}/list-roles`)}
                  disabled={loading}
>
                  Cancel
</Button>
<Button
                  type="submit"
                  variant="contained"
                  size="large"
                  sx={{ px: 4 }}
                  disabled={loading}
>
                  {loading ? <CircularProgress size={24} /> : 'Create Role'}
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
 
export default CreateRolePage;