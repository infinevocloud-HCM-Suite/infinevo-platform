import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useLocation } from "react-router-dom";
import { Modal, DatePicker } from "antd";
import dayjs from "dayjs";
import {
  PiUserCircleDuotone,
  PiPlusBold,
  PiMagnifyingGlassDuotone,
  PiCalendarBlankDuotone,
  PiEyeDuotone,
  PiPencilSimpleBold,
  PiTrashSimpleBold,
  PiXBold,
  PiLockSimpleBold,
  PiCheckCircleBold,
} from "react-icons/pi";
import {
  fetchLeaveAllocationsFromApi,
  deleteEmployeeLeaveEntryApi,
  deleteEmployeeMonthConsumptionApi,
  getStoredLeaveEmployees,
} from "../../../shared/services/leaveStore";
import Swal from "sweetalert2";

// Gradient avatar palette
const AVATAR_COLORS = [
  { bg: "linear-gradient(135deg, #e0f2fe, #bae6fd)", text: "#0369a1" },
  { bg: "linear-gradient(135deg, #fef3c7, #fde68a)", text: "#b45309" },
  { bg: "linear-gradient(135deg, #dcfce7, #bbf7d0)", text: "#15803d" },
  { bg: "linear-gradient(135deg, #f3e8ff, #e9d5ff)", text: "#7e22ce" },
  { bg: "linear-gradient(135deg, #ffe4e6, #fecdd3)", text: "#be123c" },
  { bg: "linear-gradient(135deg, #e0e7ff, #c7d2fe)", text: "#4338ca" },
];

const getAvatarInitials = (name) => {
  if (!name) return "EMP";
  const parts = name.trim().split(" ");
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
};

export const MONTH_NAMES = [
  "All Months",
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

export const YEAR_OPTIONS = [
  "2024",
  "2025",
  "2026",
  "2027",
  "2028",
];

export default function MarkLeavesOverviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const currentYearStr = String(new Date().getFullYear());
  const [selectedYear, setSelectedYear] = useState(
    () => location.state?.selectedYear || currentYearStr
  );
  const [selectedMonthName, setSelectedMonthName] = useState("All Months");

  useEffect(() => {
    if (location.state?.selectedYear && location.state.selectedYear !== selectedYear) {
      setSelectedYear(location.state.selectedYear);
    }
  }, [location.state?.selectedYear]);

  const [searchTerm, setSearchTerm] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedEmployeeForModal, setSelectedEmployeeForModal] = useState(null);
  const [loading, setLoading] = useState(false);
  const [employees, setEmployees] = useState(() =>
    getStoredLeaveEmployees(location.state?.selectedYear || currentYearStr)
  );

  // Fetch real live employees from Backend API for the selected year
  const loadEmployees = useCallback(async (year = selectedYear) => {
    setLoading(true);
    try {
      const data = await fetchLeaveAllocationsFromApi(year);
      if (Array.isArray(data)) {
        setEmployees(data);
      } else {
        setEmployees(getStoredLeaveEmployees(year));
      }
    } catch (err) {
      console.warn("Could not fetch leave allocations, using stored data:", err);
      setEmployees(getStoredLeaveEmployees(year));
    } finally {
      setLoading(false);
    }
  }, [selectedYear]);

  useEffect(() => {
    loadEmployees(selectedYear);

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

  // Generate Month-Wise Rows: Each employee has a row for each month they consumed leaves, incurred LOP, or incurred LWP
  const displayRows = useMemo(() => {
    const rows = [];

    employees.forEach((emp) => {
      const balances = emp.balances || [];
      const totalAllocated = balances.reduce((a, c) => a + (Number(c.allocated) || 0), 0);
      const totalConsumedYTD = balances.reduce((a, c) => a + (Number(c.consumed) || 0), 0);
      const totalLwpYTD = balances.reduce((a, c) => a + (Number(c.lwp) || 0), 0);
      const totalLopYTD = balances.reduce((a, c) => a + (Number(c.lop) || 0), 0);

      // Only show employees whose Consumed (YTD), LWP (YTD), or LOP (YTD) is greater than 0
      if (totalConsumedYTD <= 0 && totalLwpYTD <= 0 && totalLopYTD <= 0) {
        return;
      }

      // 1. Gather all individual leave entries across all leave types and months for this employee for selectedYear
      const allEmployeeEntries = [];

      balances.forEach((b) => {
        if (b.monthlyEntries && typeof b.monthlyEntries === "object") {
          Object.entries(b.monthlyEntries).forEach(([mKey, entryList]) => {
            const parts = mKey.split(" ");
            const yOnly = parts[1] || selectedYear;
            if (yOnly !== selectedYear) return;

            if (Array.isArray(entryList)) {
              entryList.forEach((entry) => {
                const days = Number(entry.daysTaken) || 0;
                const lwp = Number(entry.lwp) || 0;
                const lop = Number(entry.lopDays) || 0;
                if (days > 0 || lwp > 0 || lop > 0) {
                  allEmployeeEntries.push({
                    ...entry,
                    daysTaken: days,
                    lwp: lwp,
                    lopDays: lop,
                    monthKey: mKey,
                    monthNameOnly: parts[0],
                    yearOnly: yOnly,
                    leaveType: entry.leaveType || b.leaveType || "Leave",
                    rawAllocated: Number(b.allocated) || 0,
                  });
                }
              });
            }
          });
        }
      });

      // If no entries array was present, fallback to monthlyBreakdown / monthlyLopBreakdown / monthlyLwpBreakdown / leaveMonth
      if (allEmployeeEntries.length === 0) {
        balances.forEach((b) => {
          const monthKeys = new Set([
            ...Object.keys(b.monthlyBreakdown || {}),
            ...Object.keys(b.monthlyLopBreakdown || {}),
            ...Object.keys(b.monthlyLwpBreakdown || {}),
          ]);
          if (b.leaveMonth) monthKeys.add(b.leaveMonth);

          monthKeys.forEach((mKey) => {
            const parts = mKey.split(" ");
            const yOnly = parts[1] || selectedYear;
            if (yOnly !== selectedYear) return;
            const d = (b.monthlyBreakdown && Number(b.monthlyBreakdown[mKey])) || 0;
            const lop = (b.monthlyLopBreakdown && Number(b.monthlyLopBreakdown[mKey])) || 0;
            const lwp = (b.monthlyLwpBreakdown && Number(b.monthlyLwpBreakdown[mKey])) || 0;
            if (d > 0 || lop > 0 || lwp > 0) {
              allEmployeeEntries.push({
                entryId: `legacy_${mKey.replace(/\s+/g, "_")}_${b.leaveType ? b.leaveType.replace(/\s+/g, "_") : "leave"}`,
                daysTaken: d,
                lopDays: lop,
                lwp: lwp,
                monthKey: mKey,
                monthNameOnly: parts[0],
                yearOnly: yOnly,
                leaveType: b.leaveType || "Leave",
                rawAllocated: Number(b.allocated) || 0,
                markedAt: null,
              });
            }
          });
        });
      }

      // Sort entries chronologically (oldest first) to compute running totals accurately
      allEmployeeEntries.sort((a, b) => {
        const timeA = a.markedAt ? new Date(a.markedAt).getTime() : 0;
        const timeB = b.markedAt ? new Date(b.markedAt).getTime() : 0;
        return timeA - timeB;
      });

      // Calculate the grand total leaves taken by the employee across all leaves in the year
      const grandTotalConsumedYTD = allEmployeeEntries.length > 0
        ? allEmployeeEntries.reduce((sum, e) => sum + (Number(e.daysTaken) || 0), 0)
        : totalConsumedYTD;

      // Sequentially accumulate YTD per leave type and compute remaining balance as it decreases from that leave type's allocated quota
      const leaveTypeRunningYtd = {};
      const computedEmpRows = allEmployeeEntries.map((entry, eIdx) => {
        const ltKey = (entry.leaveType || "Leave").toLowerCase();
        const entryDays = Number(entry.daysTaken) || 0;
        const entryLop = Number(entry.lopDays) || 0;
        const entryLwp = Number(entry.lwp) || 0;

        const currentLtYtd = (leaveTypeRunningYtd[ltKey] || 0) + entryDays;
        leaveTypeRunningYtd[ltKey] = currentLtYtd;

        const ltAllocated = Number(entry.rawAllocated) || 0;
        const entryBalanceAfter = Math.max(0, ltAllocated - currentLtYtd);

        const rawStatus = (entry.status || "ACTIVE").toUpperCase();
        let status = "ACTIVE";
        let statusType = "primary";
        if (rawStatus === "PROCESSED") {
          status = "PROCESSED";
          statusType = "success";
        } else if (rawStatus === "INPAYRUN") {
          status = "INPAYRUN";
          statusType = "warning";
        } else {
          status = "ACTIVE";
          statusType = "primary";
        }

        return {
          rowKey: `${emp.id}-${entry.monthKey}-${entry.entryId || entry.leaveId || eIdx}`,
          id: emp.id,
          entryId: entry.entryId || entry.leaveId || null,
          employeeNumber: emp.code || emp.id,
          employeeName: emp.name || "Employee",
          department: emp.department || "General",
          designation: emp.designation || "Associate",
          month: entry.monthKey,
          monthNameOnly: entry.monthNameOnly,
          yearOnly: entry.yearOnly,
          totalAllocated,
          remainingBalance: entryBalanceAfter,
          consumedThisMonth: entryDays,
          totalConsumedYTD: grandTotalConsumedYTD,
          lopThisMonth: entryLop,
          lwpThisMonth: entryLwp,
          fromDate: entry.fromDate || null,
          toDate: entry.toDate || null,
          reason: entry.reason || null,
          leaveType: entry.leaveType || "Leave",
          status,
          statusType,
          isSpecificEntry: true,
          leaveBreakdown: balances.map((b) => ({
            type: b.leaveType,
            allocated: Number(b.allocated) || 0,
            consumedMonth: b.leaveType === entry.leaveType ? entryDays : ((b.monthlyBreakdown && Number(b.monthlyBreakdown[entry.monthKey])) || 0),
            consumedYTD: Number(b.consumed) || entryDays,
            remaining:
              b.leaveType === entry.leaveType
                ? entryBalanceAfter
                : (Number(b.balance) !== undefined
                  ? Number(b.balance)
                  : Math.max(0, (Number(b.allocated) || 0) - (Number(b.consumed) || 0))),
            lop: b.leaveType === entry.leaveType ? entryLop : ((b.monthlyLopBreakdown && Number(b.monthlyLopBreakdown[entry.monthKey])) || 0),
            lwp: b.leaveType === entry.leaveType ? entryLwp : ((b.monthlyLwpBreakdown && Number(b.monthlyLwpBreakdown[entry.monthKey])) || 0),
          })),
        };
      });

      // Render with the newest entry on top (decreasing balance order: 27 -> 30 -> 33)
      computedEmpRows.reverse().forEach((r) => rows.push(r));
    });

    return rows;
  }, [employees, selectedYear]);

  // Filtered rows list
  const filteredRows = useMemo(() => {
    return displayRows.filter((row) => {
      // Month selector filter from toolbar
      const matchesMonth =
        selectedMonthName === "All Months" ||
        row.monthNameOnly.toLowerCase() === selectedMonthName.toLowerCase();

      const matchesSearch =
        searchTerm.trim() === "" ||
        row.employeeName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        row.employeeNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
        row.department.toLowerCase().includes(searchTerm.toLowerCase()) ||
        row.leaveType.toLowerCase().includes(searchTerm.toLowerCase()) ||
        row.month.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesDept =
        departmentFilter === "ALL" ||
        row.department.toLowerCase() === departmentFilter.toLowerCase();

      const matchesStatus =
        statusFilter === "ALL" || row.status.toUpperCase() === statusFilter.toUpperCase();

      return matchesMonth && matchesSearch && matchesDept && matchesStatus;
    });
  }, [displayRows, selectedMonthName, searchTerm, departmentFilter, statusFilter]);

  // Unique departments for filter
  const departments = useMemo(() => {
    const set = new Set(displayRows.map((e) => e.department));
    return Array.from(set).filter(Boolean);
  }, [displayRows]);

  const handleDeleteClick = (row) => {
    if (row.status === "PROCESSED") {
      Swal.fire({
        icon: "warning",
        title: "Action Restricted",
        text: "This leave record has already been PROCESSED in payroll and cannot be deleted.",
        confirmButtonColor: "#0284c7",
      });
      return;
    }
    const entryLabel = row.reason ? ` ("${row.reason}")` : (row.leaveType ? ` (${row.leaveType})` : "");
    const titleText = row.entryId ? "Delete Leave Entry?" : "Delete Leave Record?";
    const bodyHtml = row.entryId
      ? `Are you sure you want to delete leave entry of <b>${row.consumedThisMonth} Days</b>${entryLabel} for <b>${row.employeeName}</b> in <b>${row.month}</b>?<br/><span class="text-muted fs-8">This will remove this specific entry and restore ${row.consumedThisMonth} day(s) to their balance.</span>`
      : `Are you sure you want to delete leave consumption for <b>${row.employeeName}</b> for <b>${row.month}</b>?<br/><span class="text-muted fs-8">This will reset consumed days and recalculate balances for this month.</span>`;

    Swal.fire({
      title: titleText,
      html: bodyHtml,
      icon: "warning",
      showCancelButton: true,
      confirmButtonColor: "#ef4444",
      cancelButtonColor: "#64748b",
      confirmButtonText: "Yes, delete it",
      cancelButtonText: "Cancel",
    }).then(async (result) => {
      if (result.isConfirmed) {
        try {
          if (row.entryId) {
            await deleteEmployeeLeaveEntryApi(row.id, row.yearOnly, row.entryId);
          } else {
            await deleteEmployeeMonthConsumptionApi(row.id, row.yearOnly, row.month);
          }
          Swal.fire({
            icon: "success",
            title: "Deleted!",
            text: `Leave entry for ${row.employeeName} deleted successfully.`,
            timer: 2000,
            showConfirmButton: false,
          });
          loadEmployees(selectedYear);
        } catch (err) {
          Swal.fire({
            icon: "error",
            title: "Delete Failed",
            text: err?.response?.data?.message || err?.message || "Failed to delete leave record.",
          });
        }
      }
    });
  };

  return (
    <>
      <Helmet>
        <title>Mark Leaves | HRMS Payroll</title>
      </Helmet>

      {/* Page Header */}
      <div id="kt_app_toolbar" className="app-toolbar py-3 py-lg-6">
        <div id="kt_app_toolbar_container" className="app-container container-fluid d-flex flex-stack">
          {/* Breadcrumb and Title */}
          <div className="page-title d-flex flex-column justify-content-center flex-wrap me-3">
            <h1 className="page-heading d-flex text-gray-900 fw-bold fs-3 flex-column justify-content-center my-0">
              Mark Leaves
            </h1>
            <ul className="breadcrumb breadcrumb-separatorless fw-semibold fs-7 my-0 pt-1">
              <li className="breadcrumb-item text-muted">Leave Management</li>
              <li className="breadcrumb-item">
                <span className="bullet bg-gray-500 w-5px h-2px mx-2"></span>
              </li>
              <li className="breadcrumb-item text-muted">Overview</li>
            </ul>
          </div>

          {/* Primary Action Button: Add Leaves */}
          <div className="d-flex align-items-center gap-3">
            <button
              type="button"
              className="btn btn-primary d-flex align-items-center gap-2 fw-bold px-5 py-3 shadow-sm"
              style={{
                borderRadius: "8px",
                boxShadow: "0 4px 14px 0 rgba(2, 132, 199, 0.35)",
              }}
              onClick={() => navigate("/mark-leaves-taken", { state: { selectedYear } })}
            >
              <PiPlusBold size={16} />
              <span>Add Leaves</span>
            </button>
          </div>
        </div>
      </div>

      {/* Page Content Body */}
      <div id="kt_app_content" className="app-content flex-column-fluid">
        <div id="kt_app_content_container" className="app-container container-fluid">

          {/* Main Card */}
          <div
            className="card border-0 shadow-sm mb-7"
            style={{
              backgroundColor: "#ffffff",
              borderRadius: "16px",
              border: "1px solid #e2e8f0",
            }}
          >
            {/* Card Header & Filters */}
            <div className="card-header border-0 pt-6 px-6 pb-4">
              <div className="card-title d-flex flex-column">
                <h3 className="fw-bolder text-gray-900 fs-4 m-0">
                  Employee Leave Balances & Monthly Consumption
                </h3>
                <span className="text-gray-500 fw-semibold fs-7 mt-1">
                  Track live employee leave quota, monthly consumed leaves, and remaining balance.
                </span>
              </div>

              {/* Filters Toolbar */}
              <div className="card-toolbar d-flex flex-wrap align-items-center gap-3 mt-3 mt-md-0">
                {/* Month & Year Selectors */}
                <div className="d-flex align-items-center gap-3 bg-light px-4 py-2 rounded-3 border border-gray-200">
                  <PiCalendarBlankDuotone size={16} className="text-primary flex-shrink-0" />

                  <div className="d-flex align-items-center gap-1">
                    <span className="fs-7 fw-semibold text-gray-600">Month:</span>
                    <select
                      className="form-select form-select-sm border-0 bg-transparent fw-bold text-gray-800 py-1 ps-2 pe-7 cursor-pointer shadow-none"
                      style={{ minWidth: "135px" }}
                      value={selectedMonthName}
                      onChange={(e) => setSelectedMonthName(e.target.value)}
                    >
                      {MONTH_NAMES.map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </select>
                  </div>

                  <span className="text-gray-300">|</span>

                  <div className="d-flex align-items-center gap-1.5">
                    <span className="fs-7 fw-semibold text-gray-600">Year:</span>
                    <DatePicker
                      picker="year"
                      value={selectedYear ? dayjs(String(selectedYear), "YYYY") : dayjs()}
                      onChange={(date, dateString) => {
                        if (dateString) {
                          setSelectedYear(dateString);
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
                </div>

                {/* Search Box */}
                <div className="position-relative">
                  <PiMagnifyingGlassDuotone
                    size={18}
                    className="text-gray-400 position-absolute top-50 translate-middle-y ms-3"
                  />
                  <input
                    type="text"
                    className="form-control form-control-sm form-control-solid ps-10 fw-semibold"
                    placeholder="Search employee, ID, dept, month..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    style={{
                      backgroundColor: "#f8fafc",
                      border: "1px solid #e2e8f0",
                      borderRadius: "8px",
                      minWidth: "220px",
                    }}
                  />
                </div>

                {/* Department Filter */}
                {departments.length > 0 && (
                  <select
                    className="form-select form-select-sm border-gray-300 fw-semibold fs-7"
                    style={{ width: "auto", borderRadius: "8px" }}
                    value={departmentFilter}
                    onChange={(e) => setDepartmentFilter(e.target.value)}
                  >
                    <option value="ALL">All Departments</option>
                    {departments.map((d) => (
                      <option key={d} value={d}>
                        {d}
                      </option>
                    ))}
                  </select>
                )}

                {/* Status Filter */}
                <select
                  className="form-select form-select-sm border-gray-300 fw-semibold fs-7"
                  style={{ width: "auto", borderRadius: "8px" }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="ACTIVE">ACTIVE (Upcoming Payrun)</option>
                  <option value="INPAYRUN">INPAYRUN (Locked in Payrun)</option>
                  <option value="PROCESSED">PROCESSED (Finalized)</option>
                </select>

                {/* Add Leaves Button */}
                <button
                  type="button"
                  className="btn btn-sm btn-primary d-flex align-items-center gap-2 fw-bold"
                  style={{ borderRadius: "8px" }}
                  onClick={() => navigate("/mark-leaves-taken", { state: { selectedYear } })}
                >
                  <PiPlusBold size={14} />
                  <span>Add Leaves</span>
                </button>
              </div>
            </div>

            {/* Table Body */}
            <div className="card-body p-0">
              <div className="table-responsive" style={{ overflowX: "auto" }}>
                <table
                  className="table table-row-bordered align-middle gs-0 gy-4 mb-0"
                  style={{ minWidth: "1400px", tableLayout: "fixed" }}
                >
                  <thead style={{ backgroundColor: "#f8fafc" }}>
                    <tr className="border-bottom text-gray-700 fw-bold fs-7">
                      <th className="ps-6 py-4" style={{ minWidth: "260px", width: "260px" }}>
                        Employee
                      </th>
                      <th className="ps-4 py-4 text-dark" style={{ minWidth: "160px", width: "160px" }}>
                        Month
                      </th>
                      <th className="text-center py-4 text-primary" style={{ minWidth: "170px", width: "170px" }}>
                        Consumed leaves
                      </th>
                      <th className="text-center py-4" style={{ minWidth: "160px", width: "160px" }}>
                        Leave Type
                      </th>
                      <th className="text-center py-4" style={{ minWidth: "160px", width: "160px" }}>
                        Remaining Balance
                      </th>
                      <th className="text-center py-4 text-danger" style={{ minWidth: "120px", width: "120px" }}>
                        LOP
                      </th>
                      <th className="text-center py-4 text-warning" style={{ minWidth: "120px", width: "120px" }}>
                        LWP
                      </th>
                      <th className="text-center py-4" style={{ minWidth: "130px", width: "130px" }}>
                        Status
                      </th>
                      <th className="text-center pe-6 py-4" style={{ minWidth: "220px", width: "220px" }}>
                        Action
                      </th>
                    </tr>
                  </thead>

                  <tbody>
                    {loading ? (
                      <tr>
                        <td colSpan="9" className="text-center py-10 text-gray-500">
                          <div
                            className="spinner-border text-primary mb-2"
                            role="status"
                            style={{ width: "2rem", height: "2rem" }}
                          >
                            <span className="visually-hidden">Loading...</span>
                          </div>
                          <span className="fw-semibold d-block text-gray-600">
                            Loading live leave records for {selectedYear}...
                          </span>
                        </td>
                      </tr>
                    ) : filteredRows.length === 0 ? (
                      <tr>
                        <td colSpan="9" className="text-center py-10 text-gray-500">
                          <PiUserCircleDuotone size={40} className="text-gray-400 mb-2 mx-auto d-block" />
                          <span className="fw-semibold d-block mb-2">
                            No employee leave records found for {selectedYear}
                            {selectedMonthName !== "All Months" ? ` (${selectedMonthName})` : ""}
                          </span>
                          <button
                            type="button"
                            className="btn btn-sm btn-light-primary fw-bold"
                            onClick={() => navigate("/mark-leaves-taken", { state: { selectedYear } })}
                          >
                            Mark / Add Leaves
                          </button>
                        </td>
                      </tr>
                    ) : (
                      filteredRows.map((row, index) => {
                        const avatarStyle = AVATAR_COLORS[index % AVATAR_COLORS.length];
                        const monthConsumed = row.consumedThisMonth;
                        const isProcessed = row.status === "PROCESSED";
                        const isInPayrun = row.status === "INPAYRUN";

                        return (
                          <tr
                            key={row.rowKey}
                            className={`border-bottom ${isProcessed ? "bg-light-subtle" : "hover-bg-light-subtle"}`}
                            style={{
                              backgroundColor: isProcessed ? "#f8fafc" : "#ffffff",
                              opacity: isProcessed ? 0.72 : 1,
                              filter: isProcessed ? "grayscale(10%)" : "none",
                              transition: "all 0.2s ease",
                            }}
                          >
                            {/* 1. Employee Name, Code, Dept */}
                            <td className="ps-6 py-4">
                              <div className="d-flex align-items-center gap-3">
                                <div
                                  style={{
                                    width: "40px",
                                    height: "40px",
                                    borderRadius: "10px",
                                    background: avatarStyle.bg,
                                    color: avatarStyle.text,
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    fontWeight: "700",
                                    fontSize: "0.85rem",
                                    flexShrink: 0,
                                  }}
                                >
                                  {getAvatarInitials(row.employeeName)}
                                </div>
                                <div className="d-flex flex-column">
                                  <span className="text-gray-900 fw-bold fs-6">
                                    {row.employeeName}
                                  </span>
                                  <div className="d-flex align-items-center gap-2 text-gray-500 fs-7 mt-0.5">
                                    <span className="badge bg-light text-gray-700 border border-gray-200 fs-8 px-1.5 py-0.5">
                                      {row.employeeNumber}
                                    </span>
                                    <span>•</span>
                                    <span>{row.department}</span>
                                  </div>
                                </div>
                              </div>
                            </td>

                            {/* 2. Month */}
                            <td className="ps-6 py-4">
                              <div className="d-flex align-items-center gap-2">
                                <PiCalendarBlankDuotone size={16} className="text-primary" />
                                <span className="text-gray-900 fw-bold fs-7">
                                  {row.month}
                                </span>
                              </div>
                            </td>

                            {/* 3. Consumed this month */}
                            <td className="text-center py-4">
                              <span
                                className={`fw-bolder fs-6 px-3 py-1 rounded-pill ${
                                  monthConsumed > 0
                                    ? "bg-light-warning text-warning border border-warning"
                                    : "text-gray-600 bg-light"
                                }`}
                                title={
                                  row.reason && row.reason.toLowerCase() !== "leave entry"
                                    ? `Reason: ${row.reason}`
                                    : undefined
                                }
                              >
                                {monthConsumed} {monthConsumed === 1 ? "Day" : "Days"}
                              </span>
                              {row.reason &&
                                row.reason.toLowerCase() !== "leave entry" &&
                                row.reason.trim() !== "" && (
                                  <div className="text-gray-500 fs-8 fst-italic mt-1">
                                    "{row.reason}"
                                  </div>
                                )}
                            </td>

                            {/* 4. Leave Type */}
                            <td className="text-center py-4">
                              <span
                                className="badge fw-bold fs-7 px-3 py-1.5"
                                style={{
                                  backgroundColor: "#f1f5f9",
                                  color: "#334155",
                                  border: "1px solid #cbd5e1",
                                  borderRadius: "6px",
                                  letterSpacing: "0.2px",
                                }}
                              >
                                {row.leaveType || "Leave"}
                              </span>
                            </td>

                            {/* 5. Remaining Balance */}
                            <td className="text-center py-4">
                              <span
                                className={`fw-bolder fs-6 ${
                                  row.remainingBalance === 0
                                    ? "text-danger"
                                    : row.remainingBalance <= 5
                                    ? "text-warning"
                                    : "text-success"
                                }`}
                              >
                                {row.remainingBalance} Days
                              </span>
                            </td>

                            {/* 6. LOP this month */}
                            <td className="text-center py-4">
                              <span
                                className={`fw-bolder fs-7 px-2.5 py-1 rounded-pill ${
                                  (row.lopThisMonth || 0) > 0
                                    ? "bg-light-danger text-danger border border-danger-subtle"
                                    : "text-gray-600 bg-light"
                                }`}
                              >
                                {row.lopThisMonth || 0} {row.lopThisMonth === 1 ? "Day" : "Days"}
                              </span>
                            </td>

                            {/* 7. LWP this month */}
                            <td className="text-center py-4">
                              <span
                                className={`fw-bolder fs-7 px-2.5 py-1 rounded-pill ${
                                  (row.lwpThisMonth || 0) > 0
                                    ? "bg-light-warning text-warning border border-warning-subtle"
                                    : "text-gray-600 bg-light"
                                }`}
                              >
                                {row.lwpThisMonth || 0} {row.lwpThisMonth === 1 ? "Day" : "Days"}
                              </span>
                            </td>

                            {/* 8. Status Badge */}
                            <td className="text-center py-4">
                              {isProcessed ? (
                                <span
                                  className="badge fw-bold fs-8 px-3 py-1.5 d-inline-flex align-items-center gap-1"
                                  style={{
                                    backgroundColor: "#ecfdf5",
                                    color: "#059669",
                                    border: "1px solid #a7f3d0",
                                    borderRadius: "6px",
                                    letterSpacing: "0.4px",
                                  }}
                                >
                                  <PiCheckCircleBold size={12} />
                                  PROCESSED
                                </span>
                              ) : isInPayrun ? (
                                <span
                                  className="badge fw-bold fs-8 px-3 py-1.5 d-inline-flex align-items-center gap-1"
                                  style={{
                                    backgroundColor: "#fff8e1",
                                    color: "#d97706",
                                    border: "1px solid #fcd34d",
                                    borderRadius: "6px",
                                    letterSpacing: "0.4px",
                                  }}
                                >
                                  INPAYRUN
                                </span>
                              ) : (
                                <span
                                  className="badge fw-bold fs-8 px-3 py-1.5 d-inline-flex align-items-center gap-1"
                                  style={{
                                    backgroundColor: "#e0f2fe",
                                    color: "#0284c7",
                                    border: "1px solid #bae6fd",
                                    borderRadius: "6px",
                                    letterSpacing: "0.4px",
                                  }}
                                >
                                  ACTIVE
                                </span>
                              )}
                            </td>

                            {/* 9. Action: View Details, Edit, and Delete for that Month */}
                            <td className="text-center pe-6 py-4">
                              <div className="d-flex align-items-center justify-content-center gap-1.5 flex-nowrap">
                                <button
                                  type="button"
                                  className="btn btn-icon btn-sm btn-light-primary"
                                  title="View Leave Breakdown"
                                  onClick={() => setSelectedEmployeeForModal(row)}
                                >
                                  <PiEyeDuotone size={16} />
                                </button>
                                <button
                                  type="button"
                                  className={`btn btn-sm fw-bold fs-8 px-2.5 py-1.5 d-flex align-items-center gap-1.5 ${
                                    isProcessed
                                      ? "btn-light text-muted border border-gray-200"
                                      : "btn-light-primary"
                                  }`}
                                  disabled={isProcessed}
                                  style={{
                                    cursor: isProcessed ? "not-allowed" : "pointer",
                                    opacity: isProcessed ? 0.6 : 1,
                                  }}
                                  title={
                                    isProcessed
                                      ? `Leave record is PROCESSED and locked after payroll run`
                                      : `Edit leave for ${row.employeeName} (${row.month})`
                                  }
                                  onClick={() => {
                                    if (isProcessed) return;
                                    navigate("/markleaveaddemploy", {
                                      state: {
                                        selectedEmployeeIds: [row.id],
                                        selectedMonthName: row.monthNameOnly,
                                        selectedYear: row.yearOnly || selectedYear,
                                        selectedEntryId: row.entryId,
                                        selectedLeaveType: row.leaveType,
                                        isEdit: true,
                                      },
                                    });
                                  }}
                                >
                                  {isProcessed ? <PiLockSimpleBold size={12} /> : <PiPencilSimpleBold size={12} />}
                                  <span>Edit</span>
                                </button>
                                <button
                                  type="button"
                                  className={`btn btn-sm fw-bold fs-8 px-2.5 py-1.5 d-flex align-items-center gap-1.5 ${
                                    isProcessed
                                      ? "btn-light text-muted border border-gray-200"
                                      : "btn-light-danger"
                                  }`}
                                  disabled={isProcessed}
                                  style={{
                                    cursor: isProcessed ? "not-allowed" : "pointer",
                                    opacity: isProcessed ? 0.6 : 1,
                                  }}
                                  title={
                                    isProcessed
                                      ? `Leave record is PROCESSED and cannot be deleted`
                                      : `Delete leave record for ${row.employeeName} (${row.month})`
                                  }
                                  onClick={() => {
                                    if (isProcessed) return;
                                    handleDeleteClick(row);
                                  }}
                                >
                                  {isProcessed ? <PiLockSimpleBold size={12} /> : <PiTrashSimpleBold size={12} />}
                                  <span>Delete</span>
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Card Footer Summary */}
            <div className="card-footer py-4 px-6 d-flex justify-content-between align-items-center bg-light-subtle border-top">
              <span className="text-gray-600 fs-7 fw-semibold">
                Showing {filteredRows.length} record{filteredRows.length === 1 ? "" : "s"} • <b>{selectedYear}</b>
              </span>
              <button
                type="button"
                className="btn btn-sm btn-primary fw-bold d-flex align-items-center gap-2"
                onClick={() => navigate("/mark-leaves-taken", { state: { selectedYear } })}
              >
                <PiPlusBold size={14} />
                <span>Go to Add Leaves Page</span>
              </button>
            </div>
          </div>

        </div>
      </div>

      {/* Review Eye Modal */}
      {selectedEmployeeForModal && (
        <Modal
          open={Boolean(selectedEmployeeForModal)}
          onCancel={() => setSelectedEmployeeForModal(null)}
          footer={null}
          width={680}
          centered
          className="custom-leave-review-modal"
          closeIcon={<PiXBold size={18} className="text-gray-500" />}
        >
          <div className="p-2">
            {/* Modal Header */}
            <div className="d-flex align-items-center gap-3 pb-4 mb-4 border-bottom">
              <div
                style={{
                  width: "44px",
                  height: "44px",
                  borderRadius: "10px",
                  backgroundColor: "#e0f2fe",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "#0284c7",
                }}
              >
                <PiUserCircleDuotone size={26} />
              </div>
              <div>
                <h4 className="fw-bolder text-gray-900 mb-0">
                  {selectedEmployeeForModal.employeeName}
                </h4>
                <span className="text-gray-500 fs-7">
                  Employee Code: {selectedEmployeeForModal.employeeNumber} • {selectedEmployeeForModal.department}
                </span>
              </div>
            </div>

            {/* Leave Breakdown Table */}
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h6 className="fw-bold text-gray-700 m-0">Leave Breakdown by Type</h6>
              <div className="d-flex align-items-center gap-2">
                <span className="badge badge-light-primary fw-bold fs-8">
                  Month: {selectedEmployeeForModal.month}
                </span>
                <span
                  className={`badge fw-bold fs-8 px-2.5 py-1 ${
                    selectedEmployeeForModal.status === "PROCESSED"
                      ? "bg-light-success text-success border border-success-subtle"
                      : selectedEmployeeForModal.status === "INPAYRUN"
                      ? "bg-light-warning text-warning border border-warning-subtle"
                      : "bg-light-primary text-primary border border-primary-subtle"
                  }`}
                >
                  {selectedEmployeeForModal.status}
                </span>
              </div>
            </div>
            <div className="border rounded-3 overflow-hidden mb-4">
              <table className="table table-bordered align-middle mb-0">
                <thead className="bg-light">
                  <tr className="fw-bold fs-7 text-gray-700">
                    <th className="ps-3 py-2">Leave Type</th>
                    <th className="text-center py-2">Allocated</th>
                    <th className="text-center py-2 text-warning">Leave Taken</th>
                    <th className="text-center py-2">Consumed (YTD)</th>
                    <th className="text-center py-2 text-success">Remaining</th>
                    <th className="text-center py-2 text-danger">LOP</th>
                    <th className="text-center py-2 text-warning">LWP</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedEmployeeForModal.leaveBreakdown.map((item, idx) => (
                    <tr key={idx} className="fs-7">
                      <td className="ps-3 py-2 fw-semibold text-gray-900">{item.type}</td>
                      <td className="text-center py-2">{item.allocated} days</td>
                      <td className="text-center py-2 text-warning fw-bold">{item.consumedMonth} days</td>
                      <td className="text-center py-2 fw-bold text-gray-800">{item.consumedYTD} days</td>
                      <td className="text-center py-2 text-success fw-bold">{item.remaining} days</td>
                      <td className="text-center py-2 text-danger fw-bold">{item.lop || 0} days</td>
                      <td className="text-center py-2 text-warning fw-bold">{item.lwp || 0} days</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Modal Bottom Actions */}
            <div className="d-flex justify-content-between align-items-center pt-2">
              <span className="text-gray-500 fs-8">
                Viewing data for: <b>{selectedEmployeeForModal.month}</b>
              </span>
              <button
                type="button"
                className="btn btn-sm btn-primary fw-bold d-flex align-items-center gap-1.5"
                onClick={() => {
                  const empId = selectedEmployeeForModal.id;
                  const mName = selectedEmployeeForModal.monthNameOnly;
                  const yOnly = selectedEmployeeForModal.yearOnly || selectedYear;
                  setSelectedEmployeeForModal(null);
                  navigate("/markleaveaddemploy", {
                    state: {
                      selectedEmployeeIds: [empId],
                      selectedMonthName: mName,
                      selectedYear: yOnly,
                      selectedEntryId: selectedEmployeeForModal.entryId,
                      selectedLeaveType: selectedEmployeeForModal.leaveType,
                      isEdit: true,
                    },
                  });
                }}
              >
                <PiPencilSimpleBold size={13} />
                <span>Edit Leaves</span>
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
