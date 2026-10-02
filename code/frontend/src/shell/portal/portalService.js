import { apiClient } from '../../shared/api/client.js';

/**
 * Service for employee self-service portal (W-25).
 */
export const portalService = {
  /**
   * Fetches the dynamic list of panels available to the caller.
   * Gated server-side by employee.isPortalEnabled, module entitlement, and action checks.
   */
  async getPanels() {
    const res = await apiClient.get('/v1/me/panels');
    return res?.data !== undefined ? res.data : res;
  },

  async getProfile() {
    const res = await apiClient.get('/v1/me/employee');
    return res?.data !== undefined ? res.data : res;
  },

  async getLeaveRequests() {
    const res = await apiClient.get('/v1/me/leave-requests');
    return res?.data !== undefined ? res.data : res;
  },

  async getLeaveBalances() {
    const res = await apiClient.get('/v1/me/leave-balances');
    return res?.data !== undefined ? res.data : res;
  },

  async getDocuments() {
    const res = await apiClient.get('/v1/me/documents');
    return res?.data !== undefined ? res.data : res;
  },

  async getPayslips() {
    const res = await apiClient.get('/v1/me/payslips');
    return res?.data !== undefined ? res.data : res;
  },

  async getTimesheet() {
    const res = await apiClient.get('/v1/me/timesheet');
    return res?.data !== undefined ? res.data : res;
  },
};
