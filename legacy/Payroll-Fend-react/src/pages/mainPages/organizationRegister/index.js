import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Loader from "../../../shared/components/loaders/fullPageLoader";
import organizationImage from "../../../assets/images/organization-image.jpg";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from "../../../assets/images/infine-logo.png";

import legalStructures from "../../../shared/appConfig/legalStructures";
import timeZones from "../../../shared/appConfig/timezone";

import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

import { countryStates } from "../../../shared/appConfig/countryStates";
import industryList from "../../../shared/appConfig/industryList";

export default function OrganizationRegister() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);

  const [initialValues, setInitialValues] = useState({
    organizationName: "",
    businessLocation: "", // Business Location - Country
    state: "", // Business Location - State
    industry: "",
    addressLine1: "",
    addressLine2: "",
    city: "",
    pinCode: "",
    hasRunPayroll: false, // Boolean value instead of string
    timezone: "",
    // email: "",
  });

  const [selectedLocationCountry, setSelectedLocationCountry] = useState("");

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    organizationName: Yup.string().required("Organization name is required"),
    businessLocation: Yup.string().required("Country is required"),
    state: Yup.string().required("State is required"),
    industry: Yup.string().required("Industry is required"),
    addressLine1: Yup.string().required("Address Line 1 is required"),
    addressLine2: Yup.string(), // optional
    city: Yup.string().required("City is required"),
    pinCode: Yup.string().required("PIN Code is required"),
    timezone: Yup.string().required("Time Zone is required"),
    // email: Yup.string().email("Invalid email format").required("Email is required"),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);
    setSubmitting(true);

    // Map frontend fields to backend DTO structure
    const postData = {
      organizationName: values.organizationName,
      businessLocation: values.businessLocation,
      industry: values.industry,
      addressLine1: values.addressLine1,
      addressLine2: values.addressLine2,
      state: values.state,
      city: values.city,
      pinCode: values.pinCode,
      hasRunPayroll: values.hasRunPayroll,
      timezone: values.timezone,
      // email: values.email,
      status: true, // Default status
    };

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/organizations/new`,
        postData
      );

      // Backend returns { status, message, data }
      if (response.data?.status === 201) {
        successMsg(
          "Organization Created",
          response.data.message || "Organization created successfully",
          true,
          () => { window.location.href = "/onboarding-dashboard"; }
        );
      } else {
        errorMsg(
          "Submission Failed",
          response.data?.message || "Unable to create organization.",
          false
        );
      }
    } catch (e) {
      console.error("Submission Error: ", e);
      errorMsg(
        "Error",
        e.response?.data?.message || e.message || "Something went wrong",
        false
      );
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>Payroll InfiNevoCloud - Organization</title>
      </Helmet>
      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-auto bg-primary w-xl-600px positon-xl-relative">
          <div className="d-flex flex-column position-xl-fixed top-0 bottom-0 w-xl-600px scroll-y">
            <div className="d-flex flex-row-fluid flex-column text-center p-5 p-lg-10 pt-lg-20">
              <a href="/" className="py-2 py-lg-20">
                <img alt="Logo" src={logo} className="h-40px h-lg-20px" />
              </a>
              <div className="py-2 py-lg-10"></div>
              <h1 className="d-none d-lg-block fw-bold text-white fs-2qx pb-5 pb-md-10">
                Welcome to <br />
                Payroll INFINEVOCLOUD
              </h1>
              <p
                className="d-none d-lg-block fw-bold fs-1 text-black mb-5"
                style={{
                  letterSpacing: "-0.5px",
                  lineHeight: "1.4",
                  fontWeight: 800,
                }}
              >
                Tell us a bit about your organisation
              </p>
            </div>
            <div
              className="d-none d-lg-block d-flex flex-row-auto bgi-no-repeat bgi-position-x-center bgi-size-contain bgi-position-y-bottom min-h-100px min-h-lg-350px"
              style={{ backgroundImage: `url(${organizationImage})` }}
            ></div>
          </div>
        </div>

        <div className="d-flex flex-column flex-lg-row-fluid py-10">
          <div className="d-flex flex-center flex-column flex-column-fluid">
            <div className="w-lg-800px p-10 p-lg-15 mx-auto">
              <div className="card shadow-sm p-10 bg-white rounded-3 border-0">
                <Formik
                  initialValues={initialValues}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize
                >
                  {({
                    isSubmitting,
                    errors,
                    touched,
                    isValid,
                    setFieldValue,
                    values
                  }) => (
                    <Form className="form w-100">
                      <div className="text-center mb-10"></div>

                      {/* Organization Name */}
                      <div className="fv-row mb-10">
                        <label
                          htmlFor="organizationName"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          Organization Name <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="organizationName"
                          className={`form-control form-control-lg form-control-solid ${errors.organizationName && touched.organizationName
                              ? "is-invalid"
                              : ""
                            }`}
                          placeholder="Enter your organization name"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="organizationName"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* Business Location */}
                      <div className="row mb-7">
                        <div className="col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Business Location (Country) <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="businessLocation"
                            className={`form-select form-select-lg form-select-solid ${errors.businessLocation && touched.businessLocation
                                ? "is-invalid"
                                : ""
                              }`}
                            disabled={isSubmitting}
                            onChange={(e) => {
                              const selected = e.target.value;
                              setFieldValue("businessLocation", selected);
                              setSelectedLocationCountry(selected);
                              setFieldValue("state", "");
                            }}
                          >
                            <option value="">
                              Select a country <RequiredStar />
                            </option>
                            {Object.keys(countryStates).map((country) => (
                              <option key={country} value={country}>
                                {country}
                              </option>
                            ))}
                          </Field>
                          <ErrorMessage
                            name="businessLocation"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Industry <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="industry"
                            className={`form-select form-select-lg form-select-solid ${errors.industry && touched.industry
                                ? "is-invalid"
                                : ""
                              }`}
                            disabled={isSubmitting}
                          >
                            <option value="">
                              Select an industry type
                            </option>
                            {industryList.map((industry, index) => (
                              <option key={index} value={industry}>
                                {industry}
                              </option>
                            ))}
                          </Field>
                          <ErrorMessage
                            name="industry"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Email */}
                      {/* <div className="fv-row mb-7">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Email <RequiredStar />
                        </label>
                        <Field
                          type="email"
                          name="email"
                          className={`form-control form-control-lg form-control-solid ${errors.email && touched.email
                              ? "is-invalid"
                              : ""
                            }`}
                          placeholder="Enter organization email"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="email"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div> */}

                      {/* Address Line 1 */}
                      <div className="fv-row mb-7">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Organization Address <RequiredStar />
                        </label>
                        <small className="text-muted d-block mb-2">
                          This will be considered as the address of your primary
                          work location.
                        </small>
                        <Field
                          type="text"
                          name="addressLine1"
                          className={`form-control form-control-lg form-control-solid ${errors.addressLine1 && touched.addressLine1
                              ? "is-invalid"
                              : ""
                            }`}
                          placeholder="Address Line 1"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="addressLine1"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* Address Line 2 */}
                      <div className="fv-row mb-7">
                        <Field
                          type="text"
                          name="addressLine2"
                          className={`form-control form-control-lg form-control-solid ${errors.addressLine2 && touched.addressLine2
                              ? "is-invalid"
                              : ""
                            }`}
                          placeholder="Address Line 2 (Optional)"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="addressLine2"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* State, City and PIN Code */}
                      <div className="row mb-7">
                        <div className="col-md-4">
                          <label className="form-label fs-6 fw-bold text-dark">
                            State <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="state"
                            className={`form-select form-select-lg form-select-solid ${errors.state && touched.state ? "is-invalid" : ""
                              }`}
                            disabled={isSubmitting || !selectedLocationCountry}
                          >
                            <option value="">Select a state</option>
                            {(countryStates[selectedLocationCountry] || []).map(
                              (state) => (
                                <option key={state} value={state}>
                                  {state}
                                </option>
                              )
                            )}
                          </Field>
                          <ErrorMessage
                            name="state"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-md-4">
                          <label className="form-label fs-6 fw-bold text-dark">
                            City <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="city"
                            className={`form-control form-control-lg form-control-solid ${errors.city && touched.city ? "is-invalid" : ""
                              }`}
                            placeholder="Enter city"
                            disabled={isSubmitting}
                          />
                          <ErrorMessage
                            name="city"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-md-4">
                          <label className="form-label fs-6 fw-bold text-dark">
                            PIN Code <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="pinCode"
                            className={`form-control form-control-lg form-control-solid ${errors.pinCode && touched.pinCode ? "is-invalid" : ""
                              }`}
                            placeholder="Enter PIN code"
                            disabled={isSubmitting}
                          />
                          <ErrorMessage
                            name="pinCode"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Time Zone */}
                      <div className="fv-row mb-7">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Time Zone 
                        </label>
                        <Field
                          as="select"
                          name="timezone"
                          className={`form-select form-select-lg form-select-solid ${errors.timezone && touched.timezone
                              ? "is-invalid"
                              : ""
                            }`}
                          disabled={isSubmitting}
                        >
                          <option value="">Select a time zone</option>
                          {(timeZones[selectedLocationCountry] || []).map(
                            (zone, index) => (
                              <option key={index} value={zone}>
                                {zone}
                              </option>
                            )
                          )}
                        </Field>
                        <ErrorMessage
                          name="timezone"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* Radio Group */}
                      <div className="fv-row mb-7">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Have you run payroll earlier this financial year?
                        </label>
                        <div className="form-check form-check-custom form-check-solid mb-2">
                          <Field
                            type="radio"
                            name="hasRunPayroll"
                            value={true}
                            className="form-check-input"
                            id="runPayrollYes"
                          />
                          <label
                            className="form-check-label"
                            htmlFor="runPayrollYes"
                          >
                            Yes, we've already run payroll(s) for this financial
                            year.
                          </label>
                        </div>

                        <div className="form-check form-check-custom form-check-solid">
                          <Field
                            type="radio"
                            name="hasRunPayroll"
                            value={false}
                            className="form-check-input"
                            id="runPayrollNo"
                          />
                          <label
                            className="form-check-label"
                            htmlFor="runPayrollNo"
                          >
                            No, we'll run this financial year's first
                            INFINEVOCLOUD with Payroll.
                          </label>
                        </div>
                        <ErrorMessage
                          name="hasRunPayroll"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                      </div>

                      {/* Submit Buttons */}
                      <div className="text-center">
                        <button
                          type="submit"
                          className="btn btn-lg btn-primary w-100 mb-3"
                          disabled={isSubmitting || !isValid}
                        >
                          Save & Continue
                        </button>
                        <a href="#" className="btn btn-text text-muted">
                          Cancel
                        </a>
                      </div>
                    </Form>
                  )}
                </Formik>
              </div>
            </div>

            <div className="d-flex flex-center flex-wrap fs-6 p-5 pb-0">
              <div className="d-flex flex-center fw-semibold fs-6">
                <a
                  href="http://infinevocloud.com/about.php"
                  className="text-muted text-hover-primary px-2"
                  target="_blank"
                >
                  About
                </a>
                <a
                  href="http://infinevocloud.com/contact.php"
                  className="text-muted text-hover-primary px-2"
                  target="_blank"
                >
                  Support
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
      {signingIn && <Loader />}
    </>
  );
}