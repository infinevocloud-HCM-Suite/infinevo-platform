import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from 'lodash';
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import Swal from 'sweetalert2';

import Loader from "../../../shared/components/loaders/fullPageLoader";
import loginBack from '../../../assets/images/login-back.png';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from '../../../assets/images/infine-logo.png';
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../shared/helpers/msgHelper";

import 'sweetalert2/dist/sweetalert2.min.css'; // <-- ADD THIS LINE HERE
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';

export default function Login() {
    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [email, setEmail] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [initialValues] = useState({
        password: '',
        confirmPassword: '',
        toc: false
    });

    useEffect(() => {
        const queryParams = new URLSearchParams(window.location.search);
        const urlEmail = queryParams.get("email");
        
        if (urlEmail) {
            setEmail(urlEmail);
            sessionStorage.setItem("reset_email", urlEmail);
        } else {
            const storedEmail = sessionStorage.getItem("reset_email");
            if (!storedEmail) {
                errorMsg("Access Denied", "Please verify your email address first before setting up a new password.", true, () => {
                    window.location.href = '/forgot-password';
                });
            } else {
                setEmail(storedEmail);
            }
        }
    }, []);

    const validationSchema = Yup.object().shape({
        password: Yup.string()
            .required('Password is required')
            .min(8, 'Password must be at least 8 characters')
            .matches(
                /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*])/,
                'Must contain uppercase, lowercase, number and special character'
            ),
        confirmPassword: Yup.string()
            .required('Please confirm your password')
            .oneOf([Yup.ref('password'), null], 'Passwords must match'),
        toc: Yup.boolean()
            .oneOf([true], 'You must accept the terms and conditions')
    });

    const handleSubmit = async (values, { setSubmitting, resetForm }) => {
        setSigningIn(true);
        setSubmitting(true);
        
        try {
            // Call API to update user password in Keycloak via Spring Boot proxy
            await axios.post(
                `${GlobalConst.API_URL}/auth/update-password`,
                null,
                {
                    params: {
                        email: email,
                        newPassword: values.password
                    }
                }
            );
            
            // Clear the reset email from sessionStorage
            sessionStorage.removeItem("reset_email");
            
            // Show success message
            Swal.fire({
                title: 'Success',
                html: '<div class="swal2-icon swal2-success swal2-icon-show" style="display: flex;">' +
                      '<div class="swal2-success-circular-line-left" style="background-color: rgb(255, 255, 255);"></div>' +
                      '<span class="swal2-success-line-tip"></span>' +
                      '<span class="swal2-success-line-long"></span>' +
                      '<div class="swal2-success-ring"></div>' +
                      '<div class="swal2-success-fix" style="background-color: rgb(255, 255, 255);"></div>' +
                      '<div class="swal2-success-circular-line-right" style="background-color: rgb(255, 255, 255);"></div>' +
                      '</div>' +
                      '<div class="swal2-html-container" style="display: block;">You have successfully reset your password!</div>',
                confirmButtonText: 'Ok, got it!',
                customClass: {
                    popup: 'swal2-popup swal2-modal swal2-icon-success swal2-show',
                    confirmButton: 'swal2-confirm btn btn-primary'
                },
                buttonsStyling: false,
                showCloseButton: false
            }).then(() => {
                // Redirect after success
                window.location.href = '/login';
            });
            
            resetForm();
        } catch (error) {
            console.error("Reset password submission error:", error);
            errorMsg("Error", "Failed to reset password. Please try again.", true);
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    }

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Reset Password</title>
            </Helmet>
            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-auto bg-primary w-xl-600px positon-xl-relative">
                    <div className="d-flex flex-column position-xl-fixed top-0 bottom-0 w-xl-600px scroll-y">
                        <div className="d-flex flex-row-fluid flex-column text-center p-5 p-lg-10 pt-lg-20">
                            <a href="/" className="py-2 py-lg-20">
                                <img alt="Logo" src={logo} className="h-40px h-lg-20px" />
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
                        <div className="w-lg-550px p-10 p-lg-15 mx-auto">
                            <Formik
                                initialValues={initialValues}
                                validationSchema={validationSchema}
                                onSubmit={handleSubmit}
                                enableReinitialize
                            >
                                {({ isSubmitting, errors, touched, values }) => (
                                    <Form className="form w-100 fv-plugins-bootstrap5 fv-plugins-framework">
                                        <div className="text-center mb-10">
                                            <h1 className="text-gray-900 mb-3">
                                                Setup New Password
                                            </h1>
                                            <div className="text-gray-500 fw-semibold fs-4">
                                                Already have reset your password ?
                                                <a href="/login" className="link-primary fw-bold">
                                                    Sign in here
                                                </a>
                                            </div>
                                        </div>

                                        <div className="mb-10 fv-row fv-plugins-icon-container" data-kt-password-meter="true">
                                            <div className="mb-1">
                                                <label className="form-label fw-bold text-gray-900 fs-6">
                                                    Password
                                                </label>
                                                 <div className="position-relative mb-3">
                                                     <Field
                                                         name="password"
                                                         type={showPassword ? "text" : "password"}
                                                         className={`form-control form-control-lg form-control-solid ${errors.password && touched.password ? 'is-invalid' : ''}`}
                                                         autoComplete="off"
                                                     />
                                                     <button
                                                         type="button"
                                                         onClick={(e) => { e.preventDefault(); setShowPassword(prev => !prev); }}
                                                         className="btn btn-sm btn-icon position-absolute translate-middle top-50 end-0 me-3"
                                                         style={{ transform: 'translateY(-50%)', zIndex: 10, cursor: 'pointer' }}
                                                     >
                                                         {showPassword ? <AiFillEyeInvisible className="fs-4" /> : <AiFillEye className="fs-4" />}
                                                     </button>
                                                     <ErrorMessage name="password" component="div" className="invalid-feedback" />
                                                 </div>
                                                <div className="d-flex align-items-center mb-3">
                                                    <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 0 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                    <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 3 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                    <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 6 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                    <div className={`flex-grow-1 rounded h-5px ${values.password?.length > 8 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                </div>
                                            </div>
                                            <div className="text-muted">
                                                Use 8 or more characters with a mix of letters, numbers &amp; symbols.
                                            </div>
                                        </div>

                                         <div className="fv-row mb-10 fv-plugins-icon-container">
                                             <label className="form-label fw-bold text-gray-900 fs-6">Confirm Password</label>
                                             <div className="position-relative">
                                                 <Field
                                                     name="confirmPassword"
                                                     type={showConfirmPassword ? "text" : "password"}
                                                     className={`form-control form-control-lg form-control-solid ${errors.confirmPassword && touched.confirmPassword ? 'is-invalid' : ''}`}
                                                     autoComplete="off"
                                                 />
                                                 <button
                                                     type="button"
                                                     onClick={(e) => { e.preventDefault(); setShowConfirmPassword(prev => !prev); }}
                                                     className="btn btn-sm btn-icon position-absolute translate-middle top-50 end-0 me-3"
                                                     style={{ transform: 'translateY(-50%)', zIndex: 10, cursor: 'pointer' }}
                                                 >
                                                     {showConfirmPassword ? <AiFillEyeInvisible className="fs-4" /> : <AiFillEye className="fs-4" />}
                                                 </button>
                                             </div>
                                             <ErrorMessage name="confirmPassword" component="div" className="invalid-feedback" />
                                         </div>

                                        <div className="fv-row mb-10 fv-plugins-icon-container">
                                            <div className="form-check form-check-custom form-check-solid form-check-inline">
                                                <Field
                                                    type="checkbox"
                                                    name="toc"
                                                    id="toc"
                                                    className={`form-check-input ${errors.toc && touched.toc ? 'is-invalid' : ''}`}
                                                />
                                                <label className="form-check-label fw-semibold text-gray-700 fs-6" htmlFor="toc">
                                                    I Agree &amp;
                                                    <a href="#" className="ms-1 link-primary">Terms and conditions</a>.
                                                </label>
                                            </div>
                                            <ErrorMessage name="toc" component="div" className="invalid-feedback" />
                                        </div>

                                        <div className="text-center">
                                            <button
                                                type="submit"
                                                className="btn btn-lg btn-primary fw-bold"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? (
                                                    <span className="indicator-progress">
                                                        Please wait... <span className="spinner-border spinner-border-sm align-middle ms-2"></span>
                                                    </span>
                                                ) : (
                                                    <span className="indicator-label">
                                                        Submit
                                                    </span>
                                                )}
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
                                target="_blank" rel="noopener noreferrer">About</a>
                            <a href="http://infinevocloud.com/contact.php" className="text-muted text-hover-primary px-2"
                                target="_blank" rel="noopener noreferrer">Support</a>
                        </div>
                    </div>
                </div>
            </div>
            {signingIn && <Loader />}
        </>
    );
}