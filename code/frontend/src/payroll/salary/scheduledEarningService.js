import { apiClient } from '@shared/api/client.js';

const EMPLOYEE_PATH = '/v1/payroll/employees';
const ROW_PATH = '/v1/payroll/scheduled-earnings';

/**
 * Scheduled earnings API (W-73.6 §4): list and add on the employee, pause / resume / cancel on the row.
 * Backend: code/backend/payroll/src/main/java/com/infinevo/payroll/scheduled/ScheduledEarningController.java
 */
export const scheduledEarningService = {
  /** @param {string} employeeId */
  async list(employeeId) {
    const res = await apiClient.get(`${EMPLOYEE_PATH}/${employeeId}/scheduled-earnings`);
    return res.data;
  },

  /**
   * @param {string} employeeId
   * @param {{ componentId: string, amount: string, firstPeriod: string, instalments: number, reason?: string }} body
   */
  async create(employeeId, body) {
    const res = await apiClient.post(`${EMPLOYEE_PATH}/${employeeId}/scheduled-earnings`, body);
    return res.data;
  },

  /** @param {string} id @param {string} [reason] */
  async pause(id, reason) {
    const res = await apiClient.post(`${ROW_PATH}/${id}/pause`, { reason: reason || null });
    return res.data;
  },

  /** @param {string} id */
  async resume(id) {
    const res = await apiClient.post(`${ROW_PATH}/${id}/resume`, {});
    return res.data;
  },

  /** @param {string} id @param {string} [reason] */
  async cancel(id, reason) {
    const res = await apiClient.post(`${ROW_PATH}/${id}/cancel`, { reason: reason || null });
    return res.data;
  },
};
