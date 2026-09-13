import React, { useState, useEffect } from "react";
import {
  Box,
  Button,
  Typography,
  TextField,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  InputAdornment,
  Select,
  MenuItem,
  Snackbar,
  Alert,
  CircularProgress,
  Chip,
} from "@mui/material";
import { Search, Edit, Delete, Add, Close } from "@mui/icons-material";
import axios from "axios";
import { format, parseISO, getDay, addDays } from "date-fns";
import API_BASE_URL from "../config/apiConfig";

const daysOfWeek = [
  "Sunday",
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
];

const HolidayAdmin = () => {
  const [holidays, setHolidays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [entriesLimit, setEntriesLimit] = useState(10);
  const [openModal, setOpenModal] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [currentHoliday, setCurrentHoliday] = useState({
    id: null,
    name: "",
    date: format(new Date(), "yyyy-MM-dd"),
  });
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success",
  });

  useEffect(() => {
    fetchHolidays();
  }, []);

  const fetchHolidays = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${API_BASE_URL}/holidays`);
      setHolidays(response.data);
      setLoading(false);
    } catch (err) {
      setError("Failed to fetch holidays");
      setLoading(false);
      showSnackbar("Failed to fetch holidays", "error");
    }
  };

  const searchHolidays = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${API_BASE_URL}/holidays/search?query=${searchQuery}`);
      setHolidays(response.data);
      setLoading(false);
    } catch (err) {
      showSnackbar("Failed to search holidays", "error");
      setLoading(false);
    }
  };

  const handleSearchChange = (e) => {
    setSearchQuery(e.target.value);
    if (e.target.value === "") {
      fetchHolidays();
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      searchHolidays();
    }
  };

  const handleEntriesChange = (e) => {
    setEntriesLimit(e.target.value);
  };

  const handleOpenModal = () => {
    setEditMode(false);
    setCurrentHoliday({
      id: null,
      name: "",
      date: format(new Date(), "yyyy-MM-dd"),
    });
    setOpenModal(true);
  };

  const handleEditModal = (holiday) => {
    setEditMode(true);
    setCurrentHoliday({
      id: holiday.id,
      name: holiday.name,
      date: format(parseISO(holiday.date), "yyyy-MM-dd"),
    });
    setOpenModal(true);
  };

  const handleCloseModal = () => setOpenModal(false);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setCurrentHoliday((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async () => {
    if (!currentHoliday.name || !currentHoliday.date) {
      showSnackbar("Please fill out all fields", "error");
      return;
    }

    try {
      if (editMode) {
        await axios.put(`${API_BASE_URL}/holidays/${currentHoliday.id}`, {
          name: currentHoliday.name,
          date: currentHoliday.date,
        });
        showSnackbar("Holiday updated successfully", "success");
      } else {
        await axios.post(`${API_BASE_URL}/holidays/add`, {
          name: currentHoliday.name,
          date: currentHoliday.date,
        });
        showSnackbar("Holiday added successfully", "success");
      }
      fetchHolidays();
      handleCloseModal();
    } catch (err) {
      showSnackbar(
        `Failed to ${editMode ? "update" : "add"} holiday`,
        "error"
      );
    }
  };

  const handleDeleteHoliday = async (id) => {
    try {
      await axios.delete(`${API_BASE_URL}/holidays/${id}`);
      showSnackbar("Holiday deleted successfully", "success");
      fetchHolidays();
    } catch (err) {
      showSnackbar("Failed to delete holiday", "error");
    }
  };

  const showSnackbar = (message, severity) => {
    setSnackbar({ open: true, message, severity });
  };

  const handleCloseSnackbar = () => {
    setSnackbar((prev) => ({ ...prev, open: false }));
  };

  const formatDate = (dateString) => {
    const date = parseISO(dateString);
    return format(date, "MMMM d, yyyy");
  };

  const getDayName = (dateString) => {
    const date = parseISO(dateString);
    return daysOfWeek[getDay(date)];
  };

  return (
    <Box sx={{ p: 3, backgroundColor: "#f8fafc", minHeight: "100vh" }}>
      <Typography variant="h4" gutterBottom sx={{ fontWeight: "bold", color: "#2d3748" }}>
        Holiday Management
      </Typography>

      {/* Controls Section */}
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Button
          variant="contained"
          color="primary"
          startIcon={<Add />}
          onClick={handleOpenModal}
          sx={{ borderRadius: 2, boxShadow: "none" }}
        >
          Add Holiday
        </Button>

        <Box sx={{ display: "flex", gap: 2 }}>
          <Select
            value={entriesLimit}
            onChange={handleEntriesChange}
            sx={{ width: 120, backgroundColor: "white" }}
            size="small"
          >
            <MenuItem value={5}>Show 5</MenuItem>
            <MenuItem value={10}>Show 10</MenuItem>
            <MenuItem value={20}>Show 20</MenuItem>
            <MenuItem value={50}>Show 50</MenuItem>
          </Select>

          <form onSubmit={handleSearchSubmit}>
            <TextField
              variant="outlined"
              size="small"
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
              sx={{ width: 300 }}
            />
          </form>
        </Box>
      </Box>

      {/* Holiday Table */}
      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Alert severity="error">{error}</Alert>
      ) : (
        <TableContainer
          component={Paper}
          sx={{
            mt: 2,
            borderRadius: 2,
            boxShadow: "0px 2px 10px rgba(0, 0, 0, 0.05)",
          }}
        >
          <Table>
            <TableHead sx={{ backgroundColor: "#f1f5f9" }}>
              <TableRow>
                <TableCell sx={{ fontWeight: "bold" }}>#</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Day</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Date</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Holiday Name</TableCell>
                <TableCell sx={{ fontWeight: "bold" }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {holidays.slice(0, entriesLimit).map((holiday, index) => (
                <TableRow
                  key={holiday.id}
                  sx={{
                    "&:hover": { backgroundColor: "#f8fafc" },
                    "&:last-child td, &:last-child th": { border: 0 },
                  }}
                >
                  <TableCell>{index + 1}</TableCell>
                  <TableCell>
                    <Chip
                      label={holiday.day}
                      color={
                        holiday.day === "Saturday" || holiday.day === "Sunday"
                          ? "primary"
                          : "default"
                      }
                    />
                  </TableCell>
                  <TableCell>{formatDate(holiday.date)}</TableCell>
                  <TableCell sx={{ fontWeight: "medium" }}>
                    {holiday.name}
                  </TableCell>
                  <TableCell>
                    <IconButton
                      color="primary"
                      onClick={() => handleEditModal(holiday)}
                      sx={{ "&:hover": { backgroundColor: "#e0f2fe" } }}
                    >
                      <Edit />
                    </IconButton>
                    <IconButton
                      color="error"
                      onClick={() => handleDeleteHoliday(holiday.id)}
                      sx={{ "&:hover": { backgroundColor: "#fee2e2" } }}
                    >
                      <Delete />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Add/Edit Holiday Modal */}
      <Modal open={openModal} onClose={handleCloseModal}>
        <Box
          sx={{
            width: 400,
            backgroundColor: "white",
            p: 3,
            borderRadius: 2,
            mx: "auto",
            mt: "10%",
            boxShadow: 24,
          }}
        >
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 2,
            }}
          >
            <Typography variant="h6" sx={{ fontWeight: "bold" }}>
              {editMode ? "Edit Holiday" : "Add New Holiday"}
            </Typography>
            <IconButton onClick={handleCloseModal}>
              <Close />
            </IconButton>
          </Box>
          <TextField
            fullWidth
            label="Holiday Name"
            variant="outlined"
            name="name"
            value={currentHoliday.name}
            onChange={handleInputChange}
            sx={{ mb: 2 }}
          />
          <TextField
            fullWidth
            label="Holiday Date"
            type="date"
            name="date"
            value={currentHoliday.date}
            onChange={handleInputChange}
            InputLabelProps={{ shrink: true }}
            sx={{ mb: 3 }}
          />
          <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
            <Button
              variant="outlined"
              color="secondary"
              onClick={handleCloseModal}
            >
              Cancel
            </Button>
            <Button
              variant="contained"
              color="primary"
              onClick={handleSubmit}
              disabled={!currentHoliday.name || !currentHoliday.date}
            >
              {editMode ? "Update" : "Add"}
            </Button>
          </Box>
        </Box>
      </Modal>

      {/* Snackbar for notifications */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbar.severity}
          sx={{ width: "100%" }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default HolidayAdmin;