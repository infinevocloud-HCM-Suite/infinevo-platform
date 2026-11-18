import { apiClient } from '../../shared/api/client.js';

/**
 * API client service for HRMS performance review cycles and appraisals (W-106.1).
 */
export const performanceService = {
  async listCycles() {
    const res = await apiClient.get('/v1/performance/cycles');
    return res.data !== undefined ? res.data : res;
  },

  async createCycle(data) {
    const res = await apiClient.post('/v1/performance/cycles', data);
    return res.data !== undefined ? res.data : res;
  },

  async getCycle(id) {
    const res = await apiClient.get(`/v1/performance/cycles/${id}`);
    return res.data !== undefined ? res.data : res;
  },

  async activateCycle(id) {
    const res = await apiClient.post(`/v1/performance/cycles/${id}/activate`);
    return res.data !== undefined ? res.data : res;
  },

  async closeCycle(id) {
    const res = await apiClient.post(`/v1/performance/cycles/${id}/close`);
    return res.data !== undefined ? res.data : res;
  },

  async getCycleSummary(id) {
    const res = await apiClient.get(`/v1/performance/cycles/${id}/summary`);
    return res.data !== undefined ? res.data : res;
  },

  async getMyReviews() {
    const res = await apiClient.get('/v1/performance/reviews/my');
    return res.data !== undefined ? res.data : res;
  },

  async submitSelfReview(id, data) {
    const res = await apiClient.patch(`/v1/performance/reviews/${id}/self-submit`, data);
    return res.data !== undefined ? res.data : res;
  },

  async getTeamReviews() {
    const res = await apiClient.get('/v1/performance/reviews/team');
    return res.data !== undefined ? res.data : res;
  },

  async submitManagerReview(id, data) {
    const res = await apiClient.patch(`/v1/performance/reviews/${id}/manager-submit`, data);
    return res.data !== undefined ? res.data : res;
  },
};

