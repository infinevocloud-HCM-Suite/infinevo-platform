import { apiClient } from '@shared/api/client.js';

/**
 * Roles, read only (D-73). Both endpoints need `core.role.read`.
 */
export const rolesService = {
  /** `GET /v1/roles` - `[RoleResponse]`: id, tenantId, code, name, system, actionCodes (sorted), createdAt, updatedAt. */
  async list() {
    const res = await apiClient.get('/v1/roles');
    return Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
  },

  /** `GET /v1/actions` - the catalogue, `[ActionResponse]`: code, name, module, description. */
  async actions() {
    const res = await apiClient.get('/v1/actions');
    return Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
  },
};
