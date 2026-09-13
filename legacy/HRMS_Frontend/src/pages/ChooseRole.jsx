// src/pages/ChooseRole.jsx
import React, { useContext, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Container,
  Card,
  CardContent,
  Typography,
  Button,
  CircularProgress,
  Box,
  Alert,
} from "@mui/material";
import axios from "axios";
import API_BASE_URL from "../components/config/apiConfig";
import { userContext } from "../components/context/ContextProvider";
import AdminPanelSettingsIcon from "@mui/icons-material/AdminPanelSettings";
import PeopleIcon from "@mui/icons-material/People";
import WorkIcon from "@mui/icons-material/Work";
import SupervisorAccountIcon from "@mui/icons-material/SupervisorAccount";
import PersonIcon from "@mui/icons-material/Person";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";

function ChooseRole() {
  const { roles, updateAuthState } = useContext(userContext);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleRoleSelect = async (role) => {
    setLoading(true);
    setError("");

    try {
      const token = localStorage.getItem("token");
      if (!token) throw new Error("No token found. Please login again.");

      // 🚀 Call backend to switch role
      const response = await axios.post(
        `${API_BASE_URL}/auth/switch-role`,
        { role },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      if (response.data.statusCode === 200) {
        const newToken = response.data.token;
        const activeRole = response.data.activeRole?.toLowerCase() || role.toLowerCase();
        const roles = (response.data.roles || []).map(r => r.toLowerCase());
        const actions = response.data.actions || [];

        // ✅ Store consistently before navigation
        localStorage.setItem("token", newToken);
        localStorage.setItem("activeRole", activeRole);
        localStorage.setItem("roles", JSON.stringify(roles));
        localStorage.setItem("actions", JSON.stringify(actions));

        const newAuthState = {
          roles,
          activeRole,
          actions,
          authenticated: true,
        };

        localStorage.setItem("authState", JSON.stringify(newAuthState));

        // ✅ Update context BEFORE navigation
        updateAuthState(newAuthState);

        // 🕒 Delay navigation slightly to ensure state sync
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
        setError(response.data.message || "Role switch failed.");
      }
    } catch (err) {
      console.error("Role switch failed:", err);
      setError(err.message || "Something went wrong while switching role.");
    } finally {
      setLoading(false);
    }
  };

  const getRoleConfig = (role) => {
    const roleLower = role.toLowerCase();
    const configs = {
      admin: {
        icon: AdminPanelSettingsIcon,
        color: "#dc2626",
        gradient: "linear-gradient(135deg, #dc2626 0%, #ef4444 100%)",
        description: "System Administration"
      },
      hr: {
        icon: PeopleIcon,
        color: "#2563eb",
        gradient: "linear-gradient(135deg, #2563eb 0%, #3b82f6 100%)",
        description: "Human Resources"
      },
      manager: {
        icon: WorkIcon,
        color: "#059669",
        gradient: "linear-gradient(135deg, #059669 0%, #10b981 100%)",
        description: "Team Management"
      },
      supervisor: {
        icon: SupervisorAccountIcon,
        color: "#7c3aed",
        gradient: "linear-gradient(135deg, #7c3aed 0%, #8b5cf6 100%)",
        description: "Operations Oversight"
      },
      "reporting manager": {
        icon: WorkIcon,
        color: "#ea580c",
        gradient: "linear-gradient(135deg, #ea580c 0%, #f97316 100%)",
        description: "Reporting & Analytics"
      },
      default: {
        icon: PersonIcon,
        color: "#0891b2",
        gradient: "linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)",
        description: "Employee Portal"
      }
    };
    return configs[roleLower] || configs.default;
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        background: "linear-gradient(135deg, #e0e7ff 0%, #f3e8ff 100%)",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        p: 3,
        position: "relative",
        "&::before": {
          content: '""',
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: "radial-gradient(circle at 20% 20%, rgba(139, 92, 246, 0.08) 0%, transparent 50%), radial-gradient(circle at 80% 80%, rgba(59, 130, 246, 0.08) 0%, transparent 50%)",
          pointerEvents: "none",
        }
      }}
    >
      {/* Glassmorphic container */}
      <Box
        sx={{
          maxWidth: "1000px",
          width: "100%",
          background: "rgba(255, 255, 255, 0.98)",
          backdropFilter: "blur(10px)",
          borderRadius: "32px",
          p: { xs: 3, md: 5 },
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.15)",
          border: "1px solid rgba(255,255,255,0.8)",
          position: "relative",
          zIndex: 1,
        }}
      >
        {/* Header */}
        <Box sx={{ textAlign: "center", mb: 5 }}>
          <Typography
            variant="h3"
            sx={{
              fontWeight: 700,
              background: "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)",
              backgroundClip: "text",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              mb: 1.5,
              letterSpacing: "-0.02em",
              fontSize: { xs: "2rem", md: "2.5rem" },
            }}
          >
            Welcome Back
          </Typography>
          <Typography
            variant="body1"
            sx={{ 
              color: "#64748b", 
              fontWeight: 500,
              fontSize: "1rem",
            }}
          >
            Select your role to access your workspace
          </Typography>
        </Box>

        {/* Error message */}
        {error && (
          <Alert
            severity="error"
            sx={{
              mb: 3,
              borderRadius: "12px",
              "& .MuiAlert-icon": { alignItems: "center" }
            }}
          >
            {error}
          </Alert>
        )}

        {/* Role cards */}
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            justifyContent: "center",
            gap: 3,
            maxWidth: "100%",
          }}
        >
          {roles.map((role) => {
            const config = getRoleConfig(role);
            const IconComp = config.icon;

            return (
              <Card
                key={role}
                onClick={() => handleRoleSelect(role)}
                sx={{
                  position: "relative",
                  width: { xs: "100%", sm: "220px" },
                  maxWidth: "220px",
                  borderRadius: "18px",
                  overflow: "hidden",
                  cursor: "pointer",
                  border: "2px solid #f1f5f9",
                  background: "white",
                  transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
                  "&:hover": {
                    transform: "translateY(-8px) scale(1.02)",
                    boxShadow: `0 20px 40px -12px ${config.color}50`,
                    borderColor: config.color,
                    "& .role-icon": {
                      transform: "scale(1.1) rotate(-5deg)",
                    },
                    "& .role-arrow": {
                      opacity: 1,
                      transform: "translateX(0)",
                    },
                    "& .role-badge": {
                      transform: "scale(1.05)",
                    }
                  },
                }}
              >
                {/* Gradient accent bar */}
                <Box
                  sx={{
                    height: "5px",
                    background: config.gradient,
                  }}
                />

                <CardContent
                  sx={{
                    p: 3,
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    textAlign: "center",
                    minHeight: "190px",
                  }}
                >
                  {/* Icon with gradient background */}
                  <Box
                    className="role-badge"
                    sx={{
                      position: "relative",
                      mb: 2,
                      transition: "all 0.3s ease",
                    }}
                  >
                    <Box
                      className="role-icon"
                      sx={{
                        width: 70,
                        height: 70,
                        borderRadius: "18px",
                        background: config.gradient,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        transition: "all 0.3s ease",
                        boxShadow: `0 8px 20px ${config.color}35`,
                      }}
                    >
                      <IconComp sx={{ fontSize: 36, color: "white" }} />
                    </Box>
                  </Box>

                  {/* Role name */}
                  <Typography
                    variant="h6"
                    sx={{
                      fontWeight: 700,
                      color: "#1e293b",
                      mb: 0.5,
                      textTransform: "capitalize",
                      fontSize: "1.125rem",
                    }}
                  >
                    {loading ? (
                      <CircularProgress size={22} sx={{ color: config.color }} />
                    ) : (
                      role
                    )}
                  </Typography>

                  {/* Description */}
                  <Typography
                    variant="body2"
                    sx={{
                      color: "#64748b",
                      mb: 2,
                      fontSize: "0.875rem",
                      lineHeight: 1.5,
                    }}
                  >
                    {config.description}
                  </Typography>

                  {/* Arrow indicator */}
                  <Box
                    className="role-arrow"
                    sx={{
                      mt: "auto",
                      display: "flex",
                      alignItems: "center",
                      gap: 0.5,
                      color: config.color,
                      fontWeight: 600,
                      fontSize: "0.8125rem",
                      opacity: 0,
                      transform: "translateX(-10px)",
                      transition: "all 0.3s ease",
                    }}
                  >
                    Access
                    <ArrowForwardIcon sx={{ fontSize: 16 }} />
                  </Box>
                </CardContent>
              </Card>
            );
          })}
        </Box>

        {/* Footer */}
        <Box sx={{ textAlign: "center", mt: 5 }}>
          <Typography
            variant="caption"
            sx={{
              color: "#94a3b8",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 0.5,
            }}
          >
            Secure Access • Role-based Permissions
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}

export default ChooseRole;