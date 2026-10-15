import { apiClient } from '@shared/api/client';
import { fyForApi } from '../tax/financialYear';

/**
 * Service for Form 16 annual tax statements (Part B) and TRACES certificates (Part A) (W-36.4, W-36.5, W-47).
 *
 * Backend baseURL is '/api', so endpoints start with '/v1/...'.
 * All financial year path variables are normalized via fyForApi() ('YYYY-YY' -> 'YYYY-YYYY').
 */
export const form16Service = {
  /**
   * Officer: fetch an employee's Form 16 Part B statement for a financial year.
   * @param {string} employeeId - UUID of the employee
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @returns {Promise<Object>} Form16Statement
   */
  async getStatement(employeeId, fy) {
    const res = await apiClient.get(
      `/v1/payroll/employees/${encodeURIComponent(employeeId)}/form16/${encodeURIComponent(fyForApi(fy))}`
    );
    return res.data?.data;
  },

  /**
   * Employee self-service: fetch own Form 16 Part B statement for a financial year.
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @returns {Promise<Object>} Form16Statement
   */
  async getOwnStatement(fy) {
    const res = await apiClient.get(
      `/v1/me/form16/${encodeURIComponent(fyForApi(fy))}`
    );
    return res.data?.data;
  },

  /**
   * Officer: upload TRACES Part A ZIP file for a financial year.
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @param {File|Blob} file - The .zip file to upload
   * @returns {Promise<Object>} PartAUploadResult { matched: number, unmatched: string[], skipped: string[] }
   */
  async uploadPartA(fy, file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post(
      `/v1/payroll/form16/${encodeURIComponent(fyForApi(fy))}/part-a`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );
    return res.data?.data;
  },

  /**
   * Officer: list uploaded Part A certificates for a financial year.
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @returns {Promise<Array>} List of PartARow [{ employee_id, document_id, source_file_name, uploaded_at }]
   */
  async listPartA(fy) {
    const res = await apiClient.get(
      `/v1/payroll/form16/${encodeURIComponent(fyForApi(fy))}/part-a`
    );
    return res.data?.data ?? [];
  },

  /**
   * Employee self-service: get signed download link for own Part A certificate.
   * @param {string} fy - Financial year ('2026-27' or '2026-2027')
   * @returns {Promise<Object>} PartAOwn { document_id, link, expires_at }
   */
  async getOwnPartA(fy) {
    const res = await apiClient.get(
      `/v1/me/form16/${encodeURIComponent(fyForApi(fy))}/part-a`
    );
    return res.data?.data;
  },

  /**
   * Officer: fetch temporary signed download link for an uploaded document.
   * @param {string} documentId - UUID of the document
   * @returns {Promise<Object>} { url, link, expiresAt }
   */
  async getDocumentDownloadLink(documentId) {
    const res = await apiClient.get(
      `/v1/documents/${encodeURIComponent(documentId)}/link`
    );
    return res.data;
  },
};
