import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Card, Avatar, Button, Typography } from "antd";
import { useNavigate, useParams, NavLink, Outlet } from "react-router-dom";
import { EditOutlined } from "@ant-design/icons";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg } from "../../../shared/helpers/msgHelper";

const { Title } = Typography;

export default function ViewEmployee() {
    const navigate = useNavigate();
    const { id } = useParams();
    const [loading, setLoading] = useState(true);
    const [employee, setEmployee] = useState(null);
    const [hrUsers, setHrUsers] = useState([]);
    const [loadingHrUsers, setLoadingHrUsers] = useState(false);

    console.log("Employee ID from URL:", id);

    // Get organizationId from localStorage
    const organizationId = localStorage.getItem("organizationId") || "default-org-id";

    // Fetch HR users
    const fetchHrUsers = async () => {
        try {
            setLoadingHrUsers(true);
            console.log("Fetching HR users...");
            const resp = await axios.get(`${GlobalConst.API_URL}/auth/hr-users`, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                }
            });

            console.log("HR Users API Response:", resp.data);

            if (resp.data && resp.data.data) {
                setHrUsers(resp.data.data);
                console.log("HR Users set:", resp.data.data);
            } else {
                console.warn("Unexpected /auth/hr-users response format", resp);
            }
        } catch (err) {
            console.error("Failed to fetch HR users", err);
        } finally {
            setLoadingHrUsers(false);
        }
    };

    // Fetch employee data from API
    const fetchEmployeeData = async () => {
        try {
            console.log("Fetching data for employee ID:", id);
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: {
                    employeeId: id
                },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                setEmployee(response.data.data);
                console.log("HR User field in employee:", response.data.data.basicDetails?.hrUser);
            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error:", error);

            if (error.response) {
                errorMsg("Error", error.response.data?.message || "Failed to load employee data", true);
            } else if (error.request) {
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchEmployeeData();
        fetchHrUsers();
    }, [id]);

    if (loading) {
        return <Loader />;
    }

    if (!employee) {
        return (
            <div className="container-fluid p-10 bg-white">
                <div className="text-center">
                    <h3>Employee not found</h3>
                    <Button type="primary" onClick={() => navigate('/employees')}>
                        Back to Employees
                    </Button>
                </div>
            </div>
        );
    }

    // Helper functions to pass to child components
    const formatDate = (dateString) => {
        if (!dateString) return '-';
        return new Date(dateString).toLocaleDateString('en-GB');
    };

    const formatAddress = (address) => {
        if (!address) return '-';
        const { addressLine1, addressLine2, city, state, zipCode } = address;
        return `${addressLine1 || ''}${addressLine2 ? ', ' + addressLine2 : ''}${city ? ', ' + city : ''}${state ? ', ' + state : ''}${zipCode ? ' - ' + zipCode : ''}`.trim();
    };

    const formatCurrency = (amount) => {
        if (!amount) return '₹0.00';
        return `₹${amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    };

    const getHrDisplay = (hrValue) => {
        if (!hrValue) return '-';
        
        const matched = hrUsers.find(u => {
            if (u.userEmail && u.userEmail.toLowerCase() === String(hrValue).toLowerCase()) {
                return true;
            }
            if (u.userId && String(u.userId) === String(hrValue)) {
                return true;
            }
            const fullName = `${u.firstName || ''} ${u.lastName || ''}`.trim();
            if (fullName && fullName.toLowerCase() === String(hrValue).toLowerCase()) {
                return true;
            }
            return false;
        });

        if (matched) {
            const displayName = `${matched.firstName || ''} ${matched.lastName || ''}`.trim();
            return displayName + (matched.userEmail ? ` (${matched.userEmail})` : '');
        }

        return String(hrValue) || '-';
    };

    // Extract basic details for header
    const { basicDetails } = employee;

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - View Employee</title>
            </Helmet>

            {/* Header */}
            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <div className="d-flex align-items-center gap-3">
                    <Avatar
                        size={40}
                        style={{ backgroundColor: '#1890ff', fontSize: '18px' }}
                    >
                        {basicDetails?.firstName?.charAt(0) || ''}{basicDetails?.lastName?.charAt(0) || ''}
                    </Avatar>
                    <div>
                        <h6 className="mb-0 fw-semibold">
                            {basicDetails?.employeeNumber || 'N/A'} - {basicDetails?.firstName || ''} {basicDetails?.lastName || ''}
                        </h6>
                        <small className="text-muted">{basicDetails?.designationName || basicDetails?.designation || 'No Designation'}</small>
                    </div>
                </div>
                <Button onClick={() => navigate('/employees')}>
                    Back to Employees
                </Button>
            </div>

            {/* Body */}
            <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
                <div className="d-flex flex-column flex-lg-row-fluid">
                    <div className="container-fluid p-4 bg-white">
                        {/* Navigation Tabs */}
                        <div className="mb-4">
                            <ul className="nav nav-tabs nav-line-tabs nav-line-tabs-2x mb-5 fs-6">
                                <li className="nav-item">
                                    <NavLink
                                        to={`/employees/view/${id}`}
                                        end
                                        className={({ isActive }) =>
                                            `nav-link ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        Overview
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink
                                        to={`/employees/view/${id}/salary-details`}
                                        className={({ isActive }) =>
                                            `nav-link ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        Salary Details
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink
                                        to={`/employees/view/${id}/investments-and-proofs`}
                                        className={({ isActive }) =>
                                            `nav-link ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        Investments
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink
                                        to={`/employees/view/${id}/payroll-and-forms`}
                                        className={({ isActive }) =>
                                            `nav-link ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        Payroll & Forms
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink
                                        to={`/employees/view/${id}/loan`}
                                        className={({ isActive }) =>
                                            `nav-link ${isActive ? 'active' : ''}`
                                        }
                                    >
                                        Loan
                                    </NavLink>
                                </li>
                            </ul>
                        </div>

                        {/* Outlet for child routes */}
                        <div className="mt-4">
                            <Outlet context={{ 
                                employee, 
                                formatDate, 
                                formatAddress, 
                                formatCurrency, 
                                getHrDisplay, 
                                hrUsers, 
                                loadingHrUsers,
                                organizationId,
                                id 
                            }} />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}