import { apiClient } from '@shared/api/client.js';

export const ptService = {
  async list() {
    const res = await apiClient.get('/v1/payroll/settings/professional-tax');
    return res.data?.data ?? res.data;
  },

  async get(stateCode) {
    const res = await apiClient.get(`/v1/payroll/settings/professional-tax/${stateCode}`);
    return res.data?.data ?? res.data;
  },

  async override(stateCode, payload) {
    const res = await apiClient.put(`/v1/payroll/settings/professional-tax/${stateCode}`, payload);
    return res.data?.data ?? res.data;
  },

  async removeOverride(stateCode) {
    const res = await apiClient.delete(`/v1/payroll/settings/professional-tax/${stateCode}/override`);
    return res.data?.data ?? res.data;
  },

  async history(stateCode) {
    const res = await apiClient.get(`/v1/payroll/settings/professional-tax/${stateCode}/history`);
    return res.data?.data ?? res.data;
  },
};
