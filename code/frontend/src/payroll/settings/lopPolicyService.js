import { apiClient } from '@shared/api/client.js';

export const lopPolicyService = {
  async get(asOf) {
    const res = await apiClient.get('/v1/lop-policy', {
      params: asOf ? { asOf } : undefined,
    });
    return res.data?.data ?? res.data;
  },

  async save(payload) {
    const res = await apiClient.put('/v1/lop-policy', payload);
    return res.data?.data ?? res.data;
  },
};
