import { apiClient } from '@shared/api/client';

const OFFICER = '/v1/payroll/employee-deductions';

/**
 * Ad-hoc salary deductions (W-47.4 §5, W-35.2 §4). Every reply is unwrapped as `res.data.data`;
 * a paged reply is Spring's `Page` (`content`, `totalElements`).
 */
export const deductionService = {
  /**
   * Posts a batch of 1-500 lines, all or nothing. A `400` carries the zero-based index of the
   * failing line under `fieldErrors.line`. Returns `{ count, rows }`.
   */
  async enter(lines) {
    const res = await apiClient.post(OFFICER, lines);
    return res.data.data;
  },

  /** Officer list. Empty filters are left off the query. */
  async list(params = {}) {
    const { employeeId, period, status, deductionType, page = 0, size = 25 } = params;
    const query = { page, size };
    if (employeeId) query.employeeId = employeeId;
    if (period) query.period = period;
    if (status) query.status = status;
    if (deductionType) query.deductionType = deductionType;
    const res = await apiClient.get(OFFICER, { params: query });
    return res.data.data;
  },

  async get(id) {
    const res = await apiClient.get(`${OFFICER}/${encodeURIComponent(id)}`);
    return res.data.data;
  },

  /** `DELETE` is the verb; the row stays, `REVERSED`. The reason travels as a query parameter. */
  async reverse(id, reason) {
    const res = await apiClient.delete(`${OFFICER}/${encodeURIComponent(id)}`, {
      params: { reason },
    });
    return res.data.data;
  },

  async listOwn() {
    const res = await apiClient.get('/v1/me/employee-deductions');
    return res.data.data;
  },
};
