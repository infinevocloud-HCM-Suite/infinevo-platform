import { apiClient } from '@shared/api/client';

const BASE = '/v1/tenant-options';

/**
 * Service for managing typed tenant options (W-104 §4.1, §5).
 */
export const optionService = {
  async list(category) {
    const res = await apiClient.get(BASE, {
      params: category ? { category } : undefined,
    });
    return res?.data !== undefined ? res.data : res;
  },

  async update(key, value) {
    const res = await apiClient.put(`${BASE}/${encodeURIComponent(key)}`, {
      value: String(value),
    });
    return res?.data !== undefined ? res.data : res;
  },

  async reset(key) {
    const res = await apiClient.delete(`${BASE}/${encodeURIComponent(key)}`);
    return res?.data !== undefined ? res.data : res;
  },
};

