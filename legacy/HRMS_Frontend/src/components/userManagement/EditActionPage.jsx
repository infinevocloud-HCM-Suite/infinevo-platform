import React, { useState, useEffect } from 'react';
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
import { HelpOutline, ArrowBack } from '@mui/icons-material';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import  { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const EditActionPage = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();
  const { actionId } = useParams();

  const [formData, setFormData] = useState({
    actionName: '',
    description: '',
    alias: ''
  });

  const [errors, setErrors] = useState({
    actionName: false,
    description: false
  });

  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'success'
  });

  useEffect(() => {
    const fetchAction = async () => {
      try {
        const res = await axios.get(`${API_BASE_URL}/auth/actions/${actionId}`);
        setFormData({
          actionName: res.data.actionName || '',
          alias: res.data.alias || '',
          description: res.data.description || ''
        });
      } catch (err) {
        console.error('Failed to fetch action:', err);
        navigate(`/${role}/list-actions`, { state: { error: 'Failed to load action' } });
      }
    };

    fetchAction();
  }, [actionId, navigate]);

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
    let valid = true;
    const newErrors = {
      actionName: formData.actionName.trim() === '',
      description: formData.description.trim() === ''
    };
    setErrors(newErrors);
    return !newErrors.actionName && !newErrors.description;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    try {
      await axios.put(`${API_BASE_URL}/auth/actions/${actionId}`, formData);
      setNotification({
        open: true,
        message: 'Action updated successfully!',
        severity: 'success'
      });
      setTimeout(() => {
        navigate(`/${role}/list-actions`, {
          state: { success: 'Action updated successfully!' }
        });
      }, 1500);
    } catch (err) {
      console.error('Error updating action:', err);
      setNotification({
        open: true,
        message: 'Failed to update action.',
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
            Edit Action
          </Typography>
        </Box>

        <Divider sx={{ mb: 3 }} />

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
                Action Details
              </Typography>
            </Grid>

            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Action Name"
                name="actionName"
                value={formData.actionName}
                onChange={handleChange}
                error={errors.actionName}
                helperText={errors.actionName ? 'Action name is required' : ''}
                variant="outlined"
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <Tooltip title="Enter a unique name for this action">
                        <HelpOutline color="action" />
                      </Tooltip>
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>

            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Alias"
                name="alias"
                value={formData.alias}
                onChange={handleChange}
                variant="outlined"
              />
            </Grid>

            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Description"
                name="description"
                value={formData.description}
                onChange={handleChange}
                error={errors.description}
                helperText={errors.description ? 'Description is required' : ''}
                variant="outlined"
                multiline
                rows={4}
              />
            </Grid>

            <Grid item xs={12}>
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
                <Button variant="outlined" onClick={() => navigate(`/${role}/list-actions`)}>
                  Cancel
                </Button>
                <Button type="submit" variant="contained" size="large" sx={{ px: 4 }}>
                  Update Action
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
        <Alert onClose={handleCloseNotification} severity={notification.severity} sx={{ width: '100%' }}>
          {notification.message}
        </Alert>
      </Snackbar>
    </Container>
  );
};

export default EditActionPage;
