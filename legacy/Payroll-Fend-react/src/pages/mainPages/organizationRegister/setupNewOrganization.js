import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useParams, useNavigate } from "react-router-dom";

import Loader from "../../../shared/components/loaders/fullPageLoader";
import organizationImage from "../../../assets/images/organization-image.jpg";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from "../../../assets/images/infine-logo.png";

import legalStructures from "../../../shared/appConfig/legalStructures"; // (not used here, kept if you later need)
import timeZones from "../../../shared/appConfig/timezone";

import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer"; // (not used here, kept if you later need)
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

import { countryStates } from "../../../shared/appConfig/countryStates";
import industryList from "../../../shared/appConfig/industryList";

export default function SetupNewOrganization() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { organizationId } = useParams();

  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);

  const [initialValues, setInitialValues] = useState({
    organizationName: "",
    businessLocation: "", // Country
    state: "",
    industry: "",
    addressLine1: "",
    addressLine2: "",
    city: "",
    pinCode: "",
    hasRunPayroll: false, // boolean
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

  // -------------------- GET: Prefill by organizationId --------------------
  useEffect(() => {
    let isMounted = true;

    async function fetchOrganization() {
      if (!organizationId) {
        setLoading(false);
        errorMsg("Missing ID", "organizationId was not found in the URL.", false);
        return;
      }

      try {
        setLoading(true);
        const token = localStorage.getItem("__t");
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/organizations/${organizationId}`,
          token
            ? { headers: { Authorization: `Bearer ${token}` } }
            : undefined
        );

        if (response?.data?.status === 200 && response?.data?.data && isMounted) {
          const org = response.data.data;

          // Normalize/guard values
          const normalized = {
            organizationName: org.organizationName || "",
            businessLocation: org.businessLocation || "",
            state: org.state || "",
            industry: org.industry || "",
            addressLine1: org.addressLine1 || "",
            addressLine2: org.addressLine2 || "",
            city: org.city || "",
            pinCode: (org.pinCode ?? "").toString(),
            hasRunPayroll: Boolean(org.hasRunPayroll),
            timezone: org.timezone || "",
          };

          setInitialValues(normalized);
          setSelectedLocationCountry(normalized.businessLocation || "");
        } else {
          errorMsg(
            "Fetch failed",
            response?.data?.message || "Unable to fetch organization.",
            false
          );
        }
      } catch (e) {
        console.error("GET /organizations/{id} error: ", e);
        errorMsg(
          "Error",
          e.response?.data?.message || e.message || "Something went wrong while loading organization.",
          false
        );
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    fetchOrganization();
    return () => {
      isMounted = false;
    };
  }, [organizationId]);

  // -------------------- PUT: Update Organization --------------------
  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);
    setSubmitting(true);

    // DTO payload
    const putData = {
      organizationName: values.organizationName,
      businessLocation: values.businessLocation,
      industry: values.industry,
      addressLine1: values.addressLine1,
      addressLine2: values.addressLine2,
      state: values.state,
      city: values.city,
      pinCode: values.pinCode,
      hasRunPayroll: Boolean(values.hasRunPayroll),
      timezone: values.timezone,
      status: true, // keep if required by your backend
    };

    try {
      const token = localStorage.getItem("__t");
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/organizations/setup/${organizationId}`,
        putData,
        token
          ? { headers: { Authorization: `Bearer ${token}` } }
          : undefined
      );

      if (response?.data?.status === 200) {
        successMsg(
          "Organization updated",
          response?.data?.message || "Organization updated successfully",
          true,
          () => {
            navigate("/onboarding-dashboard");
          }
        );
      } else {
        errorMsg(
          "Update failed",
          response?.data?.message || "Unable to update organization.",
          false
        );
      }
    } catch (e) {
      console.error("PUT /organizations/setup/{id} error: ", e);
      errorMsg(
        "Error",
        e.response?.data?.message || e.message || "Something went wrong while updating organization.",
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

                {loading ? (
                  <div className="py-10"><Loader /></div>
                ) : (
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
                      values,
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
                            className={`form-control form-control-lg form-control-solid ${
                              errors.organizationName && touched.organizationName
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

                        {/* Business Location & Industry */}
                        <div className="row mb-7">
                          <div className="col-md-6">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Business Location (Country) <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="businessLocation"
                              className={`form-select form-select-lg form-select-solid ${
                                errors.businessLocation && touched.businessLocation
                                  ? "is-invalid"
                                  : ""
                              }`}
                              disabled={isSubmitting}
                              onChange={(e) => {
                                const selected = e.target.value;
                                setFieldValue("businessLocation", selected);
                                setSelectedLocationCountry(selected);
                                // Reset dependent fields
                                setFieldValue("state", "");
                                setFieldValue("timezone", "");
                              }}
                            >
                              <option value="">Select a country</option>
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
                              className={`form-select form-select-lg form-select-solid ${
                                errors.industry && touched.industry ? "is-invalid" : ""
                              }`}
                              disabled={isSubmitting}
                            >
                              <option value="">Select an industry type</option>
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

                        {/* Address Line 1 */}
                        <div className="fv-row mb-7">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Organization Address <RequiredStar />
                          </label>
                          <small className="text-muted d-block mb-2">
                            This will be considered as the address of your primary work location.
                          </small>
                          <Field
                            type="text"
                            name="addressLine1"
                            className={`form-control form-control-lg form-control-solid ${
                              errors.addressLine1 && touched.addressLine1
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
                            className={`form-control form-control-lg form-control-solid ${
                              errors.addressLine2 && touched.addressLine2
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

                        {/* State, City, PIN */}
                        <div className="row mb-7">
                          <div className="col-md-4">
                            <label className="form-label fs-6 fw-bold text-dark">
                              State <RequiredStar />
                            </label>
                            <Field
                              as="select"
                              name="state"
                              className={`form-select form-select-lg form-select-solid ${
                                errors.state && touched.state ? "is-invalid" : ""
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
                              className={`form-control form-control-lg form-control-solid ${
                                errors.city && touched.city ? "is-invalid" : ""
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
                              className={`form-control form-control-lg form-control-solid ${
                                errors.pinCode && touched.pinCode ? "is-invalid" : ""
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
                            Time Zone <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="timezone"
                            className={`form-select form-select-lg form-select-solid ${
                              errors.timezone && touched.timezone ? "is-invalid" : ""
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

                        {/* Radio Group - hasRunPayroll (boolean) */}
                        <div className="fv-row mb-7">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Have you run payroll earlier this financial year?
                          </label>

                          <div className="form-check form-check-custom form-check-solid mb-2">
                            <input
                              type="radio"
                              name="hasRunPayroll"
                              className="form-check-input"
                              id="runPayrollYes"
                              checked={values.hasRunPayroll === true}
                              onChange={() => setFieldValue("hasRunPayroll", true)}
                              disabled={isSubmitting}
                            />
                            <label className="form-check-label" htmlFor="runPayrollYes">
                              Yes, we've already run payroll(s) for this financial year.
                            </label>
                          </div>

                          <div className="form-check form-check-custom form-check-solid">
                            <input
                              type="radio"
                              name="hasRunPayroll"
                              className="form-check-input"
                              id="runPayrollNo"
                              checked={values.hasRunPayroll === false}
                              onChange={() => setFieldValue("hasRunPayroll", false)}
                              disabled={isSubmitting}
                            />
                            <label className="form-check-label" htmlFor="runPayrollNo">
                              No, we'll run this financial year's first INFINEVOCLOUD with Payroll.
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
                )}

              </div>
            </div>

            <div className="d-flex flex-center flex-wrap fs-6 p-5 pb-0">
              <div className="d-flex flex-center fw-semibold fs-6">
                <a
                  href="http://infinevocloud.com/about.php"
                  className="text-muted text-hover-primary px-2"
                  target="_blank"
                  rel="noreferrer"
                >
                  About
                </a>
                <a
                  href="http://infinevocloud.com/contact.php"
                  className="text-muted text-hover-primary px-2"
                  target="_blank"
                  rel="noreferrer"
                >
                  Support
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>

      {(signingIn || loading) && <Loader />}
    </>
  );
}
