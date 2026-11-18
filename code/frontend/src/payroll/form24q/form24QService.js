import { apiClient } from '@shared/api/client';
import { fyForApi } from '../tax/financialYear';

/**
 * Service for Form 24Q TDS compliance and Challan management (W-89.1, W-89.2, W-89.3, W-89.4).
 *
 * Backend baseURL is '/api', so endpoints start with '/v1/...'.
 * Financial years are normalized via fyForApi() ('YYYY-YY' -> 'YYYY-YYYY').
 */
export const form24QService = {
  /**
   * List recorded TDS challans for a financial year and optional quarter.
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @param {string} [quarter] - Quarter ('Q1', 'Q2', 'Q3', 'Q4')
   * @returns {Promise<Array>} List of TdsChallanResponse
   */
  async listChallans(fy, quarter) {
    const params = { financialYear: fyForApi(fy) };
    if (quarter) {
      params.quarter = quarter;
    }
    const res = await apiClient.get('/v1/payroll/compliance/challans', { params });
    return res.data?.data ?? [];
  },

  /**
   * Record a new ITNS 281 TDS Challan.
   * @param {Object} payload - CreateTdsChallanRequest
   * @returns {Promise<Object>} TdsChallanResponse
   */
  async createChallan(payload) {
    const body = {
      ...payload,
      financialYear: fyForApi(payload.financialYear),
    };
    const res = await apiClient.post('/v1/payroll/compliance/challans', body);
    return res.data?.data;
  },

  /**
   * Allocate TDS challan to pay run lines.
   * @param {string} challanId - UUID of the challan
   * @param {Object} payload - AllocateChallanRequest
   * @returns {Promise<Object>} Allocation result
   */
  async allocateChallan(challanId, payload) {
    const res = await apiClient.post(
      `/v1/payroll/compliance/challans/${encodeURIComponent(challanId)}/allocations`,
      payload
    );
    return res.data?.data;
  },

  /**
   * Get preview and pre-flight reconciliation metrics for Annexure I.
   * @param {string} fy - Financial year
   * @param {string} quarter - Quarter ('Q1', 'Q2', 'Q3', 'Q4')
   * @returns {Promise<Object>} Annexure1PreviewResponse
   */
  async getAnnexure1Preview(fy, quarter) {
    const res = await apiClient.get(
      `/v1/payroll/compliance/24q/${encodeURIComponent(fyForApi(fy))}/${encodeURIComponent(quarter)}/annexure-1`
    );
    return res.data?.data;
  },

  /**
   * Export Annexure I in CSV or e-TDS TXT format.
   * @param {string} fy - Financial year
   * @param {string} quarter - Quarter ('Q1', 'Q2', 'Q3', 'Q4')
   * @param {'CSV'|'TXT'} [format='CSV'] - Export format
   * @returns {Promise<Blob>}
   */
  async exportAnnexure1(fy, quarter, format = 'CSV') {
    const res = await apiClient.get(
      `/v1/payroll/compliance/24q/${encodeURIComponent(fyForApi(fy))}/${encodeURIComponent(quarter)}/annexure-1/export`,
      {
        params: { format },
        responseType: 'blob',
      }
    );
    return res.data;
  },

  /**
   * Get preview and annual statutory summary for Annexure II (Q4).
   * @param {string} fy - Financial year
   * @returns {Promise<Object>} Annexure2PreviewResponse
   */
  async getAnnexure2Preview(fy) {
    const res = await apiClient.get(
      `/v1/payroll/compliance/24q/${encodeURIComponent(fyForApi(fy))}/annexure-2`
    );
    return res.data?.data;
  },

  /**
   * Export Annexure II in CSV or e-TDS TXT format (Q4 only).
   * @param {string} fy - Financial year
   * @param {'CSV'|'TXT'} [format='CSV'] - Export format
   * @returns {Promise<Blob>}
   */
  async exportAnnexure2(fy, format = 'CSV') {
    const res = await apiClient.get(
      `/v1/payroll/compliance/24q/${encodeURIComponent(fyForApi(fy))}/annexure-2/export`,
      {
        params: { format },
        responseType: 'blob',
      }
    );
    return res.data;
  },
};
