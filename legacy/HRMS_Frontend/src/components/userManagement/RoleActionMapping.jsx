import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Container,
  Checkbox,
  FormControlLabel,
  List,
  ListItem,
  Divider,
  Tooltip,
  IconButton,
  CircularProgress,
  Snackbar,
  Alert
} from '@mui/material';
import { ArrowBack, Save, SelectAll } from '@mui/icons-material';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import  { useContext } from 'react';
import { userContext } from '../context/ContextProvider';

const RoleActionMapping = () => {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const { roleId } = useParams();
  const navigate = useNavigate();
  
  const [roles, setRoles] = useState(null);
  const [allActions, setAllActions] = useState([]);
  const [selectedActions, setSelectedActions] = useState([]);
  const [selectAll, setSelectAll] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: '',
    severity: 'success'
  });

  useEffect(() => {
    const fetchRoleAndActions = async () => {
      try {
        setLoading(true);
        // Fetch role details and its mapped actions
        const roleResponse = await axios.get(`${API_BASE_URL}/roles/${roleId}`);
        const actionsResponse = await axios.get(`${API_BASE_URL}/auth/actions`);
        
        // Get the IDs of actions already mapped to this role
        const mappedActionsResponse = await axios.get(`${API_BASE_URL}/roles/getActions/${roleId}`);
        const mappedActionIds = mappedActionsResponse.data.map(action => action.actionId);

        setRoles(roleResponse.data);
        setAllActions(actionsResponse.data);
        setSelectedActions(mappedActionIds);
        setSelectAll(mappedActionIds.length === actionsResponse.data.length);
      } catch (error) {
        console.error('Error fetching data:', error);
        setSnackbar({
          open: true,
          message: 'Failed to load role and action data',
          severity: 'error'
        });
      } finally {
        setLoading(false);
      }
    };

    fetchRoleAndActions();
  }, [roleId]);

  const handleActionToggle = (actionId) => {
    const currentIndex = selectedActions.indexOf(actionId);
    const newSelectedActions = [...selectedActions];

    if (currentIndex === -1) {
      newSelectedActions.push(actionId);
    } else {
      newSelectedActions.splice(currentIndex, 1);
    }

    setSelectedActions(newSelectedActions);
    setSelectAll(newSelectedActions.length === allActions.length);
  };

  const handleSelectAll = () => {
    if (selectAll) {
      setSelectedActions([]);
    } else {
      setSelectedActions(allActions.map(action => action.actionId));
    }
    setSelectAll(!selectAll);
  };

  const handleSubmit = async () => {
    try {
      setSubmitting(true);
      
      const response = await axios.post(`${API_BASE_URL}/roles/mapActions`, {
        roleId: Number(roleId),
        actionList: selectedActions
      });

      if (response.status === 200) {
        setSnackbar({
          open: true,
          message: 'Action mapping updated successfully!',
          severity: 'success'
        });
        // Optionally navigate back after success
        setTimeout(() => navigate(`/${role}/list-roles`), 1000);
      }
    } catch (error) {
      console.error('Error updating action mapping:', error);
      setSnackbar({
        open: true,
        message: error.response?.data || 'Failed to update action mapping',
        severity: 'error'
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCloseSnackbar = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  return (
    <Container maxWidth="md" sx={{ mt: 4, mb: 4 }}>
      <Paper elevation={3} sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
          <IconButton onClick={() => navigate(`/${role}/list-roles`)} sx={{ mr: 2 }}>
            <ArrowBack />
          </IconButton>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 'bold' }}>
            Role Action Mapping
          </Typography>
        </Box>

        <Divider sx={{ mb: 3 }} />

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Role: <strong>{roles?.roleName}</strong>
            </Typography>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="subtitle1">Available Actions:</Typography>
              <Box>
                <Tooltip title={selectAll ? "Deselect All" : "Select All"}>
                  <IconButton 
                    onClick={handleSelectAll} 
                    size="small" 
                    sx={{ mr: 1, color: selectAll ? 'primary.main' : 'inherit' }}
                  >
                    <SelectAll />
                  </IconButton>
                </Tooltip>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => setSelectedActions([])}
                >
                  Clear Selection
                </Button>
              </Box>
            </Box>

            <List sx={{ maxHeight: '400px', overflow: 'auto', border: '1px solid #e0e0e0', borderRadius: 1 }}>
              {allActions.map((action) => (
                <ListItem key={action.actionId} dense>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={selectedActions.includes(action.actionId)}
                        onChange={() => handleActionToggle(action.actionId)}
                      />
                    }
                    label={
                      <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                        <Typography>{action.actionName}</Typography>
                        {action.description && (
                          <Typography variant="body2" color="text.secondary">
                            {action.description}
                          </Typography>
                        )}
                      </Box>
                    }
                  />
                </ListItem>
              ))}
            </List>

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3, gap: 2 }}>
              <Button
                variant="outlined"
                onClick={() => navigate(`/${role}/list-roles`)}
                disabled={submitting}
              >
                Cancel
              </Button>
              <Button
                variant="contained"
                startIcon={submitting ? <CircularProgress size={20} /> : <Save />}
                onClick={handleSubmit}
                disabled={submitting}
              >
                {submitting ? 'Saving...' : 'Save Mapping'}
              </Button>
            </Box>
          </>
        )}
      </Paper>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert onClose={handleCloseSnackbar} severity={snackbar.severity} sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Container>
  );
};

export default RoleActionMapping;