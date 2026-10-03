import { apiClient } from '@shared/api/client.js';

export const leaveRequestService = {
  async list(params) {
    const res = await apiClient.get('/v1/leave-requests', { params });
    return res?.data !== undefined ? res.data : res;
  },

  async get(id) {
    const res = await apiClient.get(`/v1/leave-requests/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async onBehalf(body) {
    const res = await apiClient.post('/v1/leave-requests/on-behalf', body);
    return res?.data !== undefined ? res.data : res;
  },

  async withdraw(id, reason) {
    const res = await apiClient.post(`/v1/leave-requests/${id}/withdraw`, reason ? { reason } : {});
    return res?.data !== undefined ? res.data : res;
  },

  async cancel(id, reason) {
    const res = await apiClient.post(`/v1/leave-requests/${id}/cancel`, reason ? { reason } : {});
    return res?.data !== undefined ? res.data : res;
  },

  async create(body) {
    const res = await apiClient.post('/v1/leave-requests', body);
    return res?.data !== undefined ? res.data : res;
  },

  async submit(id) {
    const res = await apiClient.post(`/v1/leave-requests/${id}/submit`);
    return res?.data !== undefined ? res.data : res;
  },
};
