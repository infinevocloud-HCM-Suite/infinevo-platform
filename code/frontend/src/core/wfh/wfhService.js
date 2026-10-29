import { apiClient } from '../../shared/api/client.js';

export const wfhService = {
  // Employee Self-Service
  async getMyRequests() {
    const res = await apiClient.get('/v1/me/wfh-requests');
    return res?.data !== undefined ? res.data : res;
  },

  async getMyBalance(asOf) {
    const params = asOf ? { asOf } : {};
    const res = await apiClient.get('/v1/me/wfh-balance', { params });
    return res?.data !== undefined ? res.data : res;
  },

  async apply(data) {
    const res = await apiClient.post('/v1/me/wfh-requests', data);
    return res?.data !== undefined ? res.data : res;
  },

  async withdraw(id, reason) {
    const res = await apiClient.post(`/v1/wfh-requests/${encodeURIComponent(id)}/withdraw`, { reason });
    return res?.data !== undefined ? res.data : res;
  },

  async cancel(id, reason) {
    const res = await apiClient.post(`/v1/wfh-requests/${encodeURIComponent(id)}/cancel`, { reason });
    return res?.data !== undefined ? res.data : res;
  },

  // Management: Policies
  async getPolicies() {
    const res = await apiClient.get('/v1/wfh-policies');
    return res?.data !== undefined ? res.data : res;
  },

  async getDefaultPolicy() {
    const res = await apiClient.get('/v1/wfh-policies/default');
    return res?.data !== undefined ? res.data : res;
  },

  async createPolicy(data) {
    const res = await apiClient.post('/v1/wfh-policies', data);
    return res?.data !== undefined ? res.data : res;
  },

  async updatePolicy(id, data) {
    const res = await apiClient.put(`/v1/wfh-policies/${encodeURIComponent(id)}`, data);
    return res?.data !== undefined ? res.data : res;
  },

  // Management: Allocations
  async getAllocations(params = {}) {
    const res = await apiClient.get('/v1/wfh-allocations', { params });
    return res?.data !== undefined ? res.data : res;
  },

  async createAllocation(data) {
    const res = await apiClient.post('/v1/wfh-allocations', data);
    return res?.data !== undefined ? res.data : res;
  },

  // Management: Requests
  async getAllRequests(status) {
    const params = status ? { status } : {};
    const res = await apiClient.get('/v1/wfh-requests', { params });
    return res?.data !== undefined ? res.data : res;
  },

  async applyOnBehalf(data) {
    const res = await apiClient.post('/v1/wfh-requests/on-behalf', data);
    return res?.data !== undefined ? res.data : res;
  },

  async getRequestById(id) {
    const res = await apiClient.get(`/v1/wfh-requests/${encodeURIComponent(id)}`);
    return res?.data !== undefined ? res.data : res;
  },

  // Management: Import
  async downloadTemplate() {
    const res = await apiClient.get('/v1/wfh-imports/template', { responseType: 'blob' });
    return res?.data !== undefined ? res.data : res;
  },

  async uploadImport(formData) {
    const res = await apiClient.post('/v1/wfh-imports', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res?.data !== undefined ? res.data : res;
  },

  async getImportLogs() {
    const res = await apiClient.get('/v1/wfh-imports');
    return res?.data !== undefined ? res.data : res;
  },
};
