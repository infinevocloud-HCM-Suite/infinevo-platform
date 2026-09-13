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

import { FaCircle } from "react-icons/fa6"; // solid/outlined circle


const { Option } = Select;

const RequiredStar = () => <span className="text-danger">*</span>;

export default function AddEmployee() {
    const dispatch = useDispatch();
    const navigate = useNavigate();

    const [signingIn, setSigningIn] = useState(false);
    const [workLocations, setWorkLocations] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [designations, setDesignations] = useState([]);
    const [loading, setLoading] = useState({
        locations: false,
        departments: false,
        designations: false,
    });

    const [employeeId, setEmployeeId] = useState(null); // Store created employee ID


    const [hasDepartments, setHasDepartments] = useState(false);
    const [showAddForm, setShowAddForm] = useState(false);
    const [showAddWorklocationForm, setshowAddWorklocationForm] = useState(false);
    const [showAddDesignationForm, setShowAddDesignationForm] = useState(false);
    const [newDesignation, setNewDesignation] = useState({
        name: "",
        code: "",
        description: ""
    });

    const organizationId = localStorage.getItem("organizationId") || "default-org-id";



    const [initialValues, setInitialValues] = useState({
        employeeId: "",
        employeeNumber: "",
        firstName: "",
        middleName: "",
        lastName: "",
        gender: "",
        dateOfJoining: "",
        departmentId: "",
        designationId: "",
        workLocationId: "",
        employeeStatus: "",
        portalEnabled: false,
        eligibleForPf: false,
        eligibleForPt: false,
        eligibleForLwf: false,
        eligibleForEsi: false,
        director: false,
        eligibleForEps: false,
        canContributeToEpsOnHigherWages: false,
        mobile: "",
        workMail: "",
        pfAccountNumber: "",
        uan: "",
        tags: [],
        amountInPercentage: "",
        statutoryComponents: {
            professionalTax: false,
            pfAccountNumber: "",
            uan: "",
            employeesProvidentFund: false,
            employeePensionScheme: false,
            epsAtActualPfWages: false
        },
        salaryDetails: {
            annualCTC: 0,
            basicPercent: 50,
            hraPercent: 50,
            conveyance: 0,
            basic: 0,
            hra: 0,
            fixedAllowance: 0,
            monthlyCTC: 0,
            annualCTCComputed: 0,
        },
        // salaryDetails: {
        //     annualCTC: "",
        //     annualCTCComputed: 0,
        //     monthlyCTC: 0,
        //     basicPercent: "",
        //     basic: 0,
        //     basicMonthly: 0,
        //     hraPercent: "",
        //     hra: 0,
        //     hraMonthly: 0,
        //     conveyanceMonthly: 0,
        //     conveyanceDisplay: 0,
        //     fixedAllowance: 0,
        //     fixedMonthly: 0,
        // },
        dateOfBirth: "",
        fatherName: "",
        panNumber: "",
        differentlyAbledType: "",
        personalEmail: "",
        residentialAddress: {
            addressLine1: "",
            addressLine2: "",
            city: "",
            state: "",
            pincode: "",
            age: ""
        },
        paymentMode: "",             // directDeposit | bankTransfer | cheque | cash
        accountHolderName: "",
        bankName: "",
        accountNumber: "",
        confirmAccountNumber: "",
        ifsc: "",
        accountType: "",
    });


    const calculateSalaryDetails = (sd, setFieldValue) => {
        const annualCTC = Number(sd.annualCTC) || 0;
        const basicPct = Number(sd.basicPercent) || 0;
        const hraPct = Number(sd.hraPercent) || 0;
        const convAnnual = Number(sd.conveyance) || 0;

        // Annual (raw)
        const basicAnnualRaw = (annualCTC * basicPct) / 100;
        const hraAnnualRaw = (basicAnnualRaw * hraPct) / 100;

        // Monthly CTC rounded (to match your expected output)
        const monthlyCTC = Math.round(annualCTC / 12);

        // Per-component monthly (round up for Basic & HRA like in your example)
        const basicMonthly = Math.ceil(basicAnnualRaw / 12);
        const hraMonthly = Math.ceil(hraAnnualRaw / 12);
        const convMonthly = Math.ceil(convAnnual / 12);

        // Fixed monthly derived so that sums match the rounded monthly CTC
        let fixedMonthly = monthlyCTC - (basicMonthly + hraMonthly + convMonthly);
        if (fixedMonthly < 0) fixedMonthly = 0;

        // Display annuals as monthly * 12 (again, like your expected output)
        const basicAnnual = basicMonthly * 12;
        const hraAnnual = hraMonthly * 12;
        const convAnnualDisplay = convMonthly * 12;
        const fixedAnnual = fixedMonthly * 12;

        setFieldValue("salaryDetails.basicMonthly", basicMonthly);
        setFieldValue("salaryDetails.hraMonthly", hraMonthly);
        setFieldValue("salaryDetails.conveyanceMonthly", convMonthly);
        setFieldValue("salaryDetails.fixedMonthly", fixedMonthly);

        setFieldValue("salaryDetails.basic", basicAnnual);
        setFieldValue("salaryDetails.hra", hraAnnual);
        // keep the input conveyance as-is, but set a display annual if you show it:
        setFieldValue("salaryDetails.conveyanceDisplay", convAnnualDisplay);
        setFieldValue("salaryDetails.fixedAllowance", fixedAnnual);

        setFieldValue("salaryDetails.monthlyCTC", monthlyCTC);
        setFieldValue("salaryDetails.annualCTCComputed", annualCTC);
    };

    const validationSchema = Yup.object().shape({
        firstName: Yup.string().required("First Name is required"),
        lastName: Yup.string().required("Last Name is required"),
        middleName: Yup.string(),
        employeeNumber: Yup.string().required("Employee ID is required"),
        dateOfJoining: Yup.date().required("Date of Joining is required"),
        workMail: Yup.string().email("Invalid email").required("Work Email is required"),
        mobile: Yup.string().required("Mobile Number is required"),
        gender: Yup.string().required("Gender is required"),
        WorkLocationName: Yup.string().required("Work Location is required"),
        workLocation: Yup.string().required("Work location is required"),
        addressLine1: Yup.string().required("Address Line 1 is required"),
        addressLine2: Yup.string(),
        state: Yup.string().required("State is required"),
        city: Yup.string().required("City is required"),
        pinCode: Yup.string()
            .required("PIN code is required")
            .matches(/^\d{6}$/, "PIN code must be a 6-digit number"),
        departmentName: Yup.string().required("Department is required"),
        designationName: Yup.string().required("Designation is required"),
        name: Yup.string()
            .required("Department name is required")
            .min(2, "Department name must be at least 2 characters")
            .max(50, "Department name cannot exceed 50 characters"),
        code: Yup.string()
            .required("Department code is required")
            .max(10, "Department code cannot exceed 10 characters"),
        description: Yup.string()
            .max(250, "Description cannot exceed 250 characters"),
        director: Yup.boolean(),


        salaryDetails: Yup.object().shape({
            annualCTC: Yup.number()
                .typeError("Annual CTC must be a number")
                .required("Annual CTC is required")
                .positive("Annual CTC must be greater than 0"),
            basicPercent: Yup.number()
                .typeError("Basic % must be a number")
                .required("Basic % is required")
                .min(1, "Must be at least 1%")
                .max(100, "Cannot exceed 100%"),
            hraPercent: Yup.number()
                .typeError("HRA % must be a number")
                .required("HRA % is required")
                .min(1, "Must be at least 1%")
                .max(100, "Cannot exceed 100%"),
        }),
        dateOfBirth: Yup.date()
            .required("Date of Birth is required"),
        age: Yup.number()
            .min(0, "Age cannot be negative")
            .max(120, "Enter a valid age")
            .required("Age is required"),
        fatherName: Yup.string()
            .required("Father Name is required"),
        panNumber: Yup.string()
            .matches(/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/, "Invalid PAN Number format")
            .required("PAN Number is required"),
        differentlyAbledType: Yup.string()
            .required("Please select a type"),
        personalEmail: Yup.string()
            .email("Invalid email address")
            .required("Personal Email is required"),
        residentialAddress: Yup.object({
            addressLine1: Yup.string().required("Address Line 1 is required"),
            addressLine2: Yup.string().nullable(), // optional
            city: Yup.string().required("City is required"),
            state: Yup.string().required("State is required"),
            pincode: Yup.string()
                .matches(/^[1-9][0-9]{5}$/, "Invalid PIN Code") // Indian PIN Code pattern
                .required("PIN Code is required")
        }),
        paymentMode: Yup.string().required("Please select a payment mode"),

        // These only apply if Bank Transfer is chosen
        accountHolderName: Yup.string().when("paymentMode", {
            is: "bankTransfer",
            then: (schema) => schema.required("Account Holder Name is required"),
        }),
        bankName: Yup.string().when("paymentMode", {
            is: "bankTransfer",
            then: (schema) => schema.required("Bank Name is required"),
        }),
        accountNumber: Yup.string()
            .when("paymentMode", {
                is: "bankTransfer",
                then: (schema) =>
                    schema
                        .matches(/^[0-9]{9,18}$/, "Enter a valid Account Number")
                        .required("Account Number is required"),
            }),
        confirmAccountNumber: Yup.string().when("paymentMode", {
            is: "bankTransfer",
            then: (schema) =>
                schema
                    .oneOf([Yup.ref("accountNumber"), null], "Account numbers must match")
                    .required("Please re-enter Account Number"),
        }),
        ifsc: Yup.string().when("paymentMode", {
            is: "bankTransfer",
            then: (schema) =>
                schema
                    .matches(/^[A-Z]{4}0[A-Z0-9]{6}$/, "Enter a valid IFSC code")
                    .required("IFSC is required"),
        }),
        accountType: Yup.string().when("paymentMode", {
            is: "bankTransfer",
            then: (schema) => schema.required("Please select an Account Type"),
        }),
    });

    // Fetch departments, designations, and work locations
    // useEffect(() => {
    //     fetchDepartments();
    //     fetchDesignations();
    //     fetchWorkLocations();
    // }, []);


    // Fetch once when component mounts
    useEffect(() => {
        fetchWorkLocations();
        fetchDepartments();
        fetchDesignations();
    }, [organizationId]);

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




    const handleDesignationSubmit = async (values, { setSubmitting }) => {
        try {
            setSubmitting(true);
            const response = await axios.post('/api/designations', values);
            if (response.data.success) {
                message.success('Designation added successfully');
                // fetchDesignations();
                setShowAddForm(false);
                setNewDesignation({ name: "" });
            }
        } catch (error) {
            message.error('Failed to add designation');
        } finally {
            setSubmitting(false);
        }
    };



    // const handleWorkLocationSubmit = async (values, { setSubmitting, resetForm }) => {
    //     if (!_.isEmpty(values.workLocation) && !_.isEmpty(values.addressLine1)) {
    //         setSubmitting(true);
    //         try {
    //             const response = await axios.post(
    //                 `${GlobalConst.API_URL}/work-locations/save`,
    //                 values
    //             );

    //             if (
    //                 response?.data?.message === "Work Location saved successfully"
    //             ) {
    //                 console.log("Work location saved.");
    //                 resetForm();
    //             } else {
    //                 errorMsg(
    //                     "Save Failed",
    //                     `There was an error saving the work location. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
    //                     true
    //                 );
    //             }
    //         } catch (e) {
    //             if (!_.isEmpty(e?.response?.data)) {
    //                 errorMsg("Save Failed", e.response.data.err_msg, false);
    //             } else {
    //                 errorMsg(e.code, e.message, true);
    //             }
    //         } finally {
    //             setSubmitting(false);
    //         }
    //     }
    // };



    const handleBasicDetailsSubmit = async (values, { setSubmitting }) => {
        setSigningIn(true);
        try {
            // Map frontend values to backend DTO
            const basicDetailsData = {
                employeeNumber: values.employeeNumber || null, // optional, backend can generate
                firstName: values.firstName,
                middleName: values.middleName,
                lastName: values.lastName,
                gender: values.gender,
                dateOfJoining: values.dateOfJoining,
                departmentId: values.departmentId,
                designationId: values.designationId,
                workLocationId: values.workLocationId,
                employeeStatus: values.employeeStatus,
                portalEnabled: values.portalEnabled,
                eligibleForPf: values.eligibleForPf,
                eligibleForPt: values.eligibleForPt,
                eligibleForLwf: values.eligibleForLwf,
                eligibleForEsi: values.eligibleForEsi,
                director: values.director,
                eligibleForEps: values.eligibleForEps,
                canContributeToEpsOnHigherWages: values.canContributeToEpsOnHigherWages,
                mobile: values.mobile,
                workMail: values.workMail,
                pfAccountNumber: values.pfAccountNumber,
                uan: values.uan,
                tags: values.tags || [],
                organizationId: organizationId, // required field
                amountInPercentage: values.amountInPercentage || null
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

            // ✅ Backend wraps data inside response.data.data
            if (response.data && response.data.data?.id) {
                setEmployeeId(response.data.data.id); // save for next steps
                successMsg("Success", response.data.message || "Employee basic details saved", false);
                next(); // go to next step in stepper
            } else {
                errorMsg("Save Failed", "Failed to save employee details", false);
            }
        } catch (error) {
            console.error("API Error:", error);
            if (error.response) {
                errorMsg("Save Failed", error.response.data?.message || "Failed to save employee details", false);
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



    const handleSalaryDetailsSubmit = async (values, { setSubmitting, resetForm }) => {
        if (!_.isEmpty(values.salaryDetails?.annualCTC)) {
            setSubmitting(true);
            try {
                const response = await axios.post(
                    `${GlobalConst.API_URL}/salary-details/save`,
                    values.salaryDetails
                );

                if (response?.data?.message === "Salary Details saved successfully") {
                    console.log("Salary details saved.");
                    resetForm();
                } else {
                    errorMsg(
                        "Save Failed",
                        `There was an error saving salary details. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (e) {
                if (!_.isEmpty(e?.response?.data)) {
                    errorMsg("Save Failed", e.response.data.err_msg, false);
                } else {
                    errorMsg(e.code, e.message, true);
                }
            } finally {
                setSubmitting(false);
            }
        } else {
            errorMsg("Validation Failed", "Please enter Annual CTC before saving", false);
        }
    };

    const handlePersonalDetailSubmit = async (values, { setSubmitting, resetForm }) => {
        if (!_.isEmpty(values.firstName) && !_.isEmpty(values.lastName) && !_.isEmpty(values.email)) {
            setSubmitting(true);
            try {
                const response = await axios.post(
                    `${GlobalConst.API_URL}/personal-details/save`,
                    values
                );

                if (response?.data?.message === "Personal Details saved successfully") {
                    console.log("Personal details saved.");
                    resetForm();
                } else {
                    errorMsg(
                        "Save Failed",
                        `There was an error saving personal details. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (e) {
                if (!_.isEmpty(e?.response?.data)) {
                    errorMsg("Save Failed", e.response.data.err_msg, false);
                } else {
                    errorMsg(e.code, e.message, true);
                }
            } finally {
                setSubmitting(false);
            }
        } else {
            errorMsg("Validation Failed", "Please fill all required personal details", false);
        }
    };

    const handlePaymentDetailSubmit = async (values, { setSubmitting, resetForm }) => {
        if (!_.isEmpty(values.accountNumber) && !_.isEmpty(values.ifscCode) && !_.isEmpty(values.bankName)) {
            setSubmitting(true);
            try {
                const response = await axios.post(
                    `${GlobalConst.API_URL}/payment-details/save`,
                    values
                );

                if (response?.data?.message === "Payment Details saved successfully") {
                    console.log("Payment details saved.");
                    resetForm();
                } else {
                    errorMsg(
                        "Save Failed",
                        `There was an error saving payment details. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (e) {
                if (!_.isEmpty(e?.response?.data)) {
                    errorMsg("Save Failed", e.response.data.err_msg, false);
                } else {
                    errorMsg(e.code, e.message, true);
                }
            } finally {
                setSubmitting(false);
            }
        } else {
            errorMsg("Validation Failed", "Please fill all required payment details", false);
        }
    };



    const designationValidationSchema = Yup.object().shape({
        name: Yup.string().required('Designation name is required'),
        code: Yup.string(),
        description: Yup.string()
    });

    const { token } = theme.useToken();
    const [current, setCurrent] = useState(0);
    const next = () => {
        setCurrent(current + 1);
    };
    const prev = () => {
        setCurrent(current - 1);
    };


    const steps = [
        {
            title: <span className="text-dark fw-semibold">Basic Details</span>,
            content: () => (
                <Formik
                    initialValues={initialValues}   // you already defined this on top
                    validationSchema={validationSchema.basicDetails}  // also defined on top
                    onSubmit={handleBasicDetailsSubmit}   // ✅ calls API when Save & Continue clicked
                    enableReinitialize={true}       // ✅ ensures Formik updates if initialValues changes

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
                                                setFieldValue("WorkLocationName", e.target.value);
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
                                                setFieldValue("designationName", e.target.value);
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
                                                setFieldValue("departmentName", e.target.value);
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

                                {/* Employees' Provident Fund */}
                                <div className="form-check form-check-custom form-check-dark mb-2">
                                    <Field
                                        type="checkbox"
                                        name="statutoryComponents.employeesProvidentFund"
                                        id="employeesProvidentFund"
                                        className="form-check-input"
                                        checked={values.statutoryComponents.employeesProvidentFund}
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
                                                    className={`form-control ${errors.statutoryComponents?.pfAccountNumber && touched.statutoryComponents?.pfAccountNumber ? "is-invalid" : ""}`}
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
                                                    className={`form-control ${errors.statutoryComponents?.uan && touched.statutoryComponents?.uan ? "is-invalid" : ""}`}
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

                           
                        </Form>
                    )}
                </Formik>

            )
        },
        {

            title: <span className="text-dark fw-semibold">Salary Details</span>,

            content: ({ isSubmitting, errors, touched, setFieldValue, values }) => (

                <div className="mb-4">
                    {/* Annual CTC Input */}
                    <label className="form-label fs-6 fw-bold text-dark mb-2">
                        Annual CTC <RequiredStar />
                    </label>
                    <div className="input-group mb-3">
                        <span className="input-group-text">₹</span>
                        <Field
                            type="number"
                            name="salaryDetails.annualCTC"
                            className="form-control"
                            onChange={(e) => {
                                const val = e.target.value;
                                setFieldValue("salaryDetails.annualCTC", val);
                                calculateSalaryDetails(
                                    { ...values.salaryDetails, annualCTC: val },
                                    setFieldValue
                                );
                            }}
                            placeholder="Enter Annual CTC"
                        />
                        <span className="input-group-text">per year</span>
                    </div>

                    {/* Salary Components Table */}
                    <div className="table-responsive">
                        <table className="table gs-7 gy-7 gx-7 align-middle">
                            <thead>
                                <tr className="fw-semibold fs-6 text-gray-800 border-bottom border-gray-200">
                                    <th>Salary Components</th>
                                    <th>Calculation Type</th>
                                    <th className="text-end">Monthly Amount</th>
                                    <th className="text-end">Annual Amount</th>
                                </tr>
                            </thead>
                            <tbody>
                                {/* Earnings Group Heading */}
                                <tr>
                                    <td colSpan={4}>
                                        <label className="form-label fs-6 fw-bold text-dark mb-2">
                                            Earnings
                                        </label>
                                    </td>
                                </tr>

                                {/* Basic */}
                                <tr>
                                    <td>Basic</td>
                                    <td>
                                        <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                            <Field
                                                type="number"
                                                name="salaryDetails.basicPercent"
                                                className="form-control form-control-sm flex-grow-1"
                                                style={{ minWidth: "80px", maxWidth: "120px" }}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setFieldValue("salaryDetails.basicPercent", val);
                                                    calculateSalaryDetails(
                                                        { ...values.salaryDetails, basicPercent: val },
                                                        setFieldValue
                                                    );
                                                }}
                                            />
                                            <span className="text-muted">% of CTC</span>
                                        </div>
                                    </td>
                                    <td className="text-end">{values.salaryDetails.basicMonthly}</td>
                                    <td className="text-end">{values.salaryDetails.basic}</td>
                                </tr>

                                {/* HRA */}
                                <tr>
                                    <td>House Rent Allowance</td>
                                    <td>
                                        <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                            <Field
                                                type="number"
                                                name="salaryDetails.hraPercent"
                                                className="form-control form-control-sm flex-grow-1"
                                                style={{ minWidth: "80px", maxWidth: "120px" }}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setFieldValue("salaryDetails.hraPercent", val);
                                                    calculateSalaryDetails(
                                                        { ...values.salaryDetails, hraPercent: val },
                                                        setFieldValue
                                                    );
                                                }}
                                            />
                                            <span className="text-muted">% of Basic</span>
                                        </div>
                                    </td>
                                    <td className="text-end">{values.salaryDetails.hraMonthly}</td>
                                    <td className="text-end">{values.salaryDetails.hra}</td>
                                </tr>

                                {/* Conveyance */}
                                <tr>
                                    <td>Conveyance Allowance</td>
                                    <td>Fixed amount</td>
                                    <td className="text-end">{values.salaryDetails.conveyanceMonthly}</td>
                                    <td className="text-end">{values.salaryDetails.conveyanceDisplay}</td>
                                </tr>

                                {/* Fixed Allowance */}
                                <tr>
                                    <td>
                                        Fixed Allowance{" "}
                                        <span
                                            className="ms-1"
                                            data-bs-toggle="tooltip"
                                            title="Monthly CTC - Sum of all other components"
                                            style={{ cursor: "pointer" }}
                                        >
                                            <i className="bi bi-info-circle-fill text-gray-500"></i>
                                        </span>
                                    </td>
                                    <td>Fixed amount</td>
                                    <td className="text-end">{values.salaryDetails.fixedMonthly}</td>
                                    <td className="text-end">{values.salaryDetails.fixedAllowance}</td>
                                </tr>
                            </tbody>

                            {/* Footer */}
                            <tfoot>
                                <tr
                                    className="fw-bold"
                                    style={{ backgroundColor: "#F6F8FF" }}
                                >
                                    <td colSpan={2}>Cost to Company</td>
                                    <td className="text-end">₹{values.salaryDetails.monthlyCTC}</td>
                                    <td className="text-end">₹{values.salaryDetails.annualCTCComputed}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                </div>



            )
        },
        {

            title: <span className="text-dark fw-semibold">Personal Details</span>,
            content: ({ isSubmitting, errors, touched, setFieldValue, values }) => (
                <>


                    <div className="mb-4">
                        {/* Date of Birth and Age */}
                        <div className="row g-3 mb-4">
                            {/* Date of Birth */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Date of Birth <RequiredStar />
                                </label>
                                <Field
                                    type="date"
                                    name="dateOfBirth"
                                    placeholder="Date of Birth"
                                    className={`form-control ${errors.dateOfBirth && touched.dateOfBirth ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="dateOfBirth"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            {/* Age (Auto-calculated or Manual) */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Age
                                </label>
                                <Field
                                    type="number"
                                    name="age"
                                    placeholder="Age"
                                    className={`form-control ${errors.age && touched.age ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="age"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                        </div>
                        {/* Father Name and PAN */}
                        <div className="row g-3 mb-4">
                            {/* Father Name */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Father Name <RequiredStar />
                                </label>
                                <Field
                                    type="text"
                                    name="fatherName"
                                    placeholder="Enter Father Name"
                                    className={`form-control ${errors.fatherName && touched.fatherName ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="fatherName"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            {/* PAN Number */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    PAN Number <RequiredStar />
                                </label>
                                <Field
                                    type="text"
                                    name="panNumber"
                                    placeholder="ABCDE1234F"
                                    maxLength="10"
                                    className={`form-control ${errors.panNumber && touched.panNumber ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="panNumber"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                        </div>
                        {/* Differently Abled Type and Personal Email Address */}
                        <div className="row g-3 mb-4">
                            {/* Differently Abled Type */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Differently Abled Type <RequiredStar />
                                </label>
                                <Field
                                    as="select"
                                    name="differentlyAbledType"
                                    className={`form-select ${errors.differentlyAbledType && touched.differentlyAbledType ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                >
                                    <option value="">Select Type</option>
                                    <option value="None">None</option>
                                    <option value="Visual">Visual</option>
                                    <option value="Hearing">Hearing</option>
                                    <option value="Speech">Speech</option>
                                    <option value="Mobility">Mobility</option>
                                    <option value="Other">Other</option>
                                </Field>
                                <ErrorMessage
                                    name="differentlyAbledType"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            {/* Personal Email Address */}
                            <div className="col-12 col-md-6">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                    Personal Email Address <RequiredStar />
                                </label>
                                <Field
                                    type="email"
                                    name="personalEmail"
                                    placeholder="example@email.com"
                                    className={`form-control ${errors.personalEmail && touched.personalEmail ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="personalEmail"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>
                        </div>
                        {/* Residential Address */}
                        <div className="mb-4">
                            <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                Residential Address <RequiredStar />
                            </label>

                            {/* Address Line 1 */}
                            <div className="mb-3">
                                <Field
                                    type="text"
                                    name="residentialAddress.addressLine1"
                                    placeholder="Address Line 1"
                                    className={`form-control ${errors.residentialAddress?.addressLine1 && touched.residentialAddress?.addressLine1 ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="residentialAddress.addressLine1"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            {/* Address Line 2 */}
                            <div className="mb-3">
                                <Field
                                    type="text"
                                    name="residentialAddress.addressLine2"
                                    placeholder="Address Line 2"
                                    className={`form-control ${errors.residentialAddress?.addressLine2 && touched.residentialAddress?.addressLine2 ? "is-invalid" : ""}`}
                                    disabled={isSubmitting}
                                />
                                <ErrorMessage
                                    name="residentialAddress.addressLine2"
                                    component="div"
                                    className="invalid-feedback"
                                />
                            </div>

                            {/* City, State, Pincode */}
                            <div className="row g-3">
                                {/* City */}
                                <div className="col-12 col-md-4">
                                    <Field
                                        type="text"
                                        name="residentialAddress.city"
                                        placeholder="City"
                                        className={`form-control ${errors.residentialAddress?.city && touched.residentialAddress?.city ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage
                                        name="residentialAddress.city"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>

                                {/* State Dropdown */}
                                <div className="col-12 col-md-4">
                                    <Field
                                        as="select"
                                        name="state"
                                        className={`form-select ${errors.state && touched.state ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    >
                                        <option value="">Select State</option>
                                        {getStates().map((state, index) => (
                                            <option key={index} value={state}>
                                                {state}
                                            </option>
                                        ))}
                                    </Field>
                                    <ErrorMessage
                                        name="residentialAddress.state"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>

                                {/* Pincode */}
                                <div className="col-12 col-md-4">
                                    <Field
                                        type="text"
                                        name="residentialAddress.pincode"
                                        placeholder="PIN Code"
                                        className={`form-control ${errors.residentialAddress?.pincode && touched.residentialAddress?.pincode ? "is-invalid" : ""}`}
                                        disabled={isSubmitting}
                                    />
                                    <ErrorMessage
                                        name="residentialAddress.pincode"
                                        component="div"
                                        className="invalid-feedback"
                                    />
                                </div>
                            </div>
                        </div>



                    </div>

                </>


            )
        },
        {

            title: <span className="text-dark fw-semibold">Payment Information</span>,

            content: ({ isSubmitting, errors, touched, setFieldValue, values }) => (
                <>
                    <div className="mb-4">
                        <div className="row g-3 mb-4">
                            <div className="col-12">
                                <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                                    How would you like to pay this employee? <RequiredStar />
                                </label>

                                {/* Payment Options Wrapper */}
                                <div className="border-top border-gray-200">

                                    {/* Direct Deposit */}
                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer border-bottom border-gray-200"
                                        onClick={() => setFieldValue("paymentMode", "directDeposit")}
                                    >
                                        <div className="d-flex align-items-start">
                                            <FaUniversity className="fs-3 text-primary me-4" />
                                            <div>
                                                <div className="fw-bold fs-6 text-gray-900">
                                                    Direct Deposit (Automated Process)
                                                </div>
                                                <div className="text-muted fs-7">
                                                    Initiate payment in Payroll once the pay run is approved
                                                </div>
                                            </div>
                                        </div>
                                        <div className="d-flex align-items-center">
                                            <a
                                                href="#/settings/direct-deposit"
                                                className="text-primary fw-semibold fs-7 text-hover-underline me-3"
                                            >
                                                Configure Now
                                            </a>

                                        </div>
                                    </div>

                                    {/* Bank Transfer */}
                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer border-bottom border-gray-200"
                                        onClick={() => setFieldValue("paymentMode", "bankTransfer")}
                                    >
                                        <div className="d-flex align-items-start">
                                            <FaMoneyCheckAlt className="fs-3 text-primary me-4" />
                                            <div>
                                                <div className="fw-bold fs-6 text-gray-900">Bank Transfer (Manual Process)</div>
                                                <div className="text-muted fs-7">
                                                    Download Bank Advice and process the payment through your bank’s website
                                                </div>
                                            </div>
                                        </div>
                                        {values.paymentMode === "bankTransfer" ? (
                                            <FaCheckCircle className="text-primary fs-2" />
                                        ) : (
                                            <FaCircle className="text-gray-400 fs-2" />
                                        )}
                                    </div>
                                    {values.paymentMode === "bankTransfer" && (
                                        <div className="px-4 pb-4 border-bottom border-gray-200">
                                            {/* Extra field shown only when Bank Transfer is selected */}
                                            <label className="form-label fw-semibold">Bank Account Details</label>
                                            <div className="mb-4">
                                                <div className="row g-3 mb-4">
                                                    {/* <div className="col-12 col-md-6"> */}
                                                    <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                        Account Holder Name <RequiredStar />
                                                    </label>
                                                    <Field
                                                        type="text"
                                                        name="accountHolderName"
                                                        placeholder="Enter Account Holder Name"
                                                        className={`form-control ${errors.accountHolderName && touched.accountHolderName ? "is-invalid" : ""}`}
                                                        disabled={isSubmitting}
                                                    />
                                                    <ErrorMessage
                                                        name="accountHolderName"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                    {/* </div> */}
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
                                                    />
                                                    <ErrorMessage
                                                        name="bankName"
                                                        component="div"
                                                        className="invalid-feedback"
                                                    />
                                                </div>
                                                <div className="row g-3 mb-4">
                                                    {/* Account Number */}
                                                    <div className="col-12 col-md-6">
                                                        <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                            Account Number <RequiredStar />
                                                        </label>
                                                        <Field
                                                            type="text"
                                                            name="accountNumber"
                                                            placeholder="Enter Account Number"
                                                            className={`form-control ${errors.accountNumber && touched.accountNumber ? "is-invalid" : ""
                                                                }`}
                                                            disabled={isSubmitting}
                                                        />
                                                        <ErrorMessage
                                                            name="accountNumber"
                                                            component="div"
                                                            className="invalid-feedback"
                                                        />
                                                    </div>

                                                    {/* Re-enter Account Number */}
                                                    <div className="col-12 col-md-6">
                                                        <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                            Re-enter Account Number <RequiredStar />
                                                        </label>
                                                        <Field
                                                            type="text"
                                                            name="confirmAccountNumber"
                                                            placeholder="Re-enter Account Number"
                                                            className={`form-control ${errors.confirmAccountNumber && touched.confirmAccountNumber
                                                                ? "is-invalid"
                                                                : ""
                                                                }`}
                                                            disabled={isSubmitting}
                                                        />
                                                        <ErrorMessage
                                                            name="confirmAccountNumber"
                                                            component="div"
                                                            className="invalid-feedback"
                                                        />
                                                    </div>

                                                    <div className="row g-3 mb-4">
                                                        {/* IFSC */}
                                                        <div className="col-12 col-md-6">
                                                            <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                                IFSC <RequiredStar />
                                                            </label>
                                                            <Field
                                                                type="text"
                                                                name="ifsc"
                                                                placeholder="AAAA0000000"
                                                                className={`form-control ${errors.ifsc && touched.ifsc ? "is-invalid" : ""
                                                                    }`}
                                                                disabled={isSubmitting}
                                                            />
                                                            <ErrorMessage
                                                                name="ifsc"
                                                                component="div"
                                                                className="invalid-feedback"
                                                            />
                                                        </div>

                                                        {/* Account Type */}
                                                        <div className="col-12 col-md-6">
                                                            <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                                                Account Type <RequiredStar />
                                                            </label>
                                                            <div className="d-flex align-items-center mt-2">
                                                                <div className="form-check me-4">
                                                                    <Field
                                                                        type="radio"
                                                                        name="accountType"
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
                                                                        name="accountType"
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
                                                                name="accountType"
                                                                component="div"
                                                                className="invalid-feedback"
                                                            />
                                                        </div>
                                                    </div>

                                                </div>


                                            </div>

                                        </div>
                                    )}

                                    {/* Cheque */}
                                    <div
                                        className="d-flex align-items-center justify-content-between w-100 p-4 bg-white cursor-pointer border-bottom border-gray-200"
                                        onClick={() => setFieldValue("paymentMode", "cheque")}
                                    >
                                        <div className="d-flex align-items-center">
                                            <FaRegMoneyBillAlt className="fs-3 text-primary me-4" />
                                            <div className="fw-bold fs-6 text-gray-900">Cheque</div>
                                        </div>
                                        {values.paymentMode === "cheque" ? (
                                            <FaCheckCircle className="text-primary fs-2" />
                                        ) : (
                                            <FaCircle className="text-gray-400 fs-2" />
                                        )}
                                    </div>


                                    {/* Cash */}
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

                </>
            )
        }

    ];

    const items = steps.map(item => ({
        key: item.title,
        title: (
            <span className="d-none d-md-inline">{item.title}</span>
        )
    }));

    const handleSubmit = async (values, { setSubmitting }) => {
        if (!_.isEmpty(values.organizationName) && !_.isEmpty(values.addressLine1)) {
            setSubmitting(true);
            try {
                const response = await axios.post(
                    `${GlobalConst.API_URL}/employee/save`,
                    values,
                    {
                        headers: {
                            "Content-Type": "multipart/form-data",
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        },
                    }
                );

                if (
                    !_.isEmpty(response) &&
                    !_.isEmpty(response.data) &&
                    response.data.message === "employee saved successfully"
                ) {
                    console.log("Employees saved.");
                } else {
                    errorMsg(
                        "Save Failed",
                        `There was an error saving the profile. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (e) {
                if (!_.isEmpty(e?.response?.data)) {
                    errorMsg("Save Failed", e.response.data.err_msg, false);
                } else {
                    errorMsg(e.code, e.message, true);
                }
            } finally {
                setSubmitting(false);
            }
        }
    };



    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Add Employee</title>
            </Helmet>

            <div className="w-100 bg-white px-3 px-lg-5 py-3 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Add Employee</h5>
            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-3 p-lg-10 bg-white">
                        <div className="w-100" style={{ maxWidth: "800px", margin: "0 auto" }}>
                            <Steps
                                current={current}
                                items={items}
                                className="mb-4"
                                responsive={true}
                                size="small"
                                direction={window.innerWidth < 768 ? 'vertical' : 'horizontal'}
                            />

                            <div style={{ padding: "20px", backgroundColor: "white", borderRadius: "8px" }}>
                                <Formik
                                    initialValues={initialValues}
                                    validationSchema={validationSchema}
                                    onSubmit={handleSubmit}
                                >
                                    {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                                        <Form className="form w-100">
                                            {steps[current].content({ isSubmitting, errors, touched, setFieldValue, values })}

                                            {/* Navigation buttons */}
                                            <div className="d-flex justify-content-between mt-5">
                                                <button
                                                    type="button"
                                                    className="btn btn-light"
                                                    onClick={() => prev()}
                                                >
                                                    Cancel
                                                </button>
                                                <div>
                                                    {current > 0 && (
                                                        <Button
                                                            style={{ marginRight: 8 }}
                                                            onClick={() => prev()}
                                                            type="button"
                                                            className="btn btn-light"
                                                        >
                                                            Previous
                                                        </Button>
                                                    )}
                                                    {current < steps.length - 1 ? (
                                                        <Button type="primary" onClick={() => next()}>
                                                            Save and Continue
                                                        </Button>
                                                    ) : (
                                                        <Button
                                                            type="primary"
                                                            htmlType="submit"
                                                            disabled={isSubmitting}
                                                        >
                                                            Submit
                                                        </Button>
                                                    )}
                                                </div>
                                            </div>
                                        </Form>
                                    )}
                                </Formik>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

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
                                    streetAddress1: "",
                                    streetAddress2: "",
                                    state: "",
                                    city: "",
                                    zipCode: "",
                                    country: "",
                                    isFilingAddress: false,
                                }}
                                validationSchema={Yup.object({
                                    workLocationName: Yup.string().required("Work Location Name is required"),
                                    streetAddress1: Yup.string().required("Address Line 1 is required"),
                                    city: Yup.string().required("City is required"),
                                    zipCode: Yup.string().required("PIN/Zip Code is required"),
                                })}
                                onSubmit={async (values, { setSubmitting, resetForm }) => {
                                    try {
                                        const response = await axios.post(
                                            `${GlobalConst.API_URL}/api/worklocations`,
                                            values,
                                            {
                                                headers: {
                                                    "Content-Type": "application/json",
                                                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                                    organizationId: organizationId,
                                                },
                                            }
                                        );

                                        if (response.data.status === 200 || response.data.status === 201) {
                                            successMsg("Success", "Work location created successfully", false);

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
                                    } catch (e) {
                                        if (!_.isEmpty(e?.response?.data)) {
                                            errorMsg(
                                                "Save Failed",
                                                e.response.data.message || "Failed to save work location",
                                                false
                                            );
                                        } else {
                                            errorMsg(e.code, e.message, true);
                                        }
                                    } finally {
                                        setSubmitting(false);
                                    }
                                }}
                            >
                                {({ isSubmitting, errors, touched }) => (
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
                                                    className={`form-control form-control-lg form-control-solid ${errors.workLocationName && touched.workLocationName ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter work location name"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="workLocationName" component="div" className="invalid-feedback" />
                                            </div>

                                            {/* Address */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">
                                                    Address <RequiredStar />
                                                </label>
                                                <Field
                                                    type="text"
                                                    name="streetAddress1"
                                                    className={`form-control form-control-lg form-control-solid mb-3 ${errors.streetAddress1 && touched.streetAddress1 ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter address line 1"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="streetAddress1" component="div" className="invalid-feedback" />

                                                <Field
                                                    type="text"
                                                    name="streetAddress2"
                                                    className={`form-control form-control-lg form-control-solid ${errors.streetAddress2 && touched.streetAddress2 ? "is-invalid" : ""
                                                        }`}
                                                    placeholder="Enter address line 2"
                                                    disabled={isSubmitting}
                                                />
                                                <ErrorMessage name="streetAddress2" component="div" className="invalid-feedback" />
                                            </div>

                                            {/* State, City, ZIP, Country */}
                                            <div className="row">
                                                <div className="col-md-4 mb-3">
                                                    <label className="form-label fs-6 fw-bold text-dark">State</label>
                                                    <Field
                                                        type="text"
                                                        name="state"
                                                        className="form-control form-control-lg form-control-solid"
                                                        placeholder="State"
                                                        disabled={isSubmitting}
                                                    />
                                                </div>

                                                <div className="col-md-4 mb-3">
                                                    <label className="form-label fs-6 fw-bold text-dark">City <RequiredStar /></label>
                                                    <Field
                                                        type="text"
                                                        name="city"
                                                        className={`form-control form-control-lg form-control-solid ${errors.city && touched.city ? "is-invalid" : ""
                                                            }`}
                                                        placeholder="City"
                                                        disabled={isSubmitting}
                                                    />
                                                    <ErrorMessage name="city" component="div" className="invalid-feedback" />
                                                </div>

                                                <div className="col-md-4 mb-3">
                                                    <label className="form-label fs-6 fw-bold text-dark">PIN/Zip Code <RequiredStar /></label>
                                                    <Field
                                                        type="text"
                                                        name="zipCode"
                                                        className={`form-control form-control-lg form-control-solid ${errors.zipCode && touched.zipCode ? "is-invalid" : ""
                                                            }`}
                                                        placeholder="Zip Code"
                                                        disabled={isSubmitting}
                                                    />
                                                    <ErrorMessage name="zipCode" component="div" className="invalid-feedback" />
                                                </div>
                                            </div>

                                            {/* Country */}
                                            <div className="fv-row mb-10">
                                                <label className="form-label fs-6 fw-bold text-dark">Country</label>
                                                <Field
                                                    type="text"
                                                    name="country"
                                                    className="form-control form-control-lg form-control-solid"
                                                    placeholder="Country"
                                                    disabled={isSubmitting}
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
                                            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
                                                {isSubmitting ? <span className="spinner-border spinner-border-sm me-1"></span> : "Save"}
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
}