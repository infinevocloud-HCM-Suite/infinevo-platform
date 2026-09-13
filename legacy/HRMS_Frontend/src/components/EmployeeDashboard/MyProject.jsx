import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  TextField,
  Card,
  CardContent,
  Tabs,
  Tab,
  Chip,
  Divider,
  CircularProgress,
  Snackbar,
  Alert,
  List,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  IconButton,
  ListItem,
  ListItemText,
  Avatar,
  ListItemAvatar,
  Badge,
  Paper,
  Tooltip
} from "@mui/material";
import {
  DateRange,
  Category,
  CheckCircle,
  HourglassEmpty,
  People,
  Work,
  Person,
  Visibility,
  Description,
  Close,
  ExpandMore,
  Info
} from "@mui/icons-material";
import { styled } from "@mui/material/styles";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import API_BASE_URL from "../config/apiConfig";

// Configure axios
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
  },
  background: "linear-gradient(135deg, #ffffff 0%, #f8fafc 100%)",
  border: "1px solid rgba(0, 0, 0, 0.05)"
}));

const StatusChip = styled(Chip)(({ status, theme }) => ({
  backgroundColor:
    status === "COMPLETED" ? "#e8f5e9" : "#e3f2fd",
  color:
    status === "COMPLETED" ? "#2e7d32" : "#1565c0",
  fontWeight: 600,
  borderRadius: "8px",
  padding: theme.spacing(0.5)
}));

const TeamMemberModal = styled(Dialog)(({ theme }) => ({
  "& .MuiDialog-paper": {
    borderRadius: "16px",
    padding: theme.spacing(2),
    minWidth: "400px",
    background: "linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)"
  }
}));

const DescriptionModal = styled(Dialog)(({ theme }) => ({
  "& .MuiDialog-paper": {
    borderRadius: "16px",
    padding: theme.spacing(3),
    maxWidth: "600px",
    width: "100%",
    background: "linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)"
  }
}));

const ExpandableText = styled(Box)({
  display: "-webkit-box",
  WebkitBoxOrient: "vertical",
  WebkitLineClamp: 2,
  overflow: "hidden",
  textOverflow: "ellipsis",
  cursor: "pointer"
});

const MyProject = () => {
  const [projects, setProjects] = useState([]);
  const [filteredProjects, setFilteredProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [tab, setTab] = useState("ALL");
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "info"
  });
  const [currentEmployee, setCurrentEmployee] = useState(null);
  const [openTeamModal, setOpenTeamModal] = useState(false);
  const [currentTeam, setCurrentTeam] = useState([]);
  const [openDescModal, setOpenDescModal] = useState(false);
  const [currentDescription, setCurrentDescription] = useState("");
  const [projectName, setProjectName] = useState("");
  const navigate = useNavigate();

  // Filter projects based on tab and search query
  useEffect(() => {
    let result = projects;

    // Apply tab filter
    if (tab !== "ALL") {
      result = result.filter(project => project.status === tab);
    }

    // Apply search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(project =>
        project.name.toLowerCase().includes(query) ||
        (project.id && project.id.toString().includes(query))
      );
    }

    setFilteredProjects(result);
  }, [projects, tab, searchQuery]);

  // Fetch all required data
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        // Get projects assigned to this employee
        const projectsResponse = await axios.get(`${API_BASE_URL}/projects/by-emp`);

        // Transform the data to match our frontend structure
        const transformedProjects = projectsResponse.data.map(project => ({
          id: project.id,
          name: project.name,
          category: project.category,
          startDate: project.startDate,
          description: project.description,
          endDate: project.endDate,
          status: project.status,
          managerId: project.managerId,
          managerName: project.managerName,
          teamMembers: project.assignedEmployeeNames?.map((name, index) => ({
            empName: name
          })) || []
        }));

        setProjects(transformedProjects);
      } catch (error) {
        console.error("Error fetching data:", error);
        setSnackbar({
          open: true,
          message: error.response?.data?.message || "Failed to fetch projects",
          severity: "error"
        });
        if (error.response?.status === 401) {
          navigate("/login");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [navigate]);

  const handleSearchChange = (e) => setSearchQuery(e.target.value);
  const handleTabChange = (e, newValue) => setTab(newValue);

  const getStatusIcon = (status) => {
    switch (status) {
      case "COMPLETED": return <CheckCircle fontSize="small" />;
      default: return <HourglassEmpty fontSize="small" />;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "Not set";
    const date = new Date(dateString);
    return isNaN(date.getTime()) ? "Invalid date" : date.toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric"
    });
  };

  const handleCloseSnackbar = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  const handleOpenTeamModal = (team, projectName) => {
    setCurrentTeam(team);
    setProjectName(projectName);
    setOpenTeamModal(true);
  };

  const handleCloseTeamModal = () => {
    setOpenTeamModal(false);
  };

  const handleOpenDescModal = (description, projectName) => {
    setCurrentDescription(description || "No description provided");
    setProjectName(projectName);
    setOpenDescModal(true);
  };

  const handleCloseDescModal = () => {
    setOpenDescModal(false);
  };

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3, backgroundColor: "#f8fafc", minHeight: "100vh" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: 700, color: "#2d3748" }}>
        My Projects
      </Typography>

      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3, gap: 2, flexWrap: "wrap" }}>
        <TextField
          variant="outlined"
          size="small"
          placeholder="Search projects by name or ID..."
          value={searchQuery}
          onChange={handleSearchChange}
          sx={{
            width: 400,
            "& .MuiOutlinedInput-root": {
              borderRadius: "10px",
              backgroundColor: "white",
              boxShadow: "0 2px 10px rgba(0,0,0,0.05)"
            }
          }}
          InputProps={{
            startAdornment: <Work sx={{ mr: 1, color: "primary.main" }} />
          }}
        />
      </Box>

      <Tabs
        value={tab}
        onChange={handleTabChange}
        textColor="primary"
        indicatorColor="primary"
        sx={{ 
          mb: 3, 
          "& .MuiTabs-flexContainer": { gap: 1 },
          "& .MuiTab-root": {
            minHeight: 48,
            padding: "12px 16px"
          }
        }}
      >
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <Badge badgeContent={projects.length} color="primary" />
              All
            </Box>
          }
          value="ALL"
          sx={{ borderRadius: "8px", textTransform: "none", fontWeight: 600 }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <HourglassEmpty fontSize="small" />
              Started
              <Badge
                badgeContent={projects.filter(p => p.status === "STARTED").length}
                color="primary"
              />
            </Box>
          }
          value="STARTED"
          sx={{ borderRadius: "8px", textTransform: "none", fontWeight: 600 }}
        />
        <Tab
          label={
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CheckCircle fontSize="small" />
              Completed
              <Badge
                badgeContent={projects.filter(p => p.status === "COMPLETED").length}
                color="primary"
              />
            </Box>
          }
          value="COMPLETED"
          sx={{ borderRadius: "8px", textTransform: "none", fontWeight: 600 }}
        />
      </Tabs>

      {filteredProjects.length === 0 ? (
        <Box sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "300px",
          backgroundColor: "white",
          borderRadius: "12px",
          boxShadow: "0 4px 20px 0 rgba(0,0,0,0.05)"
        }}>
          <Typography variant="h6" color="textSecondary">
            {tab === "ALL"
              ? "No projects assigned to you"
              : `No ${tab.toLowerCase()} projects found`}
          </Typography>
        </Box>
      ) : (
        <Box sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            sm: "repeat(2, 1fr)",
            lg: "repeat(3, 1fr)"
          },
          gap: 3
        }}>
          {filteredProjects.map((project) => (
            <StyledCard key={project.id}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: "#2d3748" }}>
                    {project.name}
                  </Typography>
                  <StatusChip
                    label={project.status.toLowerCase()}
                    status={project.status}
                    icon={getStatusIcon(project.status)}
                    size="small"
                  />
                </Box>

                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" sx={{ 
                    display: "flex", 
                    alignItems: "center", 
                    gap: 1, 
                    mb: 1,
                    color: "#4a5568"
                  }}>
                    <Category fontSize="small" color="primary" /> 
                    {project.category || "No category"}
                  </Typography>

                  <Typography variant="body2" sx={{ 
                    display: "flex", 
                    alignItems: "center", 
                    gap: 1, 
                    mb: 1,
                    color: "#4a5568"
                  }}>
                    <DateRange fontSize="small" color="primary" />
                    {formatDate(project.startDate)} - {formatDate(project.endDate)}
                  </Typography>
                </Box>

                <Paper elevation={0} sx={{ 
                  p: 2, 
                  mb: 2, 
                  borderRadius: "8px",
                  backgroundColor: "#f8fafc",
                  border: "1px solid rgba(0, 0, 0, 0.05)"
                }}>
                  <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
                    <Description fontSize="small" color="primary" />
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="subtitle2" sx={{ mb: 0.5, color: "#4a5568" }}>
                        Description
                      </Typography>
                      {project.description ? (
                        <>
                          <ExpandableText onClick={() => handleOpenDescModal(project.description, project.name)}>
                            <Typography variant="body2" sx={{ color: "#4a5568" }}>
                              {project.description}
                            </Typography>
                          </ExpandableText>
                          <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                            <Tooltip title="View full description">
                              <IconButton 
                                size="small" 
                                onClick={() => handleOpenDescModal(project.description, project.name)}
                                sx={{ color: "primary.main", mt: 0.5 }}
                              >
                                <ExpandMore fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Box>
                        </>
                      ) : (
                        <Typography variant="body2" sx={{ color: "#718096", fontStyle: "italic" }}>
                          No description provided
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </Paper>

                <Paper elevation={0} sx={{ 
                  p: 2, 
                  mb: 2, 
                  borderRadius: "8px",
                  backgroundColor: "#f8fafc",
                  border: "1px solid rgba(0, 0, 0, 0.05)"
                }}>
                  <Typography variant="body2" sx={{ 
                    display: "flex", 
                    alignItems: "center", 
                    gap: 1, 
                    mb: 1,
                    color: "#4a5568"
                  }}>
                    <Person fontSize="small" color="primary" />
                    <Box>
                      <Typography variant="subtitle2" sx={{ color: "#4a5568" }}>
                        Project Manager
                      </Typography>
                      <Typography variant="body2">
                        {project.managerName} ({project.managerId})
                      </Typography>
                    </Box>
                  </Typography>
                </Paper>

                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <Typography variant="subtitle2" sx={{ color: "#4a5568" }}>
                    <People fontSize="small" sx={{ verticalAlign: "middle", mr: 1, color: "primary.main" }} />
                    Team Member ({project.teamMembers?.length || 0})
                  </Typography>
                  {project.teamMembers?.length > 0 && (
                    <Tooltip title="View team members">
                      <IconButton 
                        size="small" 
                        onClick={() => handleOpenTeamModal(project.teamMembers, project.name)}
                        sx={{ color: "primary.main" }}
                      >
                        <Visibility fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </Box>
              </CardContent>
            </StyledCard>
          ))}
        </Box>
      )}

      {/* Team Member Modal */}
      <TeamMemberModal open={openTeamModal} onClose={handleCloseTeamModal}>
        <DialogTitle sx={{ 
          display: "flex", 
          justifyContent: "space-between", 
          alignItems: "center",
          backgroundColor: "#f8fafc",
          borderBottom: "1px solid rgba(0, 0, 0, 0.08)"
        }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Team Members - {projectName}
          </Typography>
          <IconButton onClick={handleCloseTeamModal}>
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {currentTeam.length > 0 ? (
            <List sx={{ pt: 0 }}>
              {currentTeam.map((member, index) => (
                <ListItem key={index} sx={{ px: 0 }}>
                  <ListItemAvatar>
                    <Avatar sx={{ 
                      width: 40, 
                      height: 40, 
                      fontSize: 16,
                      bgcolor: "primary.main",
                      color: "white"
                    }}>
                      {member.empName?.split(" ").map(n => n[0]).join("") || "?"}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={member.empName || "Unknown member"}
                    primaryTypographyProps={{ fontWeight: 500 }}
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Box sx={{ 
              display: "flex", 
              flexDirection: "column", 
              alignItems: "center", 
              justifyContent: "center", 
              p: 4,
              textAlign: "center"
            }}>
              <Info color="disabled" sx={{ fontSize: 48, mb: 2 }} />
              <Typography variant="h6" color="textSecondary">
                No team members assigned
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ 
          backgroundColor: "#f8fafc",
          borderTop: "1px solid rgba(0, 0, 0, 0.08)"
        }}>
          <Button 
            onClick={handleCloseTeamModal} 
            color="primary"
            variant="contained"
            sx={{ borderRadius: "8px" }}
          >
            Close
          </Button>
        </DialogActions>
      </TeamMemberModal>

      {/* Description Modal */}
      <DescriptionModal open={openDescModal} onClose={handleCloseDescModal}>
        <DialogTitle sx={{ 
          display: "flex", 
          justifyContent: "space-between", 
          alignItems: "center",
          backgroundColor: "#f8fafc",
          borderBottom: "1px solid rgba(0, 0, 0, 0.08)"
        }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Project Description - {projectName}
          </Typography>
          <IconButton onClick={handleCloseDescModal}>
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Typography variant="body1" sx={{ 
            whiteSpace: "pre-line",
            p: 2,
            lineHeight: 1.6,
            color: "#4a5568"
          }}>
            {currentDescription}
          </Typography>
        </DialogContent>
        <DialogActions sx={{ 
          backgroundColor: "#f8fafc",
          borderTop: "1px solid rgba(0, 0, 0, 0.08)"
        }}>
          <Button 
            onClick={handleCloseDescModal} 
            color="primary"
            variant="contained"
            sx={{ borderRadius: "8px" }}
          >
            Close
          </Button>
        </DialogActions>
      </DescriptionModal>

      <Snackbar open={snackbar.open} autoHideDuration={6000} onClose={handleCloseSnackbar}>
        <Alert onClose={handleCloseSnackbar} severity={snackbar.severity} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default MyProject;