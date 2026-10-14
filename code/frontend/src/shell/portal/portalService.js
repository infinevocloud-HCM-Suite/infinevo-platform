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

  /**
   * The caller's primary reporting manager name, or null if unassigned / refused (D-77, BUG-D5-021).
   * Reads GET /api/v1/employees/{id}/reporting-line. Returns the manager's name as readable words.
   *
   * @param {string} employeeId the employee's id
   * @returns {Promise<string|null>}
   */
  async getReportingManager(employeeId) {
    if (!employeeId) return null;
    try {
      const res = await apiClient.get(`/v1/employees/${encodeURIComponent(employeeId)}/reporting-line`);
      const lines = res?.data !== undefined ? res.data : res;
      if (!Array.isArray(lines) || lines.length === 0) return null;
      const primary = lines.find((line) => line?.kind === 'PRIMARY') || lines[0];
      return primary?.managerName || null;
    } catch {
      return null;
    }
  },

  /**
   * Summary of the caller's job details for welcome / portal headers (E-01):
   * department, designation, and reporting manager as readable names.
   */
  async getJobSummary() {
    try {
      const [employee, orgNames] = await Promise.all([this.getProfile(), this.getOrgNames()]);
      if (!employee?.id) return null;
      const managerName = await this.getReportingManager(employee.id);
      return {
        department: (employee.departmentId && orgNames?.departments?.[employee.departmentId]) || null,
        designation: (employee.designationId && orgNames?.designations?.[employee.designationId]) || null,
        manager: managerName,
      };
    } catch {
      return null;
    }
  },

  /**
   * The caller's leave requests, newest first, as a Spring page (`content`, `totalElements`).
   * D-80: `params` ({ page, size }) asks for a given page; without it the server's default page.
   */
  async getLeaveRequests(params) {
    const res = params
      ? await apiClient.get('/v1/me/leave-requests', { params })
      : await apiClient.get('/v1/me/leave-requests');
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
