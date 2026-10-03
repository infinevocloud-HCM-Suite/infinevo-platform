import { apiClient } from '@shared/api/client.js';

export const statutoryService = {
  async get(kind) {
    const res = await apiClient.get(`/v1/payroll/settings/${kind}`);
    return res.data?.data ?? res.data;
  },

  async save(kind, payload) {
    const res = await apiClient.put(`/v1/payroll/settings/${kind}`, payload);
    return res.data?.data ?? res.data;
  },
};
