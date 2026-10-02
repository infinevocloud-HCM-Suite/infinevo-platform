import { apiClient } from '../../shared/api/client.js';

export const reportingLineService = {
  async lines(id, asOf) {
    const params = asOf ? { asOf } : undefined;
    const res = await apiClient.get(`/v1/employees/${id}/reporting-line`, { params });
    return res.data;
  },

  async managerChain(id, asOf) {
    const params = asOf ? { asOf } : undefined;
    const res = await apiClient.get(`/v1/employees/${id}/manager-chain`, { params });
    return res.data;
  },

  async set(id, body) {
    const res = await apiClient.put(`/v1/employees/${id}/reporting-line`, body);
    return res.data;
  },
};
