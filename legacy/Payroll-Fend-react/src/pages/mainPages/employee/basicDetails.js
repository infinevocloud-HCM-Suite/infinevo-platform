import React, { useState, useEffect } from "react";
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

import { FaCircle } from "react-icons/fa6"; // solid/outlined circle

const RequiredStar = () => <span className="text-danger">*</span>;

const BasicDetails = ({
    initialValues,
    onPrev,
    onNext
}) => {
    const [signingIn, setSigningIn] = useState(false);
    const [workLocations, setWorkLocations] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [designations, setDesignations] = useState([]);
    const [hrUsers, setHrUsers] = useState([]);
    const [loading, setLoading] = useState({
        locations: false,
        departments: false,
        designations: false,
        hrUsers: false,
    });
    const [hasDepartments, setHasDepartments] = useState(false);
    const [showAddForm, setShowAddForm] = useState(false);
    const [showAddWorklocationForm, setshowAddWorklocationForm] = useState(false);
    const [showAddDesignationForm, setShowAddDesignationForm] = useState(false);
    const [newDesignation, setNewDesignation] = useState({
        name: "",
        code: "",
        description: ""
    });

    // 🔹 NEW: Org-level statutory configuration (EPF / ESI visibility)
    const [statutoryConfig, setStatutoryConfig] = useState({
        epfEnabled: false,
        esiEnabled: false,
    });
    const [isStatutoryLoading, setIsStatutoryLoading] = useState(false);

    const organizationId = localStorage.getItem("organizationId") || "default-org-id";

    // Fetch once when component mounts
    useEffect(() => {
        fetchWorkLocations();
        fetchDepartments();
        fetchDesignations();
        fetchHrUsers();
        fetchOrgStatutoryConfig(); // 🔹 NEW: fetch organization statutory flags
    }, [organizationId]);

    const fetchHrUsers = async () => {
        setLoading(prev => ({ ...prev, hrUsers: true }));
        try {
            const response = await axios.get(`${GlobalConst.API_URL}/auth/hr-users`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                }
            });

            if (response.data && response.data.data) {
                setHrUsers(response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch HR users:", error);
            message.error('Failed to fetch HR users');
        } finally {
            setLoading(prev => ({ ...prev, hrUsers: false }));
        }
    };

    const fetchDepartments = async () => {
        setLoading(prev => ({ ...prev, departments: true }));
        try {
            const response = await axios.get(`${GlobalConst.API_URL}/api/departments`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                }
            });

            if (response.data && response.data.data) {
                setDepartments(response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch departments:", error);
            message.error('Failed to fetch departments');
        } finally {
            setLoading(prev => ({ ...prev, departments: false }));
        }
    };

    const fetchDesignations = async () => {
        setLoading(prev => ({ ...prev, designations: true }));
        try {
            const response = await axios.get(`${GlobalConst.API_URL}/api/designations`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                }
            });

            if (response.data && response.data.data) {
                setDesignations(response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch designations:", error);
            message.error('Failed to fetch designations');
        } finally {
            setLoading(prev => ({ ...prev, designations: false }));
        }
    };

    const fetchWorkLocations = async () => {
        setLoading(prev => ({ ...prev, locations: true }));
        try {
            const response = await axios.get(`${GlobalConst.API_URL}/api/worklocations`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                }
            });

            if (response.data && response.data.data) {
                setWorkLocations(response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch work locations:", error);
            message.error("Failed to fetch work locations");
        } finally {
            setLoading(prev => ({ ...prev, locations: false }));
        }
    };

    // 🔹 NEW: Fetch organization statutory config (EPF / ESI flags)
    const fetchOrgStatutoryConfig = async () => {
        setIsStatutoryLoading(true);
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employees/statutory/config`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response.data && response.data.code === 0 && response.data.statutoryConfig) {
                setStatutoryConfig({
                    epfEnabled: !!response.data.statutoryConfig.epfEnabled,
                    esiEnabled: !!response.data.statutoryConfig.esiEnabled,
                });
            }
        } catch (error) {
            console.error("Failed to fetch organization statutory config:", error);
            message.error("Failed to fetch statutory configuration");
        } finally {
            setIsStatutoryLoading(false);
        }
    };

    const validationSchema = Yup.object().shape({
        firstName: Yup.string().required("First Name is required"),
        lastName: Yup.string().required("Last Name is required"),
        middleName: Yup.string(),
        employeeNumber: Yup.string().required("Employee ID is required"),
        dateOfJoining: Yup.date().required("Date of Joining is required"),

        // -------------------------
        //   WORK EMAIL VALIDATION
        // -------------------------
        workMail: Yup.string()
            .required("Work Email is required")
            .email("Invalid email")
            .matches(
                /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.(com|in|net|org)$/i,
                "Enter a valid corporate email (e.g., abc@xyz.com)"
            ),

        // -------------------------
        //   MOBILE NUMBER RULES
        // -------------------------
        mobile: Yup.string()
            .required("Mobile Number is required")

            // ✅ Only 10 digits
            .matches(/^\d{10}$/, "Enter a valid 10-digit mobile number")

            // ❌ Block sequential numbers
            .test(
                "not-sequential",
                "Sequential numbers are not allowed (e.g., 1234567890)",
                (value) =>
                    !value ||
                    !/(0123456789|1234567890|0987654321|9876543210)/.test(value)
            )

            // ❌ Block same digit repeated 10 times
            .test(
                "not-repeated",
                "Repeated digits are not allowed (e.g., 1111111111)",
                (value) =>
                    !value ||
                    !/^(\d)\1{9}$/.test(value)
            )

            // ❌ Cannot start with 0
            .test(
                "not-start-0",
                "Mobile number cannot start with 0",
                (value) => !value || !/^0/.test(value)
            )

            // ❌ Block obvious fake numbers
            .test(
                "no-fake",
                "Invalid number format",
                (value) =>
                    !value ||
                    !/(0000000000|9999999999)/.test(value)
            ),

        gender: Yup.string().required("Gender is required"),
        WorkLocationName: Yup.string().required("Work Location is required"),
        departmentName: Yup.string().required("Department is required"),
        designationName: Yup.string().required("Designation is required"),
        hrUser: Yup.string(),
        director: Yup.boolean(),

        // ======================================================
        //      🔥 STATUTORY COMPONENTS – STRICT VALIDATION
        // ======================================================
        statutoryComponents: Yup.object().shape({
            // these booleans are used in .when()
            employeesProvidentFund: Yup.boolean(),
            employeesStateInsurance: Yup.boolean(),

            // ----------------------------
            // PF ACCOUNT NUMBER VALIDATION
            // EPFO Official Format:
            // AA/AAA/0000000/000/0000000
            // ----------------------------
            pfAccountNumber: Yup.string()
                .transform((val) =>
                    typeof val === "string" ? val.toUpperCase() : val
                )
                .when("employeesProvidentFund", (epf, schema) =>
                    epf
                        ? schema
                            //   .required("PF Account Number is required")
                            .matches(
                                /^[A-Z]{2}\/[A-Z]{3}\/\d{7}\/\d{3}\/\d{7}$/,
                                "Invalid PF Number format – Use: AA/AAA/0000000/000/0000000"
                            )
                        : schema.notRequired().nullable()
                ),

            // ----------------------------
            // UAN VALIDATION (STRICT)
            // Must be EXACTLY 12 digits
            // (spaces from UI auto-format are stripped)
            // ----------------------------
            uan: Yup.string()
                .transform((val) =>
                    typeof val === "string" ? val.replace(/\s/g, "") : val
                )
                .when("employeesProvidentFund", (epf, schema) =>
                    epf
                        ? schema
                            //   .required("UAN is required")
                            .matches(/^\d{12}$/, "UAN must be exactly 12 digits")
                            .test(
                                "not-seq",
                                "Sequential UAN not allowed",
                                (v) =>
                                    !v ||
                                    !/(012345678901|123456789012)/.test(v)
                            )
                            .test(
                                "not-repeated",
                                "Invalid UAN (repeated digits)",
                                (v) => !v || !/^(\d)\1{11}$/.test(v)
                            )
                        : schema.notRequired().nullable()
                ),

            // ----------------------------
            //  ESI NUMBER VALIDATION
            // Must be EXACTLY 10 digits
            // (spaces from UI auto-format are stripped)
            // ----------------------------
            esiInsuranceNumber: Yup.string()
                .transform((val) =>
                    typeof val === "string" ? val.replace(/\s/g, "") : val
                )
                .when("employeesStateInsurance", (esi, schema) =>
                    esi
                        ? schema
                            //   .required("ESI Insurance Number is required")
                            .matches(
                                /^\d{10}$/,
                                "ESI Number must be exactly 10 digits"
                            )
                        : schema.notRequired().nullable()
                ),
        }),
        // :contentReference[oaicite:0]{index=0}
    });



    //     const handleSubmit = async (values, { setSubmitting }) => {
    //         setSigningIn(true);
    //         try {
    //             const basicDetailsData = {

    //                 employeeNumber: values.employeeNumber,
    //                 firstName: values.firstName,
    //                 middleName: values.middleName,
    //                 lastName: values.lastName,
    //                 gender: values.gender,
    //                 dateOfJoining: values.dateOfJoining,
    //                 director: values.director,
    //                 mobile: values.mobile,
    //                 workMail: values.workMail,
    //                 organizationId: organizationId,
    //                 hrUser: values.hrUser,

    //                 // ✅ IDs from selects - now using the ID fields instead of name fields
    //                 departmentId: values.departmentId || null,
    //                 designationId: values.designationId || null,
    //                 workLocationId: values.workLocationId || null,

    //                 employeeStatus: values.employeeStatus || "Active",
    //                 portalEnabled: values.enablePortalAccess || false,

    //                 eligibleForPf: values.statutoryComponents.employeesProvidentFund || false,
    //                 pfAccountNumber: values.statutoryComponents.pfAccountNumber || null,
    //                 uan: values.statutoryComponents.uan || null,
    //                 eligibleForEps: values.statutoryComponents.employeePensionScheme || false,
    //                 canContributeToEpsOnHigherWages: values.statutoryComponents.epsAtActualPfWages || false,
    //                 eligibleForPt: values.statutoryComponents.professionalTax || false,

    //                 eligibleForEsi: values.statutoryComponents.employeesStateInsurance || false,
    //                 esiInsuranceNumber: values.statutoryComponents.esiInsuranceNumber || null,


    //                 // Optional
    //                 eligibleForLwf: values.statutoryComponents.lwf || false,
    //                 tags: values.tags || [],
    //                 organizationId: organizationId,
    //             };

    //             const response = await axios.post(
    //                 `${GlobalConst.API_URL}/api/employees`,
    //                 basicDetailsData,
    //                 {
    //                     headers: {
    //                         "Content-Type": "application/json",
    //                         Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                         organizationId: organizationId
    //                     }
    //                 }
    //             );

    //             if (response.data && response.data.data?.id) {
    //                 successMsg("Employee basic details saved Successfully", response.data.message || "Employee basic details saved", false);
    //                 localStorage.setItem('currentEmployeeId', response.data.data.id);

    //                 onNext(response.data.data.id); // Pass employee ID to next step
    //             } else {
    //                 errorMsg("Save Failed", "Failed to save employee details", false);
    //             }

    //      } catch (error) {
    //     console.error("Employee Save API Error:", error);

    //     if (error.response && error.response.data) {

    //         const backendMessage =
    //             error.response.data.message ||
    //             error.response.data.error ||
    //             "Failed to save employee details";

    //         errorMsg("Save Failed", backendMessage, false);

    //     } else if (error.request) {

    //         errorMsg(
    //             "Network Error",
    //             "Unable to connect to server. Please check your internet connection.",
    //             false
    //         );

    //     } else {

    //         errorMsg(
    //             "Unexpected Error",
    //             error.message || "Something went wrong",
    //             false
    //         );
    //     }
    // }


    //         finally {
    //             setSigningIn(false);
    //             setSubmitting(false);
    //         }
    //     };




    const handleSubmit = async (values, { setSubmitting }) => {
        setSigningIn(true);
        try {
            const basicDetailsData = {
                employeeNumber: values.employeeNumber,
                firstName: values.firstName,
                middleName: values.middleName,
                lastName: values.lastName,
                gender: values.gender,
                dateOfJoining: values.dateOfJoining,
                director: values.director,
                mobile: values.mobile,
                workMail: values.workMail,
                organizationId: organizationId,
                hrUser: values.hrUser,

                // ✅ IDs from selects - now using the ID fields instead of name fields
                departmentId: values.departmentId || null,
                designationId: values.designationId || null,
                workLocationId: values.workLocationId || null,

                employeeStatus: values.employeeStatus || "Active",
                isPortalEnabled: values.enablePortalAccess || false,

                eligibleForPf: values.statutoryComponents.employeesProvidentFund || false,
                pfAccountNumber: values.statutoryComponents.pfAccountNumber || null,
                uan: values.statutoryComponents.uan || null,
                eligibleForEps: values.statutoryComponents.employeePensionScheme || false,
                canContributeToEpsOnHigherWages: values.statutoryComponents.epsAtActualPfWages || false,
                eligibleForPt: values.statutoryComponents.professionalTax || false,

                eligibleForEsi: values.statutoryComponents.employeesStateInsurance || false,
                esiInsuranceNumber: values.statutoryComponents.esiInsuranceNumber || null,

                // Optional
                eligibleForLwf: values.statutoryComponents.lwf || false,
                tags: values.tags || [],
                organizationId: organizationId,
            };

            const response = await axios.post(
                `${GlobalConst.API_URL}/api/employees`,
                basicDetailsData,
                {
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId
                    }
                }
            );

            if (response.data && response.data.data?.id) {
                successMsg("Employee basic details saved Successfully", response.data.message || "Employee basic details saved", false);
                onNext(response.data.data.id); // Pass employee ID to next step — draft persisted in parent AddEmployee
            } else {
                errorMsg("Save Failed", "Failed to save employee details", false);
            }

        } catch (error) {
            console.error("Employee Save API Error:", error);

            if (error.response && error.response.data) {
                // Extract error message from backend response
                let errorMessage = "Failed to save employee details";

                // Check different possible locations of error message in response
                if (error.response.data.message) {
                    errorMessage = error.response.data.message;
                } else if (error.response.data.error) {
                    errorMessage = error.response.data.error;
                } else if (error.response.data.errors && Array.isArray(error.response.data.errors)) {
                    // Handle validation errors array
                    errorMessage = error.response.data.errors.map(err => err.message || err).join(", ");
                } else if (typeof error.response.data === 'string') {
                    errorMessage = error.response.data;
                }

                // Check for specific error patterns
                if (errorMessage.toLowerCase().includes("email") ||
                    errorMessage.toLowerCase().includes("workmail") ||
                    errorMessage.toLowerCase().includes("already exists")) {
                    errorMsg("Employee already exists with email", errorMessage, false);
                }
                else if (errorMessage.toLowerCase().includes("employee number") ||
                    errorMessage.toLowerCase().includes("employee id") ||
                    errorMessage.toLowerCase().includes("duplicate")) {
                    errorMsg("Duplicate Employee ID", errorMessage, false);
                }
                else {
                    errorMsg("Save Failed", errorMessage, false);
                }

            } else if (error.request) {
                errorMsg(
                    "Network Error",
                    "Unable to connect to server. Please check your internet connection.",
                    false
                );
            } else {
                errorMsg(
                    "Unexpected Error",
                    error.message || "Something went wrong",
                    false
                );
            }
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    };

    return (
        <>
            <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize={true}
            >
                {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                    <Form>

                        {/* Employee Name Section */}
                        <div className="mb-4">
                            <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                                Employee Name<RequiredStar />
                            </label>

                            <div className="row g-3">
                                <div className="col-12 col-md-4">
                                    <Field
                                        type="text"
                                        name="firstName"
                                        placeholder="First Name"
                                        className={`form-control ${errors.firstName && touched.firstName ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage
                                        name="firstName"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>

                                <div className="col-12 col-md-4">
                                    <Field
                                        type="text"
                                        name="middleName"
                                        placeholder="Middle Name"
                                        className={`form-control ${errors.middleName && touched.middleName ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage
                                        name="middleName"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>

                                <div className="col-12 col-md-4">
                                    <Field
                                        type="text"
                                        name="lastName"
                                        placeholder="Last Name"
                                        className={`form-control ${errors.lastName && touched.lastName ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage
                                        name="lastName"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Employee ID and Date of Joining */}
                        <div className="row g-3 mb-4">
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Employee ID<RequiredStar />
                                </label>
                                <Field
                                    type="text"
                                    name="employeeNumber"
                                    placeholder="Employee ID"
                                    className={`form-control ${errors.employeeNumber && touched.employeeNumber ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="employeeNumber"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Date of Joining<RequiredStar />
                                </label>
                                <Field
                                    type="date"
                                    name="dateOfJoining"
                                    placeholder="Date of Joining"
                                    className={`form-control ${errors.dateOfJoining && touched.dateOfJoining ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="dateOfJoining"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                        </div>

                        {/* Work Email and Mobile Number */}
                        <div className="row g-3 mb-4">
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Work Email<RequiredStar />
                                </label>
                                <Field
                                    type="email"
                                    name="workMail"
                                    placeholder="abc@xyz.com"
                                    className={`form-control ${errors.workMail && touched.workMail ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="workMail"
                                    component="div"
                                    className="invalid-feedback"
                                />
                                <div className="text-muted small mt-1" style={{ backgroundColor: "#EEF7FF", padding: "8px", borderRadius: "4px" }}>
                                    You cannot change this Email address later on, as this will be used to send payslips and also for employees to sign in to their portal, where they can view/download their payslips.
                                </div>
                            </div>

                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Mobile Number<RequiredStar />
                                </label>
                                <Field
                                    type="text"
                                    name="mobile"
                                    placeholder="Mobile Number"
                                    maxLength={10}
                                    onInput={(e) => {
                                        e.target.value = e.target.value.replace(/[^0-9+_]/g, ""); // ✅ blocks alphabets in realtime
                                    }}
                                    className={`form-control ${errors.mobile && touched.mobile ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />


                                <ErrorMessage
                                    name="mobile"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                        </div>

                        {/* Director Checkbox */}
                        <div className="mb-4">
                            <div className="form-check form-check-custom form-check-dark mb-3 p-2 rounded">
                                <Field
                                    type="checkbox"
                                    name="director"
                                    id="director"
                                    className="form-check-input"
                                    checked={values.director}   // ✅ will work now
                                    onChange={(e) => setFieldValue("director", e.target.checked)}
                                />
                                <label className="form-check-label" htmlFor="director">
                                    Employee is a Director/person with substantial interest in the company.
                                </label>
                            </div>
                        </div>

                        {/* Gender and Work Location */}
                        <div className="row g-3 mb-4">
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Gender<RequiredStar />
                                </label>
                                <Field
                                    as="select"
                                    name="gender"
                                    className={`form-control ${errors.gender && touched.gender ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                >
                                    <option value="">Select Gender</option>
                                    <option value="Male">Male</option>
                                    <option value="Female">Female</option>
                                    <option value="Other">Other</option>
                                </Field>
                                <ErrorMessage
                                    name="gender"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Work Location<RequiredStar />
                                </label>
                                <Field
                                    as="select"
                                    name="WorkLocationName"
                                    className={`form-control ${errors.WorkLocationName && touched.WorkLocationName ? "is-invalid" : ""}`}
                                    disabled={isSubmitting || loading.locations}
                                    onChange={(e) => {
                                        if (e.target.value === "add_new") {
                                            setshowAddWorklocationForm(true);
                                            e.target.value = "";
                                        } else {
                                            const selectedLocation = workLocations.find(loc => loc.workLocationName === e.target.value);
                                            setFieldValue("WorkLocationName", e.target.value);
                                            setFieldValue("workLocationId", selectedLocation ? selectedLocation.workLocationId : null);
                                        }
                                    }}
                                >
                                    <option value="">Select Work Location</option>
                                    {workLocations.map((location) => (
                                        <option key={location.workLocationId} value={location.workLocationName}>
                                            {location.workLocationName}
                                        </option>
                                    ))}
                                    <option value="add_new">➕ Add Work Location</option>
                                </Field>
                                <ErrorMessage
                                    name="WorkLocationName"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                        </div>

                        {/* Designation and Department */}
                        < div className="row g-3 mb-4" >
                            {/* Designation Dropdown */}
                            < div className="col-12 col-md-6" >
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Designation<RequiredStar />
                                </label>
                                {/* Designation Dropdown */}
                                <Field
                                    as="select"
                                    name="designationName"
                                    className={`form-control ${errors.designationName && touched.designationName ? "is-invalid" : ""}`}
                                    disabled={isSubmitting || loading.designations}
                                    onChange={(e) => {
                                        if (e.target.value === "add_new") {
                                            setShowAddDesignationForm(true);
                                            e.target.value = "";
                                        } else {
                                            const selectedDesignation = designations.find(des => des.name === e.target.value);
                                            setFieldValue("designationName", e.target.value);
                                            setFieldValue("designationId", selectedDesignation ? selectedDesignation.designationId : null);
                                        }
                                    }}
                                >
                                    <option value="">Select Designation</option>
                                    {designations.map((designation) => (
                                        <option key={designation.designationId} value={designation.name}>
                                            {designation.name}
                                        </option>
                                    ))}
                                    <option value="add_new">➕ Add Designation</option>
                                </Field>
                                <ErrorMessage
                                    name="designationName"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div >

                            {/* Department Dropdown */}
                            < div className="col-12 col-md-6" >
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Department<RequiredStar />
                                </label>

                                <Field
                                    as="select"
                                    name="departmentName"
                                    className={`form-control ${errors.departmentName && touched.departmentName ? "is-invalid" : ""}`}
                                    disabled={isSubmitting || loading.departments}
                                    onChange={(e) => {
                                        if (e.target.value === "add_new") {
                                            setShowAddForm(true);
                                            e.target.value = "";
                                        } else {
                                            const selectedDepartment = departments.find(dept => dept.name === e.target.value);
                                            setFieldValue("departmentName", e.target.value);
                                            setFieldValue("departmentId", selectedDepartment ? selectedDepartment.departmentId : null);
                                        }
                                    }}
                                >
                                    <option value="">Select Department</option>
                                    {departments.map((department) => (
                                        <option key={department.departmentId} value={department.name}>
                                            {department.name}
                                        </option>
                                    ))}
                                    <option value="add_new">➕ Add Department</option>
                                </Field>
                                <ErrorMessage
                                    name="departmentName"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div >
                        </div >

                        {/* Reporting HR Field */}
                        <div className="row g-3 mb-4">
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Reporting HR
                                    {/* <RequiredStar /> */}
                                </label>

                                <Field
                                    as="select"
                                    name="hrUser"
                                    className={`form-control ${errors.hrUser && touched.hrUser ? "is-invalid" : ""}`}
                                    disabled={isSubmitting || loading.hrUsers}
                                    onChange={(e) => setFieldValue("hrUser", e.target.value)}
                                >
                                    <option value="">Select Reporting HR</option>
                                    {hrUsers.map((hrUser) => (
                                        <option key={hrUser.userId} value={hrUser.userEmail}>
                                            {hrUser.firstName} {hrUser.lastName} ({hrUser.userEmail})
                                        </option>
                                    ))}
                                </Field>

                                <ErrorMessage
                                    name="hrUser"
                                    component="div"
                                    className="invalid-feedback"
                                />

                                <div className="text-muted small mt-1">
                                    Select the HR responsible for this employee's records.
                                </div>
                            </div>
                        </div>


                        {/* Portal Access Checkbox */}
                        < div className="mb-4" >
                            <div className="form-check form-check-custom form-check-dark mb-3 p-2 rounded">
                                <Field
                                    type="checkbox"
                                    name="enablePortalAccess"
                                    id="enablePortalAccess"
                                    className="form-check-input"
                                    checked={values.enablePortalAccess}
                                    onChange={(e) => setFieldValue('enablePortalAccess', e.target.checked)}
                                />
                                <label className="form-check-label" htmlFor="enablePortalAccess">
                                    Enable Portal Access
                                </label>
                            </div>
                            <div className="text-muted small ps-4">
                                The employee will be able to view payslips, submit their IT declaration and create reimbursement claims through the employee portal.
                            </div>
                        </div >

                        {/* Statutory Components */}
                        < div className="mb-4" >
                            <h6 className="fw-bold mb-2">Statutory Components</h6>
                            <div className="text-muted small mb-3">
                                Enable the necessary benefits and tax applicable for this employee.
                            </div>

                            {/* 🔹 EPF-related items are shown ONLY if org EPF is enabled */}
                            {statutoryConfig.epfEnabled && (
                                <>
                                    {/* Employees' Provident Fund */}
                                    <div className="form-check form-check-custom form-check-dark mb-2">
                                        <Field
                                            type="checkbox"
                                            name="statutoryComponents.employeesProvidentFund"
                                            id="employeesProvidentFund"
                                            className="form-check-input"
                                            checked={values.statutoryComponents.employeesProvidentFund}
                                            disabled={isSubmitting || isStatutoryLoading}
                                            onChange={(e) =>
                                                setFieldValue("statutoryComponents.employeesProvidentFund", e.target.checked)
                                            }
                                        />
                                        <label className="form-check-label" htmlFor="employeesProvidentFund">
                                            Employees' Provident Fund
                                        </label>
                                    </div>

                                    {/* PF Account Number + UAN */}
                                    {
                                        values.statutoryComponents.employeesProvidentFund && (
                                            <div className="row g-3 mb-3">
                                                {/* PF Account Number */}
                                                <div className="col-12 col-md-6">
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        PF Account Number<RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="statutoryComponents.pfAccountNumber"
                                                        placeholder="AA/AAA/0000000/XXX/0000000"
                                                        maxLength={30}
                                                        onInput={(e) => {
                                                            e.target.value = e.target.value
                                                                .toUpperCase()
                                                                .replace(/[^A-Z0-9/]/g, ""); // ✅ block all other chars
                                                        }}
                                                        className={`form-control ${errors.statutoryComponents?.pfAccountNumber && touched.statutoryComponents?.pfAccountNumber
                                                            ? "is-invalid"
                                                            : ""
                                                            }`}
                                                        disabled={isSubmitting}
                                                    />
                                                    <ErrorMessage
                                                        name="statutoryComponents.pfAccountNumber"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                    <div className="text-muted small mt-1">
                                                        Format: AA/AAA/0000000/XXX/0000000
                                                    </div>
                                                </div>

                                                {/* UAN */}
                                                <div className="col-12 col-md-6">
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        UAN<RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="statutoryComponents.uan"
                                                        placeholder="000000000000"
                                                        maxLength={12}
                                                        onInput={(e) => {
                                                            e.target.value = e.target.value.replace(/[^0-9]/g, ""); // ✅ allow only digits
                                                        }}
                                                        className={`form-control ${errors.statutoryComponents?.uan && touched.statutoryComponents?.uan
                                                            ? "is-invalid"
                                                            : ""
                                                            }`}
                                                        disabled={isSubmitting}
                                                    />
                                                    <ErrorMessage
                                                        name="statutoryComponents.uan"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                </div>
                                            </div>
                                        )
                                    }

                                    {/* Contribute to Employee Pension Scheme */}
                                    {
                                        values.statutoryComponents.employeesProvidentFund && (
                                            <div className="form-check form-check-custom form-check-dark mb-2 ms-3">
                                                <Field
                                                    type="checkbox"
                                                    name="statutoryComponents.employeePensionScheme"
                                                    id="employeePensionScheme"
                                                    className="form-check-input"
                                                    checked={values.statutoryComponents.employeePensionScheme}
                                                    onChange={(e) =>
                                                        setFieldValue("statutoryComponents.employeePensionScheme", e.target.checked)
                                                    }
                                                />
                                                <label className="form-check-label" htmlFor="employeePensionScheme">
                                                    Contribute to Employee Pension Scheme
                                                    <i className="bi bi-info-circle ms-1" title="Details about EPS"></i>
                                                </label>
                                            </div>
                                        )
                                    }

                                    {/* Contribute EPS at actual PF Wages */}
                                    {
                                        values.statutoryComponents.employeePensionScheme && (
                                            <div className="form-check form-check-custom form-check-dark mb-2 ms-5">
                                                <Field
                                                    type="checkbox"
                                                    name="statutoryComponents.epsAtActualPfWages"
                                                    id="epsAtActualPfWages"
                                                    className="form-check-input"
                                                    checked={values.statutoryComponents.epsAtActualPfWages}
                                                    onChange={(e) =>
                                                        setFieldValue("statutoryComponents.epsAtActualPfWages", e.target.checked)
                                                    }
                                                />
                                                <label className="form-check-label" htmlFor="epsAtActualPfWages">
                                                    Contribute EPS at actual PF Wages
                                                    <i className="bi bi-info-circle ms-1" title="Details about EPS at PF Wages"></i>
                                                </label>
                                            </div>
                                        )
                                    }
                                </>
                            )}

                            {/* 🔹 ESI-related items are shown ONLY if org ESI is enabled */}
                            {statutoryConfig.esiEnabled && (
                                <>
                                    {/* Employees' State Insurance */}
                                    <div className="form-check form-check-custom form-check-dark mb-2">
                                        <Field
                                            type="checkbox"
                                            name="statutoryComponents.employeesStateInsurance"
                                            id="employeesStateInsurance"
                                            className="form-check-input"
                                            checked={values.statutoryComponents.employeesStateInsurance}
                                            disabled={isSubmitting || isStatutoryLoading}
                                            onChange={(e) =>
                                                setFieldValue("statutoryComponents.employeesStateInsurance", e.target.checked)
                                            }
                                        />
                                        <label className="form-check-label" htmlFor="employeesStateInsurance">
                                            Employees' State Insurance
                                        </label>
                                    </div>

                                    {/* ESI Insurance Number */}
                                    {
                                        values.statutoryComponents.employeesStateInsurance && (
                                            <div className="row g-3 mb-3">
                                                <div className="col-12 col-md-6">
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        ESI Insurance Number<RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="statutoryComponents.esiInsuranceNumber"
                                                        placeholder="0000000000"
                                                        className={`form-control ${errors.statutoryComponents?.esiInsuranceNumber && touched.statutoryComponents?.esiInsuranceNumber ? "is-invalid" : ""}`}
                                                        disabled={isSubmitting}
                                                        maxLength={10}
                                                    />
                                                    <ErrorMessage
                                                        name="statutoryComponents.esiInsuranceNumber"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                    <div className="text-muted small mt-1">
                                                        Format: 10 digits (0000000000)
                                                    </div>
                                                </div>
                                            </div>
                                        )
                                    }
                                </>
                            )}

                            {/* Professional Tax */}
                            <div className="form-check form-check-custom form-check-dark mb-2">
                                <Field
                                    type="checkbox"
                                    name="statutoryComponents.professionalTax"
                                    id="professionalTax"
                                    className="form-check-input"
                                    checked={values.statutoryComponents.professionalTax}
                                    onChange={(e) =>
                                        setFieldValue("statutoryComponents.professionalTax", e.target.checked)
                                    }
                                />
                                <label className="form-check-label" htmlFor="professionalTax">
                                    Professional Tax
                                </label>
                            </div>
                        </div >

                        {/* Navigation buttons */}
                        <div className="d-flex justify-content-between mt-5">
                            {/* Cancel button */}
                            <button
                                type="button"
                                className="btn btn-light"
                                onClick={() => window.history.back()} // or useNavigate("/employees")
                            >
                                Cancel
                            </button>

                            <div>
                                {/* Previous button (only if not first step) */}
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

                                {/* Save & Continue OR Submit */}
                                {typeof onNext === "function" ? (
                                    <Button
                                        type="primary"
                                        htmlType="submit" // Formik will trigger handleSubmit
                                        disabled={isSubmitting}
                                    >
                                        {isSubmitting ? (
                                            <span className="spinner-border spinner-border-sm me-2"></span>
                                        ) : null}
                                        Save and Continue
                                    </Button>
                                ) : (
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
                                )}
                            </div>
                        </div>

                    </Form>
                )}
            </Formik>

            {/* Add Designation Modal */}
            {showAddDesignationForm && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: "rgba(0, 0, 0, 0.7)" }}
                >
                    <div className="modal-dialog modal-dialog-centered modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">New Designation</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => {
                                        setShowAddDesignationForm(false);
                                    }}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{
                                    name: "",
                                    description: "",
                                }}
                                validationSchema={Yup.object({
                                    name: Yup.string().required("Designation Name is required"),
                                    description: Yup.string(),
                                })}
                                onSubmit={async (values, { setSubmitting, resetForm }) => {
                                    console.log("Submitting Designation form");
                                    setSigningIn(true);

                                    try {
                                        const postData = {
                                            name: values.name,
                                            description: values.description,
                                        };

                                        const response = await axios.post(
                                            `${GlobalConst.API_URL}/api/designations`,
                                            postData,
                                            {
                                                headers: {
                                                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                                    organizationId: organizationId,
                                                },
                                            }
                                        );

                                        console.log("Designation API Response:", response);

                                        if (response.data.status === 200 || response.data.status === 201) {
                                            successMsg("Success", "Designation created successfully", false);

                                            // refresh dropdown list
                                            await fetchDesignations();

                                            // reset and close modal
                                            resetForm();
                                            setShowAddDesignationForm(false);
                                        } else {
                                            errorMsg(
                                                "Save Failed",
                                                `There was an error saving the designation. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                                                true
                                            );
                                        }
                                    } catch (error) {
                                        console.log("Designation API Error:", error);

                                        if (error.response) {
                                            errorMsg(
                                                "Creation Failed",
                                                error.response.data?.message || "Designation creation failed",
                                                false
                                            );
                                        } else if (error.request) {
                                            errorMsg(
                                                "Network Error",
                                                "Cannot connect to the server. Please check your connection.",
                                                false
                                            );
                                        } else {
                                            errorMsg("Error", "An unexpected error occurred", false);
                                        }
                                    } finally {
                                        setSigningIn(false);
                                        setSubmitting(false);
                                    }
                                }}
                            >
                                {({ isSubmitting, errors, touched }) => (
                                    <Form className="form w-100">
                                        <div className="modal-body">
                                            {/* Designation Name */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Designation Name <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="name"
                                                    className={`form-control form-control-lg form-control-solid ${errors.name && touched.name ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter designation name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="name"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* Description */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Description
                                                </label>
                                                <Field
                                                    as="textarea"
                                                    name="description"
                                                    rows="3"
                                                    className={`form-control form-control-lg form-control-solid ${errors.description && touched.description ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter designation description"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="description"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>
                                        </div>

                                        {/* Modal Footer */}
                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => {
                                                    setShowAddDesignationForm(false);
                                                }}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? (
                                                    <span className="spinner-border spinner-border-sm me-1"></span>
                                                ) : (
                                                    "Save"
                                                )}
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>
                </div>
            )}


            {/* Add Department Modal */}
            {showAddForm && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: "rgba(0, 0, 0, 0.7)" }}
                >
                    <div className="modal-dialog modal-dialog-centered modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">New Department</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => {
                                        setShowAddForm(false);
                                    }}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{
                                    name: "",
                                    departmentCode: "",
                                    description: "",
                                }}
                                validationSchema={Yup.object({
                                    name: Yup.string().required("Department Name is required"),
                                    departmentCode: Yup.string().required("Department Code is required"),
                                    description: Yup.string(),
                                })}
                                onSubmit={async (values, { setSubmitting, resetForm }) => {
                                    console.log("Submitting Department form");
                                    setSigningIn(true);

                                    try {
                                        const postData = {
                                            name: values.name,
                                            departmentCode: values.departmentCode,
                                            description: values.description,
                                        };

                                        const response = await axios.post(
                                            `${GlobalConst.API_URL}/api/departments`,
                                            postData,
                                            {
                                                headers: {
                                                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                                    organizationId: organizationId,
                                                },
                                            }
                                        );

                                        console.log("Department API Response:", response);

                                        if (response.data.status === 200 || response.data.status === 201) {
                                            successMsg(
                                                "Success",
                                                "Department created successfully",
                                                false
                                            );

                                            // refresh dropdown list
                                            await fetchDepartments();

                                            // reset and close modal
                                            resetForm();
                                            setShowAddForm(false);
                                        } else {
                                            errorMsg(
                                                "Save Failed",
                                                `There was an error saving the department. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                                                true
                                            );
                                        }
                                    } catch (error) {
                                        console.log("Department API Error:", error);

                                        if (error.response) {
                                            errorMsg(
                                                "Creation Failed",
                                                error.response.data?.message || "Department creation failed",
                                                false
                                            );
                                        } else if (error.request) {
                                            errorMsg(
                                                "Network Error",
                                                "Cannot connect to the server. Please check your connection.",
                                                false
                                            );
                                        } else {
                                            errorMsg("Error", "An unexpected error occurred", false);
                                        }
                                    } finally {
                                        setSigningIn(false);
                                        setSubmitting(false);
                                    }
                                }}
                            >
                                {({ isSubmitting, errors, touched }) => (
                                    <Form className="form w-100">
                                        <div className="modal-body">
                                            {/* Department Name */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Department Name <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="name"
                                                    className={`form-control form-control-lg form-control-solid ${errors.name && touched.name ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter department name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="name"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* Department Code */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Department Code <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="departmentCode"
                                                    className={`form-control form-control-lg form-control-solid ${errors.departmentCode && touched.departmentCode
                                                        ? "is-invalid"
                                                        : ""
                                                        }`}
                                                    placeholder="Enter department code"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="departmentCode"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* Description */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Description
                                                </label>
                                                <Field
                                                    as="textarea"
                                                    name="description"
                                                    rows="3"
                                                    className={`form-control form-control-lg form-control-solid ${errors.description && touched.description
                                                        ? "is-invalid"
                                                        : ""
                                                        }`}
                                                    placeholder="Enter department description"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="description"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>
                                        </div>

                                        {/* Modal Footer */}
                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => {
                                                    setShowAddForm(false);
                                                }}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? (
                                                    <span className="spinner-border spinner-border-sm me-1"></span>
                                                ) : (
                                                    "Save"
                                                )}
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>
                </div>
            )}


            {/* worklocation add form */}
            {showAddWorklocationForm && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: "rgba(0, 0, 0, 0.7)" }}
                >
                    <div className="modal-dialog modal-dialog-centered modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">New Work Location</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => {
                                        setshowAddWorklocationForm(false);
                                    }}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{
                                    workLocationName: "",
                                    streetAddress: "",
                                    city: "",
                                    state: "",
                                    pincode: "",
                                    country: "India",
                                }}
                                validationSchema={Yup.object({
                                    workLocationName: Yup.string().required("Work Location Name is required"),
                                    streetAddress: Yup.string().required("Street Address is required"),
                                    city: Yup.string().required("City is required"),
                                    state: Yup.string().required("State is required"),
                                    pincode: Yup.string()
                                        .matches(/^[1-9][0-9]{5}$/, "Invalid PIN code")
                                        .required("PIN Code is required"),
                                })}
                                onSubmit={async (values, { setSubmitting, resetForm }) => {
                                    console.log("Submitting Work Location form");
                                    setSigningIn(true);

                                    try {
                                        const postData = {
                                            workLocationName: values.workLocationName,
                                            streetAddress: values.streetAddress,
                                            city: values.city,
                                            state: values.state,
                                            pincode: values.pincode,
                                            country: values.country,
                                        };

                                        const response = await axios.post(
                                            `${GlobalConst.API_URL}/api/worklocations`,
                                            postData,
                                            {
                                                headers: {
                                                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                                    organizationId: organizationId,
                                                },
                                            }
                                        );

                                        console.log("Work Location API Response:", response);

                                        if (response.data.status === 200 || response.data.status === 201) {
                                            successMsg(
                                                "Success",
                                                "Work Location created successfully",
                                                false
                                            );

                                            // refresh dropdown list
                                            await fetchWorkLocations();

                                            // reset and close modal
                                            resetForm();
                                            setshowAddWorklocationForm(false);
                                        } else {
                                            errorMsg(
                                                "Save Failed",
                                                `There was an error saving the work location. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                                                true
                                            );
                                        }
                                    } catch (error) {
                                        console.log("Work Location API Error:", error);

                                        if (error.response) {
                                            errorMsg(
                                                "Creation Failed",
                                                error.response.data?.message || "Work Location creation failed",
                                                false
                                            );
                                        } else if (error.request) {
                                            errorMsg(
                                                "Network Error",
                                                "Cannot connect to the server. Please check your connection.",
                                                false
                                            );
                                        } else {
                                            errorMsg("Error", "An unexpected error occurred", false);
                                        }
                                    } finally {
                                        setSigningIn(false);
                                        setSubmitting(false);
                                    }
                                }}
                            >
                                {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                                    <Form className="form w-100">
                                        <div className="modal-body">
                                            {/* Work Location Name */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Work Location Name <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="workLocationName"
                                                    className={`form-control form-control-lg form-control-solid ${errors.workLocationName && touched.workLocationName
                                                        ? "is-invalid"
                                                        : ""
                                                        }`}
                                                    placeholder="Enter work location name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="workLocationName"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* Street Address */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Street Address <RequiredStar />
                                                </label>
                                                <Field
                                                    as="textarea"
                                                    name="streetAddress"
                                                    rows="3"
                                                    className={`form-control form-control-lg form-control-solid ${errors.streetAddress && touched.streetAddress
                                                        ? "is-invalid"
                                                        : ""
                                                        }`}
                                                    placeholder="Enter street address"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="streetAddress"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* City */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    City <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="city"
                                                    className={`form-control form-control-lg form-control-solid ${errors.city && touched.city ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter city"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="city"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* State */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    State <RequiredStar />
                                                </label>
                                                <Field
                                                    as="select"
                                                    name="state"
                                                    className={`form-control form-control-lg form-control-solid ${errors.state && touched.state ? "is-invalid" : ""
                                                        }`}
                                                    disabled={isSubmitting}
                                                >
                                                    <option value="">Select State</option>
                                                    {getStates().map((state) => (
                                                        <option key={state} value={state}>
                                                            {state}
                                                        </option>
                                                    ))}
                                                </Field>
                                                <ErrorMessage
                                                    name="state"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* PIN Code */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    PIN Code <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="pincode"
                                                    className={`form-control form-control-lg form-control-solid ${errors.pincode && touched.pincode ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter PIN code"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage
                                                    name="pincode"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>

                                            {/* Country */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Country
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="country"
                                                    className="form-control form-control-lg form-control-solid"
                                                    value="India"
                                                    disabled
                                                />
                                            </div>
                                        </div>

                                        {/* Modal Footer */}
                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => {
                                                    setshowAddWorklocationForm(false);
                                                }}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? (
                                                    <span className="spinner-border spinner-border-sm me-1"></span>
                                                ) : (
                                                    "Save"
                                                )}
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>
                </div>
            )}

            {signingIn && <Loader />}
        </>
    );
};

export default BasicDetails;
