import { apiClient } from '@shared/api/client.js';

export const guidedStatutoryService = {
  async getReadiness() {
    const res = await apiClient.get('/v1/payroll/settings/guided-setup');
    return res.data?.data ?? res.data;
  },

  async applySetup(payload) {
    const res = await apiClient.post('/v1/payroll/settings/guided-setup', payload);
    return res.data?.data ?? res.data;
  },
};

