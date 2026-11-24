import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/payroll/tax-recalc-failures';

/** The figures arrive in the standard envelope `{ status, message, data }` (TaxRecalcFailureController). */
function unwrap(res) {
  return res?.data?.data ?? res?.data ?? null;
}

/**
 * Failed tax recalculations for the payroll officer (D-147 API, D-151 screen). `fy` is the API form
 * 'YYYY-YYYY' or empty for every year.
 */
export const taxRecalcFailureService = {
  /** Open failures, newest first. Each carries `employee_name`, `financial_year`, `error_message`, `last_failed_at`. */
  async open(fy) {
    const data = unwrap(await apiClient.get(BASE_PATH, { params: fy ? { fy } : {} }));
    return Array.isArray(data) ? data : [];
  },

  /** Runs the recalculation again. Resolves with the new computation; rejects with code TAX_RECALC_FAILED when it fails again. */
  async retry(id) {
    return unwrap(await apiClient.post(`${BASE_PATH}/${id}/retry`));
  },
};
