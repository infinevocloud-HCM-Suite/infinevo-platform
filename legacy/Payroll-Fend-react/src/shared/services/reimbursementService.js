import axios from "axios";
import { GlobalConst } from "../appConfig/globalConst";

/**
 * Common helper to build request headers with Authorization token and organizationId
 */
const getAuthHeaders = () => {
  const token = localStorage.getItem("__t");
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  return {
    organizationId,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
};

/**
 * ============================================================================
 * EMPLOYEE REIMBURSEMENT APIS
 * ============================================================================
 */

/**
 * Fetch all reimbursement requests submitted by the logged-in employee.
 * GET /api/employee/reimbursements
 */
export const getEmployeeReimbursements = async () => {
  const response = await axios.get(`${GlobalConst.API_URL}/api/employee/reimbursements`, {
    headers: getAuthHeaders(),
  });
  return response.data?.data || [];
};

/**
 * Fetch single reimbursement details for the logged-in employee.
 * GET /api/employee/reimbursements/{id}
 */
export const getEmployeeReimbursementById = async (id) => {
  const response = await axios.get(`${GlobalConst.API_URL}/api/employee/reimbursements/${id}`, {
    headers: getAuthHeaders(),
  });
  return response.data?.data || response.data;
};

/**
 * Submit a new reimbursement request for the logged-in employee.
 * POST /api/employee/reimbursements
 */
export const createEmployeeReimbursement = async (payload) => {
  const headers = getAuthHeaders();
  if (payload instanceof FormData) {
    delete headers["Content-Type"];
  }
  const response = await axios.post(
    `${GlobalConst.API_URL}/api/employee/reimbursements`,
    payload,
    {
      headers,
    }
  );
  return response.data;
};

/**
 * ============================================================================
 * ADMIN REIMBURSEMENT APIS
 * ============================================================================
 */

/**
 * Fetch all reimbursement requests within the organization for the Admin Portal.
 * GET /admin/reimbursements
 */
export const getAdminReimbursements = async () => {
  const response = await axios.get(`${GlobalConst.API_URL}/admin/reimbursements`, {
    headers: getAuthHeaders(),
  });
  return response.data?.data || [];
};

/**
 * Fetch single reimbursement details for Admin Review modal.
 * GET /admin/reimbursements/{id}
 */
export const getAdminReimbursementById = async (id) => {
  const response = await axios.get(`${GlobalConst.API_URL}/admin/reimbursements/${id}`, {
    headers: getAuthHeaders(),
  });
  return response.data?.data || response.data;
};

/**
 * Approve a reimbursement request with approvedAmount and optional remarks / reimbursementMonth.
 * PUT /admin/reimbursements/{id}/approve
 */
export const approveAdminReimbursement = async (id, data) => {
  const response = await axios.put(
    `${GlobalConst.API_URL}/admin/reimbursements/${id}/approve`,
    data,
    {
      headers: getAuthHeaders(),
    }
  );
  return response.data;
};

/**
 * Reject a reimbursement request with remarks.
 * PUT /admin/reimbursements/{id}/reject
 */
export const rejectAdminReimbursement = async (id, data) => {
  const response = await axios.put(
    `${GlobalConst.API_URL}/admin/reimbursements/${id}/reject`,
    data,
    {
      headers: getAuthHeaders(),
    }
  );
  return response.data;
};
