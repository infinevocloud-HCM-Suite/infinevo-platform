import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/employees';

/**
 * Employee salary structure and versions API service (W-47.1a §5, W-26.2).
 */
export const salaryService = {
  /**
   * Get salary version in force as of a given date (default today on backend).
   * @param {string} employeeId
   * @param {string} [date] YYYY-MM-DD
   */
  async asOf(employeeId, date) {
    const params = date ? { asOf: date } : {};
    const res = await apiClient.get(`${BASE_PATH}/${employeeId}/salary`, { params });
    return res.data;
  },

  /**
   * List all salary structure versions for an employee.
   * @param {string} employeeId
   */
  async versions(employeeId) {
    const res = await apiClient.get(`${BASE_PATH}/${employeeId}/salary/versions`);
    return res.data;
  },

  /**
   * Create the first salary structure version for an employee.
   * @param {string} employeeId
   * @param {Object} body SalaryVersionRequest
   */
  async create(employeeId, body) {
    const res = await apiClient.post(`${BASE_PATH}/${employeeId}/salary`, body);
    return res.data;
  },

  /**
   * Revise an employee's salary structure (creates a new dated version).
   * @param {string} employeeId
   * @param {Object} body SalaryVersionRequest
   */
  async revise(employeeId, body) {
    const res = await apiClient.post(`${BASE_PATH}/${employeeId}/salary/revisions`, body);
    return res.data;
  },

  /**
   * Update an existing salary structure version.
   * @param {string} employeeId
   * @param {string} versionId
   * @param {Object} body SalaryVersionRequest
   */
  async update(employeeId, versionId, body) {
    const res = await apiClient.put(`${BASE_PATH}/${employeeId}/salary/versions/${versionId}`, body);
    return res.data;
  },

  /**
   * Cancel an existing salary structure version.
   * @param {string} employeeId
   * @param {string} versionId
   */
  async cancel(employeeId, versionId) {
    const res = await apiClient.delete(`${BASE_PATH}/${employeeId}/salary/versions/${versionId}`);
    return res.data;
  },
};
