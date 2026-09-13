import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { updateToken } from "../../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";

export default function Preferences() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
const initialValues = {
  enablePortalAccess: true,
  salaryInformationAccess: true,
  fbpDeclarationAccess: true,
  investmentProofAccess: false,
  allowSalarySlipDownload: true,
  allowForm16Download: false,
  bannerMessage: "",
  messageExpiryDate: "",
  showDocumentsInPortal: false,
};


  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("preferences");

  const handleTabClick = (tab, path) => {
    setActiveTab(tab);
    navigate(path);
  };

const validationSchema = Yup.object().shape({
  bannerMessage: Yup.string()
    .max(250, "Max 250 characters allowed")
    .nullable(),

  messageExpiryDate: Yup.date()
    .min(new Date(), "Expiry date cannot be in the past")
    .required("Expiry date is required")
    .nullable(),
});




  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    
    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/employee-portal/preferences`,
        values,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
          },
        }
      );

      if (response.data.success) {
        console.log("Preferences saved successfully");
      } else {
        errorMsg(
          "Save Failed",
          `There was an error saving preferences. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
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
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Employee Portal Preferences</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex flex-column border-bottom">
        {/* Header */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="mb-0 fw-semibold">Employee Portal</h5>
        </div>

        {/* Tabs */}
        <ul className="nav nav-tabs border-0">
          <li className="nav-item">
            <button
              className={`nav-link fw-semibold px-3 py-2 ${
                activeTab === "preferences"
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => handleTabClick("preferences", "/employee-portal/preferences")}
            >
              Preferences
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link fw-semibold px-3 py-2 ${
                activeTab === "webTabs"
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => handleTabClick("webTabs", "/employee-portal/web-tabs")}
            >
              Web Tabs
            </button>
          </li>
        </ul>
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
                {({ isSubmitting, errors, touched, values }) => (
                  <Form className="form w-100">
                    {/* Enable Portal Access */}
                 <div className="d-flex justify-content-between align-items-center mb-3">
  <div>
    <div className="fs-6 fw-bolder text-gray-800">
      Enable Portal Access
    </div>
    <div className="text-muted fs-7">
      The employee portal allows your employees to access their salary information 
      and perform payroll related activities like declaring their Flexible Benefit 
      Plan (FBP) and submitting investment proofs for approval.
    </div>
  </div>
  <div className="form-check form-switch">
    <Field
      type="checkbox"
      name="enablePortalAccess"
      className="form-check-input"
      role="switch"
      id="enablePortalAccess"
    />
  </div>
</div>

<hr 
  style={{
    border: "none",
    borderTop: "2px dotted gray",
    width: "100%"
  }}
/>


{/* Banner Message Section */}
{/* Banner Message Section */}
<div className="fv-row mb-10">
  <label className="form-label fs-6 fw-bold text-dark">
    Banner Message
  </label>
  
  <div className="text-muted mb-3" style={{ fontSize: "0.925rem" }}>
    Send important notifications or announcements to your employees using this space. 
    The message you enter here will be displayed at the top of the Home page in the 
    Employee Self-service Portal. <a href="#" className="text-primary">View Sample Preview</a>
  </div>
</div>


<div className="fv-row mb-10">
  <label
    htmlFor="bannerMessage"
    className="form-label fs-6 fw-bold text-dark"
  >
    Enter Banner Message
    
  </label>



  <Field
    as="textarea"
    name="bannerMessage"
    id="bannerMessage"
    className={`form-control form-control-lg form-control-solid ${
      errors.bannerMessage && touched.bannerMessage ? "is-invalid" : ""
    }`}
    placeholder="Enter your banner message"
    rows={4}            // adjust height
    disabled={isSubmitting}
  />
  <ErrorMessage
    name="bannerMessage"
    component="div"
    className="invalid-feedback"
  />
</div>

<div className="fv-row mb-10">
  <label
    htmlFor="messageExpiryDate"
    className="form-label fs-6 fw-bold text-dark"
  >
    Select till when this message must be displayed in the portal
   
  </label>

  <Field
    type="date"
    name="messageExpiryDate"
    id="messageExpiryDate"
    className={`form-control form-control-lg form-control-solid ${
      errors.messageExpiryDate && touched.messageExpiryDate
        ? "is-invalid"
        : ""
    }`}
    min={new Date().toISOString().split('T')[0]}
    disabled={isSubmitting}
  />
  <ErrorMessage
    name="messageExpiryDate"
    component="div"
    className="invalid-feedback"
  />
</div>


<div className="fv-row mb-10">
  <label className="form-label fs-6 fw-bold text-dark">
    Portal Contact Information
  </label>

  <div className="text-muted mb-3" style={{ fontSize: "0.925rem" }}>
    This is the email address to which your employees can send queries through the portal.
  </div>

  <div className="card">
    <div className="card-body">
      <div className="d-flex justify-content-between align-items-center">
        <div className="d-flex align-items-center">
          <div className="symbol symbol-40px me-3">
            <span className="symbol-label bg-light-primary">
              <i className="bi bi-envelope-fill text-primary fs-4"></i>
            </span>
          </div>
          <div>
            <div className="fw-semibold fs-6 text-gray-800">
              0428pk15
            </div>
            <div className="text-muted fs-7">
              0428pk15@gmail.com
            </div>
          </div>
        </div>
        <a href="#" className="btn btn-sm btn-light-primary">
          Manage Contacts
        </a>
      </div>
    </div>
  </div>
</div>


<div className="fv-row mb-10">
  <label className="form-label fs-6 fw-bold text-dark">
    Document Management
  </label>
  
  <div className="form-check form-check-custom form-check-solid mt-3">
    <Field
      type="checkbox"
      name="showDocumentsInPortal"
      id="showDocumentsInPortal"
      className="form-check-input"
    />
    <label className="form-check-label fs-6 fw-semibold text-dark" htmlFor="showDocumentsInPortal">
      Show documents in employee portal
    </label>
  </div>
  
  <div className="text-muted mt-2" style={{ fontSize: "0.925rem" }}>
    Enable this option to make documents visible for employees to access in the employee portal.
  </div>
</div>





                                  <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Saving..." : "Save"}
                      </button>
                      {/* <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/users")}
                      >
                        Cancel
                      </button> */}
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