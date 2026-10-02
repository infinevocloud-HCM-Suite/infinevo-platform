import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/payruns';

// Every payroll pay run endpoint answers `{ status, message, data }` (PayRunApiResponse,
// CONVENTIONS.md §3); apiClient hands back the axios response, so the payload is two levels in.
function unwrap(res) {
  return res.data.data;
}

/**
 * Pay run API service (W-47.2 §5, W-29.1, W-29.2, W-29.4, W-30.2, W-36.2).
 * Every endpoint mapped 1-to-1 with backend controller contracts.
 */
export const payrunService = {
  /**
   * Paged list of pay runs filtered by status and runType.
   */
  async list(params = {}) {
    const { status, runType, page = 0, size = 20 } = params;
    const query = { page, size };
    if (status) query.status = status;
    if (runType && runType !== 'ALL') query.runType = runType;

    const res = await apiClient.get(BASE_PATH, { params: query });
    return unwrap(res);
  },

  /**
   * Get single pay run by ID with compute and progress stats.
   */
  async get(id) {
    const res = await apiClient.get(`${BASE_PATH}/${id}`);
    return unwrap(res);
  },

  /**
   * Create regular pay run for a period (e.g. { period: '2026-10' }).
   */
  async create(body) {
    const res = await apiClient.post(BASE_PATH, body);
    return unwrap(res);
  },

  /**
   * Create off-cycle pay run ({ payDate, employeeIds, notes }).
   */
  async createOffCycle(body) {
    const payload = {
      payDate: body.payDate || body.pay_date,
      employeeIds: body.employeeIds || body.employee_ids,
      notes: body.notes || '',
    };
    const res = await apiClient.post(`${BASE_PATH}/off-cycle`, payload);
    return unwrap(res);
  },

  /**
   * List employees considered in this run, paged with optional inclusion filter.
   */
  async employees(id, params = {}) {
    const { inclusion, page = 0, size = 50 } = params;
    const query = { page, size };
    if (inclusion && inclusion !== 'ALL') query.inclusion = inclusion;

    const res = await apiClient.get(`${BASE_PATH}/${id}/employees`, { params: query });
    return unwrap(res);
  },

  /**
   * Lines for a single employee in a run.
   */
  async lines(id, employeeId) {
    const res = await apiClient.get(`${BASE_PATH}/${id}/employees/${employeeId}/lines`);
    return unwrap(res);
  },

  /**
   * Trigger async compute on worker (returns 202 { job_id }).
   */
  async compute(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/compute`);
    return unwrap(res);
  },

  /**
   * Lock a draft run.
   */
  async lock(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/lock`);
    return unwrap(res);
  },

  /**
   * Cancel a draft, locked or approved run. A paid run is never cancelled (W-36.2 §13 decision 9).
   */
  async cancel(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/cancel`);
    return unwrap(res);
  },

  /**
   * Approve a computed run (W-36.2): `COMPUTED → APPROVED`.
   */
  async approve(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/approve`);
    return unwrap(res);
  },

  /**
   * Pay an approved run and release its payslips (W-36.2): `APPROVED → PAID`.
   * @param {string} id Payrun ID
   * @param {string} paidOn `YYYY-MM-DD`, not in the future and not before the period start
   */
  async pay(id, paidOn) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/pay`, { paid_on: paidOn });
    return unwrap(res);
  },

  /**
   * Save inputs for an off-cycle run.
   * @param {string} id Payrun ID
   * @param {Array<{employeeId: string, kind: string, amount: number|string, sourceRef: string}>} rows
   */
  async addInputs(id, rows) {
    const payload = (rows || []).map((r) => ({
      employeeId: r.employeeId || r.employee_id,
      kind: r.kind,
      amount: r.amount,
      sourceRef: r.sourceRef || r.source_ref,
    }));
    const res = await apiClient.post(`${BASE_PATH}/${id}/inputs`, payload);
    return unwrap(res);
  },

  /**
   * Search employees by query for off-cycle employee select.
   * Note: Module boundary rule forbids importing from @core, so this calls core API path directly.
   */
  async searchEmployees(q = '') {
    const res = await apiClient.get('/v1/employees', { params: { q } });
    const data = res?.data !== undefined ? res.data : res;
    return data?.content || (Array.isArray(data) ? data : []);
  },
};
