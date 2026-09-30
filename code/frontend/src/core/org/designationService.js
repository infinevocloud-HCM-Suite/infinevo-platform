import { createService } from '@shared/api/createService.js';
import { apiClient } from '@shared/api/client.js';

const baseService = createService('/v1/designations');

export const designationService = {
  ...baseService,
  async list(activeOnly = false) {
    const res = await apiClient.get('/v1/designations', { params: { activeOnly } });
    return res?.data !== undefined ? res.data : res;
  },
};
