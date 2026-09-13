import React, { useState } from 'react';
import {
  PiClockCountdownDuotone,
  PiFileTextDuotone,
  PiNotepadDuotone,
  PiChatCircleDotsDuotone,
  PiProjectorScreenDuotone,
  PiGearSixDuotone,
  PiCalendarBlankDuotone,
  PiUserCircleDashedDuotone,
  PiReceiptDuotone,
  PiMinusCircleDuotone
} from "react-icons/pi";
import { NavLink, useLocation } from 'react-router-dom';
import _ from "lodash";
import { useSelector } from "react-redux";
import userlogo from '../../../assets/images/userlogo.png';
import { getDecodedToken } from '../../../shared/helpers/tokenHelper';

export default function Sidebar() {
  const location = useLocation();
  const [openMenus, setOpenMenus] = useState({});

  const userInfo = useSelector(state => state.authReducer.userDetails);
  const employeeName = getDecodedToken()?.name;

  const menuLinks = [
    {
      menuLabel: "Home",
      menuLink: '/home',
      menuIcon: <PiGearSixDuotone className="ki-duotone ki-element-11 fs-1" />,
    },
    {
      menuLabel: "Salary Details",
      menuLink: '/user-salary-details',
      menuIcon: <PiProjectorScreenDuotone className="ki-duotone ki-element-11 fs-1" />,
    },
    {
      menuLabel: "Taxes & Forms",
      menuIcon: <PiFileTextDuotone className="fs-1" />,
      children: [
        { menuLabel: "Tax Calculator", menuLink: "/user-tax-calculator" },
        { menuLabel: "Proof Of Investment", menuLink: "/user-poi" },
      ],
    },
    {
      menuLabel: "Investments",
      menuLink: '/user-investment',
      menuIcon: <PiNotepadDuotone className="ki-duotone ki-element-11 fs-1" />,
    },
    {
      menuLabel: "Reimbursement",
      menuLink: '/reimbursement',
      menuIcon: <PiReceiptDuotone className="ki-duotone ki-element-11 fs-1" />,
    },
    {
      menuLabel: "My Deductions",
      menuLink: '/my-deductions',
      menuIcon: <PiMinusCircleDuotone className="ki-duotone ki-element-11 fs-1" />,
    },
    {
      menuLabel: "Logout",
      menuLink: '/logout',
      menuIcon: <PiUserCircleDashedDuotone className="ki-duotone ki-element-11 fs-1" />,
    }
  ];

  // Enhanced isLinkActive: accepts a link string OR checks children array if passed
  const isLinkActive = (link, children = []) => {
    if (!_.isEmpty(link)) {
      if (location.pathname === link || location.pathname.startsWith(link + '/')) return true;
    }

    if (children && children.length > 0) {
      return children.some(child => {
        const cl = child.menuLink;
        return cl && (location.pathname === cl || location.pathname.startsWith(cl + '/'));
      });
    }

    return false;
  };

  const toggleMenu = (menuLabel) => {
    setOpenMenus(prev => ({
      ...prev,
      [menuLabel]: !prev[menuLabel]
    }));
  };

  return (
    <div
      id="kt_app_sidebar"
      className="app-sidebar flex-column"
      data-kt-drawer="true"
      data-kt-drawer-name="app-sidebar"
      data-kt-drawer-activate="{default: true, lg: false}"
      data-kt-drawer-overlay="true"
      data-kt-drawer-width="250px"
      data-kt-drawer-direction="start"
      data-kt-drawer-toggle="#kt_app_sidebar_mobile_toggle"
      style={{
        position: 'fixed',
        paddingTop: '20px',
        top: '70px',
        height: 'calc(100% - 70px)',
        zIndex: 99,
        backgroundColor: '#fdf5f4',
      }}
    >
      <div
        className="d-flex flex-column justify-content-between h-100 hover-scroll-overlay-y my-2 d-flex flex-column"
        id="kt_app_sidebar_main"
        data-kt-scroll="true"
        data-kt-scroll-activate="true"
        data-kt-scroll-height="auto"
        data-kt-scroll-dependencies="#kt_app_header"
        data-kt-scroll-wrappers="#kt_app_main"
        data-kt-scroll-offset="5px"
      >
        {/* User Profile Section */}
        <div className="d-flex flex-column align-items-center flex-center py-10">
          <div className="symbol symbol-100px symbol-circle mb-5">
            <img src={userlogo} alt="Profile" />
            <div className="symbol-badge bg-success start-100 top-100 border-4 h-15px w-15px ms-n3 mt-n3"></div>
          </div>

          <div className="text-center">
            <div className="fs-3 fw-bold text-dark mb-1">{employeeName}</div>
          </div>

          <NavLink
            to="/userProfile"
            className="btn btn-sm btn-light-primary mt-3"
          >
            View My Profile
          </NavLink>
        </div>

        <div
          id="#kt_app_sidebar_menu"
          data-kt-menu="true"
          data-kt-menu-expand="false"
          className="flex-column-fluid menu menu-sub-indention menu-column menu-rounded menu-active-bg mb-7"
        >
          {menuLinks.map((menuItem, index) => {
            const hasChildren = menuItem.children && menuItem.children.length > 0;
            const isActive = isLinkActive(menuItem.menuLink, menuItem.children || []);
            const isMenuOpen = openMenus[menuItem.menuLabel] !== undefined
              ? openMenus[menuItem.menuLabel]
              : (hasChildren && isActive); // open by default if child route active

            // Parent with children: render toggle + submenu
            if (hasChildren) {
              return (
                <div
                  key={`${menuItem.menuLabel}-${index}`}
                  className={`menu-item menu-accordion ${isMenuOpen ? 'show here' : ''}`}
                >
                  {/* The parent toggle - not a NavLink so clicking toggles submenu only */}
                  <div
                    role="button"
                    aria-expanded={isMenuOpen}
                    aria-controls={`submenu-${index}`}
                    onClick={() => toggleMenu(menuItem.menuLabel)}
                    className={`menu-link d-flex align-items-center px-3 py-2 text-decoration-none ${isActive ? 'active' : ''}`}
                    style={{ cursor: 'pointer' }}
                  >
                    <span className="menu-icon me-2">{menuItem.menuIcon}</span>
                    <span className="menu-title flex-grow-1">{menuItem.menuLabel}</span>
                    <span className={`menu-arrow ms-2 ${isMenuOpen ? 'rotate-90' : ''}`}>▸</span>
                  </div>

                  <div id={`submenu-${index}`} className={`menu-sub menu-sub-accordion ${isMenuOpen ? 'show' : ''}`}>
                    {menuItem.children.map((child, childIndex) => (
                      <div
                        className="menu-item"
                        key={`${menuItem.menuLabel}-${child.menuLabel}-${childIndex}`}
                        // prevent parent click when clicking child (extra safety)
                        onClick={(e) => e.stopPropagation()}
                      >
                        <NavLink
                          to={child.menuLink}
                          onClick={(e) => e.stopPropagation()}
                          className={({ isActive: childActive }) => `menu-link d-flex align-items-center px-3 py-1 text-decoration-none ${childActive ? 'active' : 'text-muted'}`}
                        >
                          <span className="menu-bullet">
                            <span className="bullet bullet-dot"></span>
                          </span>
                          <span className="menu-title ms-2">{child.menuLabel}</span>
                        </NavLink>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }

            // Normal single-level item
            return (
              <div key={`${menuItem.menuLabel}-${index}`} className={`menu-item ${isActive ? 'here' : ''}`}>
                <NavLink
                  to={menuItem.menuLink}
                  className={({ isActive }) =>
                    `menu-link d-flex align-items-center px-3 py-2 text-decoration-none ${isActive ? 'active' : ''}`
                  }
                >
                  <span className="menu-icon">{menuItem.menuIcon}</span>
                  <span className="menu-title ms-2">{menuItem.menuLabel}</span>
                </NavLink>
              </div>
            );
          })}
        </div>
      </div>

      {/* Small local styles used for rotation and collapse state visual helpers */}
      <style jsx>{`
        .rotate-90 {
          transform: rotate(90deg);
          transition: transform 150ms ease;
        }
        .menu-sub {
          display: none;
          padding-left: 6px;
        }
        .menu-sub.show {
          display: block;
        }
        .menu-link.active {
          background: rgba(22, 119, 255, 0.08);
          color: #0b5ed7;
          font-weight: 600;
        }
      `}</style>
    </div>
  );
}
