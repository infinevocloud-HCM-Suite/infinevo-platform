import { apiClient } from '@shared/api/client.js';

/**
 * Service for employee invitations (W-24.2 / W-46.7).
 */
export const employeeInvitationService = {
  async list(params = {}) {
    const query = {};
    if (params.status) query.status = params.status;
    if (params.employeeId) query.employeeId = params.employeeId;
    const res = await apiClient.get('/v1/employee-invitations', { params: query });
    return Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
  },

  /** roleIds are the extra roles; the server grants `employee` itself on accept (W-73.3). */
  async create({ employeeId, roleIds = [] }) {
    const res = await apiClient.post('/v1/employee-invitations', { employeeId, roleIds });
    return res?.data ?? res;
  },

  async resend(id) {
    const res = await apiClient.post(`/v1/employee-invitations/${id}/resend`);
    return res?.data ?? res;
  },

  async revoke(id) {
    const res = await apiClient.post(`/v1/employee-invitations/${id}/revoke`);
    return res?.data !== undefined ? res.data : res;
  },
};
