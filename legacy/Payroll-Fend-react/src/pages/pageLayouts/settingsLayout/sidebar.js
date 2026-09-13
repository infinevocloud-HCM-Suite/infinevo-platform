import React, { useState } from 'react';
import { PiClockCountdownDuotone, PiNotepadDuotone, PiChatCircleDotsDuotone, PiProjectorScreenDuotone, PiGearSixDuotone, PiCalendarBlankDuotone, PiUserCircleDashedDuotone } from "react-icons/pi";
import { NavLink, useLocation } from 'react-router-dom';
import _ from "lodash";
import { useSelector } from "react-redux";

export default function Sidebar() {
    const location = useLocation();
    const [openMenus, setOpenMenus] = useState({});

    const userInfo = useSelector(state => state.authReducer.userDetails);

    // const menuLinks = [
    //     {
    //         menuLabel: "Organisation",
    //         menuIcon: <PiGearSixDuotone className="ki-duotone ki-element-11 fs-1" />,
    //         children: [
    //             {
    //                 menuLabel: "Profile",
    //                 menuLink: '/organisation-profile'
    //             },
    //             // {
    //             //     menuLabel: "Branding",
    //             //     menuLink: '/create-action'
    //             // },
    //             {
    //                 menuLabel: "Work Locations",
    //                 menuLink: '/work-locations'
    //             },
    //             {
    //                 menuLabel: "Departments",
    //                 menuLink: '/departments'
    //             },
    //             {
    //                 menuLabel: "Designations",
    //                 menuLink: '/designations'
    //             },
    //             // {
    //             //     menuLabel: "Subscription",
    //             //     menuLink: '/subscriptions'
    //             // }
    //         ]
    //     },
    //     {
    //         menuLabel: "Users & Roles",
    //         menuIcon: <PiProjectorScreenDuotone className="ki-duotone ki-element-11 fs-1" />,
    //         children: [
    //             {
    //                 menuLabel: "Users",
    //                 menuLink: '/users'
    //             },
    //             {
    //                 menuLabel: "Roles",
    //                 menuLink: '/roles'
    //             },

    //         ]
    //     },
    //     {
    //         menuLabel: "Taxes",
    //         menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //         children: [
    //             {
    //                 menuLabel: "Tax Details",
    //                 menuLink: '/tax-details'
    //             },


    //         ]
    //     },
    //     {
    //         menuLabel: "Setup & Config",
    //         menuIcon: <PiCalendarBlankDuotone className="ki-duotone ki-element-11 fs-1" />,
    //         children: [
    //             {
    //                 menuLabel: "Pay Schedule",
    //                 menuLink: '/pay-schedules'
    //             },
    //             {
    //                 menuLabel: "Statutory Components",
    //                 menuLink: '/statutory-components'
    //             },
    //             {
    //                 menuLabel: "Salary Components",
    //                 menuLink: '/salary-components'
    //             },
    //             {
    //                 menuLabel: "Employee Portal",
    //                 menuLink: '/employee-portal/preferences'
    //             },
    //             {
    //                 menuLabel: "Claims and Declarations",
    //                 menuLink: '/leaves'
    //             },

    //         ]
    //     },
    //     // {
    //     //     menuLabel: "Customisations",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Email Templates",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Sender Email Preferences",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Salary Templates",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "PDF Templates",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Reporting Tags",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },


    //     //     ]
    //     // },
    //     // {
    //     //     menuLabel: "Automations",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Workflow Rules",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Actions",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Schedules",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Workflow Logs",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //     ]
    //     // },
    //     {
    //         menuLabel: "General",
    //         menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //         children: [
    //             // {
    //             //     menuLabel: "Employee",
    //             //     menuLink: '/my-timesheet-details'
    //             // },
    //             // {
    //             //     menuLabel: "Pay Run",
    //             //     menuLink: '/my-timesheet-details'
    //             // },
    //             // {
    //             //     menuLabel: "Salary Revision",
    //             //     menuLink: '/my-timesheet-details'
    //             // },
    //             {
    //                 menuLabel: "Configure Leave And Attendance",
    //                 menuLink: '/leave-attendance-setup'
    //             },
    //             // {
    //             //     menuLabel: "Loan",
    //             //     menuLink: '/my-timesheet-details'
    //             // },


    //         ]
    //     },
    //     // {
    //     //     menuLabel: "Payments",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Direct Deposits",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //     ]
    //     // },
    //     // {
    //     //     menuLabel: "Custom Modules",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Overview",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //     ]
    //     // },

    //     // {
    //     //     menuLabel: "Integrations",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Zoho Apps",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //     ]
    //     // },
    //     // {
    //     //     menuLabel: "Developer Data",
    //     //     menuIcon: <PiClockCountdownDuotone className="ki-duotone ki-element-11 fs-1" />,
    //     //     children: [
    //     //         {
    //     //             menuLabel: "Connections",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Incoming Webhooks",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //         {
    //     //             menuLabel: "Data Backup",
    //     //             menuLink: '/my-timesheet-details'
    //     //         },
    //     //     ]
    //     // },

    // ];

    // Function to check if a link is active
    const menuLinks = [
        {
            menuLabel: "Organisation",
            menuIcon: <PiGearSixDuotone />,
            color: "#0d6efd",
            items: [
                { menuLabel: "Profile", menuLink: '/organisation-profile' },
                { menuLabel: "Work Locations", menuLink: '/work-locations' },
                { menuLabel: "Departments", menuLink: '/departments' },
                { menuLabel: "Designations", menuLink: '/designations' },
            ]
        },
        {
            menuLabel: "Users & Roles",
            menuIcon: <PiProjectorScreenDuotone />,
            color: "#6f42c1",
            items: [
                { menuLabel: "Users", menuLink: '/users' },
                { menuLabel: "Roles", menuLink: '/roles' },
            ]
        },
        {
            menuLabel: "Taxes",
            menuIcon: <PiClockCountdownDuotone />,
            color: "#dc3545",
            items: [
                { menuLabel: "Tax Details", menuLink: '/tax-details' }
            ]
        },
        {
            menuLabel: "Setup & Config",
            menuIcon: <PiCalendarBlankDuotone />,
            color: "#20c997",
            items: [
                { menuLabel: "Pay Schedule", menuLink: '/pay-schedules' },
                { menuLabel: "Statutory Components", menuLink: '/statutory-components' },
                { menuLabel: "Salary Components", menuLink: '/salary-components' },
                { menuLabel: "Employee Portal", menuLink: '/employee-portal/preferences' },
                { menuLabel: "Claims and Declarations", menuLink: '/it-declaration' },
            ]
        },
        {
            menuLabel: "General",
            menuIcon: <PiUserCircleDashedDuotone />,
            color: "#198754",
            items: [
                { menuLabel: "Configure Leave And Attendance", menuLink: '/leave-attendance-setup' },
            ]
        }
    ];


    const isLinkActive = (link) => {
        if (!_.isEmpty(link)) {
            // Exact match or starts with the link
            return location.pathname === link || location.pathname.startsWith(link + '/');
        }
        return false;
    }

    // Function to toggle menu open/close
    // const toggleMenu = (menuLabel) => {
    //     setOpenMenus(prev => ({
    //         ...prev,
    //         [menuLabel]: !prev[menuLabel]
    //     }));
    // }

    const toggleMenu = (menuLabel) => {
        setOpenMenus((prev) => {
            const isCurrentlyOpen = prev[menuLabel];

            // If it is open → close it (set all closed)
            if (isCurrentlyOpen) {
                return {};
            }

            // Else → close all, open only this one
            return { [menuLabel]: true };
        });
    };

    return (
        <div id="kt_app_sidebar" className="app-sidebar flex-column" data-kt-drawer="true"
            data-kt-drawer-name="app-sidebar" data-kt-drawer-activate="{default: true, lg: false}"
            data-kt-drawer-overlay="true" data-kt-drawer-width="250px" data-kt-drawer-direction="start"
            data-kt-drawer-toggle="#kt_app_sidebar_mobile_toggle" style={{
                position: 'fixed',
                paddingTop: '20px',
                top: '70px', // Or whatever your header height is
                height: 'calc(100% - 70px)',
                zIndex: 99,
                // backgroundColor: '#fdf5f4', // optional to match the light pink color
            }}

        >

            <div className="d-flex flex-column justify-content-between h-100 hover-scroll-overlay-y my-4 d-flex flex-column"
                id="kt_app_sidebar_main" data-kt-scroll="true" data-kt-scroll-activate="true"
                data-kt-scroll-height="auto" data-kt-scroll-dependencies="#kt_app_header"
                data-kt-scroll-wrappers="#kt_app_main" data-kt-scroll-offset="5px">

                <div id="#kt_app_sidebar_menu" data-kt-menu="true" data-kt-menu-expand="false"
                    className="flex-column-fluid menu menu-sub-indention menu-column menu-rounded menu-active-bg mb-7">

                    <div data-kt-menu-trigger="click" className="menu-item here show menu-accordion">

                        <div className='fs-8 fw-bold' style={{
                            padding: '0 1rem',
                            color: '#6c757d',
                            // fontSize: '12px',
                            textTransform: 'uppercase',
                            // fontWeight: 'bold'
                        }}>
                            Administration
                        </div>

                    </div>
                    {/* <div data-kt-menu-trigger="click" className="menu-item here show menu-accordion">
                        <NavLink
                            to="/employees"
                            className={({ isActive }) =>
                                `menu-link ${isActive ? 'active' : ''}`
                            }
                        >
                            <span className="menu-icon">
                                <PiUserCircleDashedDuotone className="ki-duotone ki-element-11 fs-1"/>
                            </span>
                            <span className="menu-title">Employees</span>
                        </NavLink>
                    </div> */}

                    {
                        menuLinks.map((parent, parentIndex) => {
                            // Check if any child link is active
                            // const hasChildActive = parent.children.some(child => isLinkActive(child.menuLink));
                            const hasChildActive = parent.items?.some(child => isLinkActive(child.menuLink));


                            // Determine if this menu should be open by default
                            // const isMenuOpen =
                            //     openMenus[parent.menuLabel] !== undefined
                            //         ? openMenus[parent.menuLabel]
                            //         : hasChildActive;
                            const isMenuOpen = !!openMenus[parent.menuLabel];

                            // let childLinks = parent.children.map((child, childIndex) => (
                            let childLinks = (parent.items || []).map((child, childIndex) => (
                                <div
                                    className="menu-item"
                                    key={`${parent.menuLabel}-${child.menuLabel}-${childIndex}`}
                                >
                                    <NavLink
                                        to={child.menuLink}
                                        className={`menu-link ${isLinkActive(child.menuLink) ? 'active' : ''}`}
                                    >
                                        <span className="menu-bullet">
                                            <span className="bullet bullet-dot"></span>
                                        </span>
                                        <span className="menu-title fs-6 fw-semibold" style={{lineHeight: 1}}>{child.menuLabel}</span>
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
                                        {/* <span className="menu-icon">
                                            {parent.menuIcon}
                                        </span> */}
                                        <span
                                            className="menu-icon"
                                            style={{
                                                background: `${parent.color}15`,
                                                borderRadius: "4px",
                                                padding: "6px",
                                            }}
                                        >
                                            {React.cloneElement(parent.menuIcon, { style: { color: parent.color } })}
                                        </span>
                                        <span className="menu-title fs-6">{parent.menuLabel}</span>
                                        <span className="menu-arrow"></span>
                                    </span>
                                    <div className="menu-sub menu-sub-accordion">
                                        {childLinks}
                                    </div>
                                </div>
                            )
                        })
                    }

                    {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                        <NavLink
                            to="/notes"
                            className={({ isActive }) =>
                                `menu-link ${isActive ? 'active' : ''}`
                            }
                        >
                            <span className="menu-icon">
                                <PiNotepadDuotone className="fs-1" />
                            </span>
                            <span className="menu-title">Profile</span>
                        </NavLink>
                    </div> */}

                    {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                        <NavLink
                            to="/feedback"
                            className={({ isActive }) =>
                                `menu-link ${isActive ? 'active' : ''}`
                            }
                        >
                            <span className="menu-icon">
                                <PiChatCircleDotsDuotone className="fs-1" />
                            </span>
                            <span className="menu-title">Feedback</span>
                        </NavLink>
                    </div> */}

                    {/* <div data-kt-menu-trigger="click" className="menu-item menu-accordion">
                        <NavLink
                            to="/help"
                            className={({ isActive }) =>
                                `menu-link ${isActive ? 'active' : ''}`
                            }
                        >
                            <span className="menu-icon">
                                <i className="ki-duotone ki-rescue fs-1">
                                    <span className="path1"></span>
                                    <span className="path2"></span>
                                </i>
                            </span>
                            <span className="menu-title">Help</span>
                        </NavLink>
                    </div> */}
                </div>
            </div>
        </div>
    )
}
