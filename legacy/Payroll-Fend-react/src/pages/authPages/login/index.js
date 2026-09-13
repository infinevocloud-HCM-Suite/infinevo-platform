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
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';
import {
    normalizeOrganizationsResponse,
    resolveAdminLandingPath,
} from "../../../shared/helpers/resolveAdminLandingPath";


export default function Login() {

    const navigate = useNavigate();


    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [initialValues, setInitialValues] = useState({
        username: '',
        password: ''
    });

    const [showPassword, setShowPassword] = useState(false);



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




    // const handleSubmit = async (values, { setSubmitting }) => {
    //     if (!_.isEmpty(values.username) && !_.isEmpty(values.password)) {
    //         setSigningIn(true);
    //         setSubmitting(true);
    //         const postData = {
    //             email: values.username,
    //             password: values.password
    //         };
    //         try {
    //             const response = await axios.post(`${GlobalConst.API_URL}/auth/login`, postData);

    //             if (!_.isEmpty(response) && !_.isEmpty(response.data) && response.data.message == 'Successfully logged in') {
    //                 if (response.data.token) {
    //                     await localStorage.setItem("__t", response.data.token);
    //                 }
    //                 else {
    //                     errorMsg("Login Failed", `There was an error while we where trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`, true);
    //                 }

    //                 let url = await localStorage.getItem('redirect_url');
    //                 if (!_.isEmpty(url)) {
    //                     localStorage.removeItem('redirect_url');
    //                     window.location.href = url;
    //                     return;
    //                 }

    //                 dispatch(updateToken(response.data.token));
    //             }
    //             else {
    //                 errorMsg("Login Failed", `There was an error while we where trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`, true);
    //             }
    //         } catch (e) {
    //             if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
    //                 errorMsg("Login Failed", e.response.data.err_msg, false);
    //             }
    //             else {
    //                 // console.log("Exception: ", e);
    //                 errorMsg(e.code, e.message, true);
    //             }
    //         } finally {
    //             setSigningIn(false);
    //             setSubmitting(false);
    //         }
    //     }
    // }



    //  const handleSubmit = async (values, { setSubmitting }) => {
    //     if (!_.isEmpty(values.username) && !_.isEmpty(values.password)) {
    //         setSigningIn(true);
    //         setSubmitting(true);

    //         const params = new URLSearchParams();
    //         params.append("client_id", GlobalConst.CLIENT_ID);
    //         params.append("grant_type", "password");
    //         params.append("username", values.username);
    //         params.append("password", values.password);


    //         try {
    //             const response = await axios.post(`${GlobalConst.AUTH_URL}/realms/HRMS/protocol/openid-connect/token`, params, {
    //                 headers: { "Content-Type": "application/x-www-form-urlencoded" },
    //             });

    //             // console.log("response", response.data);
    //             if (!_.isEmpty(response) && !_.isEmpty(response.data)) {
    //                 if (response.data.access_token) {
    //                     await localStorage.setItem("__t", response.data.access_token);
    //                     // await localStorage.setItem("organizationId", response.data.organizationId);   // set organizationId in localstorage

    //                     if (response.data.refresh_token) {
    //                         await localStorage.setItem("__r", response.data.refresh_token);
    //                     }
    //                     // dispatch(updateToken(op.data.access_token));
    //                 }
    //                 else {
    //                     errorMsg("Login Failed", `There was an error while we where trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`, true);
    //                 }

    //                 let url = await localStorage.getItem('redirect_url');
    //                 if (!_.isEmpty(url)) {
    //                     localStorage.removeItem('redirect_url');
    //                     window.location.href = url;
    //                     return;
    //                 }

    //                 dispatch(updateToken(response.data.access_token));
    //             }
    //             else {
    //                 errorMsg("Login Failed", `There was an error while we where trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`, true);
    //             }
    //         } catch (e) {
    //             if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
    //                 errorMsg("Login Failed", e.response.data.error, false);
    //             }
    //             else {
    //                 // console.log("Exception: ", e);
    //                 errorMsg(e.code, e.message, true);
    //             }
    //         } finally {
    //             setSigningIn(false);
    //             setSubmitting(false);
    //         }
    //     }
    // }



    // const handleSubmit = async (values, { setSubmitting }) => {
    //     if (!_.isEmpty(values.username) && !_.isEmpty(values.password)) {
    //         setSigningIn(true);
    //         setSubmitting(true);

    //         const params = new URLSearchParams();
    //         params.append("client_id", GlobalConst.CLIENT_ID);
    //         params.append("grant_type", "password");
    //         params.append("username", values.username);
    //         params.append("password", values.password);

    //         try {
    //             const response = await axios.post(
    //                 `${GlobalConst.AUTH_URL}/realms/HRMS/protocol/openid-connect/token`,
    //                 params,
    //                 { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
    //             );

    //             if (!_.isEmpty(response) && !_.isEmpty(response.data)) {
    //                 if (response.data.access_token) {
    //                     await localStorage.setItem("__t", response.data.access_token);
    //                     if (response.data.refresh_token) {
    //                         await localStorage.setItem("__r", response.data.refresh_token);
    //                     }

    //                     dispatch(updateToken(response.data.access_token));

    //                     // 🔹 After storing token, fetch organizations
    //                     const orgRes = await axios.get(`${GlobalConst.API_URL}/api/organizations`, {
    //                         headers: { Authorization: `Bearer ${response.data.access_token}` },
    //                     });

    //                     const organizations = orgRes.data?.data || [];

    //                     // ---- NEW REDIRECTION LOGIC ----
    //                     // 1) if no organizations -> create new organization
    //                     if (organizations.length < 1) {
    //                         window.location.href = "/create-new-organization";
    //                         return;
    //                     }

    //                     // 2) if exactly 1 organization -> go to setup for that organization
    //                     if (organizations.length === 1) {
    //                         window.location.href = `/setup-new-organization/${organizations[0].organizationId}`;
    //                         return;
    //                     }

    //                     // 3) if more than 1 organization -> check for any inactive org, else onboarding dashboard
    //                     // const inactiveOrg = organizations.find(org => org.isOrgActive === false);

    //                     // if (inactiveOrg) {
    //                     //     window.location.href = `/setup-new-organization/${inactiveOrg.organizationId}`;
    //                     //     return;
    //                     // }

    //                     // else go to dashboard
    //                     window.location.href = "/onboarding-dashboard";
    //                     return;
    //                     // ---- END NEW REDIRECTION LOGIC ----

    //                 } else {
    //                     errorMsg(
    //                         "Login Failed",
    //                         `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
    //                         true
    //                     );
    //                 }
    //             } else {
    //                 errorMsg(
    //                     "Login Failed",
    //                     `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
    //                     true
    //                 );
    //             }
    //         } catch (e) {
    //             if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
    //                 errorMsg("Login Failed", e.response.data.error, false);
    //             } else {
    //                 errorMsg(e.code, e.message, true);
    //             }
    //         } finally {
    //             setSigningIn(false);
    //             setSubmitting(false);
    //         }
    //     }
    // };



    // const handleSubmit = async (values, { setSubmitting }) => {
    //     if (!_.isEmpty(values.username) && !_.isEmpty(values.password)) {
    //         setSigningIn(true);
    //         setSubmitting(true);

    //         const params = new URLSearchParams();
    //         params.append("client_id", GlobalConst.CLIENT_ID);
    //         params.append("grant_type", "password");
    //         params.append("username", values.username);
    //         params.append("password", values.password);

    //         try {
    //             const response = await axios.post(
    //                 `${GlobalConst.AUTH_URL}/realms/HRMS/protocol/openid-connect/token`,
    //                 params,
    //                 { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
    //             );

    //             if (!_.isEmpty(response) && !_.isEmpty(response.data)) {
    //                 if (response.data.access_token) {
    //                     await localStorage.setItem("__t", response.data.access_token);
    //                     if (response.data.refresh_token) {
    //                         await localStorage.setItem("__r", response.data.refresh_token);
    //                     }

    //                     dispatch(updateToken(response.data.access_token));

    //                     // 🔹 After storing token, fetch organizations
    //                     const orgRes = await axios.get(`${GlobalConst.API_URL}/api/organizations`, {
    //                         headers: { Authorization: `Bearer ${response.data.access_token}` },
    //                     });

    //                     const organizations = orgRes.data?.data || [];
    //                     console.log("Fetched organizations:", organizations);

    //                     // ---- REDIRECTION LOGIC ----
    //                     if (organizations.length < 1) {
    //                         console.log("Redirecting to /create-new-organization");
    //                         window.location.href = "/create-new-organization";
    //                         return;
    //                     }

    //                     if (organizations.length === 1) {
    //                         const org = organizations[0];
    //                         console.log("Single organization:", org);

    //                         if (org?.organizationId) {
    //                             console.log("Redirecting to setup:", `/setup-new-organization/${org.organizationId}`);
    //                             window.location.href = `/setup-new-organization/${org.organizationId}`;
    //                             return;
    //                         } else {
    //                             console.warn("organizationId missing, redirecting to dashboard");
    //                             window.location.href = "/onboarding-dashboard";
    //                             return;
    //                         }
    //                     }

    //                     if (organizations.length > 1) {
    //                         console.log("Multiple organizations found, redirecting to dashboard");
    //                         window.location.href = "/onboarding-dashboard";
    //                         return;
    //                     }
    //                     // ---- END REDIRECTION LOGIC ----

    //                 } else {
    //                     errorMsg(
    //                         "Login Failed",
    //                         `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
    //                         true
    //                     );
    //                 }
    //             } else {
    //                 errorMsg(
    //                     "Login Failed",
    //                     `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
    //                     true
    //                 );
    //             }
    //         } catch (e) {
    //             if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
    //                 errorMsg("Login Failed", e.response.data.error, false);
    //             } else {
    //                 errorMsg(e.code, e.message, true);
    //             }
    //         } finally {
    //             setSigningIn(false);
    //             setSubmitting(false);
    //         }
    //     }
    // };



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

        if (!_.isEmpty(response?.data?.access_token)) {
          const accessToken = response.data.access_token;

          // NOTE: Do NOT persist the token yet. We must first verify this account is
          // allowed on the admin portal, otherwise an employee credential would be
          // stored/dispatched before the guard below can reject it.
          const orgRes = await axios.get(
            `${GlobalConst.API_URL}/api/organizations`,
            {
              headers: { Authorization: `Bearer ${accessToken}` },
            }
          );

          const organizations = normalizeOrganizationsResponse(orgRes);

          // ---------- ADMIN PORTAL ACCESS GUARD (frontend mitigation, fail-open) ----------
          // Intent: keep an *employee-only* account out of the admin portal, while never
          // locking out a genuine admin (or a brand-new user who just registered) when the
          // role lookup is inconclusive.
          //
          // Role signals (per organization, from /my-role):
          //   - admin/HR user      => data present with a non-empty roleId OR roleName.
          //   - employee-only user => data present, no roleId/roleName, isEmployeePortalEnable=true.
          //   - inconclusive       => the call errored/timed out/403/404, or returned no `data`
          //                           (e.g. status 404 "no mapping"). We CANNOT prove anything here.
          //
          // A user may hold different roles in different orgs (admin in one, employee in another),
          // so we look across ALL orgs and:
          //   * allow if admin in AT LEAST ONE org, OR if every result was inconclusive;
          //   * block ONLY when we have POSITIVE proof of employee-only access and NO admin role
          //     anywhere. This is deliberately fail-open — the backend OrganizationRoleInterceptor
          //     is the real, per-request enforcement; the frontend must not hard-fail login on a
          //     transient/ambiguous role lookup. (Fixes the first-login "Access Denied" regression.)
          if (organizations.length >= 1) {
            const orgIds = organizations
              .map((o) => o?.organizationId)
              .filter(Boolean);

            // Treat a numeric roleId as present (lodash _.isEmpty(number) is always true).
            const hasValue = (v) =>
              v !== null && v !== undefined && String(v).trim() !== "";

            // Resolve the caller's role in each organization in parallel.
            const roleChecks = await Promise.allSettled(
              orgIds.map((orgId) =>
                axios.get(
                  `${GlobalConst.API_URL}/api/organization-user-role-mapping/my-role`,
                  {
                    headers: {
                      Authorization: `Bearer ${accessToken}`,
                      organizationId: orgId,
                    },
                  }
                )
              )
            );

            let sawAdminRole = false;
            let sawConclusiveEmployeeOnly = false;

            roleChecks.forEach((res) => {
              if (res.status !== "fulfilled") return; // network/HTTP error => inconclusive
              const body = res.value?.data;
              // my-role returns { status, message, data } — no `data` on a 404 "no mapping".
              if (!body || body.status !== 200 || !body.data) return; // inconclusive
              const role = body.data;
              if (hasValue(role.roleId) || hasValue(role.roleName)) {
                sawAdminRole = true;
              } else if (role.isEmployeePortalEnable === true) {
                sawConclusiveEmployeeOnly = true;
              }
            });

            // Block only with positive proof: employee-enabled somewhere, admin nowhere.
            if (!sawAdminRole && sawConclusiveEmployeeOnly) {
              errorMsg(
                "Access Denied",
                "This account does not have access to the admin portal. Please use the Employee Portal to sign in.",
                false
              );
              return;
            }
          }
          // ---------- END ADMIN PORTAL ACCESS GUARD ----------

          // Access verified as admin/HR (or a new admin with no organization yet).
          // It is now safe to persist the token and continue.
          await localStorage.setItem("__t", accessToken);
          if (response.data.refresh_token) {
            await localStorage.setItem("__r", response.data.refresh_token);
          }
          // Mark this session as an admin-portal session for the route guards.
          localStorage.setItem("userType", "admin");

          dispatch(updateToken(accessToken));

          navigate(resolveAdminLandingPath(organizations));
          return;
        } else {
            errorMsg(
            "Login Failed",
            `There was an error while we were trying to login, please contact ${GlobalConst.SUPPORT_EMAIL}`,
            true
          );
        }
      } catch (e) {
        if (!_.isEmpty(e?.response?.data)) {
          errorMsg("Invalid Username or Password", e.response.data.error, false);
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
    <a href="/forgot-password" className="link-primary fs-6 fw-bold">Forgot Password ?</a>
  </div>

  <div className="position-relative">
    <Field
      type={showPassword ? 'text' : 'password'}
      name="password"
      className={`form-control form-control-lg form-control-solid ${errors.password && touched.password ? 'is-invalid' : ''}`}
      placeholder="Enter password"
      disabled={isSubmitting}
      autoComplete="current-password"
    />

    <button
      type="button"
      aria-label={showPassword ? 'Hide password' : 'Show password'}
      onClick={(e) => { e.preventDefault(); setShowPassword(prev => !prev); }}
      className="btn btn-sm btn-icon position-absolute translate-middle top-50 end-0 me-3"
      style={{ transform: 'translateY(-50%)' }}
      tabIndex={0}
    >
      {showPassword ? <AiFillEyeInvisible className="fs-4" /> : <AiFillEye className="fs-4" />}
    </button>
  </div>

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