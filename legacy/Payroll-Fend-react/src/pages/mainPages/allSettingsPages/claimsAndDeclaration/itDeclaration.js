// Updated ITDeclaration.js with proper tab integration and tax regime selection
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import itdeclarion from "../../../../assets/images/itdeclaration.png";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import ProofOfInvestmentSettings from "./proofOfInvestment";
import TaxSlabRegimeSettings from "./taxSlabRegime";
import { CloseOutlined } from "@ant-design/icons";


export default function ITDeclaration() {
    const [activeTab, setActiveTab] = useState("income-tax");
    const [isLocked, setIsLocked] = useState(true);
    const [showInfoBox, setShowInfoBox] = useState(false);
    const [lockDate, setLockDate] = useState("");
    const [reminders, setReminders] = useState([
        { id: 1, days: 5, enabled: true },
        { id: 2, days: 1, enabled: true }
    ]);
    const [loading, setLoading] = useState(false);
    const [initialData, setInitialData] = useState(null);
    const [isInitialLoad, setIsInitialLoad] = useState(true);
    const [originalLockDate, setOriginalLockDate] = useState("");
    const [originalReminders, setOriginalReminders] = useState([]);
    const [originalFormValues, setOriginalFormValues] = useState({});
    const [showLockConfirmation, setShowLockConfirmation] = useState(false);
    const [defaultTaxRegime, setDefaultTaxRegime] = useState(null);

    // Get organizationId from localStorage
    const organizationId = localStorage.getItem("organizationId");



    const getDaysDifference = (startDate, endDate) => {
        if (!startDate || !endDate) return 0;

        const start = new Date(startDate);
        const end = new Date(endDate);

        // Reset time to avoid timezone issues
        start.setHours(0, 0, 0, 0);
        end.setHours(0, 0, 0, 0);

        const diffTime = end - start;
        return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    };



    const todayISO = new Date().toISOString().split("T")[0];
    const remainingDays = lockDate
        ? getDaysDifference(todayISO, lockDate)
        : 0;


    // Tabs data
    const tabs = [
        // { id: "flexible-benefit", label: "Flexible Benefit Plan" },
        // { id: "reimbursement", label: "Reimbursement Claims" },
        { id: "income-tax", label: "Income Tax Declaration", active: true },
        { id: "proof-investment", label: "Proof Of Investments" },
        { id: "tax-slab-regime", label: "TaxSlab Regim" }
    ];

    // Fetch IT Declaration data on component mount
    useEffect(() => {
        if (activeTab === "income-tax") {
            fetchITDeclaration();
        }
    }, [activeTab]);

    const fetchITDeclaration = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/income-tax-declarations`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                const data = response.data.data;
                setInitialData(data);

                // Update state from API response
                setIsLocked(data.itDeclarationLocked);
                setLockDate(data.lastDateForItDeclaration || "");
                setOriginalLockDate(data.lastDateForItDeclaration || "");
                setDefaultTaxRegime(data.defaultTaxRegime);

                // Map reminders from API response
                if (data.reminders && data.reminders.length > 0) {
                    const mappedReminders = data.reminders.map((reminder, index) => ({
                        id: index + 1,
                        days: reminder.numberOfDays,
                        enabled: reminder.enabled,
                        reminderId: reminder.reminderId
                    }));
                    setReminders(mappedReminders);
                    setOriginalReminders([...mappedReminders]);
                }

                // Store original form values
                const originalValues = {
                    allowSwitchTaxRegime: data.canChangeTaxRegimeIt || false,
                    allowTDSModification: data.canTdsExceedAnnualLimit || false,
                    notifyWhenReleased: data.sendMailOnItDeclarationRelease || true,
                    notifyWhenLocked: data.sendMailOnItDeclarationLock || true,
                    enableEmailReminders: data.anyReminderBeforeLockdateEnabled || true,
                    defaultTaxRegime: data.defaultTaxRegime
                };
                setOriginalFormValues(originalValues);

                setIsInitialLoad(false);
            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error:", error);

            if (error.response) {
                errorMsg("Error", error.response.data?.message || "Failed to load IT Declaration settings", true);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setLoading(false);
        }
    };

    // Handle tab change
    const handleTabChange = (tabId) => {
        setActiveTab(tabId);
    };

    // Handle lock date change
    const handleLockDateChange = (e) => {
        const date = e.target.value;
        setLockDate(date);

        // Generate date range message (assuming current year)
        if (date) {
            const lockDateObj = new Date(date);
            const startDate = new Date(lockDateObj);
            startDate.setDate(startDate.getDate() - 21); // 3 weeks before

            // Format dates as dd/mm/yyyy
            const formatDate = (dateObj) => {
                const day = String(dateObj.getDate()).padStart(2, '0');
                const month = String(dateObj.getMonth() + 1).padStart(2, '0');
                const year = dateObj.getFullYear();
                return `${day}/${month}/${year}`;
            };

            console.log(`Employees can declare between ${formatDate(startDate)} and ${formatDate(lockDateObj)}`);
        }
    };

    // Handle tax regime change
    const handleTaxRegimeChange = (e) => {
        setDefaultTaxRegime(e.target.value);
    };

    // Add reminder
    const addReminder = () => {
        setReminders(prev => {
            const newId =
                prev.length > 0
                    ? Math.max(...prev.map(r => r.id)) + 1
                    : 1;

            return [
                ...prev,
                {
                    id: newId,
                    days: 1,
                    enabled: true
                }
            ];
        });
    };


    // Remove reminder
    const removeReminder = (id) => {
        setReminders(prev => prev.filter(r => r.id !== id));
    };


    // Update reminder days
    const updateReminderDays = (id, days) => {
        setReminders(reminders.map(r =>
            r.id === id ? { ...r, days: parseInt(days) || 0 } : r
        ));
    };

    // Toggle reminder
    const toggleReminder = (id) => {
        setReminders(prev =>
            prev.map(r =>
                r.id === id ? { ...r, enabled: !r.enabled } : r
            )
        );
    };


    // Handle form submission
    const handleSubmit = async (values, { setSubmitting }) => {
        try {
            setLoading(true);

            // Prepare reminders data
            const remindersData = reminders.map(reminder => ({
                reminderId: reminder.reminderId || generateRandomId(),
                numberOfDays: reminder.days,
                enabled: reminder.enabled
            }));

            // Prepare the DTO object matching backend structure
            const requestData = {
                reminders: remindersData,
                canTdsExceedAnnualLimit: values.allowTDSModification,
                itDeclarationLocked: isLocked,
                canChangeTaxRegimeIt: values.allowSwitchTaxRegime,
                lastDateForItDeclaration: lockDate,
                sendMailOnEmployeeLevelItLockAndRelease: false, // Default value
                currentPayrun: initialData?.currentPayrun || "",
                sendMailOnItDeclarationLock: values.notifyWhenLocked,
                payscheduleConfigured: initialData?.payscheduleConfigured || false,
                sendMailOnItDeclarationRelease: values.notifyWhenReleased,
                anyReminderBeforeLockdateEnabled: values.enableEmailReminders,
                panMandatoryForAnnualRentOverOneLakh: false, // Default value
                defaultTaxRegime: defaultTaxRegime
            };

            console.log("Submitting data:", requestData);

            // Make API call
            const response = await axios.put(`${GlobalConst.API_URL}/api/income-tax-declarations`,
                requestData,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId,
                        'Content-Type': 'application/json'
                    },
                }
            );

            if (response.data && response.data.status === 200) {
                successMsg("Success", "IT Declaration settings updated successfully", false);

                // Update initial data with new values
                setInitialData(response.data.data);

                // Update original values after successful save
                setOriginalLockDate(lockDate);
                setOriginalReminders([...reminders]);
                setOriginalFormValues({...values, defaultTaxRegime: defaultTaxRegime});

                // If we were updating from locked to released state, refresh the data
                if (isLocked !== requestData.itDeclarationLocked) {
                    fetchITDeclaration();
                }
            } else {
                errorMsg("Error", response.data?.message || "Failed to update IT Declaration settings", false);
            }
        } catch (error) {
            console.error("Update Error:", error);

            if (error.response) {
                errorMsg("Update Failed", error.response.data?.message || "Failed to update IT Declaration settings", false);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
            } else {
                errorMsg("Error", "An unexpected error occurred", false);
            }
        } finally {
            setLoading(false);
            setSubmitting(false);
        }
    };

    // Handle cancel/reset
    const handleCancel = () => {
        // Reset form values to original
        setLockDate(originalLockDate);
        setReminders([...originalReminders]);
        setDefaultTaxRegime(originalFormValues.defaultTaxRegime);

        // Show confirmation message
        successMsg("Reset", "All changes have been reset to original values", false);
    };

    // Handle lock IT declaration
    const handleLockDeclaration = async () => {
        setShowLockConfirmation(true);
    };

    // Confirm lock IT declaration
    const confirmLockDeclaration = async () => {
        try {
            setLoading(true);
            setShowLockConfirmation(false);

            // Prepare data for lock action
            const requestData = {
                reminders: initialData?.reminders || [],
                canTdsExceedAnnualLimit: initialData?.canTdsExceedAnnualLimit || false,
                itDeclarationLocked: true, // Set to locked
                canChangeTaxRegimeIt: initialData?.canChangeTaxRegimeIt || true,
                lastDateForItDeclaration: lockDate,
                sendMailOnEmployeeLevelItLockAndRelease: false,
                currentPayrun: initialData?.currentPayrun || "",
                sendMailOnItDeclarationLock: true,
                payscheduleConfigured: initialData?.payscheduleConfigured || false,
                sendMailOnItDeclarationRelease: initialData?.sendMailOnItDeclarationRelease || true,
                anyReminderBeforeLockdateEnabled: initialData?.anyReminderBeforeLockdateEnabled || true,
                panMandatoryForAnnualRentOverOneLakh: initialData?.panMandatoryForAnnualRentOverOneLakh || false,
                defaultTaxRegime: defaultTaxRegime
            };

            const response = await axios.put(`${GlobalConst.API_URL}/api/income-tax-declarations`,
                requestData,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId,
                        'Content-Type': 'application/json'
                    },
                }
            );

            if (response.data && response.data.status === 200) {
                setIsLocked(true);
                setInitialData(response.data.data);
                successMsg("Success", "IT Declaration has been locked successfully.", false);

                // Update original values
                setOriginalLockDate(lockDate);
                setOriginalFormValues({
                    allowSwitchTaxRegime: response.data.data.canChangeTaxRegimeIt || false,
                    allowTDSModification: response.data.data.canTdsExceedAnnualLimit || false,
                    notifyWhenReleased: response.data.data.sendMailOnItDeclarationRelease || true,
                    notifyWhenLocked: response.data.data.sendMailOnItDeclarationLock || true,
                    enableEmailReminders: response.data.data.anyReminderBeforeLockdateEnabled || true,
                    defaultTaxRegime: response.data.data.defaultTaxRegime
                });
            } else {
                errorMsg("Error", "Failed to lock IT Declaration", false);
            }
        } catch (error) {
            console.error("Lock Error:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to lock IT Declaration", false);
        } finally {
            setLoading(false);
        }
    };

    // Handle release IT declaration - Clear date and reset changes
    const handleReleaseDeclaration = () => {
        // Clear the lock date
        setLockDate("");

        // Reset reminders to original
        setReminders([...originalReminders]);

        // Change UI state to released
        setIsLocked(false);
    };

    // Generate random ID for new reminders
    const generateRandomId = () => {
        return Math.floor(100000000000 + Math.random() * 900000000000).toString();
    };

    // Initial form values from API data
    const getInitialValues = () => {
        if (isInitialLoad && !initialData) {
            return {
                allowSwitchTaxRegime: false,
                allowTDSModification: false,
                notifyWhenReleased: true,
                notifyWhenLocked: true,
                enableEmailReminders: true,
                defaultTaxRegime: null
            };
        }

        return {
            allowSwitchTaxRegime: initialData?.canChangeTaxRegimeIt || false,
            allowTDSModification: initialData?.canTdsExceedAnnualLimit || false,
            notifyWhenReleased: initialData?.sendMailOnItDeclarationRelease || true,
            notifyWhenLocked: initialData?.sendMailOnItDeclarationLock || true,
            enableEmailReminders: initialData?.anyReminderBeforeLockdateEnabled || true,
            defaultTaxRegime: initialData?.defaultTaxRegime || null
        };
    };

    // Validation schema
    const validationSchema = Yup.object().shape({
        allowSwitchTaxRegime: Yup.boolean(),
        allowTDSModification: Yup.boolean(),
        notifyWhenReleased: Yup.boolean(),
        notifyWhenLocked: Yup.boolean(),
        enableEmailReminders: Yup.boolean(),
        defaultTaxRegime: Yup.string().nullable()
    });

    const currentDate = new Date().toLocaleDateString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric",
    });
    const formattedLockDate = lockDate
        ? new Date(lockDate).toLocaleDateString("en-IN", {
            day: "2-digit",
            month: "short",
            year: "numeric",
        })
        : "";



    // Render active tab content
    const renderTabContent = () => {
        switch (activeTab) {
            case "income-tax":
                return (
                    <Formik
                        initialValues={getInitialValues()}
                        validationSchema={validationSchema}
                        onSubmit={handleSubmit}
                        enableReinitialize={!isInitialLoad}
                    >
                        {({ isSubmitting, values, setFieldValue, resetForm }) => (
                            <Form className="form w-100">
                                {/* Show locked status on front page */}
                                {isLocked && initialData?.itDeclarationLocked && (
                                    <div className={`alert ${initialData?.itDeclarationLocked ? 'alert-danger' : 'alert-primary'} d-flex align-items-center p-4 mb-6`}>
                                        <i className={`bi ${initialData?.itDeclarationLocked ? 'bi-lock-fill' : 'bi-unlock-fill'} fs-2 me-3`}></i>
                                        <div className="d-flex flex-column">
                                            <span className={`fw-bold fs-6 ${initialData?.itDeclarationLocked ? 'text-danger' : 'text-primary'}`}>
                                                {initialData?.itDeclarationLocked ? 'IT Declaration Is Locked' : 'IT Declaration Is Released'}
                                            </span>
                                        </div>
                                    </div>
                                )}

                                {/* {!isLocked && (
                                    <div className="alert alert-info d-flex align-items-center p-4 mb-6">
                                        <i className="bi bi-info-circle-fill fs-2 me-3"></i>
                                        <div className="d-flex flex-column">
                                            <span className="fw-bold fs-6">New tax regime will be used for TDS calculation for employees who are not declaring their IT.</span>
                                        </div>
                                    </div>
                                )} */}

                                {/* Introduction Text */}
                                <div className="fv-row mb-6">
                                    <div className="text-gray-700 fs-6">
                                        Employees can declare their tax saving investments and expense details through the employee portal once you enable this option.
                                        {!isLocked && " For employees without portal, you can submit it on their behalf under Employees > Employee profile > Investment > IT Declaration."}
                                    </div>
                                </div>

                                {/* Select Tax Regime Section */}
                                <div className="card mb-6">
                                    <div className="card-body">
                                        <h6 className="fw-bold mb-4">Select Tax Regime</h6>
                                        <p className="text-gray-700 mb-4">
                                            Select the default tax regime for employees who don't declare their investments. The selected regime will be used for TDS calculation.
                                        </p>
                                        
                                        <div className="row">
                                            <div className="col-md-6">
                                                <div className="form-check form-check-custom form-check-solid mb-3">
                                                    <input
                                                        className="form-check-input"
                                                        type="radio"
                                                        name="defaultTaxRegime"
                                                        id="oldTaxRegime"
                                                        value="OLD"
                                                        checked={defaultTaxRegime === "OLD"}
                                                        onChange={handleTaxRegimeChange}
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="oldTaxRegime">
                                                        Old Tax Regime
                                                    </label>
                                                </div>
                                                
                                                <div className="form-check form-check-custom form-check-solid">
                                                    <input
                                                        className="form-check-input"
                                                        type="radio"
                                                        name="defaultTaxRegime"
                                                        id="newTaxRegime"
                                                        value="NEW"
                                                        checked={defaultTaxRegime === "NEW"}
                                                        onChange={handleTaxRegimeChange}
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="newTaxRegime">
                                                        New Tax Regime
                                                    </label>
                                                </div>
                                            </div>
                                        </div>
                                        
                                        {/* Current Selection Info */}
                                        {defaultTaxRegime && (
                                            <div className="alert alert-primary mt-4">
                                                <div className="d-flex align-items-start">
                                                    <i className="bi bi-info-circle me-2 mt-1"></i>
                                                    <div>
                                                        <div className="fw-bold">
                                                            Current Selection: {defaultTaxRegime === "OLD" ? "Old Tax Regime" : "New Tax Regime"}
                                                        </div>
                                                        <div>
                                                            This tax regime will be used for TDS calculation for employees who don't declare their investments.
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {/* Help Links */}
                                {/* <div className="fv-row mb-8">
                                    <div className="d-flex flex-column gap-2">
                                        <a href="#" className="text-primary fw-semibold fs-7 text-hover-primary">
                                            Learn how to manage investment declarations. IT Declaration Help Document
                                        </a>

                                    </div>
                                </div> */}

                                {isLocked ? (
                                    /* LOCKED STATE */
                                    <>
                                        {/* Locked Image Placeholder */}
                                        <div className="text-center my-4">
                                            <img src={itdeclarion} alt="Locked" className="mw-100 h-200px h-sm-325px" />
                                        </div>
                                        <div className="card-body p-6">
                                            <div className="d-flex align-items-center mb-4">
                                                <i className="bi bi-lock-fill text-danger fs-2 me-3"></i>
                                                <div>
                                                    <h3 className="fw-bold text-danger mb-1 text-center">IT Declaration Is Locked</h3>
                                                    <p className="text-gray-700 mb-0">
                                                        You are yet to enable the submission of IT Declaration for your employees through their respective portals.
                                                        Release IT Declaration or submit it on their behalf under Employees {">"} Employee profile{">"} Investments {">"} IT Declaration.
                                                    </p>
                                                </div>
                                            </div>

                                            <div className="text-center">
                                                <button
                                                    type="button"
                                                    className="btn btn-lg btn-primary"
                                                    onClick={handleReleaseDeclaration}
                                                    disabled={loading}
                                                >
                                                    {loading ? (
                                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                                    ) : null}
                                                    Release IT Declaration
                                                </button>
                                            </div>
                                        </div>

                                        {/* Other Configurations - Locked State */}
                                        {/* <div className="card mb-6"> */}
                                            {/* <div className="card-header">
                                                <h5 className="card-title mb-0">Other Configurations</h5>
                                            </div> */}
                                            {/* <div className="card-body"> */}
                                                {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                                    <Field
                                                        type="checkbox"
                                                        name="allowSwitchTaxRegime"
                                                        id="allowSwitchTaxRegime"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="allowSwitchTaxRegime">
                                                        Allow employees to switch tax regimes
                                                    </label>
                                                </div> */}

                                                {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <Field
                                                            type="checkbox"
                                                            name="allowTDSModification"
                                                            id="allowTDSModification"
                                                            className="form-check-input"
                                                        />
                                                        <label className="form-check-label fw-semibold" htmlFor="allowTDSModification">
                                                            Allow TDS modification to exceed the current fiscal year's calculated tax amount
                                                        </label>
                                                        <span
                                                            className="svg-icon svg-icon-2 svg-icon-primary cursor-pointer"
                                                            onClick={() => setShowInfoBox(!showInfoBox)}
                                                        >
                                                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                                                <path
                                                                    opacity="0.3"
                                                                    d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
                                                                    fill="currentColor"
                                                                />
                                                                <rect opacity="0.3" x="11" y="10" width="2" height="7" rx="1" fill="currentColor" />
                                                                <rect x="11" y="7" width="2" height="2" rx="1" fill="currentColor" />
                                                            </svg>
                                                        </span>
                                                    </div>
                                                </div> */}

                                                {/* {showInfoBox && (
                                                    <div className="alert alert-info mt-3">
                                                        <div className="d-flex">
                                                            <i className="bi bi-info-circle me-2"></i>
                                                            <div>
                                                                Changes made here will also apply to POI preferences only when the TDS modification is enabled in POI preferences.
                                                            </div>
                                                        </div>
                                                    </div>
                                                )} */}
                                            {/* </div> */}
                                        {/* </div> */}

                                        <div className="d-flex justify-content-end gap-3">
                                            <button
                                                type="button"
                                                className="btn btn-lg btn-light"
                                                onClick={() => {
                                                    handleCancel();
                                                    resetForm({ values: originalFormValues });
                                                }}
                                                disabled={isSubmitting || loading}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-lg btn-primary"
                                                disabled={isSubmitting || loading}
                                            >
                                                {isSubmitting || loading ? (
                                                    <>
                                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                                        Saving...
                                                    </>
                                                ) : "Save"}
                                            </button>
                                        </div>
                                    </>
                                ) : (
                                    /* RELEASED STATE */
                                    <>
                                        {/* Allow Submission and Lock Date */}
                                        <div className="card mb-6">
                                            <div className="card-body">
                                                <div className="form-check form-check-custom form-check-solid mb-4">
                                                    {/* <Field
                                                        type="checkbox"
                                                        name="allowSubmission"
                                                        id="allowSubmission"
                                                        className="form-check-input"
                                                        // checked={true}
                                                        onChange={(e) => setFieldValue("allowSubmission", e.target.checked)}
                                                    /> */}
                                                    <label className="form-check-label fw-bold fs-6" htmlFor="allowSubmission">
                                                        Employees submit IT Declaration through the employee portal and lock on the selected date
                                                    </label>
                                                </div>

                                                <div className="row">
                                                    <div className="col-md-6">
                                                        <label htmlFor="lockDate" className="form-label fw-semibold">
                                                            Select Lock Date
                                                        </label>
                                                        <input
                                                            type="date"
                                                            id="lockDate"
                                                            className="form-control form-control-lg"
                                                            value={lockDate}
                                                            onChange={handleLockDateChange}
                                                            min={new Date().toISOString().split('T')[0]}
                                                        />
                                                    </div>
                                                </div>
                                                {lockDate && (
                                                    <div className="alert alert-success mt-4">
                                                        <div className="d-flex align-items-start">
                                                            <i className="bi bi-calendar-check me-2 mt-1"></i>

                                                            <div>
                                                                {/* First line */}
                                                                <div className="fw-bold">
                                                                    ({remainingDays} day{remainingDays > 1 ? "s" : ""} remaining)
                                                                </div>

                                                                {/* Second line */}
                                                                <div>
                                                                    Employees can declare their IT savings plan anytime between{" "}
                                                                    <strong>{currentDate}</strong> and{" "}
                                                                    <strong>{formattedLockDate}</strong>.
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>

                                                )}

                                            </div>
                                        </div>

                                        {/* Manage Alerts */}
                                        <div className="card mb-6">
                                            <div className="card-header">
                                                <h5 className="card-title mb-0">Manage alerts for Employees</h5>
                                            </div>
                                            <div className="card-body">
                                                <p className="text-muted mb-4">
                                                    Let employer send mail notifications and reminders automatically based on the configured lock date.
                                                </p>

                                                <div className="form-check form-check-custom form-check-solid mb-4">
                                                    <Field
                                                        type="checkbox"
                                                        name="notifyWhenReleased"
                                                        id="notifyWhenReleased"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="notifyWhenReleased">
                                                        Notify when IT Declaration is RELEASED
                                                    </label>
                                                </div>

                                                <div className="form-check form-check-custom form-check-solid mb-4">
                                                    <Field
                                                        type="checkbox"
                                                        name="enableEmailReminders"
                                                        id="enableEmailReminders"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="enableEmailReminders">
                                                        Enable e-mail reminder to employees to submit before lock date
                                                    </label>
                                                </div>

                                                {/* Reminders */}
                                                <div className="mb-4">
                                                    <h6 className="fw-bold mb-3">Reminders</h6>

                                                    {reminders.map((reminder) => (
                                                        <div key={reminder.id} className="d-flex align-items-center mb-3">

                                                            {/* Checkbox */}
                                                            <div className="form-check form-check-custom form-check-solid me-3">
                                                                <input
                                                                    type="checkbox"
                                                                    className="form-check-input"
                                                                    checked={reminder.enabled}
                                                                    onChange={() => toggleReminder(reminder.id)}
                                                                />
                                                            </div>

                                                            <div className="me-3">Send Reminder</div>

                                                            {/* Days input */}
                                                            <div className="me-1">
                                                                <input
                                                                    type="number"
                                                                    className="form-control form-control-sm"
                                                                    style={{ width: "70px" }}
                                                                    value={reminder.days}
                                                                    min="1"
                                                                    max="30"
                                                                    disabled={!reminder.enabled}
                                                                    onChange={(e) =>
                                                                        updateReminderDays(reminder.id, e.target.value)
                                                                    }
                                                                />
                                                            </div>

                                                            <div className="me-3 text-muted">
                                                                day(s) before the lock date
                                                            </div>

                                                            {/* ❌ Remove */}
                                                            <span
                                                                className="cursor-pointer ms-2"
                                                                onClick={() => removeReminder(reminder.id)}
                                                                title="Remove reminder"
                                                            >
                                                                <CloseOutlined
                                                                    style={{
                                                                        color: "#f1416c",
                                                                        fontSize: "16px",
                                                                        cursor: "pointer"
                                                                    }}
                                                                />
                                                            </span>

                                                        </div>
                                                    ))}

                                                    <button
                                                        type="button"
                                                        className="btn btn-sm btn-light-primary"
                                                        onClick={addReminder}
                                                    >
                                                        <i className="bi bi-plus me-1"></i> Add Reminder
                                                    </button>
                                                </div>




                                                <div className="form-check form-check-custom form-check-solid mt-4">
                                                    <Field
                                                        type="checkbox"
                                                        name="notifyWhenLocked"
                                                        id="notifyWhenLocked"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="notifyWhenLocked">
                                                        Notify when IT Declaration is LOCKED
                                                    </label>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Other Configurations - Released State */}
                                        {/* <div className="card mb-6"> */}
                                            {/* <div className="card-header">
                                                <h5 className="card-title mb-0">Other Configurations</h5>
                                            </div> */}
                                            {/* <div className="card-body"> */}
                                                {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                                    <Field
                                                        type="checkbox"
                                                        name="allowSwitchTaxRegime"
                                                        id="allowSwitchTaxRegime"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold" htmlFor="allowSwitchTaxRegime">
                                                        Allow employees to switch tax regimes
                                                    </label>
                                                </div> */}

                                                {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <Field
                                                            type="checkbox"
                                                            name="allowTDSModification"
                                                            id="allowTDSModification"
                                                            className="form-check-input"
                                                        />
                                                        <label className="form-check-label fw-semibold" htmlFor="allowTDSModification">
                                                            Allow TDS modification to exceed the current fiscal year's calculated tax amount
                                                        </label>
                                                        <span
                                                            className="svg-icon svg-icon-2 svg-icon-primary cursor-pointer"
                                                            onClick={() => setShowInfoBox(!showInfoBox)}
                                                        >
                                                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                                                <path
                                                                    opacity="0.3"
                                                                    d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
                                                                    fill="currentColor"
                                                                />
                                                                <rect opacity="0.3" x="11" y="10" width="2" height="7" rx="1" fill="currentColor" />
                                                                <rect x="11" y="7" width="2" height="2" rx="1" fill="currentColor" />
                                                            </svg>
                                                        </span>
                                                    </div>
                                                </div> */}

                                                {/* {showInfoBox && (
                                                    <div className="alert alert-info mt-3">
                                                        <div className="d-flex">
                                                            <i className="bi bi-info-circle me-2"></i>
                                                            <div>
                                                                Changes made here will also apply to POI preferences only when the TDS modification is enabled in POI preferences.
                                                            </div>
                                                        </div>
                                                    </div>
                                                )} */}
                                            {/* </div> */}
                                        {/* </div> */}

                                        {/* Action Buttons */}
                                        <div className="d-flex justify-content-between border-top pt-6">
                                            <div className="d-flex gap-3">
                                                <button
                                                    type="button"
                                                    className="btn btn-lg btn-light"
                                                    onClick={() => {
                                                        setLockDate(originalLockDate);
                                                        setReminders([...originalReminders]);
                                                        setDefaultTaxRegime(originalFormValues.defaultTaxRegime);
                                                        resetForm({ values: originalFormValues });
                                                        successMsg("Reset", "All changes have been reset to original values", false);
                                                    }}
                                                    disabled={loading}
                                                >
                                                    Cancel
                                                </button>
                                                <button
                                                    type="button"
                                                    className="btn btn-lg btn-danger"
                                                    onClick={handleLockDeclaration}
                                                    disabled={loading}
                                                >
                                                    {loading ? (
                                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                                    ) : null}
                                                    Lock IT Declaration
                                                </button>
                                            </div>
                                            <button
                                                type="submit"
                                                className="btn btn-lg btn-primary"
                                                disabled={isSubmitting || loading}
                                            >
                                                {isSubmitting || loading ? (
                                                    <>
                                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                                        Saving...
                                                    </>
                                                ) : "Save"}
                                            </button>
                                        </div>
                                    </>
                                )}
                            </Form>
                        )}
                    </Formik>
                );

            case "proof-investment":
                return <ProofOfInvestmentSettings />;

            case "tax-slab-regime":
                return <TaxSlabRegimeSettings />;

            case "flexible-benefit":
            case "reimbursement":
            default:
                return (
                    <div className="text-center py-10">
                        <h3 className="text-muted">Coming Soon</h3>
                        <p className="text-muted">This feature is under development.</p>
                    </div>
                );
        }
    };

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Claims and Declarations</title>
            </Helmet>

            {/* Lock Confirmation Modal - Only for IT Declaration */}
            {showLockConfirmation && activeTab === "income-tax" && (
                <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">Confirm Lock</h5>
                                <button
                                    type="button"
                                    className="btn-close"
                                    onClick={() => setShowLockConfirmation(false)}
                                ></button>
                            </div>
                            <div className="modal-body">
                                <p>Are you sure you want to lock IT Declaration? This action cannot be undone.</p>
                            </div>
                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn btn-light"
                                    onClick={() => setShowLockConfirmation(false)}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-danger"
                                    onClick={confirmLockDeclaration}
                                >
                                    Lock IT Declaration
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Claims and Declarations</h5>
            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
                        <div className="w-100" style={{ maxWidth: "800px" }}>

                            {/* Tabs Navigation - Updated with Keentheme classes */}
                            <ul className="nav nav-tabs nav-line-tabs nav-line-tabs-2x mb-5 fs-6">
                                {tabs.map((tab, index) => (
                                    <li key={tab.id} className="nav-item">
                                        <a
                                            className={`nav-link ${activeTab === tab.id ? 'active' : ''}`}
                                            data-bs-toggle="tab"
                                            href={`#tab-${tab.id}`}
                                            onClick={(e) => {
                                                e.preventDefault();
                                                handleTabChange(tab.id);
                                            }}
                                        >
                                            {tab.label}
                                        </a>
                                    </li>
                                ))}
                            </ul>

                            {/* Tab Content */}
                            <div className="tab-content" id="myTabContent">
                                {tabs.map((tab, index) => (
                                    <div
                                        key={tab.id}
                                        className={`tab-pane fade ${activeTab === tab.id ? 'show active' : ''}`}
                                        id={`tab-${tab.id}`}
                                        role="tabpanel"
                                    >
                                        {activeTab === tab.id && renderTabContent()}
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {loading && <Loader />}
        </>
    );
}