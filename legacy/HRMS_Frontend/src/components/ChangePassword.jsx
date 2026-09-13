import React, { useState, useContext, useRef } from 'react';
import {
    Container,
    Card,
    CardContent,
    TextField,
    Button,
    Typography,
    Box,
    Link,
    Alert,
    CircularProgress,
    List,
    ListItem,
    ListItemIcon,
    Divider,
    InputAdornment,
    IconButton,
    Popover,
    Paper,
    LinearProgress
} from '@mui/material';
import { 
    CheckCircleOutline as CheckCircleIcon,
    ErrorOutline as ErrorIcon,
    Visibility,
    VisibilityOff
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { userContext } from './context/ContextProvider';
import API_BASE_URL from './config/apiConfig';

const ChangePassword = () => {
    const { authenticated, updateAuthState } = useContext(userContext);
    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [loading, setLoading] = useState(false);
    const [showPassword, setShowPassword] = useState({
        old: false,
        new: false,
        confirm: false
    });
    const [anchorEl, setAnchorEl] = useState(null);
    const passwordFieldRef = useRef(null);
    const navigate = useNavigate();

    // Password Strength Calculator
    const calculatePasswordStrength = (password) => {
        let strength = 0;
        if (password.length >= 12) strength += 20;
        if (/[A-Z]/.test(password)) strength += 20;
        if (/[a-z]/.test(password)) strength += 20;
        if (/\d/.test(password)) strength += 20;
        if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) strength += 20;
        return Math.min(100, strength); // Cap at 100%
    };

    const passwordStrength = calculatePasswordStrength(newPassword);
    const strengthColor = passwordStrength < 40 ? 'error' : 
                         passwordStrength < 70 ? 'warning' : 'success';

    // Redirect if not authenticated
    React.useEffect(() => {
        if (!authenticated) {
            navigate('/login');
        }
    }, [authenticated, navigate]);

    const handleClickShowPassword = (field) => {
        setShowPassword({ ...showPassword, [field]: !showPassword[field] });
    };

    const validatePassword = (password) => {
        const requirements = {
            length: password.length >= 12,
            uppercase: /[A-Z]/.test(password),
            lowercase: /[a-z]/.test(password),
            number: /\d/.test(password),
            specialChar: /[!@#$%^&*(),.?":{}|<>]/.test(password)
        };

        return {
            ...requirements,
            isValid: Object.values(requirements).every(Boolean)
        };
    };

    const passwordValidation = validatePassword(newPassword);

    const handlePasswordFocus = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handlePasswordBlur = () => {
        setTimeout(() => {
            setAnchorEl(null);
        }, 200);
    };

    const isFormValid = () => {
        return (
            oldPassword.length > 0 &&
            newPassword.length > 0 &&
            confirmPassword.length > 0 &&
            passwordValidation.isValid &&
            newPassword === confirmPassword
        );
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        
        if (!isFormValid()) {
            setError('Please fill all fields correctly');
            return;
        }

        if (newPassword === oldPassword) {
            setError('New password must be different from current password');
            return;
        }

        setLoading(true);
        setError('');
        setSuccess('');

        try {
            const token = localStorage.getItem('token');
            const response = await axios.post(
                `${API_BASE_URL}/change-password`,
                {
                    oldPassword: oldPassword,
                    newPassword: newPassword
                },
                {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                }
            );

            if (response.data && response.data.statusCode === 200) {
                setSuccess(response.data.message || 'Password changed successfully');
                
                // Clear form
                setOldPassword('');
                setNewPassword('');
                setConfirmPassword('');
                
                // Logout user after 3 seconds
                setTimeout(() => {
                    localStorage.removeItem('token');
                    localStorage.removeItem('role');
                    updateAuthState({
                        authenticated: false,
                        role: null,
                        actions: []
                    });
                    navigate('/login', { 
                        state: { 
                            success: 'Password changed successfully. Please login with your new password.' 
                        } 
                    });
                }, 3000);
            } else {
                setError(response.data?.message || 'Failed to change password');
            }
        } catch (err) {
            console.error('Password change error:', err);
            setError(err.response?.data?.message || err.message || 'Failed to change password');
        } finally {
            setLoading(false);
        }
    };

    const open = Boolean(anchorEl);
    const id = open ? 'password-popover' : undefined;

    return (
        <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '80vh' }}>
            <Card sx={{ width: '100%', borderRadius: 3, boxShadow: 6 }}>
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" align="center" gutterBottom sx={{ fontWeight: 'bold', mb: 2 }}>
                        Change Password
                    </Typography>

                    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
                    {success && <Alert severity="success" sx={{ mb: 2 }}>{success}</Alert>}

                    <Box component="form" onSubmit={handleSubmit}>
                        <TextField
                            fullWidth
                            label="Current Password"
                            variant="outlined"
                            margin="normal"
                            type={showPassword.old ? "text" : "password"}
                            value={oldPassword}
                            onChange={(e) => setOldPassword(e.target.value)}
                            autoComplete="current-password"
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => handleClickShowPassword('old')}
                                            edge="end"
                                        >
                                            {showPassword.old ? <VisibilityOff /> : <Visibility />}
                                        </IconButton>
                                    </InputAdornment>
                                )
                            }}
                            required
                        />

                        <Divider sx={{ my: 2 }} />

                        <Typography variant="h6" gutterBottom>New Password</Typography>
                        <TextField
                            fullWidth
                            label="New Password"
                            variant="outlined"
                            margin="normal"
                            type={showPassword.new ? "text" : "password"}
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            onFocus={handlePasswordFocus}
                            onBlur={handlePasswordBlur}
                            inputRef={passwordFieldRef}
                            autoComplete="new-password"
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => handleClickShowPassword('new')}
                                            edge="end"
                                        >
                                            {showPassword.new ? <VisibilityOff /> : <Visibility />}
                                        </IconButton>
                                    </InputAdornment>
                                )
                            }}
                            required
                        />

                        {/* Password Strength Meter */}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                            <LinearProgress 
                                variant="determinate" 
                                value={passwordStrength} 
                                color={strengthColor}
                                sx={{ height: 6, flexGrow: 1, borderRadius: 3 }}
                            />
                            <Typography variant="caption" color="text.secondary">
                                {passwordStrength}%
                            </Typography>
                        </Box>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                            {passwordStrength < 40 ? 'Weak' : 
                             passwordStrength < 70 ? 'Moderate' : 'Strong'}
                        </Typography>

                        <Popover
                            id={id}
                            open={open}
                            anchorEl={anchorEl}
                            onClose={handlePasswordBlur}
                            anchorOrigin={{
                                vertical: 'bottom',
                                horizontal: 'left',
                            }}
                            transformOrigin={{
                                vertical: 'top',
                                horizontal: 'left',
                            }}
                            disableRestoreFocus
                            disableAutoFocus
                            disableEnforceFocus
                        >
                            <Paper sx={{ p: 2, maxWidth: 300 }}>
                                <Typography variant="subtitle2" gutterBottom>
                                    Password Requirements:
                                </Typography>
                                <List dense sx={{ p: 0 }}>
                                    {[
                                        { key: 'length', text: 'Minimum 12 characters' },
                                        { key: 'uppercase', text: 'At least one uppercase letter' },
                                        { key: 'lowercase', text: 'At least one lowercase letter' },
                                        { key: 'number', text: 'At least one number' },
                                        { key: 'specialChar', text: 'At least one special character' }
                                    ].map((req) => (
                                        <ListItem key={req.key} disablePadding sx={{ py: 0.5 }}>
                                            <ListItemIcon sx={{ minWidth: 24 }}>
                                                {passwordValidation[req.key] ? 
                                                    <CheckCircleIcon color="success" fontSize="small" /> : 
                                                    <ErrorIcon color="error" fontSize="small" />}
                                            </ListItemIcon>
                                            <Typography variant="body2">{req.text}</Typography>
                                        </ListItem>
                                    ))}
                                </List>
                            </Paper>
                        </Popover>

                        <TextField
                            fullWidth
                            label="Confirm New Password"
                            variant="outlined"
                            margin="normal"
                            type={showPassword.confirm ? "text" : "password"}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            autoComplete="new-password"
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => handleClickShowPassword('confirm')}
                                            edge="end"
                                        >
                                            {showPassword.confirm ? <VisibilityOff /> : <Visibility />}
                                        </IconButton>
                                    </InputAdornment>
                                )
                            }}
                            required
                        />

                        <Button
                            fullWidth
                            variant="contained"
                            color="primary"
                            size="large"
                            type="submit"
                            disabled={!isFormValid() || loading}
                            sx={{ mt: 2, py: 1.5 }}
                        >
                            {loading ? <CircularProgress size={24} color="inherit" /> : 'Change Password'}
                        </Button>

                        <Box sx={{ textAlign: 'center', mt: 1 }}>
                            <Link href="/forgot-password" underline="hover" variant="body2">
                                Forgot Password?
                            </Link>
                        </Box>
                    </Box>
                </CardContent>
            </Card>
        </Container>
    );
};

export default ChangePassword;