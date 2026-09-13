import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import { format } from "date-fns";
import { Button, message, Steps, theme, Select, DatePicker, Checkbox } from 'antd';
import { useNavigate } from "react-router-dom";
import { SearchOutlined, PlusOutlined, CloseOutlined } from '@ant-design/icons';
import { getStates } from "../../../shared/appConfig/stateList";
import { FaUniversity, FaMoneyCheckAlt, FaRegMoneyBillAlt, FaCashRegister, FaCheckCircle } from "react-icons/fa";
import { FaCircle } from "react-icons/fa6";

const RequiredStar = () => <span className="text-danger">*</span>;

const PaymentInformation = ({ initialValues, onSubmit, onPrev, employeeId }) => {
    const [signingIn, setSigningIn] = useState(false);
    const [existingBankDetails, setExistingBankDetails] = useState(null);
    const organizationId = localStorage.getItem("organizationId") || "default-org-id";

    // Fetch existing bank details if employeeId is provided
    // useEffect(() => {
    //     if (employeeId) {
    //         fetchBankDetails();
    //     }
    // }, [employeeId]);

    // const fetchBankDetails = async () => {
    //     try {
    //         const response = await axios.get(
    //             `${GlobalConst.API_URL}/api/v1/employees/bank-details/${employeeId}`,
    //             {
    //                 headers: {
    //                     Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                     organizationId: organizationId
    //                 }
    //             }
    //         );

    //         if (response.data && response.data.data) {
    //             setExistingBankDetails(response.data.data);
    //             // You might want to pre-fill form values here if needed
    //         }
    //     } catch (error) {
    //         console.error("Failed to fetch bank details:", error);
    //         // It's okay if no bank details exist yet
    //     }
    // };

    const validationSchema = Yup.object().shape({
        // Payment Mode must be selected (Bank Transfer / Cheque / Cash)
         paymentMode: Yup.string()
        .required("Payment Mode is required")
        .oneOf(["banktransfer", "check", "cash"], "Invalid payment mode"),

        accountHolderName: Yup.string()
            .trim()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .required("Account Holder Name is required")
                        .matches(/^[A-Za-z .]+$/, "Only alphabets, spaces and dot (.) are allowed")
                        .min(3, "Account Holder Name must be at least 3 characters")
                        .max(100, "Account Holder Name cannot exceed 100 characters")
                    : schema.nullable();
            }),

        bankName: Yup.string()
            .trim()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .required("Bank Name is required")
                        .matches(/^[A-Za-z0-9 &().-]+$/, "Bank Name contains invalid characters")
                        .min(3, "Bank Name must be at least 3 characters")
                        .max(100, "Bank Name cannot exceed 100 characters")
                    : schema.nullable();
            }),

        bankAccountNumber: Yup.string()
            .trim()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .required("Account Number is required")
                        .matches(/^\d{9,18}$/, "Account Number must be 9–18 digits")
                        .test(
                            "not-all-same",
                            "Invalid Account Number (repeated digits)",
                            (value) => {
                                if (!value) return true;
                                return !/^(\d)\1{8,17}$/.test(value);
                            }
                        )
                    : schema.nullable();
            }),

        confirmAccountNumber: Yup.string()
            .trim()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .required("Please confirm account number")
                        .oneOf(
                            [Yup.ref('bankAccountNumber'), null],
                            'Account numbers must match'
                        )
                    : schema.nullable();
            }),

        ifscCode: Yup.string()
            .trim()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .transform((val) => (val ? val.toUpperCase() : val))
                        .required("IFSC Code is required")
                        .matches(
                            /^[A-Z]{4}0[A-Z0-9]{6}$/,
                            "Invalid IFSC Code format (e.g., HDFC0001234)"
                        )
                    : schema.nullable();
            }),

        bankAccountType: Yup.string()
            .when('paymentMode', (paymentMode, schema) => {
                return paymentMode === 'banktransfer'
                    ? schema
                        .required("Account Type is required")
                        .oneOf(["current", "savings"], "Select a valid account type")
                    : schema.nullable();
            }),
    });

    const handleSubmit = async (values, { setSubmitting }) => {
        setSigningIn(true);
        try {
            // Prepare data according to DTO structure
            const bankDetailsData = {
                employeeId: employeeId,
                paymentMode: values.paymentMode,
                accountHolderName: values.paymentMode === "banktransfer" ? values.accountHolderName?.trim() : null,
                bankName: values.paymentMode === "banktransfer" ? values.bankName?.trim() : null,
                bankAccountNumber: values.paymentMode === "banktransfer" ? values.bankAccountNumber?.trim() : null,
                ifscCode: values.paymentMode === "banktransfer" ? values.ifscCode?.toUpperCase().trim() : null,
                bankAccountType: values.paymentMode === "banktransfer" ? values.bankAccountType : null,
            };

            let response;
            if (existingBankDetails) {
                // Update existing bank details
                response = await axios.put(
                    `${GlobalConst.API_URL}/api/v1/employees/bank-details/${employeeId}`,
                    bankDetailsData,
                    {
                        headers: {
                            "Content-Type": "application/json",
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        }
                    }
                );
            } else {
                // Create new bank details
                response = await axios.post(
                    `${GlobalConst.API_URL}/api/v1/employees/bank-details`,
                    bankDetailsData,
                    {
                        headers: {
                            "Content-Type": "application/json",
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        }
                    }
                );
            }

            if (response.data && response.data.status >= 200 && response.data.status < 300) {
                successMsg("Success", response.data.message || "Bank details saved successfully", false);
                onSubmit(values); // Submit final form
            } else {
                errorMsg("Save Failed", "Failed to save bank details", false);
            }
        } catch (error) {
            console.error("API Error:", error);
            if (error.response) {
                errorMsg("Save Failed", error.response.data?.message || "Failed to save bank details", false);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
            } else {
                errorMsg("Error", "An unexpected error occurred", false);
            }
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    };

    return (
        <Formik
            initialValues={{
                paymentMode: existingBankDetails?.paymentMode || "",
                accountHolderName: existingBankDetails?.accountHolderName || "",
                bankName: existingBankDetails?.bankName || "",
                bankAccountNumber: existingBankDetails?.bankAccountNumber || "",
                confirmAccountNumber: "",
                ifscCode: existingBankDetails?.ifscCode || "",
                bankAccountType: existingBankDetails?.bankAccountType || "",
                ...initialValues
            }}
            validationSchema={validationSchema}
            onSubmit={handleSubmit}
            enableReinitialize={true}
        >
            {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                <Form>
                    <div className="mb-4">
                        <div className="row g-3 mb-4">
                            <div className="col-12">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                                    How would you like to pay this employee? <RequiredStar />
                                </label>

                                {/* Show validation error for paymentMode */}
                                <ErrorMessage
                                    name="paymentMode"
                                    component="div"
                                    className="text-danger small mb-2"
                                />

                                <div className="border-top border-gray-200">
                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer border-bottom border-gray-200"
                                        onClick={() => setFieldValue("paymentMode", "banktransfer")}
                                    >
                                        <div className="d-flex align-items-start">
                                            <FaMoneyCheckAlt className="fs-3 text-primary me-4" />
                                            <div>
                                                <div className="fw-bold fs-6 text-gray-900">Bank Transfer (Manual Process)</div>
                                                <div className="text-muted fs-7">
                                                    Download Bank Advice and process the payment through your bank's website
                                                </div>
                                            </div>
                                        </div>
                                        {values.paymentMode === "banktransfer" ? (
                                            <FaCheckCircle className="text-primary fs-2" />
                                        ) : (
                                            <FaCircle className="text-gray-400 fs-2" />
                                        )}
                                    </div>
                                    {values.paymentMode === "banktransfer" && (
                                        <div className="px-4 pb-4 border-bottom border-gray-200">
                                            <label className="form-label fw-semibold">Bank Account Details</label>
                                            <div className="mb-4">
                                                <div className="row g-3 mb-4">
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        Account Holder Name <RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="accountHolderName"
                                                        placeholder="Enter Account Holder Name"
                                                        className={`form-control ${errors.accountHolderName && touched.accountHolderName ? "is-invalid" : ""}`}
                                                        disabled={isSubmitting}
                                                        onInput={(e) => {
                                                            e.target.value = e.target.value
                                                                .replace(/[^A-Za-z .]/g, "")
                                                                .toUpperCase();
                                                        }}
                                                    />
                                                    <ErrorMessage
                                                        name="accountHolderName"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                </div>
                                                <div className="row g-3 mb-4">
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        Bank Name <RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="bankName"
                                                        placeholder="Enter Bank Name"
                                                        className={`form-control ${errors.bankName && touched.bankName ? "is-invalid" : ""}`}
                                                        disabled={isSubmitting}
                                                        onInput={(e) => {
                                                            e.target.value = e.target.value
                                                                .replace(/[^A-Za-z0-9 &().-]/g, "")
                                                                .toUpperCase();
                                                        }}
                                                    />
                                                    <ErrorMessage
                                                        name="bankName"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                </div>
                                                <div className="row g-3 mb-4">
                                                    <div className="col-12 col-md-6">
                                                        <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                            Account Number <RequiredStar />
                                                        </label>
                                                        <Field
                                                            type="text"
                                                            name="bankAccountNumber"
                                                            placeholder="Enter Account Number"
                                                            className={`form-control ${errors.bankAccountNumber && touched.bankAccountNumber ? "is-invalid" : ""}`}
                                                            disabled={isSubmitting}
                                                            maxLength={18}
                                                            onInput={(e) => {
                                                                e.target.value = e.target.value.replace(/\D/g, "");
                                                            }}
                                                        />
                                                        <ErrorMessage
                                                            name="bankAccountNumber"
                                                            component="div"
                                                            className="invalid-feedback"
                                                        />
                                                    </div>

                                                    <div className="col-12 col-md-6">
                                                        <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                            Re-enter Account Number <RequiredStar />
                                                        </label>
                                                        <Field
                                                            type="text"
                                                            name="confirmAccountNumber"
                                                            placeholder="Re-enter Account Number"
                                                            className={`form-control ${errors.confirmAccountNumber && touched.confirmAccountNumber ? "is-invalid" : ""}`}
                                                            disabled={isSubmitting}
                                                            maxLength={18}
                                                            onInput={(e) => {
                                                                e.target.value = e.target.value.replace(/\D/g, "");
                                                            }}
                                                        />
                                                        <ErrorMessage
                                                            name="confirmAccountNumber"
                                                            component="div"
                                                            className="invalid-feedback"
                                                        />
                                                    </div>

                                                    <div className="row g-3 mb-4">
                                                        <div className="col-12 col-md-6">
                                                            <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                                IFSC <RequiredStar />
                                                            </label>
                                                            <Field
                                                                type="text"
                                                                name="ifscCode"
                                                                placeholder="AAAA0000000"
                                                                className={`form-control ${errors.ifscCode && touched.ifscCode ? "is-invalid" : ""}`}
                                                                disabled={isSubmitting}
                                                                maxLength={11}
                                                                onInput={(e) => {
                                                                    e.target.value = e.target.value
                                                                        .toUpperCase()
                                                                        .replace(/[^A-Z0-9]/g, "");
                                                                }}
                                                            />
                                                            <ErrorMessage
                                                                name="ifscCode"
                                                                component="div"
                                                                className="invalid-feedback"
                                                            />
                                                        </div>

                                                        <div className="col-12 col-md-6">
                                                            <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                                Account Type <RequiredStar />
                                                            </label>
                                                            <div className="d-flex align-items-center mt-2">
                                                                <div className="form-check me-4">
                                                                    <Field
                                                                        type="radio"
                                                                        name="bankAccountType"
                                                                        value="current"
                                                                        id="accountTypeCurrent"
                                                                        className="form-check-input"
                                                                        disabled={isSubmitting}
                                                                    />
                                                                    <label htmlFor="accountTypeCurrent" className="form-check-label">
                                                                        Current
                                                                    </label>
                                                                </div>

                                                                <div className="form-check">
                                                                    <Field
                                                                        type="radio"
                                                                        name="bankAccountType"
                                                                        value="savings"
                                                                        id="accountTypeSavings"
                                                                        className="form-check-input"
                                                                        disabled={isSubmitting}
                                                                    />
                                                                    <label htmlFor="accountTypeSavings" className="form-check-label">
                                                                        Savings
                                                                    </label>
                                                                </div>
                                                            </div>
                                                            <ErrorMessage
                                                                name="bankAccountType"
                                                                component="div"
                                                                className="invalid-feedback"
                                                            />
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer border-bottom border-gray-200"
                                        onClick={() => setFieldValue("paymentMode", "check")}
                                    >
                                        <div className="d-flex align-items-center">
                                            <FaRegMoneyBillAlt className="fs-3 text-primary me-4" />
                                            <div className="fw-bold fs-6 text-gray-900">Cheque</div>
                                        </div>
                                        {values.paymentMode === "check" ? (
                                            <FaCheckCircle className="text-primary fs-2" />
                                        ) : (
                                            <FaCircle className="text-gray-400 fs-2" />
                                        )}
                                    </div>

                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer"
                                        onClick={() => setFieldValue("paymentMode", "cash")}
                                    >
                                        <div className="d-flex align-items-center">
                                            <FaCashRegister className="fs-3 text-primary me-4" />
                                            <div className="fw-bold fs-6 text-gray-900">Cash</div>
                                        </div>
                                        {values.paymentMode === "cash" ? (
                                            <FaCheckCircle className="text-primary fs-2" />
                                        ) : (
                                            <FaCircle className="text-gray-400 fs-2" />
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="d-flex justify-content-between mt-5">
                        <button
                            type="button"
                            className="btn btn-light"
                            onClick={() => window.history.back()}
                        >
                            Cancel
                        </button>

                        <div>
                            {typeof onPrev === "function" && (
                                <Button
                                    style={{ marginRight: 8 }}
                                    onClick={onPrev}
                                    type="button"
                                    className="btn btn-light"
                                >
                                    Previous
                                </Button>
                            )}

                            <Button
                                type="primary"
                                htmlType="submit"
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? (
                                    <span className="spinner-border spinner-border-sm me-2"></span>
                                ) : null}
                                Submit
                            </Button>
                        </div>
                    </div>
                </Form>
            )}
        </Formik>
    );
};

export default PaymentInformation;
