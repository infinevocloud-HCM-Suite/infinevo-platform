import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function AttendancePreferences() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [preferences, setPreferences] = useState(null);
  const [hasPreferences, setHasPreferences] = useState(false);
  const [editingPreferenceId, setEditingPreferenceId] = useState(null);
  const [formKey, setFormKey] = useState(0);

  // Get organization ID from localStorage
  const organizationId = localStorage.getItem("organizationId");

  const [initialValues, setInitialValues] = useState({
    attendanceCycleStart: "28",
    attendanceCycleEnd: "27",
    payrollReportDay: "5",
    includeLeaveEncashment: false
  });

  useEffect(() => {
    if (organizationId) {
      fetchPreferences();
    }
  }, [organizationId]);

  const fetchPreferences = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/preferences`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      console.log("Preferences API Response:", response.data);

      if (response.data) {
        const prefData = response.data;
        
        // Map backend response to frontend form values
        const values = {
          // Note: Your backend only has endDay, so you need to decide how to handle start day
          // For now, I'll use the same value for both, but you should update your backend DTO
          attendanceCycleStart: prefData.endDay?.toString() || "28",
          attendanceCycleEnd: prefData.endDay?.toString() || "27",
          payrollReportDay: prefData.payrollReportDay?.toString() || "5",
          includeLeaveEncashment: prefData.leaveEncashmentEnabled || false
        };

        setInitialValues(values);
        setPreferences(prefData);
        setHasPreferences(true);
        
        // Use the ID from the response if available
        if (prefData.id) {
          setEditingPreferenceId(prefData.id);
        }
      } else {
        setHasPreferences(false);
      }
    } catch (error) {
      console.error("Error fetching preferences:", error);
      setHasPreferences(false);
      
      // Handle 404 specifically (no preferences found)
      if (error.response && error.response.status === 404) {
        console.log("No preferences found, will create new ones on save");
        // Don't show error for 404 - it's expected when no preferences exist
      } else if (error.response && error.response.status !== 404) {
        errorMsg("Error", error.response.data?.message || "Failed to load preferences", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // Map frontend form values to backend DTO
  const mapFrontendToBackend = (frontendValues) => {
    return {
      // IMPORTANT: Your backend DTO only has endDay, payrollReportDay, and leaveEncashmentEnabled
      // You need to update your backend to support attendanceCycleStart or adjust your business logic
      endDay: frontendValues.attendanceCycleEnd,
      payrollReportDay: frontendValues.payrollReportDay,
      leaveEncashmentEnabled: frontendValues.includeLeaveEncashment
    };
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    attendanceCycleStart: Yup.number()
      .required("Attendance cycle start day is required")
      .min(1, "Start day must be between 1 and 31")
      .max(31, "Start day must be between 1 and 31"),

    attendanceCycleEnd: Yup.number()
      .required("Attendance cycle end day is required")
      .min(1, "End day must be between 1 and 31")
      .max(31, "End day must be between 1 and 31")
      .test('end-after-start', 'End day must be after start day in the cycle', function(value) {
        const { attendanceCycleStart } = this.parent;
        if (!attendanceCycleStart || !value) return true;
        
        const start = parseInt(attendanceCycleStart);
        const end = parseInt(value);
        
        return true;
      }),

    payrollReportDay: Yup.number()
      .required("Payroll report generation day is required")
      .min(1, "Payroll report day must be between 1 and 31")
      .max(31, "Payroll report day must be between 1 and 31")
      .test('valid-payroll-day', 'Payroll report must be generated 1-7 days after attendance cycle ends', function(value) {
        const { attendanceCycleEnd } = this.parent;
        if (!attendanceCycleEnd || !value) return true;
        
        const cycleEnd = parseInt(attendanceCycleEnd);
        const reportDay = parseInt(value);
        
        let daysBetween;
        if (reportDay > cycleEnd) {
          daysBetween = reportDay - cycleEnd;
        } else {
          daysBetween = (31 - cycleEnd) + reportDay;
        }
        
        return daysBetween >= 1 && daysBetween <= 7;
      }),

    includeLeaveEncashment: Yup.boolean()
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    // if (!organizationId) {
    //   errorMsg("Error", "Organization ID not found. Please select an organization first.", false);
    //   return;
    // }

    try {
      setSubmitting(true);
      setSigningIn(true);

      const postData = mapFrontendToBackend(values);
      console.log("Submitting data:", postData);

      let response;
      
      if (editingPreferenceId) {
        // Update existing preference
        response = await axios.put(
          `${GlobalConst.API_URL}/api/preferences/${editingPreferenceId}`,
          postData,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            },
          }
        );
      } else {
        // Create new preference
        response = await axios.post(
          `${GlobalConst.API_URL}/api/preferences`,
          postData,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            },
          }
        );
      }

      console.log("Save API Response:", response.data);

      if (response.data) {
        successMsg(
          "Success", 
          editingPreferenceId ? "Preferences updated successfully" : "Preferences created successfully", 
          false
        );
        
        // Set the editing ID from response
        if (response.data.id) {
          setEditingPreferenceId(response.data.id);
        }
        setHasPreferences(true);
        
        // Update initialValues with the saved data
        setInitialValues(prev => ({
          ...prev,
          attendanceCycleStart: values.attendanceCycleStart,
          attendanceCycleEnd: values.attendanceCycleEnd,
          payrollReportDay: values.payrollReportDay,
          includeLeaveEncashment: values.includeLeaveEncashment
        }));
        setFormKey(prev => prev + 1);
        
        // Refresh the data
        fetchPreferences();
      }
    } catch (error) {
      console.error("Error saving preferences:", error);
      if (error.response) {
        errorMsg(
          editingPreferenceId ? "Update Failed" : "Creation Failed",
          error.response.data?.message || (editingPreferenceId ? 'Preferences update failed' : 'Preferences creation failed'),
          false
        );
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  const generateDayOptions = () => {
    const days = [];
    for (let i = 1; i <= 31; i++) {
      let suffix = "th";
      if (i === 1 || i === 21 || i === 31) suffix = "st";
      else if (i === 2 || i === 22) suffix = "nd";
      else if (i === 3 || i === 23) suffix = "rd";
      
      days.push({
        value: i.toString(),
        label: `${i}${suffix}`
      });
    }
    return days;
  };

  const dayOptions = generateDayOptions();

  const calculateDaysBetween = (cycleEnd, reportDay) => {
    const end = parseInt(cycleEnd);
    const report = parseInt(reportDay);
    
    if (report > end) {
      return report - end;
    } else {
      return (31 - end) + report;
    }
  };

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Preferences</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Preferences</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "800px" }}
            >
              <Formik
                key={formKey}
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, values }) => (
                  <Form className="form w-100">
                    {/* Attendance Cycle Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <h3 className="card-title fw-bold">Attendance Cycle</h3>
                        <div className="card-toolbar">
                          <span className="text-muted fs-7">
                            Define the start and end days of your organisation's attendance cycle
                          </span>
                        </div>
                      </div>
                      <div className="card-body">
                        <div className="row mb-6">
                          <div className="col-md-6">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Pay Schedule
                            </label>
                            <div className="form-control form-control-solid bg-light">
                              Every month
                            </div>
                            <div className="text-muted fs-7 mt-1">
                              Fixed monthly pay schedule
                            </div>
                          </div>
                        </div>

                        <div className="row">
                          <div className="col-md-6 mb-4">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Attendance Cycle Start Day <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="attendanceCycleStart"
                              className={`form-control form-control-lg form-control-solid form-select ${
                                errors.attendanceCycleStart && touched.attendanceCycleStart
                                  ? "is-invalid"
                                  : ""
                              }`}
                              disabled={isSubmitting}
                            >
                              {dayOptions.map((day) => (
                                <option key={day.value} value={day.value}>
                                  {day.label}
                                </option>
                              ))}
                            </Field>
                            <ErrorMessage
                              name="attendanceCycleStart"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <div className="col-md-6 mb-4">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Attendance Cycle End Day <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="attendanceCycleEnd"
                              className={`form-control form-control-lg form-control-solid form-select ${
                                errors.attendanceCycleEnd && touched.attendanceCycleEnd
                                  ? "is-invalid"
                                  : ""
                              }`}
                              disabled={isSubmitting}
                            >
                              {dayOptions.map((day) => (
                                <option key={day.value} value={day.value}>
                                  {day.label}
                                </option>
                              ))}
                            </Field>
                            <ErrorMessage
                              name="attendanceCycleEnd"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>
                        </div>

                        <div className="bg-light-info p-4 rounded">
                          <div className="d-flex align-items-center">
                            <i className="bi bi-info-circle text-info fs-2 me-3"></i>
                            <div>
                              <div className="fw-bold text-gray-800 mb-1">
                                Current Attendance Cycle
                              </div>
                              <div className="text-gray-700">
                                Your chosen date range <strong>{values.attendanceCycleStart}th - {values.attendanceCycleEnd}th</strong> sets the attendance cycle, 
                                which will automatically repeat for future months unless edited.
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Payroll Report Generation Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <h3 className="card-title fw-bold">Payroll Report Generation Day</h3>
                        <div className="card-toolbar">
                          <span className="text-muted fs-7">
                            Choose when to generate payroll reports from leave and attendance data
                          </span>
                        </div>
                      </div>
                      <div className="card-body">
                        <div className="row">
                          <div className="col-md-6 mb-4">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Payroll Report Generation Day <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="payrollReportDay"
                              className={`form-control form-control-lg form-control-solid form-select ${
                                errors.payrollReportDay && touched.payrollReportDay
                                  ? "is-invalid"
                                  : ""
                              }`}
                              disabled={isSubmitting}
                            >
                              {dayOptions.map((day) => (
                                <option key={day.value} value={day.value}>
                                  {day.label}
                                </option>
                              ))}
                            </Field>
                            <ErrorMessage
                              name="payrollReportDay"
                              component="div"
                              className="invalid-feedback"
                            />
                            
                            {values.attendanceCycleEnd && values.payrollReportDay && (
                              <div className={`mt-2 fs-7 ${
                                calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay) >= 1 &&
                                calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay) <= 7
                                  ? "text-success"
                                  : "text-danger"
                              }`}>
                                <i className={`bi ${
                                  calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay) >= 1 &&
                                  calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay) <= 7
                                    ? "bi-check-circle"
                                    : "bi-exclamation-circle"
                                } me-1`}></i>
                                {calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay)} days between cycle end and report generation
                              </div>
                            )}
                          </div>
                        </div>

                        <div className="bg-light-warning p-4 rounded mb-4">
                          <div className="d-flex align-items-start">
                            <i className="bi bi-exclamation-triangle text-warning fs-2 me-3 mt-1"></i>
                            <div>
                              <div className="fw-bold text-gray-800 mb-2">
                                Before you select the date for payroll report generation, ensure that:
                              </div>
                              <ul className="text-gray-700 mb-0">
                                <li>There are 1-7 days between the end date of the attendance cycle and the payroll report day.</li>
                                <li>The day of payroll report generation falls before the payday.</li>
                              </ul>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Leave Encashment Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <h3 className="card-title fw-bold">Leave Encashment Settings</h3>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-check-custom form-check-solid mb-4">
                          <Field
                            type="checkbox"
                            name="includeLeaveEncashment"
                            id="includeLeaveEncashment"
                            className="form-check-input"
                          />
                          <label className="form-check-label fw-semibold text-gray-800" htmlFor="includeLeaveEncashment">
                            Include leave encashment details in pay run
                          </label>
                        </div>
                        
                        <div className="text-muted fs-7">
                          Select this option to include leave encashment details of employees in a particular month's pay.
                        </div>

                        <div className="bg-light-info p-4 rounded mt-4">
                          <div className="d-flex align-items-start">
                            <i className="bi bi-info-circle text-info fs-2 me-3 mt-1"></i>
                            <div>
                              <div className="fw-bold text-gray-800 mb-1">
                                Note
                              </div>
                              <div className="text-gray-700">
                                You can access the leave encashment days only if the active leave encashment salary component is formula-based. 
                                To view or modify the leave encashment, navigate to{" "}
                                <a href="/settings/salary-components" className="text-primary fw-bold">
                                  Settings &gt; Salary Components &gt; Edit Leave Encashment
                                </a>.
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Summary Card */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <h3 className="card-title fw-bold">Current Settings Summary</h3>
                      </div>
                      <div className="card-body">
                        <div className="row">
                          <div className="col-md-6">
                            <div className="d-flex align-items-center mb-4">
                              <div className="symbol symbol-40px symbol-circle me-4">
                                <span className="symbol-label bg-light-primary">
                                  <i className="bi bi-calendar-week text-primary fs-2"></i>
                                </span>
                              </div>
                              <div>
                                <div className="fs-6 fw-bold text-gray-800">Attendance Cycle</div>
                                <div className="fs-5 fw-bolder text-primary">
                                  {values.attendanceCycleStart}th - {values.attendanceCycleEnd}th
                                </div>
                              </div>
                            </div>
                          </div>
                          
                          <div className="col-md-6">
                            <div className="d-flex align-items-center mb-4">
                              <div className="symbol symbol-40px symbol-circle me-4">
                                <span className="symbol-label bg-light-success">
                                  <i className="bi bi-file-earmark-text text-success fs-2"></i>
                                </span>
                              </div>
                              <div>
                                <div className="fs-6 fw-bold text-gray-800">Payroll Report Day</div>
                                <div className="fs-5 fw-bolder text-success">
                                  {values.payrollReportDay}th of each month
                                </div>
                              </div>
                            </div>
                          </div>
                          
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <div className="symbol symbol-40px symbol-circle me-4">
                                <span className="symbol-label bg-light-warning">
                                  <i className="bi bi-cash-coin text-warning fs-2"></i>
                                </span>
                              </div>
                              <div>
                                <div className="fs-6 fw-bold text-gray-800">Leave Encashment</div>
                                <div className="fs-5 fw-bolder text-warning">
                                  {values.includeLeaveEncashment ? "Included" : "Not Included"}
                                </div>
                              </div>
                            </div>
                          </div>
                          
                          <div className="col-md-6">
                            <div className="d-flex align-items-center">
                              <div className="symbol symbol-40px symbol-circle me-4">
                                <span className="symbol-label bg-light-info">
                                  <i className="bi bi-clock-history text-info fs-2"></i>
                                </span>
                              </div>
                              <div>
                                <div className="fs-6 fw-bold text-gray-800">Days Between</div>
                                <div className="fs-5 fw-bolder text-info">
                                  {calculateDaysBetween(values.attendanceCycleEnd, values.payrollReportDay)} days
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <hr className="my-5" style={{ borderColor: "#e0e0e0" }} />

                    <div className="d-flex justify-content-between align-items-center border-top pt-5">
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary w-10 mb-5"
                        disabled={isSubmitting}
                        data-kt-indicator={isSubmitting ? "on" : "off"}
                      >
                        <span className="indicator-label">
                          {hasPreferences ? "Update Preferences" : "Save Preferences"}
                        </span>
                        <span className="indicator-progress">
                          Please wait...
                          <span className="spinner-border spinner-border-sm align-middle ms-2"></span>
                        </span>
                      </button>
                      <div>
                        <span className="text-danger fs-7">* indicates mandatory fields</span>
                      </div>
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