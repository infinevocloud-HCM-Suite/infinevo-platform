import { apiClient } from '@shared/api/client.js';

export const definitionService = {
  async list(flowTypeOrParams, asOf) {
    let params = {};
    if (typeof flowTypeOrParams === 'string') {
      params.flowType = flowTypeOrParams;
      if (asOf) params.asOf = asOf;
    } else if (flowTypeOrParams && typeof flowTypeOrParams === 'object') {
      params = flowTypeOrParams;
    }
    const res = await apiClient.get('/v1/approval-definitions', { params });
    return res?.data !== undefined ? res.data : res;
  },

  async save(flowType, body) {
    const res = await apiClient.put(`/v1/approval-definitions/${flowType}`, body);
    return res?.data !== undefined ? res.data : res;
  },
};
