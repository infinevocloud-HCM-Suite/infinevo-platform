import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/settings/lwf';

export const lwfService = {
  async list() {
    const res = await apiClient.get(BASE_PATH);
    return res.data?.data || res.data || [];
  },

  async get(stateCode) {
    const res = await apiClient.get(`${BASE_PATH}/${stateCode}`);
    return res.data?.data || res.data;
  },

  async update(stateCode, payload) {
    const res = await apiClient.put(`${BASE_PATH}/${stateCode}`, payload);
    return res.data?.data || res.data;
  },
};

