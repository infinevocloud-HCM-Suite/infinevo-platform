import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import Swal from 'sweetalert2';
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg } from "../../../shared/helpers/msgHelper";
import { getUserInfoFromToken } from "../../../shared/helpers/tokenHelper";
import 'sweetalert2/dist/sweetalert2.min.css';
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';

export default function ChangePassword() {
    const [signingIn, setSigningIn] = useState(false);
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [initialValues] = useState({
        password: '',
        confirmPassword: '',
        toc: false
    });

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
            // Get the logged-in user's email from the JWT token
            const userInfo = getUserInfoFromToken();
            const email = userInfo?.email;

            if (!email) {
                errorMsg("Error", "Unable to identify the logged-in user. Please log in again.", false);
                return;
            }

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

            // Show success message (same pattern as NewPassword.js)
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
                      '<div class="swal2-html-container" style="display: block;">Your password has been updated successfully!</div>',
                confirmButtonText: 'Ok, got it!',
                customClass: {
                    popup: 'swal2-popup swal2-modal swal2-icon-success swal2-show',
                    confirmButton: 'swal2-confirm btn btn-primary'
                },
                buttonsStyling: false,
                showCloseButton: false
            }).then(() => {
                window.location.href = "/home";
            });

            resetForm();
        } catch (error) {
            console.error("Change password error:", error);
            errorMsg("Error", error?.response?.data?.message || "Failed to update password. Please try again.", true);
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    }

    return (
        <>
            <Helmet>
                <title>Change Password | InfiNevoCloud</title>
            </Helmet>

            {/* Toolbar / Breadcrumb */}
            <div id="kt_app_toolbar" className="app-toolbar pt-9">
                <div id="kt_app_toolbar_container" className="app-container container-fluid d-flex align-items-stretch">
                    <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
                        <div className="page-title d-flex flex-column gap-1 me-3 mb-2">
                            <ul className="breadcrumb breadcrumb-separatorless fw-semibold mb-6">
                                <li className="breadcrumb-item text-gray-700 fw-bold lh-1">
                                    <a href="/home" className="text-gray-500">
                                        <i className="ki-duotone ki-home fs-3 text-gray-400 me-n1"></i>
                                    </a>
                                </li>
                                <li className="breadcrumb-item">
                                    <i className="ki-duotone ki-right fs-4 text-gray-700 mx-n1"></i>
                                </li>
                                <li className="breadcrumb-item text-gray-700 fw-bold lh-1">Change Password</li>
                            </ul>
                            <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">
                                Change Password
                            </h1>
                        </div>
                    </div>
                </div>
            </div>

            {/* Page Content */}
            <div id="kt_app_content" className="app-content flex-column-fluid">
                <div id="kt_app_content_container" className="app-container container-fluid">
                    <div className="row justify-content-center">
                        <div className="col-lg-6 col-xl-5">
                            <div className="card shadow-sm">
                                <div className="card-header border-0 pt-6">
                                    <div className="d-flex align-items-center">
                                        <span className="me-3">
                                            <i className="ki-duotone ki-lock fs-2x text-primary">
                                                <span className="path1"></span>
                                                <span className="path2"></span>
                                            </i>
                                        </span>
                                        <div>
                                            <h3 className="fw-bold m-0 fs-4">Update Your Password</h3>
                                            <span className="text-muted fs-7">Keep your account secure with a strong password</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body pt-5">
                                    <Formik
                                        initialValues={initialValues}
                                        validationSchema={validationSchema}
                                        onSubmit={handleSubmit}
                                    >
                                        {({ isSubmitting, errors, touched, values }) => (
                                            <Form>
                                                {/* New Password */}
                                                <div className="mb-6">
                                                    <label className="form-label fw-semibold text-gray-700 fs-6 required">New Password</label>
                                                    <div className="position-relative">
                                                        <Field
                                                            name="password"
                                                            type={showPassword ? "text" : "password"}
                                                            className={`form-control form-control-lg form-control-solid ${errors.password && touched.password ? 'is-invalid' : ''}`}
                                                            placeholder="Enter new password"
                                                            autoComplete="new-password"
                                                        />
                                                        <button
                                                            type="button"
                                                            onClick={() => setShowPassword(p => !p)}
                                                            className="btn btn-sm btn-icon position-absolute top-50 end-0 translate-middle-y me-2"
                                                            style={{ zIndex: 10 }}
                                                        >
                                                            {showPassword ? <AiFillEyeInvisible className="fs-4 text-gray-500" /> : <AiFillEye className="fs-4 text-gray-500" />}
                                                        </button>
                                                        <ErrorMessage name="password" component="div" className="invalid-feedback" />
                                                    </div>
                                                </div>

                                                {/* Password Strength */}
                                                <div className="mb-6">
                                                    <div className="d-flex align-items-center mb-2">
                                                        <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 0 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                        <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 3 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                        <div className={`flex-grow-1 rounded h-5px me-2 ${values.password?.length > 6 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                        <div className={`flex-grow-1 rounded h-5px ${values.password?.length > 8 ? 'bg-success' : 'bg-secondary'}`}></div>
                                                    </div>
                                                    <div className="text-muted fs-8">Use 8+ characters with uppercase, lowercase, numbers &amp; symbols.</div>
                                                </div>

                                                {/* Confirm Password */}
                                                <div className="mb-8">
                                                    <label className="form-label fw-semibold text-gray-700 fs-6 required">Confirm New Password</label>
                                                    <div className="position-relative">
                                                        <Field
                                                            name="confirmPassword"
                                                            type={showConfirmPassword ? "text" : "password"}
                                                            className={`form-control form-control-lg form-control-solid ${errors.confirmPassword && touched.confirmPassword ? 'is-invalid' : ''}`}
                                                            placeholder="Confirm new password"
                                                            autoComplete="new-password"
                                                        />
                                                        <button
                                                            type="button"
                                                            onClick={() => setShowConfirmPassword(p => !p)}
                                                            className="btn btn-sm btn-icon position-absolute top-50 end-0 translate-middle-y me-2"
                                                            style={{ zIndex: 10 }}
                                                        >
                                                            {showConfirmPassword ? <AiFillEyeInvisible className="fs-4 text-gray-500" /> : <AiFillEye className="fs-4 text-gray-500" />}
                                                        </button>
                                                        <ErrorMessage name="confirmPassword" component="div" className="invalid-feedback" />
                                                    </div>
                                                </div>

                                                {/* Terms & Conditions Checkbox */}
                                                <div className="fv-row mb-8 fv-plugins-icon-container">
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

                                                {/* Actions */}
                                                <div className="d-flex justify-content-end gap-3">
                                                    <button type="reset" className="btn btn-light fw-semibold" disabled={isSubmitting}>Cancel</button>
                                                    <button
                                                        type="submit"
                                                        className="btn btn-primary fw-semibold"
                                                        disabled={isSubmitting}
                                                        data-kt-indicator={isSubmitting ? 'on' : 'off'}
                                                    >
                                                        <span className="indicator-label">Update Password</span>
                                                        <span className="indicator-progress">Saving... <span className="spinner-border spinner-border-sm align-middle ms-2"></span></span>
                                                    </button>
                                                </div>
                                            </Form>
                                        )}
                                    </Formik>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            {signingIn && <Loader />}
        </>
    );
}