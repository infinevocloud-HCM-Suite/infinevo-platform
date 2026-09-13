import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate, useLocation } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function EditUser() {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  
  const [signingIn, setSigningIn] = useState(false);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(false);
  
  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  const [initialValues, setInitialValues] = useState({
    userId: "",
    name: "",
    email: "",
    mobile: "",
    roleId: "",
    invitationType: "email",
    isSuperAdmin: false,
    userRole: "",
    status: "active"
  });

  // Get user from state or fetch by ID
  useEffect(() => {
    if (location.state && location.state.user) {
      const user = location.state.user;
      setInitialValues({
        userId: user.userId,
        name: user.username,
        email: user.email,
        mobile: user.mobile || "",
        roleId: "",
        invitationType: user.invitationType || "email",
        isSuperAdmin: user.isSuperAdmin || false,
        userRole: user.role,
        status: user.status?.toLowerCase() || "active"
      });
    } else {
      errorMsg("Error", "User data not found", false);
      navigate("/users");
    }
    
    fetchRoles();
  }, [location, navigate]);

  const fetchRoles = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/roles`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });
      
      if (response.data && response.data.data) {
        setRoles(response.data.data);
      }
    } catch (error) {
      console.error("API Error:", error);
      errorMsg("Error", "Failed to load roles", false);
    } finally {
      setLoading(false);
    }
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    name: Yup.string()
      .required("Name is required")
      .min(2, "Name must be at least 2 characters")
      .max(100, "Name cannot exceed 100 characters"),

    email: Yup.string()
      .required("Email is required")
      .email("Invalid email format")
      .max(150, "Email cannot exceed 150 characters"),

    mobile: Yup.string()
      .max(15, "Mobile number cannot exceed 15 characters"),

    roleId: Yup.string()
      .required("Role is required"),

    userRole: Yup.string()
      .required("User role is required")
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);
    
    try {
      const selectedRole = roles.find(role => role.roleId === values.roleId);
      const userRole = selectedRole ? selectedRole.roleName : values.userRole;

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/invitations/${values.userId}`,
        {
          name: values.name,
          email: values.email,
          mobile: values.mobile,
          roleId: values.roleId,
          invitationType: values.invitationType,
          isSuperAdmin: values.isSuperAdmin,
          userRole: userRole,
          status: values.status
        },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data.status === 200) {
        successMsg("Success", "User updated successfully");
        navigate("/users");
      } else {
        errorMsg(
          "Update Failed",
          `There was an error updating the user. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      console.error("API Error:", e);
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Update Failed", e.response.data.message || "Failed to update user", false);
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
        <title>HRMS InfiNevoCloud - Edit User</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit User</h5>
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
                {({ isSubmitting, errors, touched, setFieldValue }) => (
                  <Form className="form w-100">
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="name"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Name <RequiredStar />
                      </label>

                      <Field
                        type="text"
                        name="name"
                        id="name"
                        style={{ maxWidth: "500px" }}
                        className={`form-control form-control-lg form-control-solid ${errors.name && touched.name ? "is-invalid" : ""
                          }`}
                        placeholder="Enter name"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="name"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="email"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Email <RequiredStar />
                      </label>

                      <Field
                        type="email"
                        name="email"
                        id="email"
                        style={{ maxWidth: "500px" }}
                        className={`form-control form-control-lg form-control-solid ${errors.email && touched.email ? "is-invalid" : ""
                          }`}
                        placeholder="Enter email"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="email"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="mobile"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Mobile Number
                      </label>

                      <Field
                        type="text"
                        name="mobile"
                        id="mobile"
                        style={{ maxWidth: "500px" }}
                        className={`form-control form-control-lg form-control-solid ${errors.mobile && touched.mobile ? "is-invalid" : ""
                          }`}
                        placeholder="Enter mobile number"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="mobile"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="roleId"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Role <RequiredStar />
                      </label>

                      <Field
                        as="select"
                        name="roleId"
                        id="roleId"
                        style={{ maxWidth: "500px" }}
                        className={`form-select form-select-lg form-select-solid ${errors.roleId && touched.roleId ? "is-invalid" : ""
                          }`}
                        disabled={isSubmitting || loading}
                        onChange={(e) => {
                          const roleId = e.target.value;
                          setFieldValue("roleId", roleId);
                          const selectedRole = roles.find(role => role.roleId === roleId);
                          if (selectedRole) {
                            setFieldValue("userRole", selectedRole.roleName);
                          }
                        }}
                      >
                        <option value="" disabled>Select a role</option>
                        {roles.map((role) => (
                          <option key={role.roleId} value={role.roleId}>
                            {role.roleName}
                          </option>
                        ))}
                      </Field>
                      <ErrorMessage
                        name="roleId"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="invitationType"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Invitation Type
                      </label>

                      <Field
                        as="select"
                        name="invitationType"
                        id="invitationType"
                        style={{ maxWidth: "500px" }}
                        className="form-select form-select-lg form-select-solid"
                        disabled={isSubmitting}
                      >
                        <option value="email">Email</option>
                        <option value="sms">SMS</option>
                        <option value="both">Both</option>
                      </Field>
                    </div>

                    <div className="fv-row mb-10">
                      <label className="form-check form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="isSuperAdmin"
                          className="form-check-input"
                          disabled={isSubmitting}
                        />
                        <span className="form-check-label ms-2">
                          Is Super Admin
                        </span>
                      </label>
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="status"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Status
                      </label>

                      <Field
                        as="select"
                        name="status"
                        id="status"
                        style={{ maxWidth: "500px" }}
                        className="form-select form-select-lg form-select-solid"
                        disabled={isSubmitting}
                      >
                        <option value="active">Active</option>
                        <option value="inactive">Inactive</option>
                      </Field>
                    </div>

                    <Field type="hidden" name="userRole" />
                    <Field type="hidden" name="userId" />

                    <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting || loading}
                      >
                        {isSubmitting ? "Updating..." : "Update User"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/users")}
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
      {(signingIn || loading) && <Loader />}
    </>
  );
}