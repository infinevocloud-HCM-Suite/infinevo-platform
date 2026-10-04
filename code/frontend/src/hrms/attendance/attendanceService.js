import { apiClient } from '@shared/api/client';

const BASE = '/v1/hrms/attendance';

/**
 * Attendance service (W-48.4 §5; endpoints W-40.1, W-40.3). Unlike timesheets and projects, these endpoints
 * answer the record BARE and in camelCase (`clockInAt`, `workedMinutes`) — there is no {status, message, data}
 * envelope, so every call returns `res.data` as it is. Errors (409 already clocked in, 400 bad range) reject
 * with the client's error object, whose `message` is the server's.
 */
export const attendanceService = {
  async today() {
    const res = await apiClient.get(`${BASE}/today`);
    return res.data;
  },

  async clockIn() {
    const res = await apiClient.post(`${BASE}/clock-in`);
    return res.data;
  },

  async clockOut() {
    const res = await apiClient.post(`${BASE}/clock-out`);
    return res.data;
  },

  /** The caller's sessions; `from` and `to` are `YYYY-MM-DD`, both required. */
  async mySessions(from, to) {
    const res = await apiClient.get(`${BASE}/sessions/mine`, {
      params: { from, to },
    });
    return res.data;
  },

  /** Everyone's sessions in the range, optionally one employee's. */
  async allSessions(from, to, employeeId) {
    const params = { from, to };
    if (employeeId) params.employeeId = employeeId;
    const res = await apiClient.get(`${BASE}/sessions`, { params });
    return res.data;
  },

  async preferences() {
    const res = await apiClient.get(`${BASE}/preferences`);
    return res.data;
  },

  async savePreferences(body) {
    const res = await apiClient.put(`${BASE}/preferences`, body);
    return res.data;
  },
};
