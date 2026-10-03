import { apiClient } from '@shared/api/client.js';

export const leaveBalanceService = {
  async forEmployee(id, asOf) {
    const res = await apiClient.get(`/v1/employees/${id}/leave-balances`, {
      params: asOf ? { asOf } : undefined,
    });
    return res?.data !== undefined ? res.data : res;
  },

  async allocate(body) {
    const res = await apiClient.post('/v1/leave-allocations', body);
    return res?.data !== undefined ? res.data : res;
  },

  async accrue(asOf) {
    const res = await apiClient.post('/v1/leave-allocations/accrue', null, {
      params: asOf ? { asOf } : undefined,
    });
    return res?.data !== undefined ? res.data : res;
  },
};
