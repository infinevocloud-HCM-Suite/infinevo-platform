import React from "react";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { getStates } from "../../../shared/appConfig/stateList";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { useState, useEffect } from "react";


const RequiredStar = () => <span className="text-danger">*</span>;

const PersonalDetails = ({ initialValues, onNext, onPrev, employeeId }) => {
  const [signingIn, setSigningIn] = React.useState(false);

    const [personaldetails, setPersonalDetails] = useState([]);
      const [loading, setLoading] = useState(false);
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");



   // Fetch earnings data when component mounts
      // useEffect(() => {
      //     fetchPersonalDetails();
      // }, [organizationId]);
  
      // const fetchPersonalDetails = async () => {
      //     setLoading(true);
      //     try {
      //         const response = await axios.get(`${GlobalConst.API_URL}/api/employees/personal-details/${employeeId}`, {
      //             headers: {
      //                 Authorization: `Bearer ${token}`,
      //                 organizationId: organizationId
      //             }
      //         });
  
      //         if (response.data && response.data.data) {
      //             setPersonalDetails(response.data.data);
      //         }
      //     } catch (error) {
      //         console.error("Failed to fetch personal details:", error);
      //         errorMsg("Error", "Failed to fetch Personal Details data", false);
      //     } finally {
      //         setLoading(false);
      //     }
      // };
  

const validationSchema = Yup.object().shape({
  dateOfBirth: Yup.date()
    .required("Date of Birth is required")
    .max(new Date(new Date().setFullYear(new Date().getFullYear() - 18)), "User must be at least 18 years old"),

  age: Yup.number()
    .min(0, "Age cannot be negative")
    .max(120, "Enter a valid age")
    .required("Age is required"),

  fatherName: Yup.string().required("Father Name is required"),

  panNumber: Yup.string()
    .matches(/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/, "Invalid PAN Number format")
    .required("PAN Number is required"),

  differentlyAbledType: Yup.string().required("Please select a type"),

  personalEmail: Yup.string()
    .email("Invalid email address")
    .required("Personal Email is required"),

  residentialAddress: Yup.object({
    addressLine1: Yup.string().required("Address Line 1 is required"),
    addressLine2: Yup.string().nullable(),
    city: Yup.string().required("City is required"),
    state: Yup.string().required("State is required"),
    pincode: Yup.string()
      .matches(/^[1-9][0-9]{5}$/, "Invalid PIN Code")
      .required("PIN Code is required"),
  }),
});

  // const handleSubmit = async (values, { setSubmitting }) => {
  //   setSigningIn(true);
  //   try {
  //     // Map frontend data to backend DTO structure
  //     const personalDetailsData = mapToBackendDTO(values, employeeId, organizationId);

  //     // Always call POST
  //     const apiUrl = `${GlobalConst.API_URL}/api/employees/personal-details`;

  //     const response = await axios.post(
  //       apiUrl,
  //       personalDetailsData,
  //       {
  //         headers: {
  //           "Content-Type": "application/json",
  //           Authorization: `Bearer ${token}`,
  //           organizationId: organizationId
  //         }
  //       }
  //     );


  //     if (response.data && (response.data.status === 200 || response.data.status === 201)) {
  //       successMsg("Success", response.data.message || "Personal details saved successfully", false);
  //       localStorage.setItem('currentEmployeeId', response.data.data.id);

  //       onNext(); // Move to next step
  //     } else {
  //       errorMsg("Save Failed", "Failed to save personal details", false);
  //     }
  //   } catch (error) {
  //     console.error("API Error:", error);
  //     if (error.response) {
  //       errorMsg("Save Failed", error.response.data?.message || "Failed to save personal details", false);
  //     } else if (error.request) {
  //       errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
  //     } else {
  //       errorMsg("Error", "An unexpected error occurred", false);
  //     }
  //   } finally {
  //     setSigningIn(false);
  //     setSubmitting(false);
  //   }
  // };

  const handleSubmit = async (values, { setSubmitting, setFieldError }) => {
  setSigningIn(true);
  try {
    const personalDetailsData = mapToBackendDTO(values, employeeId, organizationId);
    const apiUrl = `${GlobalConst.API_URL}/api/employees/personal-details`;

    const response = await axios.post(apiUrl, personalDetailsData, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        organizationId: organizationId
      }
    });

    if (response.data && (response.data.status === 200 || response.data.status === 201)) {
      successMsg("Success", response.data.message || "Personal details saved successfully", false);
      /* Personal-details response id is not substituted for wizard employee id — parent persists step only. */
      onNext?.(); // Move to next step (safely call)
    } else {
      // Generic fallback
      errorMsg("Save Failed", response.data?.message || "Failed to save personal details", false);
    }
  } catch (error) {
    console.error("API Error:", error);

    // If backend returns structured field error or message, try to display it on the PAN field specifically
    if (error.response && error.response.data) {
      const data = error.response.data;

      // 1) If your backend returns a fieldErrors object, use that:
      //   { message: "...", fieldErrors: { pan: "PAN already exists" } }
      if (data.fieldErrors && (data.fieldErrors.pan || data.fieldErrors.panNumber)) {
        const msg = data.fieldErrors.pan || data.fieldErrors.panNumber;
        setFieldError("panNumber", msg);
      }
      // 2) If backend returns a plain message string that mentions PAN, map it:
      else if (typeof data.message === "string" && /pan/i.test(data.message)) {
        setFieldError("panNumber", data.message);
      } else {
        // Generic UI fallback:
        errorMsg("Save Failed", data.message || "Failed to save personal details", false);
      }
    } else if (error.request) {
      // No response from server
      errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
    } else {
      errorMsg("Error", "An unexpected error occurred", false);
    }
  } finally {
    setSigningIn(false);
    setSubmitting(false);
  }
};


  const mapToBackendDTO = (values, employeeId, organizationId) => {
    return {
      employeeId: employeeId,
      personalMail: values.personalEmail,
      dateOfBirth: values.dateOfBirth,
      fatherName: values.fatherName,
      pan: values.panNumber,
      differentlyAbledType: values.differentlyAbledType,
      isEligibleForFullIncomeTaxExemption: false, // Default value
      organizationId: organizationId,
      presentResidentialAddress: {
        addressLine1: values.residentialAddress.addressLine1,
        addressLine2: values.residentialAddress.addressLine2 || "",
        city: values.residentialAddress.city,
        state: values.residentialAddress.state,
        zipCode: values.residentialAddress.pincode,
        stateCode: "" // You might need to map this based on state
      },
      customFields: [] // Empty array as we don't have custom fields in the form
    };
  };

  // 🔹 Helper function to calculate age from DOB
  const calculateAge = (dob) => {
    if (!dob) return "";
    const today = new Date();
    const birthDate = new Date(dob);
    let age = today.getFullYear() - birthDate.getFullYear();
    const m = today.getMonth() - birthDate.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age;
  };

  return (
    <>
      {/* <Formik
        initialValues={initialValues}
        validationSchema={validationSchema}
        onSubmit={handleSubmit}
        enableReinitialize={true}
      > */}
      <Formik
  initialValues={initialValues}
  validationSchema={validationSchema}
  onSubmit={(values, formikHelpers) => handleSubmit(values, formikHelpers)}
  enableReinitialize={true}
>

        {({ isSubmitting, errors, touched, setFieldValue, values }) => (
          <Form>
            <div className="mb-4">
              {/* Date of Birth and Age */}
              <div className="row g-3 mb-4">
                {/* Date of Birth */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    Date of Birth <RequiredStar />
                  </label>
                  <Field
                    type="date"
                    name="dateOfBirth"
                    placeholder="Date of Birth"
                    className={`form-control ${errors.dateOfBirth && touched.dateOfBirth
                        ? "is-invalid"
                        : ""
                      }`}
                    disabled={isSubmitting}
                    onChange={(e) => {
                      const dob = e.target.value;
                      setFieldValue("dateOfBirth", dob);
                      const age = calculateAge(dob);
                      setFieldValue("age", age);
                    }}
                  />
                  <ErrorMessage
                    name="dateOfBirth"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>

                {/* Age (Auto-calculated) */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    Age
                  </label>
                  <Field
                    type="number"
                    name="age"
                    placeholder="Age"
                    className={`form-control ${errors.age && touched.age ? "is-invalid" : ""
                      }`}
                    value={values.age || ""} // always reflect calculated age
                    disabled // ✅ read-only, auto-filled
                  />
                  <ErrorMessage
                    name="age"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>
              </div>

              {/* Father Name and PAN */}
              <div className="row g-3 mb-4">
                {/* Father Name */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    Father Name <RequiredStar />
                  </label>
                  <Field
                    type="text"
                    name="fatherName"
                    placeholder="Enter Father Name"
                    className={`form-control ${errors.fatherName && touched.fatherName ? "is-invalid" : ""
                      }`}
                    disabled={isSubmitting}
                  />
                  <ErrorMessage
                    name="fatherName"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>

                {/* PAN Number */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    PAN Number <RequiredStar />
                  </label>
                  <Field
                    type="text"
                    name="panNumber"
                    placeholder="ABCDE1234F"
                    maxLength="10"
                    className={`form-control ${errors.panNumber && touched.panNumber ? "is-invalid" : ""
                      }`}
                    disabled={isSubmitting}
                  />
                  <ErrorMessage
                    name="panNumber"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>
              </div>

              {/* Differently Abled Type and Personal Email Address */}
              <div className="row g-3 mb-4">
                {/* Differently Abled Type */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    Differently Abled Type <RequiredStar />
                  </label>
                  <Field
                    as="select"
                    name="differentlyAbledType"
                    className={`form-select ${errors.differentlyAbledType && touched.differentlyAbledType
                        ? "is-invalid"
                        : ""
                      }`}
                    disabled={isSubmitting}
                  >
                    <option value="">Select Type</option>
                    <option value="None">None</option>
                    <option value="Visual">Visual</option>
                    <option value="Hearing">Hearing</option>
                    <option value="Speech">Speech</option>
                    <option value="Mobility">Mobility</option>
                    <option value="Other">Other</option>
                  </Field>
                  <ErrorMessage
                    name="differentlyAbledType"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>

                {/* Personal Email Address */}
                <div className="col-12 col-md-6">
                  <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                    Personal Email Address <RequiredStar />
                  </label>
                  <Field
                    type="email"
                    name="personalEmail"
                    placeholder="example@email.com"
                    className={`form-control ${errors.personalEmail && touched.personalEmail
                        ? "is-invalid"
                        : ""
                      }`}
                    disabled={isSubmitting}
                  />
                  <ErrorMessage
                    name="personalEmail"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>
              </div>

              {/* Residential Address */}
              <div className="mb-4">
                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                  Residential Address <RequiredStar />
                </label>

                {/* Address Line 1 */}
                <div className="mb-3">
                  <Field
                    type="text"
                    name="residentialAddress.addressLine1"
                    placeholder="Address Line 1"
                    className={`form-control ${errors.residentialAddress?.addressLine1 &&
                        touched.residentialAddress?.addressLine1
                        ? "is-invalid"
                        : ""
                      }`}
                    disabled={isSubmitting}
                  />
                  <ErrorMessage
                    name="residentialAddress.addressLine1"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>

                {/* Address Line 2 */}
                <div className="mb-3">
                  <Field
                    type="text"
                    name="residentialAddress.addressLine2"
                    placeholder="Address Line 2"
                    className={`form-control ${errors.residentialAddress?.addressLine2 &&
                        touched.residentialAddress?.addressLine2
                        ? "is-invalid"
                        : ""
                      }`}
                    disabled={isSubmitting}
                  />
                  <ErrorMessage
                    name="residentialAddress.addressLine2"
                    component="div"
                    className="invalid-feedback"
                  />
                </div>

                {/* City, State, and PIN Code */}
                <div className="row g-3">
                  {/* City */}
                  <div className="col-12 col-md-4">
                    <Field
                      type="text"
                      name="residentialAddress.city"
                      placeholder="City"
                      className={`form-control ${errors.residentialAddress?.city &&
                          touched.residentialAddress?.city
                          ? "is-invalid"
                          : ""
                        }`}
                      disabled={isSubmitting}
                    />
                    <ErrorMessage
                      name="residentialAddress.city"
                      component="div"
                      className="invalid-feedback"
                    />
                  </div>

                  {/* State */}
                  <div className="col-12 col-md-4">
                    <Field
                      as="select"
                      name="residentialAddress.state"
                      className={`form-select ${errors.residentialAddress?.state &&
                          touched.residentialAddress?.state
                          ? "is-invalid"
                          : ""
                        }`}
                      disabled={isSubmitting}
                    >
                      <option value="">Select State</option>
                      {getStates().map((state) => (
                        <option key={state} value={state}>
                          {state}
                        </option>
                      ))}
                    </Field>
                    <ErrorMessage
                      name="residentialAddress.state"
                      component="div"
                      className="invalid-feedback"
                    />
                  </div>

                  {/* PIN Code */}
                  <div className="col-12 col-md-4">
                    <Field
                      type="text"
                      name="residentialAddress.pincode"
                      placeholder="PIN Code"
                      maxLength="6"
                      className={`form-control ${errors.residentialAddress?.pincode &&
                          touched.residentialAddress?.pincode
                          ? "is-invalid"
                          : ""
                        }`}
                      disabled={isSubmitting}
                    />
                    <ErrorMessage
                      name="residentialAddress.pincode"
                      component="div"
                      className="invalid-feedback"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Navigation buttons */}
            <div className="d-flex justify-content-between mt-5">
              {/* Cancel button */}
              <button
                type="button"
                className="btn btn-light"
                onClick={() => window.history.back()} // or useNavigate("/employees")
              >
                Cancel
              </button>

              <div>
                {/* Previous button (only if not first step) */}
                {typeof onPrev === "function" && (
                  <button
                    style={{ marginRight: 8 }}
                    onClick={onPrev}
                    type="button"
                    className="btn btn-light"
                  >
                    Previous
                  </button>
                )}

                {/* Save & Continue OR Submit */}
                {typeof onNext === "function" ? (
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={isSubmitting || signingIn}
                  >
                    {signingIn ? (
                      <span className="spinner-border spinner-border-sm me-2"></span>
                    ) : null}
                    Save and Continue
                  </button>
                ) : (
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={isSubmitting || signingIn}
                  >
                    {signingIn ? (
                      <span className="spinner-border spinner-border-sm me-2"></span>
                    ) : null}
                    Submit
                  </button>
                )}
              </div>
            </div>
          </Form>
        )}
      </Formik>

      {signingIn && <Loader />}
    </>
  );
};

export default PersonalDetails;