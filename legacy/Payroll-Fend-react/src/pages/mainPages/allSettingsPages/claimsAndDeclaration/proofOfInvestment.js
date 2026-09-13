// proofOfInvestment.js
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field } from "formik";
import * as Yup from "yup";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import itdeclaration2 from "../../../../assets/images/itdeclaration2.png";
import { CloseOutlined } from "@ant-design/icons";


export default function ProofOfInvestmentSettings() {
    const [isLocked, setIsLocked] = useState(true);
    const [showInfoBox, setShowInfoBox] = useState(false);
    const [showTDSInfo, setShowTDSInfo] = useState(false);
    const [lockDate, setLockDate] = useState("");
    const [reminders, setReminders] = useState([
        { id: 1, days: 5, enabled: true },
        { id: 2, days: 1, enabled: true }
    ]);
    const [loading, setLoading] = useState(false);
    const [showLockConfirmation, setShowLockConfirmation] = useState(false);
    const [selectedMonth, setSelectedMonth] = useState("03");
    const [initialData, setInitialData] = useState(null);
    const [isInitialLoad, setIsInitialLoad] = useState(true);
    const [originalLockDate, setOriginalLockDate] = useState("");
    const [originalReminders, setOriginalReminders] = useState([]);
    const [originalFormValues, setOriginalFormValues] = useState({});
    const [originalSelectedMonth, setOriginalSelectedMonth] = useState("03");

    // Get organizationId from localStorage
    const organizationId = localStorage.getItem("organizationId");

    // Months for dropdown
    const months = [
        { value: "01", label: "January" },
        { value: "02", label: "February" },
        { value: "03", label: "March" },
        { value: "04", label: "April" },
        { value: "05", label: "May" },
        { value: "06", label: "June" },
        { value: "07", label: "July" },
        { value: "08", label: "August" },
        { value: "09", label: "September" },
        { value: "10", label: "October" },
        { value: "11", label: "November" },
        { value: "12", label: "December" }
    ];




    const getDaysDifference = (startDate, endDate) => {
        if (!startDate || !endDate) return 0;

        const start = new Date(startDate);
        const end = new Date(endDate);

        start.setHours(0, 0, 0, 0);
        end.setHours(0, 0, 0, 0);

        const diffTime = end - start;
        return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    };


    const todayISO = new Date().toISOString().split("T")[0];

    const remainingDays = lockDate
        ? getDaysDifference(todayISO, lockDate)
        : 0;


    // Fetch Proof of Investment data on component mount
    useEffect(() => {
        fetchProofOfInvestment();
    }, []);

    const fetchProofOfInvestment = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/proof-of-investment`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                const data = response.data.data;
                setInitialData(data);

                // Update state from API response
                setIsLocked(data.poiLocked);
                setLockDate(data.lastDateForPoi || "");
                setOriginalLockDate(data.lastDateForPoi || "");

                // Set selected month
                const monthValue = getMonthValue(data.monthToConsiderPoi || "march");
                setSelectedMonth(monthValue);
                setOriginalSelectedMonth(monthValue);

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
                    allowSwitchTaxRegime: data.canChangeTaxRegimePoi || false,
                    allowTDSModificationDuringPayroll: data.canTdsExceedAnnualLimit || false,
                    allowTDSModificationExceed: data.canTdsExceedAnnualLimit || false,
                    mandateProofAttachments: data.attachmentMandatoryPoiForPortal || false,
                    mandateReviewerComments: data.commentsMandatoryForPoiApproval || false,
                    notifyWhenReleased: data.sendMailOnPoiRelease || true,
                    notifyWhenLocked: data.sendMailOnPoiLock || true,
                    enableEmailReminders: data.anyReminderBeforeLockdateEnabled || true
                };
                setOriginalFormValues(originalValues);

                setIsInitialLoad(false);
            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error:", error);

            if (error.response) {
                errorMsg("Error", error.response.data?.message || "Failed to load Proof of Investment settings", true);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setLoading(false);
        }
    };

    // Helper function to convert month name to value
    const getMonthValue = (monthName) => {
        const monthMap = {
            "january": "01", "february": "02", "march": "03", "april": "04",
            "may": "05", "june": "06", "july": "07", "august": "08",
            "september": "09", "october": "10", "november": "11", "december": "12"
        };
        return monthMap[monthName.toLowerCase()] || "03";
    };

    // Helper function to convert month value to name
    const getMonthName = (monthValue) => {
        const month = months.find(m => m.value === monthValue);
        return month ? month.label.toLowerCase() : "march";
    };

    // Handle lock date change
    const handleLockDateChange = (e) => {
        const date = e.target.value;
        setLockDate(date);
    };

    // Handle month selection change
    const handleMonthChange = (e) => {
        setSelectedMonth(e.target.value);
    };

    // Add reminder
    const addReminder = () => {
        const newId = reminders.length > 0 ? Math.max(...reminders.map(r => r.id)) + 1 : 1;
        setReminders([...reminders, { id: newId, days: 3, enabled: true }]);
    };

    // Remove reminder
    const removeReminder = (id) => {
        setReminders(reminders.filter(r => r.id !== id));
    };

    // Update reminder days
    const updateReminderDays = (id, days) => {
        setReminders(reminders.map(r =>
            r.id === id ? { ...r, days: parseInt(days) || 0 } : r
        ));
    };

    // Toggle reminder
    const toggleReminder = (id) => {
        setReminders(reminders.map(r =>
            r.id === id ? { ...r, enabled: !r.enabled } : r
        ));
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
                canTdsExceedAnnualLimit: values.allowTDSModificationExceed,
                poiLocked: isLocked,
                canChangeTaxRegimePoi: values.allowSwitchTaxRegime,
                lastDateForPoi: lockDate,
                sendMailOnEmployeeLevelPoiLockAndRelease: false, // Default value
                currentPayrun: initialData?.currentPayrun || "",
                sendMailOnPoiLock: values.notifyWhenLocked,
                payscheduleConfigured: initialData?.payscheduleConfigured || false,
                sendMailOnPoiRelease: values.notifyWhenReleased,
                anyReminderBeforeLockdateEnabled: values.enableEmailReminders,
                attachmentMandatoryPoiForPortal: values.mandateProofAttachments,
                commentsMandatoryForPoiApproval: values.mandateReviewerComments,
                attachmentEnabledForPoi: true, // Default value
                canItOverrideInProofMode: false, // Default value
                monthFormatted: months.find(m => m.value === selectedMonth)?.label || "March",
                monthToConsiderPoi: getMonthName(selectedMonth),
                panMandatoryForAnnualRentOverOneLakh: false // Default value
            };

            console.log("Submitting data:", requestData);

            // Make API call
            const response = await axios.put(`${GlobalConst.API_URL}/api/proof-of-investment`,
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
                successMsg("Success", "Proof of Investment settings updated successfully", false);

                // Update initial data with new values
                setInitialData(response.data.data);

                // Update original values after successful save
                setOriginalLockDate(lockDate);
                setOriginalReminders([...reminders]);
                setOriginalSelectedMonth(selectedMonth);
                setOriginalFormValues(values);

                // If we were updating from locked to released state, refresh the data
                if (isLocked !== requestData.poiLocked) {
                    fetchProofOfInvestment();
                }
            } else {
                errorMsg("Error", response.data?.message || "Failed to update Proof of Investment settings", false);
            }
        } catch (error) {
            console.error("Update Error:", error);

            if (error.response) {
                errorMsg("Update Failed", error.response.data?.message || "Failed to update Proof of Investment settings", false);
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
        setSelectedMonth(originalSelectedMonth);

        // Show confirmation message
        successMsg("Reset", "All changes have been reset to original values", false);
    };

    // Handle lock Proof of Investment
    const handleLockPoi = async () => {
        setShowLockConfirmation(true);
    };

    // Confirm lock Proof of Investment
    const confirmLockPoi = async () => {
        try {
            setLoading(true);
            setShowLockConfirmation(false);

            // Prepare data for lock action
            const requestData = {
                reminders: initialData?.reminders || [],
                canTdsExceedAnnualLimit: initialData?.canTdsExceedAnnualLimit || false,
                poiLocked: true, // Set to locked
                canChangeTaxRegimePoi: initialData?.canChangeTaxRegimePoi || true,
                lastDateForPoi: lockDate,
                sendMailOnEmployeeLevelPoiLockAndRelease: false,
                currentPayrun: initialData?.currentPayrun || "",
                sendMailOnPoiLock: true,
                payscheduleConfigured: initialData?.payscheduleConfigured || false,
                sendMailOnPoiRelease: initialData?.sendMailOnPoiRelease || true,
                anyReminderBeforeLockdateEnabled: initialData?.anyReminderBeforeLockdateEnabled || true,
                attachmentMandatoryPoiForPortal: initialData?.attachmentMandatoryPoiForPortal || false,
                commentsMandatoryForPoiApproval: initialData?.commentsMandatoryForPoiApproval || false,
                attachmentEnabledForPoi: initialData?.attachmentEnabledForPoi || true,
                canItOverrideInProofMode: initialData?.canItOverrideInProofMode || false,
                monthFormatted: initialData?.monthFormatted || "March",
                monthToConsiderPoi: initialData?.monthToConsiderPoi || "march",
                panMandatoryForAnnualRentOverOneLakh: initialData?.panMandatoryForAnnualRentOverOneLakh || false
            };

            const response = await axios.put(`${GlobalConst.API_URL}/api/proof-of-investment`,
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
                successMsg("Success", "Proof of Investment has been locked successfully.", false);

                // Update original values
                setOriginalLockDate(lockDate);
                setOriginalSelectedMonth(selectedMonth);
                setOriginalFormValues({
                    allowSwitchTaxRegime: response.data.data.canChangeTaxRegimePoi || false,
                    allowTDSModificationDuringPayroll: response.data.data.canTdsExceedAnnualLimit || false,
                    allowTDSModificationExceed: response.data.data.canTdsExceedAnnualLimit || false,
                    mandateProofAttachments: response.data.data.attachmentMandatoryPoiForPortal || false,
                    mandateReviewerComments: response.data.data.commentsMandatoryForPoiApproval || false,
                    notifyWhenReleased: response.data.data.sendMailOnPoiRelease || true,
                    notifyWhenLocked: response.data.data.sendMailOnPoiLock || true,
                    enableEmailReminders: response.data.data.anyReminderBeforeLockdateEnabled || true
                });
            } else {
                errorMsg("Error", "Failed to lock Proof of Investment", false);
            }
        } catch (error) {
            console.error("Lock Error:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to lock Proof of Investment", false);
        } finally {
            setLoading(false);
        }
    };

    // Handle release Proof of Investment - Clear date and reset changes
    const handleReleasePoi = () => {
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

    // Get selected month label
    const getSelectedMonthLabel = () => {
        const month = months.find(m => m.value === selectedMonth);
        return month ? month.label : "March";
    };

    // Initial form values from API data
    const getInitialValues = () => {
        if (isInitialLoad && !initialData) {
            return {
                allowSwitchTaxRegime: false,
                allowTDSModificationDuringPayroll: false,
                allowTDSModificationExceed: false,
                mandateProofAttachments: false,
                mandateReviewerComments: false,
                notifyWhenReleased: true,
                notifyWhenLocked: true,
                enableEmailReminders: true
            };
        }

        return {
            allowSwitchTaxRegime: initialData?.canChangeTaxRegimePoi || false,
            allowTDSModificationDuringPayroll: initialData?.canTdsExceedAnnualLimit || false,
            allowTDSModificationExceed: initialData?.canTdsExceedAnnualLimit || false,
            mandateProofAttachments: initialData?.attachmentMandatoryPoiForPortal || false,
            mandateReviewerComments: initialData?.commentsMandatoryForPoiApproval || false,
            notifyWhenReleased: initialData?.sendMailOnPoiRelease || true,
            notifyWhenLocked: initialData?.sendMailOnPoiLock || true,
            enableEmailReminders: initialData?.anyReminderBeforeLockdateEnabled || true
        };
    };

    // Validation schema
    const validationSchema = Yup.object().shape({
        allowSwitchTaxRegime: Yup.boolean(),
        allowTDSModificationDuringPayroll: Yup.boolean(),
        allowTDSModificationExceed: Yup.boolean(),
        mandateProofAttachments: Yup.boolean(),
        mandateReviewerComments: Yup.boolean(),
        notifyWhenReleased: Yup.boolean(),
        notifyWhenLocked: Yup.boolean(),
        enableEmailReminders: Yup.boolean()
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

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Proof of Investments</title>
            </Helmet>

            {/* Lock Confirmation Modal */}
            {showLockConfirmation && (
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
                                <p>Are you sure you want to lock Proof of Investments? This action cannot be undone.</p>
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
                                    onClick={confirmLockPoi}
                                >
                                    Lock Proof of Investments
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            <Formik
                initialValues={getInitialValues()}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize={!isInitialLoad}
            >
                {({ isSubmitting, values, setFieldValue, resetForm }) => (
                    <Form className="form w-100">
                        {/* Show locked status on front page */}
                        {isLocked && initialData?.poiLocked && (
                            <div className={`alert ${initialData?.poiLocked ? 'alert-danger' : 'alert-primary'} d-flex align-items-center p-4 mb-6`}>
                                <i className={`bi ${initialData?.poiLocked ? 'bi-lock-fill' : 'bi-unlock-fill'} fs-2 me-3`}></i>
                                <div className="d-flex flex-column">
                                    <span className={`fw-bold fs-6 ${initialData?.poiLocked ? 'text-danger' : 'text-primary'}`}>
                                        {initialData?.poiLocked ? 'Proof of Investments Is Locked' : 'Proof of Investments Is Released'}
                                    </span>
                                </div>
                            </div>
                        )}

                        {/* {!isLocked && (
                            <div className="alert alert-info d-flex align-items-center p-4 mb-6">
                                <i className="bi bi-info-circle-fill fs-2 me-3"></i>
                                <div className="d-flex flex-column">
                                    <span className="fw-bold fs-6">New tax regime will be used for TDS calculation for employees who are not submitting their POI for this year.</span>
                                </div>
                            </div>
                        )} */}

                        {/* Introduction Text */}
                        <div className="fv-row mb-6">
                            <div className="text-gray-700 fs-6">
                                Employees can submit the necessary supporting documents for their declared investments {!isLocked ? "once you enable this option. For employees without portal, you can submit it on their behalf under Employees > Employee profile > Investment > Proof of Investments." : "through the employee portal once you enable this option."}
                            </div>
                        </div>

                        {/* Help Links */}
                        {/* <div className="fv-row mb-8">
                            <div className="d-flex flex-column gap-2">
                                <a href="#" className="text-primary fw-semibold fs-7 text-hover-primary">
                                    Learn how to manage investment proofs. POI Help Document
                                </a>
                               
                            </div>
                        </div> */}

                        {isLocked ? (
                            /* LOCKED STATE */
                            <>
                                {/* Locked Image Placeholder */}
                                <div className="text-center my-4">
                                    <img src={itdeclaration2} alt="Locked" className="mw-100 h-200px h-sm-325px" />
                                </div>
                                <div className="card-body p-6">
                                    <div className="d-flex align-items-center mb-4">
                                        <i className="bi bi-lock-fill text-danger fs-2 me-3"></i>
                                        <div>
                                            <h3 className="fw-bold text-danger mb-1 text-center">Proof of Investments Is Locked</h3>
                                            <p className="text-gray-700 mb-0">
                                                You are yet to enable submission of investment proofs for your employees through their respective portals.
                                                Release POI or submit it on their behalf under Employees {">"} Employee profile{">"} Investments {">"} Proof of Investments.
                                            </p>
                                        </div>
                                    </div>

                                    <div className="text-center">
                                        <button
                                            type="button"
                                            className="btn btn-lg btn-primary"
                                            onClick={handleReleasePoi}
                                            disabled={loading}
                                        >
                                            {loading ? (
                                                <span className="spinner-border spinner-border-sm me-2"></span>
                                            ) : null}
                                            Release Proof of Investments
                                        </button>
                                    </div>
                                </div>

                                {/* Process Payroll Section */}
                                <div className="card mb-6">

                                    <div className="card-body">
                                          <div className="card-header">
                                        <h5 className="card-title mb-0">TDS Deduction Effective Month</h5>
                                    </div>
                                        <p className="text-muted mb-3">
                                           Income tax (TDS) will be calculated and deducted from the employee’s salary through payroll processing starting from the {getSelectedMonthLabel()} and will continue until the end of the financial year (March).
                                        </p>
                                        <div className="row">
                                            <div className="col-md-6">
                                                <label htmlFor="processMonth" className="form-label fw-semibold">
                                                    Select Month
                                                </label>
                                                <select
                                                    id="processMonth"
                                                    className="form-select form-select-lg"
                                                    value={selectedMonth}
                                                    onChange={handleMonthChange}
                                                >
                                                    {months.map((month) => (
                                                        <option key={month.value} value={month.value}>
                                                            {month.label}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>
                                        </div>

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
                                            <Field
                                                type="checkbox"
                                                name="allowTDSModificationDuringPayroll"
                                                id="allowTDSModificationDuringPayroll"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="allowTDSModificationDuringPayroll">
                                                Allow TDS modification during Payroll
                                            </label>
                                        </div> */}

                                        {/* {values.allowTDSModificationDuringPayroll && (
                                            <div className="form-check form-check-custom form-check-solid mb-3 ms-4">
                                                <div className="d-flex align-items-center">
                                                    <Field
                                                        type="checkbox"
                                                        name="allowTDSModificationExceed"
                                                        id="allowTDSModificationExceed"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold ms-2" htmlFor="allowTDSModificationExceed">
                                                        Allow TDS modification to exceed the current fiscal year's calculated tax amount
                                                    </label>
                                                    <button
                                                        type="button"
                                                        className="btn btn-icon btn-sm btn-light ms-2"
                                                        onClick={() => setShowTDSInfo(!showTDSInfo)}
                                                    >
                                                        <i className="bi bi-info-circle"></i>
                                                    </button>
                                                </div>
                                                {showTDSInfo && (
                                                    <div className="alert alert-info mt-3">
                                                        <div className="d-flex">
                                                            <i className="bi bi-info-circle me-2"></i>
                                                            <div>
                                                                Changes made here will also apply to IT preferences.
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        )} */}

                                        {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                            <Field
                                                type="checkbox"
                                                name="mandateProofAttachments"
                                                id="mandateProofAttachments"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="mandateProofAttachments">
                                                Mandate investment proof attachments for POI submission
                                            </label>
                                        </div> */}

                                        {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                            <Field
                                                type="checkbox"
                                                name="mandateReviewerComments"
                                                id="mandateReviewerComments"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="mandateReviewerComments">
                                                Mandate reviewer comments for partial investment amount approval
                                            </label>
                                        </div> */}
                                    {/* </div> */}
                                {/* </div> */}

                                <div className="d-flex justify-content-end gap-3">
                                    <button
                                        type="button"
                                        className="btn btn-lg btn-light"
                                        onClick={() => {
                                            handleCancel();
                                            resetForm({ values: originalFormValues });
                                            setSelectedMonth(originalSelectedMonth);
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
                                                checked={true}
                                                onChange={(e) => setFieldValue("allowSubmission", e.target.checked)}
                                            /> */}
                                            <label className="form-check-label fw-bold fs-6" htmlFor="allowSubmission">
                                                Employees submit POI through the employee portal and lock on the selected date
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
                                                            Employees can submit their proof of investments anytime between{" "}
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
                                                Notify when POI Submission is RELEASED
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
                                                    <div className="form-check form-check-custom form-check-solid me-3">
                                                        <input
                                                            type="checkbox"
                                                            className="form-check-input"
                                                            checked={reminder.enabled}
                                                            onChange={() => toggleReminder(reminder.id)}
                                                        />
                                                    </div>
                                                    <div className="me-3">Send Reminder</div>
                                                    <div className="me-1">
                                                        <input
                                                            type="number"
                                                            className="form-control form-control-sm"
                                                            style={{ width: "70px" }}
                                                            value={reminder.days}
                                                            onChange={(e) => updateReminderDays(reminder.id, e.target.value)}
                                                            min="1"
                                                            max="30"
                                                        />
                                                    </div>
                                                    <div className="me-3">day(s) before the lock date</div>
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
                                                Notify when POI Submission is LOCKED
                                            </label>
                                        </div>
                                    </div>
                                </div>

                                {/* Process Payroll Section */}
                                <div className="card mb-6">
                                    <div className="card-header">
                                        <h5 className="card-title mb-0">TDS Deduction Effective Month</h5>
                                    </div>
                                    <div className="card-body">
                                        <p className="text-muted mb-3">
                                           Income tax (TDS) will be calculated and deducted from the employee’s salary through payroll processing starting from the {getSelectedMonthLabel()} and will continue until the end of the financial year (March).
                                        </p>
                                        <div className="row">
                                            <div className="col-md-6">
                                                <label htmlFor="processMonth" className="form-label fw-semibold">
                                                    Select Month
                                                </label>
                                                <select
                                                    id="processMonth"
                                                    className="form-select form-select-lg"
                                                    value={selectedMonth}
                                                    onChange={handleMonthChange}
                                                >
                                                    {months.map((month) => (
                                                        <option key={month.value} value={month.value}>
                                                            {month.label}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>
                                        </div>
                                        {/* <div className="text-muted mt-2">
                                            for upcoming years.
                                        </div> */}
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
                                            <Field
                                                type="checkbox"
                                                name="allowTDSModificationDuringPayroll"
                                                id="allowTDSModificationDuringPayroll"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="allowTDSModificationDuringPayroll">
                                                Allow TDS modification during Payroll
                                            </label>
                                        </div> */}

                                        {/* {values.allowTDSModificationDuringPayroll && (
                                            <div className="form-check form-check-custom form-check-solid mb-3 ms-4">
                                                <div className="d-flex align-items-center">
                                                    <Field
                                                        type="checkbox"
                                                        name="allowTDSModificationExceed"
                                                        id="allowTDSModificationExceed"
                                                        className="form-check-input"
                                                    />
                                                    <label className="form-check-label fw-semibold ms-2" htmlFor="allowTDSModificationExceed">
                                                        Allow TDS modification to exceed the current fiscal year's calculated tax amount
                                                    </label>
                                                    <button
                                                        type="button"
                                                        className="btn btn-icon btn-sm btn-light ms-2"
                                                        onClick={() => setShowInfoBox(!showInfoBox)}
                                                    >
                                                        <i className="bi bi-info-circle"></i>
                                                    </button>
                                                </div>
                                                {showInfoBox && (
                                                    <div className="alert alert-info mt-3">
                                                        <div className="d-flex">
                                                            <i className="bi bi-info-circle me-2"></i>
                                                            <div>
                                                                Changes made here will also apply to IT preferences.
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        )} */}

                                        {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                            <Field
                                                type="checkbox"
                                                name="mandateProofAttachments"
                                                id="mandateProofAttachments"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="mandateProofAttachments">
                                                Mandate investment proof attachments for POI submission
                                            </label>
                                        </div> */}

                                        {/* <div className="form-check form-check-custom form-check-solid mb-3">
                                            <Field
                                                type="checkbox"
                                                name="mandateReviewerComments"
                                                id="mandateReviewerComments"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="mandateReviewerComments">
                                                Mandate reviewer comments for partial investment amount approval
                                            </label>
                                        </div> */}
                                    {/* </div>
                                </div> */}

                                {/* Action Buttons */}
                                <div className="d-flex justify-content-between border-top pt-6">
                                    <div className="d-flex gap-3">
                                        <button
                                            type="button"
                                            className="btn btn-lg btn-light"
                                            onClick={() => {
                                                setLockDate(originalLockDate);
                                                setReminders([...originalReminders]);
                                                resetForm({ values: originalFormValues });
                                                setSelectedMonth(originalSelectedMonth);
                                                successMsg("Reset", "All changes have been reset to original values", false);
                                            }}
                                            disabled={loading}
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            type="button"
                                            className="btn btn-lg btn-danger"
                                            onClick={handleLockPoi}
                                            disabled={loading}
                                        >
                                            {loading ? (
                                                <span className="spinner-border spinner-border-sm me-2"></span>
                                            ) : null}
                                            Lock Proof of Investments
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

            {loading && <Loader />}
        </>
    );
}