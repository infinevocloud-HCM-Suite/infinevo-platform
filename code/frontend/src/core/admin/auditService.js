import { apiClient } from '@shared/api/client.js';

/**
 * `GET /v1/audit` (W-22.1) - the bound tenant's trail, so it reads a customer tenant only while
 * a session for it is live and the client sends `X-Impersonation` (W-65.3 §5).
 *
 * params: entity, entityId, actor, from, to (ISO instants), page (0-based), size (max 200).
 * Bare Spring page: `{ content: [AuditLogView], totalElements, number, size, ... }`.
 */
export const auditService = {
  async search(params = {}) {
    const query = {};
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') query[key] = value;
    });
    const res = await apiClient.get('/v1/audit', { params: query });
    return res?.data !== undefined ? res.data : res;
  },
};
