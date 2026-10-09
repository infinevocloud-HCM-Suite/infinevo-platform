import { apiClient } from '@shared/api/client.js';

/** The six labels an employee document can carry (W-73.5 §2); the server validates the same set. */
export const DOCUMENT_LABELS = [
  { value: 'ID_PROOF', label: 'ID proof' },
  { value: 'ADDRESS_PROOF', label: 'Address proof' },
  { value: 'OFFER_LETTER', label: 'Offer letter' },
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'CERTIFICATE', label: 'Certificate' },
  { value: 'OTHER', label: 'Other' },
];

/** The store's upload limit (W-73.5 §2: PDF, PNG, JPG up to 10 MB). */
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

/** File types the employee Documents tab accepts. */
export const ACCEPTED_DOCUMENT_TYPES = '.pdf,.png,.jpg,.jpeg';

/** Display text for a label code: its name, the code itself if unknown, or a dash when absent. */
export function labelText(code) {
  if (!code) return '—';
  const match = DOCUMENT_LABELS.find((l) => l.value === code);
  return match ? match.label : code;
}

function unwrap(res) {
  return res?.data !== undefined ? res.data : res;
}

/**
 * Service for document storage and uploads (W-21 / W-46.2 / W-73.5).
 */
export const documentService = {
  async upload(file, kind = 'LEAVE_ATTACHMENT') {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post(`/v1/documents?kind=${kind}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(res);
  },
  async get(id) {
    const res = await apiClient.get(`/v1/documents/${id}`);
    return unwrap(res);
  },
  async uploadForEmployee(file, employeeId, label) {
    const formData = new FormData();
    formData.append('file', file);
    const query = new URLSearchParams({ kind: 'EMPLOYEE_DOCUMENT', employeeId, label });
    const res = await apiClient.post(`/v1/documents?${query.toString()}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(res);
  },
  async listForEmployee(employeeId) {
    const res = await apiClient.get(`/v1/employees/${employeeId}/documents`);
    return unwrap(res);
  },
  async link(id) {
    const res = await apiClient.get(`/v1/documents/${id}/link`);
    return unwrap(res);
  },
  async remove(id) {
    const res = await apiClient.delete(`/v1/documents/${id}`);
    return unwrap(res);
  },
};
