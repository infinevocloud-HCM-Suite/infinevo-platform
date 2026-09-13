import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { Helmet } from "react-helmet-async";
import { LiaRupeeSignSolid } from "react-icons/lia";
import { FaDiscourse } from "react-icons/fa";
import { PiStudentDuotone } from "react-icons/pi";
import _ from 'lodash';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg } from "../../../shared/helpers/msgHelper";
import { setLoaderState } from "../../../shared/redux/reducers/globalReducer";
import terms2 from '../../../assets/images/terms-2.png';
import IndustryList from "../../../shared/appConfig/industryList";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { getDecodedToken } from "../../../shared/helpers/tokenHelper";
import { Link } from "react-router-dom";
import { useParams } from "react-router-dom";


export default function MySalaryDetails() {
    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [activeTab, setActiveTab] = useState("salaryStructure");
    const [financialYear, setFinancialYear] = useState("2025-26");

    const [payslips, setPayslips] = useState([]);
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(1);
    const [hasNext, setHasNext] = useState(false);

    const [salaryData, setSalaryData] = useState({
        monthlyCTC: "₹0.00",
        yearlyCTC: "₹0.00",
        earnings: {
            basic: "₹0.00",
            houseRentAllowance: "₹0.00",
            fixedAllowance: "₹0.00",
            total: "₹0.00"
        },
        reimbursements: "₹0.00",
        benefits: "₹0.00",
        dateOfJoining: "",
        employeeName: "",
        designation: "",
        department: "",
        workLocation: ""
    });

    const [earningsData, setEarningsData] = useState([]);
    const [benefitsData, setBenefitsData] = useState([]);
    const [reimbursementsData, setReimbursementsData] = useState([]);
    const [epfComponents, setEpfComponents] = useState([]);

    const financialYears = ["2025-26", "2024-25", "2023-24"];
    const months = ["Apr 2025", "May 2025", "Jun 2025", "Jul 2025", "Aug 2025", "Sep 2025",
        "Oct 2025", "Nov 2025", "Dec 2025", "Jan 2026", "Feb 2026", "Mar 2026"];

    useEffect(() => {
        fetchSalaryData();
    }, []);

    const employeeId = getDecodedToken()?.sub;

    const organizationId = localStorage.getItem("organizationId") || "default-org-id";

    const fetchSalaryData = async () => {
        try {
            setSigningIn(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: {
                    employeeId: employeeId
                },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                const employeeData = response.data.data;

                // Extract CTC structure from the nested structure
                const ctcData = employeeData.ctc || {};

                // Format currency values
                const formatCurrency = (amount) => {
                    if (amount === null || amount === undefined) return "₹0.00";
                    return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
                };

                // Calculate total earnings from earnings array
                const totalMonthlyEarnings = ctcData.earnings ?
                    ctcData.earnings.reduce((sum, earning) => sum + (earning.earningCode === 'Bonus' ? 0 : (earning.amount || 0)), 0) : 0;

                const totalYearlyEarnings = ctcData.earnings ?
                    ctcData.earnings.reduce((sum, earning) => sum + (earning.earningCode === 'Bonus' ? (earning.amount || 0) : (earning.amount || 0) * 12), 0) : 0;

                // Calculate total benefits from benefits array (if any)
                const totalBenefitsFromBenefitsArray = ctcData.benefits ?
                    ctcData.benefits.reduce((sum, benefit) => sum + (benefit.amount || 0), 0) : 0;

                // Calculate total benefits from epfComponents (monthly amounts)
                const totalBenefitsFromEpfComponents = ctcData.epfComponents ?
                    ctcData.epfComponents.reduce((sum, comp) => sum + (comp.monthlyAmount || 0), 0) : 0;

                // Final total benefits (we include both explicit benefits array + epfComponents)
                const totalBenefits = totalBenefitsFromBenefitsArray + totalBenefitsFromEpfComponents;

                // Calculate total reimbursements from reimbursements array
                const totalReimbursements = ctcData.reimbursements ?
                    ctcData.reimbursements.reduce((sum, reimbursement) => sum + (reimbursement.amount || 0), 0) : 0;

                // Get basic details
                const basicDetails = employeeData.basicDetails || {};

                // Set EPF components (directly map API epfComponents)
                // Keep the raw shape as returned by API (componentCode/componentLabel/percentage/monthlyAmount/annualAmount)
                setEpfComponents(ctcData.epfComponents || []);

                // Set the salary data with formatted values
                setSalaryData({
                    monthlyCTC: formatCurrency(ctcData.monthlySalary),
                    yearlyCTC: formatCurrency(ctcData.ctc),
                    earnings: {
                        basic: formatCurrency(ctcData.earnings?.find(earning => earning.earningCode === 'Basic')?.amount || 0),
                        houseRentAllowance: formatCurrency(ctcData.earnings?.find(earning => earning.earningCode === 'House Rent Allowance')?.amount || 0),
                        fixedAllowance: formatCurrency(ctcData.earnings?.find(earning => earning.earningCode === 'Fixed Allowance')?.amount || 0),
                        total: formatCurrency(totalMonthlyEarnings),
                        monthlyTotal: formatCurrency(totalMonthlyEarnings),
                        yearlyTotal: formatCurrency(totalYearlyEarnings)
                    },
                    reimbursements: formatCurrency(totalReimbursements),
                    // benefits should show the total benefits (including epfComponents monthly amounts)
                    benefits: formatCurrency(totalBenefits),
                    dateOfJoining: ctcData.dateOfJoining || basicDetails.dateOfJoining || "",
                    employeeName: `${ctcData.firstName || basicDetails.firstName || ''} ${ctcData.middleName || basicDetails.middleName || ''} ${ctcData.lastName || basicDetails.lastName || ''}`.trim(),
                    designation: ctcData.designation || basicDetails.designationName || "",
                    department: ctcData.department || basicDetails.departmentName || "",
                    workLocation: ctcData.workLocation || basicDetails.workLocationName || ""
                });

                // Set detailed data for tables
                setEarningsData(ctcData.earnings || []);
                setBenefitsData(ctcData.benefits || []);
                setReimbursementsData(ctcData.reimbursements || []);

            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error:", error);

            if (error.response) {
                // Server responded with error status
                errorMsg("Error", error.response.data?.message || "Failed to load salary data", true);
            } else if (error.request) {
                // Request was made but no response received
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                // Something else happened
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setSigningIn(false);
        }
    };

    const fetchPayslips = async (year = null, pageNum = page, size = pageSize) => {
        try {
            setSigningIn(true);

            // Derive the ending year for the financial year string like "2025-26"
            // e.g. "2025-26" => 2026, "2024-25" => 2025
            const yearParam = year
                ? (parseInt(String(year).split("-")[0], 10) + 1).toString()
                : undefined;

            const response = await axios.get(`${GlobalConst.API_URL}/api/payrun-employees/payslips`, {
                params: {
                    employeeId: employeeId,
                    page: pageNum,
                    size: size,
                    year: yearParam,
                },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId,
                },
            });

            if (response.data && response.data.payslips) {
                setPayslips(response.data.payslips);
                setPage(response.data.pageContext.page - 1);
                setTotalPages(response.data.pageContext.totalPages);
                setHasNext(response.data.pageContext.hasNext);
            } else {
                setPayslips([]);
            }
        } catch (error) {
            console.error("Payslip Fetch Error:", error);
            errorMsg("Error", "Failed to load payslips", true);
        } finally {
            setSigningIn(false);
        }
    };

    useEffect(() => {
        if (activeTab === "payslips") {
            fetchPayslips(financialYear, 0, pageSize);
        }
    }, [activeTab, financialYear, pageSize]);

    const formatCurrency = (amount) => {
        if (amount === null || amount === undefined) return "₹0.00";
        return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    };

    const getEarningTypeDisplayName = (earningCode) => {
        const typeMap = {
            'Basic': 'Basic',
            'House Rent Allowance': 'House Rent Allowance',
            'Fixed Allowance': 'Fixed Allowance',
            'Conveyance Allowance': 'Conveyance Allowance',
            'SPECIAL_ALLOWANCE': 'Special Allowance',
            'MEDICAL': 'Medical Allowance',
            'BONUS': 'Bonus',
            'INCENTIVE': 'Incentive'
        };
        return typeMap[earningCode] || earningCode;
    };

    const getBenefitTypeDisplayName = (benefitType) => {
        const typeMap = {
            'PF': 'Provident Fund',
            'GRATUITY': 'Gratuity',
            'INSURANCE': 'Insurance',
            'BONUS': 'Bonus'
        };
        return typeMap[benefitType] || benefitType;
    };

    const getReimbursementTypeDisplayName = (reimbursementType) => {
        const typeMap = {
            'TRAVEL': 'Travel Reimbursement',
            'MEDICAL': 'Medical Reimbursement',
            'LTA': 'Leave Travel Allowance',
            'TELEPHONE': 'Telephone Reimbursement'
        };
        return typeMap[reimbursementType] || reimbursementType;
    };

    // Use epf component label if available, otherwise fallback to known mapping
    const getEpfComponentDisplayName = (component) => {
        if (!component) return "";
        if (component.componentLabel) return component.componentLabel;
        const typeMap = {
            'EPF_EMPLOYER': 'EPF - Employer Contribution',
            'EPF_EMPLOYEE': 'EPF - Employee Contribution',
            'EPS': 'EPS Contribution',
            'EDLI_EMPLOYER': 'EDLI - Employer Contribution',
            'EPF_ADMIN': 'EPF Admin Charges - Employer Contribution',
            'EPF_ADMIN_CHARGES': 'EPF Admin Charges - Employer Contribution'
        };
        return typeMap[component.componentCode] || component.componentCode || "";
    };

    const downloadPayslip = async (payrunId) => {
        try {
            setSigningIn(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/payrun-employees/payslips/${payrunId}/download`, {
                responseType: 'blob',
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId,
                },
            });

            // Create blob link to download
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `payslip-${payrunId}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error("Download Error:", error);
            errorMsg("Error", "Failed to download payslip", true);
        } finally {
            setSigningIn(false);
        }
    };

    return (
        <>
            <Helmet>
                <title>Salary Details | HRMS InfiNevoCloud</title>
            </Helmet>

            <div id="kt_app_toolbar" className="app-toolbar pt-5">
                <div id="kt_app_toolbar_container" className="app-container container-fluid d-flex align-items-stretch">
                    <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
                        <div className="page-title d-flex flex-column gap-1 me-3 mb-2">

                            <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">
                                Salary Details
                            </h1>
                        </div>

                        <a href="#" className="btn btn-sm btn-success ms-3 px-4 py-3">
                            Salary Structure
                        </a>
                    </div>
                </div>
            </div>

            <div id="kt_app_content" className="app-content flex-column-fluid">
                <div id="kt_app_content_container" className="app-container container-fluid">
                    {/* Profile Header Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-body pt-9 pb-0">
                            <div className="d-flex flex-wrap flex-sm-nowrap mb-3">
                                <div className="flex-grow-1">
                                    <div className="d-flex justify-content-between align-items-start flex-wrap mb-2">
                                        <div className="d-flex my-4">
                                            <div className="me-0">
                                                <div className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-800 menu-state-bg-light-primary fw-semibold w-200px py-3" data-kt-menu="true">
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3">
                                                            Salary History
                                                        </a>
                                                    </div>
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3">
                                                            Tax Statements
                                                        </a>
                                                    </div>
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3">
                                                            Investment Declarations
                                                        </a>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="d-flex flex-wrap flex-stack">
                                        <div className="d-flex flex-column flex-grow-1 pe-8">
                                            <div className="d-flex flex-wrap">
                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-dollar fs-2 text-success me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{salaryData.monthlyCTC || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Monthly CTC</div>
                                                </div>

                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-chart-simple fs-2 text-primary me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{salaryData.yearlyCTC || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Yearly CTC</div>
                                                </div>

                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-calendar-8 fs-2 text-info me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{salaryData.dateOfJoining || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Date of Joining</div>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="d-flex align-items-center w-200px w-sm-300px flex-column mt-3">
                                            <div className="d-flex justify-content-between w-100 mt-auto mb-2">
                                                <span className="fw-semibold fs-6 text-gray-400">Salary Updated</span>
                                                <span className="fw-bold fs-6">100%</span>
                                            </div>
                                            <div className="h-5px mx-3 w-100 bg-light mb-3">
                                                <div className="bg-success rounded h-5px" role="progressbar" style={{ width: "100%" }} aria-valuenow="100" aria-valuemin="0" aria-valuemax="100"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Salary Tabs Navigation */}
                            <ul className="nav nav-stretch nav-line-tabs nav-line-tabs-2x border-transparent fs-5 fw-bold">
                                <li className="nav-item mt-2">
                                    <a
                                        className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === "salaryStructure" ? "active" : ""}`}
                                        href="#"
                                        onClick={(e) => { e.preventDefault(); setActiveTab("salaryStructure"); }}
                                    >
                                        Salary Structure
                                    </a>
                                </li>
                                <li className="nav-item mt-2">
                                    <a
                                        className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === "payslips" ? "active" : ""}`}
                                        href="#"
                                        onClick={(e) => { e.preventDefault(); setActiveTab("payslips"); }}
                                    >
                                        Payslips
                                    </a>
                                </li>
                                <li className="nav-item mt-2">
                                    <a
                                        className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === "annualEarnings" ? "active" : ""}`}
                                        href="#"
                                        onClick={(e) => { e.preventDefault(); setActiveTab("annualEarnings"); }}
                                    >
                                        Annual Earnings
                                    </a>
                                </li>
                                <li className="nav-item mt-2">
                                    <a
                                        className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === "epfContribution" ? "active" : ""}`}
                                        href="#"
                                        onClick={(e) => { e.preventDefault(); setActiveTab("epfContribution"); }}
                                    >
                                        EPF Contribution Summary
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </div>

                    {/* Salary Structure Tab Content */}
                    {activeTab === "salaryStructure" && (
                        <div className="card mb-5 mb-xl-10">
                            <div className="card-header">
                                <h3 className="card-title align-items-start flex-column">
                                    <span className="card-label fw-bold text-dark">Salary Structure</span>
                                    <span className="text-gray-400 mt-1 fw-semibold fs-6">Financial Year: {financialYear}</span>
                                </h3>
                            </div>
                            <div className="card-body p-9">
                                <div className="row mb-10">
                                    <div className="col-lg-6">
                                        <div className="card card-flush bg-light-primary">
                                            <div className="card-header pt-5">
                                                <h3 className="card-title text-gray-800">Salary Breakup</h3>
                                            </div>
                                            <div className="card-body pt-0">
                                                <div className="d-flex flex-column">
                                                    <div className="d-flex align-items-center justify-content-between py-3 border-bottom">
                                                        <span className="fw-semibold text-gray-600">Monthly CTC</span>
                                                        <span className="fw-bold text-gray-800">{salaryData.monthlyCTC || "Loading..."}</span>
                                                    </div>
                                                    <div className="d-flex align-items-center justify-content-between py-3">
                                                        <span className="fw-semibold text-gray-600">Yearly CTC</span>
                                                        <span className="fw-bold text-gray-800">{salaryData.yearlyCTC || "Loading..."}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="col-lg-6">
                                        <div className="card card-flush bg-light-success">
                                            <div className="card-header pt-5">
                                                <h3 className="card-title text-gray-800">Earnings Summary</h3>
                                            </div>
                                            <div className="card-body pt-0">
                                                <div className="d-flex flex-column">
                                                    <div className="d-flex align-items-center justify-content-between py-3 border-bottom">
                                                        <span className="fw-semibold text-gray-600">Earnings</span>
                                                        <span className="fw-bold text-gray-800">{salaryData.earnings.total || "Loading..."}</span>
                                                    </div>
                                                    <div className="d-flex align-items-center justify-content-between py-3 border-bottom">
                                                        <span className="fw-semibold text-gray-600">Reimbursements</span>
                                                        <span className="fw-bold text-gray-800">{salaryData.reimbursements || "Loading..."}</span>
                                                    </div>
                                                    <div className="d-flex align-items-center justify-content-between py-3">
                                                        <span className="fw-semibold text-gray-600">Benefits</span>
                                                        <span className="fw-bold text-gray-800">{salaryData.benefits || "Loading..."}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Earnings Breakdown Table */}
                                <div className="card card-flush mb-6">
                                    <div className="card-header">
                                        <h3 className="card-title text-gray-800">Earnings Breakdown</h3>
                                    </div>
                                    <div className="card-body">
                                        <div className="table-responsive">
                                            <table className="table table-flush align-middle table-row-bordered table-row-dashed gy-4">
                                                <thead>
                                                    <tr className="fs-7 fw-bold text-gray-400 border-bottom-1">
                                                        <th className="min-w-150px">Earnings</th>
                                                        <th className="min-w-100px text-end">Monthly Amount</th>
                                                        <th className="min-w-100px text-end">Annual Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {earningsData.length > 0 ? (
                                                        earningsData.map((earning, index) => (
                                                            <tr key={index}>
                                                                <td>
                                                                    <span className="fw-semibold text-gray-700">
                                                                        {getEarningTypeDisplayName(earning.earningCode)}
                                                                    </span>
                                                                </td>
                                                                <td className="text-end">
                                                                    <span className="fw-bold text-gray-800">
                                                                        {earning.earningCode === 'Bonus' ? '-' : formatCurrency(earning.amount)}
                                                                    </span>
                                                                </td>
                                                                <td className="text-end">
                                                                    <span className="fw-bold text-gray-800">
                                                                        {earning.earningCode === 'Bonus' ? formatCurrency(earning.amount) : formatCurrency(earning.amount * 12)}
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                        ))
                                                    ) : (
                                                        <tr>
                                                            <td colSpan="3" className="text-center text-muted py-4">
                                                                No earnings data available
                                                            </td>
                                                        </tr>
                                                    )}
                                                    <tr className="border-top">
                                                        <td>
                                                            <span className="fw-bold text-gray-800">Total Earnings</span>
                                                        </td>
                                                        <td className="text-end">
                                                            <span className="fw-bold text-gray-800">{salaryData.earnings.monthlyTotal || "Loading..."}</span>
                                                        </td>
                                                        <td className="text-end">
                                                            <span className="fw-bold text-gray-800">{salaryData.earnings.yearlyTotal || "Loading..."}</span>
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>

                                {/* Benefits Breakdown Table */}
                                <div className="card card-flush mb-6">
                                    <div className="card-header">
                                        <h3 className="card-title text-gray-800">Benefits Breakdown</h3>
                                    </div>
                                    <div className="card-body">
                                        <div className="table-responsive">
                                            <table className="table table-flush align-middle table-row-bordered table-row-dashed gy-4">
                                                <thead>
                                                    <tr className="fs-7 fw-bold text-gray-400 border-bottom-1">
                                                        <th className="min-w-150px">Benefits</th>
                                                        <th className="min-w-100px text-end">Amount (Monthly)</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {epfComponents.length > 0 ? (
                                                        epfComponents.map((component, index) => (
                                                            <tr key={index}>
                                                                <td>
                                                                    <span className="fw-semibold text-gray-700">
                                                                        {getEpfComponentDisplayName(component)}
                                                                    </span>
                                                                    {/* show percentage if available */}
                                                                    {component.percentage ? (
                                                                        <div className="fs-7 text-muted">{component.percentage}%</div>
                                                                    ) : null}
                                                                </td>
                                                                <td className="text-end">
                                                                    <span className="fw-bold text-gray-800">
                                                                        {formatCurrency(component.monthlyAmount)}
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                        ))
                                                    ) : (
                                                        <tr>
                                                            <td colSpan="2" className="text-center text-muted py-4">
                                                                No benefits data available
                                                            </td>
                                                        </tr>
                                                    )}
                                                    <tr className="border-top">
                                                        <td>
                                                            <span className="fw-bold text-gray-800">Total Benefits</span>
                                                        </td>
                                                        <td className="text-end">
                                                            <span className="fw-bold text-gray-800">{salaryData.benefits || "Loading..."}</span>
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>

                                {/* Reimbursements Breakdown Table */}
                                <div className="card card-flush mb-6">
                                    <div className="card-header">
                                        <h3 className="card-title text-gray-800">Reimbursements Breakdown</h3>
                                    </div>
                                    <div className="card-body">
                                        <div className="table-responsive">
                                            <table className="table table-flush align-middle table-row-bordered table-row-dashed gy-4">
                                                <thead>
                                                    <tr className="fs-7 fw-bold text-gray-400 border-bottom-1">
                                                        <th className="min-w-150px">Reimbursements</th>
                                                        <th className="min-w-100px text-end">Amount</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {reimbursementsData.length > 0 ? (
                                                        reimbursementsData.map((reimbursement, index) => (
                                                            <tr key={index}>
                                                                <td>
                                                                    <span className="fw-semibold text-gray-700">
                                                                        {getReimbursementTypeDisplayName(reimbursement.reimbursementType)}
                                                                    </span>
                                                                </td>
                                                                <td className="text-end">
                                                                    <span className="fw-bold text-gray-800">
                                                                        {formatCurrency(reimbursement.amount)}
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                        ))
                                                    ) : (
                                                        <tr>
                                                            <td colSpan="2" className="text-center text-muted py-4">
                                                                No reimbursements data available
                                                            </td>
                                                        </tr>
                                                    )}
                                                    <tr className="border-top">
                                                        <td>
                                                            <span className="fw-bold text-gray-800">Total Reimbursements</span>
                                                        </td>
                                                        <td className="text-end">
                                                            <span className="fw-bold text-gray-800">{salaryData.reimbursements || "Loading..."}</span>
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>

                                {/* EPF Contribution Section */}
                                <div className="card card-flush bg-light-warning">
                                    <div className="card-header">
                                        <h3 className="card-title text-gray-800">EPF Contribution</h3>
                                    </div>
                                    <div className="card-body">
                                        <div className="row">
                                            <div className="col-lg-6">
                                                <div className="d-flex flex-column">
                                                    <h4 className="fw-bold text-gray-700 mb-4">Employer Contribution</h4>
                                                    {epfComponents.length > 0 ? (
                                                        epfComponents.map((component, index) => (
                                                            <div key={index} className="d-flex align-items-center justify-content-between py-2">
                                                                <span className="fw-semibold text-gray-600">
                                                                    {getEpfComponentDisplayName(component)}
                                                                </span>
                                                                <span className="fw-bold text-gray-800">
                                                                    {/* show percentage and monthly amount */}
                                                                    {component.percentage ? `${component.percentage}%` : ""}
                                                                    {component.monthlyAmount ? ` • ${formatCurrency(component.monthlyAmount)}` : ""}
                                                                </span>
                                                            </div>
                                                        ))
                                                    ) : (
                                                        <div className="text-muted py-2">No EPF data available</div>
                                                    )}
                                                </div>
                                            </div>
                                            <div className="col-lg-6">
                                                <div className="d-flex flex-column">
                                                    <h4 className="fw-bold text-gray-700 mb-4">Your Contribution</h4>
                                                    {epfComponents.length > 0 ? (
                                                        epfComponents.map((component, index) => (
                                                            <div key={index} className="d-flex align-items-center justify-content-between py-2">
                                                                <span className="fw-semibold text-gray-600">
                                                                    {getEpfComponentDisplayName(component)}
                                                                </span>
                                                                <span className="fw-bold text-gray-800">
                                                                    {/* As requested: show same values as Employer Contribution */}
                                                                    {component.percentage ? `${component.percentage}%` : ""}
                                                                    {component.monthlyAmount ? ` • ${formatCurrency(component.monthlyAmount)}` : ""}
                                                                </span>
                                                            </div>
                                                        ))
                                                    ) : (
                                                        <div className="text-muted py-2">No EPF data available</div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Payslips Tab Content */}
                    {activeTab === "payslips" && (
                        <div className="card mb-5 mb-xl-10">
                            <div className="card-header border-0 pt-6">
                                <div className="card-title">
                                    <h3 className="fw-bold m-0">Payslips</h3>
                                </div>

                                <div className="card-toolbar d-flex align-items-center gap-4">
                                    <div className="d-flex align-items-center">
                                        <span className="fw-semibold text-gray-600 me-3">Financial Year:</span>
                                        <select
                                            className="form-select form-select-sm w-150px"
                                            value={financialYear}
                                            onChange={(e) => {
                                                const newYear = e.target.value;
                                                setFinancialYear(newYear);
                                                setPage(0);
                                            }}
                                        >
                                            {financialYears.map((year) => (
                                                <option key={year} value={year}>
                                                    {year}
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    <div className="d-flex align-items-center">
                                        <span className="fw-semibold text-gray-600 me-3">Limit:</span>
                                        <select
                                            className="form-select form-select-sm w-100px"
                                            value={pageSize}
                                            onChange={(e) => {
                                                setPageSize(Number(e.target.value));
                                                setPage(0);
                                                fetchPayslips(financialYear, 0, Number(e.target.value));
                                            }}
                                        >
                                            {[5, 10, 20].map((size) => (
                                                <option key={size} value={size}>
                                                    {size}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div className="card-body py-4">
                                {payslips.length === 0 ? (
                                    <div className="text-center py-10">
                                        <div className="mb-6">
                                            <i className="ki-duotone ki-file fs-4tx text-gray-400 mb-4"></i>
                                        </div>
                                        <h3 className="fw-bold text-gray-900 mb-2">
                                            For the financial year: {financialYear}
                                        </h3>
                                        <div className="text-muted fw-semibold fs-6">
                                            No payslips available for this period.
                                        </div>
                                    </div>
                                ) : (
                                    <div className="table-responsive">
                                        <table className="table align-middle table-row-dashed fs-6 gy-4">
                                            <thead>
                                                <tr className="text-start text-gray-500 fw-bold fs-7 text-uppercase gs-0 border-bottom border-gray-200">
                                                    <th className="min-w-125px">Month</th>
                                                    <th className="text-end min-w-100px">Gross Pay</th>
                                                    <th className="text-end min-w-100px">Reimbursements</th>
                                                    <th className="text-end min-w-100px">Deductions</th>
                                                    <th className="text-end min-w-100px">Take Home</th>
                                                    <th className="text-center min-w-150px">Payslip</th>
                                                </tr>
                                            </thead>

                                            <tbody className="fw-semibold text-gray-700">
                                                {payslips.map((p, index) => {
                                                    // ✅ Fetch all data from backend - no frontend calculations
                                                    const grossPay = Number(p.grossEarnings || 0);
                                                    const reimbursements = Number(p.grossReimbursements || 0);
                                                    const deductions = Number(p.grossDeductions || 0);
                                                    const takeHome = Number(p.netPay || 0);

                                                    return (
                                                        <tr key={index} className="hover:bg-light-primary cursor-pointer">
                                                            <td>
                                                                <span className="text-gray-900 fw-semibold">
                                                                    {p.payPeriod}
                                                                </span>
                                                            </td>
                                                            <td className="text-end">
                                                                ₹{grossPay.toLocaleString("en-IN", {
                                                                    minimumFractionDigits: 2,
                                                                })}
                                                            </td>
                                                            <td className="text-end">
                                                                ₹{reimbursements.toLocaleString("en-IN", {
                                                                    minimumFractionDigits: 2,
                                                                })}
                                                            </td>
                                                            <td className="text-end">
                                                                ₹{deductions.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                            </td>
                                                            <td className="text-end text-success fw-bold">
                                                                ₹{takeHome.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                                                            </td>
                                                            <td className="text-center">
                                                                <div className="d-flex justify-content-center gap-2">
                                                                    <Link
                                                                        to={`/payslips/${p.payrunId}`}
                                                                        className="btn btn-sm btn-light-primary fw-bold px-3 py-2"
                                                                    >
                                                                        View
                                                                    </Link>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>

                                        {/* Pagination */}
                                        <div className="d-flex justify-content-between align-items-center mt-5">
                                            <div className="btn-group">
                                                <button
                                                    className="btn btn-sm btn-light"
                                                    disabled={page === 0}
                                                    onClick={() => fetchPayslips(financialYear, page - 1, pageSize)}
                                                >
                                                    <i className="ki-outline ki-left fs-6"></i>
                                                    Previous
                                                </button>
                                                <button
                                                    className="btn btn-sm btn-light"
                                                    disabled={!hasNext}
                                                    onClick={() => fetchPayslips(financialYear, page + 1, pageSize)}
                                                >
                                                    Next
                                                    <i className="ki-outline ki-right fs-6"></i>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Annual Earnings Tab Content */}
                    {activeTab === "annualEarnings" && (
                        <div className="card mb-5 mb-xl-10">
                            <div className="card-header">
                                <div className="card-title">
                                    <h3 className="fw-bold m-0">Annual Earnings</h3>
                                </div>
                                <div className="card-toolbar">
                                    <div className="d-flex align-items-center">
                                        <span className="fw-semibold text-muted me-3">Financial Year:</span>
                                        <select
                                            className="form-select form-select-sm w-150px"
                                            value={financialYear}
                                            onChange={(e) => setFinancialYear(e.target.value)}
                                        >
                                            {financialYears.map(year => (
                                                <option key={year} value={year}>{year}</option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                            </div>
                            <div className="card-body p-9">
                                <div className="text-center py-10">
                                    <div className="mb-7">
                                        <i className="ki-duotone ki-chart-pie-3 fs-4tx text-gray-400 mb-4">
                                            <span className="path1"></span>
                                            <span className="path2"></span>
                                            <span className="path3"></span>
                                        </i>
                                    </div>
                                    <h3 className="fw-bold text-gray-900 mb-3">For the financial year: {financialYear}</h3>
                                    <div className="text-muted fw-semibold fs-5 mb-7">
                                        There are no data for this period
                                    </div>

                                    <div className="d-flex flex-wrap justify-content-center gap-3">
                                        {months.map(month => (
                                            <div key={month} className="bg-light rounded p-3 text-center min-w-100px">
                                                <div className="fw-semibold text-gray-700">{month}</div>
                                                <div className="text-muted fs-7">No data</div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* EPF Contribution Summary Tab Content */}
                    {activeTab === "epfContribution" && (
                        <div className="card mb-5 mb-xl-10">
                            <div className="card-header">
                                <div className="card-title">
                                    <h3 className="fw-bold m-0">EPF Contribution Summary</h3>
                                </div>
                                <div className="card-toolbar">
                                    <div className="d-flex align-items-center">
                                        <span className="fw-semibold text-muted me-3">Financial Year:</span>
                                        <select
                                            className="form-select form-select-sm w-150px"
                                            value={financialYear}
                                            onChange={(e) => setFinancialYear(e.target.value)}
                                        >
                                            {financialYears.map(year => (
                                                <option key={year} value={year}>{year}</option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                            </div>
                            <div className="card-body p-9">
                                <div className="text-center py-10">
                                    <div className="mb-7">
                                        <i className="ki-duotone ki-chart-simple fs-4tx text-gray-400 mb-4">
                                            <span className="path1"></span>
                                            <span className="path2"></span>
                                        </i>
                                    </div>
                                    <h3 className="fw-bold text-gray-900 mb-3">For the financial year: {financialYear}</h3>
                                    <div className="text-muted fw-semibold fs-5 mb-7">
                                        EPF Contribution Summary data will be displayed here
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {signingIn && <Loader />}
        </>
    );
}