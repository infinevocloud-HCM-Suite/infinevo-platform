import React, { useState, useContext, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import {
    Container, Card, CardContent, TextField, Button, Typography,
    Box, Checkbox, FormControlLabel, Link, IconButton, InputAdornment,
    Alert, CircularProgress
} from "@mui/material";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import { userContext } from "../context/ContextProvider";
import axios from "axios";
import API_BASE_URL from "../config/apiConfig"; 

function LoginPage() {
    const { updateAuthState } = useContext(userContext);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

        // Clear any stale auth data when login page loads
useEffect(() => {
    localStorage.clear();
}, []);



// Update the handleSubmit function to handle all roles
const handleSubmit = async (e) => {
  e.preventDefault();
  setLoading(true);
  setError("");

  if (!email || !password) {
    setError("Please fill in all fields");
    setLoading(false);
    return;
  }

  try {
    const response = await UserService.login(email, password);
    console.log("Login response:", response);

    if (!response.token) {
      setError(response.message || "Login failed. Please try again.");
      setLoading(false);
      return;
    }

    // ✅ Save the base token immediately
    localStorage.setItem("token", response.token);
    const roles = (response.roles || []).map((r) => r.toLowerCase());

    // --- SINGLE ROLE LOGIN ---
    if (roles.length === 1) {
      const role = roles[0];

      // 🚀 Switch to role to get role-scoped token
      const switchRes = await axios.post(
        `${API_BASE_URL}/auth/switch-role`,
        { role },
        { headers: { Authorization: `Bearer ${response.token}` } }
      );

      if (switchRes.data.statusCode === 200) {
        const newToken = switchRes.data.token;
        const activeRole = switchRes.data.activeRole?.toLowerCase() || role;
        const actions = switchRes.data.actions || [];

        // ✅ Save new data consistently before updating state
        localStorage.setItem("token", newToken);
        localStorage.setItem("activeRole", activeRole);
        localStorage.setItem("roles", JSON.stringify(roles));
        localStorage.setItem("actions", JSON.stringify(actions));

        const authState = {
          roles,
          activeRole,
          actions,
          authenticated: true,
        };

        localStorage.setItem("authState", JSON.stringify(authState));

        // ✅ Update context and delay navigation slightly for stability
        updateAuthState(authState);

        setTimeout(() => {
          switch (activeRole) {
            case "admin":
              navigate("/admin/dashboard");
              break;
            case "hr":
              navigate("/hr/hr-dashboard");
              break;
            case "manager":
              navigate("/manager/manager-dashboard");
              break;
            case "supervisor":
              navigate("/supervisor/supervisor-dashboard");
              break;
            case "reporting manager":
              navigate("/reporting manager/reporting-manager-dashboard");
              break;
            default:
              navigate("/user/employee-dashboard");
          }
        }, 200);
      } else {
        setError(switchRes.data.message || "Role switch failed");
      }
    }

    // --- MULTI ROLE LOGIN ---
    else {
      localStorage.setItem("roles", JSON.stringify(roles));
      localStorage.setItem("activeRole", "");
      localStorage.setItem("token", response.token);

      const authState = {
        roles,
        activeRole: null,
        actions: [],
        authenticated: true,
      };

      localStorage.setItem("authState", JSON.stringify(authState));
      updateAuthState(authState);

      navigate("/choose-role");
    }
  } catch (error) {
    console.error("Login error:", error);
    setError(
      error.response?.data?.message ||
        error.message ||
        "Login failed. Please check your credentials."
    );
  } finally {
    setLoading(false);
  }
};

    const handleKeyPress = (e) => {
        if (e.key === 'Enter') {
            handleSubmit(e);
        }
    };

    return (
        <Container maxWidth="sm" sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center' }}>
            <Card sx={{ width: '100%', mt: 2, mb: 2, borderRadius: 3, boxShadow: 6 }}>
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" align="center" gutterBottom sx={{ fontWeight: 'bold', mb: 3 }}>
                        Login
                    </Typography>
                    <Typography variant="body1" align="center" color="text.secondary" sx={{ mb: 4 }}>
                        Please enter your credentials to login
                    </Typography>

                    {error && (
                        <Alert severity="error" sx={{ mb: 3 }}>
                            {error}
                        </Alert>
                    )}

                    <form onSubmit={handleSubmit}>
                        <TextField
                            fullWidth
                            label="Email Address"
                            variant="outlined"
                            margin="normal"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            onKeyPress={handleKeyPress}
                            autoComplete="email"
                            autoFocus
                            sx={{ mb: 2 }}
                        />
                        <TextField
                            fullWidth
                            label="Password"
                            variant="outlined"
                            margin="normal"
                            type={showPassword ? "text" : "password"}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyPress={handleKeyPress}
                            autoComplete="current-password"
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => setShowPassword(!showPassword)}
                                            edge="end"
                                        >
                                            {showPassword ? <VisibilityOff /> : <Visibility />}
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                            sx={{ mb: 1 }}
                        />
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={showPassword}
                                        onChange={() => setShowPassword(!showPassword)}
                                        color="primary"
                                    />
                                }
                                label="Show Password"
                            />
                            <Link href="/forgot-password" underline="hover" variant="body2">
                                Forgot Password?
                            </Link>
                        </Box>
                        <Button
                            fullWidth
                            variant="contained"
                            color="primary"
                            size="large"
                            type="submit"
                            disabled={loading}
                            sx={{ py: 1.5, mb: 2 }}
                        >
                            {loading ? (
                                <CircularProgress size={24} color="inherit" />
                            ) : (
                                'Sign In'
                            )}
                        </Button>
                    </form>
                </CardContent>
            </Card>
        </Container>
    );
}

export default LoginPage;