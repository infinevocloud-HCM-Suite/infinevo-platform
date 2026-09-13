/**
 * Reimbursement API Service
 *
 * Centralised API functions for the Employee Reimbursement module.
 * Uses the same axios + GlobalConst.API_URL + auth header pattern
 * as other pages in this project (e.g. payslipGenerator.js).
 *
 * Auth:
 *  - JWT Bearer token from localStorage.__t  (auto-attached per request)
 *  - organizationId from caller (localStorage.getItem("organizationId"))
 *  - employeeId is NEVER sent in the request body — backend reads it from JWT
 */

import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";

/**
 * Build standard headers for every reimbursement API call.
 * @param {string} organizationId
 * @returns {Object} headers object
 */
const buildHeaders = (organizationId) => ({
  organizationId: organizationId,
  Authorization: `Bearer ${localStorage.getItem("__t")}`,
  "Content-Type": "application/json",
});

/**
 * Fetch all reimbursement requests for the authenticated employee.
 * GET /api/employee/reimbursements
 *
 * @param {string} organizationId
 * @returns {Promise<Array>} array of EmployeeReimbursementResponseDTO
 */
export const getEmployeeReimbursements = async (organizationId) => {
  const response = await axios.get(
    `${GlobalConst.API_URL}/api/employee/reimbursements`,
    { headers: buildHeaders(organizationId) }
  );
  // Backend wraps: { status, message, data: [...] }
  return response.data?.data ?? [];
};

/**
 * Submit a new reimbursement request.
 * POST /api/employee/reimbursements
 *
 * @param {Object} payload  - { reimbursementType, requestedAmount, billDate, description, attachmentUrl }
 * @param {string} organizationId
 * @returns {Promise<Object>} created EmployeeReimbursementResponseDTO
 */
export const createEmployeeReimbursement = async (payload, organizationId) => {
  const response = await axios.post(
    `${GlobalConst.API_URL}/api/employee/reimbursements`,
    payload,
    { headers: buildHeaders(organizationId) }
  );
  return response.data;
};

/**
 * Fetch a single reimbursement request by ID (own records only).
 * GET /api/employee/reimbursements/{id}
 *
 * Returns null if the backend returns 404 (record not found / not owned by employee).
 *
 * @param {number|string} id
 * @param {string}        organizationId
 * @returns {Promise<Object|null>} EmployeeReimbursementResponseDTO or null
 */
export const getReimbursementById = async (id, organizationId) => {
  const response = await axios.get(
    `${GlobalConst.API_URL}/api/employee/reimbursements/${id}`,
    { headers: buildHeaders(organizationId) }
  );
  return response.data?.data ?? null;
};
