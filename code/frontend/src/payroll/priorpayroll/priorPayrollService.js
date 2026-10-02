import { apiClient } from '@shared/api/client';
import { fyForApi } from '../tax/financialYear';

const BASE = '/v1/payroll/prior-payroll';
const IMPORTS = '/v1/payroll/prior-payroll-imports';

/**
 * Prior payroll service (W-47.6 §4, W-38.1 §4). Payroll replies come in the {status, message, data}
 * envelope; the document endpoints (W-21) and the template answer bare, so each reads its own shape.
 */
export const priorPayrollService = {
  /** The CSV template as text (no envelope). */
  async template() {
    const res = await apiClient.get(`${BASE}/template`, { responseType: 'text' });
    return res.data;
  },

  /** Stores the file as an EMPLOYEE_DOCUMENT and returns its document id (bare DocumentResponse). */
  async upload(file) {
    const form = new FormData();
    form.append('file', file);
    const res = await apiClient.post('/v1/documents', form, {
      params: { kind: 'EMPLOYEE_DOCUMENT' },
      // apiClient defaults to application/json, which would serialise the form to JSON.
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.id;
  },

  /** Dry run or real import; financialYear in display form, converted here. */
  async import({ documentId, financialYear, dryRun }) {
    const res = await apiClient.post(IMPORTS, {
      documentId,
      financialYear: fyForApi(financialYear),
      dryRun,
    });
    return res.data.data;
  },

  async imports(page = 0, size = 20) {
    const res = await apiClient.get(IMPORTS, { params: { page, size } });
    return res.data.data;
  },

  async months(fy, page = 0, size = 50) {
    const res = await apiClient.get(BASE, { params: { fy: fyForApi(fy), page, size } });
    return res.data.data;
  },

  async remove(id) {
    await apiClient.delete(`${BASE}/${id}`);
  },

  async status(fy) {
    const res = await apiClient.get(`${BASE}/status`, { params: { fy: fyForApi(fy) } });
    return res.data.data;
  },

  /** A short-lived signed link to the error file (bare {url, expiresAt}). */
  async errorFileLink(documentId) {
    const res = await apiClient.get(`/v1/documents/${documentId}/link`);
    return res.data.url;
  },
};
