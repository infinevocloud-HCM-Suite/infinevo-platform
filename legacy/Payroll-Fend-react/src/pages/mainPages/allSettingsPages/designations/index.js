import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { CiImport } from "react-icons/ci";
import { BiEdit, BiTrash } from "react-icons/bi";
import { useNavigate } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";
import designation from '../../../../assets/images/designation.png';

export default function Designations() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [designations, setDesignations] = useState([]);
  const [hasDesignations, setHasDesignations] = useState(false);
  const [editingDesignationId, setEditingDesignationId] = useState(null);

  const [initialValues, setInitialValues] = useState({
    name: "",
  });

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    fetchDesignations();
  }, []);

  const fetchDesignations = async () => {
    try {
      setSigningIn(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/designations`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      // Check if the response has the expected structure
      if (response.data && response.data.data) {
        setDesignations(response.data.data);
        setHasDesignations(response.data.data.length > 0);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      
      if (error.response) {
        // Server responded with error status
        errorMsg("Error", error.response.data?.message || "Failed to load designations", true);
      } else if (error.request) {
        // Request was made but no response received
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        // Something else happened
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    name: Yup.string()
      .required("Designation name is required")
      .min(2, "Designation name must be at least 2 characters")
      .max(50, "Designation name cannot exceed 50 characters"),
  });

  const handleSubmit = async (values, { setSubmitting, resetForm }) => {
    setSigningIn(true);

    try {
      const postData = {
        name: values.name,
      };

      let response;
      let url;
      
      if (editingDesignationId) {
        // Update existing designation
        url = `${GlobalConst.API_URL}/api/designations/${editingDesignationId}`;
        response = await axios.put(url, postData, {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        });
      } else {
        // Create new designation
        url = `${GlobalConst.API_URL}/api/designations`;
        response = await axios.post(url, postData, {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        });
      }

      console.log("API Response:", response);
      
      if (response.data.status === 200 || response.data.status === 201) {
        successMsg(
          "Success",
          editingDesignationId ? "Designation updated successfully" : "Designation created successfully",
          false
        );
        setShowAddForm(false);
        setEditingDesignationId(null);
        resetForm();
        fetchDesignations(); // Refresh the list
      }
    } catch (error) {
      console.log("API Error:", error);
      
      if (error.response) {
        errorMsg(
          editingDesignationId ? "Update Failed" : "Creation Failed",
          error.response.data?.message || (editingDesignationId ? 'Designation update failed' : 'Designation creation failed'),
          false
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          false
        );
      } else {
        errorMsg(
          "Error",
          "An unexpected error occurred",
          false
        );
      }
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  const handleDelete = async (designationId) => {
    if (!window.confirm("Are you sure you want to delete this designation?")) {
      return;
    }

    try {
      setSigningIn(true);
      await axios.delete(`${GlobalConst.API_URL}/api/designations/${designationId}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      successMsg("Success", "Designation deleted successfully", false);
      fetchDesignations(); // Refresh the list
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete designation", true);
    } finally {
      setSigningIn(false);
    }
  };

  const handleEdit = (designation) => {
    setInitialValues({
      name: designation.name,
    });
    setEditingDesignationId(designation.designationId);
    setShowAddForm(true);
  };

  const handleCloseForm = () => {
    setShowAddForm(false);
    setEditingDesignationId(null);
    setInitialValues({ name: "" });
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Designations</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Designations</h5>
        <div className="d-flex align-items-center gap-2">
          <button className="btn btn-primary btn-sm" onClick={() => setShowAddForm(true)}>
            Add Designation
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
              overflowY: 'auto'
            }}
          >
            <CiImport size={18} onClick={() => navigate("/designations/import")} />
          </div>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{ minHeight: '100vh', overflowY: 'auto', display: 'flex', justifyContent: 'center' }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              {hasDesignations ? (
                <div className="py-5">
                  <div className="table-responsive">
                    <table className="table table-row-dashed table-row-gray-300 gy-7">
                      <thead>
                        <tr className="fw-bold fs-6 text-gray-800">
                          <th>Designation Name</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {designations.map((designation) => (
                          <tr key={designation.designationId}>
                            <td>{designation.name}</td>
                            <td>
                              <div className="d-flex gap-2">
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => handleEdit(designation)}
                                >
                                  <BiEdit className="text-primary" />
                                </button>
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => handleDelete(designation.designationId)}
                                >
                                  <BiTrash className="text-danger" />
                                </button>
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
                        <img
                          src={designation}
                          alt="No designations"
                          className="mw-100 h-200px h-sm-325px"
                        />
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Track employee job titles with designations</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ?
                            "Failed to load designations. Please try again later." :
                            "Create designation based on the ones present in the organization and associate with employees"}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => setShowAddForm(true)}
                        >
                          <i className="bi bi-plus fs-2"></i> New Designation
                        </button>

                        <button
                          className="btn btn-light-primary"
                          onClick={() => navigate("/designations/import")}
                        >
                          <i className="bi bi-upload fs-2"></i> Import
                        </button>
                      </div>

                      <div className="mt-10 pt-10 border-top">
                        <h4 className="fw-bold text-gray-900 mb-4">With this feature you can</h4>
                        <div className="row g-4 g-md-5">
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-file-earmark-text fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Generate reports that break down payroll data by designation</h5>
                                <span className="text-muted fw-semibold">Carry out tasks such as salary revisions and exit processes easily</span>
                              </div>
                            </div>
                          </div>
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-cash-coin fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-800 mb-1">Break down payroll data by department</h5>
                                <span className="text-muted fw-semibold">Analyze salary expenses across departments</span>
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

      {/* Add/Edit Designation Modal */}
      {showAddForm && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0, 0, 0, 0.7)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h2 className="modal-title">{editingDesignationId ? "Edit Designation" : "New Designation"}</h2>
                <button
                  type="button"
                  className="btn btn-icon btn-sm btn-light"
                  onClick={handleCloseForm}
                >
                  <i className="bi bi-x fs-2"></i>
                </button>
              </div>

              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched }) => (
                  <Form>
                    <div className="modal-body">
                      <div className="fv-row mb-10">
                        <label
                          htmlFor="designationName"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          Designation Name <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="name"
                          id="designationName"
                          className={`form-control form-control-lg form-control-solid ${
                            errors.name && touched.name ? "is-invalid" : ""
                          }`}
                          placeholder="Enter designation name"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="name"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>
                    </div>

                    <div className="modal-footer">
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={handleCloseForm}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <span className="spinner-border spinner-border-sm me-1"></span>
                        ) : (
                          editingDesignationId ? "Update" : "Save"
                        )}
                      </button>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      )}

      {signingIn && <Loader />}
    </>
  );
}