import React, { useState, useEffect, useContext } from "react";
import { 
    Box, 
    Typography, 
    Card, 
    CardContent, 
    CardHeader, 
    Avatar, 
    List, 
    ListItem, 
    ListItemAvatar, 
    ListItemText, 
    Divider, 
    Chip, 
    CircularProgress,
    Accordion,
    AccordionSummary,
    AccordionDetails,
    Paper,
    useTheme
} from "@mui/material";
import { 
    People as PeopleIcon,
    ExpandMore as ExpandMoreIcon,
    Work as WorkIcon,
    Email as EmailIcon,
    Business as DepartmentIcon
} from "@mui/icons-material";
import axios from "axios";
import { userContext } from "../context/ContextProvider";
import API_BASE_URL from "../config/apiConfig";

const MyTeam = () => {
    const [projectsWithTeams, setProjectsWithTeams] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const { activeRole, actions } = useContext(userContext);
const role = activeRole?.toLowerCase();
    const theme = useTheme();

    useEffect(() => {
        const fetchMyTeams = async () => {
            try {
                const token = localStorage.getItem("token");
                if (!token) {
                    throw new Error("No authentication token found");
                }

                // Get manager's profile to get their empId
                const profileResponse = await axios.get(`${API_BASE_URL}/get-complete-profile`, {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                });

                const managerId = profileResponse.data.employeeData.personal.empId;

                // Fetch projects with teams
                const response = await axios.get(`${API_BASE_URL}/projects/by-manager/${managerId}`, {
                    headers: {
                        Authorization: `Bearer ${token}`
                    }
                });

                setProjectsWithTeams(response.data);
            } catch (err) {
                console.error("Error fetching my teams:", err);
                setError(err.message || "Failed to load team data");
            } finally {
                setLoading(false);
            }
        };

        fetchMyTeams();
    }, []);

    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '300px' }}>
                <CircularProgress size={60} />
            </Box>
        );
    }

    if (error) {
        return (
            <Box sx={{ p: 3 }}>
                <Typography color="error">{error}</Typography>
            </Box>
        );
    }

    if (projectsWithTeams.length === 0) {
        return (
            <Box sx={{ p: 3, textAlign: 'center' }}>
                <Typography variant="h6">No projects assigned to you yet</Typography>
                <Typography variant="body1" color="textSecondary">
                    You'll see your team members here once you're assigned to projects and team members are assigned to those projects.
                </Typography>
            </Box>
        );
    }

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h4" gutterBottom sx={{ fontWeight: 700, mb: 3 }}>
                My Teams
            </Typography>

            {projectsWithTeams.map((project) => (
                <Accordion 
                    key={project.projectId} 
                    defaultExpanded
                    sx={{ 
                        mb: 3,
                        borderRadius: '12px',
                        overflow: 'hidden',
                        boxShadow: theme.shadows[3],
                        '&:before': {
                            display: 'none'
                        }
                    }}
                >
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon />}
                        sx={{
                            backgroundColor: theme.palette.primary.light,
                            color: theme.palette.primary.contrastText,
                            '& .MuiAccordionSummary-content': {
                                alignItems: 'center'
                            }
                        }}
                    >
                        <WorkIcon sx={{ mr: 2 }} />
                        <Typography variant="h6" sx={{ fontWeight: 600 }}>
                            {project.projectName}
                        </Typography>
                        <Chip 
                            label={project.projectStatus} 
                            size="small" 
                            sx={{ 
                                ml: 2,
                                backgroundColor: 
                                    project.projectStatus === 'COMPLETED' ? 
                                    theme.palette.success.main : 
                                    theme.palette.primary.main,
                                color: 'white'
                            }} 
                        />
                    </AccordionSummary>
                    <AccordionDetails sx={{ p: 0 }}>
                        {project.teamMembers.length > 0 ? (
                            <List>
                                {project.teamMembers.map((member, index) => (
                                    <React.Fragment key={member.empId}>
                                        <ListItem sx={{ p: 3 }}>
                                            <ListItemAvatar>
                                                <Avatar sx={{ bgcolor: theme.palette.secondary.main }}>
                                                    {member.empName.charAt(0)}
                                                </Avatar>
                                            </ListItemAvatar>
                                            <ListItemText
                                                primary={member.empName}
                                                secondary={
                                                    <Box sx={{ display: 'flex', flexDirection: 'column', mt: 1 }}>
                                                        <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5 }}>
                                                            <EmailIcon fontSize="small" sx={{ mr: 1, color: theme.palette.text.secondary }} />
                                                            <Typography variant="body2" color="textSecondary">
                                                                {member.email || 'N/A'}
                                                            </Typography>
                                                        </Box>
                                                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                                            <DepartmentIcon fontSize="small" sx={{ mr: 1, color: theme.palette.text.secondary }} />
                                                            <Typography variant="body2" color="textSecondary">
                                                                {member.department || 'N/A'}
                                                            </Typography>
                                                        </Box>
                                                    </Box>
                                                }
                                            />
                                            <Chip 
                                                label={member.empId} 
                                                size="small" 
                                                variant="outlined" 
                                                sx={{ mr: 2 }} 
                                            />
                                        </ListItem>
                                        {index < project.teamMembers.length - 1 && <Divider />}
                                    </React.Fragment>
                                ))}
                            </List>
                        ) : (
                            <Box sx={{ p: 3, textAlign: 'center' }}>
                                <PeopleIcon sx={{ fontSize: 60, color: theme.palette.text.disabled, mb: 2 }} />
                                <Typography variant="body1" color="textSecondary">
                                    No team members assigned to this project yet
                                </Typography>
                            </Box>
                        )}
                    </AccordionDetails>
                </Accordion>
            ))}
        </Box>
    );
};

export default MyTeam;