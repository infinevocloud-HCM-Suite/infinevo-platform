import { apiClient } from '@shared/api/client';

/**
 * Service for role and permission action management (W-11.1).
 */
export const roleService = {
  /**
   * Fetches all tenant system and custom roles.
   *
   * @returns {Promise<Array<Object>>}
   */
  async list() {
    const res = await apiClient.get('/v1/roles');
    return res.data || [];
  },

  /**
   * Fetches the complete reference action catalogue.
   *
   * @returns {Promise<Array<Object>>}
   */
  async listActions() {
    const res = await apiClient.get('/v1/actions');
    return res.data || [];
  },

  /**
   * Creates a new custom role.
   *
   * @param {{ name: string, code?: string, actionCodes?: string[] }} payload
   * @returns {Promise<Object>}
   */
  async create(payload) {
    const res = await apiClient.post('/v1/roles', payload);
    return res.data;
  },

  /**
   * Updates an existing custom role.
   *
   * @param {string} id
   * @param {{ name?: string, actionCodes?: string[] }} payload
   * @returns {Promise<Object>}
   */
  async update(id, payload) {
    const res = await apiClient.put(`/v1/roles/${id}`, payload);
    return res.data;
  },

  /**
   * Deletes a custom role.
   *
   * @param {string} id
   * @returns {Promise<void>}
   */
  async delete(id) {
    await apiClient.delete(`/v1/roles/${id}`);
  },
};
