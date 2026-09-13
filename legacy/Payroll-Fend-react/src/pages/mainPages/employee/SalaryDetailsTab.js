import React, { useState, useEffect } from "react";
import { Card, Row, Col, Button, Typography, Tag, Modal, message } from "antd";
import { Link, useOutletContext, useNavigate, useParams } from "react-router-dom";
import { EditOutlined, RollbackOutlined, EyeOutlined } from "@ant-design/icons";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";



const { Title } = Typography;
const { confirm } = Modal;

export default function SalaryDetailsTab() {
    const {
        formatCurrency,
        organizationId,
        id
    } = useOutletContext();

    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [salaryData, setSalaryData] = useState(null);
    const [basicDetails, setBasicDetails] = useState(null);
    const [showReviseConfirm, setShowReviseConfirm] = useState(false);
    const [releasing, setReleasing] = useState(false);
    const [revisionData, setRevisionData] = useState(null);
    const [loadingRevision, setLoadingRevision] = useState(false);
    const [revisionId, setRevisionId] = useState(null);
    const [latestRevisionCtc, setLatestRevisionCtc] = useState(null);

    // Fetch salary data specifically for this tab
    const fetchSalaryData = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            console.log("Salary API Response:", response.data);

            if (response.data && response.data.data) {
                // Set the full response data
                const employeeData = response.data.data;
                setSalaryData(employeeData.ctc || {});
                setBasicDetails(employeeData.basicDetails || {});

                // Check for latest revision data
                if (employeeData.latestRevisionCtc) {
                    setLatestRevisionCtc(employeeData.latestRevisionCtc);
                    setRevisionId(employeeData.latestRevisionCtc.revisionId);

                    // If latest revision exists, fetch its details
                    if (employeeData.latestRevisionCtc.revisionId) {
                        fetchRevisionData(employeeData.latestRevisionCtc.revisionId);
                    }
                } else {
                    // Check if there's a revision ID in the salary data (fallback)
                    if (employeeData.ctc?.revisionId) {
                        setRevisionId(employeeData.ctc.revisionId);
                        fetchRevisionData(employeeData.ctc.revisionId);
                    }
                }

            } else {
                errorMsg("Error", "No salary data found", true);
            }
        } catch (error) {
            console.error("Error fetching salary data:", error);
            errorMsg("Error", "Failed to load salary details", true);
        } finally {
            setLoading(false);
        }
    };

    // Fetch revision data
    const fetchRevisionData = async (revisionId) => {
        try {
            setLoadingRevision(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision`, {
                params: {
                    revisionId: revisionId,
                    organizationId: organizationId
                },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            console.log("Revision API Response:", response.data);

            if (response.data && response.data.data) {
                setRevisionData(response.data.data);
            }
        } catch (error) {
            console.error("Error fetching revision data:", error);
            // Don't show error message for revision fetch - it's optional
        } finally {
            setLoadingRevision(false);
        }
    };

    useEffect(() => {
        fetchSalaryData();
    }, [id]);

    const isRevisionAppliedInPayrun = () => {
        return latestRevisionCtc?.appliedInPayrun === true;
    };


    // Handle revise button click
    const handleReviseClick = () => {

        // CASE 1: Revision exists AND salary already applied in payrun
        if (latestRevisionCtc?.revisionId && isRevisionAppliedInPayrun()) {
            confirm({
                title: 'Salary Already Processed',
                content:
                    'The revised salary has already been processed in payroll. Do you want to create a new salary revision?',
                okText: 'Yes, Revise Again',
                cancelText: 'No',
                okType: 'primary',
                onOk() {
                    navigateToCreateRevision();
                },
            });
            return;
        }

        // CASE 2: Revision exists but NOT applied in payrun → EDIT
        if (latestRevisionCtc?.revisionId && !isRevisionAppliedInPayrun()) {
            confirm({
                title: 'Salary Revision Already Exists',
                content:
                    'You have already revised the salary for this employee. Would you like to edit the revision?',
                okText: 'Yes, Edit Revision',
                cancelText: 'No',
                okType: 'primary',
                onOk() {
                    navigateToEditRevision();
                },
            });
            return;
        }

        // CASE 3: No revision exists → CREATE
        confirm({
            title: 'Revise Salary',
            content:
                'Are you sure you want to revise this salary? You will be redirected to the salary revision page.',
            okText: 'Yes, Revise',
            cancelText: 'No',
            okType: 'primary',
            onOk() {
                navigateToCreateRevision();
            },
        });
    };


    // Navigate to edit existing revision (PUT operation)
    const navigateToEditRevision = () => {
        try {
            setReleasing(true);

            // Show success message
            // successMsg("Success", "Navigating to details revision page...", false);

            // Navigate to edit revision page with revisionId
            navigate(
                `/employees/revision-salary-details/${id}?revisionId=${revisionData.revisionId}`
            );

        } catch (error) {
            console.error("Error navigating to edit revision:", error);
            errorMsg("Error", "Failed to navigate to edit revision page", true);
        } finally {
            setReleasing(false);
        }
    };

    // Navigate to create new revision (POST operation)
    const navigateToCreateRevision = () => {
        try {
            setReleasing(true);

            // Show success message
            // successMsg("Success", "Navigating to create revision page...", false);

            // Navigate to create new revision page
            navigate(`/employees/edit-revise-salary/${id}?action=create`);

        } catch (error) {
            console.error("Error navigating to create revision:", error);
            errorMsg("Error", "Failed to navigate to create revision page", true);
        } finally {
            setReleasing(false);
        }
    };

    if (loading) {
        return <Loader />;
    }

    if (!salaryData) {
        return (
            <div className="text-center py-5">
                <div className="fs-3 text-muted mb-3">No Salary Data Found</div>
                <p className="text-muted">Salary details are not available for this employee.</p>
            </div>
        );
    }

    // Helper to get amount by earningCode
    const getAmountByCode = (code) => {
        return salaryData?.earnings?.find(e => e.earningCode === code)?.amount || 0;
    };

    const basicAmount = getAmountByCode("Basic");
    const hraAmount = getAmountByCode("House Rent Allowance");
    const conveyanceAmount = getAmountByCode("Conveyance Allowance");
    const fixedAllowanceAmount = getAmountByCode("Fixed Allowance");

    // Calculate total earnings
    const totalEarnings = salaryData?.earnings?.reduce((sum, earning) => sum + (earning.amount || 0), 0) || 0;

    // Calculate percentages for salary components
    const calculatePercentage = (componentAmount, totalCTC) => {
        if (!componentAmount || !totalCTC || totalCTC === 0) return '';
        const percentage = ((componentAmount * 12) / totalCTC) * 100;
        return `(${percentage.toFixed(2)}% of CTC)`;
    };

    // Calculate basic percentage
    const calculateBasicPercentage = (basicAmount, totalCTC) => {
        if (!basicAmount || !totalCTC || totalCTC === 0) return '';
        const percentage = ((basicAmount * 12) / totalCTC) * 100;
        return `(${percentage.toFixed(2)}% of CTC)`;
    };

    // Calculate HRA percentage
    const calculateHRAPercentage = (hraAmount, basicAmount) => {
        if (!hraAmount || !basicAmount || basicAmount === 0) return '';
        const percentage = (hraAmount / basicAmount) * 100;
        return `(${percentage.toFixed(2)}% of Basic Amount)`;
    };

    // Calculate total cost to company including benefits
    const calculateTotalCTC = () => {
        let total = totalEarnings || 0;

        // Add benefits
        if (salaryData?.benefits?.length > 0) {
            total += salaryData.benefits.reduce((sum, benefit) => sum + (benefit.amount || 0), 0);
        }

        // Add FBP components
        if (salaryData?.fbpComponents?.length > 0) {
            total += salaryData.fbpComponents.reduce((sum, fbp) => sum + (fbp.amount || 0), 0);
        }

        // Add reimbursements
        if (salaryData?.reimbursements?.length > 0) {
            total += salaryData.reimbursements.reduce((sum, reimbursement) => sum + (reimbursement.amount || 0), 0);
        }

        return total;
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

    // Get display name for EPF/ESI components from backend
    const getComponentDisplayName = (component) => {
        if (!component) return "";
        if (component.componentLabel) return component.componentLabel;
        const typeMap = {
            'EPF_EMPLOYER': 'EPF - Employer Contribution',
            'EPF_EMPLOYEE': 'EPF - Employee Contribution',
            'EPS': 'EPS Contribution',
            'EDLI_EMPLOYER': 'EDLI - Employer Contribution',
            'EPF_ADMIN': 'EPF Admin Charges - Employer Contribution',
            'EPF_ADMIN_CHARGES': 'EPF Admin Charges - Employer Contribution',
            'ESI_EMPLOYER': 'ESI - Employer Contribution',
            'ESI_EMPLOYEE': 'ESI - Employee Contribution'
        };
        return typeMap[component.componentCode] || component.componentCode || "";
    };

    return (
        <div className="tab-content">
            <div className="tab-pane fade show active" id="kt_tab_pane_salary" role="tabpanel">



                <div className="d-flex justify-content-between align-items-center mb-4">
                    <Title level={4} className="mb-0">Salary Details</Title>
                    <div className="d-flex gap-2">
                        {/* Revise Button - Updated with condition */}
                        <Button
                            type="primary"
                            size="small"
                            icon={<RollbackOutlined />}
                            onClick={handleReviseClick}
                            loading={releasing || loadingRevision}
                            className="d-flex align-items-center"
                        >
                            {
                                latestRevisionCtc?.revisionId && !latestRevisionCtc?.appliedInPayrun
                                    ? 'Edit Revision'
                                    : 'Revise'
                            }
                        </Button>

                        {/* Edit Button */}
                        <Button
                            type="link"
                            size="small"
                            icon={<EditOutlined />}
                            className="d-flex align-items-center"
                        >
                            <Link to={`/employees/edit-salary/${id}`}>Edit</Link>
                        </Button>
                    </div>
                </div>

                {/* Salary Summary Cards */}
                <Row gutter={[16, 16]} className="mb-5">
                    <Col xs={24} md={12}>
                        <Card className="bg-light-primary border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formatCurrency(salaryData?.ctc || calculateTotalCTC())}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Annual CTC</div>
                                    {latestRevisionCtc && (
                                        <div className="text-muted small">
                                            Revised: {formatCurrency(latestRevisionCtc.ctc)}
                                            {latestRevisionCtc.ctc && salaryData?.ctc && (
                                                <span className={`ms-2 ${latestRevisionCtc.ctc > salaryData.ctc ? 'text-success' : 'text-danger'}`}>
                                                    ({latestRevisionCtc.ctc > salaryData.ctc ? '↑' : '↓'}
                                                    {Math.abs(((latestRevisionCtc.ctc - salaryData.ctc) / salaryData.ctc * 100)).toFixed(1)}%)
                                                </span>
                                            )}
                                        </div>
                                    )}
                                    {revisionData && revisionData.previousCtc && (
                                        <div className="text-muted small">
                                            Previous: {formatCurrency(revisionData.previousCtc)}
                                            {revisionData.ctc && (
                                                <span className={`ms-2 ${revisionData.ctc > revisionData.previousCtc ? 'text-success' : 'text-danger'}`}>
                                                    ({revisionData.ctc > revisionData.previousCtc ? '↑' : '↓'}
                                                    {Math.abs(((revisionData.ctc - revisionData.previousCtc) / revisionData.previousCtc * 100)).toFixed(1)}%)
                                                </span>
                                            )}
                                        </div>
                                    )}
                                </div>
                                <div className="text-primary fs-1">
                                    <i className="ki-duotone ki-chart-simple">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={12}>
                        <Card className="bg-light-success border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formatCurrency(salaryData?.monthlySalary || (totalEarnings || 0))}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Monthly CTC</div>
                                    {latestRevisionCtc && (
                                        <div className="text-muted small">
                                            Revised: {formatCurrency(latestRevisionCtc.monthlySalary)}
                                            {latestRevisionCtc.monthlySalary && salaryData?.monthlySalary && (
                                                <span className={`ms-2 ${latestRevisionCtc.monthlySalary > salaryData.monthlySalary ? 'text-success' : 'text-danger'}`}>
                                                    ({latestRevisionCtc.monthlySalary > salaryData.monthlySalary ? '↑' : '↓'}
                                                    {Math.abs(((latestRevisionCtc.monthlySalary - salaryData.monthlySalary) / salaryData.monthlySalary * 100)).toFixed(1)}%)
                                                </span>
                                            )}
                                        </div>
                                    )}
                                    {revisionData && revisionData.previousMonthlySalary && (
                                        <div className="text-muted small">
                                            Previous: {formatCurrency(revisionData.previousMonthlySalary)}
                                            {revisionData.monthlySalary && (
                                                <span className={`ms-2 ${revisionData.monthlySalary > revisionData.previousMonthlySalary ? 'text-success' : 'text-danger'}`}>
                                                    ({revisionData.monthlySalary > revisionData.previousMonthlySalary ? '↑' : '↓'}
                                                    {Math.abs(((revisionData.monthlySalary - revisionData.previousMonthlySalary) / revisionData.previousMonthlySalary * 100)).toFixed(1)}%)
                                                </span>
                                            )}
                                        </div>
                                    )}
                                </div>
                                <div className="text-success fs-1">
                                    <i className="ki-duotone ki-dollar">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                </Row>

                {/* Salary Structure Table */}
                <Card
                    title={
                        <div className="d-flex justify-content-between align-items-center">
                            <span className="fw-bold fs-4">Salary Structure</span>
                            <span className="text-muted fw-semibold">Financial Year: 2025-26</span>
                        </div>
                    }
                    className="shadow-sm mb-4"
                >
                    <div className="table-responsive">
                        <table className="table table-bordered table-hover">
                            <thead className="bg-light">
                                <tr>
                                    <th className="fw-bold text-gray-700 py-3 px-4" style={{ width: '40%' }}>SALARY COMPONENTS</th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>MONTHLY AMOUNT</th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>ANNUAL AMOUNT</th>
                                </tr>
                            </thead>
                            <tbody>
                                {/* Earnings Section Header */}
                                <tr className="bg-light-warning">
                                    <td colSpan="3" className="fw-bold text-gray-800 py-2 px-4">
                                        Earnings
                                    </td>
                                </tr>

                                {/* Loop through all earnings */}
                                {salaryData?.earnings?.filter(earning => earning.enabled && earning.amount >= 0).map((earning, index) => (
                                    <tr key={index}>
                                        <td className="py-3 px-4">
                                            <div className="fw-semibold text-gray-700">
                                                {getEarningDisplayName(earning.earningCode)}
                                            </div>
                                            <div className="text-muted small">
                                                {earning.earningCode === 'Basic' && calculateBasicPercentage(earning.amount, salaryData?.ctc || 0)}
                                                {earning.earningCode === 'House Rent Allowance' && calculateHRAPercentage(earning.amount, basicAmount)}
                                                {earning.amountInPercentage && earning.earningCode !== 'Basic' && earning.earningCode !== 'House Rent Allowance' &&
                                                    `(${earning.amountInPercentage}%)`}
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

                                {/* Benefits Section Header */}
                                {salaryData?.benefits?.length > 0 && (
                                    <>
                                        <tr className="bg-light-info">
                                            <td colSpan="3" className="fw-bold text-gray-800 py-2 px-4">
                                                Benefits
                                            </td>
                                        </tr>
                                        {salaryData.benefits.map((benefit, index) => (
                                            <tr key={`benefit-${index}`}>
                                                <td className="py-3 px-4">
                                                    <div className="fw-semibold text-gray-700">
                                                        {benefit.componentName || 'Benefit'}
                                                    </div>
                                                    <div className="text-muted small">
                                                        {benefit.calculationType || ''}
                                                    </div>
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(benefit.amount)}
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(benefit.amount * 12)}
                                                </td>
                                            </tr>
                                        ))}
                                    </>
                                )}

                                {/* EPF Components from backend (Employer Contribution in Salary Structure) */}
                                {salaryData?.epfComponents?.length > 0 && (
                                    <>
                                        {salaryData.epfComponents
    .filter(
        (component) =>
            component.componentCode !== "EDLI_EMPLOYER" &&
            component.componentCode !== "EPF_ADMIN" &&
            component.componentCode !== "EPF_ADMIN_CHARGES"
    )
    .map((component, index) => (
                                            <tr key={`epf-salary-${index}`}>
                                                <td className="py-3 px-4">
                                                    <div className="fw-semibold text-gray-700">
                                                        {getComponentDisplayName(component)}
                                                    </div>
                                                    <div className="text-muted small">
                                                        {component.calculationType || (component.percentage ? `${component.percentage}% of PF Wages` : '')}
                                                    </div>
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(component.monthlyAmount || 0)}
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(component.annualAmount || (component.monthlyAmount || 0) * 12)}
                                                </td>
                                            </tr>
                                        ))}
                                    </>
                                )}

                                {/* ESI Components from backend (Employer Contribution in Salary Structure) */}
                                {salaryData?.esiComponents?.length > 0 && (
                                    <>
                                        {salaryData.esiComponents.map((component, index) => (
                                            <tr key={`esi-salary-${index}`}>
                                                <td className="py-3 px-4">
                                                    <div className="fw-semibold text-gray-700">
                                                        {getComponentDisplayName(component)}
                                                    </div>
                                                    <div className="text-muted small">
                                                        {component.calculationType || (component.percentage ? `${component.percentage}% of ESI Wages` : '')}
                                                    </div>
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(component.monthlyAmount || 0)}
                                                </td>
                                                <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                    {formatCurrency(component.annualAmount || (component.monthlyAmount || 0) * 12)}
                                                </td>
                                            </tr>
                                        ))}
                                    </>
                                )}

                                {/* Cost to Company Total */}
                                <tr className="border-top-2 border-gray-300">
                                    <td className="py-3 px-4">
                                        <div className="fw-bold text-gray-800 fs-5">Cost to Company</div>
                                    </td>
                                    <td className="py-3 px-4 text-center fw-bold text-gray-800 fs-5">
                                        {formatCurrency(salaryData?.monthlySalary || totalEarnings)}
                                    </td>
                                    <td className="py-3 px-4 text-center fw-bold text-gray-800 fs-5">
                                        {formatCurrency(salaryData?.ctc || calculateTotalCTC())}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </Card>

                {/* Deductions Section - All data from backend API */}
                <Card
                    title={
                        <div className="d-flex justify-content-between align-items-center">
                            <span className="fw-bold fs-4">Deductions</span>
                            <span className="text-muted fw-semibold">Monthly Deductions</span>
                        </div>
                    }
                    className="shadow-sm mb-4"
                >
                    <div className="table-responsive">
                        <table className="table table-bordered table-hover">
                            <thead className="bg-light">
                                <tr>
                                    <th className="fw-bold text-gray-700 py-3 px-4" style={{ width: '40%' }}>DEDUCTION NAME</th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>CALCULATION TYPE</th>
                                    <th className="fw-bold text-gray-700 py-3 px-4 text-center" style={{ width: '30%' }}>MONTHLY AMOUNT</th>
                                </tr>
                            </thead>
                            <tbody>
                                {/* EPF Components from backend */}
   {/* EPF Components from backend */}
{salaryData?.epfComponents?.length > 0 && (
    salaryData.epfComponents
        .filter(
            (component) =>
                component.componentCode !== "EPF_ADMIN" &&
                component.componentCode !== "EPF_ADMIN_CHARGES" &&
                component.componentCode !== "EDLI_EMPLOYER"
        )
        .map((component, index) => (
                                        <tr key={`epf-deduction-${index}`}>
                                            <td className="py-3 px-4">
                                                <div className="fw-semibold text-gray-700">
                                                    {getComponentDisplayName(component)}
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center">
                                                <div className="text-muted">
                                                    {component.calculationType || (component.percentage ? `${component.percentage}% of PF Wages` : '')}
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                {formatCurrency(component.monthlyAmount || 0)}
                                            </td>
                                        </tr>
                                    ))
                                )}

                                {/* EPF Employee Contribution - mirrors EPF Employer amount (same as per PF rules) */}
                                {(() => {
                                    const epfEmployer = salaryData?.epfComponents?.find(c => c.componentCode === 'EPF_EMPLOYER');
                                    if (!epfEmployer) return null;
                                    return (
                                        <tr key="epf-employee-deduction">
                                            <td className="py-3 px-4">
                                                <div className="fw-semibold text-gray-700">
                                                    EPF - Employee Contribution
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center">
                                                <div className="text-muted">
                                                    {epfEmployer.calculationType || (epfEmployer.percentage ? `${epfEmployer.percentage}% of PF Wages` : '')}
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                {formatCurrency(epfEmployer.monthlyAmount || 0)}
                                            </td>
                                        </tr>
                                    );
                                })()}

                                {/* ESI Components from backend */}
                                {salaryData?.esiComponents?.length > 0 && (
                                    salaryData.esiComponents.map((component, index) => (
                                        <tr key={`esi-deduction-${index}`}>
                                            <td className="py-3 px-4">
                                                <div className="fw-semibold text-gray-700">
                                                    {getComponentDisplayName(component)}
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center">
                                                <div className="text-muted">
                                                    {component.calculationType || (component.percentage ? `${component.percentage}% of ESI Wages` : '')}
                                                </div>
                                            </td>
                                            <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                                {formatCurrency(component.monthlyAmount || 0)}
                                            </td>
                                        </tr>
                                    ))
                                )}

                                {/* Professional Tax - hardcoded ₹200 when eligible, ₹0 when not */}
                                <tr>
                                    <td className="py-3 px-4">
                                        <div className="fw-semibold text-gray-700">Professional Tax</div>
                                    </td>
                                    <td className="py-3 px-4 text-center">
                                        <div className="text-muted">As per state slab</div>
                                    </td>
                                    <td className="py-3 px-4 text-center fw-bold text-gray-800">
                                        {formatCurrency(basicDetails?.eligibleForPt ? 200 : 0)}
                                    </td>
                                </tr>

                                {/* Show message if no deductions data from backend (excluding PT which is always shown) */}
                                {(!salaryData?.epfComponents || salaryData.epfComponents.length === 0) &&
                                 (!salaryData?.esiComponents || salaryData.esiComponents.length === 0) &&
                                 !basicDetails?.eligibleForPt && (
                                    <tr>
                                        <td colSpan="3" className="py-4 text-center text-muted">
                                            No deduction components available from backend.
                                        </td>
                                    </tr>
                                )}

                            </tbody>
                        </table>
                    </div>
                </Card>

                {/* Final Annual Tax Card */}
                {salaryData?.finalAnnualTax > 0 && (
                    <Row gutter={[16, 16]} className="mb-4">
                        <Col xs={24} md={12}>
                            <Card className="shadow-sm border-0 bg-light-danger">
                                <div className="d-flex align-items-center justify-content-between">
                                    <div>
                                        <div className="text-muted fw-semibold">
                                            Final Annual Tax
                                        </div>

                                        <div className="fw-bold fs-2 text-danger">
                                            {formatCurrency(salaryData?.finalAnnualTax)}
                                        </div>

                                        <div className="small text-muted">
                                            As per Income Tax Calculation
                                        </div>
                                    </div>

                                    <div className="text-danger fs-1">
                                        <i className="ki-duotone ki-bank">
                                            <span className="path1"></span>
                                            <span className="path2"></span>
                                        </i>
                                    </div>
                                </div>
                            </Card>
                        </Col>
                    </Row>
                )}


                {/* Additional Sections */}
                <Row gutter={[16, 16]}>
                    {/* Benefits Section */}
                    {salaryData?.benefits?.length > 0 && (
                        <Col xs={24} md={12}>
                            <Card
                                title="Benefits"
                                className="h-100"
                                extra={
                                    <Button type="link" size="small">View Details</Button>
                                }
                            >
                                {salaryData.benefits.map((benefit, index) => (
                                    <div key={index} className="d-flex justify-content-between align-items-center py-2 border-bottom">
                                        <span className="fw-semibold">{benefit.componentName || 'Benefit'}</span>
                                        <span className="fw-bold text-gray-800">{formatCurrency(benefit.amount)}</span>
                                    </div>
                                ))}
                                <div className="d-flex justify-content-between align-items-center py-2 mt-3">
                                    <span className="fw-bold">Total Benefits</span>
                                    <span className="fw-bold text-primary">
                                        {formatCurrency(salaryData.benefits.reduce((sum, benefit) => sum + (benefit.amount || 0), 0))}
                                    </span>
                                </div>
                            </Card>
                        </Col>
                    )}

                    {/* Reimbursements Section */}
                    {salaryData?.reimbursements?.length > 0 && (
                        <Col xs={24} md={12}>
                            <Card title="Reimbursements" className="h-100">
                                {salaryData.reimbursements.map((reimbursement, index) => (
                                    <div key={index} className="d-flex justify-content-between align-items-center py-2 border-bottom">
                                        <span className="fw-semibold">{reimbursement.componentName || 'Reimbursement'}</span>
                                        <span className="fw-bold text-gray-800">{formatCurrency(reimbursement.amount)}</span>
                                    </div>
                                ))}
                            </Card>
                        </Col>
                    )}

                    {/* FBP Components */}
                    {salaryData?.fbpComponents?.length > 0 && (
                        <Col xs={24} md={12}>
                            <Card title="Flexible Benefit Plan" className="h-100">
                                {salaryData.fbpComponents.map((fbp, index) => (
                                    <div key={index} className="d-flex justify-content-between align-items-center py-2 border-bottom">
                                        <span className="fw-semibold">{fbp.componentName || 'FBP Component'}</span>
                                        <span className="fw-bold text-gray-800">{formatCurrency(fbp.amount)}</span>
                                    </div>
                                ))}
                            </Card>
                        </Col>
                    )}
                </Row>


                {/* Display Revision Notice if latest revision exists */}
                {/* {latestRevisionCtc && latestRevisionCtc.revisionId && !loadingRevision && (
                    <div className="alert alert-info alert-dismissible fade show mb-4" role="alert">
                        <div className="d-flex align-items-center">
                            <i className="ki-duotone ki-information fs-2 me-3">
                                <span className="path1"></span>
                                <span className="path2"></span>
                                <span className="path3"></span>
                            </i>
                            <div className="flex-grow-1">
                                <strong>Salary Revision {latestRevisionCtc.revisionStatus || 'Pending'}!</strong>
                                {latestRevisionCtc.revisionStatus === 'APPROVED' ? 
                                    ` There is an approved salary revision for this employee. It will be effective from ${latestRevisionCtc.effectiveDate || 'N/A'}.` :
                                    ` There is a salary revision pending for this employee. It will be effective from ${latestRevisionCtc.effectiveDate || 'N/A'}.`
                                }
                            </div>
                            <Button
                                type="link"
                                icon={<EyeOutlined />}
                                onClick={() => navigate(`/employees/salary-revision-details/${id}?revisionId=${latestRevisionCtc.revisionId}`)}
                                className="ms-2"
                            >
                                View Details
                            </Button>
                        </div>
                    </div>
                )} */}


            </div>
        </div>
    );
}