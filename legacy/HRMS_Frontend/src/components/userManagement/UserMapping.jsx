import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Box,
  Typography,
  Button,
  Paper,
  Container,
  Checkbox,
  FormControlLabel,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Grid,
  Divider,
  Chip,
  Snackbar,
  Alert,
  IconButton
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ArrowBack from '@mui/icons-material/ArrowBack';
import API_BASE_URL from '../config/apiConfig';

const UserMapping = () => {
  const { userId, name } = useParams();
  const username = localStorage.getItem('username');
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [actions, setActions] = useState([]);
  const [selectedPermissions, setSelectedPermissions] = useState({});
  const [loading, setLoading] = useState(false);
  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'success'
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const token = localStorage.getItem('token');
        if (!token) {
          navigate('/login');
          return;
        }

         // Fetch user data first
        // const userResponse = await axios.get(`${API_BASE_URL}/common/get-complete-profile`, {
        //   headers: { Authorization: `Bearer ${token}` }
        // });

        // console.log('User profile response:', userResponse.data);

        // setUser(userResponse.data.user || userResponse.data);

        // Fetch all data in parallel
        const [rolesResponse, actionsResponse, userPermissionsResponse] = await Promise.all([
          axios.get(`${API_BASE_URL}/roles`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          axios.get(`${API_BASE_URL}/auth/actions`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
          axios.get(`${API_BASE_URL}/users/${userId}/actions`, {
            headers: { Authorization: `Bearer ${token}` }
          }),
        ]);

        setRoles(rolesResponse.data);
        setActions(actionsResponse.data);

        // Initialize selected permissions structure
        const initialPermissions = {};
        
        // Fetch actions for each role and initialize permissions
        const roleActionsPromises = rolesResponse.data.map(async (role) => {
          const roleActionsResponse = await axios.get(
            `${API_BASE_URL}/roles/getActions/${role.roleId}`,
            { headers: { Authorization: `Bearer ${token}` } }
          );
          
          initialPermissions[role.roleId] = {};
          roleActionsResponse.data.forEach(action => {
            const isAssigned = userPermissionsResponse.data.some(
              perm => perm.roleId === role.roleId && perm.actionId === action.actionId
            );
            initialPermissions[role.roleId][action.actionId] = isAssigned;
          });
        });

        await Promise.all(roleActionsPromises);
        setSelectedPermissions(initialPermissions);
      } catch (error) {
        console.error('Failed to fetch data', error);
        setNotification({
          open: true,
          message: 'Failed to load data',
          severity: 'error'
        });
      }
    };

    fetchData();
  }, [userId, navigate]);

  const handleCheckboxChange = (roleId, actionId) => {
    setSelectedPermissions(prev => ({
      ...prev,
      [roleId]: {
        ...prev[roleId],
        [actionId]: !prev[roleId]?.[actionId]
      }
    }));
  };

  const handleRoleToggle = (roleId, isSelected) => {
    setSelectedPermissions(prev => {
      const updated = { ...prev };
      const role = roles.find(r => r.roleId === roleId);
      
      if (!role) return prev;
      
      // Get all actions for this role
      const roleActions = actions.filter(action => 
        selectedPermissions[roleId]?.[action.actionId] !== undefined
      );
      
      updated[roleId] = {};
      roleActions.forEach(action => {
        updated[roleId][action.actionId] = isSelected;
      });
      
      return updated;
    });
  };

  const handleSelectAll = () => {
    const allSelected = {};
    
    roles.forEach(role => {
      allSelected[role.roleId] = {};
      actions.forEach(action => {
        if (selectedPermissions[role.roleId]?.[action.actionId] !== undefined) {
          allSelected[role.roleId][action.actionId] = true;
        }
      });
    });
    
    setSelectedPermissions(allSelected);
  };

  const handleDeselectAll = () => {
    const noneSelected = {};
    
    roles.forEach(role => {
      noneSelected[role.roleId] = {};
      actions.forEach(action => {
        if (selectedPermissions[role.roleId]?.[action.actionId] !== undefined) {
          noneSelected[role.roleId][action.actionId] = false;
        }
      });
    });
    
    setSelectedPermissions(noneSelected);
  };

  const handleSubmit = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem('token');

      // Prepare the request data according to backend requirements
      const requestData = {
        userId: parseInt(userId),
        roleActionIds: []
      };

      // Convert selectedPermissions to the required format: "roleId_actionId"
      Object.keys(selectedPermissions).forEach(roleId => {
        Object.keys(selectedPermissions[roleId]).forEach(actionId => {
          if (selectedPermissions[roleId][actionId]) {
            requestData.roleActionIds.push(`${roleId}_${actionId}`);
          }
        });
      });

      await axios.post(
        `${API_BASE_URL}/users/mapActionsToUser`,
        requestData,
        { headers: { Authorization: `Bearer ${token}` } }
      );

      setNotification({
        open: true,
        message: 'Permissions updated successfully',
        severity: 'success'
      });
    } catch (error) {
      console.error('Failed to update permissions', error);
      setNotification({
        open: true,
        message: 'Failed to update permissions',
        severity: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCloseNotification = () => {
    setNotification(prev => ({ ...prev, open: false }));
  };

  const isRoleChecked = (roleId) => {
    const roleActions = actions.filter(action => 
      selectedPermissions[roleId]?.[action.actionId] !== undefined
    );
    
    if (roleActions.length === 0) return false;
    
    return roleActions.every(action => 
      selectedPermissions[roleId]?.[action.actionId]
    );
  };

  const isActionChecked = (roleId, actionId) => {
    return selectedPermissions[roleId]?.[actionId] || false;
  };

  return (
    <Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
      <Paper elevation={3} sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
          <IconButton onClick={() => navigate(-1)} sx={{ mr: 2 }}>
            <ArrowBack />
          </IconButton>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 'bold' }}>
            User Mapping
          </Typography>
          {name && (
            <Chip 
              label={name} 
              color="primary" 
              sx={{ ml: 2, fontWeight: 'bold' }} 
            />
          )}
        </Box>

        <Divider sx={{ mb: 3 }} />

        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            <strong>Username:</strong> {name || 'Not available'}
          </Typography>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>
            <strong>User ID:</strong> {userId}
          </Typography>
        </Box>

        <Box sx={{ mb: 3 }}>
          <Grid container spacing={2}>
            <Grid item>
              <Button variant="contained" onClick={handleSelectAll}>
                Select All
              </Button>
            </Grid>
            <Grid item>
              <Button variant="outlined" onClick={handleDeselectAll}>
                Deselect All
              </Button>
            </Grid>
          </Grid>
        </Box>

        {roles.map(role => (
          <Accordion key={role.roleId} defaultExpanded sx={{ mb: 2 }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={isRoleChecked(role.roleId)}
                    indeterminate={
                      !isRoleChecked(role.roleId) && 
                      Object.values(selectedPermissions[role.roleId] || {}).some(val => val)
                    }
                    onChange={(e) => handleRoleToggle(role.roleId, e.target.checked)}
                    onClick={(e) => e.stopPropagation()}
                    color="primary"
                  />
                }
                label={
                  <Typography sx={{ fontWeight: 600 }}>
                    {role.roleName}
                  </Typography>
                }
              />
            </AccordionSummary>
            <AccordionDetails>
              <Box sx={{ ml: 4 }}>
                {actions
                  .filter(action => selectedPermissions[role.roleId]?.[action.actionId] !== undefined)
                  .map(action => (
                    <FormControlLabel
                      key={`${role.roleId}-${action.actionId}`}
                      control={
                        <Checkbox
                          checked={isActionChecked(role.roleId, action.actionId)}
                          onChange={() => 
                            handleCheckboxChange(role.roleId, action.actionId)
                          }
                          color="primary"
                        />
                      }
                      label={action.actionName}
                      sx={{ display: 'block', mb: 1 }}
                    />
                  ))}
              </Box>
            </AccordionDetails>
          </Accordion>
        ))}

        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3, gap: 2 }}>
          <Button
            variant="outlined"
            onClick={() => navigate(-1)}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={handleSubmit}
            disabled={loading}
          >
            {loading ? <CircularProgress size={24} /> : 'Submit'}
          </Button>
        </Box>
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

export default UserMapping;