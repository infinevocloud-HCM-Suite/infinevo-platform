import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from 'lodash';
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import Swal from 'sweetalert2';
import 'sweetalert2/dist/sweetalert2.min.css';

import Loader from "../../../shared/components/loaders/fullPageLoader";
import loginBack from '../../../assets/images/login-back.png';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import logo from '../../../assets/images/infine-logo.png';
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../shared/helpers/msgHelper";

export default function ForgotPasswordPage() {
    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [initialValues] = useState({
        userEmail: ''
    });

    const validationSchema = Yup.object().shape({
        userEmail: Yup.string()
            .email('Enter a valid email')
            .required('Email is required')
    });

   


     const handleSubmit = async (values, { setSubmitting, resetForm }) => {
            setSigningIn(true);
            setSubmitting(true);
            
            try {
                // Call backend API to verify email and send reset email
                const params = new URLSearchParams();
                params.append("email", values.userEmail);
                
                const response = await axios.post(
                    `${GlobalConst.API_URL}/auth/forgot-password`,
                    null,
                    { params }
                );
                
                if (response.status === 200) {
                    // Show success message and let the user wait for their email
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
                              '<div class="swal2-html-container" style="display: block;">We have sent a password reset link to your email address. Please check your inbox.</div>',
                        confirmButtonText: 'Ok, got it!',
                        customClass: {
                            popup: 'swal2-popup swal2-modal swal2-icon-success swal2-show',
                            confirmButton: 'swal2-confirm btn btn-primary'
                        },
                        buttonsStyling: false,
                        showCloseButton: false
                    });
                    
                    resetForm();
                }
            } catch (error) {
                console.error("Forgot password email sending error:", error);
                if (error.response?.status === 404) {
                    errorMsg("Error", "This email address is not registered in our system.", true);
                } else {
                    errorMsg("Error", "Failed to send password reset link. Please try again later.", true);
                }
            } finally {
                setSigningIn(false);
                setSubmitting(false);
            }
        }
    return (
        <>
            <Helmet>
                <title>Payroll - Forgot Password</title>
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
                        <div className="w-lg-500px p-10 p-lg-15 mx-auto">
                            <Formik
                                initialValues={initialValues}
                                validationSchema={validationSchema}
                                onSubmit={handleSubmit}
                                enableReinitialize
                            >
                                {({ isSubmitting, errors, touched }) => (
                                    <Form className="form w-100 fv-plugins-bootstrap5 fv-plugins-framework">
                                        <div className="text-center mb-10">
                                            <h1 className="text-gray-900 mb-3">
                                                Forgot Password ?
                                            </h1>
                                            <div className="text-gray-500 fw-semibold fs-4">
                                                Enter your email to reset your password.
                                            </div>
                                        </div>

                                        <div className="fv-row mb-7 fv-plugins-icon-container">
                                            <label htmlFor="userEmail" className="form-label fs-6 fw-bold text-dark">Email</label>
                                            <Field
                                                type="email"
                                                name="userEmail"
                                                className={`form-control form-control-lg form-control-solid ${errors.userEmail && touched.userEmail ? 'is-invalid' : ''}`}
                                                placeholder="Enter Email Address"
                                                disabled={isSubmitting}
                                            />
                                            <ErrorMessage name="userEmail" component="div" className="invalid-feedback" />
                                        </div>

                                        <div className="d-flex flex-wrap justify-content-center pb-lg-0">
                                            <button 
                                                type="submit" 
                                                className="btn btn-lg btn-primary fw-bold me-4"
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
                                            <a href="/login" className="btn btn-lg btn-light-primary fw-bold">Cancel</a>
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