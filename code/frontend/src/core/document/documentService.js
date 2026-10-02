import { apiClient } from '@shared/api/client.js';

/**
 * Service for document storage and uploads (W-21 / W-46.2).
 */
export const documentService = {
  async upload(file, kind = 'LEAVE_IMPORT') {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post(`/v1/documents?kind=${kind}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res?.data !== undefined ? res.data : res;
  },
  async get(id) {
    const res = await apiClient.get(`/v1/documents/${id}`);
    return res?.data !== undefined ? res.data : res;
  },
};
