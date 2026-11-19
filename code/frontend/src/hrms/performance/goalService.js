import { apiClient } from '../../shared/api/client.js';

/**
 * API client service for employee goals and OKR key results (W-106.2).
 */
export const goalService = {
  async getMyGoals() {
    const res = await apiClient.get('/v1/hrms/goals/me');
    return res.data !== undefined ? res.data : res;
  },

  async createGoal(data) {
    const res = await apiClient.post('/v1/hrms/goals/me', data);
    return res.data !== undefined ? res.data : res;
  },

  async editGoal(id, data) {
    const res = await apiClient.patch(`/v1/hrms/goals/me/${id}`, data);
    return res.data !== undefined ? res.data : res;
  },

  async startGoal(id) {
    const res = await apiClient.post(`/v1/hrms/goals/me/${id}/start`);
    return res.data !== undefined ? res.data : res;
  },

  async cancelGoal(id) {
    const res = await apiClient.post(`/v1/hrms/goals/me/${id}/cancel`);
    return res.data !== undefined ? res.data : res;
  },

  async checkIn(id, data) {
    const res = await apiClient.post(`/v1/hrms/goals/me/${id}/check-ins`, data);
    return res.data !== undefined ? res.data : res;
  },

  async getCheckIns(id) {
    const res = await apiClient.get(`/v1/hrms/goals/${id}/check-ins`);
    return res.data !== undefined ? res.data : res;
  },

  async getTeamGoals() {
    const res = await apiClient.get('/v1/hrms/goals/team');
    return res.data !== undefined ? res.data : res;
  },

  async getGoalsForEmployee(employeeId, cycleId) {
    const query = cycleId ? `?cycleId=${encodeURIComponent(cycleId)}` : '';
    const res = await apiClient.get(`/v1/hrms/goals/employee/${employeeId}${query}`);
    return res.data !== undefined ? res.data : res;
  },

  async getGoalsForCycle(cycleId) {
    const res = await apiClient.get(`/v1/hrms/goals/cycle/${cycleId}`);
    return res.data !== undefined ? res.data : res;
  },
};
