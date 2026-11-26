import { apiClient } from '@shared/api/client';
import { fyForApi } from '../tax/financialYear';

const CHALLANS = '/v1/payroll/compliance/challans';
const RETURN = '/v1/payroll/compliance/24q';
const DEDUCTOR = '/v1/payroll/settings/tax-deductor';

const data = (res) => res.data?.data;
const enc = encodeURIComponent;

/**
 * W-89: the quarterly TDS return on salary — challans, their allocation to employees' TDS, the quarter's
 * statement (Annexure I), the year's salary statement (Annexure II) and the Form 138 portal file.
 * Financial years go to the API as 'YYYY-YYYY'.
 */
export const form24QService = {
  async listChallans(fy, quarter) {
    const params = { financialYear: fyForApi(fy) };
    if (quarter) params.quarter = quarter;
    return data(await apiClient.get(CHALLANS, { params })) ?? [];
  },

  async createChallan(payload) {
    return data(await apiClient.post(CHALLANS, { ...payload, financialYear: fyForApi(payload.financialYear) }));
  },

  async updateChallan(id, payload) {
    return data(
      await apiClient.put(`${CHALLANS}/${enc(id)}`, { ...payload, financialYear: fyForApi(payload.financialYear) }),
    );
  },

  async deleteChallan(id) {
    await apiClient.delete(`${CHALLANS}/${enc(id)}`);
  },

  /** Employees with TDS on the challan's quarter's runs, with what is covered here, elsewhere and still open. */
  async candidates(challanId) {
    return data(await apiClient.get(`${CHALLANS}/${enc(challanId)}/allocation-candidates`)) ?? [];
  },

  /** Sets this challan's amount for each employee of one run; 0 removes it. */
  async allocate(challanId, payRunId, items) {
    return data(
      await apiClient.post(`${CHALLANS}/${enc(challanId)}/allocate`, {
        payRunId,
        employeeAllocations: items.map((i) => ({
          employeeId: i.employeeId,
          taxAmount: i.amount,
          surchargeAmount: 0,
          cessAmount: 0,
        })),
      }),
    );
  },

  async returns(fy) {
    return data(await apiClient.get(`${CHALLANS}/returns`, { params: { financialYear: fyForApi(fy) } })) ?? [];
  },

  async markFiled(fy, quarter, tokenNumber, filedOn) {
    return data(
      await apiClient.post(`${CHALLANS}/returns/${enc(fyForApi(fy))}/${enc(quarter)}`, { tokenNumber, filedOn }),
    );
  },

  async reopen(fy, quarter) {
    await apiClient.delete(`${CHALLANS}/returns/${enc(fyForApi(fy))}/${enc(quarter)}`);
  },

  async getAnnexure1Preview(fy, quarter) {
    return data(await apiClient.get(`${RETURN}/${enc(fyForApi(fy))}/${enc(quarter)}/annexure-1`));
  },

  /** 'CSV' for the statement, 'TXT' for the Form 138 portal file. */
  async exportAnnexure1(fy, quarter, format = 'CSV') {
    const res = await apiClient.get(`${RETURN}/${enc(fyForApi(fy))}/${enc(quarter)}/annexure-1/export`, {
      params: { format },
      responseType: 'blob',
    });
    return res.data;
  },

  async getAnnexure2Preview(fy) {
    return data(await apiClient.get(`${RETURN}/${enc(fyForApi(fy))}/annexure-2`));
  },

  async exportAnnexure2(fy) {
    const res = await apiClient.get(`${RETURN}/${enc(fyForApi(fy))}/annexure-2/export`, {
      params: { format: 'CSV' },
      responseType: 'blob',
    });
    return res.data;
  },

  /** The deductor's details, or null when none are saved yet. */
  async getDeductor() {
    try {
      return data(await apiClient.get(DEDUCTOR));
    } catch (err) {
      if (err?.status === 404) return null;
      throw err;
    }
  },

  async saveDeductor(body) {
    return data(await apiClient.put(DEDUCTOR, body));
  },
};

/** Saves a downloaded file. */
export function saveFile(blob, fileName) {
  const url = window.URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', fileName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
