import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";

export default function Attendance() {
  const [signingIn, setSigningIn] = useState(false);
  const [showEditShiftModal, setShowEditShiftModal] = useState(false);
  const [attendancePreferences, setAttendancePreferences] = useState([]);
  const [hasPreferences, setHasPreferences] = useState(false);
  const [editingPreferenceId, setEditingPreferenceId] = useState(null);
  const [formKey, setFormKey] = useState(0); // Add key to force re-render

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const [initialValues, setInitialValues] = useState({
    // Work Shift Time - These will be updated from backend or modal
    checkInTime: "09:00",
    checkOutTime: "18:00",
    
    // Working Hours Calculation
    workingHoursCalculation: "firstLast",
    
    // Workday Duration
    minHalfDay: "04:00",
    minFullDay: "08:00",
    maxFullDay: "10:00",
    
    // Regularization Settings
    regularizationSetting: "anytime",
    regularizationDays: "",
    restrictRegularization: false,
    maxRegularizationDays: ""
  });

  // Fetch attendance preferences on component mount
  useEffect(() => {
    fetchAttendancePreferences();
  }, []);

  const fetchAttendancePreferences = async () => {
    try {
      setSigningIn(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/attendance-preferences`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      if (response.data && response.data.length > 0) {
        const preferences = response.data[0]; // Assuming we work with the first preference
        setAttendancePreferences(response.data);
        setHasPreferences(true);
        
        // Map backend data to frontend form values
        mapBackendToFrontend(preferences);
        setEditingPreferenceId(preferences.attendancePreferenceId);
      } else {
        setHasPreferences(false);
      }
    } catch (error) {
      console.error("API Error:", error);
      setHasPreferences(false);
      
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load attendance preferences", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };

  // Map backend DTO to frontend form values
  const mapBackendToFrontend = (backendData) => {
    const frontendValues = {
      // Work Shift Time - Use values from backend if available
      checkInTime: backendData.checkInTime || "09:00",
      checkOutTime: backendData.checkOutTime || "18:00",
      
      // Working Hours Calculation
      workingHoursCalculation: backendData.calculationOnFirstInLastOut ? "firstLast" : "everyCheck",
      
      // Workday Duration
      minHalfDay: backendData.halfDayMinimumHours || "04:00",
      minFullDay: backendData.fullDayMinimumHours || "08:00",
      maxFullDay: backendData.fullDayMaximumHours || "10:00",
      
      // Regularization Settings
      regularizationSetting: backendData.regularization?.allowFutureRegularization ? "anytime" : "limit",
      regularizationDays: backendData.regularization?.requestDaysBuffer || "",
      restrictRegularization: !!backendData.regularization?.maximumRequestsAllowed,
      maxRegularizationDays: backendData.regularization?.maximumRequestsAllowed || ""
    };
    
    setInitialValues(frontendValues);
    setFormKey(prev => prev + 1); // Force Formik to re-initialize with new values
  };

  // Map frontend form values to backend DTO
  const mapFrontendToBackend = (frontendValues) => {
    return {
      calculationOnFirstInLastOut: frontendValues.workingHoursCalculation === "firstLast",
      minimumHoursRequired: true,
      maximumHoursRequired: true,
      canIncludeHolidaysForPay: false,
      canIncludeLeavesForPay: false,
      canIncludeWeekendsForPay: false,
      fullDayMinimumHours: frontendValues.minFullDay,
      fullDayMaximumHours: frontendValues.maxFullDay,
      halfDayMinimumHours: frontendValues.minHalfDay,
      halfDayMaximumHours: frontendValues.maxFullDay,
      minimumHoursForOvertime: frontendValues.minFullDay,
      // Include checkInTime and checkOutTime in the backend data
      checkInTime: frontendValues.checkInTime,
      checkOutTime: frontendValues.checkOutTime,
      regularization: {
        canCreateNewEntries: true,
        allowFutureRegularization: frontendValues.regularizationSetting === "anytime",
        maximumRequestsAllowed: frontendValues.restrictRegularization ? frontendValues.maxRegularizationDays : null,
        periodType: "monthly",
        requestDaysBuffer: frontendValues.regularizationSetting === "limit" ? frontendValues.regularizationDays : null
      }
    };
  };

  const validationSchema = Yup.object().shape({
    checkInTime: Yup.string()
      .required("Check-in time is required"),

    checkOutTime: Yup.string()
      .required("Check-out time is required"),

    minHalfDay: Yup.string()
      .required("Minimum half day hours are required"),

    minFullDay: Yup.string()
      .required("Minimum full day hours are required"),

    maxFullDay: Yup.string()
      .required("Maximum full day hours are required"),

    regularizationDays: Yup.mixed().when("regularizationSetting", {
      is: "limit",
      then: () =>
        Yup.string().required("Regularization days are required"),
      otherwise: () => Yup.mixed().notRequired(),
    }),

    maxRegularizationDays: Yup.mixed().when("restrictRegularization", {
      is: true,
      then: () =>
        Yup.string().required("Max regularization days are required"),
      otherwise: () => Yup.mixed().notRequired(),
    }),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);

    try {
      const postData = mapFrontendToBackend(values);
      let response;
      
      if (editingPreferenceId) {
        // Update existing preference
        response = await axios.put(
          `${GlobalConst.API_URL}/api/attendance-preferences/${editingPreferenceId}`,
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
          `${GlobalConst.API_URL}/api/attendance-preferences`,
          postData,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            },
          }
        );
      }

      console.log("API Response:", response);
      
      if (response.data) {
        successMsg(
          "Success",
          editingPreferenceId ? "Attendance preferences updated successfully" : "Attendance preferences created successfully",
          false
        );
        setEditingPreferenceId(response.data.attendancePreferenceId);
        setHasPreferences(true);
        
        // Update initialValues with the saved data including check-in/check-out times
        setInitialValues(prev => ({
          ...prev,
          checkInTime: values.checkInTime,
          checkOutTime: values.checkOutTime
        }));
        setFormKey(prev => prev + 1); // Force re-render
      }
    } catch (error) {
      console.log("API Error:", error);
      
      if (error.response) {
        errorMsg(
          editingPreferenceId ? "Update Failed" : "Creation Failed",
          error.response.data?.message || (editingPreferenceId ? 'Attendance preferences update failed' : 'Attendance preferences creation failed'),
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

  const handleEditShift = () => {
    setShowEditShiftModal(true);
  };

  const handleCloseModal = () => {
    setShowEditShiftModal(false);
  };

  const handleModalSubmit = (modalValues) => {
    // Update the main form with new check-in/check-out times
    setInitialValues(prev => ({
      ...prev,
      checkInTime: modalValues.checkInTime,
      checkOutTime: modalValues.checkOutTime
    }));
    setFormKey(prev => prev + 1); // Force Formik to re-initialize with new values
    handleCloseModal();
  };

  const formatTimeForDisplay = (time) => {
    if (!time) return "";
    const [hours, minutes] = time.split(':');
    const hour = parseInt(hours);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    const formattedHour = hour % 12 || 12;
    return `${formattedHour}:${minutes} ${ampm}`;
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Attendance Preference</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Attendance Preference</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{ minHeight: '100vh', overflowY: 'auto', display: 'flex', justifyContent: 'center' }}
          >
            <div className="w-100" style={{ maxWidth: "1000px" }}>
              {/* Add key prop to Formik to force re-render when initialValues change */}
              <Formik
                key={formKey}
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, values, errors, touched }) => (
                  <Form>
                    {/* Define Work Shift Time Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Define Work Shift Time</h4>
                        <p className="text-muted mb-0">Set the regular working hours for your organisation</p>
                      </div>
                      <div className="card-body">
                        <div className="table-responsive">
                          <table className="table table-bordered">
                            <thead>
                              <tr className="fw-bold fs-6 text-gray-800">
                                <th>Check-In Time</th>
                                <th>Check-Out Time</th>
                                <th>Action</th>
                              </tr>
                            </thead>
                            <tbody>
                              <tr>
                                <td>{formatTimeForDisplay(values.checkInTime)}</td>
                                <td>{formatTimeForDisplay(values.checkOutTime)}</td>
                                <td>
                                  <button 
                                    type="button"
                                    className="btn btn-sm btn-light-primary"
                                    onClick={handleEditShift}
                                  >
                                    Edit
                                  </button>
                                </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                        
                        <div className="bg-light-info p-4 rounded mt-4">
                          <p className="text-muted mb-0">
                            <strong>Note:</strong> Based on your work shift hours, we consider a day in your organisation's attendance cycle to start at 1:30 AM and end at 1:30 AM the next day.
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* Rest of your form sections remain the same */}
                    {/* Working Hours Calculation Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Working Hours Calculation</h4>
                        <p className="text-muted mb-0">Define how to calculate the total working hours of the employees</p>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="radio"
                            name="workingHoursCalculation"
                            value="firstLast"
                            className="form-check-input"
                          />
                          <label className="form-check-label fw-bold">
                            First check-in and last check-out
                          </label>
                          <div className="text-muted ms-4">
                            Track the initial check-in and final check-out times for accurate attendance records.
                          </div>
                        </div>

                        <div className="form-check form-check-custom form-check-solid">
                          <Field
                            type="radio"
                            name="workingHoursCalculation"
                            value="everyCheck"
                            className="form-check-input"
                          />
                          <label className="form-check-label fw-bold">
                            Every valid check-in and check-out
                          </label>
                        </div>
                      </div>
                    </div>

                    {/* Workday Duration Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Workday Duration</h4>
                        <p className="text-muted mb-0">
                          Define the minimum and maximum hours for a standard workday, including options for half-day and full-day.
                        </p>
                      </div>
                      <div className="card-body">
                        <div className="row">
                          <div className="col-md-6">
                            <h5 className="fw-bold text-gray-800 mb-4">Minimum Hours <RequiredStar /></h5>
                            
                            <div className="mb-4">
                              <label className="form-label fw-semibold">Half Day</label>
                              <Field
                                type="time"
                                name="minHalfDay"
                                className={`form-control form-control-solid ${errors.minHalfDay && touched.minHalfDay ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage name="minHalfDay" component="div" className="invalid-feedback" />
                            </div>

                            <div className="mb-4">
                              <label className="form-label fw-semibold">Full Day</label>
                              <Field
                                type="time"
                                name="minFullDay"
                                className={`form-control form-control-solid ${errors.minFullDay && touched.minFullDay ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage name="minFullDay" component="div" className="invalid-feedback" />
                            </div>
                          </div>

                          <div className="col-md-6">
                            <h5 className="fw-bold text-gray-800 mb-4">Maximum Hours <RequiredStar /></h5>
                            
                            <div className="mb-4">
                              <label className="form-label fw-semibold">Full Day</label>
                              <Field
                                type="time"
                                name="maxFullDay"
                                className={`form-control form-control-solid ${errors.maxFullDay && touched.maxFullDay ? "is-invalid" : ""}`}
                              />
                              <ErrorMessage name="maxFullDay" component="div" className="invalid-feedback" />
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Regularization Settings Section */}
                    <div className="card mb-10">
                      <div className="card-header">
                        <h4 className="card-title fw-bold">Regularization Settings</h4>
                        <p className="text-muted mb-0">
                          In situations where check-in or check-out has been missed, choose when employees can request adjustments to ensure accurate records
                        </p>
                      </div>
                      <div className="card-body">
                        <div className="mb-5">
                          <div className="form-check form-check-custom form-check-solid mb-3">
                            <Field
                              type="radio"
                              name="regularizationSetting"
                              value="anytime"
                              className="form-check-input"
                            />
                            <label className="form-check-label fw-bold">Allow Anytime</label>
                          </div>

                          <div className="form-check form-check-custom form-check-solid mb-3">
                            <Field
                              type="radio"
                              name="regularizationSetting"
                              value="limit"
                              className="form-check-input"
                            />
                            <label className="form-check-label fw-bold">Limit Requests</label>
                          </div>

                          {values.regularizationSetting === "limit" && (
                            <div className="ms-5 mt-3">
                              <label className="form-label fw-semibold">
                                Number of days before current date for which attendance can be regularized
                              </label>
                              <Field
                                type="number"
                                name="regularizationDays"
                                className={`form-control form-control-solid w-auto ${errors.regularizationDays && touched.regularizationDays ? "is-invalid" : ""}`}
                                placeholder="Enter days"
                              />
                              <ErrorMessage name="regularizationDays" component="div" className="invalid-feedback" />
                            </div>
                          )}
                        </div>

                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="restrictRegularization"
                            className="form-check-input"
                          />
                          <label className="form-check-label fw-bold">
                            Restrict the number of regularization days an employee can make in a month
                          </label>
                        </div>

                        {values.restrictRegularization && (
                          <div className="ms-5 mt-3">
                            <Field
                              type="number"
                              name="maxRegularizationDays"
                              className={`form-control form-control-solid w-auto ${errors.maxRegularizationDays && touched.maxRegularizationDays ? "is-invalid" : ""}`}
                              placeholder="Days"
                            />
                            <ErrorMessage name="maxRegularizationDays" component="div" className="invalid-feedback" />
                          </div>
                        )}

                        {/* Save Button for the entire form */}
                        <div className="d-flex justify-content-end border-top pt-5 mt-5">
                          <button
                            type="submit"
                            className="btn btn-primary"
                            disabled={isSubmitting}
                          >
                            {isSubmitting ? (
                              <>
                                <span className="spinner-border spinner-border-sm me-2"></span>
                                Saving...
                              </>
                            ) : (
                              hasPreferences ? "Update Preferences" : "Save Preferences"
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>

      {/* Edit Shift Time Modal */}
      {showEditShiftModal && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0, 0, 0, 0.7)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h2 className="modal-title">Edit Shift Time</h2>
                <button
                  type="button"
                  className="btn btn-icon btn-sm btn-light"
                  onClick={handleCloseModal}
                >
                  <i className="bi bi-x fs-2"></i>
                </button>
              </div>

              <Formik
                initialValues={{
                  checkInTime: initialValues.checkInTime,
                  checkOutTime: initialValues.checkOutTime
                }}
                validationSchema={Yup.object().shape({
                  checkInTime: Yup.string().required("Check-in time is required"),
                  checkOutTime: Yup.string().required("Check-out time is required")
                })}
                onSubmit={(values, { setSubmitting }) => {
                  handleModalSubmit(values);
                  setSubmitting(false);
                }}
                enableReinitialize
              >
                {({ isSubmitting, values }) => (
                  <Form>
                    <div className="modal-body">
                      <div className="table-responsive">
                        <table className="table table-bordered">
                          <thead>
                            <tr className="fw-bold fs-6 text-gray-800">
                              <th>Check-In Time</th>
                              <th>Check-Out Time</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr>
                              <td>
                                <Field
                                  type="time"
                                  name="checkInTime"
                                  className="form-control form-control-solid"
                                />
                                <div className="text-center mt-1">
                                  {formatTimeForDisplay(values.checkInTime)}
                                </div>
                              </td>
                              <td>
                                <Field
                                  type="time"
                                  name="checkOutTime"
                                  className="form-control form-control-solid"
                                />
                                <div className="text-center mt-1">
                                  {formatTimeForDisplay(values.checkOutTime)}
                                </div>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <div className="modal-footer">
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={handleCloseModal}
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
                          "Save"
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