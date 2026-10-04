import { apiClient } from '@shared/api/client';

const BASE = '/v1/hrms/timesheets';
const PROJECTS = '/v1/hrms/projects';

/**
 * Timesheet service (W-48.2 §5; endpoints W-42.1 §4, W-42.3 §4). Every reply is the {status, message, data}
 * envelope, `/me/timesheet` included (its `data` is null when the week has no timesheet), so each call unwraps
 * `data`. DELETE answers 204 with no body.
 */
export const timesheetService = {
  /** The caller's weeks, newest first; `from` and `to` are `YYYY-MM-DD` or omitted. */
  async mine(from, to) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const res = await apiClient.get(`${BASE}/mine`, { params });
    return res.data.data;
  },

  /** The caller's timesheet for the week starting `weekStart`, or null when there is none. */
  async week(weekStart) {
    const res = await apiClient.get('/v1/me/timesheet', { params: { weekStart } });
    return res.data.data ?? null;
  },

  async create(body) {
    const res = await apiClient.post(BASE, body);
    return res.data.data;
  },

  /** Replaces a DRAFT week; on a REJECTED week this is the resubmit of the projects in the body (W-42.3). */
  async replace(id, body) {
    const res = await apiClient.put(`${BASE}/${id}`, body);
    return res.data.data;
  },

  async submit(id) {
    const res = await apiClient.put(`${BASE}/${id}/submit`);
    return res.data.data;
  },

  async remove(id) {
    await apiClient.delete(`${BASE}/${id}`);
  },

  /** Projects the caller is assigned to. */
  async myProjects() {
    const res = await apiClient.get(`${PROJECTS}/mine`);
    return res.data.data;
  },

  async tasks(projectId) {
    const res = await apiClient.get(`${PROJECTS}/${projectId}/tasks`);
    return res.data.data;
  },
};
