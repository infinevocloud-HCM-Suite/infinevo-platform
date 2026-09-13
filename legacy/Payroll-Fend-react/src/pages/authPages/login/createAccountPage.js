import React, { useState, useEffect } from 'react';
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import axios from 'axios';
import { Dropdown, Space } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import Loader from "../../../shared/components/loaders/fullPageLoader";
import loginBack from '../../../assets/images/login-back.png';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { countryCodes } from "../../../shared/appConfig/countryCodes";
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import { countryStateList } from "../../../shared/appConfig/countryStateList";
// near top of file (you already have React, useState), add these icon imports:
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';

export default function CreateAccountPage() {
    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [items, setItems] = useState([]);
    const [registrationError, setRegistrationError] = useState(null);
    const [selectedCountry, setSelectedCountry] = useState('');
    const [filteredStates, setFilteredStates] = useState([]);
    const [selectedCountryCode, setSelectedCountryCode] = useState('+91'); // Default to +91

    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);

    // Prepare country code dropdown items
    useEffect(() => {
        let array = [];
        countryCodes.forEach((el, ind) => {
            array.push({
                label: (
                    <span onClick={() => {
                        setSelectedCountryCode(el.dial_code);
                    }}>
                        {el.name} ({el.dial_code})
                    </span>
                ),
                key: ind.toString(),
            })
        });
        setItems(array);
    }, []);

    // Validation schema with comprehensive checks - FIXED password regex to match min 10 characters
    const validationSchema = Yup.object().shape({
        companyName: Yup.string()
            .required('Company Name is required')
            .min(2, 'Company Name must be at least 2 characters')
            .max(50, 'Company Name must not exceed 50 characters'),

        userEmail: Yup.string()
            .email('Invalid email address')
            .required('Email is required')
            .matches(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Invalid email format'),

        phoneNumber: Yup.string()
            .required('Phone Number is required')
            .matches(/^[0-9]{10}$/, 'Phone number must be 10 digits'),

        name: Yup.string()
            .required('Country is required')
            .min(2, 'Country name too short')
            .max(50, 'Country name too long'),

        state: Yup.string()
            .required('State is required')
            .min(2, 'State name too short')
            .max(50, 'State name too long'),

        firstName: Yup.string()
            .required('First Name is required')
            .min(2, 'First Name must be at least 2 characters')
            .max(50, 'First Name must not exceed 50 characters'),

        lastName: Yup.string()
            .required('Last Name is required')
            .min(2, 'Last Name must be at least 2 characters')
            .max(50, 'Last Name must not exceed 50 characters'),

        password: Yup.string()
            .required('Password is required')
            .min(10, 'Password must be at least 10 characters')
            .matches(
                /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{10,}$/, // Changed 8+ to 10+
                'Password must include uppercase, lowercase, number, and special character'
            ),

        confirmPassword: Yup.string()
            .required('Confirm Password is required')
            .oneOf([Yup.ref('password'), null], 'Passwords must match'),

        termsAndConditions: Yup.boolean()
            .required('You must accept the terms and conditions')
            .oneOf([true], 'You must accept the terms and conditions')
    });

    // Initial form values
    const initialValues = {
        companyName: '',
        userEmail: '',
        phoneNumber: '',
        name: '',
        state: '',
        firstName: '',
        lastName: '',
        password: '',
        confirmPassword: '',
        termsAndConditions: false,
        countryCode: '+91'
    };

    // Handle form submission
    const handleSubmit = async (values, { setSubmitting, setFieldError }) => {
        console.log("in handle form");
        setSigningIn(true);
        setRegistrationError(null);

        try {
            const postData = {
                "companyName": values.companyName,
                "userEmail": values.userEmail,
                "firstName": values.firstName,
                "lastName": values.lastName,
                "phoneNumber": selectedCountryCode + values.phoneNumber,
                "country": values.name,
                "state": values.state,
                "password": values.password,
                "toc": true
            }

            axios
                .post(`${GlobalConst.API_URL}/auth/register`, postData)
                .then(op => {
                    if (op.data == "User registered successfully.") {
                        successMsg(
                            "User Registered Successfully",
                            "New user registered successfully please login to continue...",
                            true,
                            () => { window.location.href = "/login" }
                        )
                    }
                })
                .catch(e => {
                    console.log("exception: ", e);
                    
                    // FIXED: Properly extract and display backend error message
                    let errorMessage = 'Registration failed. Please try again.';
                    
                    if (e.response?.data?.errorMessage) {
                        errorMessage = e.response.data.errorMessage;
                    } else if (e.response?.data?.err_msg) {
                        errorMessage = e.response.data.err_msg;
                    } else if (e.response?.data?.message) {
                        errorMessage = e.response.data.message;
                    } else if (e.message) {
                        errorMessage = e.message;
                    }
                    
                    // Set registration error to display in UI
                    setRegistrationError(errorMessage);
                    
                    // Also set field-specific error for email if it's a duplicate
                    if (errorMessage.includes('User exists with same email') || 
                        errorMessage.includes('email already exists') ||
                        errorMessage.includes('duplicate') && errorMessage.includes('email')) {
                        setFieldError('userEmail', 'This email is already registered');
                    }
                    
                    errorMsg(
                        "Registration Failed, User exists with same email",
                        errorMessage,
                        false
                    );
                })
                .finally(() => {
                    console.log("executed");
                    setSigningIn(false);
                    setSubmitting(false);
                })
        } catch (error) {
            // Handle error
            let errorMessage = 'Registration failed. Please try again.';
            
            if (error.response?.data?.errorMessage) {
                errorMessage = error.response.data.errorMessage;
            } else if (error.response?.data?.err_msg) {
                errorMessage = error.response.data.err_msg;
            } else if (error.response?.data?.message) {
                errorMessage = error.response.data.message;
            } else if (error.message) {
                errorMessage = error.message;
            }
            
            setRegistrationError(errorMessage);
            
            errorMsg(
                error.code || 'Error',
                errorMessage,
                true
            );
            setSigningIn(false);
            setSubmitting(false);
        }
    };

    return (
        <>
            <Helmet>
                <title>Payroll - Let's Get Started</title>
            </Helmet>
            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-auto bg-primary w-xl-600px positon-xl-relative">
                    <div className="d-flex flex-column position-xl-fixed top-0 bottom-0 w-xl-600px scroll-y">
                        <div className="d-flex flex-row-fluid flex-column text-center p-5 p-lg-10 pt-lg-20">
                            <a href="/" className="py-2 py-lg-20">
                                {/* Logo placeholder */}
                            </a>
                            <div className="py-2 py-lg-20"></div>
                            <h1 className="d-none d-lg-block fw-bold text-white fs-2qx pb-5 pb-md-10">Welcome to <br />HRMS INFINEVOCLOUD</h1>
                            <p className="d-none d-lg-block fw-semibold fs-2 text-white">
                                HRMS Platform to
                                manage the leaves and other HR related tasks
                            </p>
                        </div>
                        <div
                            className="d-none d-lg-block d-flex flex-row-auto bgi-no-repeat bgi-position-x-center bgi-size-contain bgi-position-y-bottom min-h-100px min-h-lg-350px"
                            style={{ "backgroundImage": `url(${loginBack})` }}
                        ></div>
                    </div>
                </div>

                <div className="d-flex flex-column flex-lg-row-fluid py-10">
                    <div className="d-flex flex-center flex-column flex-column-fluid">
                        <div className="w-lg-600px p-10 p-lg-15 mx-auto">
                            <Formik
                                initialValues={initialValues}
                                validationSchema={validationSchema}
                                onSubmit={handleSubmit}
                                enableReinitialize
                                validateOnChange={true}
                                validateOnBlur={true}
                            >
                                {({ isSubmitting, errors, touched, setFieldValue }) => (
                                    <Form className="form w-100 fv-plugins-bootstrap5 fv-plugins-framework" noValidate>
                                        <div className="mb-10 text-center">
                                            <h1 className="text-dark mb-3">
                                                Create an Account
                                            </h1>
                                            <div className="text-gray-400 fw-semibold fs-4">
                                                Already have an account?
                                                <a href="/" className="link-primary fw-bold ms-1">
                                                    Sign in here
                                                </a>
                                            </div>
                                        </div>

                                        {/* First and Last Name */}
                                        <div className="row fv-row mb-7 fv-plugins-icon-container">
                                            <div className="col-xl-6">
                                                <label htmlFor="firstName" className="form-label fw-bold text-dark fs-6">First Name</label>
                                                <Field
                                                    type="text"
                                                    name="firstName"
                                                    className={`form-control form-control-lg form-control-solid ${errors.firstName && touched.firstName ? 'is-invalid' : ''}`}
                                                    placeholder="Enter First Name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="firstName" component="div" className="invalid-feedback" />
                                            </div>
                                            <div className="col-xl-6">
                                                <label htmlFor="lastName" className="form-label fw-bold text-dark fs-6">Last Name</label>
                                                <Field
                                                    type="text"
                                                    name="lastName"
                                                    className={`form-control form-control-lg form-control-solid ${errors.lastName && touched.lastName ? 'is-invalid' : ''}`}
                                                    placeholder="Enter Last Name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="lastName" component="div" className="invalid-feedback" />
                                            </div>
                                        </div>

                                        {/* Company Name */}
                                        <div className="fv-row mb-7 fv-plugins-icon-container">
                                            <label htmlFor="companyName" className="form-label fs-6 fw-bold text-dark">Company Name</label>
                                            <Field
                                                type="text"
                                                name="companyName"
                                                className={`form-control form-control-lg form-control-solid ${errors.companyName && touched.companyName ? 'is-invalid' : ''}`}
                                                placeholder="Enter Company Name"
                                                disabled={isSubmitting}
                                            />
                                            <div className="form-text text-muted">
                                                This is saved to your profile. You will set up your organization after your first login.
                                            </div>
                                            <ErrorMessage name="companyName" component="div" className="invalid-feedback" />
                                        </div>

                                        {/* Email */}
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

                                        {/* Phone Number */}
                                        <div className="fv-row mb-7 fv-plugins-icon-container">
                                            <label htmlFor="phoneNumber" className="form-label fs-6 fw-bold text-dark">Phone Number</label>
                                            <div className="input-group mb-5">
                                                <span className="input-group-text border-0" id="basic-addon3">
                                                    <Dropdown
                                                        menu={{ items }}
                                                        trigger={['click']}
                                                    >
                                                        <a onClick={e => e.preventDefault()}>
                                                            <Space>
                                                                {selectedCountryCode}
                                                                <DownOutlined />
                                                            </Space>
                                                        </a>
                                                    </Dropdown>
                                                </span>
                                                <Field
                                                    type="text"
                                                    name="phoneNumber"
                                                    className={`form-control form-control-lg form-control-solid ${errors.phoneNumber && touched.phoneNumber ? 'is-invalid' : ''}`}
                                                    placeholder="Enter Phone Number"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="phoneNumber" component="div" className="invalid-feedback" />
                                            </div>
                                        </div>

                                        {/* Country and State */}
                                        <div className={`row fv-row mb-7 fv-plugins-icon-container ${(errors.name && touched.name) || (errors.state && touched.state) ? 'has-danger' : ''}`}>
                                            <div className="col-xl-6">
                                                <label className="form-label fw-bold text-dark fs-6">Country</label>
                                                <Field
                                                    as="select"
                                                    name="name"
                                                    className={`form-control form-control-lg form-control-solid ${errors.name && touched.name ? 'is-invalid' : ''}`}
                                                    onChange={(e) => {
                                                        const selected = e.target.value;
                                                        setSelectedCountry(selected);
                                                        setFieldValue("name", selected);
                                                        setFieldValue("state", ""); // Reset state when country changes
                                                        const matched = countryStateList.find(item => item.name === selected);
                                                        setFilteredStates(matched ? matched.states : []);
                                                    }}
                                                    disabled={isSubmitting}
                                                >
                                                    <option value="">Select Country</option>
                                                    {countryStateList && countryStateList.map((item, idx) => (
                                                        <option key={idx} value={item.name}>{item.name}</option>
                                                    ))}
                                                </Field>
                                                <ErrorMessage name="name" component="div" className="invalid-feedback" />
                                            </div>

                                            <div className="col-xl-6">
                                                <label className="form-label fw-bold text-dark fs-6">State</label>
                                                <Field
                                                    as="select"
                                                    name="state"
                                                    className={`form-control form-control-lg form-control-solid ${errors.state && touched.state ? 'is-invalid' : ''}`}
                                                    disabled={isSubmitting || !selectedCountry}
                                                >
                                                    <option value="">Select State</option>
                                                    {filteredStates && filteredStates.map((state, idx) => (
                                                        <option key={idx} value={state}>{state}</option>
                                                    ))}
                                                </Field>
                                                <ErrorMessage name="state" component="div" className="invalid-feedback" />
                                            </div>
                                        </div>

                                        {/* Password */}
                                        <div className="fv-row mb-7 fv-plugins-icon-container">
                                            <label htmlFor="password" className="form-label fs-6 fw-bold text-dark">Password</label>
                                            <div className="position-relative">
                                                <Field
                                                    type={showPassword ? 'text' : 'password'}
                                                    name="password"
                                                    className={`form-control form-control-lg form-control-solid ${errors.password && touched.password ? 'is-invalid' : ''}`}
                                                    placeholder="Enter Password"
                                                    disabled={isSubmitting}
                                                />
                                                <button
                                                    type="button"
                                                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                                                    onClick={(e) => { 
                                                        e.preventDefault(); 
                                                        setShowPassword(prev => !prev); 
                                                    }}
                                                    className="btn btn-sm btn-icon position-absolute translate-middle top-50 end-0 me-3"
                                                    style={{ transform: 'translateY(-50%)', zIndex: 10 }}
                                                    tabIndex={-1}
                                                >
                                                    {showPassword ? <AiFillEyeInvisible className="fs-4" /> : <AiFillEye className="fs-4" />}
                                                </button>
                                                <ErrorMessage name="password" component="div" className="invalid-feedback" />
                                            </div>
                                            {/* Password requirements hint */}
                                            {!errors.password && touched.password && (
                                                <div className="form-text text-muted">
                                                    Password must be at least 10 characters with uppercase, lowercase, number, and special character.
                                                </div>
                                            )}
                                        </div>

                                        {/* Confirm Password */}
                                        <div className="fv-row mb-7 fv-plugins-icon-container">
                                            <label htmlFor="confirmPassword" className="form-label fs-6 fw-bold text-dark">Confirm Password</label>
                                            <div className="position-relative">
                                                <Field
                                                    type={showConfirmPassword ? 'text' : 'password'}
                                                    name="confirmPassword"
                                                    className={`form-control form-control-lg form-control-solid ${errors.confirmPassword && touched.confirmPassword ? 'is-invalid' : ''}`}
                                                    placeholder="Confirm Password"
                                                    disabled={isSubmitting}
                                                />
                                                <button
                                                    type="button"
                                                    aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
                                                    onClick={(e) => { 
                                                        e.preventDefault(); 
                                                        setShowConfirmPassword(prev => !prev); 
                                                    }}
                                                    className="btn btn-sm btn-icon position-absolute translate-middle top-50 end-0 me-3"
                                                    style={{ transform: 'translateY(-50%)', zIndex: 10 }}
                                                    tabIndex={-1}
                                                >
                                                    {showConfirmPassword ? <AiFillEyeInvisible className="fs-4" /> : <AiFillEye className="fs-4" />}
                                                </button>
                                                <ErrorMessage name="confirmPassword" component="div" className="invalid-feedback" />
                                            </div>
                                        </div>

                                        {/* Terms and Conditions - FIXED */}
                                        <div className="fv-row mb-10">
                                            <div className="form-check">
                                                <Field
                                                    type="checkbox"
                                                    name="termsAndConditions"
                                                    className={`form-check-input ${errors.termsAndConditions && touched.termsAndConditions ? 'is-invalid' : ''}`}
                                                    id="termsAndConditions"
                                                />
                                                <label className="form-check-label" htmlFor="termsAndConditions">
                                                    I Agree <a href="#" className="ms-1 link-primary">Terms and conditions</a>.
                                                </label>
                                                <ErrorMessage name="termsAndConditions" component="div" className="invalid-feedback d-block" />
                                            </div>
                                        </div>

                                        {/* Registration Error Message - FIXED */}
                                        {registrationError && (
                                            <div className="alert alert-danger alert-dismissible fade show" role="alert">
                                                <strong>Registration Failed!</strong>{registrationError}
                                                <button 
                                                    type="button" 
                                                    className="btn-close" 
                                                    onClick={() => setRegistrationError(null)}
                                                    aria-label="Close"
                                                ></button>
                                            </div>
                                        )}

                                        {/* Submit Button */}
                                        <div className="text-center">
                                            <button
                                                type="submit"
                                                id="kt_sign_up_submit"
                                                className="btn btn-lg btn-primary"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? (
                                                    <>
                                                        <span className="indicator-label">Submitting...</span>
                                                        <span className="spinner-border spinner-border-sm align-middle ms-2"></span>
                                                    </>
                                                ) : (
                                                    'Submit'
                                                )}
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>

                    {/* Footer Links */}
                    <div className="d-flex flex-center flex-wrap fs-6 p-5 pb-0">
                        <div className="d-flex flex-center fw-semibold fs-6">
                            <a
                                href="http://infinevocloud.com/about.php"
                                className="text-muted text-hover-primary px-2"
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                About
                            </a>
                            <a
                                href="http://infinevocloud.com/contact.php"
                                className="text-muted text-hover-primary px-2"
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                Support
                            </a>
                        </div>
                    </div>
                </div>
            </div>

            {/* Loader */}
            {signingIn && <Loader />}
        </>
    );
}