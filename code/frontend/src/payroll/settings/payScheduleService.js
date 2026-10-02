import { apiClient } from '@shared/api/client.js';

export const payScheduleService = {
  async get() {
    const res = await apiClient.get('/v1/payroll/pay-schedule');
    return res.data?.data ?? res.data;
  },

  async save(payload) {
    const res = await apiClient.put('/v1/payroll/pay-schedule', payload);
    return res.data?.data ?? res.data;
  },

  async period(period) {
    const res = await apiClient.get('/v1/payroll/pay-schedule/period', {
      params: { period },
    });
    return res.data?.data ?? res.data;
  },
};
