import { apiClient } from '@shared/api/client';
import { fyForApi } from './financialYear';

/**
 * Service for administrative Income Tax Declaration window settings per financial year.
 *
 * B-4 fix: apiClient base URL is already '/api'. Using '/v1/' prevents double '/api/api/v1/...' 404s.
 * B-3 fix: Formats display financial year (e.g. '2026-27') to backend API format ('2026-2027') using fyForApi.
 */
export const taxSettingsService = {
  /**
   * Fetches window configuration for a financial year.
   *
   * @param {string} fy e.g. '2026-27'
   * @returns {Promise<Object>} TaxDeclarationWindowResponse
   */
  async get(fy) {
    const res = await apiClient.get(`/v1/payroll/tax-declaration/settings/${encodeURIComponent(fyForApi(fy))}`);
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
    const res = await apiClient.put(`/v1/payroll/tax-declaration/settings/${encodeURIComponent(fyForApi(fy))}`, body);
    return res.data.data;
  },
};
