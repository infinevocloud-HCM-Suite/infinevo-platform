import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/components';

/**
 * Salary components API service (W-47.1a §5, W-26.1).
 * Supports earnings, deductions, benefits, and reimbursements.
 */
export const componentService = {
  /**
   * List components of a specific kind, optionally filtering by activeOnly.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {{ activeOnly?: boolean }} [params]
   */
  async list(kind, params = {}) {
    const res = await apiClient.get(`${BASE_PATH}/${kind}`, { params });
    return res.data;
  },

  /**
   * Get a single component by ID.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {string} id
   */
  async get(kind, id) {
    const res = await apiClient.get(`${BASE_PATH}/${kind}/${id}`);
    return res.data;
  },

  /**
   * Create a new component.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {Object} body
   */
  async create(kind, body) {
    const res = await apiClient.post(`${BASE_PATH}/${kind}`, body);
    return res.data;
  },

  /**
   * Update an existing component.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {string} id
   * @param {Object} body
   */
  async update(kind, id, body) {
    const res = await apiClient.put(`${BASE_PATH}/${kind}/${id}`, body);
    return res.data;
  },

  /**
   * Toggle active status of a component.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {string} id
   * @param {boolean} active
   */
  async setActive(kind, id, active) {
    const res = await apiClient.put(`${BASE_PATH}/${kind}/${id}/active`, { active });
    return res.data;
  },

  /**
   * Soft-delete a component.
   * @param {'earnings'|'deductions'|'benefits'|'reimbursements'} kind
   * @param {string} id
   */
  async remove(kind, id) {
    const res = await apiClient.delete(`${BASE_PATH}/${kind}/${id}`);
    return res.data;
  },
};
