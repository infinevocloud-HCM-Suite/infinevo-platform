import { createService } from '@shared/api/createService.js';
import { apiClient } from '@shared/api/client.js';

const baseService = createService('/v1/leave-types');

export const leaveTypeService = {
  ...baseService,
  async savePolicy(id, body) {
    const res = await apiClient.put(`${baseService.basePath}/${id}/policy`, body);
    return res?.data !== undefined ? res.data : res;
  },
  async previewPolicy(id, params) {
    const res = await apiClient.get(`${baseService.basePath}/${id}/policy/preview`, { params });
    return res?.data !== undefined ? res.data : res;
  },
  async eligible(employeeId, asOf) {
    const res = await apiClient.get(`${baseService.basePath}/eligible`, {
      params: { employeeId, asOf },
    });
    return res?.data !== undefined ? res.data : res;
  },
};
