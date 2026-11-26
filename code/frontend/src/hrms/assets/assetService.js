import { apiClient } from '@shared/api/client.js';

export const assetService = {
  async list(status, category) {
    const params = {};
    if (status) params.status = status;
    if (category) params.category = category;
    const res = await apiClient.get('/v1/hrms/assets', { params });
    return res?.data?.data || res?.data || [];
  },

  async create(payload) {
    const res = await apiClient.post('/v1/hrms/assets', payload);
    return res?.data?.data || res?.data;
  },

  async get(id) {
    const res = await apiClient.get(`/v1/hrms/assets/${id}`);
    return res?.data?.data || res?.data;
  },

  async update(id, payload) {
    const res = await apiClient.put(`/v1/hrms/assets/${id}`, payload);
    return res?.data?.data || res?.data;
  },

  async updateStatus(id, { status, condition, notes }) {
    const res = await apiClient.patch(`/v1/hrms/assets/${id}/status`, { status, condition, notes });
    return res?.data?.data || res?.data;
  },

  async assign(id, { employeeId, assignedDate, condition, notes }) {
    const res = await apiClient.post(`/v1/hrms/assets/${id}/assign`, {
      employeeId,
      assignedDate,
      condition,
      notes,
    });
    return res?.data?.data || res?.data;
  },

  async returnAsset(id, { returnedDate, condition, notes }) {
    const res = await apiClient.post(`/v1/hrms/assets/${id}/return`, {
      returnedDate,
      condition,
      notes,
    });
    return res?.data?.data || res?.data;
  },

  async getHistory(id) {
    const res = await apiClient.get(`/v1/hrms/assets/${id}/history`);
    return res?.data?.data || res?.data || [];
  },

  async getEmployeeClearance(employeeId) {
    const res = await apiClient.get(`/v1/hrms/employees/${employeeId}/assets`);
    return res?.data?.data || res?.data;
  },

  async getMyAssets() {
    const res = await apiClient.get('/v1/me/assets');
    return res?.data?.data || res?.data || [];
  },
};

