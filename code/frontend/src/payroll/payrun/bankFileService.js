import { apiClient } from '@shared/api/client.js';

const TEMPLATES = '/v1/payroll/bank-file-templates';
const runFiles = (payrunId) => `/v1/payroll/payruns/${payrunId}/bank-files`;

/** W-88.2: bank file templates, and the files made from them for a pay run. */
export const bankFileService = {
  async templates() {
    const res = await apiClient.get(TEMPLATES);
    return Array.isArray(res?.data) ? res.data : [];
  },

  async fields() {
    const res = await apiClient.get(`${TEMPLATES}/fields`);
    return Array.isArray(res?.data) ? res.data : [];
  },

  async template(id) {
    const res = await apiClient.get(`${TEMPLATES}/${encodeURIComponent(id)}`);
    return res?.data;
  },

  async create(body) {
    const res = await apiClient.post(TEMPLATES, body);
    return res?.data;
  },

  async update(id, body) {
    const res = await apiClient.put(`${TEMPLATES}/${encodeURIComponent(id)}`, body);
    return res?.data;
  },

  async remove(id) {
    await apiClient.delete(`${TEMPLATES}/${encodeURIComponent(id)}`);
  },

  async copy(id) {
    const res = await apiClient.post(`${TEMPLATES}/${encodeURIComponent(id)}/copy`);
    return res?.data;
  },

  /** @param {{templateId: string, employeeIds?: string[]}} body */
  async preview(payrunId, body) {
    const res = await apiClient.post(`${runFiles(payrunId)}/preview`, body);
    return res?.data;
  },

  async generate(payrunId, body) {
    const res = await apiClient.post(runFiles(payrunId), body);
    return res?.data;
  },

  async history(payrunId) {
    const res = await apiClient.get(runFiles(payrunId));
    return Array.isArray(res?.data) ? res.data : [];
  },

  /** The stored file as a Blob. */
  async download(payrunId, exportId) {
    const res = await apiClient.get(`${runFiles(payrunId)}/${exportId}/download`, { responseType: 'blob' });
    return res?.data;
  },
};

/** Saves a Blob in the browser under fileName. */
export function saveBlob(blob, fileName) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
