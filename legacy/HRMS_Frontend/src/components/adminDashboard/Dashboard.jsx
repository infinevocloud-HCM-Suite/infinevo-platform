// Dashboard.jsx
import React, { useContext } from "react";
import { Outlet, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  Container,
  AppBar,
  Toolbar,
} from "@mui/material";
import {
  PersonAdd as PersonAddIcon,
  Person as PersonIcon,
  ListAlt as ListAltIcon,
  Badge as BadgeIcon,
  GroupAdd as GroupAddIcon,
  SettingsInputComponent as SettingsInputComponentIcon,
} from "@mui/icons-material";
import { userContext } from "../context/ContextProvider";

const Dashboard = () => {
  const navigate = useNavigate();
  const { activeRole, actions } = useContext(userContext);
  const role = activeRole?.toLowerCase();

  const hasAction = (actionName) => actions.includes(actionName);

  const dashboardCards = [
    {
      title: "User Management",
      description: "View and manage all users",
      path: `/${role}/user-management`,
      action: "MANAGE_USERS_MANAGEMENT",
      color: "#1976D2",
      icon: <PersonIcon fontSize="large" />,
    },
    {
      title: "Create User",
      description: "Register a new user",
      path: `/${role}/register`,
      action: "CREATE_USER",
      color: "#2E7D32",
      icon: <PersonAddIcon fontSize="large" />,
    },
    {
      title: "List Actions",
      description: "View all available actions",
      path: `/${role}/list-actions`,
      action: "VIEW_LIST_ACTIONS",
      color: "#0288D1",
      icon: <ListAltIcon fontSize="large" />,
    },
    {
      title: "Create Action",
      description: "Create a new action",
      path: `/${role}/create-action`,
      action: "CREATE_ACTIONS",
      color: "#009688",
      icon: <SettingsInputComponentIcon fontSize="large" />,
    },
    {
      title: "List Roles",
      description: "View all available roles",
      path: `/${role}/list-roles`,
      action: "VIEW_LIST_ROLES",
      color: "#D32F2F",
      icon: <BadgeIcon fontSize="large" />,
    },
    {
      title: "Create Role",
      description: "Create a new role",
      path: `/${role}/create-role`,
      action: "CREATE_ROLE",
      color: "#7B1FA2",
      icon: <GroupAddIcon fontSize="large" />,
    },
  ];

  const handleCardClick = (path) => {
    navigate(path);
  };

  return (
    <Box sx={{ flexGrow: 1, bgcolor: "#f8fafc" }}>
<Box
  component="main"
  sx={{
    flexGrow: 1,
    bgcolor: "#f8fafc",
    minHeight: "100vh",
    pt: 0,
    px: 2,
  }}
>
          {window.location.pathname === `/${role}/dashboard` ? (
            <Container maxWidth="xl">
              <Typography variant="h4" gutterBottom sx={{ mb: 3, fontWeight: "bold" }}>
                System Admin Panel
              </Typography>

              <Grid container spacing={3}>
                {dashboardCards.map(
                  (card, index) =>
                    hasAction(card.action) && (
                      <Grid item xs={12} sm={6} md={4} key={index}>
                        <Card
                          sx={{
                            height: "100%",
                            display: "flex",
                            flexDirection: "column",
                            borderLeft: `4px solid ${card.color}`,
                            "&:hover": {
                              boxShadow: 3,
                              transform: "translateY(-2px)",
                              transition: "all 0.3s ease",
                              cursor: "pointer",
                            },
                          }}
                          onClick={() => handleCardClick(card.path)}
                        >
                          <CardContent
                            sx={{
                              flexGrow: 1,
                              display: "flex",
                              flexDirection: "column",
                              alignItems: "center",
                              textAlign: "center",
                              p: 3,
                            }}
                          >
                            <Box
                              sx={{
                                color: card.color,
                                mb: 2,
                                "& svg": {
                                  fontSize: "2.5rem",
                                },
                              }}
                            >
                              {card.icon}
                            </Box>
                            <Typography variant="h5" component="h2" gutterBottom>
                              {card.title}
                            </Typography>
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                              {card.description}
                            </Typography>
                          </CardContent>
                          <Box
                            sx={{
                              p: 2,
                              display: "flex",
                              justifyContent: "center",
                              bgcolor: "rgba(0, 0, 0, 0.02)",
                            }}
                          >
                            <Button
                              size="small"
                              variant="contained"
                              sx={{
                                backgroundColor: card.color,
                                "&:hover": {
                                  backgroundColor: card.color,
                                  opacity: 0.9,
                                },
                              }}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleCardClick(card.path);
                              }}
                            >
                              Go to {card.title}
                            </Button>
                          </Box>
                        </Card>
                      </Grid>
                    )
                )}
              </Grid>
            </Container>
          ) : (
            <Outlet />
          )}
        </Box>
      </Box>
  );
};

export default Dashboard;