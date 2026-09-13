import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { FaEdit, FaEllipsisV, FaUserFriends } from 'react-icons/fa';
import { CiImport } from "react-icons/ci";
import 'bootstrap/dist/css/bootstrap.min.css';
import { useNavigate } from "react-router-dom";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import _ from "lodash";
import Loader from "../../../shared/components/loaders/fullPageLoader";

export default function WorkLocations() {
  const [workLocations, setWorkLocations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [signingIn, setSigningIn] = useState(false);
  const navigate = useNavigate();
  
  const [openDropdownId, setOpenDropdownId] = useState(null);
  const [processingId, setProcessingId] = useState(null);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    fetchWorkLocations();
  }, []);

  const fetchWorkLocations = async () => {
    try {
      setLoading(true);
      setError(null);
      
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
      } else {
        setError("Unexpected response format from server");
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      console.error("API Error:", error);
      setError("Failed to load work locations");
      
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load work locations", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleToggleActive = async (location) => {
    const workLocationId = location.workLocationId;
    const currentlyActive = location.status !== false; // Assuming status is true for active
    const newStatus = currentlyActive ? false : true;

    console.log(`Toggling location ${workLocationId} -> ${newStatus ? 'ACTIVE' : 'INACTIVE'}`);
    setProcessingId(workLocationId);

    try {
      // First try to update status through the update endpoint
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/worklocations/${workLocationId}`,
        {
          workLocationName: location.workLocationName,
          streetAddress1: location.streetAddress1,
          streetAddress2: location.streetAddress2,
          city: location.city,
          state: location.state,
          zipCode: location.zipCode,
          country: location.country,
          isFilingAddress: location.isFilingAddress,
          status: newStatus
        },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data.status === 200) {
        // Update UI locally
        setWorkLocations((prev) =>
          prev.map((l) => (l.workLocationId === workLocationId ? { ...l, status: newStatus } : l))
        );
        successMsg('Success', `Work location marked as ${newStatus ? 'active' : 'inactive'}.`, false);
      } else {
        errorMsg(
          'Update Failed',
          response?.data?.message || `Could not update status for work location`,
          true
        );
      }
    } catch (e) {
      if (e?.response?.data) {
        errorMsg('Update Failed', e.response.data.message || 'Server error', false);
      } else {
        errorMsg('Error', e.message || 'Unknown error', true);
      }
    } finally {
      setProcessingId(null);
      setOpenDropdownId(null);
    }
  };

  const handleDelete = async (workLocationId) => {
    if (!window.confirm("Are you sure you want to delete this work location?")) {
      return;
    }

    try {
      setSigningIn(true);
      await axios.delete(
        `${GlobalConst.API_URL}/api/worklocations/${workLocationId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );
      
      successMsg("Success", "Work location deleted successfully", false);
      fetchWorkLocations(); // Refresh the list
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete work location", true);
    } finally {
      setSigningIn(false);
      setOpenDropdownId(null);
    }
  };

  const handleEdit = (location) => {
  navigate(`/work-locations/edit/${location.workLocationId}`, { state: location });
};



  if (loading) {
    return <Loader />;
  }

  if (error && workLocations.length === 0) {
    return (
      <>
        <Helmet>
          <title>HRMS InfiNevoCloud - Work Locations</title>
        </Helmet>

        <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom mb-4">
          <h5 className="mb-0 fw-semibold">Work Locations</h5>
          <div className="d-flex align-items-center gap-2">
            <button
              className="btn btn-primary btn-sm"
              onClick={() => navigate("/work-locations/new")}
            >
              Add Work Location
            </button>
            <div
              style={{
                width: '32px',
                height: '32px',
                border: '1px solid #ccc',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
                cursor: 'pointer',
              }}
            >
              <CiImport size={18} onClick={() => navigate("/work-locations/import")} />
            </div>
          </div>
        </div>

        <div className="d-flex flex-column flex-lg-row flex-column-fluid">
          <div className="d-flex flex-column flex-lg-row-fluid py-2">
            <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center p-10 bg-white">
              <div className="text-center">
                <div className="mb-4">
                  <i className="bi bi-exclamation-triangle fs-1 text-danger"></i>
                </div>
                <h4 className="fw-bold text-gray-900 mb-2">Error Loading Work Locations</h4>
                <p className="text-muted">{error}</p>
                <button
                  className="btn btn-primary"
                  onClick={fetchWorkLocations}
                >
                  Try Again
                </button>
              </div>
            </div>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Work Locations</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom mb-4">
        <h5 className="mb-0 fw-semibold">Work Locations</h5>
        <div className="d-flex align-items-center gap-2">
          <button
            className="btn btn-primary btn-sm"
            onClick={() => navigate("/work-locations/new")}
          >
            Add Work Location
          </button>

          <div
            style={{
              width: '32px',
              height: '32px',
              border: '1px solid #ccc',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
          >
            <CiImport size={18} onClick={() => navigate("/work-locations/import")} />
          </div>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100">
              {/* Empty state */}
              {workLocations.length === 0 && !error ? (
                <div className="text-center py-10">
                  <div className="mb-7">
                    <i className="bi bi-building fs-1 text-muted"></i>
                  </div>
                  <div className="mb-10">
                    <h3 className="fw-bold text-gray-900 mb-2">No work locations yet</h3>
                    <div className="text-muted fw-semibold fs-5">
                      Add work locations to manage your organization's physical offices
                    </div>
                  </div>
                  <button
                    className="btn btn-primary"
                    onClick={() => navigate("/work-locations/new")}
                  >
                    <i className="bi bi-plus fs-2"></i> Add Work Location
                  </button>
                </div>
              ) : (
                /* Card Grid */
                <div className="d-flex flex-wrap gap-3">
                  {workLocations.map((location) => (
                    <div
                      className="card shadow-sm border rounded p-3"
                      style={{ width: '320px', minHeight: '200px' }}
                      key={location.workLocationId}
                    >
                      {/* Header Row */}
                      <div className="d-flex justify-content-between align-items-start mb-2">
                        <h5 className="mb-0 fw-semibold">{location.workLocationName}</h5>
                        {location.status === false && (
                          <span className="badge bg-warning text-dark ms-2">Inactive</span>
                        )}
                        <div className="d-flex gap-2">
                          <button
                            className="btn btn-light btn-sm p-1"
                            onClick={() => handleEdit(location)}
                          >
                            <FaEdit size={18} />
                          </button>

                          <div className="position-relative">
                            <button
                              className="btn btn-light btn-sm p-1"
                              onClick={() =>
                                setOpenDropdownId((prevId) => (prevId === location.workLocationId ? null : location.workLocationId))
                              }
                            >
                              <FaEllipsisV size={16} />
                            </button>

                            {openDropdownId === location.workLocationId && (
                              <div
                                className="dropdown-menu show shadow-sm"
                                style={{ position: 'absolute', right: 0, top: '100%', zIndex: 1000 }}
                              >
                                <button
                                  className="dropdown-item text-danger"
                                  onClick={() => handleDelete(location.workLocationId)}
                                  disabled={processingId === location.workLocationId}
                                >
                                  Delete
                                </button>

                                <button
                                  className="dropdown-item"
                                  onClick={() => handleToggleActive(location)}
                                  disabled={processingId === location.workLocationId}
                                >
                                  {location.status !== false ? 'Mark as Inactive' : 'Mark as Active'}
                                  {processingId === location.workLocationId && (
                                    <span className="ms-2 spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                                  )}
                                </button>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Address */}
                      <div className="mb-3">
                        <div>{location.streetAddress1}</div>
                        {location.streetAddress2 && <div>{location.streetAddress2}</div>}
                        <div>{location.city}, {location.state} {location.zipCode}</div>
                        {location.country && <div>{location.country}</div>}
                      </div>

                      {/* Footer */}
                      <div className="d-flex justify-content-between align-items-center mt-auto">
                        {/* Employee count would need to be fetched separately or included in the work location response */}
                        <div className="d-flex align-items-center text-muted small">
                          <FaUserFriends className="me-1" />
                          {/* This would need to be populated from your backend */}
                          {/* {location.employeeCount || 0} Employees */}
                        </div>
                        {location.isFilingAddress && (
                          <span className="badge text-white" style={{ backgroundColor: '#00bfa5' }}>
                            FILING ADDRESS
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {signingIn && <Loader />}
    </>
  );
}