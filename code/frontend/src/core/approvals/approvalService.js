import { apiClient } from '@shared/api/client.js';

export const approvalService = {
  async pending(page = 0, size = 20) {
    const res = await apiClient.get('/v1/approvals/pending', {
      params: { page, size },
    });
    return res?.data !== undefined ? res.data : res;
  },

  async get(instanceId) {
    const res = await apiClient.get(`/v1/approvals/${instanceId}`);
    return res?.data !== undefined ? res.data : res;
  },

  async history(instanceId) {
    const res = await apiClient.get(`/v1/approvals/${instanceId}/history`);
    return res?.data !== undefined ? res.data : res;
  },

  async decide(stepId, body) {
    const res = await apiClient.post(`/v1/approvals/steps/${stepId}/decide`, body);
    return res?.data !== undefined ? res.data : res;
  },

  async reassign(instanceId, body) {
    const res = await apiClient.post(`/v1/approvals/${instanceId}/reassign`, body);
    return res?.data !== undefined ? res.data : res;
  },
};
