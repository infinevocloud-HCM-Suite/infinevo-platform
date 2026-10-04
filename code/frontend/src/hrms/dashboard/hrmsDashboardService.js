import { apiClient } from '@shared/api/client';

/**
 * The HRMS dashboard read (W-48.6 §5). The reply is the `{ status, message, data }` envelope
 * (HrmsDashboardController.java:40), so the payload is `res.data.data`.
 */
export const hrmsDashboardService = {
  async summary() {
    const res = await apiClient.get('/v1/hrms/dashboard');
    return res.data.data;
  },
};
