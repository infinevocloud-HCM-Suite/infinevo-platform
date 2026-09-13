import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
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
    CircularProgress
} from '@mui/material';
import API_BASE_URL from '../components/config/apiConfig';

const ForgetPassword = () => {
    const [email, setEmail] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [step, setStep] = useState(1); // Step 1: Enter email, Step 2: Set new password
    const [error, setError] = useState(null);
    const [successMessage, setSuccessMessage] = useState(null);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const validateEmail = (email) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    };

    const handleEmailSubmit = (event) => {
        event.preventDefault();
        setLoading(true);
        
        if (!validateEmail(email)) {
            setError('Please enter a valid email address');
            setLoading(false);
            return;
        }

        axios
            .post(`${API_BASE_URL}/auth/forgot-password`, { email })
            .then((response) => {
                setSuccessMessage("Password reset link sent to your email!");
                setStep(2);
                setError(null);
            })
            .catch((err) => {
                console.error(err);
                setError(err.response?.data?.message || "Failed to send reset link. Please try again.");
            })
            .finally(() => {
                setLoading(false);
            });
    };

    const handlePasswordSubmit = (event) => {
        event.preventDefault();
        setLoading(true);
        
        if (newPassword !== confirmPassword) {
            setError('Passwords do not match.');
            setLoading(false);
            return;
        }

        axios
            .post(`${API_BASE_URL}/auth/reset-password`, { email, newPassword })
            .then((response) => {
                setSuccessMessage("Password reset successfully. Redirecting to login...");
                setError(null);
                setTimeout(() => navigate('/login'), 3000);
            })
            .catch((err) => {
                console.error(err);
                setError(err.response?.data?.message || "Failed to reset password. Please try again.");
            })
            .finally(() => {
                setLoading(false);
            });
    };

    const handleKeyPress = (e) => {
        if (e.key === 'Enter') {
            if (step === 1) {
                handleEmailSubmit(e);
            } else {
                handlePasswordSubmit(e);
            }
        }
    };

    return (
        <Container maxWidth="sm" sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center' }}>
            <Card sx={{ width: '100%', mt: 2, mb: 2, borderRadius: 3, boxShadow: 6 }}>
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" align="center" gutterBottom sx={{ fontWeight: 'bold', mb: 3 }}>
                        Forgot Password
                    </Typography>
                    <Typography variant="body1" align="center" color="text.secondary" sx={{ mb: 4 }}>
                        {step === 1 
                            ? "Enter your email to receive a reset link" 
                            : "Set your new password"}
                    </Typography>

                    {error && (
                        <Alert severity="error" sx={{ mb: 3 }}>
                            {error}
                        </Alert>
                    )}

                    {successMessage && (
                        <Alert severity="success" sx={{ mb: 3 }}>
                            {successMessage}
                        </Alert>
                    )}

                    {step === 1 ? (
                        <Box component="form" onSubmit={handleEmailSubmit}>
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
                                sx={{ mb: 3 }}
                            />
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
                                    'Send Reset Link'
                                )}
                            </Button>
                            <Box sx={{ textAlign: 'center', mt: 2 }}>
                                <Link href="/login" underline="hover" variant="body2">
                                    Back to Login
                                </Link>
                            </Box>
                        </Box>
                    ) : (
                        <Box component="form" onSubmit={handlePasswordSubmit}>
                            <TextField
                                fullWidth
                                label="New Password"
                                variant="outlined"
                                margin="normal"
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                onKeyPress={handleKeyPress}
                                autoComplete="new-password"
                                autoFocus
                                sx={{ mb: 2 }}
                            />
                            <TextField
                                fullWidth
                                label="Confirm Password"
                                variant="outlined"
                                margin="normal"
                                type="password"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                onKeyPress={handleKeyPress}
                                autoComplete="new-password"
                                sx={{ mb: 3 }}
                            />
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
                                    'Reset Password'
                                )}
                            </Button>
                            <Box sx={{ textAlign: 'center', mt: 2 }}>
                                <Link 
                                    href="#" 
                                    underline="hover" 
                                    variant="body2"
                                    onClick={() => {
                                        setStep(1);
                                        setError(null);
                                        setSuccessMessage(null);
                                    }}
                                >
                                    Back to Email Entry
                                </Link>
                            </Box>
                        </Box>
                    )}
                </CardContent>
            </Card>
        </Container>
    );
};

export default ForgetPassword;