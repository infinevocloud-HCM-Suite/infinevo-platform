import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate } from "react-router-dom";
import axios from "axios";


import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function AddLeaveType() {
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);
  const [showNegativeBalanceOptions, setShowNegativeBalanceOptions] = useState(false);
  const [showPastDateLimit, setShowPastDateLimit] = useState(false);
  const [showFutureDateLimit, setShowFutureDateLimit] = useState(false);
  const [showExpiryDate, setShowExpiryDate] = useState(false);
  const [applicabilityType, setApplicabilityType] = useState("all");
  const [showPostponeCredits, setShowPostponeCredits] = useState(false);
  const [showResetBalanceOptions, setShowResetBalanceOptions] = useState(false);
  const [criteriaList, setCriteriaList] = useState([]);

  const initialValues = {
    leaveName: "",
    code: "",
    leaveType: "",
    description: "",
    leaveAccrual: "",
    days: "",
    proRateBalance: false,
    resetBalance: false,
    resetFrequency: "",
    carryForward: false,
    maxCarryForwardDays: "",
    encashLeave: false,
    maxEncashmentDays: "",
    allowNegativeBalance: false,
    negativeBalanceType: "",
    negativeBalanceLimit: "",
    allowPastDates: false,
    pastDateOption: "noLimit",
    pastDateLimit: "",
    allowFutureDates: false,
    futureDateOption: "noLimit",
    futureDateLimit: "",
    applicability: "all",
    postponeCredits: false,
    postponePeriod: "",
    postponeUnit: "",
    effectiveFrom: new Date().toISOString().split('T')[0],
    setExpiry: false,
    expiryDate: ""
  };

  // Simplified validation schema without complex conditional logic
  const validationSchema = Yup.object().shape({
    leaveName: Yup.string()
      .required("Leave name is required")
      .min(2, "Leave name must be at least 2 characters")
      .max(50, "Leave name cannot exceed 50 characters"),
    code: Yup.string()
      .required("Code is required")
      .max(10, "Code cannot exceed 10 characters"),
    leaveType: Yup.string()
      .required("Leave type is required"),
    leaveAccrual: Yup.string()
      .required("Leave accrual period is required"),
    days: Yup.number()
      .required("Days is required")
      .min(0.1, "Days must be greater than 0")
      .max(365, "Days cannot exceed 365"),
    effectiveFrom: Yup.date()
      .required("Effective from date is required"),
  });

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const mapFormDataToBackend = (values) => {
    const backendData = {
      name: values.leaveName,
      code: values.code,
      description: values.description,
      type: values.leaveType,
      unit: "day_based",
      status: "active",
      allowHalfDay: false,
      validityFrom: values.effectiveFrom,
      validityTo: values.setExpiry ? values.expiryDate : null,
      maxLeavePerApplication: null,
      
      // Accrual Configuration
      accrualConfiguration: {
        enabled: true,
        frequency: values.leaveAccrual,
        units: parseFloat(values.days),
        regularHours: "8"
      },
      
      // Reset Configuration
      resetConfiguration: {
        enabled: values.resetBalance,
        frequency: values.resetFrequency,
        carryForwardConfiguration: {
          enabled: values.carryForward,
          units: values.carryForward ? parseFloat(values.maxCarryForwardDays) : null
        },
        encashmentConfiguration: {
          enabled: values.encashLeave,
          units: values.encashLeave ? parseFloat(values.maxEncashmentDays) : null
        }
      },
      
      // Past Booking Configuration
      pastBookingConfiguration: {
        enabled: values.allowPastDates,
        limitDays: values.allowPastDates && values.pastDateOption === "setLimit" ? parseInt(values.pastDateLimit) : null
      },
      
      // Future Booking Configuration
      futureBookingConfiguration: {
        enabled: values.allowFutureDates,
        limitDays: values.allowFutureDates && values.futureDateOption === "setLimit" ? parseInt(values.futureDateLimit) : null
      },
      
      // Exceed Balance Configuration
      exceedBalance: {
        enabled: values.allowNegativeBalance,
        mode: values.allowNegativeBalance ? values.negativeBalanceType : null
      },
      
      // Effective After Configuration
      effectiveAfterConfiguration: {
        period: values.postponeCredits ? values.postponeUnit : null,
        units: values.postponeCredits ? parseInt(values.postponePeriod) : null
      },
      
      // Pro-rate Configuration
      proRateConfiguration: {
        enabled: values.proRateBalance
      },
      
      // Include Weekend (default values)
      includeWeekend: {
        enabled: false,
        minDays: null
      },
      
      // Include Holiday (default values)
      includeHoliday: {
        enabled: false,
        minDays: null
      }
    };

    // Handle applicability
    if (applicabilityType === "criteria" && criteriaList.length > 0) {
      // Map criteria to backend format
      const departmentIds = new Set();
      const designationIds = new Set();
      const workLocationIds = new Set();
      
      criteriaList.forEach(criteria => {
        if (criteria.type === "department" && criteria.value) {
          departmentIds.add(criteria.value);
        } else if (criteria.type === "designation" && criteria.value) {
          designationIds.add(criteria.value);
        } else if (criteria.type === "workLocation" && criteria.value) {
          workLocationIds.add(criteria.value);
        }
      });
      
      if (departmentIds.size > 0) backendData.departmentIds = Array.from(departmentIds);
      if (designationIds.size > 0) backendData.designationIds = Array.from(designationIds);
      if (workLocationIds.size > 0) backendData.workLocationIds = Array.from(workLocationIds);
    }

    return backendData;
  };

  const handleSubmit = async (values, { setSubmitting, resetForm }) => {
    setSigningIn(true);

    try {
      console.log("Submitting leave type:", values);
      
      // Custom validation for conditional fields
      let errors = {};
      
      if (values.allowNegativeBalance && !values.negativeBalanceType) {
        errors.negativeBalanceType = "Negative balance type is required";
      }
      
      if (values.allowNegativeBalance && values.negativeBalanceType === "yearEndLimit" && !values.negativeBalanceLimit) {
        errors.negativeBalanceLimit = "Negative balance limit is required";
      }
      
      if (values.allowPastDates && values.pastDateOption === "setLimit" && !values.pastDateLimit) {
        errors.pastDateLimit = "Past date limit is required";
      }
      
      if (values.allowFutureDates && values.futureDateOption === "setLimit" && !values.futureDateLimit) {
        errors.futureDateLimit = "Future date limit is required";
      }
      
      if (values.postponeCredits && !values.postponePeriod) {
        errors.postponePeriod = "Postpone period is required";
      }
      
      if (values.postponeCredits && !values.postponeUnit) {
        errors.postponeUnit = "Postpone unit is required";
      }
      
      if (values.setExpiry && !values.expiryDate) {
        errors.expiryDate = "Expiry date is required";
      }
      
      if (values.setExpiry && values.expiryDate && values.effectiveFrom && new Date(values.expiryDate) <= new Date(values.effectiveFrom)) {
        errors.expiryDate = "Expiry date must be after effective date";
      }

      // Reset balance validation
      if (values.resetBalance && !values.resetFrequency) {
        errors.resetFrequency = "Reset frequency is required";
      }
      
      if (values.resetBalance && values.carryForward && !values.maxCarryForwardDays) {
        errors.maxCarryForwardDays = "Max carry forward days is required";
      }
      
      if (values.resetBalance && values.encashLeave && !values.maxEncashmentDays) {
        errors.maxEncashmentDays = "Max encashment days is required";
      }

      if (Object.keys(errors).length > 0) {
        // Show first error
        const firstError = Object.values(errors)[0];
        errorMsg("Validation Error", firstError, false);
        return;
      }

      // Map form data to backend format
      const backendData = mapFormDataToBackend(values);

      // Make API call
      const response = await axios.post(`${GlobalConst.API_URL}/api/leave-types`, backendData, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data && response.data.code === 0) {
        successMsg("Success", "Leave type created successfully", false);
        resetForm();
        navigate('/leave-types'); // Navigate back to leave types list
      } else {
        throw new Error(response.data?.message || "Failed to create leave type");
      }
    } catch (error) {
      console.log("Error:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to create leave type", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", error.message || "An unexpected error occurred", false);
      }
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  const addCriteria = () => {
    setCriteriaList([...criteriaList, { id: Date.now(), type: "", value: "" }]);
  };

  const removeCriteria = (id) => {
    setCriteriaList(criteriaList.filter(item => item.id !== id));
  };

  const updateCriteria = (id, field, value) => {
    setCriteriaList(criteriaList.map(item => 
      item.id === id ? { ...item, [field]: value } : item
    ));
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Add Leave Type</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Add Leave Type</h5>
        <div className="d-flex align-items-center gap-2">
          <button 
            type="button" 
            className="btn btn-light btn-sm"
            onClick={() => navigate('/leave-types')}
          >
            Cancel
          </button>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "800px" }}>
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                  <Form className="form w-100">
                    {/* Header Section */}
                    <div className="fv-row mb-10">
                      <h1 className="fw-bold text-gray-900 mb-2">Add Leave type</h1>
                      <p className="text-muted fs-6">
                        Let's set up a new leave type.
                      </p>
                    </div>

                    {/* Basic Information Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Basic Information</h4>
                      </div>
                      <div className="card-body">
                        <div className="row">
                          <div className="col-md-6 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Leave Name <RequiredStar />
                            </label>
                            <Field
                              type="text"
                              name="leaveName"
                              className={`form-control form-control-solid ${errors.leaveName && touched.leaveName ? "is-invalid" : ""}`}
                              placeholder="Enter leave name"
                            />
                            <ErrorMessage name="leaveName" component="div" className="invalid-feedback" />
                          </div>

                          <div className="col-md-6 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Code <RequiredStar />
                            </label>
                            <Field
                              type="text"
                              name="code"
                              className={`form-control form-control-solid ${errors.code && touched.code ? "is-invalid" : ""}`}
                              placeholder="Enter code"
                            />
                            <ErrorMessage name="code" component="div" className="invalid-feedback" />
                          </div>

                          <div className="col-md-6 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Select Type <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="leaveType"
                              className={`form-control form-control-solid form-select ${errors.leaveType && touched.leaveType ? "is-invalid" : ""}`}
                            >
                              <option value="">Select Type</option>
                              <option value="paid">Paid</option>
                              <option value="unpaid">Unpaid</option>
                            </Field>
                            <ErrorMessage name="leaveType" component="div" className="invalid-feedback" />
                          </div>

                          <div className="col-12 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Description
                            </label>
                            <Field
                              as="textarea"
                              name="description"
                              className="form-control form-control-solid"
                              placeholder="Enter description"
                              rows="3"
                            />
                          </div>

                          <div className="col-md-6 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              How many leaves do employees get? <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="leaveAccrual"
                              className={`form-control form-control-solid form-select ${errors.leaveAccrual && touched.leaveAccrual ? "is-invalid" : ""}`}
                            >
                              <option value="">Select Period</option>
                              <option value="monthly">Monthly</option>
                              <option value="yearly">Yearly</option>
                            </Field>
                            <ErrorMessage name="leaveAccrual" component="div" className="invalid-feedback" />
                          </div>

                          <div className="col-md-6 mb-5">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Days <RequiredStar />
                            </label>
                            <Field
                              type="number"
                              name="days"
                              step="0.5"
                              className={`form-control form-control-solid ${errors.days && touched.days ? "is-invalid" : ""}`}
                              placeholder="Enter days"
                            />
                            <ErrorMessage name="days" component="div" className="invalid-feedback" />
                          </div>
                        </div>

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="proRateBalance"
                            className="form-check-input"
                          />
                          <label className="form-check-label fs-6">
                            Pro-rate leave balance for new joinees based on their date of joining
                          </label>
                        </div>

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="resetBalance"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("resetBalance", e.target.checked);
                              setShowResetBalanceOptions(e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("resetFrequency", "");
                                setFieldValue("carryForward", false);
                                setFieldValue("maxCarryForwardDays", "");
                                setFieldValue("encashLeave", false);
                                setFieldValue("maxEncashmentDays", "");
                              }
                            }}
                          />
                          <label className="form-check-label fs-6">
                            Reset the leave balance of employees
                          </label>
                        </div>

                        {/* Reset Balance Options */}
                        {showResetBalanceOptions && (
                          <div className="ms-5 mb-4">
                            <div className="row mb-3">
                              <div className="col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark">
                                  Reset Frequency <RequiredStar />
                                </label>
                                <Field
                                  as="select"
                                  name="resetFrequency"
                                  className={`form-control form-control-solid form-select ${errors.resetFrequency && touched.resetFrequency ? "is-invalid" : ""}`}
                                >
                                  <option value="">Select Frequency</option>
                                  <option value="yearly">Yearly</option>
                                  <option value="monthly">Monthly</option>
                                  <option value="quarterly">Quarterly</option>
                                  <option value="halfYearly">Half Yearly</option>
                                </Field>
                                <ErrorMessage name="resetFrequency" component="div" className="invalid-feedback" />
                              </div>
                            </div>

                            <div className="form-check form-check-custom form-check-solid mb-3">
                              <Field
                                type="checkbox"
                                name="carryForward"
                                className="form-check-input"
                                onChange={(e) => {
                                  setFieldValue("carryForward", e.target.checked);
                                  if (!e.target.checked) {
                                    setFieldValue("maxCarryForwardDays", "");
                                  }
                                }}
                              />
                              <label className="form-check-label fs-6">
                                Carry forward unused leave days upon reset?
                              </label>
                            </div>

                            {values.carryForward && (
                              <div className="row ms-4 mb-3">
                                <div className="col-md-6">
                                  <label className="form-label fs-6 fw-bold text-dark">
                                    Max carry forward days
                                  </label>
                                  <Field
                                    type="number"
                                    name="maxCarryForwardDays"
                                    className={`form-control form-control-solid ${errors.maxCarryForwardDays && touched.maxCarryForwardDays ? "is-invalid" : ""}`}
                                    placeholder="0"
                                    min="0"
                                  />
                                  <ErrorMessage name="maxCarryForwardDays" component="div" className="invalid-feedback" />
                                </div>
                              </div>
                            )}

                            <div className="form-check form-check-custom form-check-solid mb-3">
                              <Field
                                type="checkbox"
                                name="encashLeave"
                                className="form-check-input"
                                onChange={(e) => {
                                  setFieldValue("encashLeave", e.target.checked);
                                  if (!e.target.checked) {
                                    setFieldValue("maxEncashmentDays", "");
                                  }
                                }}
                              />
                              <label className="form-check-label fs-6">
                                Encash remaining leave days?
                              </label>
                            </div>

                            {values.encashLeave && (
                              <div className="row ms-4">
                                <div className="col-md-6">
                                  <label className="form-label fs-6 fw-bold text-dark">
                                    Max encashment days
                                  </label>
                                  <Field
                                    type="number"
                                    name="maxEncashmentDays"
                                    className={`form-control form-control-solid ${errors.maxEncashmentDays && touched.maxEncashmentDays ? "is-invalid" : ""}`}
                                    placeholder="0"
                                    min="0"
                                  />
                                  <ErrorMessage name="maxEncashmentDays" component="div" className="invalid-feedback" />
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Employee Leave Request Preferences Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Employee Leave Request Preferences</h4>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-check-custom form-check-solid mb-5">
                          <Field
                            type="checkbox"
                            name="allowNegativeBalance"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("allowNegativeBalance", e.target.checked);
                              setShowNegativeBalanceOptions(e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("negativeBalanceType", "");
                                setFieldValue("negativeBalanceLimit", "");
                              }
                            }}
                          />
                          <label className="form-check-label fs-6 fw-bold">
                            Allow negative leave balance
                          </label>
                        </div>

                        {showNegativeBalanceOptions && (
                          <div className="row ms-5 mb-5">
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark">
                                Consider negative leave balance
                              </label>
                              <Field
                                as="select"
                                name="negativeBalanceType"
                                className="form-control form-control-solid form-select"
                              >
                                <option value="">Select Option</option>
                                <option value="noLimit">No Limit</option>
                                <option value="yearEndLimit">Year End Limit</option>
                                <option value="markAsLOP">Mark as LOP</option>
                              </Field>
                            </div>
                            {values.negativeBalanceType === "yearEndLimit" && (
                              <div className="col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark">
                                  Limit
                                </label>
                                <Field
                                  type="number"
                                  name="negativeBalanceLimit"
                                  className="form-control form-control-solid"
                                  placeholder="Enter limit"
                                />
                              </div>
                            )}
                          </div>
                        )}

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="allowPastDates"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("allowPastDates", e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("pastDateOption", "noLimit");
                                setFieldValue("pastDateLimit", "");
                                setShowPastDateLimit(false);
                              }
                            }}
                          />
                          <label className="form-check-label fs-6 fw-bold">
                            Allow applying for leave on past dates
                          </label>
                        </div>

                        {values.allowPastDates && (
                          <div className="row ms-5 mb-5">
                            <div className="col-md-6">
                              <div className="form-check form-check-custom form-check-solid mb-2">
                                <Field
                                  type="radio"
                                  name="pastDateOption"
                                  value="noLimit"
                                  className="form-check-input"
                                  onChange={() => {
                                    setFieldValue("pastDateOption", "noLimit");
                                    setShowPastDateLimit(false);
                                    setFieldValue("pastDateLimit", "");
                                  }}
                                />
                                <label className="form-check-label">No limit on past dates</label>
                              </div>
                              <div className="form-check form-check-custom form-check-solid">
                                <Field
                                  type="radio"
                                  name="pastDateOption"
                                  value="setLimit"
                                  className="form-check-input"
                                  onChange={() => {
                                    setFieldValue("pastDateOption", "setLimit");
                                    setShowPastDateLimit(true);
                                  }}
                                />
                                <label className="form-check-label">Set Limit</label>
                              </div>
                            </div>
                            {showPastDateLimit && (
                              <div className="col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark">
                                  Days before today
                                </label>
                                <Field
                                  type="number"
                                  name="pastDateLimit"
                                  className="form-control form-control-solid"
                                  placeholder="Enter days"
                                />
                              </div>
                            )}
                          </div>
                        )}

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="allowFutureDates"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("allowFutureDates", e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("futureDateOption", "noLimit");
                                setFieldValue("futureDateLimit", "");
                                setShowFutureDateLimit(false);
                              }
                            }}
                          />
                          <label className="form-check-label fs-6 fw-bold">
                            Allow applying for leave on future dates
                          </label>
                        </div>

                        {values.allowFutureDates && (
                          <div className="row ms-5">
                            <div className="col-md-6">
                              <div className="form-check form-check-custom form-check-solid mb-2">
                                <Field
                                  type="radio"
                                  name="futureDateOption"
                                  value="noLimit"
                                  className="form-check-input"
                                  onChange={() => {
                                    setFieldValue("futureDateOption", "noLimit");
                                    setShowFutureDateLimit(false);
                                    setFieldValue("futureDateLimit", "");
                                  }}
                                />
                                <label className="form-check-label">No limit on future dates</label>
                              </div>
                              <div className="form-check form-check-custom form-check-solid">
                                <Field
                                  type="radio"
                                  name="futureDateOption"
                                  value="setLimit"
                                  className="form-check-input"
                                  onChange={() => {
                                    setFieldValue("futureDateOption", "setLimit");
                                    setShowFutureDateLimit(true);
                                  }}
                                />
                                <label className="form-check-label">Set Limit</label>
                              </div>
                            </div>
                            {showFutureDateLimit && (
                              <div className="col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark">
                                  Days from today
                                </label>
                                <Field
                                  type="number"
                                  name="futureDateLimit"
                                  className="form-control form-control-solid"
                                  placeholder="Enter days"
                                />
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Applicability Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Applicability</h4>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="radio"
                            name="applicability"
                            value="all"
                            className="form-check-input"
                            checked={applicabilityType === "all"}
                            onChange={() => setApplicabilityType("all")}
                          />
                          <label className="form-check-label fs-6">
                            All employees
                          </label>
                        </div>

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="radio"
                            name="applicability"
                            value="criteria"
                            className="form-check-input"
                            checked={applicabilityType === "criteria"}
                            onChange={() => setApplicabilityType("criteria")}
                          />
                          <label className="form-check-label fs-6">
                            Based on criteria
                          </label>
                        </div>

                        {applicabilityType === "criteria" && (
                          <div className="ms-5">
                            {criteriaList.map((criteria) => (
                              <div key={criteria.id} className="row mb-3">
                                <div className="col-md-5">
                                  <select
                                    className="form-control form-control-solid form-select"
                                    value={criteria.type}
                                    onChange={(e) => updateCriteria(criteria.id, "type", e.target.value)}
                                  >
                                    <option value="">Select Criteria</option>
                                    <option value="department">Department</option>
                                    <option value="designation">Designation</option>
                                    <option value="workLocation">Work Location</option>
                                  </select>
                                </div>
                                <div className="col-md-5">
                                  <select
                                    className="form-control form-control-solid form-select"
                                    value={criteria.value}
                                    onChange={(e) => updateCriteria(criteria.id, "value", e.target.value)}
                                  >
                                    <option value="">Select Value</option>
                                    {/* Options would be populated based on the selected criteria type */}
                                    <option value="dept1">Department 1</option>
                                    <option value="dept2">Department 2</option>
                                    <option value="desig1">Designation 1</option>
                                    <option value="desig2">Designation 2</option>
                                    <option value="loc1">Location 1</option>
                                    <option value="loc2">Location 2</option>
                                  </select>
                                </div>
                                <div className="col-md-2">
                                  <button
                                    type="button"
                                    className="btn btn-danger btn-sm"
                                    onClick={() => removeCriteria(criteria.id)}
                                  >
                                    Remove
                                  </button>
                                </div>
                              </div>
                            ))}
                            <button
                              type="button"
                              className="btn btn-primary btn-sm"
                              onClick={addCriteria}
                            >
                              + Add Criteria
                            </button>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Additional Settings Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Additional Settings</h4>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-check-custom form-check-solid mb-5">
                          <Field
                            type="checkbox"
                            name="postponeCredits"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("postponeCredits", e.target.checked);
                              setShowPostponeCredits(e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("postponePeriod", "");
                                setFieldValue("postponeUnit", "");
                              }
                            }}
                          />
                          <label className="form-check-label fs-6 fw-bold">
                            Postpone credits to employees
                          </label>
                        </div>

                        {showPostponeCredits && (
                          <div className="row ms-5 mb-5">
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark">
                                After
                              </label>
                              <Field
                                type="number"
                                name="postponePeriod"
                                className="form-control form-control-solid"
                                placeholder="Enter period"
                              />
                            </div>
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark">
                                Unit
                              </label>
                              <Field
                                as="select"
                                name="postponeUnit"
                                className="form-control form-control-solid form-select"
                              >
                                <option value="">Select Unit</option>
                                <option value="days">Days</option>
                                <option value="months">Months</option>
                                <option value="years">Years</option>
                              </Field>
                            </div>
                          </div>
                        )}

                        <div className="row mb-5">
                          <div className="col-md-6">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Effective From <RequiredStar />
                            </label>
                            <Field
                              type="date"
                              name="effectiveFrom"
                              className={`form-control form-control-solid ${errors.effectiveFrom && touched.effectiveFrom ? "is-invalid" : ""}`}
                            />
                            <ErrorMessage name="effectiveFrom" component="div" className="invalid-feedback" />
                          </div>
                        </div>

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="setExpiry"
                            className="form-check-input"
                            onChange={(e) => {
                              setFieldValue("setExpiry", e.target.checked);
                              setShowExpiryDate(e.target.checked);
                              if (!e.target.checked) {
                                setFieldValue("expiryDate", "");
                              }
                            }}
                          />
                          <label className="form-check-label fs-6 fw-bold">
                            Set expiry date
                          </label>
                        </div>

                        {showExpiryDate && (
                          <div className="row ms-5">
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark">
                                Expiry Date
                              </label>
                              <Field
                                type="date"
                                name="expiryDate"
                                className={`form-control form-control-solid ${errors.expiryDate && touched.expiryDate ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage name="expiryDate" component="div" className="invalid-feedback" />
                            </div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Submit Button */}
                    <div className="d-flex justify-content-end gap-3">
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate('/leave-types')}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Creating..." : "Create Leave Type"}
                      </button>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>

      {signingIn && <Loader />}
    </>
  );
}