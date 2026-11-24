import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/employees';

/** The figures arrive in the standard envelope `{ status, message, data }` (EmployeeTdsController). */
function unwrap(res) {
  return res?.data?.data ?? res?.data ?? null;
}

/**
 * The officer's view of an employee's annual TDS figures (W-36.1 §4, D-145). The financial year is the
 * API form 'YYYY-YYYY' (`fyForApi`).
 */
export const employeeTdsService = {
  /** The active record with deducted so far, remaining, months left and monthly share. 404 when none. */
  async get(employeeId, fy) {
    return unwrap(await apiClient.get(`${BASE_PATH}/${employeeId}/tds/${fy}`));
  },

  /** Every record for the year, newest first. */
  async history(employeeId, fy) {
    const data = unwrap(await apiClient.get(`${BASE_PATH}/${employeeId}/tds/${fy}/history`));
    return Array.isArray(data) ? data : [];
  },

  /** Records new figures: the active record is superseded, the new one is active. */
  async save(employeeId, fy, body) {
    return unwrap(await apiClient.put(`${BASE_PATH}/${employeeId}/tds/${fy}`, body));
  },
};
