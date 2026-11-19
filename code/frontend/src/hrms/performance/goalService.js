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

  async updateProgress(id, data) {
    const res = await apiClient.patch(`/v1/hrms/goals/me/${id}/progress`, data);
    return res.data !== undefined ? res.data : res;
  },

  async getTeamGoals() {
    const res = await apiClient.get('/v1/hrms/goals/team');
    return res.data !== undefined ? res.data : res;
  },

  async getGoalsForEmployee(employeeId) {
    const res = await apiClient.get(`/v1/hrms/goals/employee/${employeeId}`);
    return res.data !== undefined ? res.data : res;
  },

  async getGoalsForCycle(cycleId) {
    const res = await apiClient.get(`/v1/hrms/goals/cycle/${cycleId}`);
    return res.data !== undefined ? res.data : res;
  },
};

