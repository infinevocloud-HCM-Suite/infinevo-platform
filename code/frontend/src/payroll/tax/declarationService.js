import { apiClient } from '@shared/api/client';

/**
 * Service for employee investment declarations under /me and officer header review.
 */
export const declarationService = {
  // ── Header & Lifecycle ───────────────────────────────────────────────────────
  async header(fy) {
    const res = await apiClient.get(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}`);
    return res.data.data;
  },

  async saveHeader(fy, body) {
    const res = await apiClient.put(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}`, body);
    return res.data.data;
  },

  async submit(fy) {
    const res = await apiClient.post(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/submit`);
    return res.data.data;
  },

  async reopen(fy) {
    const res = await apiClient.post(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/reopen`);
    return res.data.data;
  },

  async headerOf(employeeId, fy) {
    const res = await apiClient.get(
      `/api/v1/payroll/employees/${encodeURIComponent(employeeId)}/tax-declaration/${encodeURIComponent(fy)}`,
    );
    return res.data.data;
  },

  // ── Housing Section ─────────────────────────────────────────────────────────
  async housing(fy) {
    const res = await apiClient.get(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/housing`);
    return res.data.data;
  },

  async saveHouseRent(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/house-rent`,
      body,
    );
    return res.data.data;
  },

  async saveHomeLoan(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/home-loan`,
      body,
    );
    return res.data.data;
  },

  async saveLetOut(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/let-out-property`,
      body,
    );
    return res.data.data;
  },

  // ── Deductions Section ──────────────────────────────────────────────────────
  async items(fy) {
    const res = await apiClient.get(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/section6a-items`,
    );
    return res.data.data;
  },

  async deductions(fy) {
    const res = await apiClient.get(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/deductions`);
    return res.data.data;
  },

  async save6a(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/section6a`,
      body,
    );
    return res.data.data;
  },

  async savePreTax(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/pre-tax-deductions`,
      body,
    );
    return res.data.data;
  },

  async savePrevEmployment(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/previous-employment`,
      body,
    );
    return res.data.data;
  },

  // ── Other Income Section ────────────────────────────────────────────────────
  async otherIncome(fy) {
    const res = await apiClient.get(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/other-income`,
    );
    return res.data.data;
  },

  async saveOtherIncome(fy, body) {
    const res = await apiClient.put(
      `/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/other-income`,
      body,
    );
    return res.data.data;
  },

  // ── Summary Section ─────────────────────────────────────────────────────────
  async summary(fy) {
    const res = await apiClient.get(`/api/v1/me/tax-declaration/${encodeURIComponent(fy)}/summary`);
    return res.data.data;
  },
};
