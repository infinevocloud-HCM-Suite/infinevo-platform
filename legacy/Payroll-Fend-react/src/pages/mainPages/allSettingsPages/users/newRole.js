import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate } from "react-router-dom";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function CreateNewRole() {
  const navigate = useNavigate();
  const dispatch = useDispatch();

  const [signingIn, setSigningIn] = useState(false);
  
  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  const [initialValues] = useState({
    roleName: "",
    roleDescription: "",
    accessType: "full", // Default value
    userActionRequired: false,
    isDefault: false
  });

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    roleName: Yup.string()
      .required("Role Name is required")
      .min(2, "Role Name must be at least 2 characters")
      .max(100, "Role Name cannot exceed 100 characters"),

    roleDescription: Yup.string()
      .required("Description is required")
      .min(5, "Description must be at least 5 characters")
      .max(250, "Description cannot exceed 250 characters"),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);
    
    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/roles`,
        {
          roleName: values.roleName,
          roleDescription: values.roleDescription,
          accessType: values.accessType,
          userActionRequired: values.userActionRequired,
          isDefault: values.isDefault
        },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data.status === 201) {
        successMsg("Success", "Role created successfully");
        navigate("/roles");
      } else {
        errorMsg(
          "Creation Failed",
          `There was an error creating the role. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      console.error("API Error:", e);
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Creation Failed", e.response.data.message || "Failed to create role", false);
      } else if (e.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Add Role</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Add Role</h5>
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
                {({ isSubmitting, errors, touched }) => (
                  <Form className="form w-100">
                    {/* Role Name */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="roleName"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Role Name <RequiredStar />
                      </label>

                      <Field
                        type="text"
                        name="roleName"
                        id="roleName"
                        style={{ maxWidth: "500px" }}
                        className={`form-control form-control-lg form-control-solid ${
                          errors.roleName && touched.roleName ? "is-invalid" : ""
                        }`}
                        placeholder="Enter role name"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="roleName"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {/* Description */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="roleDescription"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Description <RequiredStar />
                      </label>

                      <Field
                        as="textarea"
                        name="roleDescription"
                        id="roleDescription"
                        style={{ maxWidth: "500px", minHeight: "100px" }}
                        className={`form-control form-control-lg form-control-solid ${
                          errors.roleDescription && touched.roleDescription
                            ? "is-invalid"
                            : ""
                        }`}
                        placeholder="Enter description"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="roleDescription"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {/* Access Type */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="accessType"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Access Type
                      </label>

                      <Field
                        as="select"
                        name="accessType"
                        id="accessType"
                        style={{ maxWidth: "500px" }}
                        className="form-control form-control-lg form-control-solid"
                        disabled={isSubmitting}
                      >
                        <option value="full">Full Access</option>
                        <option value="limited">Limited Access</option>
                        <option value="restricted">Restricted Access</option>
                      </Field>
                    </div>

                    {/* User Action Required */}
                    <div className="fv-row mb-10">
                      <label className="form-check form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="userActionRequired"
                          className="form-check-input"
                          disabled={isSubmitting}
                        />
                        <span className="form-check-label ms-2">
                          User Action Required
                        </span>
                      </label>
                    </div>

                    {/* Is Default */}
                    <div className="fv-row mb-10">
                      <label className="form-check form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="isDefault"
                          className="form-check-input"
                          disabled={isSubmitting}
                        />
                        <span className="form-check-label ms-2">
                          Set as Default Role
                        </span>
                      </label>
                    </div>

                    {/* Save & Cancel */}
                    <div
                      className="d-flex align-items-center mt-5"
                      style={{ gap: "10px" }}
                    >
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Creating..." : "Create Role"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/roles")}
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