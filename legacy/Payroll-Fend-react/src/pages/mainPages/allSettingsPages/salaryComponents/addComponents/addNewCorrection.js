//empty setting format

import React, { useState } from "react";
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
import { errorMsg } from "../../../../../shared/helpers/msgHelper";
//import { dateFormats } from "../../../shared/appConfig/dateFormat";
import { format } from "date-fns";


export default function AddNewCorrection() {
  const navigate = useNavigate();

  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
    const [initialValues, setInitialValues] = useState({
  organizationName: "",
  firstName: "",
  industry: "",
  dateFormat: "",
  addressLine1: "",
  addressLine2: "",
  state: "",
  city: "",
  pinCode: "",
  logo: null,
  updateAllTransactions: false,
  filingAddress: "",
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

  //     const keycloak = new Keycloak({

  //   url: 'https://your-keycloak-domain/auth',

  //   realm: 'payroll-dev',

  //   clientId: 'your-client-id'

  // });

 const validationSchema = Yup.object().shape({
   organizationName: Yup.string()
     .required("Organisation name is required")
     .min(2, "Organisation name must be at least 2 characters")
     .max(100, "Organisation name cannot exceed 100 characters"),
 
   firstName: Yup.string()
     .required("Business location is required")
     .min(2, "Business location must be at least 2 characters"),
 
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
     .test("valid-pin", "PIN code must be a 6-digit number", function (value) {
       return /^\d{6}$/.test(value);
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

  //     const onSuccess = response => console.log(response);
  // const onFailure = response => console.error(response);

  const handleSubmit = async (values, { setSubmitting }) => {
  if (!_.isEmpty(values.organizationName) && !_.isEmpty(values.addressLine1)) {
    setSubmitting(true);

    // Build FormData for file upload
    const postData = new FormData();
    postData.append("organizationName", values.organizationName);
    postData.append("firstName", values.firstName);
    postData.append("industry", values.industry);
    postData.append("dateFormat", values.dateFormat);
    postData.append("addressLine1", values.addressLine1);
    postData.append("addressLine2", values.addressLine2);
    postData.append("state", values.state);
    postData.append("city", values.city);
    postData.append("pinCode", values.pinCode);
    postData.append("updateAllTransactions", values.updateAllTransactions);
    postData.append("filingAddress", values.filingAddress);
    if (values.logo) {
      postData.append("logo", values.logo);
    }

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/organisation-profile/save`,
        postData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
          },
        }
      );

      if (
        !_.isEmpty(response) &&
        !_.isEmpty(response.data) &&
        response.data.message === "Profile saved successfully"
      ) {
        // Optionally show success message or redirect
        console.log("Organisation profile saved.");
      } else {
        errorMsg(
          "Save Failed",
          `There was an error saving the profile. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Save Failed", e.response.data.err_msg, false);
      } else {
        errorMsg(e.code, e.message, true);
      }
    } finally {
     
      setSubmitting(false);
    }
  }
};

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Login</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">New Correction Component</h5>
        {/* <span className="text-muted">
          Organisation ID: <span className="text-primary">60044614590</span>
        </span> */}

      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          {/* <div className="d-flex flex-center flex-column flex-column-fluid"> */}
          {/* <div className="d-flex flex-column flex-column-fluid align-items-start"> */}
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "600px" }}
            >
              {/* Start of card content */}

              {/* Optional title/header */}

              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched }) => (
                  <Form className="form w-100">

<div className="fv-row mb-10">
  <label
    htmlFor="createCorrectionFor"
    className="form-label fs-6 fw-bold text-dark"
  >
    Create Correction for <RequiredStar />
  </label>

  <Field
    as="select"
    name="createCorrectionFor"
    id="createCorrectionFor"
    style={{ maxWidth: "500px" }}
    className={`form-select form-select-lg form-select-solid ${
      errors.createCorrectionFor && touched.createCorrectionFor ? "is-invalid" : ""
    }`}
    aria-label="Select correction type"
    disabled={isSubmitting}
  >
    <option value="" disabled>
      Select
    </option>
    <option value="1">Admin</option>
    <option value="2">Manager</option>
    <option value="3">Reimbursements and POI Reviewer</option>
  </Field>

  <ErrorMessage
    name="createCorrectionFor"
    component="div"
    className="invalid-feedback"
  />
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
                        onClick={() => navigate("/salary-components")}
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
