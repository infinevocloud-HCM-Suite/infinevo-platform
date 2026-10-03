import { apiClient } from '@shared/api/client.js';

/**
 * Opening and closing an impersonation session (W-65.2 §4, W-65.3 §5).
 *
 * Both endpoints require the caller to be bound to the platform tenant, so neither may carry
 * `X-Impersonation` - closing in particular happens while a session is live.
 *
 * open: `POST /v1/tenants/{id}/impersonations` with `{ email | userAccountId, reason }`; neither
 * target opens a bootstrap session, which the server allows only while the tenant has no user
 * accounts. Bare reply `{ sessionId, tenantId, userAccountId, userEmail, expiresAt }`.
 * close: `DELETE /v1/impersonations/{sessionId}`, `204`.
 */
const PLATFORM_ONLY = { skipImpersonation: true };

export const impersonationService = {
  /**
   * @param {string} tenantId target tenant
   * @param {{ email?: string, userAccountId?: string } | null} target the user to act as; null for bootstrap
   * @param {string} reason why, 1-200 characters
   */
  async open(tenantId, target, reason) {
    const body = { reason };
    if (target?.userAccountId) body.userAccountId = target.userAccountId;
    else if (target?.email) body.email = target.email.trim();
    const res = await apiClient.post(`/v1/tenants/${tenantId}/impersonations`, body, PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

  async close(sessionId) {
    await apiClient.delete(`/v1/impersonations/${sessionId}`, PLATFORM_ONLY);
  },
};
