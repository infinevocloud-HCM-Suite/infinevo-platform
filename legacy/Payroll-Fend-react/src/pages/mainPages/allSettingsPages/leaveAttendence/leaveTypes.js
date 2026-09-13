import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { BiDotsVerticalRounded } from "react-icons/bi";
import { useNavigate } from "react-router-dom";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function LeaveTypes() {
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [hasLeaveTypes, setHasLeaveTypes] = useState(false);
  const [editingLeaveTypeId, setEditingLeaveTypeId] = useState(null);
  const [showActionMenu, setShowActionMenu] = useState(null);

  const [initialValues, setInitialValues] = useState({
    leaveName: "",
    leaveType: "",
    unit: "Days",
    status: "Active"
  });

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchLeaveTypes();
  }, []);

  const orgId = localStorage.getItem("organizationId");

  const fetchLeaveTypes = async () => {
    try {
      setSigningIn(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/leave-types`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: orgId
        },
      });
      
      if (response.data && response.data.leaves) {
        setLeaveTypes(response.data.leaves);
        setHasLeaveTypes(response.data.leaves.length > 0);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load leave types", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };

  const handleDelete = async (leaveId) => {
    if (!window.confirm("Are you sure you want to delete this leave type?")) {
      return;
    }

    try {
      setSigningIn(true);
      await axios.delete(`${GlobalConst.API_URL}/api/leave-types/${leaveId}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      successMsg("Success", "Leave type deleted successfully", false);
      fetchLeaveTypes(); // Refresh the list
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete leave type", true);
    } finally {
      setSigningIn(false);
    }
  };

  const handleStatusToggle = async (leaveType) => {
    const newStatus = leaveType.status === "active" ? "inactive" : "active";
    
    try {
      setSigningIn(true);
      await axios.patch(`${GlobalConst.API_URL}/api/leave-types/${leaveType.id}/status`, 
        { status: newStatus },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );
      
      successMsg("Success", `Leave type ${newStatus} successfully`, false);
      fetchLeaveTypes(); // Refresh the list
    } catch (error) {
      console.error("Status Update Error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to update leave type status", true);
    } finally {
      setSigningIn(false);
      setShowActionMenu(null);
    }
  };

  const handleEdit = (leaveTypeId) => {
    navigate(`/leave-types/edit/${leaveTypeId}`);
  };

  const toggleActionMenu = (leaveId) => {
    setShowActionMenu(showActionMenu === leaveId ? null : leaveId);
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Leave Types</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Leave Types</h5>
        <div className="d-flex align-items-center gap-2">
          <button className="btn btn-primary btn-sm" onClick={() => navigate('/leave-types/add')}>
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
              {hasLeaveTypes ? (
                <div className="py-5">
                  <div className="table-responsive">
                    <table className="table table-row-dashed table-row-gray-300 gy-7">
                      <thead>
                        <tr className="fw-bold fs-6 text-gray-800">
                          <th>Leave Name</th>
                          <th>Leave Type</th>
                          <th>Unit</th>
                          <th>Status</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {leaveTypes.map((leaveType) => (
                          <tr key={leaveType.id}>
                            <td>{leaveType.name}</td>
                            <td>{leaveType.type}</td>
                            <td>{leaveType.unit}</td>
                            <td>
                              <span className={`badge badge-${leaveType.status === 'active' ? 'success' : 'danger'}`}>
                                {leaveType.status}
                              </span>
                            </td>
                            <td>
                              <div className="dropdown position-relative">
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => toggleActionMenu(leaveType.id)}
                                >
                                  <BiDotsVerticalRounded />
                                </button>
                                
                                {showActionMenu === leaveType.id && (
                                  <div className="dropdown-menu show position-absolute end-0" style={{ zIndex: 1000, minWidth: '200px' }}>
                                    <button
                                      className="dropdown-item d-flex align-items-center gap-2"
                                      onClick={() => handleEdit(leaveType.id)}
                                    >
                                      <i className="bi bi-pencil text-primary"></i>
                                      Edit
                                    </button>
                                    <button
                                      className="dropdown-item d-flex align-items-center gap-2"
                                      onClick={() => handleStatusToggle(leaveType)}
                                    >
                                      <i className={`bi bi-${leaveType.status === 'active' ? 'x-circle' : 'check-circle'} ${leaveType.status === 'active' ? 'text-danger' : 'text-success'}`}></i>
                                      Mark as {leaveType.status === 'active' ? 'Inactive' : 'Active'}
                                    </button>
                                    <div className="dropdown-divider"></div>
                                    <button
                                      className="dropdown-item d-flex align-items-center gap-2 text-danger"
                                      onClick={() => handleDelete(leaveType.id)}
                                    >
                                      <i className="bi bi-trash"></i>
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
                          <i className="bi bi-calendar2-event fs-2x text-primary"></i>
                        </div>
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">No Leave Types Found</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ? 
                            "Failed to load leave types. Please try again later." : 
                            "Create leave types to manage employee leave policies effectively"}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => navigate('/leave-types/add')}
                        >
                          <i className="bi bi-plus fs-2"></i> Add New Leave Type
                        </button>
                      </div>

                      <div className="mt-10 pt-10 border-top">
                        <h4 className="fw-bold text-gray-900 mb-4">With leave types you can</h4>
                        <div className="row g-4 g-md-5">
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-person-check fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Manage different leave categories</h5>
                                <span className="text-muted fw-semibold">Create sick leave, casual leave, earned leave, etc.</span>
                              </div>
                            </div>
                          </div>
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-gear fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Configure leave policies</h5>
                                <span className="text-muted fw-semibold">Set accrual rules, carryover limits, and approval workflows</span>
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