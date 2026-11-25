import { apiClient } from '@shared/api/client.js';

const reportsPath = (payrunId) => `/v1/payroll/payruns/${payrunId}/reports/pf-ecr`;

/**
 * Service for monthly EPFO ECR 2.0 preview and file exports (W-87).
 */
export const pfEcrService = {
  /**
   * Fetches ECR summary, warnings, and employee rows.
   * @param {string} payrunId
   */
  async getPreview(payrunId) {
    const res = await apiClient.get(`${reportsPath(payrunId)}/preview`);
    return res?.data?.data || res?.data || res;
  },

  /**
   * Downloads ECR file as Blob.
   * @param {string} payrunId
   * @param {'TXT' | 'XLSX' | 'CSV'} format
   */
  async downloadFile(payrunId, format = 'TXT') {
    const res = await apiClient.get(`${reportsPath(payrunId)}/export`, {
      params: { format },
      responseType: 'blob',
    });
    return res?.data || res;
  },
};

