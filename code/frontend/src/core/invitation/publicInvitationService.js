import { apiClient } from '@shared/api/client.js';

/**
 * Service for public invitation acceptance and decline (W-24.2 / W-46.7).
 * The token is sent in the body; no Authorization header is attached.
 */
export const publicInvitationService = {
  async accept(token) {
    const res = await apiClient.post('/v1/invitations/accept', { token });
    return res?.data ?? res;
  },

  async decline(token, reason) {
    const res = await apiClient.post('/v1/invitations/decline', { token, reason });
    return res?.data ?? res;
  },
};
