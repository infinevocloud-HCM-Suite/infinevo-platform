import { apiClient } from '@shared/api/client.js';

/**
 * Service for querying roles (W-11.1 / W-46.4).
 */
export const roleService = {
  async list() {
    const res = await apiClient.get('/v1/roles');
    return Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
  },
};
