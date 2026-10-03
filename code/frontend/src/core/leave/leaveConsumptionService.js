import { apiClient } from '@shared/api/client.js';

export const leaveConsumptionService = {
  async rows(id, year) {
    const res = await apiClient.get(`/v1/employees/${id}/leave-consumption`, {
      params: year ? { year } : undefined,
    });
    return res?.data !== undefined ? res.data : res;
  },

  async lop(id, period) {
    const res = await apiClient.get(`/v1/employees/${id}/lop`, {
      params: { period },
    });
    return res?.data !== undefined ? res.data : res;
  },
};
