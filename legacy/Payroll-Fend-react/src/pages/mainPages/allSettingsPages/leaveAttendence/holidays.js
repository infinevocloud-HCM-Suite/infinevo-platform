import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { BiEdit, BiTrash, BiDotsVerticalRounded } from "react-icons/bi";
import { useNavigate } from "react-router-dom";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function Holidays() {
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState(false);
  const [holidays, setHolidays] = useState([]);
  const [hasHolidays, setHasHolidays] = useState(false);
  const [editingHolidayId, setEditingHolidayId] = useState(null);
  const [showActionMenu, setShowActionMenu] = useState(null);
  const [workLocations, setWorkLocations] = useState([]);
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear().toString());
  const [selectedLocation, setSelectedLocation] = useState("all");

  const [initialValues, setInitialValues] = useState({
    name: "",
    fromDate: "",
    toDate: "",
    description: "",
    restrictedHoliday: false,
    locations: []
  });

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchHolidays();
    fetchWorkLocations();
  }, []);

  useEffect(() => {
    if (holidays.length > 0) {
      filterHolidays();
    }
  }, [selectedYear, selectedLocation]);

  const fetchHolidays = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/holidays`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data && response.data.holidays) {
        setHolidays(response.data.holidays);
        setHasHolidays(response.data.holidays.length > 0);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load holidays", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchWorkLocations = async () => {
    try {
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/worklocations`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data && response.data.data) {
        setWorkLocations(response.data.data);
      }
    } catch (error) {
      console.error("Error fetching work locations:", error);
    }
  };

  const filterHolidays = () => {
    // This would be implemented based on the actual filtering logic
    // For now, we'll just use the original holidays array
    return holidays;
  };

  const handleDelete = async (holidayId) => {
    if (!window.confirm("Are you sure you want to delete this holiday?")) {
      return;
    }

    try {
      setSigningIn(true);
      await axios.delete(
        `${GlobalConst.API_URL}/api/holidays/${holidayId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );
      
      successMsg("Success", "Holiday deleted successfully", false);
      fetchHolidays(); // Refresh the list
    } catch (error) {
      console.error("Delete Error:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to delete holiday", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };

  const handleEdit = (holiday) => {
    setInitialValues({
      name: holiday.name,
      fromDate: holiday.fromDate,
      toDate: holiday.toDate,
      description: holiday.description || "",
      restrictedHoliday: holiday.restrictedHoliday,
      locations: holiday.locations || []
    });
    setEditingHolidayId(holiday.holidayId);
    setShowActionMenu(null);
    navigate(`/holidays/edit/${holiday.holidayId}`);
  };

  const toggleActionMenu = (holidayId) => {
    setShowActionMenu(showActionMenu === holidayId ? null : holidayId);
  };

  const formatDateRange = (startDate, endDate) => {
    const start = new Date(startDate);
    const end = new Date(endDate);
    
    if (start.toDateString() === end.toDateString()) {
      return `${start.getDate()}/${start.getMonth() + 1}/${start.getFullYear()}`;
    }
    
    return `${start.getDate()}/${start.getMonth() + 1}/${start.getFullYear()} - ${end.getDate()}/${end.getMonth() + 1}/${end.getFullYear()}`;
  };

  const getLocationName = (locationId) => {
    const location = workLocations.find(loc => loc.workLocationId === locationId);
    return location ? location.name : locationId;
  };

  const filteredHolidays = filterHolidays();

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Holidays</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Holidays</h5>
        <div className="d-flex align-items-center gap-2">
          <button className="btn btn-primary btn-sm" onClick={() => navigate("/holidays/add")}>
            + Add New
          </button>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{ minHeight: '100vh', overflowY: 'auto', display: 'flex', justifyContent: 'center' }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              {/* Filter Section */}
              <div className="row mb-5">
                <div className="col-md-6">
                  <label className="form-label fw-bold">FILTER BY :</label>
                  <div className="d-flex gap-3">
                    <select 
                      className="form-control form-control-solid w-auto"
                      value={selectedYear}
                      onChange={(e) => setSelectedYear(e.target.value)}
                    >
                      <option value="2025">2025</option>
                      <option value="2024">2024</option>
                      <option value="2023">2023</option>
                    </select>
                    <select 
                      className="form-control form-control-solid w-auto"
                      value={selectedLocation}
                      onChange={(e) => setSelectedLocation(e.target.value)}
                    >
                      <option value="all">All Locations</option>
                      {workLocations.map((location) => (
                        <option key={location.workLocationId} value={location.workLocationId}>
                          {location.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>

              {loading ? (
                <div className="text-center py-10">
                  <div className="spinner-border text-primary"></div>
                  <div className="mt-3">Loading holidays...</div>
                </div>
              ) : hasHolidays ? (
                <div className="py-5">
                  <div className="table-responsive">
                    <table className="table table-row-dashed table-row-gray-300 gy-7">
                      <thead>
                        <tr className="fw-bold fs-6 text-gray-800">
                          <th>Holiday Name</th>
                          <th>Date</th>
                          <th>Description</th>
                          <th>Locations</th>
                          <th>Type</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredHolidays.map((holiday) => (
                          <tr key={holiday.holidayId}>
                            <td>{holiday.name}</td>
                            <td>{formatDateRange(holiday.fromDate, holiday.toDate)}</td>
                            <td>{holiday.description}</td>
                            <td>
                              {holiday.locations && holiday.locations.length > 0 ? (
                                holiday.locations.slice(0, 2).map(loc => getLocationName(loc)).join(', ') +
                                (holiday.locations.length > 2 ? ` +${holiday.locations.length - 2} more` : '')
                              ) : (
                                "All Locations"
                              )}
                            </td>
                            <td>
                              <span className={`badge ${holiday.restrictedHoliday ? 'badge-light-warning' : 'badge-light-success'}`}>
                                {holiday.restrictedHoliday ? 'Restricted' : 'Regular'}
                              </span>
                            </td>
                            <td>
                              <div className="dropdown position-relative">
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => toggleActionMenu(holiday.holidayId)}
                                >
                                  <BiDotsVerticalRounded />
                                </button>
                                
                                {showActionMenu === holiday.holidayId && (
                                  <div className="dropdown-menu show position-absolute end-0" style={{ zIndex: 1000, minWidth: '150px' }}>
                                    <button
                                      className="dropdown-item d-flex align-items-center gap-2"
                                      onClick={() => handleEdit(holiday)}
                                    >
                                      <BiEdit className="text-primary" />
                                      Edit
                                    </button>
                                    <div className="dropdown-divider"></div>
                                    <button
                                      className="dropdown-item d-flex align-items-center gap-2 text-danger"
                                      onClick={() => handleDelete(holiday.holidayId)}
                                    >
                                      <BiTrash />
                                      Delete
                                    </button>
                                  </div>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <center>
                  <div className="card-body py-10">
                    <div className="text-center">
                      <div className="mb-7">
                        <div className="symbol symbol-150px symbol-circle bg-light-primary mb-4">
                          <i className="bi bi-calendar-event fs-2x text-primary"></i>
                        </div>
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Configure Holidays For Your Organisation</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ? 
                            "Failed to load holidays. Please try again later." : 
                            "Add new holidays to your calendar for accurate tracking and attendance management."}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => navigate("/holidays/add")}
                        >
                          <i className="bi bi-plus fs-2"></i> Add New
                        </button>
                      </div>

                      <div className="mt-10 pt-10 border-top">
                        <h4 className="fw-bold text-gray-900 mb-4">With this feature you can</h4>
                        <div className="row g-4 g-md-5">
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-calendar-check fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Track employee attendance accurately</h5>
                                <span className="text-muted fw-semibold">Automatically mark holidays in attendance records</span>
                              </div>
                            </div>
                          </div>
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-clock-history fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Manage leave policies effectively</h5>
                                <span className="text-muted fw-semibold">Plan holidays and manage employee leave requests</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </center>
              )}
            </div>
          </div>
        </div>
      </div>

      {signingIn && <Loader />}
    </>
  );
}