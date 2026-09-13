import React, { useState, useEffect, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useLocation } from "react-router-dom";
import { Modal } from "antd";
import Swal from "sweetalert2";
import {
  PiBuildingsDuotone,
  PiIdentificationBadgeDuotone,
  PiFloppyDiskBold,
  PiArrowLeftBold,
  PiCalendarBlankDuotone,
  PiCaretDownBold,
  PiCaretUpBold,
  PiUserPlusBold,
  PiMagnifyingGlassDuotone,
  PiXBold,
  PiUsersThreeDuotone,
} from "react-icons/pi";
import {
  getStoredLeaveEmployees,
  fetchLeaveAllocationsFromApi,
  saveAllMarkedLeavesViaPutApi,
  isLeaveExpired,
} from "../../../shared/services/leaveStore";

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

const DEFAULT_EMPTY_LEAVE_TYPES = [
  "Sick Leave",
  "Earned Leave",
  "Casual Leave",
];

export const MONTH_NAMES = [
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

// Helper to accurately compute the starting balance, balance after, and LOP for a specific month
export const computeMonthBalanceState = (b, cardMonth, newDaysTaken = 0, isEdit = false) => {
  const allocated = Number(b.allocated) || 0;

  // Total consumed in all OTHER months
  const allOtherMonthsConsumed = Object.entries(b.monthlyBreakdown || {}).reduce((acc, [m, d]) => {
    if (m !== cardMonth) return acc + (Number(d) || 0);
    return acc;
  }, 0);

  // Existing days already recorded in THIS month
  let existingDaysInThisMonth = 0;
  if (b.monthlyBreakdown && b.monthlyBreakdown[cardMonth] !== undefined) {
    existingDaysInThisMonth = Number(b.monthlyBreakdown[cardMonth]) || 0;
  } else if (b.leaveMonth === cardMonth) {
    existingDaysInThisMonth = Number(b.consumed) || 0;
  }

  // Available positive annual quota entering this month after considering leaves taken in all other months
  const availableQuotaInMonth = Math.max(0, allocated - allOtherMonthsConsumed);

  const additionalDays = Number(newDaysTaken) || 0;
  let balanceAfter = 0;

  if (isEdit) {
    // In edit mode, newDaysTaken replaces this month's consumed days
    balanceAfter = availableQuotaInMonth - additionalDays;
  } else {
    // In add mode:
    const totalMonthDays = existingDaysInThisMonth + additionalDays;
    balanceAfter = availableQuotaInMonth - totalMonthDays;
  }

  const lop = balanceAfter < 0 ? Math.abs(balanceAfter) : 0;

  return {
    allocated,
    allOtherMonthsConsumed,
    availableQuotaInMonth,
    existingDaysInThisMonth,
    balanceAfter,
    lop,
  };
};

// Helper to build a complete card object for a single employee with their own specific month
const buildCardForEmployee = (emp, initialMonthName, year, isEdit = false, entryId = null, selectedLeaveType = null) => {
  const cardMonthName = initialMonthName || "September";
  const cardMonth = `${cardMonthName} ${year}`;

  let balances =
    Array.isArray(emp.balances) && emp.balances.length > 0
      ? emp.balances
      : DEFAULT_EMPTY_LEAVE_TYPES.map((lt, idx) => ({
          id: `leave-${idx + 1}`,
          leaveType: lt,
          allocated: 0,
          consumed: 0,
          balance: 0,
        }));

  // If in edit mode, filter to ONLY the selected leave type (e.g. Earned Leave) so other leave types are NOT visible
  if (isEdit) {
    if (selectedLeaveType) {
      const filtered = balances.filter(
        (b) => b.leaveType?.toLowerCase() === selectedLeaveType.toLowerCase()
      );
      if (filtered.length > 0) {
        balances = filtered;
      }
    } else if (entryId) {
      const filtered = balances.filter((b) => {
        const targetEntry = Array.isArray(b.monthlyEntries?.[cardMonth])
          ? b.monthlyEntries[cardMonth].find((e) => e.entryId === entryId || e.leaveId === entryId)
          : null;
        return Boolean(targetEntry);
      });
      if (filtered.length > 0) {
        balances = filtered;
      }
    }
  }

  return {
    id: `card-emp-${emp.id}`,
    employeeId: emp.id,
    name: emp.name,
    code: emp.code,
    department: emp.department || "General",
    designation: emp.designation || "Associate",
    selectedMonthName: cardMonthName,
    selectedMonth: cardMonth,
    isExpanded: true,
    entries: balances.map((b, bIdx) => {
      const allocated = Number(b.allocated) || 0;
      const currentConsumed = Number(b.consumed) || 0;

      const targetEntry = Array.isArray(b.monthlyEntries?.[cardMonth])
        ? b.monthlyEntries[cardMonth].find((e) => e.entryId === entryId)
        : null;

      const monthDays = targetEntry
        ? (Number(targetEntry.daysTaken) || 0)
        : (b.monthlyBreakdown && b.monthlyBreakdown[cardMonth] !== undefined)
        ? Number(b.monthlyBreakdown[cardMonth]) || 0
        : (b.leaveMonth === cardMonth ? Number(b.consumed) || 0 : 0);

      const initialDaysTaken = isEdit ? monthDays : 0;
      const monthState = computeMonthBalanceState(b, cardMonth, initialDaysTaken, isEdit);

      const initialLop = targetEntry?.lopDays !== undefined
        ? Number(targetEntry.lopDays)
        : monthState.lop;
      const initialLwp = targetEntry?.lwp !== undefined
        ? Number(targetEntry.lwp)
        : (b.monthlyLwpBreakdown && b.monthlyLwpBreakdown[cardMonth] !== undefined
            ? Number(b.monthlyLwpBreakdown[cardMonth]) || 0
            : (isEdit && b.lwp !== undefined ? Number(b.lwp) : 0));

      const isExpired = isLeaveExpired(b.expirationDate);

      return {
        id: b.id,
        targetEntryId: targetEntry ? targetEntry.entryId : null,
        rowId: `row-${emp.id}-${b.id || bIdx + 1}`,
        leaveType: b.leaveType,
        allocated: allocated,
        existingConsumed: currentConsumed,
        currentBalance: monthState.availableQuotaInMonth,
        otherMonthsConsumed: monthState.otherMonthsConsumed,
        existingDaysInThisMonth: monthState.existingDaysInThisMonth,
        daysTaken: initialDaysTaken,
        balanceAfter: monthState.balanceAfter,
        fromDate: targetEntry?.fromDate || "",
        toDate: targetEntry?.toDate || "",
        reason: targetEntry?.reason || "",
        lwp: initialLwp,
        lopDays: initialLop,
        monthlyBreakdown: b.monthlyBreakdown || {},
        monthlyLopBreakdown: b.monthlyLopBreakdown || {},
        monthlyLwpBreakdown: b.monthlyLwpBreakdown || {},
        monthlyEntries: b.monthlyEntries || {},
        expirationDate: b.expirationDate || "2026-12-31",
        carryForward: Boolean(b.carryForward),
        isExpired: isExpired,
      };
    }),
  };
};

export default function MarkLeaveAddEmploy() {
  const navigate = useNavigate();
  const location = useLocation();
  const currentYearStr = String(new Date().getFullYear());
  
  const isEditMode = Boolean(location.state?.isEdit);
  // Main Year locked from the previous page
  const selectedYear = location.state?.selectedYear || currentYearStr;
  const initialMonthName = location.state?.selectedMonthName || "September";

  // Read selected employee IDs, entryId, and specific leaveType passed from Overview or Mark Leaves Taken Page
  const selectedEmployeeIds = location.state?.selectedEmployeeIds || [];
  const selectedEntryId = location.state?.selectedEntryId || null;
  const selectedLeaveType = location.state?.selectedLeaveType || null;

  // Full employees list for Add Employee modal
  const [allEmployees, setAllEmployees] = useState(() => getStoredLeaveEmployees(selectedYear));
  const [isAddEmployeeModalOpen, setIsAddEmployeeModalOpen] = useState(false);
  const [modalSearchTerm, setModalSearchTerm] = useState("");
  const [modalSelectedIds, setModalSelectedIds] = useState([]);

  // Fetch updated employees from backend for current year
  useEffect(() => {
    fetchLeaveAllocationsFromApi(selectedYear)
      .then((data) => {
        if (data && data.length > 0) {
          setAllEmployees(data);
          setMarkLeaveCards((prevCards) => {
            if (prevCards.length === 0) {
              if (Array.isArray(selectedEmployeeIds) && selectedEmployeeIds.length > 0) {
                const targetEmployees = data.filter((emp) => selectedEmployeeIds.includes(emp.id));
                return targetEmployees.map((emp) =>
                  buildCardForEmployee(emp, initialMonthName, selectedYear, isEditMode, selectedEntryId, selectedLeaveType)
                );
              }
              return [];
            }
            return prevCards.map((card) => {
              const freshEmp = data.find((emp) => emp.id === card.employeeId || emp.code === card.code);
              if (!freshEmp) return card;
              return buildCardForEmployee(freshEmp, card.selectedMonthName, selectedYear, isEditMode, selectedEntryId, selectedLeaveType);
            });
          });
        }
      })
      .catch((err) => console.warn("Failed to fetch employees:", err));
  }, [selectedYear]);

  // Load cards for initially selected employees (each with their own month)
  const [markLeaveCards, setMarkLeaveCards] = useState(() => {
    const stored = getStoredLeaveEmployees(selectedYear);
    let targetEmployees = stored;

    if (Array.isArray(selectedEmployeeIds) && selectedEmployeeIds.length > 0) {
      targetEmployees = stored.filter((emp) => selectedEmployeeIds.includes(emp.id));
    }

    if (!Array.isArray(targetEmployees) || targetEmployees.length === 0) {
      return [];
    }

    return targetEmployees.map((emp) =>
      buildCardForEmployee(emp, initialMonthName, selectedYear, isEditMode, selectedEntryId, selectedLeaveType)
    );
  });

  // Handle individual employee card month change
  const handleCardMonthChange = (cardId, newMonthName) => {
    const newActiveMonth = `${newMonthName} ${selectedYear}`;
    const stored = allEmployees.length > 0 ? allEmployees : getStoredLeaveEmployees();

    setMarkLeaveCards((prev) =>
      prev.map((c) => {
        if (c.id !== cardId) return c;

        const emp = stored.find((e) => e.id === c.employeeId || e.code === c.code);
        let balances = emp?.balances || [];
        if (isEditMode && selectedLeaveType) {
          const filtered = balances.filter(
            (b) => b.leaveType?.toLowerCase() === selectedLeaveType.toLowerCase()
          );
          if (filtered.length > 0) {
            balances = filtered;
          }
        }

        return {
          ...c,
          selectedMonthName: newMonthName,
          selectedMonth: newActiveMonth,
          entries: c.entries.map((e) => {
            const b = balances.find((x) => x.leaveType?.toLowerCase() === e.leaveType?.toLowerCase()) || {};

            const monthDays = (b.monthlyBreakdown && b.monthlyBreakdown[newActiveMonth] !== undefined)
              ? Number(b.monthlyBreakdown[newActiveMonth]) || 0
              : (b.leaveMonth === newActiveMonth ? Number(b.consumed) || 0 : 0);

            const isExpired = isLeaveExpired(b.expirationDate || e.expirationDate);
            const days = isEditMode ? monthDays : 0;
            const monthState = computeMonthBalanceState(
              {
                allocated: b.allocated || e.allocated,
                monthlyBreakdown: b.monthlyBreakdown || e.monthlyBreakdown,
                consumed: b.consumed || e.existingConsumed,
                leaveMonth: b.leaveMonth,
              },
              newActiveMonth,
              days,
              isEditMode
            );

            const monthLwp = (b.monthlyLwpBreakdown && b.monthlyLwpBreakdown[newActiveMonth] !== undefined)
              ? Number(b.monthlyLwpBreakdown[newActiveMonth]) || 0
              : 0;

            return {
              ...e,
              isExpired: isExpired,
              daysTaken: days,
              otherMonthsConsumed: monthState.otherMonthsConsumed,
              existingDaysInThisMonth: monthState.existingDaysInThisMonth,
              currentBalance: monthState.availableQuotaInMonth,
              balanceAfter: monthState.balanceAfter,
              lwp: monthLwp,
              lopDays: monthState.lop,
              monthlyBreakdown: b.monthlyBreakdown || e.monthlyBreakdown || {},
              monthlyLopBreakdown: b.monthlyLopBreakdown || e.monthlyLopBreakdown || {},
              monthlyLwpBreakdown: b.monthlyLwpBreakdown || e.monthlyLwpBreakdown || {},
              monthlyEntries: b.monthlyEntries || e.monthlyEntries || {},
            };
          }),
        };
      })
    );
  };

  // Toggle Collapse/Expand
  const toggleCardCollapse = (cardId) => {
    setMarkLeaveCards((prev) =>
      prev.map((c) => (c.id === cardId ? { ...c, isExpanded: !c.isExpanded } : c))
    );
  };

  // Remove entire employee card
  const handleRemoveEmployeeCard = (cardId) => {
    setMarkLeaveCards((prev) => prev.filter((c) => c.id !== cardId));
  };

  // Real-time calculation when 'Days Taken' / 'Consumed this month' changes:
  const handleDaysTakenChange = (cardId, rowId, val) => {
    const parsed = val === "" ? "" : parseFloat(val);
    const numericVal = parsed === "" || isNaN(parsed) ? "" : parsed;
    
    setMarkLeaveCards((prev) =>
      prev.map((c) => {
        if (c.id !== cardId) return c;
        const cardMonth = c.selectedMonth;

        return {
          ...c,
          entries: c.entries.map((e) => {
            if (e.rowId === rowId) {
              if (e.isExpired && !isEditMode) return e;
              const days = numericVal === "" ? 0 : Math.max(0, numericVal);

              const monthState = computeMonthBalanceState(
                {
                  allocated: e.allocated,
                  monthlyBreakdown: e.monthlyBreakdown,
                  consumed: e.existingConsumed,
                },
                cardMonth,
                days,
                isEditMode
              );

              return {
                ...e,
                currentBalance: monthState.availableQuotaInMonth,
                daysTaken: numericVal,
                balanceAfter: monthState.balanceAfter,
                lopDays: monthState.lop,
              };
            }
            return e;
          }),
        };
      })
    );
  };

  // Real-time update when 'LWP' changes manually
  const handleLwpChange = (cardId, rowId, val) => {
    const parsed = val === "" ? "" : parseFloat(val);
    const numericVal = parsed === "" || isNaN(parsed) ? "" : parsed;
    setMarkLeaveCards((prev) =>
      prev.map((c) => {
        if (c.id !== cardId) return c;
        return {
          ...c,
          entries: c.entries.map((e) => {
            if (e.rowId === rowId) {
              return {
                ...e,
                lwp: numericVal,
              };
            }
            return e;
          }),
        };
      })
    );
  };

  const handleReasonChange = (cardId, rowId, val) => {
    setMarkLeaveCards((prev) =>
      prev.map((c) => {
        if (c.id !== cardId) return c;
        return {
          ...c,
          entries: c.entries.map((e) => (e.rowId === rowId ? { ...e, reason: val } : e)),
        };
      })
    );
  };

  // Modal Handlers for Add Employee
  const handleOpenAddEmployeeModal = () => {
    setModalSelectedIds(markLeaveCards.map((c) => c.employeeId));
    setModalSearchTerm("");
    setIsAddEmployeeModalOpen(true);
  };

  const handleToggleModalEmployee = (empId) => {
    setModalSelectedIds((prev) =>
      prev.includes(empId) ? prev.filter((id) => id !== empId) : [...prev, empId]
    );
  };

  const handleSelectAllModal = () => {
    const allIds = filteredModalEmployees.map((e) => e.id);
    const isAll = allIds.every((id) => modalSelectedIds.includes(id));
    if (isAll) {
      setModalSelectedIds((prev) => prev.filter((id) => !allIds.includes(id)));
    } else {
      setModalSelectedIds((prev) => Array.from(new Set([...prev, ...allIds])));
    }
  };

  const handleConfirmAddEmployees = () => {
    const currentEmpIds = markLeaveCards.map((c) => c.employeeId);
    const newEmpIds = modalSelectedIds.filter((id) => !currentEmpIds.includes(id));

    if (newEmpIds.length > 0) {
      const newCards = newEmpIds
        .map((id) => {
          const emp = allEmployees.find((e) => e.id === id);
          return emp ? buildCardForEmployee(emp, initialMonthName, selectedYear, false, null) : null;
        })
        .filter(Boolean);

      setMarkLeaveCards((prev) => [...prev, ...newCards]);

      Swal.fire({
        icon: "success",
        title: "Employees Added!",
        text: `Added ${newCards.length} employee(s) to leave marking list.`,
        timer: 1600,
        showConfirmButton: false,
      });
    }

    setIsAddEmployeeModalOpen(false);
  };

  const filteredModalEmployees = useMemo(() => {
    const q = modalSearchTerm.trim().toLowerCase();
    if (!q) return allEmployees;
    return allEmployees.filter(
      (e) =>
        e.name?.toLowerCase().includes(q) ||
        e.code?.toLowerCase().includes(q) ||
        e.department?.toLowerCase().includes(q) ||
        e.designation?.toLowerCase().includes(q)
    );
  }, [allEmployees, modalSearchTerm]);

  const [isSaving, setIsSaving] = useState(false);

  // Save All handler -> Saves each employee with their respective chosen month
  const handleSaveAll = async () => {
    const hasExpiredWithDays = markLeaveCards.some((card) =>
      card.entries.some((entry) => entry.isExpired && !isEditMode && Number(entry.daysTaken) > 0)
    );

    if (hasExpiredWithDays) {
      Swal.fire({
        icon: "warning",
        title: "Expired Leave Cannot Be Marked",
        text: "One or more leave types have expired and cannot be marked. Please remove days taken from expired leave rows.",
        confirmButtonColor: "#0284c7",
      });
      return;
    }

    setIsSaving(true);
    try {
      const preparedCards = markLeaveCards.map((card) => {
        const cardMonth = card.selectedMonth || `${initialMonthName} ${selectedYear}`;

        return {
          ...card,
          selectedMonth: cardMonth,
          leaveMonth: cardMonth,
          entries: card.entries.map((entry) => {
            const newDays = entry.daysTaken === "" || isNaN(entry.daysTaken) ? 0 : Number(entry.daysTaken);
            const autoLop = Number(entry.balanceAfter) < 0 ? Math.abs(Number(entry.balanceAfter)) : 0;
            const userLwp = entry.lwp === "" || isNaN(entry.lwp) ? 0 : Number(entry.lwp);

            const updatedMonthlyBreakdown = { ...(entry.monthlyBreakdown || {}) };
            const updatedMonthlyLopBreakdown = { ...(entry.monthlyLopBreakdown || {}) };
            const updatedMonthlyLwpBreakdown = { ...(entry.monthlyLwpBreakdown || {}) };
            const updatedMonthlyEntries = { ...(entry.monthlyEntries || {}) };
            const monthEntriesList = [...(updatedMonthlyEntries[cardMonth] || [])];

            if (newDays > 0 || userLwp > 0) {
              const genLeaveId = `LV-${selectedYear}-${Date.now()}-${Math.random().toString(36).substr(2, 4).toUpperCase()}`;
              const entryObj = {
                entryId: entry.targetEntryId || genLeaveId,
                leaveId: entry.targetEntryId || genLeaveId,
                leaveType: entry.leaveType,
                leaveMonth: cardMonth,
                daysTaken: newDays,
                fromDate: entry.fromDate || null,
                toDate: entry.toDate || null,
                reason: entry.reason || "",
                balanceAfter: entry.balanceAfter,
                lopDays: autoLop,
                lwp: userLwp,
                status: entry.status || "ACTIVE",
                markedAt: new Date().toISOString(),
              };

              const existingIdx = monthEntriesList.findIndex(
                (e) => (e.entryId === entryObj.entryId || e.leaveId === entryObj.leaveId)
              );
              if (existingIdx >= 0) {
                monthEntriesList[existingIdx] = entryObj;
              } else {
                monthEntriesList.push(entryObj);
              }

              updatedMonthlyEntries[cardMonth] = monthEntriesList;
            }

            const sumMonthDays = monthEntriesList.length > 0
              ? monthEntriesList.reduce((s, e) => s + (Number(e.daysTaken) || 0), 0)
              : newDays;
            const sumMonthLop = monthEntriesList.length > 0
              ? monthEntriesList.reduce((s, e) => s + (Number(e.lopDays) || 0), 0)
              : autoLop;
            const sumMonthLwp = monthEntriesList.length > 0
              ? monthEntriesList.reduce((s, e) => s + (Number(e.lwp) || 0), 0)
              : userLwp;

            if (cardMonth) {
              updatedMonthlyBreakdown[cardMonth] = sumMonthDays;
              updatedMonthlyLopBreakdown[cardMonth] = sumMonthLop;
              updatedMonthlyLwpBreakdown[cardMonth] = sumMonthLwp;
            }

            const otherMonthsSum = Object.entries(updatedMonthlyBreakdown).reduce((acc, [m, d]) => {
              if (m !== cardMonth) return acc + (Number(d) || 0);
              return acc;
            }, 0);
            const totalConsumed = otherMonthsSum + sumMonthDays;

            return {
              ...entry,
              consumed: totalConsumed,
              daysTaken: newDays,
              balanceAfter: entry.balanceAfter,
              lopDays: autoLop,
              lwp: userLwp,
              monthlyBreakdown: updatedMonthlyBreakdown,
              monthlyLopBreakdown: updatedMonthlyLopBreakdown,
              monthlyLwpBreakdown: updatedMonthlyLwpBreakdown,
              monthlyEntries: updatedMonthlyEntries,
            };
          }),
        };
      });

      await saveAllMarkedLeavesViaPutApi(preparedCards, selectedYear);

      const monthsSummary = Array.from(new Set(preparedCards.map((c) => c.selectedMonth))).join(", ");

      Swal.fire({
        icon: "success",
        title: isEditMode ? "Leaves Updated Successfully!" : "Leaves Marked Successfully!",
        html: `Saved leave records for <b>${markLeaveCards.length} selected employee(s)</b> across <b>${monthsSummary}</b>.<br/><span style="font-size: 13px; color: #64748b">Changes have been saved to the database via PUT API and updated on the Leave Overview.</span>`,
        confirmButtonText: "Back to Mark Leaves",
        confirmButtonColor: "#0284c7",
      }).then(() => {
        navigate("/mark-leaves", { state: { selectedYear } });
      });
    } catch (err) {
      console.error("Save leave consumption failed:", err);
      Swal.fire({
        icon: "error",
        title: "Save Failed",
        text: err?.response?.data?.message || err?.message || "Could not save leave changes to server.",
        confirmButtonColor: "#0284c7",
      });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>{isEditMode ? "Edit Leaves" : "Mark Leaves"} — Selected Employees | HRMS</title>
      </Helmet>

      {/* Page Header / Toolbar */}
      <div id="kt_app_toolbar" className="app-toolbar py-3 py-lg-6 mb-2">
        <div
          id="kt_app_toolbar_container"
          className="app-container container-fluid d-flex align-items-stretch"
        >
          <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
            {/* Title & Back Button */}
            <div className="page-title d-flex flex-column gap-1 me-3">
              <div className="d-flex align-items-center gap-3">
                <button
                  type="button"
                  className="btn btn-icon btn-sm btn-light border shadow-xs"
                  onClick={() => navigate("/mark-leaves", { state: { selectedYear } })}
                  title="Back to Mark Leaves Overview"
                >
                  <PiArrowLeftBold size={16} />
                </button>
                <div>
                  <h1 className="page-heading text-dark fw-bolder fs-2 mb-2" style={{ lineHeight: "1.25" }}>
                    {isEditMode
                      ? `Edit Leave Record ${selectedLeaveType ? `(${selectedLeaveType})` : ""}`
                      : `Mark Leaves Taken — Selected Employees (${markLeaveCards.length})`}
                  </h1>
                  <span className="text-gray-500 fw-medium fs-7 d-block" style={{ marginTop: "6px", lineHeight: "1.4" }}>
                    {isEditMode
                      ? `Modify and update consumed days for ${selectedLeaveType || "this leave"} in the selected month.`
                      : "Enter leave days taken per employee for their selected month. Remaining balances compute automatically."}
                  </span>
                </div>
              </div>
            </div>

            {/* Top Right Actions: Year Badge & Add Employee Button */}
            <div className="d-flex align-items-center gap-3 flex-wrap">
              {!isEditMode && (
                <button
                  type="button"
                  className="btn btn-sm btn-primary d-flex align-items-center gap-2 fw-bold px-4 py-2 shadow-xs"
                  style={{ borderRadius: "8px" }}
                  onClick={handleOpenAddEmployeeModal}
                >
                  <PiUserPlusBold size={16} />
                  <span>Add Employee</span>
                </button>
              )}

              <div className="d-flex align-items-center gap-2 bg-white px-3.5 py-2 rounded-3 border border-gray-300 shadow-xs">
                <PiCalendarBlankDuotone size={16} className="text-primary flex-shrink-0" />
                <span className="fs-7 fw-semibold text-gray-600">Year:</span>
                <span className="badge bg-light-primary text-primary border border-primary-subtle fw-bolder fs-7 px-3 py-1">
                  {selectedYear}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div id="kt_app_content" className="app-content flex-column-fluid">
        <div id="kt_app_content_container" className="app-container container-fluid">

          {/* Render each Selected Employee Card */}
          <div className="d-flex flex-column gap-5 mb-7">
            {markLeaveCards.length === 0 ? (
              <div
                className="card border-0 shadow-sm p-12 text-center"
                style={{
                  backgroundColor: "#ffffff",
                  borderRadius: "14px",
                  border: "1px dashed #cbd5e1",
                }}
              >
                <div className="text-gray-400 mb-3">
                  <PiUsersThreeDuotone size={48} className="text-primary opacity-50" />
                </div>
                <h4 className="text-gray-800 fw-bold mb-1">No Employees Selected</h4>
                <p className="text-gray-500 fs-7 mb-4">
                  Select employees from the system to mark leaves for <b>{selectedYear}</b>.
                </p>
                <div>
                  <button
                    type="button"
                    className="btn btn-primary fw-bold d-inline-flex align-items-center gap-2 px-5 py-2.5"
                    style={{ borderRadius: "8px" }}
                    onClick={handleOpenAddEmployeeModal}
                  >
                    <PiUserPlusBold size={16} />
                    <span>Select Employees</span>
                  </button>
                </div>
              </div>
            ) : (
              markLeaveCards.map((card, idx) => {
                const avatarStyle = AVATAR_COLORS[idx % AVATAR_COLORS.length];
                const cardMonth = card.selectedMonth || `${initialMonthName} ${selectedYear}`;

                return (
                  <div
                    key={card.id}
                    className="card border-0 shadow-sm"
                    style={{
                      backgroundColor: "#ffffff",
                      borderRadius: "14px",
                      boxShadow: "0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.03)",
                    }}
                  >
                    {/* Card Header: Employee Info, Per-Employee Month Selector & Controls */}
                    <div className="card-header border-bottom py-3 px-6 d-flex align-items-center justify-content-between bg-light-subtle flex-wrap gap-3">
                      <div className="d-flex align-items-center gap-3">
                        {/* Expand / Collapse Button */}
                        <button
                          type="button"
                          className="btn btn-icon btn-sm btn-light border-0"
                          onClick={() => toggleCardCollapse(card.id)}
                          title={card.isExpanded ? "Collapse leave types" : "Expand leave types"}
                        >
                          {card.isExpanded ? <PiCaretUpBold size={14} /> : <PiCaretDownBold size={14} />}
                        </button>

                        {/* Avatar */}
                        <div
                          style={{
                            width: "38px",
                            height: "38px",
                            borderRadius: "50%",
                            background: avatarStyle.bg,
                            color: avatarStyle.text,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontWeight: "700",
                            fontSize: "0.8rem",
                            flexShrink: 0,
                          }}
                        >
                          {getAvatarInitials(card.name)}
                        </div>

                        <div>
                          <div className="d-flex align-items-center gap-2 flex-wrap">
                            <span className="text-gray-900 fw-bold fs-6">
                              {card.name}
                            </span>
                            <span className="badge bg-light text-gray-700 border border-gray-200 fs-8 px-1.5 py-0.5">
                              ({card.code})
                            </span>
                            {(() => {
                              const expiredCount = card.entries.filter((e) => e.isExpired).length;
                              if (expiredCount === card.entries.length && card.entries.length > 0) {
                                return (
                                  <span className="badge bg-light-danger text-danger border border-danger-subtle fs-8 px-2 py-0.5 fw-bold">
                                    All Leaves Expired
                                  </span>
                                );
                              }
                              if (expiredCount > 0) {
                                return (
                                  <span className="badge bg-light-warning text-warning border border-warning-subtle fs-8 px-2 py-0.5 fw-bold">
                                    {expiredCount} Expired {expiredCount === 1 ? "Leave" : "Leaves"}
                                  </span>
                                );
                              }
                              return null;
                            })()}
                          </div>
                          <div className="d-flex align-items-center gap-3 text-gray-500 fs-7 mt-0.5">
                            <span className="d-flex align-items-center gap-1">
                              <PiBuildingsDuotone size={13} />
                              <span>{card.department}</span>
                            </span>
                            <span>•</span>
                            <span className="d-flex align-items-center gap-1">
                              <PiIdentificationBadgeDuotone size={13} />
                              <span>{card.designation}</span>
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Right Controls: Individual Month Selector for this employee & Remove Button */}
                      <div className="d-flex align-items-center gap-3 flex-wrap">
                        <div className="d-flex align-items-center gap-2 bg-white px-3 py-1.5 rounded-3 border border-gray-300 shadow-xs">
                          <PiCalendarBlankDuotone size={16} className="text-primary flex-shrink-0" />
                          <span className="fs-7 fw-semibold text-gray-600">Leave Month:</span>
                          <select
                            className="form-select form-select-sm border-0 bg-transparent fw-bold text-gray-800 p-0 ps-1 pe-6 cursor-pointer shadow-none fs-7"
                            style={{ width: "auto", minWidth: "125px" }}
                            value={card.selectedMonthName || initialMonthName}
                            onChange={(e) => handleCardMonthChange(card.id, e.target.value)}
                          >
                            {MONTH_NAMES.map((m) => (
                              <option key={m} value={m}>
                                {m}
                              </option>
                            ))}
                          </select>
                        </div>

                        <button
                          type="button"
                          className="btn btn-sm btn-light-danger fw-semibold fs-7 py-1.5 px-3"
                          style={{ borderRadius: "6px" }}
                          onClick={() => handleRemoveEmployeeCard(card.id)}
                        >
                          Remove
                        </button>
                      </div>
                    </div>

                    {/* Card Body: Sub-grid of Leave Types */}
                    {card.isExpanded && (
                      <div className="card-body p-0">
                        {/* Informative Banner: show if previous entries exist in this employee's selected month */}
                        {(() => {
                          const existingEntriesInMonth = [];
                          card.entries.forEach((e) => {
                            if (e.monthlyEntries && Array.isArray(e.monthlyEntries[cardMonth])) {
                              e.monthlyEntries[cardMonth].forEach((ent) => {
                                if (Number(ent.daysTaken) > 0) {
                                  existingEntriesInMonth.push(ent);
                                }
                              });
                            }
                          });

                          if (existingEntriesInMonth.length === 0) return null;

                          const totalMonthDays = existingEntriesInMonth.reduce(
                            (s, ent) => s + (Number(ent.daysTaken) || 0),
                            0
                          );

                          return (
                            <div className="px-6 py-2.5 bg-light-info border-bottom border-info d-flex align-items-center justify-content-between flex-wrap gap-2">
                              <div className="d-flex align-items-center gap-2">
                                <span className="badge badge-primary fs-8">
                                  {existingEntriesInMonth.length}{" "}
                                  {existingEntriesInMonth.length === 1 ? "Entry" : "Entries"} Recorded
                                </span>
                                <span className="text-gray-800 fs-7 fw-semibold">
                                  Already taken in {cardMonth}:{" "}
                                  <b className="text-primary">{totalMonthDays} Days</b> total.
                                </span>
                              </div>
                              <span className="text-muted fs-8">
                                {isEditMode
                                  ? "Editing leave entry"
                                  : "Submitting days below will add a new entry to this employee's record."}
                              </span>
                            </div>
                          );
                        })()}

                        <div className="table-responsive">
                          <table className="table table-row-bordered align-middle gs-0 gy-3 mb-0">
                            <thead style={{ backgroundColor: "#f8fafc" }}>
                              <tr className="border-bottom text-gray-600 fw-bold fs-7">
                                <th className="ps-6 py-3" style={{ width: "16%" }}>
                                  Leave Type
                                </th>
                                <th className="ps-6 py-3" style={{ width: "13%" }}>
                                  Allocated (Annual)
                                </th>
                                <th className="ps-6 py-3" style={{ width: "13%" }}>
                                  Consumed (YTD)
                                </th>
                                <th className="ps-6 py-3" style={{ width: "17%" }}>
                                  {isEditMode ? `Consumed (${cardMonth}) *` : `Days Taken (${cardMonth}) *`}
                                </th>
                                <th className="ps-6 py-3" style={{ width: "20%" }}>
                                  Reason (Optional)
                                </th>
                                <th className="ps-6 py-3" style={{ width: "11%" }}>
                                  Balance After
                                </th>
                                <th className="ps-6 py-3" style={{ width: "10%" }}>
                                  LWP
                                </th>
                              </tr>
                            </thead>
                            <tbody>
                              {card.entries.map((row) => (
                                <tr key={row.rowId} className="border-bottom">
                                  {/* 1. Leave Type */}
                                  <td className="ps-6 py-3">
                                    <div className="d-flex flex-column gap-1">
                                      <div className="d-flex align-items-center gap-2 flex-wrap">
                                        <span className="badge bg-light text-gray-900 border border-gray-300 fs-7 fw-bold px-3 py-2">
                                          {row.leaveType}
                                        </span>
                                        {row.isExpired && (
                                          <span className="badge bg-light-danger text-danger border border-danger-subtle fw-bold fs-8 px-2 py-1">
                                            Expired
                                          </span>
                                        )}
                                      </div>
                                      {row.isExpired && row.expirationDate && (
                                        <span className="text-danger fs-8 fw-semibold">
                                          Expired on {row.expirationDate}
                                        </span>
                                      )}
                                    </div>
                                  </td>

                                  {/* 2. Allocated (Annual Days) */}
                                  <td className="ps-6 py-3">
                                    <span className="badge badge-light-primary fw-bolder fs-7 px-3 py-2">
                                      {row.allocated} Days
                                    </span>
                                  </td>

                                  {/* 3. Consumed (YTD) */}
                                  <td className="ps-6 py-3">
                                    <span className="badge bg-light text-gray-800 border border-gray-200 fw-bold fs-7 px-3 py-2">
                                      {row.existingConsumed !== undefined ? row.existingConsumed : (row.consumed || 0)} Days
                                    </span>
                                  </td>

                                  {/* 4. Days Taken Input */}
                                  <td className="ps-6 py-3">
                                    <div style={{ maxWidth: "120px" }}>
                                      <input
                                        type="number"
                                        min="0"
                                        step="1"
                                        placeholder={row.isExpired && !isEditMode ? "Expired" : "0"}
                                        disabled={row.isExpired && !isEditMode}
                                        className={`form-control form-control-sm border-gray-300 fs-7 fw-bold ${
                                          row.isExpired && !isEditMode
                                            ? "bg-light-danger text-muted border-danger-subtle opacity-75 cursor-not-allowed"
                                            : ""
                                        }`}
                                        value={
                                          row.isExpired && !isEditMode
                                            ? ""
                                            : row.daysTaken === 0
                                            ? "0"
                                            : row.daysTaken !== undefined && row.daysTaken !== null
                                            ? row.daysTaken
                                            : ""
                                        }
                                        onChange={(e) =>
                                          handleDaysTakenChange(card.id, row.rowId, e.target.value)
                                        }
                                        title={
                                          row.isExpired && !isEditMode
                                            ? `This leave expired on ${row.expirationDate} and cannot be marked.`
                                            : ""
                                        }
                                      />
                                    </div>
                                  </td>

                                  {/* 5. Reason (Optional) */}
                                  <td className="ps-6 py-3">
                                    <div style={{ maxWidth: "230px" }}>
                                      <input
                                        type="text"
                                        placeholder={row.isExpired && !isEditMode ? "Leave is expired" : "e.g. Doctor appointment"}
                                        disabled={row.isExpired && !isEditMode}
                                        className={`form-control form-control-sm border-gray-300 fs-7 py-1 px-2.5 ${
                                          row.isExpired && !isEditMode ? "bg-light text-muted opacity-75 cursor-not-allowed" : ""
                                        }`}
                                        value={row.reason || ""}
                                        onChange={(e) => handleReasonChange(card.id, row.rowId, e.target.value)}
                                      />
                                    </div>
                                  </td>

                                  {/* 6. Balance After This Entry */}
                                  <td className="ps-6 py-3">
                                    {(() => {
                                      const computedBal =
                                        row.balanceAfter !== undefined && row.balanceAfter !== null
                                          ? row.balanceAfter
                                          : (Number(row.allocated) || 0) -
                                            ((Number(row.existingConsumed) || 0) + (Number(row.daysTaken) || 0));

                                      return (
                                        <div className="d-flex align-items-center gap-2">
                                          <span
                                            className={`fw-bolder fs-7 ${
                                              computedBal < 0
                                                ? "text-danger"
                                                : computedBal === 0
                                                ? "text-warning"
                                                : "text-gray-900"
                                            }`}
                                          >
                                            {computedBal} Days
                                          </span>
                                          {computedBal < 0 && (
                                            <span className="badge badge-light-danger fs-8">
                                              Over Limit
                                            </span>
                                          )}
                                        </div>
                                      );
                                    })()}
                                  </td>

                                  {/* 7. LWP */}
                                  <td className="ps-6 py-3">
                                    <div style={{ maxWidth: "100px" }}>
                                      <input
                                        type="number"
                                        min="0"
                                        step="1"
                                        placeholder="0"
                                        disabled={row.isExpired && !isEditMode}
                                        className={`form-control form-control-sm fs-7 fw-bold ${
                                          row.isExpired && !isEditMode
                                            ? "bg-light text-muted opacity-75 cursor-not-allowed border-gray-300"
                                            : (Number(row.lwp) || 0) > 0
                                            ? "border-danger text-danger bg-light-danger"
                                            : "border-gray-300"
                                        }`}
                                        value={
                                          row.isExpired && !isEditMode
                                            ? ""
                                            : row.lwp === 0
                                            ? "0"
                                            : row.lwp !== undefined && row.lwp !== null
                                            ? row.lwp
                                            : ""
                                        }
                                        onChange={(e) =>
                                          handleLwpChange(card.id, row.rowId, e.target.value)
                                        }
                                      />
                                    </div>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>

                          {/* Footer Note */}
                          <div className="p-3 bg-light-subtle border-top d-flex justify-content-between align-items-center">
                            <span className="text-gray-500 fs-8">
                              Leave allocations are defined on the Leave Allocation page.
                            </span>
                            <span className="text-gray-600 fs-8 fw-semibold">
                              Employee Leave Month: <b>{cardMonth}</b>
                            </span>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {/* Sticky Bottom Actions Bar */}
          <div
            className="d-flex align-items-center justify-content-between p-4 bg-white rounded-3 shadow-sm border mt-4"
            style={{
              position: "sticky",
              bottom: "20px",
              zIndex: 100,
              borderColor: "#e2e8f0",
            }}
          >
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="btn btn-light d-flex align-items-center gap-2 fw-semibold"
                style={{ borderRadius: "8px" }}
                onClick={() => navigate("/mark-leaves", { state: { selectedYear } })}
              >
                <PiArrowLeftBold size={16} />
                <span>Back to Overview</span>
              </button>

              <button
                type="button"
                className="btn btn-light-primary d-flex align-items-center gap-2 fw-bold px-4"
                style={{ borderRadius: "8px" }}
                onClick={handleOpenAddEmployeeModal}
              >
                <PiUserPlusBold size={16} />
                <span>Add Employee</span>
              </button>
            </div>

            <div className="d-flex align-items-center gap-3">
              <button
                type="button"
                className="btn btn-light fw-semibold"
                style={{ borderRadius: "8px" }}
                onClick={() => navigate("/mark-leaves", { state: { selectedYear } })}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary d-flex align-items-center gap-2 fw-bold px-6 shadow-sm"
                style={{
                  borderRadius: "8px",
                  boxShadow: "0 4px 14px 0 rgba(2, 132, 199, 0.35)",
                }}
                disabled={markLeaveCards.length === 0 || isSaving}
                onClick={handleSaveAll}
              >
                {isSaving ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status"></span>
                    <span>Saving...</span>
                  </>
                ) : (
                  <>
                    <PiFloppyDiskBold size={18} />
                    <span>{isEditMode ? "Save Changes" : "Save All"}</span>
                  </>
                )}
              </button>
            </div>
          </div>

        </div>
      </div>

      {/* Add Employee Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center justify-content-between pe-6 py-1">
            <div className="d-flex align-items-center gap-2">
              <PiUsersThreeDuotone size={22} className="text-primary" />
              <span className="fw-bolder fs-5 text-gray-900">Select Employees for Leave Marking</span>
            </div>
            <span className="badge bg-light text-gray-700 fs-8 fw-semibold">
              Year: {selectedYear}
            </span>
          </div>
        }
        open={isAddEmployeeModalOpen}
        onCancel={() => setIsAddEmployeeModalOpen(false)}
        footer={[
          <div key="footer-wrapper" className="d-flex align-items-center justify-content-between w-100">
            <button
              type="button"
              className="btn btn-sm btn-link text-gray-600 fw-semibold text-decoration-none p-0"
              onClick={handleSelectAllModal}
            >
              {filteredModalEmployees.length > 0 &&
              filteredModalEmployees.every((e) => modalSelectedIds.includes(e.id))
                ? "Deselect Filtered"
                : "Select All Filtered"}
            </button>
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="btn btn-sm btn-light fw-semibold px-4"
                style={{ borderRadius: "8px" }}
                onClick={() => setIsAddEmployeeModalOpen(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-sm btn-primary fw-bold px-4"
                style={{ borderRadius: "8px" }}
                onClick={handleConfirmAddEmployees}
              >
                Add Selected ({modalSelectedIds.length})
              </button>
            </div>
          </div>,
        ]}
        width={620}
        centered
      >
        <div className="py-2">
          {/* Search Bar */}
          <div
            className="d-flex align-items-center bg-light rounded-3 px-3 py-2 border mb-3"
            style={{ borderColor: "#e2e8f0" }}
          >
            <PiMagnifyingGlassDuotone size={18} className="text-gray-400 me-2 flex-shrink-0" />
            <input
              type="text"
              className="form-control form-control-sm border-0 bg-transparent text-gray-900 fw-medium shadow-none p-0"
              placeholder="Search employee by name, ID, department..."
              value={modalSearchTerm}
              onChange={(e) => setModalSearchTerm(e.target.value)}
            />
            {modalSearchTerm && (
              <button
                type="button"
                className="btn btn-icon btn-sm text-gray-400 hover-text-dark p-0 ms-1"
                onClick={() => setModalSearchTerm("")}
              >
                <PiXBold size={14} />
              </button>
            )}
          </div>

          {/* Employee Selection List */}
          <div
            style={{
              maxHeight: "360px",
              overflowY: "auto",
              paddingRight: "4px",
            }}
          >
            {filteredModalEmployees.length === 0 ? (
              <div className="text-center py-8 text-gray-500 fs-7">
                No employees found matching "{modalSearchTerm}".
              </div>
            ) : (
              filteredModalEmployees.map((emp, idx) => {
                const isChecked = modalSelectedIds.includes(emp.id);
                const isAlreadyInCards = markLeaveCards.some((c) => c.employeeId === emp.id);
                const avatarStyle = AVATAR_COLORS[idx % AVATAR_COLORS.length];

                return (
                  <div
                    key={emp.id}
                    className={`d-flex align-items-center justify-content-between p-3 rounded-3 mb-1.5 transition ${
                      isChecked
                        ? "bg-light-primary border border-primary-subtle"
                        : "bg-light-subtle hover-bg-light border border-transparent"
                    }`}
                    onClick={() => handleToggleModalEmployee(emp.id)}
                    style={{ cursor: "pointer", transition: "all 0.15s ease" }}
                  >
                    <div className="d-flex align-items-center gap-3">
                      <input
                        type="checkbox"
                        className="form-check-input mt-0 cursor-pointer"
                        checked={isChecked}
                        onChange={() => {}}
                        style={{ width: "18px", height: "18px" }}
                      />
                      <div
                        style={{
                          width: "36px",
                          height: "36px",
                          borderRadius: "50%",
                          background: avatarStyle.bg,
                          color: avatarStyle.text,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          fontWeight: "700",
                          fontSize: "0.75rem",
                          flexShrink: 0,
                        }}
                      >
                        {getAvatarInitials(emp.name)}
                      </div>
                      <div>
                        <div className="d-flex align-items-center gap-2">
                          <span className="fw-bold text-gray-900 fs-7">{emp.name}</span>
                          <span className="text-gray-500 fs-8">({emp.code})</span>
                        </div>
                        <div className="text-gray-500 fs-8">
                          {emp.department || "General"} • {emp.designation || "Associate"}
                        </div>
                      </div>
                    </div>

                    <div>
                      {isAlreadyInCards ? (
                        <span className="badge bg-light-success text-success border border-success-subtle fs-8 px-2 py-1">
                          Active in Editor
                        </span>
                      ) : (
                        <span className="badge bg-light text-gray-600 border border-gray-200 fs-8 px-2 py-1">
                          {(emp.balances || []).length} leave types
                        </span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </Modal>
    </>
  );
}
