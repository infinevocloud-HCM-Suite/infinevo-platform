import { apiClient } from '@shared/api/client';

const BASE = '/v1/hrms/timesheets';

/** Only the filters that are set go on the query; `page` is 0-based, as the server reads it. */
function params(filters = {}) {
  const out = {};
  for (const key of ['from', 'to', 'status', 'projectId', 'employeeId', 'page', 'size']) {
    const v = filters[key];
    if (v !== undefined && v !== null && v !== '') out[key] = v;
  }
  return out;
}

/**
 * Timesheet review service (W-48.3 §5; endpoints `TimesheetController.java` /managed, /team, /, /{id},
 * /project-entries/{entryId}). Every reply is the {status, message, data} envelope; lists are a `TimesheetPage`.
 */
export const reviewService = {
  async managed(filters) {
    const res = await apiClient.get(`${BASE}/managed`, { params: params(filters) });
    return res.data.data;
  },
  async team(filters) {
    const res = await apiClient.get(`${BASE}/team`, { params: params(filters) });
    return res.data.data;
  },
  async all(filters) {
    const res = await apiClient.get(BASE, { params: params(filters) });
    return res.data.data;
  },
  async get(id) {
    const res = await apiClient.get(`${BASE}/${id}`);
    return res.data.data;
  },
  async entry(entryId) {
    const res = await apiClient.get(`${BASE}/project-entries/${entryId}`);
    return res.data.data;
  },
};
