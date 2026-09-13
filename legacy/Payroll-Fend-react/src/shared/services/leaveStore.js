/**
 * leaveStore.js
 * Comprehensive Leave Allocation, Consumption, and Balance tracking service.
 * Connects directly to backend API (/api/leave-allocation) with persistent local fallback.
 */

import axios from "axios";
import dayjs from "dayjs";
import { GlobalConst } from "../appConfig/globalConst";

const LEAVE_STORE_PREFIX = "hrms_leave_employees_state_v6";

export const DEFAULT_LEAVE_EMPLOYEES = [];

const CURRENT_YEAR = String(new Date().getFullYear());

/**
 * Helper to check if a leave allocation has expired
 */
export const isLeaveExpired = (expirationDate) => {
  if (!expirationDate) return false;
  const exp = dayjs(String(expirationDate).trim());
  if (!exp.isValid()) return false;
  return exp.isBefore(dayjs(), "day");
};

/**
 * Helper to get year-specific storage key
 */
export const getLeaveStoreKey = (year = CURRENT_YEAR) => `${LEAVE_STORE_PREFIX}_${year}`;

/**
 * Helper to build common auth & tenant headers
 */
const getAuthHeaders = () => {
  const token = localStorage.getItem("__t");
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  return {
    Authorization: `Bearer ${token}`,
    organizationId: organizationId,
  };
};

/**
 * Normalizes a balance entry to ensure mathematical consistency and expiration checks
 */
export const normalizeBalanceRecord = (entry) => {
  const allocated = Number(entry.allocated) || 0;
  const consumed = Number(entry.consumed) || 0;
  const rawDiff = allocated - consumed;
  const expirationDate = entry.expirationDate || `${CURRENT_YEAR}-12-31`;
  const expired = isLeaveExpired(expirationDate);

  let balance = rawDiff;
  let lop = entry.lopDays !== undefined ? Number(entry.lopDays) : (entry.lop !== undefined ? Number(entry.lop) : 0);
  let status = "Available";
  let statusType = "success";

  if (expired) {
    status = "Expired";
    statusType = "danger";
  } else if (rawDiff <= 0) {
    balance = rawDiff < 0 ? rawDiff : 0;
    lop = Math.max(lop, Math.abs(rawDiff));
    status = "Exhausted";
    statusType = "danger";
  } else if (rawDiff <= 3) {
    status = "Low Balance";
    statusType = "warning";
  }

  return {
    ...entry,
    allocated,
    consumed,
    balance,
    lop,
    lopDays: lop,
    lwp: entry.lwp !== undefined ? Number(entry.lwp) : 0,
    leaveMonth: entry.leaveMonth || null,
    monthlyBreakdown: entry.monthlyBreakdown || {},
    monthlyLopBreakdown: entry.monthlyLopBreakdown || {},
    monthlyLwpBreakdown: entry.monthlyLwpBreakdown || {},
    monthlyEntries: entry.monthlyEntries || {},
    status,
    statusType,
    isExpired: expired,
    expirationDate,
  };
};

/**
 * Get stored employees from localStorage for a specific year
 */
export const getStoredLeaveEmployees = (year = CURRENT_YEAR) => {
  try {
    const raw = localStorage.getItem(getLeaveStoreKey(year));
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return [];
    }
    return parsed.map((emp) => ({
      ...emp,
      balances: (emp.balances || []).map(normalizeBalanceRecord),
    }));
  } catch (err) {
    console.warn(`Could not read leave store for year ${year} from localStorage:`, err);
    return [];
  }
};

/**
 * Save employees leave balances to localStorage for a specific year
 */
export const saveLeaveEmployees = (employees, year = CURRENT_YEAR, notify = true) => {
  try {
    localStorage.setItem(getLeaveStoreKey(year), JSON.stringify(employees));
    if (notify) {
      window.dispatchEvent(new CustomEvent("leave_store_updated", { detail: { year } }));
    }
  } catch (err) {
    console.error(`Could not write leave store for year ${year} to localStorage:`, err);
  }
};

/**
 * Fetch leave allocations from Backend API (GET /api/leave-allocation?year=...)
 * If backend has records, formats and saves to local state.
 * If backend is empty or unavailable, falls back cleanly to local stored employees.
 */
/**
 * Fetch leave allocations and consumptions from Backend API
 * Queries /api/leave-consumption?year=... first, falling back to /api/leave-allocation.
 */
/**
 * Fetch leave allocations and consumptions from Backend API
 * Queries both /api/leave-allocation (annual master definitions) and /api/leave-consumption (operational tracking)
 * and merges them so all allocated employees are always available across all leave management pages.
 */
export const fetchLeaveAllocationsFromApi = async (year = CURRENT_YEAR) => {
  try {
    // 1. Fetch from leave-allocation (annual master definitions)
    const allocMap = new Map();
    try {
      const allocRes = await axios.get(
        `${GlobalConst.API_URL}/api/leave-allocation?year=${encodeURIComponent(year)}`,
        { headers: getAuthHeaders() }
      );
      if (allocRes.data && allocRes.data.status === 200 && Array.isArray(allocRes.data.data)) {
        allocRes.data.data.forEach((item) => {
          if (item && item.employeeId) {
            allocMap.set(item.employeeId, item);
          }
        });
      }
    } catch (e) {
      console.warn("GET /api/leave-allocation failed:", e?.message);
    }

    // 2. Fetch from leave-consumption (operational tracking)
    const consMap = new Map();
    try {
      const consRes = await axios.get(
        `${GlobalConst.API_URL}/api/leave-consumption?year=${encodeURIComponent(year)}`,
        { headers: getAuthHeaders() }
      );
      if (consRes.data && consRes.data.status === 200 && Array.isArray(consRes.data.data)) {
        consRes.data.data.forEach((item) => {
          if (item && item.employeeId) {
            consMap.set(item.employeeId, item);
          }
        });
      }
    } catch (e) {
      console.warn("GET /api/leave-consumption failed:", e?.message);
    }

    // 3. Union of all employeeIds across both APIs:
    // An employee belongs in this year's leaves if they have an active allocation record in allocMap,
    // OR if they have positive recorded consumption in consMap.
    const allCandidateIds = Array.from(new Set([...allocMap.keys(), ...consMap.keys()]));
    const validEmployeeIds = allCandidateIds.filter((empId) => {
      if (allocMap.has(empId)) return true;
      const cons = consMap.get(empId);
      if (cons && Array.isArray(cons.leaveTypes)) {
        const hasConsumption = cons.leaveTypes.some(
          (lt) => (Number(lt.consumedDays) || 0) > 0 || (Number(lt.lopDays) || 0) > 0 || (Number(lt.lwp) || 0) > 0
        );
        if (hasConsumption) return true;
      }
      return false;
    });

    const formatted = validEmployeeIds.map((empId) => {
      const alloc = allocMap.get(empId);
      const cons = consMap.get(empId);

      const name = (cons && cons.employeeName && cons.employeeName !== "Employee")
        ? cons.employeeName
        : (alloc && alloc.employeeName) || "Employee";
      const code = (cons && cons.employeeNumber && cons.employeeNumber !== "EMP")
        ? cons.employeeNumber
        : (alloc && alloc.employeeNumber) || empId;

      // Merge leave types by leaveType name
      const ltMap = new Map();

      // Add base allocations
      if (alloc && Array.isArray(alloc.leaveTypes)) {
        alloc.leaveTypes.forEach((lt) => {
          const total = lt.totalDays || ((lt.annualDays || 0) + (lt.carriedForwardDays || 0));
          ltMap.set(lt.leaveType.toLowerCase(), {
            id: lt.id || `leave-${lt.leaveType}`,
            leaveType: lt.leaveType,
            allocated: total,
            consumed: 0,
            balance: total,
            lop: 0,
            lopDays: 0,
            lwp: 0,
            leaveMonth: null,
            monthlyBreakdown: {},
            monthlyLopBreakdown: {},
            monthlyLwpBreakdown: {},
            monthlyEntries: {},
            expirationDate: lt.expirationDate || `${year}-12-31`,
            carryForward: Boolean(lt.carryForward),
          });
        });
      }

      // Overlay consumption data
      if (cons && Array.isArray(cons.leaveTypes)) {
        cons.leaveTypes.forEach((lt) => {
          const key = lt.leaveType.toLowerCase();
          const existing = ltMap.get(key);
          const total = lt.totalDays || (existing ? existing.allocated : ((lt.annualDays || 0) + (lt.carriedForwardDays || 0)));
          const consumed = lt.consumedDays !== undefined ? lt.consumedDays : 0;
          const balance = lt.balanceDays !== undefined ? lt.balanceDays : Math.max(0, total - consumed);

          ltMap.set(key, {
            id: lt.id || (existing ? existing.id : `leave-${lt.leaveType}`),
            leaveType: lt.leaveType,
            allocated: total,
            consumed: consumed,
            balance: balance,
            lop: lt.lopDays !== undefined ? lt.lopDays : (consumed > total ? consumed - total : 0),
            lopDays: lt.lopDays !== undefined ? lt.lopDays : (consumed > total ? consumed - total : 0),
            lwp: lt.lwp !== undefined ? lt.lwp : 0,
            leaveMonth: lt.leaveMonth || cons.leaveMonth || null,
            monthlyBreakdown: lt.monthlyBreakdown || {},
            monthlyLopBreakdown: lt.monthlyLopBreakdown || {},
            monthlyLwpBreakdown: lt.monthlyLwpBreakdown || {},
            monthlyEntries: lt.monthlyEntries || {},
            expirationDate: lt.expirationDate || (existing ? existing.expirationDate : `${year}-12-31`),
            carryForward: lt.carryForward !== undefined ? Boolean(lt.carryForward) : (existing ? existing.carryForward : false),
          });
        });
      }

      return {
        id: empId,
        name: name,
        code: code,
        department: (alloc && alloc.department) || (cons && cons.department) || "General",
        designation: (alloc && alloc.designation) || (cons && cons.designation) || "Associate",
        balances: Array.from(ltMap.values()).map(normalizeBalanceRecord),
      };
    });

    saveLeaveEmployees(formatted, year, false);
    return formatted;
  } catch (err) {
    console.warn(`Live API leave fetch failed for year ${year}, using stored records:`, err?.message);
    return getStoredLeaveEmployees(year);
  }
};

/**
 * Delete single leave entry for an employee by entryId
 */
export const deleteEmployeeLeaveEntryApi = async (employeeId, year = CURRENT_YEAR, entryId) => {
  try {
    const url = `${GlobalConst.API_URL}/api/leave-consumption/${encodeURIComponent(employeeId)}/entry/${encodeURIComponent(entryId)}?year=${encodeURIComponent(year)}`;
    const response = await axios.delete(url, { headers: getAuthHeaders() });
    return response.data;
  } catch (err) {
    console.warn(`DELETE /api/leave-consumption/${employeeId}/entry/${entryId} failed:`, err?.message);
    throw err;
  }
};

/**
 * Delete single employee's leave consumption for a specific month
 */
export const deleteEmployeeMonthConsumptionApi = async (employeeId, year = CURRENT_YEAR, month = null) => {
  try {
    const url = month
      ? `${GlobalConst.API_URL}/api/leave-consumption/${encodeURIComponent(employeeId)}?year=${encodeURIComponent(year)}&month=${encodeURIComponent(month)}`
      : `${GlobalConst.API_URL}/api/leave-consumption/${encodeURIComponent(employeeId)}?year=${encodeURIComponent(year)}`;

    const response = await axios.delete(url, { headers: getAuthHeaders() });
    return response.data;
  } catch (err) {
    console.warn(`DELETE /api/leave-consumption/${employeeId} failed:`, err?.message);
    throw err;
  }
};

/**
 * Update single employee's leave consumption via PUT API (PUT /api/leave-consumption/{employeeId}?year=...)
 */
export const updateEmployeeLeaveConsumptionApi = async (employeeId, entries, year = CURRENT_YEAR, leaveMonth = null) => {
  const payload = {
    employeeId: employeeId,
    leaveMonth: leaveMonth,
    leaveTypes: entries.map((entry) => ({
      id: entry.id && typeof entry.id === "number" ? entry.id : (entry.id && !String(entry.id).startsWith("leave-") ? Number(entry.id) || null : null),
      leaveType: entry.leaveType,
      annualDays: Number(entry.allocated) || 12,
      carriedForwardDays: Number(entry.carriedForwardDays) || 0,
      consumedDays: Number(entry.daysTaken !== undefined && entry.daysTaken !== "" ? entry.daysTaken : (entry.consumed || 0)),
      lopDays: Number(entry.lopDays !== undefined ? entry.lopDays : (Number(entry.balanceAfter) < 0 ? Math.abs(Number(entry.balanceAfter)) : 0)),
      lwp: Number(entry.lwp !== undefined ? entry.lwp : 0),
      leaveMonth: leaveMonth || entry.leaveMonth,
      monthlyBreakdown: entry.monthlyBreakdown || {},
      monthlyLopBreakdown: entry.monthlyLopBreakdown || {},
      monthlyLwpBreakdown: entry.monthlyLwpBreakdown || {},
      monthlyEntries: entry.monthlyEntries || {},
      expirationDate: entry.expirationDate || "2026-12-31",
      carryForward: entry.carryForward !== undefined ? Boolean(entry.carryForward) : false,
    })),
  };

  try {
    const response = await axios.put(
      `${GlobalConst.API_URL}/api/leave-consumption/${encodeURIComponent(employeeId)}?year=${encodeURIComponent(year)}`,
      payload,
      {
        headers: getAuthHeaders(),
      }
    );
    return response.data;
  } catch (err) {
    console.warn(`PUT /api/leave-consumption/${employeeId} failed:`, err?.message);
    throw err;
  }
};

/**
 * Update single employee's leave allocation via PUT API (PUT /api/leave-allocation/{employeeId}?year=...)
 */
export const updateEmployeeLeaveAllocationApi = async (employeeId, entries, year = CURRENT_YEAR, leaveMonth = null) => {
  return updateEmployeeLeaveConsumptionApi(employeeId, entries, year, leaveMonth);
};

/**
 * Save marked leaves for all selected employees via individual PUT API calls in parallel
 * Updates both database via PUT /api/leave-consumption and local storage.
 */
export const saveAllMarkedLeavesViaPutApi = async (cards, year = CURRENT_YEAR, leaveMonth = null) => {
  // 1. Update local store first for immediate reactivity
  const updatedLocal = applyMarkLeaveCardsUpdates(cards, leaveMonth, year);

  // 2. Perform parallel PUT requests for each modified employee card to the consumption endpoint
  const results = await Promise.allSettled(
    cards.map((card) => {
      const employeeId = card.employeeId || card.id;
      const cardMonth = card.selectedMonth || card.leaveMonth || leaveMonth;
      return updateEmployeeLeaveConsumptionApi(employeeId, card.entries, year, cardMonth);
    })
  );

  console.log("PUT leave consumption results:", results);
  return updatedLocal;
};

/**
 * Save leave allocations directly to Backend API (POST /api/leave-allocation/bulk)
 * And simultaneously updates local store.
 */
export const saveLeaveAllocationsToApi = async (cards, year = CURRENT_YEAR) => {
  // 1. Update local store first
  const updatedLocal = applyMarkLeaveCardsUpdates(cards, null, year);

  // 2. Prepare payload for Bulk API
  const bulkPayload = {
    year: year,
    allocations: cards.map((card) => ({
      employeeId: card.employeeId,
      leaveTypes: card.entries.map((entry) => ({
        id: entry.id && typeof entry.id === "number" ? entry.id : (entry.id && !String(entry.id).startsWith("leave-") ? Number(entry.id) || null : null),
        leaveType: entry.leaveType,
        annualDays: Number(entry.allocated) || 12,
        carriedForwardDays: 0,
        consumedDays: Number(entry.daysTaken !== undefined && entry.daysTaken !== "" ? entry.daysTaken : (entry.consumed || 0)),
        expirationDate: entry.expirationDate || `${year}-12-31`,
        carryForward: true,
      })),
    })),
  };

  try {
    const response = await axios.post(
      `${GlobalConst.API_URL}/api/leave-allocation/bulk`,
      bulkPayload,
      {
        headers: getAuthHeaders(),
      }
    );
    if (response.data && response.data.status === 200) {
      console.log("Bulk leave allocation successfully synced with backend DB.");
    }
  } catch (apiErr) {
    console.warn("Could not save to backend /api/leave-allocation/bulk:", apiErr?.message);
  }

  return updatedLocal;
};

/**
 * Update employee balances from the MarkLeaveAddEmploy cards state
 */
export const applyMarkLeaveCardsUpdates = (cards, leaveMonth = null, year = CURRENT_YEAR) => {
  const currentEmployees = getStoredLeaveEmployees(year);
  const updatedEmployees = [...currentEmployees];

  cards.forEach((card) => {
    const cardMonth = card.selectedMonth || card.leaveMonth || leaveMonth;

    let empIndex = updatedEmployees.findIndex(
      (e) => e.id === card.employeeId || e.code === card.code
    );

    if (empIndex === -1) {
      const newEmp = {
        id: card.employeeId || `EMP-${Date.now()}`,
        name: card.name,
        code: card.code,
        department: card.department || "General",
        designation: card.designation || "Associate",
        balances: [],
      };
      updatedEmployees.push(newEmp);
      empIndex = updatedEmployees.length - 1;
    }

    const employee = { ...updatedEmployees[empIndex] };
    const currentBalances = Array.isArray(employee.balances) ? [...employee.balances] : [];
    const newBalances = [...currentBalances];

    card.entries.forEach((entry) => {
      const leaveType = entry.leaveType;
      const daysTaken =
        entry.daysTaken === "" || isNaN(entry.daysTaken) ? 0 : Number(entry.daysTaken);
      const allocated = Number(entry.allocated) || 12;

      let balIndex = newBalances.findIndex(
        (b) => b.leaveType?.toLowerCase() === leaveType?.toLowerCase()
      );

      if (balIndex !== -1) {
        const existing = newBalances[balIndex];
        const monthlyBreakdown = { ...(entry.monthlyBreakdown || existing.monthlyBreakdown || {}) };
        const monthlyLopBreakdown = { ...(entry.monthlyLopBreakdown || existing.monthlyLopBreakdown || {}) };
        const monthlyLwpBreakdown = { ...(entry.monthlyLwpBreakdown || existing.monthlyLwpBreakdown || {}) };
        const monthlyEntries = { ...(entry.monthlyEntries || existing.monthlyEntries || {}) };

        const totalYtd = Object.values(monthlyBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);
        const newConsumed = entry.consumed !== undefined ? Number(entry.consumed) : (Object.keys(monthlyBreakdown).length > 0 ? totalYtd : daysTaken);
        const totalLopYtd = Object.values(monthlyLopBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);
        const totalLwpYtd = Object.values(monthlyLwpBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);

        newBalances[balIndex] = normalizeBalanceRecord({
          ...existing,
          allocated,
          consumed: newConsumed,
          balance: Math.max(0, allocated - newConsumed),
          monthlyBreakdown,
          monthlyLopBreakdown,
          monthlyLwpBreakdown,
          monthlyEntries,
          leaveMonth: cardMonth || leaveMonth || existing.leaveMonth,
          lop: totalLopYtd,
          lopDays: totalLopYtd,
          lwp: totalLwpYtd,
        });
      } else {
        const monthlyBreakdown = { ...(entry.monthlyBreakdown || {}) };
        const monthlyLopBreakdown = { ...(entry.monthlyLopBreakdown || {}) };
        const monthlyLwpBreakdown = { ...(entry.monthlyLwpBreakdown || {}) };
        const monthlyEntries = { ...(entry.monthlyEntries || {}) };

        const totalYtd = Object.values(monthlyBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);
        const newConsumed = entry.consumed !== undefined ? Number(entry.consumed) : (Object.keys(monthlyBreakdown).length > 0 ? totalYtd : daysTaken);
        const totalLopYtd = Object.values(monthlyLopBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);
        const totalLwpYtd = Object.values(monthlyLwpBreakdown).reduce((a, c) => a + (Number(c) || 0), 0);

        newBalances.push(
          normalizeBalanceRecord({
            id: `leave-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
            leaveType: leaveType,
            allocated: allocated,
            consumed: newConsumed,
            balance: Math.max(0, allocated - newConsumed),
            monthlyBreakdown,
            monthlyLopBreakdown,
            monthlyLwpBreakdown,
            monthlyEntries,
            leaveMonth: cardMonth || leaveMonth,
            lop: totalLopYtd,
            lopDays: totalLopYtd,
            lwp: totalLwpYtd,
          })
        );
      }
    });

    employee.balances = newBalances;
    updatedEmployees[empIndex] = employee;
  });

  saveLeaveEmployees(updatedEmployees, year);
  return updatedEmployees;
};
