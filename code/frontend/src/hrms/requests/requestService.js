import { apiClient } from '@shared/api/client';

const REG = '/v1/hrms/attendance/regularizations';
const OT = '/v1/hrms/overtime-requests';
const CORE_OT = '/v1/overtime';

/**
 * Overtime replies are snake_case (core's OvertimeResponse, @JsonProperty); map them to the camelCase shape
 * regularizations already use, so the screens see one (W-48.5 §3).
 */
export function toOvertime(o) {
  if (!o) return o;
  return {
    id: o.id,
    employeeId: o.employee_id,
    employeeName: o.employee_name,
    overtimeDate: o.overtime_date,
    hours: o.hours,
    amount: o.amount,
    status: o.status,
    source: o.source,
    remarks: o.remarks,
    payInputId: o.pay_input_id,
    postedPeriod: o.posted_period,
    createdAt: o.created_at,
  };
}

const list = (data) => (Array.isArray(data) ? data : []);

/** Regularization and overtime requests (W-48.5 §5). Replies are bare — no envelope — so each call returns `res.data`. */
export const requestService = {
  async myRegularizations(from, to) {
    const res = await apiClient.get(`${REG}/mine`, { params: { from, to } });
    return res.data;
  },

  async allRegularizations(from, to, status, employeeId) {
    const params = { from, to };
    if (status) params.status = status;
    if (employeeId) params.employeeId = employeeId;
    const res = await apiClient.get(REG, { params });
    return res.data;
  },

  async regularization(id) {
    const res = await apiClient.get(`${REG}/${id}`);
    return res.data;
  },

  async submitRegularization(body) {
    const res = await apiClient.post(REG, body);
    return res.data;
  },

  async myOvertime(from, to) {
    const res = await apiClient.get(`${OT}/mine`, { params: { from, to } });
    return list(res.data).map(toOvertime);
  },

  /** HR: everyone's overtime in a range, from core (W-68 �5). `employeeId` only when set. */
  async allOvertime(from, to, employeeId) {
    const params = { from, to };
    if (employeeId) params.employeeId = employeeId;
    const res = await apiClient.get(CORE_OT, { params });
    return list(res.data).map(toOvertime);
  },

  async overtime(id) {
    const res = await apiClient.get(`${OT}/${id}`);
    return toOvertime(res.data);
  },

  /** `body` is `{overtime_date, hours, remarks}`, as the server reads it. */
  async submitOvertime(body) {
    const res = await apiClient.post(OT, body);
    return toOvertime(res.data);
  },
};
