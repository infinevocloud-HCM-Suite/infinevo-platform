import { apiClient } from '@shared/api/client.js';

/**
 * Service for public invitation acceptance and decline (W-24.2 / W-46.7).
 * The token is sent in the body; no Authorization header is attached.
 * Accepting carries the password the invitee chose (D-88); the server sets it and the reply's `outcome`
 * says what to do next.
 */
export const publicInvitationService = {
  async accept(token, password) {
    const res = await apiClient.post('/v1/invitations/accept', { token, password });
    return res?.data ?? res;
  },

  async decline(token, reason) {
    const res = await apiClient.post('/v1/invitations/decline', { token, reason });
    return res?.data ?? res;
  },
};
