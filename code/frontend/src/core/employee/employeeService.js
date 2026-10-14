import { createService } from '../../shared/api/createService.js';
import { apiClient } from '../../shared/api/client.js';

const baseCrud = createService('/v1/employees');

export const employeeService = {
  ...baseCrud,

  /**
   * Search / list employees with server pagination and filtering.
   */
  async list(params = {}) {
    const { q, status, page, size, sort, includeDeleted } = params;
    const query = {};
    if (q) query.q = q;
    if (status) query.status = status;
    if (page !== undefined) query.page = page;
    if (size !== undefined) query.size = size;
    if (sort) query.sort = sort;
    if (includeDeleted !== undefined) query.includeDeleted = includeDeleted;

    const res = await apiClient.get('/v1/employees', { params: query });
    return res.data;
  },

  /**
   * Fetch one of the 5 detail sections: personal, contact, identification, employment, bank.
   */
  async section(id, name) {
    const res = await apiClient.get(`/v1/employees/${id}/${name}`);
    return res.data;
  },

  /**
   * Save one of the 5 detail sections.
   */
  async saveSection(id, name, body) {
    const res = await apiClient.put(`/v1/employees/${id}/${name}`, body);
    return res.data;
  },

  /**
   * Portal access of one employee (W-73.3): {state: NONE|INVITED|ACTIVE, invitationId, expiresAt, roles}.
   */
  async getAccess(id) {
    const res = await apiClient.get(`/v1/employees/${id}/access`);
    return res.data;
  },

  /**
   * Export all employees as an Excel file through W-23 reporting export path.
   */
  async export(filters = {}) {
    const defsRes = await apiClient.get('/v1/report-definitions');
    const empDef = (defsRes.data || []).find((d) => d.code === 'employees');
    if (!empDef) {
      throw new Error("Report definition 'employees' not found");
    }
    const exportRes = await apiClient.post('/v1/exports', {
      definitionId: empDef.id,
      filters,
    });
    return exportRes.data;
  },

  /**
   * Triggers browser download for a given URL.
   */
  downloadFile(url, fileName) {
    let downloadUrl = url;
    if (!downloadUrl.startsWith('http') && !downloadUrl.startsWith('/api') && downloadUrl.startsWith('/')) {
      downloadUrl = `/api${downloadUrl}`;
    }
    const a = document.createElement('a');
    a.href = downloadUrl;
    if (fileName) {
      a.download = fileName;
    }
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  },
};

