import { createService } from '@shared/api/createService.js';
import { apiClient } from '@shared/api/client.js';

const baseService = createService('/v1/work-locations');

export const workLocationService = {
  ...baseService,
  async list(activeOnly = false) {
    const res = await apiClient.get('/v1/work-locations', { params: { activeOnly } });
    return res?.data !== undefined ? res.data : res;
  },
  async get(id) {
    const all = await this.list(false);
    return (Array.isArray(all) ? all : []).find((loc) => String(loc.id) === String(id)) || null;
  },
};
