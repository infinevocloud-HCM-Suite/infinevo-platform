import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/employees';

/**
 * Employee statutory profile API service (W-47.1a §5, W-26.2).
 */
export const statutoryProfileService = {
  /**
   * Get employee statutory eligibility profile.
   * @param {string} employeeId
   */
  async get(employeeId) {
    const res = await apiClient.get(`${BASE_PATH}/${employeeId}/statutory-profile`);
    return res.data;
  },

  /**
   * Update / upsert employee statutory eligibility profile.
   * @param {string} employeeId
   * @param {Object} body StatutoryProfileRequest
   */
  async save(employeeId, body) {
    const res = await apiClient.put(`${BASE_PATH}/${employeeId}/statutory-profile`, body);
    return res.data;
  },
};
