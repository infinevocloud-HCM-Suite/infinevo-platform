import { createService } from '../../shared/api/createService.js';
import { apiClient } from '../../shared/api/client.js';

const baseCrud = createService('/v1/roles');

export const roleService = {
  ...baseCrud,

  /**
   * List all system and custom roles with their action codes.
   */
  async list() {
    const res = await apiClient.get('/v1/roles');
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Fetch one role by ID.
   */
  async get(id) {
    const res = await apiClient.get(`/v1/roles/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Create a new custom role.
   * @param {{ name: string, code?: string, actionCodes: string[] }} body
   */
  async create(body) {
    const res = await apiClient.post('/v1/roles', body);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Update an existing custom role.
   * @param {string} id
   * @param {{ name: string, actionCodes: string[] }} body
   */
  async update(id, body) {
    const res = await apiClient.put(`/v1/roles/${id}`, body);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Delete a custom role.
   * @param {string} id
   */
  async remove(id) {
    const res = await apiClient.delete(`/v1/roles/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Fetch all system actions from the reference catalogue.
   */
  async listActions() {
    const res = await apiClient.get('/v1/actions');
    return res?.data !== undefined ? res.data : res;
  },
};
