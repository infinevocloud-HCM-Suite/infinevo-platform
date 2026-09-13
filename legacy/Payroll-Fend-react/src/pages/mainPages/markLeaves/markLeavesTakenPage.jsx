import React, { useState, useEffect, useCallback } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useLocation } from "react-router-dom";
import { Modal, DatePicker } from "antd";
import dayjs from "dayjs";
import Swal from "sweetalert2";
import {
  PiCalendarCheckDuotone,
  PiUserCircleDuotone,
  PiInfoDuotone,
  PiEyeDuotone,
  PiMagnifyingGlassDuotone,
  PiBuildingsDuotone,
  PiIdentificationBadgeDuotone,
  PiArrowSquareOutDuotone,
  PiCheckSquareOffsetDuotone,
  PiXBold,
  PiArrowCounterClockwiseDuotone,
  PiCheckBold,
  PiArrowLeftBold,
} from "react-icons/pi";
import {
  getStoredLeaveEmployees,
  fetchLeaveAllocationsFromApi,
  isLeaveExpired,
} from "../../../shared/services/leaveStore";

// Helper to extract initials for employee avatar
const getAvatarInitials = (name) => {
  if (!name) return "EMP";
  const parts = name.trim().split(" ");
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
};

// Gradient avatar palette
const AVATAR_COLORS = [
  { bg: "linear-gradient(135deg, #e0f2fe, #bae6fd)", text: "#0369a1" },
  { bg: "linear-gradient(135deg, #fef3c7, #fde68a)", text: "#b45309" },
  { bg: "linear-gradient(135deg, #dcfce7, #bbf7d0)", text: "#15803d" },
  { bg: "linear-gradient(135deg, #f3e8ff, #e9d5ff)", text: "#7e22ce" },
  { bg: "linear-gradient(135deg, #ffe4e6, #fecdd3)", text: "#be123c" },
];

const currentYearStr = String(new Date().getFullYear());

export default function MarkLeavesTakenPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const initialYear = location.state?.selectedYear || currentYearStr;
  
  // Year Selector state
  const [selectedYear, setSelectedYear] = useState(initialYear);
  const [employees, setEmployees] = useState(() => getStoredLeaveEmployees(initialYear));

  useEffect(() => {
    if (location.state?.selectedYear && location.state.selectedYear !== selectedYear) {
      setSelectedYear(location.state.selectedYear);
    }
  }, [location.state?.selectedYear]);

  // Search text state
  const [searchTerm, setSearchTerm] = useState("");

  // Status Filter state
  const [statusFilter, setStatusFilter] = useState("ALL");

  // Selection state for Table Checkboxes (for Mark Leave action)
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState([]);

  // State for Review Eye Modal
  const [selectedEmployeeForModal, setSelectedEmployeeForModal] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const [loading, setLoading] = useState(false);

  // Reload latest employees whenever page is mounted / navigated to or year changed
  const loadEmployees = useCallback(async (yearToLoad = selectedYear) => {
    setLoading(true);
    try {
      const data = await fetchLeaveAllocationsFromApi(yearToLoad);
      if (Array.isArray(data)) {
        setEmployees(data);
      } else {
        setEmployees(getStoredLeaveEmployees(yearToLoad));
      }
    } catch (err) {
      console.warn(`Using stored leave employees for year ${yearToLoad}:`, err);
      setEmployees(getStoredLeaveEmployees(yearToLoad));
    } finally {
      setLoading(false);
    }
  }, [selectedYear]);

  useEffect(() => {
    loadEmployees(selectedYear);

    // Listen to storage events and custom update events for real-time reactivity
    const handleStorageChange = (e) => {
      if (!e?.key || e.key.includes("hrms_leave_employees_state") || e.key.includes(selectedYear)) {
        loadEmployees(selectedYear);
      }
    };
    const handleCustomUpdate = (e) => {
      if (!e?.detail?.year || e.detail.year === selectedYear) {
        loadEmployees(selectedYear);
      }
    };

    window.addEventListener("storage", handleStorageChange);
    window.addEventListener("leave_store_updated", handleCustomUpdate);
    return () => {
      window.removeEventListener("storage", handleStorageChange);
      window.removeEventListener("leave_store_updated", handleCustomUpdate);
    };
  }, [loadEmployees, selectedYear]);

  // Open employee leave details modal
  const handleOpenReviewModal = (employee) => {
    setSelectedEmployeeForModal(employee);
    setIsModalOpen(true);
  };

  const handleCloseReviewModal = () => {
    setIsModalOpen(false);
    setSelectedEmployeeForModal(null);
  };

  // Helper to compute summary for a single employee
  const computeEmployeeSummary = (employee) => {
    const balances = employee.balances || [];
    const totalAllocated = balances.reduce((acc, curr) => acc + (Number(curr.allocated) || 0), 0);
    const totalConsumed = balances.reduce((acc, curr) => acc + (Number(curr.consumed) || 0), 0);
    const totalBalance = balances.reduce(
      (acc, curr) => acc + (curr.balance !== undefined ? Number(curr.balance) : 0),
      0
    );
    const totalLop = balances.reduce((acc, curr) => acc + (Number(curr.lop) || 0), 0);

    const isAllExpired =
      balances.length > 0 && balances.every((b) => isLeaveExpired(b.expirationDate));
    const hasSomeExpired = balances.some((b) => isLeaveExpired(b.expirationDate));

    // Compute overall status
    let overallStatus = "Available";
    let statusType = "success";

    if (isAllExpired) {
      overallStatus = "Expired";
      statusType = "expired";
    } else if (totalLop > 0 || totalBalance <= 0) {
      overallStatus = "Exhausted";
      statusType = "danger";
    } else if (totalBalance <= 3) {
      overallStatus = "Low Balance";
      statusType = "warning";
    }

    return {
      totalAllocated,
      totalConsumed,
      totalBalance,
      totalLop,
      overallStatus,
      statusType,
      isAllExpired,
      hasSomeExpired,
    };
  };



  // Clear all search and status filters
  const handleClearAllFilters = () => {
    setSearchTerm("");
    setStatusFilter("ALL");
  };

  // Filter employees for the main table:
  // Key feature: ANY SELECTED EMPLOYEE ALWAYS REMAINS VISIBLE even when typing a new search query or clearing search!
  const filteredEmployees = employees.filter((emp) => {
    const summary = computeEmployeeSummary(emp);
    const query = searchTerm.toLowerCase().trim();

    // 1. If this employee is already selected, ALWAYS keep them visible in the table!
    const isSelected = selectedEmployeeIds.includes(emp.id);

    // 2. Matches the active search query (by name, code, department, or designation)
    const matchesSearch =
      !query ||
      emp.name?.toLowerCase().includes(query) ||
      emp.code?.toLowerCase().includes(query) ||
      emp.department?.toLowerCase().includes(query) ||
      emp.designation?.toLowerCase().includes(query);

    const matchesStatus =
      statusFilter === "ALL" ||
      (statusFilter === "AVAILABLE" && summary.statusType === "success") ||
      (statusFilter === "LOW_BALANCE" && summary.statusType === "warning") ||
      (statusFilter === "EXHAUSTED" && summary.statusType === "danger");

    return (isSelected || matchesSearch) && matchesStatus;
  });

  // Table Checkbox handlers (for selecting rows to Mark Leave)
  const isAllSelected =
    filteredEmployees.length > 0 &&
    filteredEmployees.every((emp) => selectedEmployeeIds.includes(emp.id));

  const handleToggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedEmployeeIds([]);
    } else {
      setSelectedEmployeeIds(filteredEmployees.map((emp) => emp.id));
    }
  };

  const handleToggleSelectEmployee = (empId) => {
    setSelectedEmployeeIds((prev) =>
      prev.includes(empId) ? prev.filter((id) => id !== empId) : [...prev, empId]
    );
  };

  // Navigate to /markleaveaddemploy with only the selected employees
  const handleNavigateToMarkLeave = () => {
    if (selectedEmployeeIds.length === 0) {
      Swal.fire({
        icon: "info",
        title: "No Employees Selected",
        text: "Please select one or more employees using the table checkboxes to mark their leaves.",
        confirmButtonColor: "#0284c7",
        confirmButtonText: "Got it",
      });
      return;
    }

    // Check if any selected employee has ALL their leaves expired
    const selectedEmps = employees.filter((emp) => selectedEmployeeIds.includes(emp.id));
    const fullyExpired = selectedEmps.filter((emp) => {
      const summary = computeEmployeeSummary(emp);
      return summary.isAllExpired;
    });

    if (fullyExpired.length > 0) {
      const names = fullyExpired.map((e) => e.name).join(", ");
      Swal.fire({
        icon: "warning",
        title: "Leaves Expired",
        html: `Cannot mark leaves for <b>${names}</b> because all allocated leaves have expired and are unavailable.`,
        confirmButtonColor: "#0284c7",
        confirmButtonText: "Got it",
      });
      return;
    }

    navigate("/markleaveaddemploy", {
      state: {
        selectedEmployeeIds,
        selectedYear,
      },
    });
  };

  const getStatusBadge = (status, statusType) => {
    if (status === "Expired" || statusType === "expired" || (statusType === "danger" && status === "Expired")) {
      return (
        <span
          style={{
            backgroundColor: "#fef2f2",
            color: "#dc2626",
            border: "1px solid #fca5a5",
            borderRadius: "16px",
            padding: "4px 14px",
            fontSize: "12px",
            fontWeight: "600",
            display: "inline-flex",
            alignItems: "center",
            gap: "5px",
            letterSpacing: "0.01em",
          }}
        >
          Expired
        </span>
      );
    }
    if (statusType === "warning" || status === "Low Balance") {
      return (
        <span
          style={{
            backgroundColor: "#fff7ed",
            color: "#c2410c",
            border: "1px solid #fed7aa",
            borderRadius: "16px",
            padding: "4px 14px",
            fontSize: "12px",
            fontWeight: "600",
            display: "inline-flex",
            alignItems: "center",
            gap: "5px",
            letterSpacing: "0.01em",
          }}
        >
          Low Balance
        </span>
      );
    }
    if (statusType === "danger" || status === "Exhausted") {
      return (
        <span
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            border: "1px solid #fecaca",
            borderRadius: "16px",
            padding: "4px 14px",
            fontSize: "12px",
            fontWeight: "600",
            display: "inline-flex",
            alignItems: "center",
            gap: "5px",
            letterSpacing: "0.01em",
          }}
        >
          Exhausted
        </span>
      );
    }
    return (
      <span
        style={{
          backgroundColor: "#f0fdf4",
          color: "#15803d",
          border: "1px solid #bbf7d0",
          borderRadius: "16px",
          padding: "4px 14px",
          fontSize: "12px",
          fontWeight: "600",
          display: "inline-flex",
          alignItems: "center",
          gap: "5px",
          letterSpacing: "0.01em",
        }}
      >
        Available
      </span>
    );
  };

  const isAnyFilterActive = searchTerm !== "" || statusFilter !== "ALL";

  return (
    <>
      <Helmet>
        <title>Mark Leaves Taken | Employee Leave Overview</title>
      </Helmet>

      {/* Page Header / Toolbar */}
      <div id="kt_app_toolbar" className="app-toolbar py-3 py-lg-6 mb-2">
        <div
          id="kt_app_toolbar_container"
          className="app-container container-fluid d-flex align-items-stretch"
        >
          <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
            {/* Title & Description */}
            <div className="page-title d-flex flex-column gap-1 me-3">
              <div className="d-flex align-items-center gap-3">
                <span
                  style={{
                    background: "linear-gradient(135deg, #e0f2fe, #bae6fd)",
                    borderRadius: "10px",
                    padding: "8px 10px",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <PiCalendarCheckDuotone size={24} style={{ color: "#0284c7" }} />
                </span>
                <div>
                  <h1 className="page-heading text-dark fw-bolder fs-2 mb-1" style={{ lineHeight: "1.25" }}>
                    Mark Leaves Taken
                  </h1>
                  <ul className="breadcrumb breadcrumb-separatorless fw-semibold fs-7 my-0 pb-1">
                    <li className="breadcrumb-item text-muted">Leave Management</li>
                    <li className="breadcrumb-item">
                      <span className="bullet bg-gray-400 w-5px h-2px mx-2"></span>
                    </li>
                    <li
                      className="breadcrumb-item text-primary cursor-pointer fw-bold"
                      onClick={() => navigate("/mark-leaves", { state: { selectedYear } })}
                    >
                      Mark Leaves
                    </li>
                    <li className="breadcrumb-item">
                      <span className="bullet bg-gray-400 w-5px h-2px mx-2"></span>
                    </li>
                    <li className="breadcrumb-item text-muted">Add Leaves Selection</li>
                  </ul>
                  <span className="text-gray-500 fw-medium fs-7 d-block" style={{ marginTop: "2px", lineHeight: "1.4" }}>
                    Select employees to mark leaves, track allocations, consumed days, and loss of pay (LOP)
                  </span>
                </div>
              </div>
            </div>

            {/* Top Right Action */}
            <div className="d-flex align-items-center gap-3 flex-wrap">
              <button
                type="button"
                className="btn btn-light d-flex align-items-center gap-2 fw-bold px-4 py-2 border border-gray-300 shadow-xs"
                style={{ borderRadius: "8px" }}
                onClick={() => navigate("/mark-leaves", { state: { selectedYear } })}
              >
                <PiArrowLeftBold size={16} />
                <span>Back to Mark Leaves</span>
              </button>
              <button
                id="btn-nav-mark-leave"
                type="button"
                className="btn btn-primary d-flex align-items-center gap-2 fw-bold shadow-sm px-4 py-2"
                style={{ borderRadius: "8px" }}
                onClick={handleNavigateToMarkLeave}
              >
                <PiCalendarCheckDuotone size={18} />
                <span>
                  Mark Leave{" "}
                  {selectedEmployeeIds.length > 0 ? `(${selectedEmployeeIds.length} Selected)` : ""}
                </span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div id="kt_app_content" className="app-content flex-column-fluid">
        <div id="kt_app_content_container" className="app-container container-fluid">



          {/* Selection Banner when employees are checked for leave marking */}
          {selectedEmployeeIds.length > 0 && (
            <div
              className="d-flex align-items-center justify-content-between flex-wrap gap-3 p-4 mb-4 rounded-3 border"
              style={{
                backgroundColor: "#eff6ff",
                borderColor: "#bfdbfe",
              }}
            >
              <div className="d-flex align-items-center gap-2">
                <PiCheckSquareOffsetDuotone size={22} className="text-primary" />
                <span className="fs-6 fw-bold text-gray-900">
                  {selectedEmployeeIds.length} employee{selectedEmployeeIds.length > 1 ? "s" : ""} selected for marking leaves
                </span>
                <span className="text-gray-500 fs-7">
                  — click "Mark Leave" to open the sub-grid editor.
                </span>
              </div>
              <div className="d-flex align-items-center gap-2">
                <button
                  type="button"
                  className="btn btn-sm btn-link text-gray-600 fw-semibold text-decoration-none"
                  onClick={() => setSelectedEmployeeIds([])}
                >
                  Clear Checkboxes
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-primary fw-bold d-flex align-items-center gap-2 shadow-xs"
                  onClick={handleNavigateToMarkLeave}
                >
                  <PiCalendarCheckDuotone size={16} />
                  <span>Mark Leave ({selectedEmployeeIds.length})</span>
                </button>
              </div>
            </div>
          )}

          {/* Main Card — Employee-Centric Leave Balance Table */}
          <div
            className="card border-0 shadow-sm mb-8"
            style={{
              backgroundColor: "#ffffff",
              borderRadius: "14px",
              boxShadow: "0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.03)",
            }}
          >
            {/* Card Header with Search, Filters & Action */}
            <div className="p-6 pb-4">
              {/* Top: Title, Subtitle & Mark Leave Button */}
              <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 mb-4">
                {/* Left Side Title & Subtitle */}
                <div>
                  <h2
                    className="fw-bold text-gray-900 mb-1"
                    style={{
                      fontSize: "1.25rem",
                      fontWeight: 700,
                      letterSpacing: "-0.01em",
                    }}
                  >
                    Employee Leave Balances
                  </h2>
                  <p
                    className="text-gray-500 mb-0"
                    style={{
                      fontSize: "0.875rem",
                      fontWeight: 400,
                    }}
                  >
                    Type to search and select employees. Selected employees will stay visible even when searching for others.
                  </p>
                </div>

                {/* Right Side Action: Mark Leave */}
                <div>
                  <button
                    type="button"
                    className="btn btn-primary fw-bold d-flex align-items-center gap-2 px-4 shadow-sm"
                    style={{ height: "38px", borderRadius: "8px" }}
                    onClick={handleNavigateToMarkLeave}
                  >
                    <PiCalendarCheckDuotone size={18} />
                    <span>
                      Mark Leave{" "}
                      {selectedEmployeeIds.length > 0 ? `(${selectedEmployeeIds.length})` : ""}
                    </span>
                  </button>
                </div>
              </div>

              {/* Search Bar & Filter Toolbar placed directly below Employee Leave Balances */}
              <div className="d-flex align-items-center gap-3 flex-wrap">
                {/* Real-time Search Input */}
                <div
                  className="d-flex align-items-center bg-light rounded-3 px-3 py-2 border flex-grow-1"
                  style={{
                    maxWidth: "380px",
                    minWidth: "260px",
                    borderColor: "#e2e8f0",
                    transition: "all 0.2s ease",
                  }}
                >
                  <PiMagnifyingGlassDuotone size={18} className="text-gray-400 me-2 flex-shrink-0" />
                  <input
                    type="text"
                    className="form-control form-control-sm border-0 bg-transparent text-gray-900 fw-medium shadow-none p-0"
                    placeholder="Search employee, ID, dept..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                  {searchTerm && (
                    <button
                      type="button"
                      className="btn btn-icon btn-sm text-gray-400 hover-text-dark p-0 ms-1"
                      onClick={() => setSearchTerm("")}
                      title="Clear search text"
                    >
                      <PiXBold size={14} />
                    </button>
                  )}
                </div>

                {/* Status Filter */}
                <select
                  className="form-select form-select-sm border-0 bg-light fw-bold text-gray-700 cursor-pointer"
                  style={{ borderRadius: "8px", width: "auto", padding: "8px 28px 8px 12px" }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="AVAILABLE">Available</option>
                  <option value="LOW_BALANCE">Low Balance</option>
                  <option value="EXHAUSTED">Exhausted / LOP</option>
                </select>

                {/* Year Selector */}
                <div className="d-flex align-items-center gap-1.5 bg-light px-2.5 py-1 rounded-3">
                  <span className="text-gray-600 fw-bold fs-8">Year:</span>
                  <DatePicker
                    picker="year"
                    value={selectedYear ? dayjs(String(selectedYear), "YYYY") : dayjs()}
                    onChange={(date, dateString) => {
                      if (dateString) {
                        setSelectedYear(dateString);
                        loadEmployees(dateString);
                      }
                    }}
                    allowClear={false}
                    style={{
                      width: "105px",
                      height: "32px",
                      borderRadius: "6px",
                      fontWeight: "600",
                      borderColor: "#e2e8f0",
                    }}
                  />
                </div>

                {/* Clear All Filters / All Employees Button */}
                {isAnyFilterActive && (
                  <button
                    type="button"
                    className="btn btn-light-danger btn-sm d-flex align-items-center gap-1 fw-bold px-3 py-2"
                    style={{ borderRadius: "8px" }}
                    onClick={handleClearAllFilters}
                    title="Reset search and show all employees"
                  >
                    <PiArrowCounterClockwiseDuotone size={16} />
                    <span>All Employees</span>
                  </button>
                )}
              </div>

              {/* Selected Employees Tag Chips Bar */}
              {selectedEmployeeIds.length > 0 && (
                <div className="d-flex align-items-center gap-2 flex-wrap mt-3 pt-3 border-top">
                  <span className="text-gray-500 fs-8 fw-bold text-uppercase">
                    Selected ({selectedEmployeeIds.length}):
                  </span>
                  {selectedEmployeeIds.map((id) => {
                    const emp = employees.find((e) => e.id === id);
                    if (!emp) return null;
                    return (
                      <span
                        key={id}
                        className="badge bg-light-primary text-primary border border-primary-subtle d-inline-flex align-items-center gap-1 px-2 py-1 fs-7"
                        style={{ borderRadius: "6px" }}
                      >
                        <PiCheckBold size={12} className="text-success" />
                        <b>{emp.name}</b>
                        <span className="text-gray-500 fs-8">({emp.code})</span>
                        <PiXBold
                          size={12}
                          className="cursor-pointer ms-1 hover-text-danger"
                          onClick={() => handleToggleSelectEmployee(id)}
                          title="Unselect employee"
                        />
                      </span>
                    );
                  })}
                  <button
                    type="button"
                    className="btn btn-link text-danger fs-8 fw-bold p-0 text-decoration-none ms-2"
                    onClick={() => setSelectedEmployeeIds([])}
                  >
                    Clear All Selected
                  </button>
                </div>
              )}
            </div>

            {/* Table Container */}
            <div className="p-6 pt-2">
              <div
                style={{
                  border: "1px solid #f1f5f9",
                  borderRadius: "10px",
                  overflow: "hidden",
                }}
              >
                <div className="table-responsive">
                  <table
                    className="table align-middle gs-0 gy-4 mb-0"
                    style={{ borderCollapse: "separate", width: "100%" }}
                  >
                    {/* Table Header */}
                    <thead>
                      <tr
                        style={{
                          backgroundColor: "#f8fafc",
                          borderBottom: "1px solid #e2e8f0",
                        }}
                      >
                        {/* Checkbox Select All Column */}
                        <th
                          className="ps-4 py-4 text-center"
                          style={{ width: "48px" }}
                        >
                          <input
                            type="checkbox"
                            className="form-check-input cursor-pointer"
                            style={{
                              width: "18px",
                              height: "18px",
                              borderRadius: "4px",
                              border: "1.5px solid #cbd5e1",
                            }}
                            checked={isAllSelected}
                            onChange={handleToggleSelectAll}
                            title="Select All Visible Employees"
                          />
                        </th>

                        <th
                          className="ps-3 py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "32%",
                          }}
                        >
                          Employee
                        </th>
                        <th
                          className="text-center py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "16%",
                          }}
                        >
                          Allocated (Annual Days)
                        </th>
                        <th
                          className="text-center py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "16%",
                          }}
                        >
                          Consumed (YTD)
                        </th>
                        <th
                          className="text-center py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "16%",
                          }}
                        >
                          Remaining Balance
                        </th>
                        <th
                          className="text-center py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "10%",
                          }}
                        >
                          LOP (YTD)
                        </th>
                        <th
                          className="text-center pe-6 py-4 text-gray-700"
                          style={{
                            fontSize: "0.85rem",
                            fontWeight: "600",
                            width: "10%",
                          }}
                        >
                          Action
                        </th>
                      </tr>
                    </thead>

                    {/* Table Body */}
                    <tbody>
                      {loading ? (
                        <tr>
                          <td colSpan="7" className="text-center py-8 text-gray-500">
                            <div
                              className="spinner-border text-primary mb-2"
                              role="status"
                              style={{ width: "2rem", height: "2rem" }}
                            >
                              <span className="visually-hidden">Loading...</span>
                            </div>
                            <span className="fw-semibold d-block text-gray-600">
                              Fetching live leave allocations from server...
                            </span>
                          </td>
                        </tr>
                      ) : filteredEmployees.length === 0 ? (
                        <tr>
                          <td colSpan="7" className="text-center py-8 text-gray-500">
                            <PiUserCircleDuotone size={36} className="text-gray-400 mb-2 mx-auto d-block" />
                            <span className="fw-semibold d-block mb-2">
                              No employee leave records matching "<b>{searchTerm}</b>"
                            </span>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary fw-bold"
                              onClick={handleClearAllFilters}
                            >
                              Show All Employees
                            </button>
                          </td>
                        </tr>
                      ) : (
                        filteredEmployees.map((emp, index) => {
                          const isLast = index === filteredEmployees.length - 1;
                          const summary = computeEmployeeSummary(emp);
                          const colorScheme = AVATAR_COLORS[index % AVATAR_COLORS.length];
                          const isSelected = selectedEmployeeIds.includes(emp.id);

                          return (
                            <tr
                              key={emp.id || index}
                              style={{
                                borderBottom: isLast ? "none" : "1px solid #f1f5f9",
                                backgroundColor: isSelected ? "#f0fdf4" : "transparent",
                                transition: "background-color 0.15s ease",
                              }}
                              className="hover-bg-light"
                            >
                              {/* Checkbox Column */}
                              <td className="ps-4 py-4 text-center">
                                <input
                                  type="checkbox"
                                  className="form-check-input cursor-pointer"
                                  style={{
                                    width: "18px",
                                    height: "18px",
                                    borderRadius: "4px",
                                    border: isSelected ? "1.5px solid #16a34a" : "1.5px solid #cbd5e1",
                                    backgroundColor: isSelected ? "#16a34a" : "#fff",
                                  }}
                                  checked={isSelected}
                                  onChange={() => handleToggleSelectEmployee(emp.id)}
                                  title={`Select ${emp.name}`}
                                />
                              </td>

                              {/* 1. Employee Info (Avatar, Name, Code, Dept) */}
                              <td className="ps-3 py-4">
                                <div className="d-flex align-items-center gap-3">
                                  <div
                                    style={{
                                      width: "40px",
                                      height: "40px",
                                      borderRadius: "10px",
                                      background: colorScheme.bg,
                                      color: colorScheme.text,
                                      display: "flex",
                                      alignItems: "center",
                                      justifyContent: "center",
                                      fontWeight: "700",
                                      fontSize: "0.85rem",
                                      flexShrink: 0,
                                    }}
                                  >
                                    {getAvatarInitials(emp.name)}
                                  </div>
                                  <div>
                                    <div className="d-flex align-items-center gap-2">
                                      <span
                                        className="text-gray-900 fw-bold d-block"
                                        style={{ fontSize: "0.925rem" }}
                                      >
                                        {emp.name}
                                      </span>
                                      {isSelected && (
                                        <span
                                          className="badge"
                                          style={{
                                            backgroundColor: "#dcfce7",
                                            color: "#15803d",
                                            fontSize: "10px",
                                            fontWeight: "700",
                                            padding: "2px 6px",
                                            borderRadius: "4px",
                                          }}
                                        >
                                          ✓ Selected
                                        </span>
                                      )}
                                    </div>
                                    <div className="d-flex align-items-center gap-2 mt-1">
                                      <span
                                        className="badge bg-light text-gray-700 border border-gray-200"
                                        style={{ fontSize: "11px", fontWeight: "600" }}
                                      >
                                        {emp.code}
                                      </span>
                                      <span className="text-gray-500 fs-8">
                                        {emp.department} • {emp.designation || "Associate"}
                                      </span>
                                    </div>
                                  </div>
                                </div>
                              </td>

                              {/* 2. Allocated (Annual Days) */}
                              <td className="text-center py-4">
                                <span
                                  className="text-gray-800 fw-semibold"
                                  style={{ fontSize: "0.95rem" }}
                                >
                                  {summary.totalAllocated}
                                </span>
                              </td>

                              {/* 3. Consumed (YTD) */}
                              <td className="text-center py-4">
                                <span
                                  className="text-gray-800 fw-semibold"
                                  style={{ fontSize: "0.95rem" }}
                                >
                                  {summary.totalConsumed}
                                </span>
                              </td>

                              {/* 4. Remaining Balance */}
                              <td className="text-center py-4">
                                <span
                                  style={{
                                    color: Number(summary.totalBalance) < 0 ? "#dc2626" : "#16a34a",
                                    fontWeight: "700",
                                    fontSize: "0.975rem",
                                  }}
                                >
                                  {summary.totalBalance}
                                </span>
                              </td>

                              {/* 5. LOP (YTD) */}
                              <td className="text-center py-4">
                                <span
                                  style={{
                                    color: summary.totalLop > 0 ? "#dc2626" : "#4b5563",
                                    fontWeight: summary.totalLop > 0 ? "700" : "500",
                                    fontSize: "0.95rem",
                                  }}
                                >
                                  {summary.totalLop}
                                </span>
                              </td>

                              {/* 6. Action: Review Eye Button */}
                              <td className="text-center pe-6 py-4">
                                <button
                                  id={`review-btn-${emp.id}`}
                                  type="button"
                                  onClick={() => handleOpenReviewModal(emp)}
                                  style={{
                                    padding: "6px 14px",
                                    background: "#059669",
                                    border: "none",
                                    borderRadius: "6px",
                                    color: "#fff",
                                    fontWeight: 600,
                                    fontSize: "0.82rem",
                                    cursor: "pointer",
                                    display: "inline-flex",
                                    alignItems: "center",
                                    gap: "6px",
                                    transition: "all 0.15s ease",
                                    boxShadow: "0 1px 2px rgba(5, 150, 105, 0.2)",
                                  }}
                                  onMouseEnter={(e) => {
                                    e.currentTarget.style.background = "#047857";
                                    e.currentTarget.style.transform = "translateY(-1px)";
                                  }}
                                  onMouseLeave={(e) => {
                                    e.currentTarget.style.background = "#059669";
                                    e.currentTarget.style.transform = "translateY(0)";
                                  }}
                                >
                                  <PiEyeDuotone size={16} /> Review
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {/* Card Footer */}
            <div
              className="px-6 py-4 bg-light-subtle rounded-bottom-4 d-flex align-items-center justify-content-between flex-wrap gap-2"
              style={{ borderTop: "1px solid #f1f5f9" }}
            >
              <div className="d-flex align-items-center gap-2 text-gray-600 fs-7">
                <PiInfoDuotone size={18} className="text-primary" />
                <span>
                  Select employees using the checkboxes and click "Mark Leave" to modify leave entries.
                </span>
              </div>
              <span className="text-gray-500 fs-8">
                Showing {filteredEmployees.length} of {employees.length} Employee Records
              </span>
            </div>
          </div>

        </div>
      </div>

      {/* ── Review & Leave Details Modal ─────────────────────────────────────────── */}
      <Modal
        open={isModalOpen}
        onCancel={handleCloseReviewModal}
        footer={null}
        width={720}
        centered
        destroyOnClose
        title={
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span
              style={{
                background: "linear-gradient(135deg, #ecfdf5, #d1fae5)",
                borderRadius: "8px",
                padding: "6px 8px",
                display: "inline-flex",
              }}
            >
              <PiEyeDuotone size={18} style={{ color: "#059669" }} />
            </span>
            <div>
              <span
                style={{
                  fontWeight: 700,
                  fontSize: "1.05rem",
                  color: "#111827",
                  display: "block",
                }}
              >
                Employee Leave Details & Breakdown
              </span>
              <span style={{ fontSize: "12px", color: "#6b7280", fontWeight: "400" }}>
                Annual quota breakdown and consumption history
              </span>
            </div>
          </div>
        }
      >
        {selectedEmployeeForModal && (
          <div>
            {/* Employee Profile Header inside Modal */}
            <div
              style={{
                background: "linear-gradient(135deg, #f8fafc, #f1f5f9)",
                borderRadius: "12px",
                border: "1px solid #e2e8f0",
                padding: "16px 20px",
                marginBottom: "20px",
                marginTop: "12px",
              }}
            >
              <div className="d-flex align-items-center justify-content-between flex-wrap gap-3">
                <div className="d-flex align-items-center gap-3">
                  <div
                    style={{
                      width: "48px",
                      height: "48px",
                      borderRadius: "12px",
                      background: "linear-gradient(135deg, #0284c7, #0369a1)",
                      color: "#ffffff",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontWeight: "700",
                      fontSize: "1.1rem",
                    }}
                  >
                    {getAvatarInitials(selectedEmployeeForModal.name)}
                  </div>
                  <div>
                    <h4 className="fw-bold text-gray-900 mb-1 fs-5">
                      {selectedEmployeeForModal.name}
                    </h4>
                    <div className="d-flex align-items-center gap-2 flex-wrap">
                      <span className="badge bg-white text-dark border border-gray-300 fs-8 fw-bold">
                        {selectedEmployeeForModal.code}
                      </span>
                      <span className="text-gray-600 fs-7">
                        <PiBuildingsDuotone className="me-1 text-primary" />
                        {selectedEmployeeForModal.department}
                      </span>
                      <span className="text-gray-400">•</span>
                      <span className="text-gray-600 fs-7">
                        <PiIdentificationBadgeDuotone className="me-1 text-primary" />
                        {selectedEmployeeForModal.designation || "Associate"}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Quick Status Pill */}
                <div>
                  {(() => {
                    const s = computeEmployeeSummary(selectedEmployeeForModal);
                    return getStatusBadge(s.overallStatus, s.statusType);
                  })()}
                </div>
              </div>

              {/* 4 Mini Stat Boxes inside Modal */}
              {(() => {
                const s = computeEmployeeSummary(selectedEmployeeForModal);
                return (
                  <div className="row g-2 mt-3 pt-3 border-top border-gray-200">
                    <div className="col-6 col-sm-3">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-gray-500 fs-8 fw-semibold d-block">Allocated</span>
                        <span className="fs-5 fw-bold text-gray-900">{s.totalAllocated} days</span>
                      </div>
                    </div>
                    <div className="col-6 col-sm-3">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-gray-500 fs-8 fw-semibold d-block">Consumed</span>
                        <span className="fs-5 fw-bold text-gray-900">{s.totalConsumed} days</span>
                      </div>
                    </div>
                    <div className="col-6 col-sm-3">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-gray-500 fs-8 fw-semibold d-block">Remaining</span>
                        <span className={`fs-5 fw-bold ${s.totalBalance < 0 ? "text-danger" : "text-success"}`}>
                          {s.totalBalance} days
                        </span>
                      </div>
                    </div>
                    <div className="col-6 col-sm-3">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-gray-500 fs-8 fw-semibold d-block">Loss of Pay</span>
                        <span className={`fs-5 fw-bold ${s.totalLop > 0 ? "text-danger" : "text-gray-700"}`}>
                          {s.totalLop} days
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })()}
            </div>

            {/* Expired Leaves Alert Banner in Modal */}
            {(() => {
              const expiredLeaves = (selectedEmployeeForModal.balances || []).filter((item) =>
                isLeaveExpired(item.expirationDate)
              );
              const isAllExpired =
                selectedEmployeeForModal.balances?.length > 0 &&
                expiredLeaves.length === selectedEmployeeForModal.balances.length;

              if (expiredLeaves.length === 0) return null;

              return (
                <div
                  style={{
                    backgroundColor: "#fef2f2",
                    border: "1px solid #fca5a5",
                    borderRadius: "10px",
                    padding: "12px 16px",
                    marginBottom: "16px",
                    display: "flex",
                    alignItems: "flex-start",
                    gap: "10px",
                  }}
                >
                  <PiInfoDuotone size={20} style={{ color: "#dc2626", flexShrink: 0, marginTop: "2px" }} />
                  <div className="fs-7 text-danger">
                    {isAllExpired ? (
                      <span>
                        <strong>All leaves have expired:</strong> All allocated leaves for <b>{selectedEmployeeForModal.name}</b> have expired and cannot be applied.
                      </span>
                    ) : (
                      <span>
                        <strong>Expired Leave Notice:</strong>{" "}
                        {expiredLeaves.map((l) => `${l.leaveType} (expired on ${l.expirationDate})`).join(", ")} — these expired leaves cannot be applied.
                      </span>
                    )}
                  </div>
                </div>
              );
            })()}

            {/* Sub-Table of Leave Types */}
            <h5 className="fw-bold text-gray-900 mb-3 fs-6">
              Leave Types Quota Breakdown
            </h5>
            <div
              style={{
                border: "1px solid #e5e7eb",
                borderRadius: "10px",
                overflow: "hidden",
                marginBottom: "20px",
              }}
            >
              <table className="table table-row-bordered align-middle gs-0 gy-3 mb-0">
                <thead style={{ backgroundColor: "#f8fafc" }}>
                  <tr className="border-bottom text-gray-700 fw-bold fs-7">
                    <th className="ps-4 py-3" style={{ width: "26%" }}>
                      Leave Type
                    </th>
                    <th className="text-center py-3" style={{ width: "15%" }}>
                      Allocated
                    </th>
                    <th className="text-center py-3" style={{ width: "15%" }}>
                      Consumed
                    </th>
                    <th className="text-center py-3" style={{ width: "14%" }}>
                      Balance
                    </th>
                    <th className="text-center py-3" style={{ width: "16%" }}>
                      Expire Date
                    </th>
                    <th className="text-center pe-4 py-3" style={{ width: "14%" }}>
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {(selectedEmployeeForModal.balances || []).map((item, bIdx) => {
                    const expired = isLeaveExpired(item.expirationDate);
                    const itemStatus = expired ? "Expired" : item.status;
                    const itemStatusType = expired ? "expired" : item.statusType;

                    return (
                      <tr key={item.id || bIdx} className="border-bottom">
                        <td className="ps-4 py-3">
                          <span className="text-gray-900 fw-bold fs-7 d-block">
                            {item.leaveType}
                          </span>
                          {expired ? (
                            <span className="text-danger fs-8 fw-bold d-block">
                              Expired on {item.expirationDate}
                            </span>
                          ) : (
                            (item.lwp > 0 || item.lop > 0) && (
                              <span className="text-danger fs-8 fw-semibold">
                                (Incurred {item.lwp || item.lop} day{(item.lwp || item.lop) > 1 ? "s" : ""} LWP)
                              </span>
                            )
                          )}
                        </td>
                        <td className="text-center py-3 text-gray-800 fw-semibold fs-7">
                          {item.allocated} days
                        </td>
                        <td className="text-center py-3 text-gray-800 fw-semibold fs-7">
                          {item.consumed} days
                        </td>
                        <td className="text-center py-3">
                          <span
                            style={{
                              color: Number(item.balance) < 0 ? "#dc2626" : "#16a34a",
                              fontWeight: "700",
                              fontSize: "0.875rem",
                            }}
                          >
                            {item.balance}
                          </span>
                        </td>
                        <td className="text-center py-3">
                          <span
                            className={`badge ${
                              expired
                                ? "bg-light-danger text-danger border border-danger-subtle"
                                : "bg-light text-gray-700 border border-gray-200"
                            }`}
                            style={{
                              fontSize: "12px",
                              padding: "5px 10px",
                              fontWeight: "600",
                              borderRadius: "6px",
                            }}
                          >
                            {item.expirationDate || "2026-12-31"}
                          </span>
                        </td>
                        <td className="text-center pe-4 py-3">
                          {getStatusBadge(itemStatus, itemStatusType)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Note & Action Footer in Modal */}
            <div
              style={{
                backgroundColor: "#eff6ff",
                border: "1px solid #bfdbfe",
                borderRadius: "10px",
                padding: "12px 16px",
                marginBottom: "20px",
                display: "flex",
                alignItems: "flex-start",
                gap: "10px",
              }}
            >
              <PiInfoDuotone size={20} style={{ color: "#2563eb", flexShrink: 0, marginTop: "2px" }} />
              <div className="fs-7 text-gray-700">
                <span>
                  Any negative balance or LOP days recorded will automatically be deducted during the monthly PayRun calculation for <b>{selectedEmployeeForModal.name}</b>.
                </span>
              </div>
            </div>

            <div className="d-flex align-items-center justify-content-between gap-3 pt-2">
              <button
                type="button"
                className="btn btn-light fw-bold"
                style={{ borderRadius: "8px" }}
                onClick={handleCloseReviewModal}
              >
                Close
              </button>

              <button
                type="button"
                className="btn btn-primary d-flex align-items-center gap-2 fw-bold"
                style={{ borderRadius: "8px" }}
                onClick={() => {
                  const expiredLeaves = (selectedEmployeeForModal.balances || []).filter((item) =>
                    isLeaveExpired(item.expirationDate)
                  );
                  const isAllExpired =
                    selectedEmployeeForModal.balances?.length > 0 &&
                    expiredLeaves.length === selectedEmployeeForModal.balances.length;

                  if (isAllExpired) {
                    Swal.fire({
                      icon: "warning",
                      title: "Leaves Expired",
                      html: `All leave allocations for <b>${selectedEmployeeForModal.name}</b> have expired and cannot be applied.`,
                      confirmButtonColor: "#0284c7",
                      confirmButtonText: "Got it",
                    });
                    return;
                  }

                  handleCloseReviewModal();
                  navigate("/markleaveaddemploy", {
                    state: {
                      selectedEmployeeIds: [selectedEmployeeForModal.id],
                      selectedYear: selectedYear,
                    },
                  });
                }}
              >
                <span>Mark Leaves for this Employee</span>
                <PiArrowSquareOutDuotone size={16} />
              </button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}
