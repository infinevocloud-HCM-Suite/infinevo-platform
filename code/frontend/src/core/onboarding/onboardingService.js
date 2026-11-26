import { apiClient } from '../../shared/api/client.js';

function unwrap(res) {
  return res?.data !== undefined ? res.data : res;
}

export const onboardingService = {
  async getTemplates() {
    const res = await apiClient.get('/v1/onboarding/templates');
    return unwrap(res);
  },

  async createTemplate(data) {
    const res = await apiClient.post('/v1/onboarding/templates', data);
    return unwrap(res);
  },

  async updateTemplate(id, data) {
    const res = await apiClient.put(`/v1/onboarding/templates/${id}`, data);
    return unwrap(res);
  },

  async deleteTemplate(id) {
    const res = await apiClient.delete(`/v1/onboarding/templates/${id}`);
    return unwrap(res);
  },

  async getEmployeeProgress(employeeId) {
    const res = await apiClient.get(`/v1/employees/${employeeId}/onboarding`);
    return unwrap(res);
  },

  async assignTasks(employeeId) {
    const res = await apiClient.post(`/v1/employees/${employeeId}/onboarding/assign`);
    return unwrap(res);
  },

  async updateTask(employeeId, taskId, data) {
    const res = await apiClient.patch(`/v1/employees/${employeeId}/onboarding/${taskId}`, data);
    return unwrap(res);
  },

  async getMyProgress() {
    const res = await apiClient.get('/v1/me/onboarding');
    return unwrap(res);
  },
};

