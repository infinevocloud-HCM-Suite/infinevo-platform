import React from 'react';
import { FiSave } from "react-icons/fi";
import { Formik, Form, ErrorMessage, Field } from 'formik';
import _ from 'lodash';
import * as Yup from 'yup';
import { Tooltip } from 'antd';
import axios from 'axios';
import { GlobalConst } from '../../../../shared/appConfig/globalConst';
import { errorMsg, successMsg } from '../../../../shared/helpers/msgHelper';
import { useSelector } from 'react-redux';

const EditSecurityDetailsTab = () => {

    const userState = useSelector((state) => state.authReducer.userDetails);

    const initialValues = {
        oldPassword: '',
        newPassword: '',
        confirmNewPassword: ''
    };

    const validationSchema = Yup.object().shape({
        oldPassword: Yup.string().required('Current password is required'),
        newPassword: Yup.string()
            .required('New password is required')
            .min(8, 'Password must be at least 8 characters')
            .matches(
                /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,24}$/,
                'Password must be 6-24 characters and contain both letters and numbers'
            ),
        confirmNewPassword: Yup.string()
            .required('Confirm password is required')
            .oneOf([Yup.ref('newPassword')], 'Passwords must match')
    });

    const handleSubmit = async (values, { setSubmitting, resetForm }) => {
        setSubmitting(true);

        const postData = {
            "loginUsername": userState.preferred_username,
            "loginPassword": values.oldPassword,
            "newPassword": values.newPassword,
            "confirmPassword": values.confirmNewPassword
        };

        axios
            .put(`${GlobalConst.API_URL}/api/v1/entrance/user/change-password`, postData)
            .then(op => {
                if (!_.isEmpty(op) && !_.isEmpty(op.data) && op.data.message === 'PASSWORD_UPDATED_SUCCESSFULLY') {
                    successMsg("Password updated successfully", "Your password has been successfully updated. Please use your new password for future logins.", true, () => {
                        resetForm();
                    });
                }
                else {
                    errorMsg("Unable to update password", "We are facing some issues while updating your password, please try after sometime or if the issue is still persisting then please contact the helpdesk.", true);
                }
            })
            .catch((e) => {
                if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.data)) {
                    errorMsg("Unable to update password", e.response.data.err_msg, false);
                }
                else {
                    errorMsg(e.code, e.message, true);
                }
            })
            .finally(() => {
                setSubmitting(false);
            });
    };



    return (

        <Formik
            initialValues={initialValues}
            validationSchema={validationSchema}
            onSubmit={handleSubmit}
        >
            {({ isSubmitting, errors, touched }) => (
                <Form className="form w-100">

                    <div className="card mb-5 mb-xl-10" id="kt_profile_details_view">
                        <div className="card-header">
                            <div className="card-title m-0">
                                <h3 className="fw-bold m-0">Security Settings</h3>
                            </div>
                        </div>

                        <div className="card-body p-9">
                            <div className="row mb-7">
                                <label htmlFor="oldPassword" className="col-lg-4 text-muted d-flex align-items-center fs-6 fw-semibold form-label mb-2">
                                    <span className="required">Current Password</span>
                                    <Tooltip title="Enter your current password to verify your identity">
                                        <span className="ms-1">
                                            <i className="ki-duotone ki-information-5 text-gray-500 fs-6">
                                                <span className="path1"></span>
                                                <span className="path2"></span>
                                                <span className="path3"></span>
                                            </i>
                                        </span>
                                    </Tooltip>
                                </label>
                                <div className="col-lg-8">

                                    <Field
                                        id="oldPassword"
                                        type="password"
                                        name="oldPassword"
                                        className={`form-control form-control-lg form-control-solid mb-3 mb-lg-0 ${errors.oldPassword && touched.oldPassword ? 'is-invalid' : ''}`}
                                        placeholder="Enter current password"
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage name="oldPassword" component="div" className="invalid-feedback" />

                                </div>
                            </div>
                            <div className="row mb-7">
                                <label htmlFor="newPassword" className="col-lg-4 text-muted d-flex align-items-center fs-6 fw-semibold form-label mb-2">
                                    <span className="required">New Password</span>
                                    <Tooltip title="Password must be at least 8 characters and include uppercase, lowercase, number, and special character">
                                        <span className="ms-1">
                                            <i className="ki-duotone ki-information-5 text-gray-500 fs-6">
                                                <span className="path1"></span>
                                                <span className="path2"></span>
                                                <span className="path3"></span>
                                            </i>
                                        </span>
                                    </Tooltip>
                                </label>
                                <div className="col-lg-8">
                                    <Field
                                        id="newPassword"
                                        type="password"
                                        name="newPassword"
                                        className={`form-control form-control-lg form-control-solid mb-3 mb-lg-0 ${errors.newPassword && touched.newPassword ? 'is-invalid' : ''}`}
                                        placeholder="Enter new password"
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage name="newPassword" component="div" className="invalid-feedback" />
                                </div>
                            </div>
                            <div className="row mb-7">
                                <label htmlFor="confirmNewPassword" className="col-lg-4 text-muted d-flex align-items-center fs-6 fw-semibold form-label mb-2">
                                    <span className="required">Confirm New Password</span>
                                </label>
                                <div className="col-lg-8">
                                    <Field
                                        id="confirmNewPassword"
                                        type="password"
                                        name="confirmNewPassword"
                                        className={`form-control form-control-lg form-control-solid mb-3 mb-lg-0 ${errors.confirmNewPassword && touched.confirmNewPassword ? 'is-invalid' : ''}`}
                                        placeholder="Confirm new password"
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage name="confirmNewPassword" component="div" className="invalid-feedback" />
                                </div>
                            </div>

                            <div className="card-footer py-6 px-9">
                                <div className="row">
                                    <div className="col-lg-4"></div>
                                    <div className="col-lg-8">
                                        <button type="submit" className="btn btn-primary" disabled={isSubmitting} data-kt-indicator={(isSubmitting) ? "on" : "off"}>
                                            <span className="indicator-label"><FiSave className="me-2" />Update Password</span>
                                            <span className="indicator-progress">Updating password...
                                                <span className="spinner-border spinner-border-sm align-middle ms-2"></span></span>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </Form>
            )}
        </Formik>
    );
};

export default EditSecurityDetailsTab;