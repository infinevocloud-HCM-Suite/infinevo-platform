import { apiClient } from '@shared/api/client.js';

export const fbpService = {
  async plan() {
    const res = await apiClient.get('/v1/payroll/fbp/plan');
    return res.data?.data ?? res.data;
  },

  async savePlan(payload) {
    const res = await apiClient.put('/v1/payroll/fbp/plan', payload);
    return res.data?.data ?? res.data;
  },

  async lock() {
    const res = await apiClient.post('/v1/payroll/fbp/plan/lock');
    return res.data?.data ?? res.data;
  },

  async unlock() {
    const res = await apiClient.post('/v1/payroll/fbp/plan/unlock');
    return res.data?.data ?? res.data;
  },

  async components() {
    const res = await apiClient.get('/v1/payroll/fbp/components');
    return res.data?.data ?? res.data;
  },

  async declaration(employeeId, asOf) {
    const res = await apiClient.get(`/v1/payroll/employees/${employeeId}/fbp-declaration`, {
      params: asOf ? { asOf } : undefined,
    });
    return res.data?.data ?? res.data;
  },

  async setDeclaration(employeeId, payload) {
    const body = Array.isArray(payload) ? { lines: payload } : payload;
    const res = await apiClient.put(`/v1/payroll/employees/${employeeId}/fbp-declaration`, body);
    return res.data?.data ?? res.data;
  },
};
