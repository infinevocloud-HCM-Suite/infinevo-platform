import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import UserService from '../service/UserService';
import {
  Box,
  Typography,
  TextField,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Alert,
  CircularProgress,
  Paper,
  Snackbar
} from '@mui/material';
import { FormControlLabel, Checkbox } from '@mui/material';
import { useContext } from 'react';
import { userContext } from '../context/ContextProvider';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';

function UpdateUser() {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const { userId } = useParams();
  const navigate = useNavigate();
  const [userData, setUserData] = useState({
    name: '',
    email: '',
    roles: [],
    city: '',
    password: ''
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [notification, setNotification] = useState({
    open: false,
    message: '',
    severity: 'success'
  });
  const [roles, setRoles] = useState([]);
  const [rolesLoading, setRolesLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const token = localStorage.getItem('token');

        if (!token) {
          navigate('/login');
          return;
        }

        // Fetch roles
        const rolesResponse = await axios.get(`${API_BASE_URL}/roles`);
        setRoles(rolesResponse.data);
        setRolesLoading(false);

        // Fetch user data
        const userResponse = await UserService.getUserById(userId, token);

        if (userResponse && userResponse.user) {
          setUserData({
            name: userResponse.user.name,
            email: userResponse.user.email,
            roles: userResponse.user.roles || [],
            city: userResponse.user.city || '',
            password: '' // Don't pre-fill password
          });
        } else {
          setError('User data not found');
          showNotification('User data not found', 'error');
        }
      } catch (error) {
        console.error('Error fetching data:', error);
        setError(error.response?.data?.message || 'Failed to load data. Please try again.');
        showNotification(error.response?.data?.message || 'Failed to load data', 'error');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [userId, navigate]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setUserData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const confirmUpdate = window.confirm('Are you sure you want to update this user?');
      if (confirmUpdate) {
        const token = localStorage.getItem('token');
        if (!token) {
          navigate('/login');
          return;
        }

        // Create payload with only changed fields if needed
        const payload = {
          name: userData.name,
          email: userData.email,
          roles: userData.roles,
          city: userData.city,
          ...(userData.password && { password: userData.password }) // Only include password if it's set
        };

        const response = await UserService.updateUser(userId, payload, token);

        if (response && response.statusCode === 200) {
          setSuccess('User updated successfully!');
          showNotification('User updated successfully!', 'success');
          setTimeout(() => {
            navigate(`/${role}/user-management`);
          }, 1500);
        } else {
          throw new Error(response?.message || 'Update failed');
        }
      }
    } catch (error) {
      console.error('Error updating user:', error);
      setError(error.response?.data?.message || 'Failed to update user. Please try again.');
      showNotification(error.response?.data?.message || 'Failed to update user', 'error');
    }
  };

  const showNotification = (message, severity) => {
    setNotification({
      open: true,
      message,
      severity
    });
  };

  const handleCloseNotification = () => {
    setNotification(prev => ({ ...prev, open: false }));
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress size={60} />
      </Box>
    );
  }

  return (
    <Box sx={{ padding: 3 }}>
      <Snackbar
        open={notification.open}
        autoHideDuration={6000}
        onClose={handleCloseNotification}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert
          onClose={handleCloseNotification}
          severity={notification.severity}
          sx={{ width: '100%' }}
        >
          {notification.message}
        </Alert>
      </Snackbar>

      <Typography variant="h4" gutterBottom>
        Update User
      </Typography>

      <Paper sx={{ padding: 3, maxWidth: 600 }}>
        {error && !notification.open && (
          <Alert severity="error" sx={{ marginBottom: 2 }}>
            {error}
          </Alert>
        )}

        {success && !notification.open && (
          <Alert severity="success" sx={{ marginBottom: 2 }}>
            {success}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label="Name"
            name="name"
            value={userData.name}
            onChange={handleInputChange}
            margin="normal"
            required
          />

          <TextField
            fullWidth
            label="Email"
            name="email"
            type="email"
            value={userData.email}
            onChange={handleInputChange}
            margin="normal"
            required
          />

          <FormControl fullWidth margin="normal" required>
  <InputLabel shrink>Roles</InputLabel>
  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1 }}>
    {rolesLoading ? (
      <CircularProgress size={24} />
    ) : (
      roles.map((role) => (
        <FormControlLabel
          key={role.roleId}
          control={
            <Checkbox
              checked={userData.roles.includes(role.roleName)}
              onChange={(e) => {
                if (e.target.checked) {
                  setUserData((prev) => ({
                    ...prev,
                    roles: [...prev.roles, role.roleName]
                  }));
                } else {
                  setUserData((prev) => ({
                    ...prev,
                    roles: prev.roles.filter((r) => r !== role.roleName)
                  }));
                }
              }}
            />
          }
          label={role.roleName.toUpperCase()}
        />
      ))
    )}
  </Box>
</FormControl>

          <TextField
            fullWidth
            label="City"
            name="city"
            value={userData.city}
            onChange={handleInputChange}
            margin="normal"
          />

          <TextField
            fullWidth
            label="New Password (leave blank to keep current)"
            name="password"
            type="password"
            value={userData.password}
            onChange={handleInputChange}
            margin="normal"
            helperText="Only enter if you want to change the password"
          />

          <Box sx={{ marginTop: 2 }}>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              sx={{ marginRight: 2 }}
              disabled={rolesLoading}
            >
              Update User
            </Button>
            <Button
              variant="outlined"
              onClick={() => navigate(`/${role}/user-management`)}
            >
              Cancel
            </Button>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}

export default UpdateUser;