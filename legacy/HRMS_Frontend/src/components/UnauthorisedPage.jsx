import React from 'react';
import { Box, Button, Typography, Container, Paper } from '@mui/material';
import { useNavigate, useLocation } from 'react-router-dom';
import Unauthorizedimg from '../assets/Unauthorizedimg.png';

const UnauthorisedPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const fromPath = location.state?.from?.pathname || '/';

  const handleGoHome = () => {
    const role = localStorage.getItem('role');
    let homePath = '/';
    
    switch(role) {
      case 'admin': homePath = '/admin/dashboard'; break;
      case 'hr': homePath = '/hr/hr-dashboard'; break;
      case 'manager': homePath = '/manager/manager-dashboard'; break;
      case 'supervisor': homePath = '/supervisor/supervisor-dashboard'; break;
      case 'user': homePath = '/user/employee-dashboard'; break;
      default: homePath = '/';
    }
    
    navigate(homePath);
  };

  const handleGoBack = () => {
    navigate(-1);
  };

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper
        elevation={4}
        sx={{
          width: '100%',
          p: 4,
          textAlign: 'center',
          borderRadius: 4,
          bgcolor: 'background.paper',
        }}
      >
        <Box
          component="img"
          src={Unauthorizedimg}
          alt="Unauthorized Access"
          sx={{
            height: 'auto',
            width: '100%',
            maxWidth: 400,
            mb: 4,
            objectFit: 'contain',
          }}
        />

        
        
        <Typography 
          variant="subtitle1" 
          color="text.secondary" 
          gutterBottom
          sx={{ mb: 4 }}
        >
          You don't have the required permissions to access this resource.
        </Typography>
        
       
        <Box sx={{ 
          display: 'flex', 
          justifyContent: 'center', 
          gap: 2,
          mt: 4
        }}>
          <Button
            variant="outlined"
            onClick={handleGoBack}
            sx={{ px: 4, py: 1.5 }}
          >
            Go Back
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={handleGoHome}
            sx={{ px: 4, py: 1.5 }}
          >
            Go to Home
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};

export default UnauthorisedPage;