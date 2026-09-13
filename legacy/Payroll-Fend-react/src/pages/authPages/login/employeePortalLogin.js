import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from 'lodash';
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import { useNavigate } from "react-router-dom";


import Loader from "../../../shared/components/loaders/fullPageLoader";
import loginBack from '../../../assets/images/login-back.png';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from '../../../assets/images/infine-logo.png';
// import qs from 'qs';
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../shared/helpers/msgHelper";

export default function EmployeePortalLogin() {

    const navigate = useNavigate();

    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [initialValues, setInitialValues] = useState({
        username: '',
        password: ''
    });


    const validationSchema = Yup.object().shape({
        username: Yup.string()
            .required('Email or mobile number is required')
            .test('email-or-phone', 'Enter a valid email or mobile number', function (value) {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                const phoneRegex = /^[6-9]\d{9}$/;
                return emailRegex.test(value) || phoneRegex.test(value);
            }),
        password: Yup.string().required('Password is required')
    });



  const handleSubmit = async (values, { setSubmitting }) => {
    if (!_.isEmpty(values.username) && !_.isEmpty(values.password)) {
      setSigningIn(true);
      setSubmitting(true);

      const params = new URLSearchParams();
      params.append("client_id", GlobalConst.CLIENT_ID);
      params.append("grant_type", "password");
      params.append("username", values.username);
      params.append("password", values.password);

      try {
        const response = await axios.post(
          `${GlobalConst.AUTH_URL}/realms/${GlobalConst.REALM}/protocol/openid-connect/token`,
          params,
          { headers: { "Content-Type": "application/x-www-form-urlencoded" } }

        );

        console.log("Login Response:", response);
        // return;

        if (!_.isEmpty(response?.data?.access_token)) {
          const accessToken = response.data.access_token;

          // NOTE: Do NOT persist the token yet. We first verify this account has
          // employee-portal access, otherwise an admin-only credential would be
          // stored/dispatched before we can reject it.
          // my-organizations returns only orgs where employee portal is enabled.
          const orgRes = await axios.get(
            `${GlobalConst.API_URL}/api/organization-user-role-mapping/my-organizations`,
            {
              headers: { Authorization: `Bearer ${accessToken}` },
            }
          );

          const orgIds = Array.isArray(orgRes?.data?.data)
            ? orgRes.data.data
            : [];

          // ---------- EMPLOYEE PORTAL ACCESS GUARD (frontend mitigation) ----------
          // An admin-only account has no organization with employeePortalEnable=true,
          // so my-organizations comes back empty => block employee portal access.
          if (orgIds.length === 0) {
            errorMsg(
              "Access Denied",
              "This account does not have Employee Portal access. Please use the Admin login to sign in.",
              false
            );
            return;
          }
          // ---------- END EMPLOYEE PORTAL ACCESS GUARD ----------

          // Access verified -> now it is safe to persist the session.
          localStorage.setItem("__t", accessToken);
          if (response.data.refresh_token) {
            localStorage.setItem("__r", response.data.refresh_token);
          }
          // Mark this session as an employee-portal session for the route guards.
          localStorage.setItem("userType", "employee");
          // Store the first organizationId in localStorage.
          localStorage.setItem("organizationId", orgIds[0]);

          dispatch(updateToken(accessToken));

          // Navigate to home page
          navigate("/home");
            } else {
                errorMsg(
                    "Login Failed",
                    `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
                    true
                );
            }

      } catch (e) {
        if (!_.isEmpty(e?.response?.data)) {
          errorMsg("Login Failed", e.response.data.error, false);
        } else {
          errorMsg(e.code || "Error", e.message || "Something went wrong", true);
        }
      } finally {
        setSigningIn(false);
        setSubmitting(false);
      }
    }
  };

  


    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Login</title>
            </Helmet>
            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-auto bg-primary w-xl-600px positon-xl-relative">
                    <div className="d-flex flex-column position-xl-fixed top-0 bottom-0 w-xl-600px scroll-y">
                        <div className="d-flex flex-row-fluid flex-column text-center p-5 p-lg-10 pt-lg-20">
                            <a href="/" className="py-2 py-lg-20">
                                <img alt="Logo" src={logo} className="h-40px h-lg-20px" />
                                {/* <h2>INFINEVOCLOUD</h2> */}
                            </a>
                            <div className="py-2 py-lg-10"></div>
                            <h1 className="d-none d-lg-block fw-bold text-white fs-2qx pb-5 pb-md-10">Welcome to <br />HRMS INFINEVOCLOUD</h1>
                            <p className="d-none d-lg-block fw-semibold fs-2 text-white">
                                HRMS Platform to
                                manage the leaves and other HR related tasks
                            </p>
                        </div>
                        <div className="d-none d-lg-block d-flex flex-row-auto bgi-no-repeat bgi-position-x-center bgi-size-contain bgi-position-y-bottom min-h-100px min-h-lg-350px"
                            style={{ "backgroundImage": `url(${loginBack})` }}></div>
                    </div>
                </div>

                <div className="d-flex flex-column flex-lg-row-fluid py-10">
                    <div className="d-flex flex-center flex-column flex-column-fluid">
                        <div className="w-lg-500px p-10 p-lg-15 mx-auto">
                            <Formik
                                initialValues={initialValues}
                                validationSchema={validationSchema}
                                onSubmit={handleSubmit}
                                enableReinitialize
                            >
                                {({ isSubmitting, errors, touched }) => (
                                    <Form className="form w-100">
                                        <div className="text-center mb-10">
                                            <h1 className="text-dark mb-3">Sign In to access InfinevoCloud Payroll</h1>
                                            <div className="text-gray-400 fw-semibold fs-4">New Here?
                                                <a href="/create-new-account"
                                                    className="link-primary fw-bold ms-1">Create an Account</a>
                                            </div>
                                        </div>

                                        <div className="fv-row mb-10">
                                            {/* <label htmlFor="username" className="form-label fs-6 fw-bold text-dark">Email</label>
                                            <Field
                                                type="email"
                                                name="username"
                                                className={`form-control form-control-lg form-control-solid ${errors.username && touched.username ? 'is-invalid' : ''}`}
                                                placeholder="Enter username"
                                                disabled={isSubmitting}
                                            />
                                            <ErrorMessage name="username" component="div" className="invalid-feedback" /> */}



                                            <label htmlFor="username" className="form-label fs-6 fw-bold text-dark">
                                                Email or Mobile Number
                                            </label>
                                            <Field
                                                type="text"
                                                name="username"
                                                className={`form-control form-control-lg form-control-solid ${errors.username && touched.username ? 'is-invalid' : ''
                                                    }`}
                                                placeholder="Enter email or mobile number"
                                                disabled={isSubmitting}
                                            />
                                            <ErrorMessage name="username" component="div" className="invalid-feedback" />

                                        </div>
                                        <div className="fv-row mb-10">
                                            <div className="d-flex flex-stack mb-2">
                                                <label htmlFor="password" className="form-label fw-bold text-dark fs-6 mb-0">Password</label>
                                                <a href="/forgot-password"
                                                    className="link-primary fs-6 fw-bold">Forgot Password ?</a>
                                            </div>
                                            <Field
                                                type="password"
                                                name="password"
                                                className={`form-control form-control-lg form-control-solid ${errors.password && touched.password ? 'is-invalid' : ''}`}
                                                placeholder="Enter password"
                                                disabled={isSubmitting}
                                            />
                                            <ErrorMessage name="password" component="div" className="invalid-feedback" />
                                        </div>
                                        <div className="text-center">
                                            <button type="submit" className="btn btn-lg btn-primary w-100 mb-5" disabled={isSubmitting} data-kt-indicator={(isSubmitting) ? "on" : "off"}>
                                                <span className="indicator-label">Continue</span>
                                                <span className="indicator-progress">Please wait...
                                                    <span className="spinner-border spinner-border-sm align-middle ms-2"></span></span>
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>

                    <div className="d-flex flex-center flex-wrap fs-6 p-5 pb-0">
                        <div className="d-flex flex-center fw-semibold fs-6">
                            <a href="http://infinevocloud.com/about.php" className="text-muted text-hover-primary px-2"
                                target="_blank">About</a>
                            <a href="http://infinevocloud.com/contact.php" className="text-muted text-hover-primary px-2"
                                target="_blank">Support</a>
                        </div>
                    </div>
                </div>
            </div>
            {signingIn && <Loader />}
        </>
    );
}