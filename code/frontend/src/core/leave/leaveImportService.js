import { apiClient } from '@shared/api/client.js';

export const leaveImportService = {
  async start(body) {
    const res = await apiClient.post('/v1/leave-imports', body);
    return res?.data !== undefined ? res.data : res;
  },

  async get(id) {
    const res = await apiClient.get(`/v1/leave-imports/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async history(page = 0, size = 20) {
    const res = await apiClient.get('/v1/leave-imports', {
      params: { page, size },
    });
    return res?.data !== undefined ? res.data : res;
  },
};
