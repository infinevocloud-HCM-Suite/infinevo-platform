import { apiClient } from '@shared/api/client';
import { fyForApi } from './financialYear';

/**
 * Service for Proof of Investment (POI) employee uploads and officer review workflows (W-34.1, W-34.2, W-34.3).
 */
export const proofService = {
  /**
   * Fetches employee's own proof of investment for a financial year.
   *
   * @param {string} fy e.g. '2026-27'
   * @returns {Promise<Object>} ProofResponse
   */
  async getOwn(fy) {
    const res = await apiClient.get(`/v1/me/proof-of-investment/${encodeURIComponent(fyForApi(fy))}`);
    return res.data?.data;
  },

  /**
   * Updates an item's claimed amount on own proof.
   *
   * @param {string} fy
   * @param {string} itemId
   * @param {number|string} claimedAmount
   * @returns {Promise<Object>} ProofItemResponse
   */
  async updateItemOwn(fy, itemId, claimedAmount) {
    const res = await apiClient.put(
      `/v1/me/proof-of-investment/${encodeURIComponent(fyForApi(fy))}/items/${itemId}`,
      { claimed_amount: claimedAmount },
    );
    return res.data?.data;
  },

  /**
   * Uploads and attaches a proof receipt file to an item.
   *
   * @param {string} fy
   * @param {string} itemId
   * @param {File} file
   * @returns {Promise<Object>} ProofDocumentResponse
   */
  async attachDocumentOwn(fy, itemId, file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post(
      `/v1/me/proof-of-investment/${encodeURIComponent(fyForApi(fy))}/items/${itemId}/documents`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return res.data?.data;
  },

  /**
   * Detaches and deletes a proof receipt file from an item.
   *
   * @param {string} fy
   * @param {string} itemId
   * @param {string} documentId
   * @returns {Promise<void>}
   */
  async detachDocumentOwn(fy, itemId, documentId) {
    await apiClient.delete(
      `/v1/me/proof-of-investment/${encodeURIComponent(fyForApi(fy))}/items/${itemId}/documents/${documentId}`,
    );
  },

  /**
   * Submits own proof of investment for review.
   *
   * @param {string} fy
   * @returns {Promise<Object>} ProofResponse
   */
  async submitOwn(fy) {
    const res = await apiClient.post(`/v1/me/proof-of-investment/${encodeURIComponent(fyForApi(fy))}/submit`);
    return res.data?.data;
  },

  /**
   * Officer: lists proof-of-investment chase queue across employees.
   *
   * @param {string} fy
   * @param {string} [status]
   * @param {string} [search]
   * @param {number} [page=0]
   * @param {number} [size=25]
   * @returns {Promise<Object>} Paged ProofChaseRow
   */
  async listQueue(fy, status, search, page = 0, size = 25) {
    const params = {
      fy: fyForApi(fy),
      page,
      size,
    };
    if (status) params.status = status;
    if (search) params.search = search;
    const res = await apiClient.get('/v1/payroll/proof-of-investment', { params });
    return res.data?.data;
  },

  /**
   * Officer: fetches proof review details for a specific proof.
   *
   * @param {string} proofId
   * @returns {Promise<Object>} ProofReviewResponse
   */
  async getReview(proofId) {
    const res = await apiClient.get(`/v1/payroll/proof-of-investment/${proofId}/review`);
    return res.data?.data;
  },

  /**
   * Officer: decides on a specific item (APPROVE, DISALLOW, RETURN).
   *
   * @param {string} proofId
   * @param {string} itemId
   * @param {{ action: string, approvedAmount?: number|string, comment?: string }} decision
   * @returns {Promise<Object>} ProofReviewItemResponse
   */
  async decideItem(proofId, itemId, decision) {
    const payload = {
      action: decision.action,
      approved_amount: decision.approvedAmount,
      comment: decision.comment,
    };
    const res = await apiClient.post(
      `/v1/payroll/proof-of-investment/${proofId}/items/${itemId}/decide`,
      payload,
    );
    return res.data?.data;
  },

  /**
   * Officer: records final decision for the entire proof (APPROVE or RETURN).
   *
   * @param {string} proofId
   * @param {{ action: string, comment?: string }} decision
   * @returns {Promise<Object>} ProofReviewResponse
   */
  async decideFinal(proofId, decision) {
    const res = await apiClient.post(
      `/v1/payroll/proof-of-investment/${proofId}/final`,
      decision,
    );
    return res.data?.data;
  },
};
