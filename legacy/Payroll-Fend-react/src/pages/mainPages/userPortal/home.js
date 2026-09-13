import { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { LiaRupeeSignSolid } from "react-icons/lia";

export default function Home() {
    const [userData, setUserData] = useState({
        name: "Kaushtubh Nathani",
        designation: "Full Stack Developer",
        employeeId: "KN001",
        email: "kr12071997@gmail.com",
        department: "IT",
        office: "Head Office",
        dateOfJoining: "01/08/2025"
    });

    const [salaryStats, setSalaryStats] = useState({
        monthlyCTC: "₹25,000.00",
        yearlyCTC: "₹3,00,000.00",
        lastPayslip: "August 2025",
        taxDeduction: "₹2,500.00"
    });

    // Initialize charts after component mounts
    useEffect(() => {
        initializeCharts();
    }, []);

    const initializeCharts = () => {
        // This would typically initialize Keentheme charts
        // For demo purposes, we'll simulate chart initialization
        setTimeout(() => {
            console.log("Charts initialized");
        }, 100);
    };

    const quickActions = [
        { label: "View Payslip", icon: "ki-file", link: "/user-salary-details", color: "primary" },
        { label: "Update Profile", icon: "ki-profile-user", link: "/userProfile", color: "success" },
        { label: "Tax Summary", icon: "ki-chart-simple", link: "#", color: "info" },
        { label: "Documents", icon: "ki-folder", link: "/documents", color: "warning" }
    ];

    return (
        <>
            <Helmet>
                <title>Dashboard | HRMS InfiNevoCloud</title>
            </Helmet>

            <div id="kt_app_toolbar" className="app-toolbar pt-5">
                <div id="kt_app_toolbar_container" className="app-container container-fluid d-flex align-items-stretch">
                    <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
                        <div className="page-title d-flex flex-column gap-1 me-3 mb-2">
                            <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">
                                Dashboard
                            </h1>
                        </div>
                        <a href="/user-salary-details" className="btn btn-sm btn-success ms-3 px-4 py-3">
                            Download Payslip
                        </a>
                    </div>
                </div>
            </div>

            <div id="kt_app_content" className="app-content flex-column-fluid">
                <div id="kt_app_content_container" className="app-container container-fluid">
                    
                    {/* Welcome Card */}
                   

                    {/* Quick Actions */}
                    <div className="row g-5 g-xl-8 mb-5 mb-xl-10">
                        {quickActions.map((action, index) => (
                            <div key={index} className="col-xl-3 col-lg-6">
                                <a href={action.link} className="card bg-light-${action.color} hoverable">
                                    <div className="card-body">
                                        <i className={`ki-duotone ${action.icon} fs-2hx text-${action.color} mb-4`}>
                                            <span className="path1"></span>
                                            <span className="path2"></span>
                                        </i>
                                        <div className="text-gray-900 fw-bold fs-5 mb-2">{action.label}</div>
                                        <div className="fw-semibold text-gray-600">Click to view details</div>
                                    </div>
                                </a>
                            </div>
                        ))}
                    </div>

                    {/* Charts and Visualizations */}
                    <div className="row g-5 g-xl-8">
                        
                        {/* Salary Distribution Chart */}
                        <div className="col-xl-6">
                            <div className="card card-flush h-md-100">
                                <div className="card-header pt-7">
                                    <h3 className="card-title align-items-start flex-column">
                                        <span className="card-label fw-bold text-dark">Salary Distribution</span>
                                        <span className="text-gray-400 mt-1 fw-semibold fs-6">Monthly Breakdown</span>
                                    </h3>
                                </div>
                                <div className="card-body pt-5">
                                    <div id="kt_charts_widget_1" className="min-h-auto" style={{height: "300px"}}></div>
                                    <div className="d-flex flex-wrap justify-content-center gap-5 pt-5">
                                        <div className="d-flex align-items-center">
                                            <div className="bullet bullet-dot bg-primary me-3 h-10px w-10px"></div>
                                            <span className="fw-semibold text-gray-600">Basic Salary</span>
                                        </div>
                                        <div className="d-flex align-items-center">
                                            <div className="bullet bullet-dot bg-success me-3 h-10px w-10px"></div>
                                            <span className="fw-semibold text-gray-600">Allowances</span>
                                        </div>
                                        <div className="d-flex align-items-center">
                                            <div className="bullet bullet-dot bg-info me-3 h-10px w-10px"></div>
                                            <span className="fw-semibold text-gray-600">Benefits</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Tax Deduction Chart */}
                        <div className="col-xl-6">
                            <div className="card card-flush h-md-100">
                                <div className="card-header pt-7">
                                    <h3 className="card-title align-items-start flex-column">
                                        <span className="card-label fw-bold text-dark">Tax Overview</span>
                                        <span className="text-gray-400 mt-1 fw-semibold fs-6">Current Financial Year</span>
                                    </h3>
                                </div>
                                <div className="card-body pt-5">
                                    <div id="kt_charts_widget_2" className="min-h-auto" style={{height: "300px"}}></div>
                                    <div className="d-flex justify-content-between pt-5">
                                        <div className="text-center">
                                            <span className="fw-bold text-gray-800 d-block fs-3">{salaryStats.taxDeduction}</span>
                                            <span className="text-gray-400 fw-semibold">Monthly Tax</span>
                                        </div>
                                        <div className="text-center">
                                            <span className="fw-bold text-gray-800 d-block fs-3">₹30,000.00</span>
                                            <span className="text-gray-400 fw-semibold">Yearly Tax</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Earnings Trend */}
                        <div className="col-xl-8">
                            <div className="card card-flush h-md-100">
                                <div className="card-header pt-7">
                                    <h3 className="card-title align-items-start flex-column">
                                        <span className="card-label fw-bold text-dark">Earnings Trend</span>
                                        <span className="text-gray-400 mt-1 fw-semibold fs-6">Last 6 Months</span>
                                    </h3>
                                    <div className="card-toolbar">
                                        <button className="btn btn-sm btn-light" data-kt-menu-trigger="click">
                                            Last 6 Months
                                        </button>
                                    </div>
                                </div>
                                <div className="card-body pt-5">
                                    <div id="kt_charts_widget_3" className="min-h-auto" style={{height: "350px"}}></div>
                                </div>
                            </div>
                        </div>

                        {/* Recent Activity */}
                        <div className="col-xl-4">
                            <div className="card card-flush h-md-100">
                                <div className="card-header pt-7">
                                    <h3 className="card-title align-items-start flex-column">
                                        <span className="card-label fw-bold text-dark">Recent Activity</span>
                                        <span className="text-gray-400 mt-1 fw-semibold fs-6">Latest updates</span>
                                    </h3>
                                </div>
                                <div className="card-body pt-5">
                                    <div className="timeline timeline-line-dashed">
                                        <div className="timeline-item">
                                            <div className="timeline-content">
                                                <div className="fw-bold text-gray-800">Payslip Generated</div>
                                                <div className="text-muted fs-7">August 2025 payslip is ready</div>
                                                <span className="fs-8 text-success">2 hours ago</span>
                                            </div>
                                        </div>
                                        <div className="timeline-item">
                                            <div className="timeline-content">
                                                <div className="fw-bold text-gray-800">Profile Updated</div>
                                                <div className="text-muted fs-7">Your profile information was updated</div>
                                                <span className="fs-8 text-primary">1 day ago</span>
                                            </div>
                                        </div>
                                        <div className="timeline-item">
                                            <div className="timeline-content">
                                                <div className="fw-bold text-gray-800">Tax Statement</div>
                                                <div className="text-muted fs-7">Q1 tax statement available</div>
                                                <span className="fs-8 text-info">3 days ago</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}