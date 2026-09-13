import React, { useEffect, useState } from "react";
import axios from "axios";
import {
  Card,
  CardContent,
  Typography,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  CircularProgress,
  Box,
  Tooltip,
  Avatar,
  Chip,
  Paper,
  useTheme,
  Skeleton,
  Stack,
  IconButton
} from '@mui/material';
import {
  Person as PersonIcon,
  Email as EmailIcon,
  Badge as BadgeIcon,
  Info as InfoIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import API_BASE_URL from "../config/apiConfig";

const ReportingTeam = () => {
  // Keep all existing state and API logic exactly the same
  const [teamMembers, setTeamMembers] = useState([]);
  const [projectsMap, setProjectsMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const theme = useTheme();

  useEffect(() => {
    const fetchTeamAndProjects = async () => {
      try {
        const token = localStorage.getItem("token");
        if (!token) {
          throw new Error("🔐 No authentication token found in localStorage");
        }

        const teamRes = await axios.get(`${API_BASE_URL}/employees/reporting-manager`, {
          headers: {
            Authorization: `Bearer ${token}`
          }
        });

        const members = teamRes.data;
        setTeamMembers(members);

        // Fetch projects for each employee (unchanged)
        const projectsResults = await Promise.all(
          members.map(async (member) => {
            try {
              const res = await axios.get(`${API_BASE_URL}/projects/by-employee/${member.empId}`, {
                headers: {
                  Authorization: `Bearer ${token}`
                }
              });
              return { empId: member.empId, projects: res.data };
            } catch (err) {
              console.error(`❌ Failed to fetch projects for ${member.empId}`, err);
              return { empId: member.empId, projects: [] };
            }
          })
        );

        const projectMap = {};
        projectsResults.forEach(entry => {
          projectMap[entry.empId] = entry.projects;
        });

        setProjectsMap(projectMap);
      } catch (err) {
        console.error("❌ Error fetching team or projects:", err);
        setError(err.message || "Failed to load team/project data");
      } finally {
        setLoading(false);
      }
    };

    fetchTeamAndProjects();
  }, []);

  const getStatusColor = (status) => {
    switch (status?.toLowerCase()) {
      case 'active': return 'success';
      case 'completed': return 'primary';
      case 'pending': return 'warning';
      default: return 'default';
    }
  };

  if (loading) {
    return (
      <Box textAlign="center" mt={4}>
        <CircularProgress />
        <Typography variant="body1" mt={2}>Loading team members...</Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box textAlign="center" mt={4}>
        <Typography color="error">{error}</Typography>
      </Box>
    );
  }

  // Only design changes below this point
  return (
    <Paper 
      elevation={0}
      sx={{
        borderRadius: 3,
        p: 3,
        border: `1px solid ${theme.palette.divider}`,
        bgcolor: 'background.paper'
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="600">
          My Reporting Team
        </Typography>
        <IconButton onClick={() => window.location.reload()} size="small">
          <RefreshIcon />
        </IconButton>
      </Stack>

      {teamMembers.length === 0 ? (
        <Box 
          textAlign="center" 
          p={4} 
          sx={{ 
            bgcolor: 'background.default', 
            borderRadius: 2,
            border: `1px dashed ${theme.palette.divider}`
          }}
        >
          <Typography variant="h6" color="textSecondary">
            No Team Members Found
          </Typography>
        </Box>
      ) : (
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.hover' }}>
              <TableCell sx={{ fontWeight: '600' }}>Employee</TableCell>
              <TableCell sx={{ fontWeight: '600' }}>Email</TableCell>
              <TableCell sx={{ fontWeight: '600' }}>Projects</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {teamMembers.map((member) => (
              <TableRow key={member.userId} hover>
                <TableCell>
                  <Stack direction="row" alignItems="center" spacing={2}>
                    <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                      {member.name.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography fontWeight="500">{member.name}</Typography>
                      <Typography variant="body2" color="textSecondary">
                        ID: {member.empId}
                      </Typography>
                    </Box>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Stack spacing={1}>
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <EmailIcon fontSize="small" color="action" />
                      <Typography variant="body2">{member.email}</Typography>
                    </Stack>
                  </Stack>
                </TableCell>
                <TableCell>
                  {projectsMap[member.empId] && projectsMap[member.empId].length > 0 ? (
                    <Stack spacing={1.5}>
                      {projectsMap[member.empId].map((proj) => (
                        <Tooltip
                          key={proj.id}
                          title={
                            <Box p={1}>
                              <Typography variant="subtitle2">{proj.name}</Typography>
                              <Typography variant="body2" mt={1}>
                                <strong>Status:</strong> {proj.status || "N/A"}
                              </Typography>
                              <Typography variant="body2">
                                <strong>Description:</strong> {proj.description || "No description"}
                              </Typography>
                            </Box>
                          }
                          arrow
                        >
                          <Chip
                            label={
                              <Box>
                                <Typography component="span" fontWeight="500">
                                  {proj.name}
                                </Typography>
                                <Typography 
                                  component="span" 
                                  variant="body2" 
                                  color="textSecondary"
                                  ml={1}
                                >
                                  ({proj.status})
                                </Typography>
                              </Box>
                            }
                            variant="outlined"
                            color={getStatusColor(proj.status)}
                            sx={{ borderRadius: 1 }}
                          />
                        </Tooltip>
                      ))}
                    </Stack>
                  ) : (
                    <Chip
                      label="No projects assigned"
                      variant="outlined"
                      icon={<InfoIcon />}
                    />
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Paper>
  );
};

export default ReportingTeam;