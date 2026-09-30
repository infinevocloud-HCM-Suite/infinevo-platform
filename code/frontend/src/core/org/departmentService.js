import { createService } from '@shared/api/createService.js';
import { apiClient } from '@shared/api/client.js';

const baseService = createService('/v1/departments');

export const departmentService = {
  ...baseService,
  async list(activeOnly = false) {
    const res = await apiClient.get('/v1/departments', { params: { activeOnly } });
    return res?.data !== undefined ? res.data : res;
  },
};
