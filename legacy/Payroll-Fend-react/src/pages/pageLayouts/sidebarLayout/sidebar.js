import React, { useState } from 'react';
import {
    PiUserCircleDashedDuotone,    // Employees
    PiProjectorScreenDuotone,      // Approvals
    PiMoneyDuotone,                // Pay Runs
    PiFileTextDuotone,             // Form 16
    PiHandCoinsDuotone,            // Loans
    PiGearDuotone,                 // Settings
    PiReceiptDuotone,              // Reimbursement
    PiCalendarBlankDuotone,        // Leave Management
} from "react-icons/pi";
import { NavLink, useLocation } from 'react-router-dom';
import _ from "lodash";

export default function Sidebar({ isSidebarOpen, closeSidebar }) {
    const location = useLocation();
    const [openMenus, setOpenMenus] = useState({});

    // Close sidebar when clicking outside on mobile
    const handleOverlayClick = () => {
        closeSidebar();
    };


    const menuLinks = [
        {
            menuLabel: "Approvals",
            menuIcon: <PiProjectorScreenDuotone className="fs-1" />,
            children: [
                {
                    menuLabel: "Reimbursement",
                    menuLink: '/reimbursement-approvals'
                },
                {
                    menuLabel: "Proof of Investment",
                    menuLink: '/proof-of-investment'
                },
                {
                    menuLabel: "Salary Revision",
                    menuLink: '/salary-revision-approvals'
                },
            ]
        },
        {
            menuLabel: "Taxes & Forms",
            menuIcon: <PiFileTextDuotone className="fs-1" />,
            children: [
                { menuLabel: "TDS Liabilities", menuLink: "/tds-liabilities" },
                { menuLabel: "Challans", menuLink: "/challans" },
                { menuLabel: "Form 24Q", menuLink: "/form24q" },
                { menuLabel: "Form 16", menuLink: "/form16s" },
                { menuLabel: "Tax Calculator", menuLink: "/tax-calculator" },
            ],
        },
        {
            menuLabel: "Leave Management",
            menuIcon: <PiCalendarBlankDuotone className="fs-1" />,
            children: [
                {
                    menuLabel: "Leave Allocation",
                    menuLink: '/leave-allocation'
                },
                {
                    menuLabel: "Mark Leaves Taken",
                    menuLink: '/mark-leaves'
                },
            ]
        },
    ];


    // Function to check if a link is active
    const isLinkActive = (link) => {
        if (!_.isEmpty(link)) {
            // Exact match or starts with the link
            return location.pathname === link || location.pathname.startsWith(link + '/');
        }
        return false;
    }

    // Function to toggle menu open/close
    const toggleMenu = (menuLabel) => {
        setOpenMenus(prev => ({
            ...prev,
            [menuLabel]: !prev[menuLabel]
        }));
    }

    return (
        <>
            {/* Overlay for mobile */}
            {isSidebarOpen && (
                <div
                    className="drawer-overlay"
                    style={{
                        position: 'fixed',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        backgroundColor: 'rgba(0, 0, 0, 0.5)',
                        zIndex: 104,
                        display: 'block'
                    }}
                    onClick={handleOverlayClick}
                />
            )}

            <div id="kt_app_sidebar" className={`app-sidebar flex-column ${isSidebarOpen ? 'drawer drawer-on' : ''}`} data-kt-drawer="true"
                data-kt-drawer-name="app-sidebar" data-kt-drawer-activate="{default: true, lg: false}"
                data-kt-drawer-overlay="true" data-kt-drawer-width="250px" data-kt-drawer-direction="start"
                data-kt-drawer-toggle="#kt_app_sidebar_mobile_toggle">

                <div className="d-flex flex-column justify-content-between h-100 hover-scroll-overlay-y my-2 d-flex flex-column"
                    id="kt_app_sidebar_main" data-kt-scroll="true" data-kt-scroll-activate="true"
                    data-kt-scroll-height="auto" data-kt-scroll-dependencies="#kt_app_header"
                    data-kt-scroll-wrappers="#kt_app_main" data-kt-scroll-offset="5px">

                    <div id="#kt_app_sidebar_menu" data-kt-menu="true" data-kt-menu-expand="false"
                        className="flex-column-fluid menu menu-sub-indention menu-column menu-rounded menu-active-bg mb-7">

                        <div data-kt-menu-trigger="click" className="menu-item here show menu-accordion">
                            <NavLink
                                to="/onboarding-dashboard"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    <i className="ki-duotone ki-element-11 fs-1">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                        <span className="path3"></span>
                                        <span className="path4"></span>
                                    </i>
                                </span>
                                <span className="menu-title">Dashboard</span>
                            </NavLink>
                        </div>
                        <div data-kt-menu-trigger="click" className="menu-item here show menu-accordion">
                            <NavLink
                                to="/employees"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    <PiUserCircleDashedDuotone className="ki-duotone ki-element-11 fs-1" />
                                </span>
                                <span className="menu-title">Employees</span>
                            </NavLink>
                        </div>

                        {
                            menuLinks.map((parent, parentIndex) => {
                                // Check if any child link is active
                                const hasChildActive = parent.children.some(child => isLinkActive(child.menuLink));

                                // Determine if this menu should be open by default
                                const isMenuOpen =
                                    openMenus[parent.menuLabel] !== undefined
                                        ? openMenus[parent.menuLabel]
                                        : hasChildActive;

                                let childLinks = parent.children.map((child, childIndex) => (
                                    <div
                                        className="menu-item"
                                        key={`${parent.menuLabel}-${child.menuLabel}-${childIndex}`}
                                    >
                                        <NavLink
                                            to={child.menuLink}
                                            className={`menu-link ${isLinkActive(child.menuLink) ? 'active' : ''}`}
                                            onClick={closeSidebar}
                                        >
                                            <span className="menu-bullet">
                                                <span className="bullet bullet-dot"></span>
                                            </span>
                                            <span className="menu-title">{child.menuLabel}</span>
                                        </NavLink>
                                    </div>
                                ));

                                return (
                                    <div
                                        key={`${parent.menuLabel}-${parentIndex}`}
                                        data-kt-menu-trigger="click"
                                        className={`menu-item here menu-accordion ${isMenuOpen ? 'show' : ''}`}
                                        onClick={() => toggleMenu(parent.menuLabel)}
                                    >
                                        <span className="menu-link">
                                            <span className="menu-icon">
                                                {parent.menuIcon}
                                            </span>
                                            <span className="menu-title">{parent.menuLabel}</span>
                                            <span className="menu-arrow"></span>
                                        </span>
                                        <div className="menu-sub menu-sub-accordion">
                                            {childLinks}
                                        </div>
                                    </div>
                                )
                            })
                        }

                        <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/payruns"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    <PiMoneyDuotone className="fs-1" />
                                </span>
                                <span className="menu-title">Pay Runs</span>
                            </NavLink>
                        </div>

                        <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/admin-reimbursements"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    <PiReceiptDuotone className="fs-1" />
                                </span>
                                <span className="menu-title">Reimbursement</span>
                            </NavLink>
                        </div>

                        <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/employee-deductions"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    <PiHandCoinsDuotone className="fs-1" />
                                </span>
                                <span className="menu-title">Deductions</span>
                            </NavLink>
                        </div>
                        {/*
                         <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/form16s"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                            >
                                <span className="menu-icon">
                                    <PiFileTextDuotone className="fs-1" />
                                </span>
                                <span className="menu-title">Form 16</span>
                            </NavLink>
                        </div>  */}

                        {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/loans"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                            >
                                <span className="menu-icon">
                                    < PiHandCoinsDuotone className="fs-1" />
                                    <span className="path1"></span>
                                    <span className="path2"></span>

                                </span>
                                <span className="menu-title">Loans</span>
                            </NavLink>
                        </div> */}

                        {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/givings"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                            >
                                <span className="menu-icon">
                                    < PiChatCircleDotsDuotone className="fs-1" />
                                    <span className="path1"></span>
                                    <span className="path2"></span>

                                </span>
                                <span className="menu-title">Givings</span>
                            </NavLink>
                        </div> */}


                        {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/documents"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                            >
                                <span className="menu-icon">
                                    < PiFolderDuotone className="fs-1" />
                                    <span className="path1"></span>
                                    <span className="path2"></span>

                                </span>
                                <span className="menu-title">Documents</span>
                            </NavLink>
                        </div> */}

                        {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/Reports"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                            >
                                <span className="menu-icon">
                                    < PiChartBarDuotone className="fs-1" />
                                    <span className="path1"></span>
                                    <span className="path2"></span>

                                </span>
                                <span className="menu-title">Reports</span>
                            </NavLink>
                        </div> */}

                        <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                            <NavLink
                                to="/all-settings"
                                className={({ isActive }) =>
                                    `menu-link ${isActive ? 'active' : ''}`
                                }
                                onClick={closeSidebar}
                            >
                                <span className="menu-icon">
                                    < PiGearDuotone className="fs-1" />
                                    <span className="path1"></span>
                                    <span className="path2"></span>

                                </span>
                                <span className="menu-title">Settings</span>
                            </NavLink>
                        </div>
                    </div>
                </div>
            </div>
        </>
    )
}