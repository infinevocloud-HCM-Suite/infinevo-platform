import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  Divider,
  Chip,
  TextField,
  InputAdornment,
  CircularProgress,
  Alert,
} from "@mui/material";
import { Search, Event } from "@mui/icons-material";
import axios from "axios";
import { format, parseISO, isAfter, isThisYear } from "date-fns";
import API_BASE_URL from "../config/apiConfig";

const HolidayUser = () => {
  const [holidays, setHolidays] = useState([]);
  const [upcomingHolidays, setUpcomingHolidays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    fetchHolidays();
  }, []);

  const fetchHolidays = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${API_BASE_URL}/holidays/upcoming`);
      const allHolidays = await axios.get(`${API_BASE_URL}/holidays`);
      
      setHolidays(allHolidays.data);
      setUpcomingHolidays(response.data);
      setLoading(false);
    } catch (err) {
      setError("Failed to fetch holidays");
      setLoading(false);
    }
  };

  const handleSearchChange = (e) => {
    setSearchQuery(e.target.value);
  };

  const filteredHolidays = holidays.filter((holiday) =>
    holiday.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const formatDate = (dateString) => {
    const date = parseISO(dateString);
    return format(date, "MMMM d, yyyy");
  };

  const isUpcoming = (dateString) => {
    const date = parseISO(dateString);
    return isAfter(date, new Date()) && isThisYear(date);
  };

  return (
    <Box sx={{ p: 3, backgroundColor: "#f8fafc", minHeight: "100vh" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: "bold", color: "#2d3748" }}>
        Company Holidays
      </Typography>

      {/* Upcoming Holidays Card */}
      <Card sx={{ mb: 4, boxShadow: 3, borderRadius: 2 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2, fontWeight: "bold", display: "flex", alignItems: "center" }}>
            <Event color="primary" sx={{ mr: 1 }} /> Upcoming Holidays
          </Typography>
          {loading ? (
            <Box sx={{ display: "flex", justifyContent: "center" }}>
              <CircularProgress />
            </Box>
          ) : error ? (
            <Alert severity="error">{error}</Alert>
          ) : upcomingHolidays.length === 0 ? (
            <Typography variant="body1" color="text.secondary">
              No upcoming holidays this year.
            </Typography>
          ) : (
            <List>
              {upcomingHolidays.map((holiday, index) => (
                <React.Fragment key={holiday.id}>
                  <ListItem>
                    <ListItemText
                      primary={
                        <Box sx={{ display: "flex", alignItems: "center" }}>
                          <Chip
                            label={holiday.day}
                            color="primary"
                            size="small"
                            sx={{ mr: 2 }}
                          />
                          <Typography variant="body1" sx={{ fontWeight: "medium" }}>
                            {holiday.name}
                          </Typography>
                        </Box>
                      }
                      secondary={formatDate(holiday.date)}
                    />
                  </ListItem>
                  {index < upcomingHolidays.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          )}
        </CardContent>
      </Card>

      {/* All Holidays Section */}
      <Box sx={{ mb: 2 }}>
        <TextField
          fullWidth
          variant="outlined"
          placeholder="Search holidays..."
          value={searchQuery}
          onChange={handleSearchChange}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search />
              </InputAdornment>
            ),
            sx: { borderRadius: 2, backgroundColor: "white" },
          }}
        />
      </Box>

      <Card sx={{ boxShadow: 3, borderRadius: 2 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2, fontWeight: "bold" }}>
            All Holidays
          </Typography>
          {loading ? (
            <Box sx={{ display: "flex", justifyContent: "center" }}>
              <CircularProgress />
            </Box>
          ) : error ? (
            <Alert severity="error">{error}</Alert>
          ) : filteredHolidays.length === 0 ? (
            <Typography variant="body1" color="text.secondary">
              No holidays found.
            </Typography>
          ) : (
            <List>
              {filteredHolidays.map((holiday, index) => (
                <React.Fragment key={holiday.id}>
                  <ListItem>
                    <ListItemText
                      primary={
                        <Box sx={{ display: "flex", alignItems: "center" }}>
                          <Chip
                            label={holiday.day}
                            color={isUpcoming(holiday.date) ? "primary" : "default"}
                            size="small"
                            sx={{ mr: 2 }}
                          />
                          <Typography variant="body1" sx={{ fontWeight: "medium" }}>
                            {holiday.name}
                          </Typography>
                        </Box>
                      }
                      secondary={formatDate(holiday.date)}
                    />
                  </ListItem>
                  {index < filteredHolidays.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

export default HolidayUser;