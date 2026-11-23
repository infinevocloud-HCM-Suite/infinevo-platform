import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/settlement/fnf';

function unwrap(res) {
  return res.data?.data !== undefined ? res.data.data : res.data;
}

export const fnfService = {
  /**
   * Lists all F&F settlements in the tenant.
   */
  async listSettlements() {
    const res = await apiClient.get(BASE_PATH);
    return unwrap(res);
  },

  /**
   * Retrieves a single F&F settlement by ID.
   */
  async getSettlement(id) {
    const res = await apiClient.get(`${BASE_PATH}/${id}`);
    return unwrap(res);
  },

  /**
   * Retrieves all F&F settlements for a specific employee.
   */
  async getSettlementsByEmployee(employeeId) {
    const res = await apiClient.get(`${BASE_PATH}/employee/${employeeId}`);
    return unwrap(res);
  },

  /**
   * Initiates and computes a consolidated F&F settlement draft.
   */
  async initiateSettlement(data) {
    const res = await apiClient.post(`${BASE_PATH}/initiate`, data);
    return unwrap(res);
  },

  /**
   * Approves an F&F settlement in DRAFT status.
   */
  async approveSettlement(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/approve`);
    return unwrap(res);
  },

  /**
   * Marks an approved F&F settlement as PAID.
   */
  async paySettlement(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/pay`);
    return unwrap(res);
  },
};
