import React, { useState, useEffect } from "react";
import {
  Box, Typography, Card, CardContent,
  IconButton, Tabs, Tab, Chip, Divider, 
  Badge, CircularProgress, Avatar
} from "@mui/material";
import {
  DateRange, PriorityHigh, 
  Description, Category, CheckCircle, HourglassEmpty, 
  ThumbUp, Person, Assignment, CalendarToday, Alarm
} from "@mui/icons-material";
import { styled } from "@mui/material/styles";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import API_BASE_URL from "../config/apiConfig";

// Add axios interceptor for auth tokens
axios.interceptors.request.use(config => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const StyledCard = styled(Card)(({ theme }) => ({
  borderRadius: "12px",
  boxShadow: "0 4px 20px 0 rgba(0,0,0,0.08)",
  transition: "transform 0.3s, box-shadow 0.3s",
  "&:hover": {
    transform: "translateY(-5px)",
    boxShadow: "0 8px 30px 0 rgba(0,0,0,0.12)"
  }
}));

const StatusChip = styled(Chip)(({ status }) => ({
  backgroundColor:
    status === "COMPLETED"
      ? "#e8f5e9"
      : status === "IN_REVIEW"
      ? "#fff3e0"
      : "#e3f2fd",
  color:
    status === "COMPLETED"
      ? "#2e7d32"
      : status === "IN_REVIEW"
      ? "#e65100"
      : "#1565c0",
  fontWeight: 600,
  borderRadius: "8px"
}));

const MyTask = () => {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState("ALL");
  const navigate = useNavigate();

  // Fetch tasks assigned to current user
  useEffect(() => {
    const fetchMyTasks = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}/tasks`);
        setTasks(response.data);
      } catch (error) {
        console.error("Error fetching user tasks:", error);
      } finally {
        setLoading(false);
      }
    };
  
    fetchMyTasks();
  }, [tab]); // ✅ Re-fetches every time the tab changes
  
  
    

  const handleTabChange = (event, newTab) => {
    setTab(newTab);
  };

  const filteredTasks = tasks.filter(
    (task) => tab === "ALL" || task.status === tab
  );

  const getStatusIcon = (status) => {
    switch (status) {
      case "COMPLETED":
        return <CheckCircle fontSize="small" />;
      case "IN_REVIEW":
        return <ThumbUp fontSize="small" />;
      case "IN_PROGRESS":
        return <HourglassEmpty fontSize="small" />;
      default:
        return <Assignment fontSize="small" />;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "No due date";
    const date = new Date(dateString);
    return date.toLocaleDateString();
  };

  if (loading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh"
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, backgroundColor: "#f8fafc", minHeight: "100vh" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: 700 }}>
        My Tasks
      </Typography>
      <Typography variant="subtitle1" color="text.secondary" sx={{ mb: 3 }}>
        Tasks assigned to you
      </Typography>

      {/* Tabs for Task Status */}
      <Tabs
        value={tab}
        onChange={handleTabChange}
        textColor="primary"
        indicatorColor="primary"
        sx={{
          mb: 3,
          "& .MuiTabs-flexContainer": {
            gap: 1
          }
        }}
      >
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Badge badgeContent={tasks.length} color="primary" />
              All
            </Box>
          }
          value="ALL"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Assignment fontSize="small" />
              To Do
              <Badge
                badgeContent={tasks.filter((t) => t.status === "TODO").length}
                color="primary"
              />
            </Box>
          }
          value="TODO"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <HourglassEmpty fontSize="small" />
              In Progress
              <Badge
                badgeContent={tasks.filter((t) => t.status === "IN_PROGRESS").length}
                color="primary"
              />
            </Box>
          }
          value="IN_PROGRESS"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <ThumbUp fontSize="small" />
              In Review
              <Badge
                badgeContent={tasks.filter((t) => t.status === "IN_REVIEW").length}
                color="primary"
              />
            </Box>
          }
          value="IN_REVIEW"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CheckCircle fontSize="small" />
              Completed
              <Badge
                badgeContent={tasks.filter((t) => t.status === "COMPLETED").length}
                color="primary"
              />
            </Box>
          }
          value="COMPLETED"
          sx={{ borderRadius: "8px", textTransform: "none" }}
        />
      </Tabs>

      {/* Task Cards */}
      {filteredTasks.length === 0 ? (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "300px",
            backgroundColor: "white",
            borderRadius: "12px",
            boxShadow: "0 4px 20px 0 rgba(0,0,0,0.05)"
          }}
        >
          <Typography variant="h6" color="textSecondary">
            {tab === "ALL" 
              ? "You don't have any tasks assigned yet." 
              : `You don't have any ${tab.toLowerCase().replace('_', ' ')} tasks.`}
          </Typography>
        </Box>
      ) : (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, 1fr)",
              lg: "repeat(3, 1fr)"
            },
            gap: 3
          }}
        >
          {filteredTasks.map((task) => (
            <StyledCard key={task.id}>
              <CardContent>
                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                    mb: 2
                  }}
                >
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {task.title}
                  </Typography>
                  <StatusChip
                    label={task.status.toLowerCase().replace('_', ' ')}
                    status={task.status}
                    icon={getStatusIcon(task.status)}
                    size="small"
                  />
                </Box>

                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}
                >
                  <Category fontSize="small" /> {task.project?.name || "No project"}
                </Typography>

                <Typography
                  variant="body2"
                  sx={{ mb: 2 }}
                >
                  {task.description || "No description provided"}
                </Typography>

                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: "flex", alignItems: "center", gap: 1 }}
                  >
                    <CalendarToday fontSize="small" />
                    {formatDate(task.dueDate)}
                  </Typography>
                  <Chip
                    label={task.priority.toLowerCase()}
                    size="small"
                    sx={{
                      backgroundColor:
                        task.priority === "HIGH"
                          ? "#ffebee"
                          : task.priority === "MEDIUM"
                          ? "#fff3e0"
                          : "#e8f5e9",
                      color:
                        task.priority === "HIGH"
                          ? "#c62828"
                          : task.priority === "MEDIUM"
                          ? "#e65100"
                          : "#2e7d32"
                    }}
                  />
                </Box>

                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mt: 2 }}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <Alarm fontSize="small" />
                    <Typography variant="body2">
                      {task.estimatedHours || 0}h estimated
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </StyledCard>
          ))}
        </Box>
      )}
    </Box>
  );
};

export default MyTask;