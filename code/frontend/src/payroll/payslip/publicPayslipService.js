import { apiClient } from '@shared/api/client.js';

/**
 * Service for public signed payslip access (W-36.2 / Defect #7 R-03).
 * Fetches the payslip via the anonymous open endpoint without bearer auth.
 */
export const publicPayslipService = {
  async getOpenPayslip(token) {
    if (!token) {
      throw new Error('This link is not valid or has expired');
    }
    const res = await apiClient.get('/v1/payroll/payslips/open', {
      params: { t: token },
    });
    return res?.data?.data ?? res?.data;
  },
};
