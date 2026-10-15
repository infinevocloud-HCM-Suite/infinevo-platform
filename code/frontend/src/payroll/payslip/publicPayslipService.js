import { apiClient } from '@shared/api/client.js';

/**
 * Service for public, unauthenticated payslip viewing and download (W-36.2 §4).
 * Calls anonymous GET /api/v1/payroll/payslips/open?t=<token>.
 * No Authorization header is attached.
 */
export const publicPayslipService = {
  /**
   * Reads a payslip by signed link token.
   *
   * @param {string} token the signed link token
   * @returns {Promise<Object>} PayslipResponse data
   */
  async getPayslip(token) {
    if (!token || typeof token !== 'string' || !token.trim()) {
      throw new Error('This link is not valid or has expired');
    }
    const res = await apiClient.get('/v1/payroll/payslips/open', {
      params: { t: token.trim() },
    });
    const body = res?.data !== undefined ? res.data : res;
    return body && typeof body === 'object' && 'data' in body ? body.data : body;
  },

  /**
   * Alias for getPayslip.
   */
  async getOpenPayslip(token) {
    return this.getPayslip(token);
  },
};
