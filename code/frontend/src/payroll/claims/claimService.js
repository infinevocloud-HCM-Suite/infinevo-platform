import { apiClient } from '@shared/api/client';

const ME = '/v1/me/reimbursement-claims';
const OFFICER = '/v1/payroll/reimbursement-claims';

/**
 * Reimbursement claims (W-47.4 §5, W-35.1 §4).
 *
 * Every payroll reply is `{ status, message, data }` and apiClient hands back the axios response,
 * so the payload is `res.data.data` (the pattern of `tax/taxSettingsService.js:18-19`). Not
 * `createService`: it returns the envelope. A paged reply is Spring's `Page` - rows in
 * `content`, the count in `totalElements`.
 */
export const claimService = {
  /** The components the signed-in employee may claim against: `[{id, code, name, max_limit}]`. */
  async components() {
    const res = await apiClient.get(`${ME}/components`);
    return res.data.data;
  },

  /** `body`: `{ reimbursement_id, requested_amount (string, 2 decimals), bill_date, description, document_id }`. */
  async submit(body) {
    const res = await apiClient.post(ME, body);
    return res.data.data;
  },

  async listOwn() {
    const res = await apiClient.get(ME);
    return res.data.data;
  },

  async getOwn(id) {
    const res = await apiClient.get(`${ME}/${encodeURIComponent(id)}`);
    return res.data.data;
  },

  /** Officer list. Empty filters are left off the query. */
  async list(params = {}) {
    const { employeeId, status, from, to, page = 0, size = 25 } = params;
    const query = { page, size };
    if (employeeId) query.employeeId = employeeId;
    if (status) query.status = status;
    if (from) query.from = from;
    if (to) query.to = to;
    const res = await apiClient.get(OFFICER, { params: query });
    return res.data.data;
  },

  async get(id) {
    const res = await apiClient.get(`${OFFICER}/${encodeURIComponent(id)}`);
    return res.data.data;
  },

  /**
   * Employees matching `q`, for the employee pickers. Core's `GET /v1/employees` answers a bare
   * `Page` with no envelope (`core/.../EmployeeController.java:71-79`), so this reads `res.data`.
   * Payroll reaches core over HTTP only, never by import.
   */
  async searchEmployees(q = '') {
    const params = { size: 20 };
    if (q) params.q = q;
    const res = await apiClient.get('/v1/employees', { params });
    return res.data?.content ?? [];
  },
};
