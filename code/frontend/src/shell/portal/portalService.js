import { apiClient } from '../../shared/api/client.js';

/** An id -> name map of an org master list. */
function namesById(list) {
  return (Array.isArray(list) ? list : []).reduce((acc, item) => {
    if (item?.id) acc[item.id] = item.name;
    return acc;
  }, {});
}

/** The `data` of a PayslipApiResponse{status, message, data} envelope. */
function unwrapEnvelope(res) {
  const body = res?.data !== undefined ? res.data : res;
  return body && typeof body === 'object' && 'data' in body ? body.data : body;
}

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

  /**
   * id -> name maps of the tenant's departments, designations and work locations (D-77), so a
   * profile shows names rather than ids. Each list is read on its own: a list the caller may not
   * read leaves only its own map empty. Retired entries are included - an employee may still sit
   * in one.
   *
   * @returns {Promise<{departments: Object, designations: Object, workLocations: Object}>}
   */
  async getOrgNames() {
    const paths = ['/v1/departments', '/v1/designations', '/v1/work-locations'];
    const [departments, designations, workLocations] = (
      await Promise.allSettled(paths.map((path) => apiClient.get(path, { params: { activeOnly: false } })))
    ).map((outcome) => (outcome.status === 'fulfilled' ? namesById(outcome.value?.data) : {}));
    return { departments, designations, workLocations };
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

  /**
   * The caller's paid payslips, newest first (D-74).
   * PayslipController answers PayslipApiResponse{status, message, data}; data is a Spring page
   * (content, totalElements, number, size) of PayslipSummaryResponse.
   *
   * @param {number} [page] zero-based page number
   * @param {number} [size] rows per page
   */
  async getPayslips(page = 0, size = 12) {
    const res = await apiClient.get('/v1/me/payslips', { params: { page, size } });
    return unwrapEnvelope(res);
  },

  /**
   * One of the caller's paid payslips as PayslipResponse (D-74).
   *
   * @param {string} payrunId the pay run's id
   */
  async getPayslip(payrunId) {
    const res = await apiClient.get(`/v1/me/payslips/${encodeURIComponent(payrunId)}`);
    return unwrapEnvelope(res);
  },

  async getTimesheet() {
    const res = await apiClient.get('/v1/me/timesheet');
    return res?.data !== undefined ? res.data : res;
  },
};
