import { apiClient } from '@shared/api/client';

/**
 * Service for administrative Income Tax Declaration window settings per financial year.
 */
export const taxSettingsService = {
  /**
   * Fetches window configuration for a financial year.
   *
   * @param {string} fy e.g. '2026-27'
   * @returns {Promise<Object>} TaxDeclarationWindowResponse
   */
  async get(fy) {
    const res = await apiClient.get(`/api/v1/payroll/tax-declaration/settings/${encodeURIComponent(fy)}`);
    return res.data.data;
  },

  /**
   * Saves/updates window configuration for a financial year.
   *
   * @param {string} fy e.g. '2026-27'
   * @param {Object} body TaxDeclarationWindowRequest
   * @returns {Promise<Object>} TaxDeclarationWindowResponse
   */
  async save(fy, body) {
    const res = await apiClient.put(`/api/v1/payroll/tax-declaration/settings/${encodeURIComponent(fy)}`, body);
    return res.data.data;
  },
};
