import { apiClient } from '@shared/api/client.js';

export const setupService = {
  get: async () => {
    const response = await apiClient.get('/v1/setup-checklist');
    return response.data;
  },

  skip: async (stepCode, reason) => {
    const response = await apiClient.post(
      `/v1/setup-checklist/${encodeURIComponent(stepCode)}/skip`,
      { reason }
    );
    return response.data;
  },
};
