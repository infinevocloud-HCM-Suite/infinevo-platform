import { apiClient } from '@shared/api/client.js';

export const surveyService = {
  async list(status) {
    const params = status ? { status } : {};
    const res = await apiClient.get('/v1/hrms/surveys', { params });
    return res?.data?.data || res?.data || [];
  },

  async create(payload) {
    const res = await apiClient.post('/v1/hrms/surveys', payload);
    return res?.data?.data || res?.data;
  },

  async get(id) {
    const res = await apiClient.get(`/v1/hrms/surveys/${id}`);
    return res?.data?.data || res?.data;
  },

  async updateStatus(id, status) {
    const res = await apiClient.patch(`/v1/hrms/surveys/${id}/status`, { status });
    return res?.data?.data || res?.data;
  },

  async getResults(id) {
    const res = await apiClient.get(`/v1/hrms/surveys/${id}/results`);
    return res?.data?.data || res?.data;
  },

  async listActive() {
    const res = await apiClient.get('/v1/me/surveys/active');
    return res?.data?.data || res?.data || [];
  },

  async getForTaking(id) {
    const res = await apiClient.get(`/v1/me/surveys/${id}`);
    return res?.data?.data || res?.data;
  },

  async submit(id, answers) {
    const res = await apiClient.post(`/v1/me/surveys/${id}/submit`, { answers });
    return res?.data?.data || res?.data;
  },
};

