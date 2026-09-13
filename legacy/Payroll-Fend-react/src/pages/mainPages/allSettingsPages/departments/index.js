import { useState, useEffect } from "react";
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

import deptimg from '../../../../assets/images/deptimg.svg';

export default function Departments() {

  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [departments, setDepartments] = useState([]);
  const [hasDepartments, setHasDepartments] = useState(false);
  const [editingDepartmentId, setEditingDepartmentId] = useState(null);

  const [initialValues, setInitialValues] = useState({
    name: "",
    departmentCode: "",
    description: "",
  });

  // Get organizationId from localStorage or context
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchDepartments();
  }, []);

  const orgId = localStorage.getItem("organizationId");

  const fetchDepartments = async () => {
    try {
      setSigningIn(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/departments`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: orgId
        },
      });
      
      // Check if the response has the expected structure
      if (response.data && response.data.data) {
        setDepartments(response.data.data);
        setHasDepartments(response.data.data.length > 0);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      
      if (error.response) {
        // Server responded with error status
        errorMsg("Error", error.response.data?.message || "Failed to load departments", true);
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

  const validationSchema = Yup.object().shape({
    name: Yup.string()
      .required("Department name is required")
      .min(2, "Department name must be at least 2 characters")
      .max(50, "Department name cannot exceed 50 characters"),
    departmentCode: Yup.string()
      .required("Department code is required")
      .max(10, "Department code cannot exceed 10 characters"),
    description: Yup.string()
      .max(250, "Description cannot exceed 250 characters"),
  });

  const handleSubmit = async (values, { setSubmitting, resetForm }) => {
    console.log("in handle form");
    setSigningIn(true);

    try {
      const postData = {
        name: values.name,
        departmentCode: values.departmentCode,
        description: values.description
      };

      let response;
      let url;
      
      if (editingDepartmentId) {
        // Update existing department
        url = `${GlobalConst.API_URL}/api/departments/${editingDepartmentId}`;
        response = await axios.put(url, postData, {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        });
      } else {
        // Create new department
        url = `${GlobalConst.API_URL}/api/departments`;
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
          editingDepartmentId ? "Department updated successfully" : "Department created successfully",
          false
        );
        setShowAddForm(false);
        setEditingDepartmentId(null);
        resetForm();
        fetchDepartments(); // Refresh the list
      }
    } catch (error) {
      console.log("API Error:", error);
      
      if (error.response) {
        errorMsg(
          editingDepartmentId ? "Update Failed" : "Creation Failed",
          error.response.data?.message || (editingDepartmentId ? 'Department update failed' : 'Department creation failed'),
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

  const handleDelete = async (departmentId) => {
    if (!window.confirm("Are you sure you want to delete this department?")) {
      return;
    }

    try {
      setSigningIn(true);
      await axios.delete(`${GlobalConst.API_URL}/api/departments/${departmentId}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      successMsg("Success", "Department deleted successfully", false);
      fetchDepartments(); // Refresh the list
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete department", true);
    } finally {
      setSigningIn(false);
    }
  };

  const handleEdit = (department) => {
    setInitialValues({
      name: department.name,
      departmentCode: department.departmentCode,
      description: department.description || ""
    });
    setEditingDepartmentId(department.departmentId);
    setShowAddForm(true);
  };

  const handleCloseForm = () => {
    setShowAddForm(false);
    setEditingDepartmentId(null);
    setInitialValues({ name: "", departmentCode: "", description: "" });
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Departments</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Departments</h5>
        <div className="d-flex align-items-center gap-2">
          <button className="btn btn-primary btn-sm" onClick={() => setShowAddForm(true)}>
            Add Department
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
            <CiImport size={18}
             onClick={() => navigate("/departments/import")} />
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
              {hasDepartments ? (
                <div className="py-5">
                  <div className="table-responsive">
                    <table className="table table-row-dashed table-row-gray-300 gy-7">
                      <thead>
                        <tr className="fw-bold fs-6 text-gray-800">
                          <th>Department Name</th>
                          <th>Department Code</th>
                          <th>Description</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {departments.map((department) => (
                          <tr key={department.departmentId}>
                            <td>{department.name}</td>
                            <td>{department.departmentCode}</td>
                            <td>{department.description}</td>
                            <td>
                              <div className="d-flex gap-2">
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => handleEdit(department)}
                                >
                                  <BiEdit className="text-primary" />
                                </button>
                                <button
                                  className="btn btn-icon btn-sm btn-light"
                                  onClick={() => handleDelete(department.departmentId)}
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
                          src={deptimg}
                          alt="No departments"
                          className="mw-100 h-200px h-sm-325px"
                        />
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Enhance organisation structure with new departments</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ?
                            "Failed to load departments. Please try again later." :
                            "Create department based on the ones present in the organization and associate with employees"}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => setShowAddForm(true)}
                        >
                          <i className="bi bi-plus fs-2"></i> New Department
                        </button>

                        <button
                          className="btn btn-light-primary"
                                       onClick={() => navigate("/departments/import")} 

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
                                <h5 className="fw-bold text-gray-800 mb-1">Generate reports by department</h5>
                                <span className="text-muted fw-semibold">Break down your organization data by department</span>
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

      {/* Add/Edit Department Modal */}
      {showAddForm && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0, 0, 0, 0.7)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h2 className="modal-title">{editingDepartmentId ? "Edit Department" : "New Department"}</h2>
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
                {({ isSubmitting }) => (
                  <Form>
                    <div className="modal-body">
                      <div className="mb-5">
                        <label className="form-label required">Department Name</label>
                        <Field
                          type="text"
                          name="name"
                          className="form-control form-control-solid"
                          placeholder="Enter department name"
                        />
                        <ErrorMessage
                          name="name"
                          component="div"
                          className="text-danger mt-1"
                        />
                      </div>

                      <div className="mb-5">
                        <label className="form-label required">Department Code</label>
                        <Field
                          type="text"
                          name="departmentCode"
                          className="form-control form-control-solid"
                          placeholder="Enter department code"
                        />
                        <ErrorMessage
                          name="departmentCode"
                          component="div"
                          className="text-danger mt-1"
                        />
                      </div>

                      <div className="mb-5">
                        <label className="form-label">Description</label>
                        <Field
                          as="textarea"
                          name="description"
                          className="form-control form-control-solid"
                          placeholder="Enter description (max 250 characters)"
                          rows="3"
                        />
                        <ErrorMessage
                          name="description"
                          component="div"
                          className="text-danger mt-1"
                        />
                        <div className="text-muted fs-7 mt-1">Max 250 characters</div>
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
                          editingDepartmentId ? "Update" : "Save"
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