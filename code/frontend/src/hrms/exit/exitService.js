import { apiClient } from '@shared/api/client';

const ME_EXIT = '/v1/me/exit';
const HRMS_EXITS = '/v1/hrms/exits';

export const exitService = {
  myExits: async () => {
    const data = await apiClient.get(ME_EXIT);
    return Array.isArray(data) ? data : [];
  },

  submitResignation: (payload) => apiClient.post(ME_EXIT, payload),

  withdrawMyExit: (id) => apiClient.post(`${ME_EXIT}/${id}/withdraw`),

  listExits: async (status) => {
    const params = status ? { status } : undefined;
    const data = await apiClient.get(HRMS_EXITS, { params });
    return Array.isArray(data) ? data : [];
  },

  getExit: (id) => apiClient.get(`${HRMS_EXITS}/${id}`),

  approveExit: (id, approvedLwd) =>
    apiClient.post(`${HRMS_EXITS}/${id}/approve`, approvedLwd ? { approvedLwd } : {}),

  rejectExit: (id, remarks) =>
    apiClient.post(`${HRMS_EXITS}/${id}/reject`, remarks ? { remarks } : {}),

  signOffDepartment: (id, dept, status = 'CLEARED', remarks = '') =>
    apiClient.put(`${HRMS_EXITS}/${id}/clearance/${dept}`, { status, remarks }),

  updateClearanceItem: (id, itemId, status = 'CLEARED', remarks = '') =>
    apiClient.put(`${HRMS_EXITS}/${id}/clearance/items/${itemId}`, { status, remarks }),

  handOffToPayroll: (id) => apiClient.post(`${HRMS_EXITS}/${id}/hand-off-to-payroll`),
};
