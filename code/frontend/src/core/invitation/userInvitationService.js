import { apiClient } from '@shared/api/client.js';

/**
 * Service for company user invitations (W-24.2 / W-46.7).
 */
export const userInvitationService = {
  async list(params = {}) {
    const query = {};
    if (params.status) query.status = params.status;
    const res = await apiClient.get('/v1/user-invitations', { params: query });
    return Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
  },

  async create(body) {
    const res = await apiClient.post('/v1/user-invitations', body);
    return res?.data ?? res;
  },

  async resend(id) {
    const res = await apiClient.post(`/v1/user-invitations/${id}/resend`);
    return res?.data ?? res;
  },

  async revoke(id) {
    const res = await apiClient.post(`/v1/user-invitations/${id}/revoke`);
    return res?.data !== undefined ? res.data : res;
  },
};
