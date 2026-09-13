import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Card, Dropdown, Space, Typography, Row, Col, Tag, Button, Badge, Table, Menu } from 'antd';
import { DownOutlined, CalendarOutlined, UserOutlined, DollarOutlined, EyeOutlined, FileExcelOutlined, MoreOutlined, } from '@ant-design/icons';
import { useNavigate } from "react-router-dom";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

import payRuns from '../../../assets/images/payRuns.jpg';
import payrollHistory from '../../../assets/images/payrollHistory.jpg';
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import _ from "lodash";
import axios from "axios";

export default function PayRuns() {
    const [fetchError, setFetchError] = useState(false);
    const navigate = useNavigate();
    const [selectedPayrollType, setSelectedPayrollType] = useState('All');
    const [payRunsData, setPayRunsData] = useState([]);
    const [filteredPayRunsData, setFilteredPayRunsData] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [signingIn, setSigningIn] = useState(false);
    const [payrollHistoryData, setPayrollHistoryData] = useState([]);
    const [isHistoryLoading, setIsHistoryLoading] = useState(false);
    const [downloadingReport, setDownloadingReport] = useState(false);
    const [currentDownloadingId, setCurrentDownloadingId] = useState(null);

    // Tab state and content
    const [activeTabKey, setActiveTabKey] = useState('payRuns');
    const [showOneTimePayoutForm, setShowOneTimePayoutForm] = useState(false);
    const [showOffCyclePayrunForm, setShowOffCyclePayrunForm] = useState(false);


    const [showTdsMessage, setShowTdsMessage] = useState(true);


    // Get organizationId from localStorage
    const organizationId = localStorage.getItem("organizationId") || "default-org-id";

    // Header dropdown items
    const headerDropdownItems = [
        {
            label: 'One Time Payout',
            key: 'one-time-payout',
        },
        {
            type: 'divider',
        },
        {
            label: 'Off Cycle Payrun',
            key: 'off-cycle-payrun',
        },
    ];

    const tabList = [
        {
            key: 'payRuns',
            tab: 'Pay Runs',
        },
        {
            key: 'payrollHistory',
            tab: 'Payroll History',
        },
    ];

    // Payroll type dropdown items
    const payrollTypeItems = [
        {
            key: 'All',
            label: 'All',
        },
        {
            key: 'Regular Payroll',
            label: 'Regular Payroll',
        },
        {
            key: 'Past Payroll',
            label: 'Past Payroll',
        },
        {
            key: 'Final Settlement Payroll',
            label: 'Final Settlement Payroll',
        },
        {
            key: 'One Time Payout',
            label: 'One Time Payout',
        },
        {
            key: 'Off Cycle Payroll',
            label: 'Off Cycle Payroll',
        },
        {
            key: 'Bulk Final Settlement Payroll',
            label: 'Bulk Final Settlement Payroll',
        },
        {
            key: 'Resettlement Payroll',
            label: 'Resettlement Payroll',
        },
    ];

    // Filter buttons for pay runs
    const filterButtons = [
        { key: 'All', label: 'All Pending', count: 3 },
        { key: 'Regular Payroll', label: 'Regular Payroll', count: 1 },
        { key: 'One Time Payout', label: 'One Time Payout', count: 1 },
        { key: 'Off Cycle Payroll', label: 'Off Cycle Payroll', count: 1 },
    ];

    const fetchPayRuns = async () => {
        try {
            setIsLoading(true);
            setSigningIn(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/payruns`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            // Check if the response has the expected structure
            if (response.data && (response.data.payrollRuns || response.data.readyStatePayrollRuns)) {
                const data = response.data;
                const payrollRuns = data.payrollRuns || [];
                const readyState = data.readyStatePayrollRuns;

                // Transform API response into frontend format for payrollRuns
                const formattedRuns = payrollRuns.map((run) => ({
                    id: run.payrunId,
                    type: getPayrollTypeDisplayName(run.type),
                    status: run.status,
                    netPay: run.payrollTotal || 0,
                    paymentDate: run.payDate,
                    employeeCount: run.noOfEmployees,
                    currency: "₹",
                    dueMessage: run.statusInfo || "",
                    paymentDue: run.paymentDue || false,
                    payPeriodStartDate: run.payPeriodStartDate,
                    payPeriodEndDate: run.payPeriodEndDate,
                    processingPeriod: run.processingPeriod,
                    approvalType: run.approvalType,
                    approvalDetails: run.approvalDetails,
                    compensationName: run.compensationName
                }));

                // Add readyState payroll run if it exists
                let allRuns = [...formattedRuns];

                if (readyState) {
                    const readyStateRun = {
                        id: "ready-state",
                        type: getPayrollTypeDisplayName(readyState.type),
                        status: readyState.status,
                        netPay: readyState.payrollTotal || 0,
                        paymentDate: readyState.payDate,
                        employeeCount: readyState.noOfEmployees,
                        currency: "₹",
                        dueMessage: readyState.statusInfo || "",
                        paymentDue: readyState.paymentDue || false,
                        payPeriodStartDate: readyState.payPeriodStartDate,
                        payPeriodEndDate: readyState.payPeriodEndDate,
                        processingPeriod: readyState.processingPeriod,
                        isReadyState: true
                    };
                    allRuns = [readyStateRun, ...formattedRuns];
                }

                console.log("Fetched Pay Runs:", allRuns);

                setPayRunsData(allRuns);
                setFilteredPayRunsData(allRuns);
                setFetchError(false);
            } else {
                setFetchError(true);
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            setFetchError(true);
            console.error("API Error:", error);

            if (error.response) {
                errorMsg("Error", error.response.data?.message || "Failed to load pay runs", true);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setIsLoading(false);
            setSigningIn(false);
        }
    };

    const fetchPayrollHistory = async () => {
        try {
            setIsHistoryLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/payruns/completed`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.payrollRuns) {
                const formattedHistory = response.data.payrollRuns.map((run) => ({
                    key: run.payrunId,
                    payrunId: run.payrunId,
                    payDate: run.payDate,
                    type: getPayrollTypeDisplayName(run.type),
                    payPeriod: `${formatDate(run.payPeriodStartDate)} - ${formatDate(run.payPeriodEndDate)}`,
                    payPeriodStartDate: run.payPeriodStartDate,
                    payPeriodEndDate: run.payPeriodEndDate,
                    status: run.status,
                    paymentStatus: run.paymentStatus,
                    netPay: run.payrollTotal,
                    employeeCount: run.noOfEmployees,
                    processingPeriod: run.processingPeriod,
                    currency: "₹"
                }));

                setPayrollHistoryData(formattedHistory);
            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error fetching payroll history:", error);

            if (error.response) {
                errorMsg("Error", error.response.data?.message || "Failed to load payroll history", true);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setIsHistoryLoading(false);
        }
    };

    // Function to download Excel report for a specific payrun
    const downloadPayrunReport = async (payrunId) => {
        try {
            setDownloadingReport(true);
            setCurrentDownloadingId(payrunId);

            const token = localStorage.getItem("__t");
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/payruns/${payrunId}/report/excel`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId
                    },
                    responseType: 'blob' // Important for file download
                }
            );

            // Create a blob from the response data
            const blob = new Blob([response.data], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            });

            // Create download link
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;

            // Get filename from content-disposition header or use default
            const contentDisposition = response.headers['content-disposition'];
            let filename = `PayRun_Report_${payrunId}.xlsx`;

            if (contentDisposition) {
                const filenameMatch = contentDisposition.match(/filename="?(.+)"?/);
                if (filenameMatch && filenameMatch.length === 2) {
                    filename = filenameMatch[1];
                }
            }

            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();

            // Clean up
            link.parentNode.removeChild(link);
            window.URL.revokeObjectURL(url);

            successMsg("Success", "Report downloaded successfully", false);
        } catch (error) {
            console.error("Error downloading report:", error);

            if (error.response) {
                // Handle specific error cases
                if (error.response.status === 404) {
                    errorMsg("Error", "Report not found for this pay run", true);
                } else if (error.response.status === 500) {
                    errorMsg("Error", "Error generating report. Please try again later.", true);
                } else {
                    errorMsg("Error", error.response.data?.message || "Failed to download report", true);
                }
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setDownloadingReport(false);
            setCurrentDownloadingId(null);
        }
    };

    // Helper function to convert API type to display name
    const getPayrollTypeDisplayName = (type) => {
        if (!type) return 'Unknown';

        const typeMap = {
            'regular': 'Regular Payroll',
            'one-time payout': 'One Time Payout',
            'off cycle payroll': 'Off Cycle Payroll',
            'past payroll': 'Past Payroll',
            'final settlement payroll': 'Final Settlement Payroll',
            'bulk final settlement payroll': 'Bulk Final Settlement Payroll',
            'resettlement payroll': 'Resettlement Payroll'
        };

        return typeMap[type.toLowerCase()] || type;
    };

    // Reverse mapping for sending data to backend
    const getBackendPayrollType = (displayName) => {
        if (!displayName) return 'regular';

        const reverseTypeMap = {
            'Regular Payroll': 'regular',
            'One Time Payout': 'one-time payout',
            'Off Cycle Payroll': 'off cycle payroll',
            'Past Payroll': 'past payroll',
            'Final Settlement Payroll': 'final settlement payroll',
            'Bulk Final Settlement Payroll': 'bulk final settlement payroll',
            'Resettlement Payroll': 'resettlement payroll'
        };

        return reverseTypeMap[displayName] || displayName.toLowerCase();
    };

    useEffect(() => {
        fetchPayRuns();
    }, []);

    useEffect(() => {
        if (activeTabKey === 'payrollHistory') {
            fetchPayrollHistory();
        }
    }, [activeTabKey]);

    useEffect(() => {
        // Filter pay runs based on selected payroll type
        if (selectedPayrollType === 'All') {
            setFilteredPayRunsData(payRunsData);
        } else {
            const filtered = payRunsData.filter(payRun =>
                payRun.type === selectedPayrollType
            );
            setFilteredPayRunsData(filtered);
        }
    }, [selectedPayrollType, payRunsData]);

    const handleHeaderMenuClick = (e) => {
        if (e.key === 'one-time-payout') {
            setTimeout(() => {
                setShowOneTimePayoutForm(true);
            }, 100);
        } else if (e.key === 'off-cycle-payrun') {
            setTimeout(() => {
                setShowOffCyclePayrunForm(true);
            }, 100);
        }
    };

    const oneTimePayoutValidationSchema = Yup.object({
        component: Yup.string().required('Please select a component'),
        paymentDate: Yup.date()
            .required('Please select payment date')
            .min(new Date(), 'Payment date cannot be in the past')
    });

    const offCyclePayrunValidationSchema = Yup.object({
        paymentDate: Yup.date()
            .required('Please select payment date')
            .min(new Date(), 'Payment date cannot be in the past')
    });

    // Handle submit function for One Time Payout
    const handleOneTimePayoutSubmit = async (values, { setSubmitting, resetForm }) => {
        if (!_.isEmpty(values.component) && !_.isEmpty(values.paymentDate)) {
            setSubmitting(true);
            setSigningIn(true);
            try {
                const postData = {
                    component: values.component,
                    paymentDate: values.paymentDate
                };

                const response = await axios.post(
                    `${GlobalConst.API_URL}/api/payruns/create-one-time-payout`,
                    postData,
                    {
                        headers: {
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        },
                    }
                );

                if (response.data.status === 200 || response.data.status === 201) {
                    successMsg("Success", "One Time Payout created successfully", false);
                    resetForm();
                    setShowOneTimePayoutForm(false);
                    fetchPayRuns(); // Refresh the list
                    navigate("/addOneTimePayoutDetails");
                } else {
                    errorMsg(
                        "Creation Failed",
                        response.data?.message || `There was an error creating the one time payout. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (error) {
                console.error("API Error:", error);

                if (error.response) {
                    errorMsg(
                        "Creation Failed",
                        error.response.data?.message || 'One Time Payout creation failed',
                        false
                    );
                } else if (error.request) {
                    errorMsg(
                        "Network Error",
                        "Cannot connect to the server. Please check your connection.",
                        false
                    );
                } else {
                    errorMsg(
                        "Error",
                        "An unexpected error occurred",
                        false
                    );
                }
            } finally {
                setSubmitting(false);
                setSigningIn(false);
            }
        }
    };

    // Handle submit function for Off Cycle Payrun
    const handleOffCyclePayrunSubmit = async (values, { setSubmitting, resetForm }) => {
        if (!_.isEmpty(values.paymentDate)) {
            setSubmitting(true);
            setSigningIn(true);
            try {
                const postData = {
                    paymentDate: values.paymentDate
                };

                const response = await axios.post(
                    `${GlobalConst.API_URL}/api/payruns/create-off-cycle-payrun`,
                    postData,
                    {
                        headers: {
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        },
                    }
                );

                if (response.data.status === 200 || response.data.status === 201) {
                    successMsg("Success", "Off Cycle Payrun created successfully", false);
                    resetForm();
                    setShowOffCyclePayrunForm(false);
                    fetchPayRuns(); // Refresh the list
                    navigate("/addOffCycleDetails");
                } else {
                    errorMsg(
                        "Creation Failed",
                        response.data?.message || `There was an error creating the off cycle payrun. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
                        true
                    );
                }
            } catch (error) {
                console.error("API Error:", error);

                if (error.response) {
                    errorMsg(
                        "Creation Failed",
                        error.response.data?.message || 'Off Cycle Payrun creation failed',
                        false
                    );
                } else if (error.request) {
                    errorMsg(
                        "Network Error",
                        "Cannot connect to the server. Please check your connection.",
                        false
                    );
                } else {
                    errorMsg(
                        "Error",
                        "An unexpected error occurred",
                        false
                    );
                }
            } finally {
                setSubmitting(false);
                setSigningIn(false);
            }
        }
    };

    // Handle Create Payrun for READY status
    const handleCreatePayrun = async (payRunData, payRun) => {
        try {
            setSigningIn(true);

            // Prepare the data for creating payrun - convert display names to backend enum values
            const postData = {
                type: getBackendPayrollType(payRunData.type),
                payPeriodStartDate: payRunData.payPeriodStartDate,
                payPeriodEndDate: payRunData.payPeriodEndDate,
                payDate: payRunData.paymentDate,
                processingPeriod: payRunData.processingPeriod,
                employeeCount: payRunData.employeeCount
            };

            const response = await axios.post(
                `${GlobalConst.API_URL}/api/payruns`,
                postData,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId
                    },
                }
            );

            if (response.data.status === 200 || response.data.status === 201) {
                successMsg("Success", "Payrun created successfully", false);
                fetchPayRuns(); // Refresh the list
                // Navigate to preview page with the created payrun data
                const createdPayrun = response.data.payrollRun || response.data.data || response.data;
                const payrunId = createdPayrun?.payrunId || createdPayrun?.payRunId || createdPayrun?.payrollRunId;
                if (payrunId) {
                    navigate(`/preview/${payrunId}`, {
                        state: { payrunData: createdPayrun }
                    });
                } else {
                    errorMsg("Navigation Failed", "Created payrun id not returned by server.", true);
                }
            } else {
                errorMsg(
                    "Creation Failed",
                    response.data?.message || "Failed to create payrun",
                    true
                );
            }
        } catch (error) {
            console.error("Create Payrun Error:", error);

            if (error.response) {
                errorMsg(
                    "Creation Failed",
                    error.response.data?.message || "Failed to create payrun",
                    false
                );
            } else if (error.request) {
                errorMsg(
                    "Network Error",
                    "Cannot connect to the server. Please check your connection.",
                    false
                );
            } else {
                errorMsg(
                    "Error",
                    "An unexpected error occurred",
                    false
                );
            }
        } finally {
            setSigningIn(false);
        }
    };

    const handlePayrollTypeSelect = ({ key }) => {
        setSelectedPayrollType(key);
    };

    const handleFilterButtonClick = (filterKey) => {
        setSelectedPayrollType(filterKey);
    };

    const getStatusColor = (status) => {
        switch (status) {
            case 'DRAFT':
                return 'blue';
            case 'PENDING':
                return 'orange';
            case 'APPROVED':
                return 'green';
            case 'PROCESSED':
                return 'purple';
            case 'READY':
                return 'green';
            case 'COMPLETED':
                return 'green';
            default:
                return 'default';
        }
    };

    const formatCurrency = (amount, currency = '₹') => {
        return `${currency}${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    };

    const formatDate = (dateString) => {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleDateString('en-GB');
    };

    const isDueToday = (paymentDate) => {
        const today = new Date();
        const payment = new Date(paymentDate);
        return today.toDateString() === payment.toDateString();
    };

    // Table columns for payroll history
    const payrollHistoryColumns = [
        {
            title: 'PAY DATE',
            dataIndex: 'payDate',
            key: 'payDate',
            render: (date) => formatDate(date)
        },
        {
            title: 'PAYROLL TYPE',
            dataIndex: 'type',
            key: 'type'
        },
        {
            title: 'DETAILS',
            dataIndex: 'payPeriod',
            key: 'payPeriod',
            render: (text, record) => (
                <span>
                    {formatDate(record.payPeriodStartDate)} - {formatDate(record.payPeriodEndDate)}
                </span>
            )
        },
        {
            title: 'STATUS',
            dataIndex: 'status',
            key: 'status',
            render: (status) => (
                <Tag color={getStatusColor(status)} className="fw-bold">
                    {status}
                </Tag>
            )
        },
        {
            title: 'NET PAY',
            dataIndex: 'netPay',
            key: 'netPay',
            render: (amount, record) => formatCurrency(amount, record.currency)
        },
        {
            title: 'EMPLOYEES',
            dataIndex: 'employeeCount',
            key: 'employeeCount'
        },
        {
            title: 'ACTIONS',
            key: 'actions',
            render: (_, record) => (
                <Dropdown
                    overlay={
                        <Menu>
                            <Menu.Item
                                key="view-details"
                                icon={<EyeOutlined />}
                                onClick={() => navigate(`/summary/${record.payrunId}`)}
                            >
                                View Details
                            </Menu.Item>
                            <Menu.Item
                                key="download-report"
                                icon={<FileExcelOutlined />}
                                onClick={() => downloadPayrunReport(record.payrunId)}
                                disabled={downloadingReport && currentDownloadingId === record.payrunId}
                            >
                                {downloadingReport && currentDownloadingId === record.payrunId ? (
                                    <>
                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                        Downloading...
                                    </>
                                ) : (
                                    "Download Report"
                                )}
                            </Menu.Item>
                        </Menu>
                    }
                    trigger={['click']}
                    placement="bottomRight"
                >

                    <Button type="text" icon={<MoreOutlined />} size="small" />

                </Dropdown>
            )
        }
    ];

    // Single PayRun Card Component
    const PayRunCard = ({ payRun }) => (
        <Card
            className="w-100"
            style={{ maxWidth: '100%' }}
            title={
                <div className="d-flex justify-content-between align-items-center">
                    <span className="fw-bold">
                        {payRun.type}
                        {payRun.subType && ` (${payRun.subType})`}
                    </span>
                    <Tag color={getStatusColor(payRun.status)} className="fw-bold">
                        {payRun.status}
                    </Tag>
                </div>
            }
            extra={
                payRun.status === 'READY' ? (
                    <Button
                        type="primary"
                        className="fw-semibold"
                        onClick={() => handleCreatePayrun(payRun)}
                    >
                        Create Payrun
                    </Button>
                ) : (
                    <Button
                        type="link"
                        className="fw-semibold"
                        onClick={() => {
                            const navigationMap = {
                                'APPROVED': `/summary/${payRun.id}`,
                                'PROCESSED': `/summary/${payRun.id}`,
                                'COMPLETED': `/summary/${payRun.id}`,
                                'PAID': `/summary/${payRun.id}`,
                                'DRAFT': `/preview/${payRun.id}`,
                                'PENDING': `/preview/${payRun.id}`,
                                'REJECTED': `/preview/${payRun.id}`
                            };

                            const targetPath = navigationMap[payRun.status] || `/preview/${payRun.id}`;
                            navigate(targetPath);
                        }}
                    >
                        View Details
                    </Button>
                )
            }
        >
            <div className="d-flex flex-column gap-3">
                <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted fs-6">EMPLOYEES' NET PAY</span>
                    <span className="fw-bold fs-5">
                        {formatCurrency(payRun.netPay, payRun.currency)}
                    </span>
                </div>

                <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted fs-6">
                        <CalendarOutlined className="me-2" />
                        PAYMENT DATE
                    </span>
                    <span className={isDueToday(payRun.paymentDate) ? 'text-danger fw-bold' : 'fw-semibold'}>
                        {formatDate(payRun.paymentDate)}
                    </span>
                </div>

                <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted fs-6">
                        <UserOutlined className="me-2" />
                        NO. OF EMPLOYEES
                    </span>
                    <span className="fw-semibold">{payRun.employeeCount}</span>
                </div>
                <div className="d-flex justify-content-between align-items-center">
                    <span className="text-muted fs-6">
                        <CalendarOutlined className="me-2" />
                        Processing Period
                    </span>
                    <span className="fw-semibold">{payRun.processingPeriod}</span>
                </div>

                {payRun.dueMessage && (
                    <div className="alert alert-warning mt-3 mb-0 py-2">
                        <small className="fw-semibold">{payRun.dueMessage}</small>
                    </div>
                )}
            </div>
        </Card>
    );

    const payRunCards = (
        <div className="p-3 p-md-4">


            {/* TDS Deduction Information Message */}
            {showTdsMessage && (
                <div className="alert alert-warning alert-dismissible fade show mb-4" role="alert">
                    <div className="fw-bold mb-2">TDS Deduction Setup Required</div>
                    <div className="small">
                        To ensure correct income tax (TDS) deduction during payroll processing:
                        <ul className="mt-2 mb-2">
                            <li>
                                Go to <b>Settings &gt; Claims & Declaration &gt; IT Declaration</b>.
                            </li>
                            <li>
                                Select and save the <b>Default Tax</b> for employees who have not submitted their IT Declaration.
                            </li>
                            <li>
                                Go to <b>Settings &gt; Claims & Declaration &gt; Proof of Investment</b>.
                            </li>
                            <li>
                                Select the month for deducting income tax amount and save it.
                            </li>
                        </ul>
                        Please complete these settings before creating the pay run.
                    </div>
                    <button
                        type="button"
                        className="btn-close"
                        onClick={() => setShowTdsMessage(false)}
                    ></button>
                </div>
            )}

            {/* Filter Buttons */}
            <div className="d-flex flex-wrap gap-3 mb-4">
                {filterButtons.map((button) => (
                    <Button
                        key={button.key}
                        type={selectedPayrollType === button.key ? 'primary' : 'default'}
                        size="middle"
                        className="d-flex align-items-center px-3 py-2"
                        onClick={() => handleFilterButtonClick(button.key)}
                    >
                        <span className="fw-semibold">{button.label}</span>
                        <Badge
                            count={button.count}
                            size="small"
                            style={{
                                backgroundColor: selectedPayrollType === button.key ? '#fff' : '#1890ff',
                                color: selectedPayrollType === button.key ? '#1890ff' : '#fff',
                                marginLeft: '8px'
                            }}
                        />
                    </Button>
                ))}
            </div>

            {/* Pay Run Cards */}
            {isLoading ? (
                <div className="text-center py-5">
                    <div className="spinner-border text-primary" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </div>
                </div>
            ) : filteredPayRunsData.length > 0 ? (
                <Row gutter={[16, 16]}>
                    {filteredPayRunsData.map((payRun) => (
                        <Col xs={24} key={payRun.id}>
                            <PayRunCard payRun={payRun} />
                        </Col>
                    ))}
                </Row>
            ) : (
                <div className="text-center">
                    <div className="mb-7">
                        <img
                            src={payRuns}
                            alt="No Payruns"
                            className="mw-100 h-200px h-sm-325px"
                        />
                    </div>

                    <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Almost there! Just a few things left</h3>
                        <div className="text-muted fw-semibold fs-5">
                            {fetchError ? (
                                "Failed to load payruns. Please try again later."
                            ) : (
                                <>
                                    We're almost ready to process your first payroll.
                                    <br />
                                    Before that, please complete your organisation setup.
                                    <br />
                                    It only takes a few minutes to finish.
                                </>
                            )
                            }
                        </div>
                    </div>

                    <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                            className="btn btn-primary"
                            onClick={() => navigate("/onboarding-dashboard")}
                        >
                            <i className="bi bi-plus fs-2"></i> Complete Setup
                        </button>
                    </div>
                </div>
            )}
        </div>
    );

    const payrollHistoryContent = (
        <div className="p-4">
            <div className="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center mb-4 gap-2">
                <div className="d-flex align-items-center">
                    <span className="fw-semibold me-2">Payroll Type:</span>
                    <Dropdown
                        menu={{
                            items: payrollTypeItems,
                            selectable: true,
                            defaultSelectedKeys: ['All'],
                            onSelect: handlePayrollTypeSelect,
                        }}
                        trigger={['click']}
                    >
                        <Typography.Link>
                            <Space className="border rounded px-3 py-1 bg-light">
                                {selectedPayrollType}
                                <DownOutlined />
                            </Space>
                        </Typography.Link>
                    </Dropdown>
                </div>
            </div>

            {isHistoryLoading ? (
                <div className="text-center py-5">
                    <div className="spinner-border text-primary" role="status">
                        <span className="visually-hidden">Loading...</span>
                    </div>
                </div>
            ) : payrollHistoryData.length > 0 ? (
                <div className="table-responsive">
                    <Table
                        columns={payrollHistoryColumns}
                        dataSource={payrollHistoryData}
                        pagination={false}
                        className="table table-row-dashed table-row-gray-300 gy-7"
                    />
                </div>
            ) : (
                <div className="text-center p-5 border rounded">
                    <div className="mb-4">
                        <i className="bi bi-receipt fs-1 text-muted" style={{ fontSize: '4rem' }}></i>
                    </div>
                    <div className="mb-7">
                        <img
                            src={payrollHistory}
                            alt="No Payroll History"
                            className="mw-100 h-200px h-sm-325px"
                        />
                    </div>
                    <p className="fw-bold text-gray-900 mb-2">
                        You don't have any pay runs for the selected payroll type filter.
                    </p>
                </div>
            )}
        </div>
    );

    const contentList = {
        payRuns: payRunCards,
        payrollHistory: payrollHistoryContent,
    };

    const onTabChange = (key) => {
        setActiveTabKey(key);
    };

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Pay Runs</title>
            </Helmet>

            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Pay Runs</h5>

                <Dropdown
                    menu={{
                        items: headerDropdownItems,
                        onClick: handleHeaderMenuClick,
                        style: { minWidth: '200px' }
                    }}
                    trigger={['click']}
                    placement="bottomRight"
                >
                    <a onClick={(e) => e.preventDefault()} style={{ display: 'inline-block' }}>
                        <Space className="btn btn-primary d-flex align-items-center justify-content-between" style={{ minWidth: '150px' }}>
                            Create Pay Runs
                            <DownOutlined className="ms-2" style={{ fontSize: '12px' }} />
                        </Space>
                    </a>
                </Dropdown>
            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div
                        className="container-fluid p-10 bg-white"
                        style={{ minHeight: '100vh', overflowY: 'auto' }}
                    >
                        <div className="w-100">
                            <Card
                                style={{ width: '100%' }}
                                tabList={tabList}
                                activeTabKey={activeTabKey}
                                onTabChange={onTabChange}
                                tabProps={{
                                    size: 'middle',
                                }}
                            >
                                {contentList[activeTabKey]}
                            </Card>
                        </div>
                    </div>
                </div>
            </div>

            {/* One Time Payout Modal */}
            {showOneTimePayoutForm && (
                <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1050 }}>
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">Create One Time Payout</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => setShowOneTimePayoutForm(false)}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{ component: '', paymentDate: '' }}
                                validationSchema={oneTimePayoutValidationSchema}
                                onSubmit={handleOneTimePayoutSubmit}
                            >
                                {({ isSubmitting }) => (
                                    <Form>
                                        <div className="modal-body">
                                            <div className="mb-5">
                                                <label className="form-label required">Select One Time Component</label>
                                                <Field
                                                    as="select"
                                                    name="component"
                                                    className="form-control form-control-solid"
                                                >
                                                    <option value="">Select</option>
                                                    <option value="Bonus">Bonus</option>
                                                    <option value="Commission">Commission</option>
                                                    <option value="Leave Encashment">Leave Encashment</option>
                                                </Field>
                                                <ErrorMessage
                                                    name="component"
                                                    component="div"
                                                    className="text-danger mt-1"
                                                />
                                            </div>

                                            <div className="mb-5">
                                                <label className="form-label required">When would you like to pay?</label>
                                                <Field
                                                    type="date"
                                                    name="paymentDate"
                                                    className="form-control form-control-solid"
                                                />
                                                <ErrorMessage
                                                    name="paymentDate"
                                                    component="div"
                                                    className="text-danger mt-1"
                                                />
                                            </div>
                                        </div>

                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => setShowOneTimePayoutForm(false)}
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
                                                    "Save and Continue"
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

            {/* Off Cycle Payrun Modal */}
            {showOffCyclePayrunForm && (
                <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1050 }}>
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">Initiate Off Cycle Pay Run</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => setShowOffCyclePayrunForm(false)}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{ paymentDate: '' }}
                                validationSchema={offCyclePayrunValidationSchema}
                                onSubmit={handleOffCyclePayrunSubmit}
                            >
                                {({ isSubmitting }) => (
                                    <Form>
                                        <div className="modal-body">
                                            <div className="mb-5">
                                                <label className="form-label required">When would you like to pay?</label>
                                                <Field
                                                    type="date"
                                                    name="paymentDate"
                                                    className="form-control form-control-solid"
                                                    placeholder="dd/MM/yyyy"
                                                />
                                                <ErrorMessage
                                                    name="paymentDate"
                                                    component="div"
                                                    className="text-danger mt-1"
                                                />
                                            </div>
                                        </div>

                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => setShowOffCyclePayrunForm(false)}
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
                                                    "Save and Continue"
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
}