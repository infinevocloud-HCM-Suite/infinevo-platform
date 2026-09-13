import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
// import Keycloak from "keycloak-js";
// import OAuth2Login from 'react-simple-oauth2-login';

import Loader from "../../../shared/components/loaders/fullPageLoader";
import loginBack from "../../../assets/images/login-back.png";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from "../../../assets/images/infine-logo.png";
// import qs from 'qs';
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
//import { dateFormats } from "../../../shared/appConfig/dateFormat";
import { format } from "date-fns";
import industryList from "../../../shared/appConfig/industryList";
import { countryStateList } from "../../../shared/appConfig/countryStateList";
import countryList from "../../../shared/appConfig/countryList";
import { countryStates } from "../../../shared/appConfig/countryStates";


export default function OrganisationProfile() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [initialValues, setInitialValues] = useState({
    organizationName: "",
    businessLocation: "",
    industry: "",
    dateFormat: "dd/MM/yyyy",
    addressLine1: "",
    addressLine2: "",
    state: "",
    city: "",
    pinCode: "",
    logo: null,
    updateAllTransactions: false,
    filingAddress: "",
    email: "",
    fileUrl: ""
  });

  const [availableStates, setAvailableStates] = useState([]);
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
  const [logoPreview, setLogoPreview] = useState(null);

  // new — store work locations from API and the selected filing location
  const [workLocations, setWorkLocations] = useState([]);
  const [filingLocation, setFilingLocation] = useState(null);


  // Get organization ID from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    if (organizationId) {
      fetchOrganizationData();
    }
  }, [organizationId]);

  // Update available states when business location (country) changes
  // useEffect(() => {
  //   if (initialValues.businessLocation) {
  //     const countryData = countryList.find(
  //       country => country.name === initialValues.businessLocation
  //     );

  //     if (countryData) {
  //       // Find states for the selected country
  //       const statesForCountry = countryStateList.filter(
  //         state => state.countryCode === countryData.code
  //       );
  //       setAvailableStates(statesForCountry);
  //     } else {
  //       setAvailableStates([]);
  //     }
  //   }
  // }, [initialValues.businessLocation]);


  // Update available states when business location (country) changes
  useEffect(() => {
    if (initialValues.businessLocation) {
      const statesForCountry = countryStates[initialValues.businessLocation] || [];
      setAvailableStates(statesForCountry);
    } else {
      setAvailableStates([]);
    }
  }, [initialValues.businessLocation]);




  const fetchOrganizationData = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/organizations/${organizationId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
          },
        }
      );

      if (response.data && response.data.data) {
        const orgData = response.data.data;

        // Prepare form initial values (unchanged)
        const values = {
          organizationName: orgData.organizationName || "",
          businessLocation: orgData.businessLocation || "",
          industry: orgData.industry || "",
          dateFormat: orgData.dateFormat || "dd/MM/yyyy",
          addressLine1: orgData.addressLine1 || "",
          addressLine2: orgData.addressLine2 || "",
          state: orgData.state || "",
          city: orgData.city || "",
          pinCode: orgData.pinCode || "",
          logo: null,
          updateAllTransactions: false,
          filingAddress: orgData.filingAddress || "",
          email: orgData.email || "",
          fileUrl: orgData.fileUrl || ""
        };

        setInitialValues(values);

        // Set logo preview if file exists
        if (orgData.fileUrl) {
          setLogoPreview(orgData.fileUrl);
        }

        // Set available states based on business location (country)
        if (orgData.businessLocation) {
          const statesForCountry = countryStates[orgData.businessLocation] || [];
          setAvailableStates(statesForCountry);
        }

        // --- NEW: capture workLocations array and determine filingLocation ---
        const wl = Array.isArray(orgData.workLocations) ? orgData.workLocations : [];
        setWorkLocations(wl);

        // Prefer the work location with isFilingAddress === true; fallback to first item or null
        const selectedFiling = wl.find(w => w.isFilingAddress === true) || wl[0] || null;
        setFilingLocation(selectedFiling);
      }
    } catch (error) {
      console.error("Error fetching organization data:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load organization data", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };


  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    organizationName: Yup.string()
      .required("Organisation name is required")
      .min(2, "Organisation name must be at least 2 characters")
      .max(100, "Organisation name cannot exceed 100 characters"),

    businessLocation: Yup.string()
      .required("Business location is required"),

    industry: Yup.string()
      .required("Industry is required"),

    dateFormat: Yup.string()
      .required("Date format is required"),

    addressLine1: Yup.string()
      .required("Address Line 1 is required")
      .min(5, "Address Line 1 must be at least 5 characters"),

    addressLine2: Yup.string(), // Optional, so no .required()

    state: Yup.string()
      .required("State is required"),

    city: Yup.string()
      .required("City is required")
      .min(2, "City must be at least 2 characters"),

    pinCode: Yup.string()
      .required("PIN code is required")
      .test("valid-pin", "PIN code must be a valid format for the country", function (value) {
        // Basic validation - can be enhanced for specific countries
        return /^[a-zA-Z0-9\s\-]{3,10}$/.test(value);
      }),

    logo: Yup.mixed()
      .test("fileSize", "Logo must be less than 1MB", function (value) {
        return !value || (value && value.size <= 1024 * 1024);
      })
      .test("fileType", "Unsupported file format. Only PNG, JPG, JPEG allowed", function (value) {
        return (
          !value ||
          (value &&
            ["image/jpeg", "image/jpg", "image/png"].includes(value.type))
        );
      }),

    updateAllTransactions: Yup.boolean(), // Optional checkbox

    filingAddress: Yup.string(), // Optional modal-only field
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    if (!organizationId) {
      errorMsg("Error", "Organization ID not found. Please select an organization first.", false);
      return;
    }

    try {
      setSubmitting(true);
      setSigningIn(true);

      // Build FormData for file upload
      const postData = new FormData();

      // Create organization object without logo field
      const { logo, updateAllTransactions, filingAddress, ...orgData } = values;

      // Convert the organization data to a Blob with proper content type
      const orgBlob = new Blob([JSON.stringify(orgData)], {
        type: 'application/json'
      });

      postData.append("organization", orgBlob);

      if (values.logo) {
        postData.append("file", values.logo);
      }

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/organizations/${organizationId}`,
        postData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
          },
        }
      );

      if (response.data && response.data.status === 200) {
        successMsg("Success", response.data.message || "Organization profile updated successfully", false);
        // Refresh the data to get updated file URL
        fetchOrganizationData();
      } else {
        errorMsg(
          "Update Failed",
          response.data?.message || `There was an error updating the profile. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Update Failed", e.response.data.message || "Failed to update organization profile", false);
      } else {
        errorMsg(e.code, e.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  const handleLogoChange = (event, form) => {
    const file = event.currentTarget.files[0];
    if (file) {
      form.setFieldValue("logo", file);

      // Create preview
      const reader = new FileReader();
      reader.onload = (e) => {
        setLogoPreview(e.target.result);
      };
      reader.readAsDataURL(file);
    }
  };

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Organization Profile</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Organisation Profile</h5>
        <span className="text-muted">
          Organisation ID: <span className="text-primary">{organizationId}</span>
        </span>
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
                {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                  <Form className="form w-100">
                    {/* Organization Logo Section */}
                    <div className="fv-row mb-10">
                      <div className="d-flex flex-stack mb-2">
                        <label
                          htmlFor="logo"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          Organisation Logo
                        </label>
                      </div>

                      <div className="d-flex align-items-start gap-4 flex-wrap">
                        {/* Upload Box with Preview */}
                        <div
                          className="upload-box d-flex align-items-center justify-content-center"
                          style={{
                            width: "120px",
                            height: "120px",
                            border: "2px dashed #ccc",
                            borderRadius: "8px",
                            cursor: "pointer",
                            position: "relative",
                            flexShrink: 0,
                            overflow: "hidden",
                          }}
                          onClick={() => document.getElementById("logoInput").click()}
                        >
                          {logoPreview ? (
                            <img
                              src={logoPreview}
                              alt="Logo Preview"
                              style={{
                                width: "100%",
                                height: "100%",
                                objectFit: "cover",
                              }}
                            />
                          ) : (
                            <span className="text-muted fw-semibold">📁 Upload Logo</span>
                          )}
                        </div>

                        {/* Guidelines + Remove link */}
                        <div className="flex-grow-1 text-muted small">
                          <p className="mb-1">
                            This logo will be displayed on documents such as Payslip and TDS
                            Worksheet.
                          </p>
                          <p className="mb-1">
                            <strong>Preferred Image Size:</strong> 240 × 240 pixels @ 72 DPI,
                            Maximum size of 1MB.
                          </p>
                          <p className="mb-0">
                            <strong>File Formats:</strong> PNG, JPG, and JPEG
                          </p>

                          {/* Remove Logo link (only if preview exists) */}
                          {logoPreview && (
                            <button
                              type="button"
                              className="btn btn-link text-danger p-0 mt-2"
                              onClick={async () => {
                                try {
                                  setSigningIn(true);
                                  await axios.delete(
                                    `${GlobalConst.API_URL}/api/organizations/${organizationId}/logo`,
                                    {
                                      headers: {
                                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                      },
                                    }
                                  );
                                  successMsg("Success", "Logo removed successfully", false);
                                  setLogoPreview(null); // clear local preview
                                  setFieldValue("logo", null); // reset form field
                                } catch (error) {
                                  errorMsg(
                                    "Error",
                                    error.response?.data?.message || "Failed to remove logo",
                                    true
                                  );
                                } finally {
                                  setSigningIn(false);
                                }
                              }}
                            >
                              Remove logo
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Hidden Input */}
                      <input
                        id="logoInput"
                        name="logo"
                        type="file"
                        accept="image/png, image/jpeg, image/jpg"
                        className={`form-control form-control-lg form-control-solid ${errors.logo && touched.logo ? "is-invalid" : ""
                          }`}
                        style={{ display: "none" }}
                        onChange={(event) => handleLogoChange(event, { setFieldValue })}
                      />

                      {/* Error Message */}
                      <ErrorMessage
                        name="logo"
                        component="div"
                        className="invalid-feedback d-block mt-2"
                      />
                    </div>


                    {/* Organization Name Field */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="organizationName"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Organisation Name
                        <RequiredStar />
                      </label>

                      <div
                        className="text-muted mb-2"
                        style={{ fontSize: "0.925rem" }}
                      >
                        This is your registered business name which will appear
                        in all the forms and payslips.
                      </div>

                      <Field
                        type="text"
                        name="organizationName"
                        id="organizationName"
                        className={`form-control form-control-lg form-control-solid ${errors.organizationName && touched.organizationName
                          ? "is-invalid"
                          : ""
                          }`}
                        placeholder="Enter organization name"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="organizationName"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row row mb-10">
                      {/* Business Location (Country) - Read Only */}
                      <div className="col-md-6 mb-3">
                        <label
                          htmlFor="businessLocation"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          Business Location (Country)
                          <span className="text-danger">*</span>
                        </label>
                        <Field
                          type="text"
                          name="businessLocation"
                          placeholder="Business Location"
                          disabled={true}
                          className="form-control form-control-lg form-control-solid bg-light"
                        />
                        <small className="text-muted">
                          Country cannot be changed
                        </small>
                        <ErrorMessage
                          name="businessLocation"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      <div className="col-md-6 mb-3">
                        <label
                          htmlFor="industry"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          Industry <RequiredStar />
                        </label>
                        <Field
                          as="select"
                          name="industry"
                          disabled={isSubmitting}
                          className={`form-control form-control-lg form-control-solid form-select ${errors.industry && touched.industry
                            ? "is-invalid"
                            : ""
                            }`}
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

                    <div className="row mt-3">
                      {/* Date Format */}
                      <div className="col-md-6 mb-3">
                        <label
                          htmlFor="dateFormat"
                          className="form-label fw-semibold"
                        >
                          Date Format<span className="text-danger">*</span>
                        </label>
                        <Field
                          as="select"
                          name="dateFormat"
                          className={`form-control form-control-lg form-control-solid form-select ${errors.dateFormat && touched.dateFormat
                            ? "is-invalid"
                            : ""
                            }`}
                        >
                          {dateFormats.map((fmt) => (
                            <option key={fmt.value} value={fmt.value}>
                              {fmt.label}
                            </option>
                          ))}
                        </Field>
                        <ErrorMessage
                          name="dateFormat"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>
                    </div>

                    {/* 👇 Address Section Starts */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="addressLine1"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Organisation Address <RequiredStar />
                      </label>

                      <div
                        className="text-muted mb-2"
                        style={{ fontSize: "0.925rem" }}
                      >
                        This will be considered as the address of your primary
                        work location.
                      </div>

                      {/* Address Line 1 */}
                      <Field
                        type="text"
                        name="addressLine1"
                        id="addressLine1"
                        className={`form-control form-control-lg form-control-solid mb-3 ${errors.addressLine1 && touched.addressLine1
                          ? "is-invalid"
                          : ""
                          }`}
                        placeholder="Enter address line 1"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="addressLine1"
                        component="div"
                        className="invalid-feedback"
                      />

                      {/* Address Line 2 */}
                      <Field
                        type="text"
                        name="addressLine2"
                        id="addressLine2"
                        className={`form-control form-control-lg form-control-solid ${errors.addressLine2 && touched.addressLine2
                          ? "is-invalid"
                          : ""
                          }`}
                        placeholder="Enter address line 2"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="addressLine2"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {/* 👇 State, City, Pincode in one row */}
                    <div className="row">
                      {/* State */}
                      <div className="col-md-4 mb-3">
                        <label
                          htmlFor="state"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          State <span className="text-danger">*</span>
                        </label>
                        {/* <Field
                          as="select"
                          name="state"
                          className={`form-control form-control-lg form-control-solid form-select ${errors.state && touched.state ? "is-invalid" : ""
                            }`}
                        >
                          <option value="">Select State</option>
                          {availableStates.map((state, index) => (
                            <option
                              key={index}
                              value={state.name}
                            >
                              {state.name}
                            </option>
                          ))}
                        </Field> */}


                        <Field
                          as="select"
                          name="state"
                          className={`form-control form-control-lg form-control-solid form-select ${errors.state && touched.state ? "is-invalid" : ""
                            }`}
                          disabled={isSubmitting || !initialValues.businessLocation}
                        >
                          <option value="">Select State</option>
                          {availableStates.map((state, index) => (
                            <option key={index} value={state}>
                              {state}
                            </option>
                          ))}
                        </Field>

                        <ErrorMessage
                          name="state"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* City */}
                      <div className="col-md-4 mb-3">
                        <label
                          htmlFor="city"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          City <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="city"
                          className={`form-control form-control-lg form-control-solid ${errors.city && touched.city ? "is-invalid" : ""
                            }`}
                          placeholder="City"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="city"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      {/* PIN Code */}
                      <div className="col-md-4 mb-3">
                        <label
                          htmlFor="pinCode"
                          className="form-label fs-6 fw-bold text-dark"
                        >
                          PIN Code <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="pinCode"
                          className={`form-control form-control-lg form-control-solid ${errors.pinCode && touched.pinCode
                            ? "is-invalid"
                            : ""
                            }`}
                          placeholder="PIN Code"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="pinCode"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>
                    </div>

                    {/* 👇 Checkbox: Update previous transactions */}
                    <div className="form-check form-check-custom form-check-solid mb-10 bg-light-warning p-5 rounded">
                      <Field
                        type="checkbox"
                        name="updateAllTransactions"
                        id="updateAllTransactions"
                        className="form-check-input"
                      />

                      <div className="form-text text-muted">
                        This option would update the new address in all previous
                        transactions.
                      </div>
                    </div>

                    <div className="card mb-6">
                      <div className="card-body">
                        <div className="d-flex justify-content-between align-items-center mb-2">
                          <div>
                            <div className="fs-6 fw-bolder text-gray-800">
                              Filing Address
                            </div>
                            <div className="text-muted fs-7">
                              This registered address will be used across all
                              Forms and Payslips.
                            </div>
                          </div>
                          <button
                            type="button"
                            className="btn btn-sm btn-light-primary"
                            onClick={() => setShowFilingModal(true)}
                          >
                            <i className="bi bi-pencil"></i> Change
                          </button>
                        </div>

                        <div className="mt-3">
                          <div className="fw-bold">
                            {filingLocation?.workLocationName || "Head Office"}
                          </div>

                          <div className="text-gray-700">
                            {/* street address (prefer filingLocation; fallback to organisation addressLine1/2) */}
                            {filingLocation?.streetAddress1 || values.addressLine1}
                            {filingLocation?.streetAddress2 ? `, ${filingLocation.streetAddress2}` : (values.addressLine2 ? `, ${values.addressLine2}` : '')}
                            <br />

                            {/* city, state */}
                            {filingLocation?.city || values.city}, {filingLocation?.state || values.state}
                            <br />

                            {/* zip / pin and country/businessLocation */}
                            {filingLocation?.zipCode || values.pinCode}
                            {filingLocation?.country ? `, ${filingLocation.country}` : (values.businessLocation ? `, ${values.businessLocation}` : '')}
                          </div>
                        </div>

                      </div>
                    </div>
{showFilingModal && (
  <div className="modal fade show d-block" tabIndex="-1">
    <div className="modal-dialog">
      <div className="modal-content">
        <div className="modal-header">
          <h5 className="modal-title">Update Filing Address</h5>
          <button
            type="button"
            className="btn btn-icon btn-sm btn-light"
            onClick={() => setShowFilingModal(false)}
          >
            <i className="bi bi-x fs-2"></i>
          </button>
        </div>

        <Formik
          enableReinitialize
          initialValues={{ filingAddress: filingLocation?.workLocationId || "" }}
          validate={(vals) => {
            const errors = {};
            if (!vals.filingAddress) errors.filingAddress = "Please select a filing address";
            return errors;
          }}
          onSubmit={async (values, { setSubmitting, setErrors }) => {
            setSubmitting(true);
            try {
              // validation guard (redundant but safe)
              if (!values.filingAddress) {
                setErrors({ filingAddress: "Please select a filing address" });
                return;
              }

              // Build URL and call PUT (controller expects path params, no body)
              const url = `${GlobalConst.API_URL}/api/organizations/${organizationId}/worklocation/${values.filingAddress}/filing-address`;

              // Await the PUT — network should appear in Network tab reliably
              await axios.put(url, null, {
                headers: {
                  Authorization: `Bearer ${localStorage.getItem("__t")}`,
                },
              });

              // Refresh from server so UI shows canonical state
              await fetchOrganizationData();

              const selected = workLocations.find(w => w.workLocationId === values.filingAddress) || null;
              setFilingLocation(selected);

              successMsg("Success", "Filing address updated", false);

              // Only close modal after success
              setShowFilingModal(false);
            } catch (err) {
              console.error("Failed to set filing address:", err);
              // Friendly error message
              errorMsg(
                "Error",
                err?.response?.data?.message || "Failed to update filing address. Please try again.",
                true
              );
            } finally {
              // ALWAYS reset submitting state
              setSubmitting(false);
            }
          }}
        >
          {({ isSubmitting, values: modalValues, submitForm, setFieldValue }) => {
            // preview chosen item (updates when setFieldValue runs)
            const preview = workLocations.find(w => w.workLocationId === modalValues.filingAddress) || filingLocation || null;

            return (
              <Form onSubmit={(e) => { e.preventDefault(); /* prevent native submit */ }}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label required">Select Filing Address</label>

                    <Field
                      as="select"
                      name="filingAddress"
                      className="form-select form-select-solid"
                      disabled={isSubmitting}
                      onChange={(e) => setFieldValue("filingAddress", e.target.value)}
                      value={modalValues.filingAddress}
                    >
                      <option value="">Select</option>
                      {workLocations.map((w) => (
                        <option key={w.workLocationId} value={w.workLocationId}>
                          {w.workLocationName}
                        </option>
                      ))}
                    </Field>

                    <ErrorMessage name="filingAddress" component="div" className="text-danger mt-1" />
                  </div>

                  <div className="bg-light rounded p-3">
                    <div className="fw-bold">{preview?.workLocationName || "Head Office"}</div>
                    <div className="text-gray-700">
                      {preview?.streetAddress1 || initialValues.addressLine1}
                      {preview?.streetAddress2 ? `, ${preview.streetAddress2}` : (initialValues.addressLine2 ? `, ${initialValues.addressLine2}` : "")}
                      <br />
                      {preview?.city || initialValues.city}, {preview?.state || initialValues.state}
                      <br />
                      {preview?.zipCode || initialValues.pinCode}{preview?.country ? `, ${preview.country}` : (initialValues.businessLocation ? `, ${initialValues.businessLocation}` : "")}
                    </div>
                  </div>

                  <div className="form-text mt-3">
                    <strong>Note:</strong> Your filing address can only be one of your work locations. To
                    set a new address as your filing address, add that address as a work location in{" "}
                    <a href="/settings/work-location" className="text-primary fw-bold">Settings &gt; Work Locations</a>.
                  </div>
                </div>

                <div className="modal-footer">
                  {/* Use button type=button and call submitForm explicitly to avoid native submit */}
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => submitForm()}
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? "Saving..." : "Save"}
                  </button>

                  <button
                    type="button"
                    className="btn btn-light"
                    onClick={() => setShowFilingModal(false)}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                </div>
              </Form>
            );
          }}
        </Formik>
      </div>
    </div>
  </div>
)}




                    <div className="row mb-10">
                      {/* Primary Contact Email Address */}
                      <div className="col-md-6">
                        <div className="fv-row mb-5">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Primary Contact Email Address
                          </label>

                          <div className="d-flex align-items-center">
                            <div className="symbol symbol-30px me-3">
                              <span className="symbol-label bg-light-warning text-warning fw-bold">
                                ★
                              </span>
                            </div>

                            <div>
                              <div className="fw-semibold fs-6 text-gray-800">
                                {/* 👇 Replace with dynamic username from backend */}
                                {initialValues.email.split('@')[0] || 'user'}
                              </div>
                              <div className="text-muted fs-7">
                                {/* 👇 Replace with dynamic email from backend */}
                                {initialValues.email}
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Emails Are Sent Through */}
                      <div className="col-md-6">
                        <div className="fv-row mb-5">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Emails Are Sent Through
                          </label>

                          <div className="d-flex align-items-start">
                            <div className="symbol symbol-30px me-3">
                              <i className="bi bi-envelope text-primary fs-3"></i>
                            </div>
                            <div className="flex-grow-1">
                              <div className="fw-semibold fs-7 text-gray-800">
                                {/* 👇 Replace with dynamic sender email from backend */}
                                Nevoinfi@gmail.com
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Info Box */}
                      <div className="col-12">
                        <div className="bg-light-info p-4 rounded">
                          <span className="text-gray-800 fs-7">
                            <strong className="text-danger">!</strong> Your
                            primary contact's email address belongs to a public
                            domain. So, emails will be sent from{" "}
                            <strong>Nevoinfi@gmail.com</strong>{" "}
                            to prevent them from landing in the Spam folder. If
                            you still want to send emails using the public
                            domain,&nbsp;
                            {/* <a
                              href="#"
                              className="text-primary text-hover-underline"
                            >
                              Change Setting
                            </a> */}
                            .
                          </span>
                        </div>
                      </div>

                      {/* Configure Sender Preferences */}
                      <div className="col-12 mt-4">
                        <a href="#" className="text-primary fw-semibold fs-7">
                          Configure Sender Email Preferences →
                        </a>
                      </div>
                    </div>

                    <hr
                      className="my-5"
                      style={{ borderColor: "#e0e0e0" }}
                    />

                    <div className="d-flex justify-content-between align-items-center border-top pt-5">
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary w-10 mb-5"
                        disabled={isSubmitting}
                        data-kt-indicator={isSubmitting ? "on" : "off"}
                      >
                        <span className="indicator-label">Save</span>
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