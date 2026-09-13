import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import React, { useMemo, useState } from "react";
import {
  FaBuilding, FaUsersCog, FaMoneyBillWave, FaCogs, FaMagic, FaRobot, FaCreditCard, FaPuzzlePiece
} from "react-icons/fa";
import { MdSettings } from "react-icons/md";
import { BiChevronRight } from "react-icons/bi";
import { BiSearch } from "react-icons/bi";



export default function AllSettingsComponents() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const settingsCategories = [
    {
      title: "Organisation Settings",
      icon: <FaBuilding className="fs-2" />,
      gradient: "rgba(13,110,253",    // blue → keep same
      iconColor: "#0d6efd",
      items: [
        { name: "Profile", path: "/organisation-profile" },
        { name: "Work Locations", path: "/work-locations" },
        { name: "Departments", path: "/departments" },
        { name: "Designations", path: "/designations" }
      ]
    },
    {
      title: "Users & Roles",
      icon: <FaUsersCog className="fs-2" />,
      gradient: "rgba(111,66,193",    // purple
      iconColor: "#6f42c1",
      items: [
        { name: "Users", path: "/users" },
        { name: "Roles", path: "/roles" }
      ]
    },
    {
      title: "Taxes",
      icon: <FaMoneyBillWave className="fs-2" />,
      gradient: "rgba(220,53,69",     // red / attention
      iconColor: "#dc3545",
      items: [{ name: "Tax Details", path: "/tax-details" }]
    },
    {
      title: `Setup & Configurations`,
      icon: <FaCogs className="fs-2" />,
      gradient: "rgba(32,201,151",    // teal system config
      iconColor: "#20c997",
      items: [
        { name: "Pay Schedule", path: "/pay-schedules" },
        { name: "Statutory Components", path: "/statutory-components" },
        { name: "Salary Components", path: "/salary-components" },
        { name: "Employee Portal", path: "/employee-portal/preferences" },
        { name: `Claims & Declarations`, path: "/it-declaration" }
      ]
    },
    {
      title: "Customisations",
      icon: <FaMagic className="fs-2" />,
      gradient: "rgba(255,193,7",     // amber creativity
      iconColor: "#ffc107",
      items: [
        { name: "Email Templates", path: "/settings/email-templates" },
        { name: "Sender Email Preferences", path: "/settings/email-preferences" },
        { name: "Salary Templates", path: "/settings/salary-templates" },
        { name: "PDF Templates", path: "/settings/pdf-templates" },
        { name: "Reporting Tags", path: "/settings/reporting-tags" }
      ]
    },
    {
      title: "Automations",
      icon: <FaRobot className="fs-2" />,
      gradient: "rgba(253,126,20",    // orange motion
      iconColor: "#fd7e14",
      items: [
        { name: "Workflow Rules", path: "/settings/workflow-rules" },
        { name: "Actions", path: "/settings/actions" },
        { name: "Schedules", path: "/settings/schedules" },
        { name: "Workflow Logs", path: "/settings/workflow-logs" }
      ]
    },
    {
      title: "Module Settings",
      icon: <MdSettings className="fs-2" />,
      gradient: "rgba(108,117,125",   // gray neutral
      iconColor: "#6c757d",
      items: [
        { name: "General", path: "/settings/module-general" },
        { name: "Employees", path: "/settings/module-employees" },
        { name: "Pay Runs", path: "/settings/module-payruns" },
        { name: "Salary revisions", path: "/settings/salary-revisions" },
        { name: "Leave and Attendance", path: "/leave-attendance-setup" },
        { name: "Loans", path: "/settings/loans" }
      ]
    },
    {
      title: "Payments",
      icon: <FaCreditCard className="fs-2" />,
      gradient: "rgba(25,135,84",     // green banking
      iconColor: "#198754",
      items: [{ name: "Direct Deposits", path: "/settings/direct-deposits" }]
    },
    {
      title: "Custom Modules",
      icon: <FaPuzzlePiece className="fs-2" />,
      gradient: "rgba(232,62,140",    // pink extension
      iconColor: "#e83e8c",
      items: [{ name: "Overview", path: "/settings/custom-modules" }]
    }
  ];

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return settingsCategories;
    return settingsCategories
      .map(cat => ({
        ...cat,
        items: cat.items.filter(i => i.name.toLowerCase().includes(q))
      }))
      .filter(cat => cat.items.length > 0);
  }, [query, settingsCategories]);

  return (
    <>
      <Helmet><title>Settings Hub</title></Helmet>

      <div id="kt_app_content" className="app-content flex-column-fluid rounded">
        <div id="kt_app_content_container" className="app-container container-fluid py-4">

          {/* Top bar with title + search */}
          <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
            <h1 className="text-gray-900 fw-bold mb-0">Settings Hub</h1>
            <div className="position-relative" style={{ minWidth: 280 }}>
              <div class="input-group input-group-sm">
                <span class="input-group-text" id="basic-addon1">
                  <i class="ki-duotone ki-magnifier fs-4">
                    <span class="path1"></span>
                    <span class="path2"></span>
                  </i>
                </span>
                <input
                  type="text"
                  class="form-control "
                  placeholder="Search settings…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  aria-label="Search settings" />
              </div>
            </div>
          </div>

          <div className="row g-4">
            {filtered.map((category, index) => (
              <div key={index} className="col-xxl-3 col-xl-4 col-lg-4 col-md-6">
                <div className="card tile h-100 border-0">
                  <div className="card-header border-0 bg-transparent pt-4 pb-0 px-4">
                    <div className="d-flex align-items-center">
                      {/* <span className="icon-badge me-3">
                        {category.icon}
                      </span> */}
                      <span
                        className="icon-badge me-3"
                        style={{
                          background: `radial-gradient(120% 120% at 0% 0%, ${category.gradient},0.20) 0%, ${category.gradient},0.08) 60%, ${category.gradient},0.03) 100%)`,
                          boxShadow: `inset 0 0 0 1px ${category.iconColor}33`
                        }}
                      >
                        {React.cloneElement(category.icon, { style: { color: category.iconColor } })}
                      </span>
                      <span className="fw-bold text-gray-900 fs-5">{category.title}</span>
                    </div>
                  </div>

                  <div className="card-body px-2 py-3">
                    <ul className="list-unstyled mb-0">
                      {category.items.map((item, itemIndex) => (
                        <li key={itemIndex}>
                          <div
                            role="button"
                            tabIndex={0}
                            className="item-row d-flex align-items-center justify-content-between px-3 py-2 rounded-1"
                            onClick={() => navigate(item.path)}
                            onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && navigate(item.path)}
                            aria-label={item.name}
                            title={item.name}
                          >
                            <span className="text-truncate text-gray-700 fw-semibold">{item.name}</span>
                            <BiChevronRight />
                          </div>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>
            ))}

            {filtered.length === 0 && (
              <div className="col-12">
                <div class="alert alert-dismissible bg-light-primary d-flex flex-center flex-column py-10 px-10 px-lg-20 mb-10">
                  <i class="ki-duotone ki-information-5 fs-5tx text-primary mb-5"><span class="path1"></span><span class="path2"></span><span class="path3"></span></i>
                  <div class="text-center">
                    <h1 class="fw-bold mb-5">No Results</h1>
                    <div class="separator separator-dashed border-primary opacity-25 mb-5"></div>
                    <div class="mb-9 text-gray-900">
                      No results for <strong>“{query}”</strong>. Try a different term.
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Styles: subtle gradient tiles, soft icon badges, hover lift & glow */}
      <style>{`
        .tile {
          background: var(--kt-card-bg, #fff);
          border-radius: 16px !important;
          box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
          transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
          border: 1px solid rgba(0,0,0,0.05);
          overflow: hidden;
        }
        .tile:hover {
          transform: translateY(-4px);
          box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
          border-color: rgba(13,110,253,.25);
        }
        .icon-badge {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 42px; height: 42px;
          border-radius: 8px;
          background: radial-gradient(120% 120% at 0% 0%, rgba(13,110,253,0.12) 0%, rgba(13,110,253,0.06) 60%, rgba(13,110,253,0.02) 100%);
          box-shadow: inset 0 0 0 1px rgba(13,110,253,.15);
        }
        .item-row {
          background: transparent;
          border: 1px solid transparent;
          transition: background-color .15s ease, transform .15s ease, border-color .15s ease, color .15s ease;
        }
        .item-row:hover, .item-row:focus {
          background: rgba(13,110,253,.06);
          border-color: rgba(13,110,253,.18);
          transform: translateX(2px);
          outline: none;
        }
        .item-row:active {
          transform: translateX(1px) scale(0.99);
        }
        .item-row .chevron {
          opacity: .0;
          transition: opacity .15s ease, transform .15s ease;
          transform: translateX(-4px);
        }
        .item-row:hover .chevron, .item-row:focus .chevron {
          opacity: 1;
          transform: translateX(0);
        }
        /* Search icon color */
        .bi-search { color: var(--bs-gray-500, #6c757d); }

        /* Dark mode friendly tweaks (Keen uses data-bs-theme="dark") */
        [data-bs-theme="dark"] .tile {
          background: #0f172a;
          border-color: rgba(255,255,255,0.06);
          box-shadow: 0 6px 18px rgba(0,0,0,0.6);
        }
        [data-bs-theme="dark"] .item-row:hover {
          background: rgba(13,110,253,.18);
          border-color: rgba(13,110,253,.35);
        }
        [data-bs-theme="dark"] .icon-badge {
          background: radial-gradient(120% 120% at 0% 0%, rgba(13,110,253,0.25) 0%, rgba(13,110,253,0.12) 60%, rgba(13,110,253,0.06) 100%);
          box-shadow: inset 0 0 0 1px rgba(13,110,253,.35);
        }
      `}</style>
    </>
  );
}
