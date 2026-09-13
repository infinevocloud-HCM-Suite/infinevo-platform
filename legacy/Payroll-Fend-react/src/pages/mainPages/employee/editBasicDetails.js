import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate, useParams } from "react-router-dom";
import { Button, message } from 'antd';
import { EditOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const RequiredStar = () => <span className="text-danger">*</span>;

export default function EditBasicDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [employee, setEmployee] = useState(null);
  const [workLocations, setWorkLocations] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [designations, setDesignations] = useState([]);
  const [hrUsers, setHrUsers] = useState([]);
  const [dropdownLoading, setDropdownLoading] = useState({
    locations: false,
    departments: false,
    designations: false,
    hrUsers: false,
  });

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // Fetch employee data and dropdown options
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        
        // Fetch employee data
        const employeeResponse = await axios.get(
          `${GlobalConst.API_URL}/api/employees/${id}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            }
          }
        );
        
        if (employeeResponse.data && employeeResponse.data.data) {
          setEmployee(employeeResponse.data.data);
        }
        
        // Fetch dropdown data
        await Promise.all([
          fetchWorkLocations(),
          fetchDepartments(),
          fetchDesignations(),
          fetchHrUsers()
        ]);
        
      } catch (error) {
        console.error("Failed to fetch data:", error);
        message.error('Failed to load employee data');
      } finally {
        setLoading(false);
      }
    };
    
    fetchData();
  }, [id, organizationId]);

  const fetchHrUsers = async () => {
    setDropdownLoading(prev => ({ ...prev, hrUsers: true }));
    try {
      const response = await axios.get(`${GlobalConst.API_URL}/auth/hr-users`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });

      if (response.data && response.data.data) {
        setHrUsers(response.data.data);
      }
    } catch (error) {
      console.error("Failed to fetch HR users:", error);
      message.error('Failed to fetch HR users');
    } finally {
      setDropdownLoading(prev => ({ ...prev, hrUsers: false }));
    }
  };

  const fetchWorkLocations = async () => {
    setDropdownLoading(prev => ({ ...prev, locations: true }));
    try {
      const response = await axios.get(`${GlobalConst.API_URL}/api/worklocations`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });

      if (response.data && response.data.data) {
        setWorkLocations(response.data.data);
      }
    } catch (error) {
      console.error("Failed to fetch work locations:", error);
    } finally {
      setDropdownLoading(prev => ({ ...prev, locations: false }));
    }
  };

  const fetchDepartments = async () => {
    setDropdownLoading(prev => ({ ...prev, departments: true }));
    try {
      const response = await axios.get(`${GlobalConst.API_URL}/api/departments`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });

      if (response.data && response.data.data) {
        setDepartments(response.data.data);
      }
    } catch (error) {
      console.error("Failed to fetch departments:", error);
    } finally {
      setDropdownLoading(prev => ({ ...prev, departments: false }));
    }
  };

  const fetchDesignations = async () => {
    setDropdownLoading(prev => ({ ...prev, designations: true }));
    try {
      const response = await axios.get(`${GlobalConst.API_URL}/api/designations`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });

      if (response.data && response.data.data) {
        setDesignations(response.data.data);
      }
    } catch (error) {
      console.error("Failed to fetch designations:", error);
    } finally {
      setDropdownLoading(prev => ({ ...prev, designations: false }));
    }
  };

  const validationSchema = Yup.object().shape({
    firstName: Yup.string().required("First Name is required"),
    lastName: Yup.string().required("Last Name is required"),
    middleName: Yup.string(),
    employeeNumber: Yup.string().required("Employee ID is required"),
    dateOfJoining: Yup.date().required("Date of Joining is required"),
    workMail: Yup.string().email("Invalid email").required("Work Email is required"),
    mobile: Yup.string().required("Mobile Number is required"),
    gender: Yup.string().required("Gender is required"),
    workLocationId: Yup.string().required("Work Location is required"),
    departmentId: Yup.string().required("Department is required"),
    designationId: Yup.string().required("Designation is required"),
    hrUser: Yup.string(),
  });

  const handleSubmit = async (values) => {
    setSubmitting(true);
    try {
      // Prepare data according to BasicDetailsDTO
      const updateData = {
        employeeNumber: values.employeeNumber,
        firstName: values.firstName,
        middleName: values.middleName,
        lastName: values.lastName,
        gender: values.gender,
        dateOfJoining: values.dateOfJoining,
        director: values.director,
        mobile: values.mobile,
        workMail: values.workMail,
        hrUser: values.hrUser,
        departmentId: values.departmentId,
        designationId: values.designationId,
        workLocationId: values.workLocationId,
        employeeStatus: values.employeeStatus || "Active",
        isPortalEnabled: values.portalEnabled || false,
        eligibleForPf: values.eligibleForPf || false,
        pfAccountNumber: values.pfAccountNumber || null,
        uan: values.uan || null,
        eligibleForEps: values.eligibleForEps || false,
        canContributeToEpsOnHigherWages: values.canContributeToEpsOnHigherWages || false,
        eligibleForPt: values.eligibleForPt || false,
        eligibleForLwf: values.eligibleForLwf || false,
        eligibleForEsi: values.eligibleForEsi || false,
        esiInsuranceNumber: values.esiInsuranceNumber || null,
        tags: values.tags || [],
        organizationId: organizationId,
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/employees/${id}`,
        updateData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          }
        }
      );

      if (response.data && response.data.status === 200) {
        successMsg("Success", "Employee details updated successfully", false);
        navigate(`/employees/view/${id}`);
      } else {
        errorMsg("Update Failed", "Failed to update employee details", false);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Update Failed", error.response.data?.message || "Failed to update employee details", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <Loader />;
  }

  if (!employee) {
    return (
      <div className="container-fluid p-10 bg-white">
        <div className="text-center">
          <h3>Employee not found</h3>
          <Button type="primary" onClick={() => navigate('/employees')}>
            Back to Employees
          </Button>
        </div>
      </div>
    );
  }

  // Find names for IDs to display in dropdowns
  const departmentName = employee.departmentId 
    ? departments.find(dept => dept.departmentId === employee.departmentId)?.name || ""
    : "";
    
  const designationName = employee.designationId 
    ? designations.find(des => des.designationId === employee.designationId)?.name || ""
    : "";
    
  const workLocationName = employee.workLocationId 
    ? workLocations.find(loc => loc.workLocationId === employee.workLocationId)?.workLocationName || ""
    : "";

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Basic Details</title>
      </Helmet>

      {/* Header */}
      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <div className="d-flex align-items-center gap-3">
          <Button 
            type="text" 
            icon={<ArrowLeftOutlined />} 
            onClick={() => navigate(`/employees/view/${id}`)}
          >
            Back
          </Button>
          <div>
            <h6 className="mb-0 fw-semibold">
              Edit Basic Details - {employee.employeeNumber}
            </h6>
            <small className="text-muted">
              {employee.firstName} {employee.lastName}
            </small>
          </div>
        </div>
      </div>

      {/* Form */}
      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid">
          <div className="container-fluid p-4 bg-white">
            <div className="card">
              <div className="card-body">
                <Formik
                  initialValues={{
                    firstName: employee.firstName || "",
                    middleName: employee.middleName || "",
                    lastName: employee.lastName || "",
                    employeeNumber: employee.employeeNumber || "",
                    dateOfJoining: employee.dateOfJoining || "",
                    workMail: employee.workMail || "",
                    mobile: employee.mobile || "",
                    gender: employee.gender || "",
                    workLocationId: employee.workLocationId || "",
                    departmentId: employee.departmentId || "",
                    designationId: employee.designationId || "",
                    hrUser: employee.hrUser || "",
                    director: employee.director || false,
                    portalEnabled: employee.portalEnabled || false,
                    eligibleForPf: employee.eligibleForPf || false,
                    pfAccountNumber: employee.pfAccountNumber || "",
                    uan: employee.uan || "",
                    eligibleForEps: employee.eligibleForEps || false,
                    canContributeToEpsOnHigherWages: employee.canContributeToEpsOnHigherWages || false,
                    eligibleForPt: employee.eligibleForPt || false,
                    eligibleForLwf: employee.eligibleForLwf || false,
                    eligibleForEsi: employee.eligibleForEsi || false,
                    esiInsuranceNumber: employee.esiInsuranceNumber || "",
                    tags: employee.tags || [],
                  }}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize={true}
                >
                  {({ values, setFieldValue, errors, touched }) => (
                    <Form className="form w-100">
                      {/* Employee Name Section */}
                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                          Employee Name<RequiredStar />
                        </label>

                        <div className="row g-3">
                          <div className="col-12 col-md-4">
                            <Field
                              type="text"
                              name="firstName"
                              placeholder="First Name"
                              className={`form-control ${errors.firstName && touched.firstName ? "is-invalid" : ""}`}
                            />
                            <ErrorMessage
                              name="firstName"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <div className="col-12 col-md-4">
                            <Field
                              type="text"
                              name="middleName"
                              placeholder="Middle Name"
                              className="form-control"
                            />
                            <ErrorMessage
                              name="middleName"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <div className="col-12 col-md-4">
                            <Field
                              type="text"
                              name="lastName"
                              placeholder="Last Name"
                              className={`form-control ${errors.lastName && touched.lastName ? "is-invalid" : ""}`}
                            />
                            <ErrorMessage
                              name="lastName"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>
                        </div>
                      </div>

                      {/* Employee ID and Date of Joining */}
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Employee ID<RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="employeeNumber"
                            placeholder="Employee ID"
                            className={`form-control ${errors.employeeNumber && touched.employeeNumber ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="employeeNumber"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Date of Joining<RequiredStar />
                          </label>
                          <Field
                            type="date"
                            name="dateOfJoining"
                            placeholder="Date of Joining"
                            className={`form-control ${errors.dateOfJoining && touched.dateOfJoining ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="dateOfJoining"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Work Email and Mobile Number */}
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Work Email<RequiredStar />
                          </label>
                          <Field
                            type="email"
                            name="workMail"
                            placeholder="abc@xyz.com"
                            className={`form-control ${errors.workMail && touched.workMail ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="workMail"
                            component="div"
                            className="invalid-feedback"
                          />
                          <div className="text-muted small mt-1" style={{ backgroundColor: "#EEF7FF", padding: "8px", borderRadius: "4px" }}>
                            You cannot change this Email address later on, as this will be used to send payslips and also for employees to sign in to their portal, where they can view/download their payslips.
                          </div>
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Mobile Number<RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="mobile"
                            placeholder="Mobile Number"
                            className={`form-control ${errors.mobile && touched.mobile ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="mobile"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Director Checkbox */}
                      <div className="mb-4">
                        <div className="form-check form-check-custom form-check-dark mb-3 p-2 rounded">
                          <Field
                            type="checkbox"
                            name="director"
                            id="director"
                            className="form-check-input"
                            checked={values.director}
                            onChange={(e) => setFieldValue("director", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="director">
                            Employee is a Director/person with substantial interest in the company.
                          </label>
                        </div>
                      </div>

                      {/* Gender and Work Location */}
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Gender<RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="gender"
                            className={`form-control ${errors.gender && touched.gender ? "is-invalid" : ""}`}
                          >
                            <option value="">Select Gender</option>
                            <option value="Male">Male</option>
                            <option value="Female">Female</option>
                            <option value="Other">Other</option>
                          </Field>
                          <ErrorMessage
                            name="gender"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Work Location<RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="workLocationId"
                            className={`form-control ${errors.workLocationId && touched.workLocationId ? "is-invalid" : ""}`}
                            disabled={dropdownLoading.locations}
                          >
                            <option value="">Select Work Location</option>
                            {workLocations.map((location) => (
                              <option key={location.workLocationId} value={location.workLocationId}>
                                {location.workLocationName}
                              </option>
                            ))}
                          </Field>
                          <ErrorMessage
                            name="workLocationId"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Designation and Department */}
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Designation<RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="designationId"
                            className={`form-control ${errors.designationId && touched.designationId ? "is-invalid" : ""}`}
                            disabled={dropdownLoading.designations}
                          >
                            <option value="">Select Designation</option>
                            {designations.map((designation) => (
                              <option key={designation.designationId} value={designation.designationId}>
                                {designation.name}
                              </option>
                            ))}
                          </Field>
                          <ErrorMessage
                            name="designationId"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Department<RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="departmentId"
                            className={`form-control ${errors.departmentId && touched.departmentId ? "is-invalid" : ""}`}
                            disabled={dropdownLoading.departments}
                          >
                            <option value="">Select Department</option>
                            {departments.map((department) => (
                              <option key={department.departmentId} value={department.departmentId}>
                                {department.name}
                              </option>
                            ))}
                          </Field>
                          <ErrorMessage
                            name="departmentId"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Reporting HR Field */}
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Reporting HR <RequiredStar />
                          </label>

                          <Field
                            as="select"
                            name="hrUser"
                            className={`form-control ${errors.hrUser && touched.hrUser ? "is-invalid" : ""}`}
                            disabled={dropdownLoading.hrUsers}
                          >
                            <option value="">Select Reporting HR</option>
                            {hrUsers.map((hrUser) => (
                              <option key={hrUser.userId} value={hrUser.userEmail}>
                                {hrUser.firstName} {hrUser.lastName} ({hrUser.userEmail})
                              </option>
                            ))}
                          </Field>

                          <ErrorMessage
                            name="hrUser"
                            component="div"
                            className="invalid-feedback"
                          />

                          <div className="text-muted small mt-1">
                            Select the HR responsible for this employee's records.
                          </div>
                        </div>
                      </div>

                      {/* Portal Access Checkbox */}
                      <div className="mb-4">
                        <div className="form-check form-check-custom form-check-dark mb-3 p-2 rounded">
                          <Field
                            type="checkbox"
                            name="portalEnabled"
                            id="portalEnabled"
                            className="form-check-input"
                            checked={values.portalEnabled}
                            onChange={(e) => setFieldValue('portalEnabled', e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="portalEnabled">
                            Enable Portal Access
                          </label>
                        </div>
                        <div className="text-muted small ps-4">
                          The employee will be able to view payslips, submit their IT declaration and create reimbursement claims through the employee portal.
                        </div>
                      </div>

                      {/* Statutory Components */}
                      <div className="mb-4">
                        <h6 className="fw-bold mb-2">Statutory Components</h6>
                        <div className="text-muted small mb-3">
                          Enable the necessary benefits and tax applicable for this employee.
                        </div>

                        {/* Employees' Provident Fund */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForPf"
                            id="eligibleForPf"
                            className="form-check-input"
                            checked={values.eligibleForPf}
                            onChange={(e) => setFieldValue("eligibleForPf", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForPf">
                            Employees' Provident Fund
                          </label>
                        </div>

                        {/* PF Account Number + UAN */}
                        {values.eligibleForPf && (
                          <div className="row g-3 mb-3">
                            {/* PF Account Number */}
                            <div className="col-12 col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                PF Account Number
                              </label>
                              <Field
                                type="text"
                                name="pfAccountNumber"
                                placeholder="AA/AAA/0000000/XXX/0000000"
                                maxLength={30}
                                onInput={(e) => {
                                  e.target.value = e.target.value
                                    .toUpperCase()
                                    .replace(/[^A-Z0-9/]/g, ""); // ✅ block all other chars
                                }}
                                className={`form-control ${errors.pfAccountNumber && touched.pfAccountNumber ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage
                                name="pfAccountNumber"
                                component="div"
                                className="invalid-feedback"
                              />
                              <div className="text-muted small mt-1">
                                Format: AA/AAA/0000000/XXX/0000000
                              </div>
                            </div>

                            {/* UAN */}
                            <div className="col-12 col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                UAN
                              </label>
                              <Field
                                type="text"
                                name="uan"
                                placeholder="000000000000"
                                maxLength={12}
                                onInput={(e) => {
                                  e.target.value = e.target.value.replace(/[^0-9]/g, ""); // ✅ allow only digits
                                }}
                                className={`form-control ${errors.uan && touched.uan ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage
                                name="uan"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>
                          </div>
                        )}

                        {/* Employees' State Insurance */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForEsi"
                            id="eligibleForEsi"
                            className="form-check-input"
                            checked={values.eligibleForEsi}
                            onChange={(e) => setFieldValue("eligibleForEsi", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForEsi">
                            Employees' State Insurance
                          </label>
                        </div>

                        {/* ESI Insurance Number */}
                        {values.eligibleForEsi && (
                          <div className="row g-3 mb-3">
                            <div className="col-12 col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                ESI Insurance Number
                              </label>
                              <Field
                                type="text"
                                name="esiInsuranceNumber"
                                placeholder="0000000000"
                                className={`form-control ${errors.esiInsuranceNumber && touched.esiInsuranceNumber ? "is-invalid" : ""}`}
                                maxLength={10}
                              />
                              <ErrorMessage
                                name="esiInsuranceNumber"
                                component="div"
                                className="invalid-feedback"
                              />
                              <div className="text-muted small mt-1">
                                Format: 10 digits (0000000000)
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Contribute to Employee Pension Scheme */}
                        {values.eligibleForPf && (
                          <div className="form-check form-check-custom form-check-dark mb-2 ms-3">
                            <Field
                              type="checkbox"
                              name="eligibleForEps"
                              id="eligibleForEps"
                              className="form-check-input"
                              checked={values.eligibleForEps}
                              onChange={(e) => setFieldValue("eligibleForEps", e.target.checked)}
                            />
                            <label className="form-check-label" htmlFor="eligibleForEps">
                              Contribute to Employee Pension Scheme
                              <i className="bi bi-info-circle ms-1" title="Details about EPS"></i>
                            </label>
                          </div>
                        )}

                        {/* Contribute EPS at actual PF Wages */}
                        {values.eligibleForEps && (
                          <div className="form-check form-check-custom form-check-dark mb-2 ms-5">
                            <Field
                              type="checkbox"
                              name="canContributeToEpsOnHigherWages"
                              id="canContributeToEpsOnHigherWages"
                              className="form-check-input"
                              checked={values.canContributeToEpsOnHigherWages}
                              onChange={(e) => setFieldValue("canContributeToEpsOnHigherWages", e.target.checked)}
                            />
                            <label className="form-check-label" htmlFor="canContributeToEpsOnHigherWages">
                              Contribute EPS at actual PF Wages
                              <i className="bi bi-info-circle ms-1" title="Details about EPS at PF Wages"></i>
                            </label>
                          </div>
                        )}

                        {/* Professional Tax */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForPt"
                            id="eligibleForPt"
                            className="form-check-input"
                            checked={values.eligibleForPt}
                            onChange={(e) => setFieldValue("eligibleForPt", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForPt">
                            Professional Tax
                          </label>
                        </div>

                        {/* Labor Welfare Fund */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForLwf"
                            id="eligibleForLwf"
                            className="form-check-input"
                            checked={values.eligibleForLwf}
                            onChange={(e) => setFieldValue("eligibleForLwf", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForLwf">
                            Labor Welfare Fund
                          </label>
                        </div>
                      </div>

                      {/* Form Actions */}
                      <div className="d-flex justify-content-end gap-3 mt-5">
                        <Button 
                          type="default" 
                          onClick={() => navigate(`/employees/view/${id}`)}
                        >
                          Cancel
                        </Button>
                        <Button 
                          type="primary" 
                          htmlType="submit"
                          loading={submitting}
                          icon={<EditOutlined />}
                        >
                          Update Details
                        </Button>
                      </div>
                    </Form>
                  )}
                </Formik>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}