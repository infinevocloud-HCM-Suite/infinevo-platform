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
};
