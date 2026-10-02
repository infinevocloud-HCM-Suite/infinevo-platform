import { apiClient } from '@shared/api/client';
import { fyForApi } from './financialYear';

/**
 * Service for employee investment declarations under /me and officer header review.
 *
 * B-4 fix: apiClient.baseURL is '/api'. All paths here must therefore start with '/v1/'
 * (NOT '/api/v1/') — sending '/api/v1/...' causes the request to hit '/api/api/v1/...'
 * which the backend never routes, returning 404.
 *
 * B-3 fix: All financial year values passed in URL path segments are converted through
 * fyForApi() which maps the display format 'YYYY-YY' to the backend-required 'YYYY-YYYY'.
 */
export const declarationService = {
  // ── Header & Lifecycle ───────────────────────────────────────────────────────
  async header(fy) {
    const res = await apiClient.get(`/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}`);
    return res.data.data;
  },

  async saveHeader(fy, body) {
    const res = await apiClient.put(`/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}`, body);
    return res.data.data;
  },

  async submit(fy) {
    const res = await apiClient.post(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/submit`,
    );
    return res.data.data;
  },

  async reopen(fy) {
    const res = await apiClient.post(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/reopen`,
    );
    return res.data.data;
  },

  async headerOf(employeeId, fy) {
    const res = await apiClient.get(
      `/v1/payroll/employees/${encodeURIComponent(employeeId)}/tax-declaration/${encodeURIComponent(fyForApi(fy))}`,
    );
    return res.data.data;
  },

  // ── Housing Section ─────────────────────────────────────────────────────────
  async housing(fy) {
    const res = await apiClient.get(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/housing`,
    );
    return res.data.data;
  },

  async saveHouseRent(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/house-rent`,
      body,
    );
    return res.data.data;
  },

  async saveHomeLoan(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/home-loan`,
      body,
    );
    return res.data.data;
  },

  async saveLetOut(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/let-out-property`,
      body,
    );
    return res.data.data;
  },

  // ── Deductions Section ──────────────────────────────────────────────────────
  async items(fy) {
    const res = await apiClient.get(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/section6a-items`,
    );
    return res.data.data;
  },

  async deductions(fy) {
    const res = await apiClient.get(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/deductions`,
    );
    return res.data.data;
  },

  async save6a(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/section6a`,
      body,
    );
    return res.data.data;
  },

  async savePreTax(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/pre-tax-deductions`,
      body,
    );
    return res.data.data;
  },

  async savePrevEmployment(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/previous-employment`,
      body,
    );
    return res.data.data;
  },

  // ── Other Income Section ────────────────────────────────────────────────────
  async otherIncome(fy) {
    const res = await apiClient.get(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/other-income`,
    );
    return res.data.data;
  },

  async saveOtherIncome(fy, body) {
    const res = await apiClient.put(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/other-income`,
      body,
    );
    return res.data.data;
  },

  // ── Summary Section ─────────────────────────────────────────────────────────
  async summary(fy) {
    const res = await apiClient.get(
      `/v1/me/tax-declaration/${encodeURIComponent(fyForApi(fy))}/summary`,
    );
    return res.data.data;
  },
};
