import { apiClient } from '@shared/api/client.js';

export const holidayService = {
  async between({ workLocationId, from, to } = {}) {
    const params = {};
    if (workLocationId) params.workLocationId = workLocationId;
    if (from) params.from = from;
    if (to) params.to = to;
    const res = await apiClient.get('/v1/holidays', { params });
    return res?.data !== undefined ? res.data : res;
  },
};
