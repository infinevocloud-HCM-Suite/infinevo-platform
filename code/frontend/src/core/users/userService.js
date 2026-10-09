import { apiClient } from '@shared/api/client.js';

/**
 * Users & access (W-73.4 §5): the tenant's accounts, their roles, Disable and Enable.
 */
export const userService = {
  /** `GET /v1/users` — `[UserView]`: id, email, displayName, roles[{id, code, name}], employeeId, employeeNumber, enabled. */
  async list(params = {}) {
    const query = {};
    if (params.q) query.q = params.q;
    const res = await apiClient.get('/v1/users', { params: query });
    return Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
  },

  /** `PUT /v1/users/{id}/roles` — the complete role set; needs `core.role.assign`. */
  async setRoles(id, roleIds) {
    const res = await apiClient.put(`/v1/users/${id}/roles`, { roleIds });
    return res?.data ?? res;
  },

  async disable(id) {
    await apiClient.post(`/v1/users/${id}/disable`);
  },

  async enable(id) {
    await apiClient.post(`/v1/users/${id}/enable`);
  },

  /** The tenant's roles, for the pickers. `platform-admin` is never grantable from a tenant, so it is left out. */
  async roles() {
    const res = await apiClient.get('/v1/roles');
    const data = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
    return data.filter((r) => r.code !== 'platform-admin');
  },
};
