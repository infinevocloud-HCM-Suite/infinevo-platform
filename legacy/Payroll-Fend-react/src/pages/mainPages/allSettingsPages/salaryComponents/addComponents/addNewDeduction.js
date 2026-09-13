import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
// import Keycloak from "keycloak-js";
// import OAuth2Login from 'react-simple-oauth2-login';

import Loader from "../../../../../shared/components/loaders/fullPageLoader";

import { GlobalConst } from "../../../../../shared/appConfig/globalConst";
import axios from "axios";

import { useNavigate } from "react-router-dom";

// import qs from 'qs';
import { useDispatch } from "react-redux";
import { updateToken } from "../../../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../../../shared/helpers/msgHelper";
//import { dateFormats } from "../../../shared/appConfig/dateFormat";
import { format } from "date-fns";

export default function AddNewDeduction() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  
  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const [initialValues] = useState({
    nameInPayslip: "",
    deductionFrequency: "",
    isActive: true
  });

  const date = new Date();

  const dateFormats = [
    {
      value: "dd/MM/yyyy",
      label: `dd/MM/yyyy [ ${format(date, "dd/MM/yyyy")} ]`,
    },
    {
      value: "MM-dd-yyyy",
      label: `MM-dd-yyyy [ ${format(date, "MM-dd-yyyy")} ]`,
    },
    { value: "yy.MM.dd", label: `yy.MM.dd [ ${format(date, "yy.MM.dd")} ]` },
  ];

  const [showFilingModal, setShowFilingModal] = useState(false);

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    nameInPayslip: Yup.string()
      .required("Name in Payslip is required")
      .min(2, "Name must be at least 2 characters"),
    deductionFrequency: Yup.string().required("Please select deduction frequency"),
    isActive: Yup.boolean()
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    // Map form values to DTO structure
    const payload = {
      deductionName: values.nameInPayslip,
      isRecurring: values.deductionFrequency === "recurring",
      status: values.isActive ? "active" : "inactive",
      // Set default values for required fields in DTO
      deductionType: "custom", // Default type for custom deductions
      deductionTypeFormatted: "Custom Deduction",
      statusFormatted: values.isActive ? "Active" : "Inactive",
      isUserConfigurable: true,
      isAssociatedWithEmployee: false
    };

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/deductions`,
        payload,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response?.data?.status === 201) {
        successMsg(
          "Success",
          "Deduction created successfully",
          false
        );
        navigate("/salary-components/deductions");
      } else {
        errorMsg(
          "Save Failed",
          response?.data?.message || "Could not save deduction. Try again later.",
          true
        );
      }
    } catch (e) {
      console.error("Save Error:", e);
      if (e.response) {
        errorMsg("Save Failed", e.response.data?.message || e.message, true);
      } else if (e.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Save Failed", e.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Login</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">New Deduction</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "600px" }}
            >
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, values, setFieldValue }) => (
                  <Form className="form w-100">
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="nameInPayslip"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Name in Payslip <RequiredStar />
                      </label>
                      <Field
                        type="text"
                        name="nameInPayslip"
                        id="nameInPayslip"
                        className={`form-control form-control-lg form-control-solid w-75 ${
                          errors.nameInPayslip && touched.nameInPayslip
                            ? "is-invalid"
                            : ""
                        }`}
                        placeholder="Enter name for payslip"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="nameInPayslip"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Select the deduction frequency <span className="text-danger">*</span>
                      </label>

                      <div className="d-flex flex-column mt-2">
                        <div className="form-check mb-2">
                          <Field
                            type="radio"
                            name="deductionFrequency"
                            value="one-time"
                            id="one-time"
                            className="form-check-input"
                            checked={values.deductionFrequency === "one-time"}
                            onChange={() => setFieldValue("deductionFrequency", "one-time")}
                          />
                          <label className="form-check-label ms-2" htmlFor="one-time">
                            One-time deduction
                          </label>
                        </div>

                        <div className="form-check">
                          <Field
                            type="radio"
                            name="deductionFrequency"
                            value="recurring"
                            id="recurring"
                            className="form-check-input"
                            checked={values.deductionFrequency === "recurring"}
                            onChange={() => setFieldValue("deductionFrequency", "recurring")}
                          />
                          <label className="form-check-label ms-2" htmlFor="recurring">
                            Recurring deduction for subsequent Payrolls
                          </label>
                        </div>
                      </div>

                      <ErrorMessage
                        name="deductionFrequency"
                        component="div"
                        className="invalid-feedback d-block"
                      />
                    </div>

                    <div className="fv-row mb-10 form-check">
                      <Field
                        type="checkbox"
                        name="isActive"
                        className="form-check-input"
                        id="isActive"
                        checked={values.isActive}
                        onChange={(e) => setFieldValue("isActive", e.target.checked)}
                      />
                      <label
                        className="form-check-label text-dark"
                        htmlFor="isActive"
                      >
                        Mark this as Active
                      </label>
                    </div>

                    <div className="alert alert-warning mt-4" role="alert">
                      <strong>Note:</strong> Once this benefit is linked to an employee, you can edit only the Name in the Payslip. 
                      The update will reflect for all employees, past and future
                    </div>

                    <br></br>
                    <hr></hr>

                    {/* Save & Cancel */}
                    <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Saving..." : "Save"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/salary-components/deductions")}
                      >
                        Cancel
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