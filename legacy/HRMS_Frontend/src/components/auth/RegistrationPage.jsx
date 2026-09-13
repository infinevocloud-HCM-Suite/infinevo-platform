import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Card, CardContent, TextField, Button, Typography,
  MenuItem, Select, FormControl, InputLabel, Box, IconButton, InputAdornment,
  Alert, CircularProgress, OutlinedInput, Checkbox, ListItemText
} from "@mui/material";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import UserService from '../service/UserService';
import axios from 'axios';
import API_BASE_URL from '../config/apiConfig';
import { userContext } from '../context/ContextProvider';

function RegistrationPage() {
  const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    roles: [],   // ✅ array instead of single role
    city: '',
    empId: ''
  });

  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [roles, setRoles] = useState([]);
  const [rolesLoading, setRolesLoading] = useState(true);

  // Fetch roles
  useEffect(() => {
    const fetchRoles = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}/roles`);
        setRoles(response.data);
      } catch (err) {
        console.error('Error fetching roles:', err);
        setError('Failed to load roles. Please try again later.');
      } finally {
        setRolesLoading(false);
      }
    };
    fetchRoles();
  }, []);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    // Convert empId to uppercase
    const processedValue = name === 'empId' ? value.toUpperCase() : value;
    setFormData({ ...formData, [name]: processedValue });
  };

  const handleRoleChange = (event) => {
    const { value } = event.target;
    setFormData({
      ...formData,
      roles: typeof value === 'string' ? value.split(',') : value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (!formData.empId || !formData.name || !formData.email || formData.roles.length === 0 || !formData.password) {
        throw new Error('All fields are required');
      }

      if (!/^[A-Z0-9]+$/.test(formData.empId)) {
        throw new Error('Employee ID must contain only uppercase letters and numbers');
      }

      const token = localStorage.getItem("token");
      const response = await UserService.register(formData, token);


// NOTE: response is now the small object returned above.
// DO NOT console.log the whole backend data (huge) — log only summary
console.log("Register result:", { status: response.status, message: response.message, userId: response.userId });

if (response.status === 200 || response.userId) {
  setFormData({ name: '', email: '', password: '', roles: [], city: '', empId: '' });
  navigate(`/${role}/user-management`, {
    state: { success: response.message || 'User registered successfully' }
  });
} else {
  throw new Error(response.message || 'Registration failed');
}
    } catch (error) {
      console.error('Registration error:', error);
      setError(error.response?.data?.message || error.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container
      maxWidth="sm"
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: "100vh",
        background: "linear-gradient(135deg, #1976D2 30%, #42A5F5 90%)",
        p: 2,
      }}
    >
      <Card sx={{ p: 4, borderRadius: 3, boxShadow: 6, width: "100%", backgroundColor: "white" }}>
        <CardContent>
          <Typography variant="h4" align="center" gutterBottom fontWeight="bold" color="primary">
            Register New User
          </Typography>

          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

          <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 2 }}>
            
            <TextField
              fullWidth label="Employee ID" name="empId" variant="outlined" margin="normal"
              value={formData.empId} onChange={handleInputChange} required disabled={loading}
              inputProps={{ style: { textTransform: 'uppercase' }, pattern: "[A-Z0-9]*", title: "Only uppercase letters and numbers are allowed" }}
              helperText="Must be uppercase alphanumeric characters"
            />

            <TextField
              fullWidth label="Full Name" name="name" variant="outlined" margin="normal"
              value={formData.name} onChange={handleInputChange} required disabled={loading}
            />

            <TextField
              fullWidth label="Email" name="email" type="email" variant="outlined" margin="normal"
              value={formData.email} onChange={handleInputChange} required disabled={loading}
            />

            {/* ✅ Multiple roles select */}
            <FormControl fullWidth margin="normal" required>
              <InputLabel>Roles</InputLabel>
              <Select
                multiple
                name="roles"
                value={formData.roles}
                onChange={handleRoleChange}
                input={<OutlinedInput label="Roles" />}
                renderValue={(selected) => selected.join(', ')}
                disabled={loading || rolesLoading}
              >
                {rolesLoading ? (
                  <MenuItem disabled><CircularProgress size={24} /></MenuItem>
                ) : (
                  roles.map((r) => (
                    <MenuItem key={r.roleId} value={r.roleName}>
                      <Checkbox checked={formData.roles.indexOf(r.roleName) > -1} />
                      <ListItemText primary={r.roleName.toUpperCase()} />
                    </MenuItem>
                  ))
                )}
              </Select>
            </FormControl>

            <TextField
              fullWidth label="City" name="city" variant="outlined" margin="normal"
              value={formData.city} onChange={handleInputChange} required disabled={loading}
            />

            <TextField
              fullWidth label="Password" name="password" variant="outlined" margin="normal"
              type={showPassword ? "text" : "password"}
              value={formData.password} onChange={handleInputChange} required disabled={loading}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowPassword(!showPassword)} edge="end" disabled={loading}>
                      {showPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />

            <Button
              fullWidth variant="contained" color="primary"
              sx={{ mt: 3, py: 1.5, fontWeight: "bold", fontSize: "1rem", borderRadius: 2 }}
              type="submit" disabled={loading || rolesLoading}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Register'}
            </Button>

          </Box>
        </CardContent>
      </Card>
    </Container>
  );
}

export default RegistrationPage;