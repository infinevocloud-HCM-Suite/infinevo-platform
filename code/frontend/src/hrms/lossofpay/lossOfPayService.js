import { apiClient } from '@shared/api/client';

const PAY_INPUTS = '/v1/pay-inputs';
const EMPLOYEES = '/v1/employees';

/** The ledger kind this screen writes and lists (D-140). */
export const LOP_KIND = 'LOP_DAYS';

/** core caps a page of `GET /v1/employees` at 100 (EmployeeQueryService.MAX_PAGE_SIZE). */
const PAGE_SIZE = 100;
/** A stop for a server that never says `last` - 50 pages is 5,000 employees. */
const MAX_PAGES = 50;

/**
 * Prefix of the idempotency key this screen sends as `source_ref`. The key is random per entry, so
 * two entries with the same reason never collide on the ledger's unique (source_module, source_ref).
 */
export const MANUAL_LOP_REF_PREFIX = 'manual-lop:';

/** A fresh `source_ref` for one hand-entered row; falls back where `crypto.randomUUID` is missing. */
export function manualLopRef() {
  const uuid =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
  return `${MANUAL_LOP_REF_PREFIX}${uuid}`;
}

/** "E001 - Asha Rao" from an EmployeeSummaryResponse. */
export function employeeLabel(e) {
  const name = [e?.firstName, e?.lastName].filter(Boolean).join(' ');
  return [e?.employeeNumber, name].filter(Boolean).join(' - ') || e?.id || '';
}

/**
 * Manual loss of pay over core's pay input ledger (D-140). Replies are bare - no envelope - and
 * snake_case (PayInputResponse, @JsonProperty), so each call returns `res.data` as sent.
 */
export const lossOfPayService = {
  /** Every pay input of one month, all kinds: `{rows, quantity_totals_by_kind, amount_totals_by_kind}`. */
  async forPeriod(period) {
    const res = await apiClient.get(PAY_INPUTS, { params: { period } });
    return res.data;
  },

  /**
   * Record `days` of loss of pay. The reply's `posted_period` is the month it landed in - the next
   * open one when `period` was locked. `days` goes as text so the server reads an exact BigDecimal.
   * `reason` goes as `note` (display only, at most 200 characters); `source_ref` is a fresh random
   * key, since a reason is not unique and the key column holds 64 characters.
   */
  async record({ employeeId, period, days, reason }) {
    const res = await apiClient.post(PAY_INPUTS, {
      employee_id: employeeId,
      period,
      kind: LOP_KIND,
      quantity: days,
      amount: null,
      source_ref: manualLopRef(),
      note: reason,
    });
    return res.data;
  },

  /** Post the reversal row for `id`; the server keeps `reason` as the reversal row's `note`. */
  async reverse(id, reason) {
    const res = await apiClient.post(`${PAY_INPUTS}/${id}/reverse`, { reason: reason || null });
    return res.data;
  },

  /** Active employees matching `q`, for the picker. */
  async searchActive(q = '') {
    const params = { status: 'ACTIVE', size: 20 };
    if (q) params.q = q;
    const res = await apiClient.get(EMPLOYEES, { params });
    return res.data?.content ?? [];
  },

  /**
   * Every employee, any status, as a map of id to label - the ledger rows carry ids only, and a
   * month's loss of pay may belong to someone since terminated.
   */
  async directory() {
    const labels = {};
    for (let page = 0; page < MAX_PAGES; page += 1) {
      const res = await apiClient.get(EMPLOYEES, { params: { page, size: PAGE_SIZE } });
      const data = res?.data || {};
      const content = data.content || [];
      for (const e of content) labels[e.id] = employeeLabel(e);
      if (data.last !== false || content.length === 0) break;
    }
    return labels;
  },
};
