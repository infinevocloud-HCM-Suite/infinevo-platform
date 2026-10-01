import { apiClient } from './client.js';

/**
 * Standard API service factory (W-45 §5).
 *
 * Provides standard CRUD verbs over apiClient for a given basePath.
 * Feature services spread this object and append domain-specific methods.
 *
 * @param {string} basePath URL prefix for this resource (e.g. '/v1/employees')
 * @returns {{ list: Function, get: Function, create: Function, update: Function, remove: Function }}
 */
export function createService(basePath) {
  const cleanPath = basePath.replace(/\/+$/, '');

  return {
    basePath: cleanPath,
    async list(params) {
      const res = await apiClient.get(cleanPath, { params });
      return res?.data !== undefined ? res.data : res;
    },
    async get(id) {
      const res = await apiClient.get(`${cleanPath}/${id}`);
      return res?.data !== undefined ? res.data : res;
    },
    async create(body) {
      const res = await apiClient.post(cleanPath, body);
      return res?.data !== undefined ? res.data : res;
    },
    async update(id, body) {
      const res = await apiClient.put(`${cleanPath}/${id}`, body);
      return res?.data !== undefined ? res.data : res;
    },
    async remove(id) {
      const res = await apiClient.delete(`${cleanPath}/${id}`);
      return res?.data !== undefined ? res.data : res;
    },
  };
}
