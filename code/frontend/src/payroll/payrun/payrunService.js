import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/payruns';

/**
 * Pay run API service (W-47.2 §5, W-29.1, W-29.2, W-29.4, W-30.2).
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
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Get single pay run by ID with compute and progress stats.
   */
  async get(id) {
    const res = await apiClient.get(`${BASE_PATH}/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Create regular pay run for a period (e.g. { period: '2026-10' }).
   */
  async create(body) {
    const res = await apiClient.post(BASE_PATH, body);
    return res?.data !== undefined ? res.data : res;
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
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * List employees considered in this run, paged with optional inclusion filter.
   */
  async employees(id, params = {}) {
    const { inclusion, page = 0, size = 50 } = params;
    const query = { page, size };
    if (inclusion && inclusion !== 'ALL') query.inclusion = inclusion;

    const res = await apiClient.get(`${BASE_PATH}/${id}/employees`, { params: query });
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Lines for a single employee in a run.
   */
  async lines(id, employeeId) {
    const res = await apiClient.get(`${BASE_PATH}/${id}/employees/${employeeId}/lines`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Trigger async compute on worker (returns 202 { job_id }).
   */
  async compute(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/compute`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Lock a draft run.
   */
  async lock(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/lock`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Cancel a draft or locked run.
   */
  async cancel(id) {
    const res = await apiClient.post(`${BASE_PATH}/${id}/cancel`);
    return res?.data !== undefined ? res.data : res;
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
    return res?.data !== undefined ? res.data : res;
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
