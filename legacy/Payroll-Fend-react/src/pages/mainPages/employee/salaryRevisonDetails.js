import React, { useState, useEffect } from "react";
import {
    Card,
    Row,
    Col,
    Button,
    Typography,
    Tag,
    Descriptions,
    Alert,
    Divider,
    Modal,
    message,
    Space,
    Statistic,
    Tooltip
} from "antd";
import {
    EditOutlined,
    ArrowLeftOutlined,
    CalendarOutlined,
    DeleteOutlined,
    DollarOutlined,
    UserOutlined,
    IdcardOutlined,
    ExclamationCircleOutlined,
    PercentageOutlined,
    HistoryOutlined
} from "@ant-design/icons";
import { useNavigate, useLocation, useParams } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { Title, Text, Paragraph } = Typography;
const { confirm } = Modal;

// Fallback formatCurrency function
const formatCurrency = (amount) => {
    if (amount === undefined || amount === null) return '₹0.00';
    const numAmount = typeof amount === 'string' ? parseFloat(amount) : amount;
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(numAmount);
};

// Format date
const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
        if (dateString.includes('-')) {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return dateString;

            // For paymentMonth format like "2026-03"
            if (dateString.length === 7 && dateString.includes('-')) {
                const [year, month] = dateString.split('-');
                const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
                    'July', 'August', 'September', 'October', 'November', 'December'];
                return `${monthNames[parseInt(month) - 1]}, ${year}`;
            }

            return date.toLocaleDateString('en-IN', {
                year: 'numeric',
                month: 'long',
                day: 'numeric'
            });
        }
        return dateString;
    } catch (error) {
        return dateString;
    }
};

const getRevisionType = (changeInPercent) => {
    if (changeInPercent !== null && changeInPercent !== undefined) {
        return `Percentage (${changeInPercent}%)`;
    }
    return "Flat Amount";
};


// Get earning display name
const getEarningDisplayName = (earningCode) => {
    const displayNames = {
        'Basic': 'Basic',
        'House Rent Allowance': 'House Rent Allowance',
        'Conveyance Allowance': 'Conveyance Allowance',
        'Fixed Allowance': 'Fixed Allowance',
        'Special Allowance': 'Special Allowance',
        'Medical Allowance': 'Medical Allowance',
        'Bonus': 'Bonus',
        'Incentive': 'Incentive'
    };
    return displayNames[earningCode] || earningCode;
};

export default function SalaryRevisionDetails() {
    const navigate = useNavigate();
    const location = useLocation();
    const queryParams = new URLSearchParams(location.search);
    const revisionId = queryParams.get('revisionId');
    const employeeId = useParams().id;

    const [loading, setLoading] = useState(true);
    const [deleting, setDeleting] = useState(false);
    const [revisionData, setRevisionData] = useState(null);

    // Fetch revision data
    const fetchRevisionData = async () => {
        try {
            setLoading(true);
            const token = localStorage.getItem("__t");
            const organizationId = localStorage.getItem('organizationId');

            if (!token) {
                errorMsg("Error", "Authentication token not found", true);
                return;
            }

            const response = await axios.get(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision`, {
                params: {
                    revisionId: revisionId,
                    organizationId: organizationId
                },
                headers: {
                    Authorization: `Bearer ${token}`,
                    organizationId: organizationId
                },
            });

            console.log("Revision API Response:", response.data);

            if (response.data && response.data.data) {
                setRevisionData(response.data.data);
            } else {
                errorMsg("Error", "No revision data found", true);
            }
        } catch (error) {
            console.error("Error fetching revision data:", error);
            errorMsg("Error", "Failed to load revision details", true);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!revisionId || revisionId === "null") {
            errorMsg("Error", "Revision ID is required", true);
            navigate(-1);
            return;
        }
        fetchRevisionData();
    }, [revisionId]);

    // Handle edit revision
    const handleEditRevision = () => {
        navigate(`/employees/edit-salary-revision/${employeeId}?revisionId=${revisionData.revisionId}`);
    };

    // Handle delete revision
    const handleDeleteRevision = () => {
        if (!revisionData) return;

        // Get employee full name
        const employeeFullName = `${revisionData.firstName || ''} ${revisionData.lastName || ''}`.trim() ||
            revisionData.employeeName ||
            'this employee';

        confirm({
            title: 'Confirm Delete',
            icon: <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
            content: (
                <div>
                    <Alert
                        message="Warning"
                        description={
                            <div>
                                <p><strong>You are about to delete the salary revision of {employeeFullName}.</strong></p>
                                <p>This action cannot be undone. Are you sure you want to proceed?</p>
                            </div>
                        }
                        type="warning"
                        showIcon
                    />
                    <div style={{ marginTop: 16 }}>
                        <strong>Revision Details:</strong>
                        <ul style={{ marginTop: 8, paddingLeft: 20 }}>
                            <li>Employee: {employeeFullName}</li>
                            <li>Employee ID: {revisionData.employeeId || 'N/A'}</li>
                            <li>Effective Date: {formatDate(revisionData.effectiveDate)}</li>
                            <li>Revised CTC: {formatCurrency(revisionData.ctc)}</li>
                        </ul>
                    </div>
                </div>
            ),
            okText: 'Proceed',
            okType: 'danger',
            cancelText: 'Cancel',
            onOk: async () => {
                await deleteRevision();
            },
            onCancel() {
                console.log('Delete cancelled');
            },
        });
    };

    // Delete revision API call
    const deleteRevision = async () => {
        try {
            setDeleting(true);
            const token = localStorage.getItem("__t");
            const organizationId = localStorage.getItem('organizationId');

            if (!token || !organizationId) {
                errorMsg("Error", "Authentication token or organization ID not found", true);
                return;
            }

            const response = await axios.delete(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision/delete`, {
                params: {
                    revisionId: revisionId,
                    organizationId: organizationId
                },
                headers: {
                    Authorization: `Bearer ${token}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.status === 200) {
                successMsg("Success", "Salary revision deleted successfully", true);
                // Navigate back to previous page
                navigate(-1);
            } else {
                errorMsg("Error", "Failed to delete salary revision", true);
            }
        } catch (error) {
            console.error("Error deleting revision:", error);
            errorMsg(
                "Error",
                error.response?.data?.message || "Failed to delete salary revision",
                true
            );
        } finally {
            setDeleting(false);
        }
    };


    

    // Calculate total earnings
    const calculateTotalEarnings = () => {
        return revisionData?.earnings?.reduce((sum, earning) => sum + (earning.amount || 0), 0) || 0;
    };

    // Calculate basic percentage
    const calculateBasicPercentage = () => {
        if (!revisionData?.earnings) return '';
        const basicEarning = revisionData.earnings.find(e => e.earningCode === 'Basic');
        const totalCTC = revisionData.ctc || 0;
        if (!basicEarning?.amount || !totalCTC || totalCTC === 0) return '';
        const percentage = ((basicEarning.amount * 12) / totalCTC) * 100;
        return `${percentage.toFixed(1)}% of CTC`;
    };

    // Calculate HRA percentage
    const calculateHRAPercentage = () => {
        const basicEarning = revisionData?.earnings?.find(e => e.earningCode === 'Basic');
        const hraEarning = revisionData?.earnings?.find(e => e.earningCode === 'House Rent Allowance');
        const basicAmount = basicEarning?.amount || 0;
        const hraAmount = hraEarning?.amount || 0;
        if (!hraAmount || !basicAmount || basicAmount === 0) return '';
        const percentage = (hraAmount / basicAmount) * 100;
        return `${percentage.toFixed(1)}% of Basic`;
    };

    // Get employee full name
    const getEmployeeFullName = () => {
        if (!revisionData) return '';

        // First check firstName/lastName from DTO
        if (revisionData.firstName || revisionData.lastName) {
            return `${revisionData.firstName || ''} ${revisionData.middleName || ''} ${revisionData.lastName || ''}`.trim().replace(/\s+/g, ' ');
        }

        // Fallback to employeeName from list DTO
        return revisionData.employeeName || '';
    };

    // Get employee number
    const getEmployeeNumber = () => {
        if (!revisionData) return '';
        return revisionData.employeeNumber || revisionData.employeeId || '';
    };

    if (loading) {
        return <Loader />;
    }

    if (!revisionData) {
        return (
            <div className="text-center py-5">
                <div className="fs-3 text-muted mb-3">No Revision Data Found</div>
                <p className="text-muted">Salary revision details are not available.</p>
                <Button
                    type="primary"
                    icon={<ArrowLeftOutlined />}
                    onClick={() => navigate(-1)}
                >
                    Go Back
                </Button>
            </div>
        );
    }

    const employeeFullName = getEmployeeFullName();
    const employeeNumber = getEmployeeNumber();
    const totalEarnings = calculateTotalEarnings();
    const monthlyCTC = revisionData.monthlySalary || 0;
    const annualCTC = revisionData.ctc || 0;
    const previousCTC = revisionData.previousCtc || 0;
    const ctcChange = previousCTC ? annualCTC - previousCTC : 0;
    const ctcChangePercentage = previousCTC ? ((ctcChange / previousCTC) * 100).toFixed(1) : 0;
    const isIncrease = ctcChange > 0;
    

    return (
        <div className="container-fluid py-4 bg-white">
            {/* Header Section */}
            <div className="d-flex justify-content-between align-items-center mb-4">

                {/* LEFT SECTION */}
                <div className="d-flex align-items-center flex-wrap gap-3">

                    {/* Back Button */}
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        onClick={() => navigate(-1)}
                        size="large"
                    />

                    {/* Title + Status */}
                    <div className="d-flex align-items-center gap-3">
                        <Title level={10} className="mb-0">
                            Salary Revision Details
                        </Title>

                        {revisionData.revisionStatus && (
                            <Tag
                                color={
                                    revisionData.revisionStatus === "APPROVED"
                                        ? "success"
                                        : revisionData.revisionStatus === "PENDING"
                                            ? "warning"
                                            : revisionData.revisionStatus === "DRAFT"
                                                ? "blue"
                                                : revisionData.revisionStatus === "REJECTED"
                                                    ? "error"
                                                    : "default"
                                }
                                className="px-3 py-1"
                            >
                                {revisionData.revisionStatus}
                            </Tag>
                        )}
                    </div>

                    {/* Employee Info */}
                    <Card
                        size="small"
                        className="border-0"
                        style={{ backgroundColor: "#f8f9fa" }}
                    >
                        <Row gutter={[24, 8]}>
                            <Col>
                                <div className="d-flex align-items-center gap-2">
                                    <UserOutlined style={{ color: "#1890ff" }} />
                                    <div>
                                        <div className="text-muted small">Employee Name</div>
                                        <div className="fw-bold">{employeeFullName || "N/A"}</div>
                                    </div>
                                </div>
                            </Col>

                            <Col>
                                <div className="d-flex align-items-center gap-2">
                                    <IdcardOutlined style={{ color: "#52c41a" }} />
                                    <div>
                                        <div className="text-muted small">Employee Number</div>
                                        <div className="fw-bold">{employeeNumber || "N/A"}</div>
                                    </div>
                                </div>
                            </Col>
                        </Row>
                    </Card>
                </div>

                {/* RIGHT SECTION - ACTION BUTTONS */}
                <div className="d-flex gap-2">
                    <Button
                        type="primary"
                        icon={<EditOutlined />}
                        onClick={handleEditRevision}
                        size="large"
                    >
                        Edit Revision
                    </Button>

                    <Button
                        danger
                        icon={<DeleteOutlined />}
                        onClick={handleDeleteRevision}
                        loading={deleting}
                        size="large"
                    >
                        Delete
                    </Button>
                </div>
            </div>


            {/* CTC Comparison Section */}
            <Row gutter={[16, 16]} className="mb-5">
                <Col xs={24} md={6}>
                    <Card className="border-0 shadow-sm h-100">
                        <Statistic
                            title="Previous CTC"
                            value={previousCTC}
                            precision={2}
                            prefix="₹"
                            valueStyle={{ color: '#8c8c8c' }}
                            suffix="per annum"
                        />
                        <div className="text-muted small mt-2">
                            <HistoryOutlined /> Before revision
                        </div>
                    </Card>
                </Col>
                <Col xs={24} md={6}>
                    <Card className="border-0 shadow-sm h-100">
                        <Statistic
                            title="Revised CTC"
                            value={annualCTC}
                            precision={2}
                            prefix="₹"
                            valueStyle={{ color: '#1890ff' }}
                            suffix="per annum"
                        />
                        <div className="mt-2">
                            {ctcChange !== 0 && (
                                <Tag color={isIncrease ? "success" : "error"}>
                                    {isIncrease ? "↑" : "↓"} {formatCurrency(Math.abs(ctcChange))}
                                    {ctcChangePercentage !== 0 && ` (${ctcChangePercentage}%)`}
                                </Tag>
                            )}
                        </div>
                    </Card>
                </Col>
                <Col xs={24} md={6}>
                    <Card className="border-0 shadow-sm h-100">
                        <Statistic
                            title="Monthly Salary"
                            value={monthlyCTC}
                            precision={2}
                            prefix="₹"
                            valueStyle={{ color: '#52c41a' }}
                        />
                        {revisionData.previousMonthlySalary && (
                            <div className="text-muted small mt-2">
                                Previous: {formatCurrency(revisionData.previousMonthlySalary)}
                            </div>
                        )}
                    </Card>
                </Col>
                <Col xs={24} md={6}>
                    <Card className="border-0 shadow-sm h-100">
                        <Statistic
                            title="Change Percentage"
                            value={Math.abs(ctcChangePercentage)}
                            precision={1}
                            suffix="%"
                            valueStyle={{
                                color: isIncrease ? '#52c41a' : '#ff4d4f',
                                fontSize: '2rem'
                            }}
                            prefix={isIncrease ? "↑" : "↓"}
                        />
                        <div className="text-muted small mt-2">
                            <PercentageOutlined /> Change from previous
                        </div>
                    </Card>
                </Col>
            </Row>

            {/* Revision Timeline */}
            <Card className="mb-4 border-0 shadow-sm">
                <Title level={5} className="mb-3">
                    <CalendarOutlined /> Revision Timeline
                </Title>
                <Row gutter={[16, 16]}>
                    <Col xs={24} md={8}>
                        <Card size="small" className="text-center">
                            <div className="text-muted small mb-1">Effective From</div>
                            <div className="fw-bold fs-5">
                                {formatDate(revisionData.effectiveDate)}
                            </div>
                            <div className="text-muted small mt-1">
                                Date when revision takes effect
                            </div>
                        </Card>
                    </Col>
                    {/* <Col xs={24} md={8}>
                        <Card size="small" className="text-center">
                            <div className="text-muted small mb-1">Payout Month</div>
                            <div className="fw-bold fs-5">
                                {formatDate(revisionData.paymentMonth)}
                            </div>
                            <div className="text-muted small mt-1">
                                First month with revised salary
                            </div>
                        </Card>
                    </Col> */}
                    <Col xs={24} md={8}>
                        <Card size="small" className="text-center">
                            <div className="text-muted small mb-1">Created Date</div>
                            <div className="fw-bold fs-5">
                                {formatDate(revisionData.createdAt)}
                            </div>
                            <div className="text-muted small mt-1">
                                When this revision was created
                            </div>
                        </Card>
                    </Col>
                </Row>
            </Card>

            {/* Salary Structure Table */}
            <Card className="mb-4 border-0 shadow-sm">
                <div className="card-header border-0 bg-white d-flex justify-content-between align-items-center">
                    <Title level={4} className="mb-0">
                        <DollarOutlined /> Revised Salary Structure
                    </Title>
                    <div className="text-muted">
                        Total CTC: <strong>{formatCurrency(annualCTC)}</strong>
                    </div>
                </div>
                <div className="card-body p-0">
                    <div className="table-responsive">
                        <table className="table table-bordered mb-0">
                            <thead className="bg-light">
                                <tr>
                                    <th className="fw-bold text-gray-700 py-3 px-4" style={{ width: '40%' }}>
                                        SALARY COMPONENTS
                                    </th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>
                                        MONTHLY AMOUNT
                                    </th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>
                                        ANNUAL AMOUNT
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {/* Earnings Section */}
                                <tr className="bg-light">
                                    <td colSpan="3" className="fw-bold text-gray-800 py-2 px-4">
                                        Earnings
                                    </td>
                                </tr>

                                {revisionData?.earnings?.filter(earning => earning.enabled && earning.amount >= 0).map((earning, index) => (
                                    <tr key={index}>
                                        <td className="py-3 px-4">
                                            <div className="d-flex justify-content-between align-items-start">
                                                <div>
                                                    <div className="fw-semibold text-gray-700">
                                                        {getEarningDisplayName(earning.earningCode)}
                                                    </div>
                                                    <div className="text-muted small">
                                                        {earning.earningCode === 'Basic' && (
                                                            <Tooltip title={calculateBasicPercentage()}>
                                                                <span>{calculateBasicPercentage()}</span>
                                                            </Tooltip>
                                                        )}
                                                        {earning.earningCode === 'House Rent Allowance' && (
                                                            <Tooltip title={calculateHRAPercentage()}>
                                                                <span>{calculateHRAPercentage()}</span>
                                                            </Tooltip>
                                                        )}
                                                        {earning.amountInPercentage &&
                                                            earning.earningCode !== 'Basic' &&
                                                            earning.earningCode !== 'House Rent Allowance' && (
                                                                <span>({earning.amountInPercentage}%)</span>
                                                            )}
                                                    </div>
                                                </div>
                                                {earning.editable && (
                                                    <Tag color="blue" size="small">Editable</Tag>
                                                )}
                                            </div>
                                        </td>
                                        <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                            {earning.earningCode === 'Bonus' ? '-' : formatCurrency(earning.amount)}
                                        </td>
                                        <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                            {earning.earningCode === 'Bonus' ? formatCurrency(earning.amount) : formatCurrency(earning.amount * 12)}
                                        </td>
                                    </tr>
                                ))}

                                {/* EPF Components Section */}
                                {revisionData?.epfComponents?.length > 0 && (
                                    <>
                                        <tr className="bg-light">
                                            <td colSpan="3" className="fw-bold text-gray-800 py-2 px-4">
                                                Benefits
                                            </td>
                                        </tr>
                                        {revisionData.epfComponents.map((epf, index) => (
                                            <tr key={`epf-${index}`}>
                                                <td className="py-3 px-4">
                                                    <div className="fw-semibold text-gray-700">
                                                        {epf.componentLabel}
                                                    </div>
                                                    <div className="text-muted small">
                                                        {epf.calculationType === 'PERCENTAGE' && epf.percentage &&
                                                            `${epf.percentage.replace('_', '.')}% of PF Wages`}
                                                    </div>
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(epf.monthlyAmount)}
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(epf.annualAmount)}
                                                </td>
                                            </tr>
                                        ))}
                                    </>
                                )}

                                {/* Total Row */}
                                <tr className="border-top-2 border-gray-300 bg-light">
                                    <td className="py-3 px-4">
                                        <div className="fw-bold text-gray-800 fs-5">Cost to Company (CTC)</div>
                                    </td>
                                    <td className="py-3 px-4 text-center fw-bold text-gray-800 fs-5">
                                        {formatCurrency(monthlyCTC)}
                                    </td>
                                    <td className="py-3 px-4 text-center fw-bold text-gray-800 fs-5">
                                        {formatCurrency(annualCTC)}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </Card>

            {/* Summary Section */}
            <Row gutter={[16, 16]}>
                <Col xs={24} md={12}>
                    <Card title="Revision Summary" className="border-0 shadow-sm">
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="Employee">
                                {employeeFullName}
                            </Descriptions.Item>
                            <Descriptions.Item label="Employee Number">
                                {employeeNumber}
                            </Descriptions.Item>
                            <Descriptions.Item label="Previous CTC">
                                {formatCurrency(previousCTC)}
                            </Descriptions.Item>
                            <Descriptions.Item label="Revised CTC">
                                {formatCurrency(annualCTC)}
                            </Descriptions.Item>
                            <Descriptions.Item label="Change Amount">
                                <span style={{ color: isIncrease ? '#52c41a' : '#ff4d4f' }}>
                                    {isIncrease ? '+' : ''}{formatCurrency(ctcChange)}
                                </span>
                            </Descriptions.Item>
                            <Descriptions.Item label="Change Percentage">
                                <span style={{ color: isIncrease ? '#52c41a' : '#ff4d4f' }}>
                                    {isIncrease ? '+' : ''}{ctcChangePercentage}%
                                </span>
                            </Descriptions.Item>
                            <Descriptions.Item label="Revision Type">
    <Tag color={revisionData.changeInPercent !== null ? "blue" : "purple"}>
        {getRevisionType(revisionData.changeInPercent)}
    </Tag>
</Descriptions.Item>

                            <Descriptions.Item label="Status">
                                <Tag color={
                                    revisionData.revisionStatus === 'APPROVED' ? 'success' :
                                        revisionData.revisionStatus === 'PENDING' ? 'warning' :
                                            revisionData.revisionStatus === 'DRAFT' ? 'blue' :
                                                revisionData.revisionStatus === 'REJECTED' ? 'error' : 'default'
                                }>
                                    {revisionData.revisionStatus}
                                </Tag>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>
                <Col xs={24} md={12}>
                    <Card title="Quick Actions" className="border-0 shadow-sm">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <Button
                                type="primary"
                                block
                                icon={<EditOutlined />}
                                onClick={handleEditRevision}
                                size="large"
                            >
                                Edit This Revision
                            </Button>
                            <Button
                                block
                                icon={<ArrowLeftOutlined />}
                                onClick={() => navigate(-1)}
                                size="large"
                            >
                                Back to Previous Page
                            </Button>
                            <Button
                                danger
                                block
                                icon={<DeleteOutlined />}
                                onClick={handleDeleteRevision}
                                loading={deleting}
                                size="large"
                            >
                                Delete This Revision
                            </Button>
                        </Space>
                    </Card>
                </Col>
            </Row>

            {/* Delete Warning */}
            <Alert
                message="Important Note"
                description="Deleting a salary revision is a permanent action. Once deleted, the revision cannot be recovered. Please ensure you have proper authorization before proceeding."
                type="warning"
                showIcon
                className="mt-4"
            />
        </div>
    );
}