import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate } from "react-router-dom";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function AddHoliday() {
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [workLocations, setWorkLocations] = useState([]);

  const initialValues = {
    name: "",
    fromDate: "",
    toDate: "",
    description: "",
    restrictedHoliday: false,
    locations: []
  };

  // Get organization ID from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    fetchWorkLocations();
  }, []);

  const fetchWorkLocations = async () => {
    try {
      setLoading(true);
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
      errorMsg("Error", "Failed to load work locations", true);
    } finally {
      setLoading(false);
    }
  };

  const validationSchema = Yup.object().shape({
    name: Yup.string()
      .required("Holiday name is required")
      .min(2, "Holiday name must be at least 2 characters")
      .max(100, "Holiday name cannot exceed 100 characters"),
    fromDate: Yup.date()
      .required("Start date is required"),
    toDate: Yup.date()
      .required("End date is required")
      .min(Yup.ref('fromDate'), "End date must be after start date"),
    description: Yup.string()
      .max(500, "Description cannot exceed 500 characters"),
    locations: Yup.array()
      .min(1, "At least one work location must be selected")
  });

  const handleSubmit = async (values, { setSubmitting, resetForm }) => {
    if (!organizationId) {
      errorMsg("Error", "Organization ID not found. Please select an organization first.", false);
      return;
    }

    setSigningIn(true);

    try {
      const postData = {
        name: values.name,
        fromDate: values.fromDate,
        toDate: values.toDate,
        description: values.description,
        restrictedHoliday: values.restrictedHoliday,
        locations: values.locations
      };

      const response = await axios.post(
        `${GlobalConst.API_URL}/api/holidays`,
        postData,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data) {
        successMsg("Success", "Holiday created successfully", false);
        resetForm();
        setTimeout(() => {
          navigate("/holidays");
        }, 1500);
      }
    } catch (error) {
      console.error("Error creating holiday:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to create holiday", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Add Holiday</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Add Holiday</h5>
        <div className="d-flex align-items-center gap-2">
          <button 
            type="button" 
            className="btn btn-light btn-sm"
            onClick={() => navigate("/holidays")}
          >
            Cancel
          </button>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "600px" }}>
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
                      <h1 className="fw-bold text-gray-900 mb-2">Add Holiday</h1>
                    </div>

                    {/* Holiday Name */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Holiday Name <RequiredStar />
                      </label>
                      <Field
                        type="text"
                        name="name"
                        className={`form-control form-control-solid ${errors.name && touched.name ? "is-invalid" : ""}`}
                        placeholder="Enter holiday name"
                      />
                      <ErrorMessage name="name" component="div" className="invalid-feedback" />
                    </div>

                    {/* Date Section */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Date <RequiredStar />
                      </label>

                      <div className="row">
                        <div className="col-md-6 mb-3">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Start date <RequiredStar />
                          </label>
                          <Field
                            type="date"
                            name="fromDate"
                            className={`form-control form-control-solid ${errors.fromDate && touched.fromDate ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage name="fromDate" component="div" className="invalid-feedback" />
                        </div>

                        <div className="col-md-6 mb-3">
                          <label className="form-label fs-6 fw-bold text-dark">
                            End date <RequiredStar />
                          </label>
                          <Field
                            type="date"
                            name="toDate"
                            className={`form-control form-control-solid ${errors.toDate && touched.toDate ? "is-invalid" : ""}`}
                            min={values.fromDate}
                          />
                          <ErrorMessage name="toDate" component="div" className="invalid-feedback" />
                        </div>
                      </div>
                    </div>

                    {/* Description */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Description
                      </label>
                      <Field
                        as="textarea"
                        name="description"
                        className={`form-control form-control-solid ${errors.description && touched.description ? "is-invalid" : ""}`}
                        placeholder="Enter holiday description"
                        rows="4"
                      />
                      <ErrorMessage name="description" component="div" className="invalid-feedback" />
                    </div>

                    {/* Restricted Holiday Checkbox */}
                    <div className="fv-row mb-10">
                      <div className="form-check form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="restrictedHoliday"
                          id="restrictedHoliday"
                          className="form-check-input"
                        />
                        <label
                          className="form-check-label fw-semibold text-gray-800"
                          htmlFor="restrictedHoliday"
                        >
                          Restricted Holiday
                        </label>
                      </div>
                      <div className="text-muted fs-7 mt-1">
                        Mark this as a restricted holiday (optional)
                      </div>
                    </div>

                    {/* Work Locations Multi-select */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Work Locations <RequiredStar />
                      </label>
                      <div className={`border rounded p-3 ${errors.locations && touched.locations ? "border-danger" : "border-gray-300"}`}>
                        {workLocations.length > 0 ? (
                          workLocations.map((location) => (
                            <div key={location.workLocationId} className="form-check mb-2">
                              <Field
                                type="checkbox"
                                name="locations"
                                value={location.workLocationId}
                                id={`location-${location.workLocationId}`}
                                className="form-check-input"
                              />
                              <label
                                className="form-check-label"
                                htmlFor={`location-${location.workLocationId}`}
                              >
                                {location.name}
                              </label>
                            </div>
                          ))
                        ) : (
                          <div className="text-muted">No work locations available</div>
                        )}
                      </div>
                      <ErrorMessage name="locations" component="div" className="text-danger mt-1" />
                      <div className="text-muted fs-7 mt-1">
                        Select the work locations where this holiday applies
                      </div>
                    </div>

                    {/* Mandatory Fields Note */}
                    <div className="fv-row mb-10">
                      <span className="text-danger fs-7">* indicates mandatory fields</span>
                    </div>

                    {/* Form Actions */}
                    <div className="d-flex justify-content-end gap-3 border-top pt-5">
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/holidays")}
                      >
                        Cancel
                      </button>
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
      </div>

      {(signingIn || loading) && <Loader />}
    </>
  );
}