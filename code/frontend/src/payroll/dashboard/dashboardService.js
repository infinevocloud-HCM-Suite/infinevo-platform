import { apiClient } from '@shared/api/client';

/**
 * The payroll dashboard's two reads (W-47.5 §4, §5).
 *
 * The dashboard reply is payroll's `{ status, message, data }` envelope, so the payload is
 * `res.data.data` (the pattern of `tax/taxSettingsService.js:18-19`). The setup checklist is
 * core's bare `SetupChecklistResponse` with no envelope (`SetupChecklistController.java:38`), so
 * that one is `res.data`. Payroll reaches core over HTTP only, never by import.
 */
export const dashboardService = {
  /** `fy` is the year the financial year starts: `2026` = April 2026 to March 2027 (W-37 §4). */
  async summary(fy) {
    const res = await apiClient.get('/v1/payroll/dashboard', { params: { fy } });
    return res.data.data;
  },

  /** `{ steps: [{ code, module, completed, skipped, ... }], ... }` from `/v1/setup-checklist`. */
  async setupChecklist() {
    const res = await apiClient.get('/v1/setup-checklist');
    return res.data;
  },
};
