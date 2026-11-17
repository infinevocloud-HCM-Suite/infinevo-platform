import { apiClient } from '../../shared/api/client.js';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { Capacitor } from '@capacitor/core';
import dayjs from 'dayjs';

/**
 * Normalizes Spring page or raw array from /v1/me/payslips.
 */
function unwrapEnvelope(res) {
  const body = res?.data !== undefined ? res.data : res;
  return body && typeof body === 'object' && 'data' in body ? body.data : body;
}

/**
 * Mobile Payslip Service (W-98.4 §4.2)
 * Handles retrieving published payslips, detailed breakdown,
 * and saving/sharing PDF documents via Capacitor native plugins.
 */
export const mobilePayslipService = {
  /**
   * Fetches published payslips, newest first.
   *
   * @param {number} page zero-based page number
   * @param {number} size rows per page
   * @returns {Promise<{ rows: Array, total: number, latest: Object|null }>}
   */
  async getPayslips(page = 0, size = 12) {
    const res = await apiClient.get('/v1/me/payslips', {
      params: { page, size },
    });
    const data = unwrapEnvelope(res);

    const rows = Array.isArray(data)
      ? data
      : Array.isArray(data?.content)
      ? data.content
      : [];

    const total =
      data?.totalElements !== undefined
        ? Number(data.totalElements)
        : rows.length;

    const normalized = rows.map((r) => ({
      payrunId: r.payrun_id || r.payrunId || r.id,
      period: r.period,
      paidOn: r.paid_on || r.paidOn || r.pay_date || r.payDate,
      netPay: Number(r.net_pay ?? r.netPay ?? 0),
      status: r.status || 'PAID',
    }));

    return {
      rows: normalized,
      total,
      latest: normalized.length > 0 ? normalized[0] : null,
    };
  },

  /**
   * Fetches the detailed payslip breakdown for a given payrun.
   *
   * @param {string} payrunId
   * @returns {Promise<Object>}
   */
  async getPayslipDetail(payrunId) {
    if (!payrunId) {
      throw new Error('Payrun ID is required');
    }
    const res = await apiClient.get(
      `/v1/me/payslips/${encodeURIComponent(payrunId)}`
    );
    const data = unwrapEnvelope(res);
    return data || {};
  },

  /**
   * Generates a simple text/HTML representation base64 string for payslip PDF export.
   *
   * @param {Object} payslip
   * @returns {string} base64 encoded content
   */
  formatPayslipBase64(payslip) {
    const run = payslip?.run || {};
    const employer = payslip?.employer || {};
    const employee = payslip?.employee || {};
    const totals = payslip?.totals || {};
    const days = payslip?.days || {};

    const content = [
      `============================================================`,
      `                    ${employer.name || 'INFINEVO HCM'}`,
      `                     SALARY PAYSLIP`,
      `============================================================`,
      `Pay Period : ${run.period || 'N/A'}`,
      `Paid On    : ${run.paid_on || run.pay_date || 'N/A'}`,
      `------------------------------------------------------------`,
      `Employee   : ${employee.name || 'N/A'} (${employee.number || 'N/A'})`,
      `Designation: ${employee.designation || 'N/A'}`,
      `Department : ${employee.department || 'N/A'}`,
      `Payable / LOP Days: ${days.payable_days ?? days.paid_days ?? 0} / ${days.lop_days ?? 0}`,
      `------------------------------------------------------------`,
      `Gross Earnings    : ₹ ${Number(totals.gross_earnings ?? 0).toFixed(2)}`,
      `Total Deductions  : ₹ ${Number(totals.total_deductions ?? 0).toFixed(2)}`,
      `NET TAKE-HOME PAY : ₹ ${Number(totals.net_pay ?? 0).toFixed(2)}`,
      `============================================================`,
      `Generated securely via Infinevo Mobile App`,
    ].join('\n');

    if (typeof btoa === 'function') {
      return btoa(unescape(encodeURIComponent(content)));
    }
    if (typeof globalThis !== 'undefined' && globalThis.Buffer) {
      return globalThis.Buffer.from(content).toString('base64');
    }
    return '';
  },

  /**
   * Downloads and opens native share sheet for the given payslip.
   * Leverages @capacitor/filesystem to write to documents folder,
   * then @capacitor/share to open the OS share sheet.
   *
   * @param {Object} params
   * @param {string} params.payrunId
   * @param {string} [params.period]
   * @param {Object} [params.payslipData]
   * @returns {Promise<{ success: boolean, fileName: string, uri: string|null }>}
   */
  async downloadAndSharePayslip({ payrunId, period, payslipData = null }) {
    let detail = payslipData;
    if (!detail && payrunId) {
      detail = await this.getPayslipDetail(payrunId);
    }

    const safePeriod = period || detail?.run?.period || dayjs().format('YYYY-MM');
    const fileName = `Payslip_${safePeriod}.pdf`;
    const base64Data = this.formatPayslipBase64(detail);

    const isNative = Capacitor.isNativePlatform();

    if (isNative) {
      // 1. Write file to device Documents directory
      await Filesystem.writeFile({
        path: fileName,
        data: base64Data,
        directory: Directory.Documents,
      });

      // 2. Resolve native file URI
      const uriResult = await Filesystem.getUri({
        path: fileName,
        directory: Directory.Documents,
      });

      const fileUri = uriResult?.uri || '';

      // 3. Open native OS share sheet
      await Share.share({
        title: `Payslip - ${safePeriod}`,
        text: `Official Payslip for ${safePeriod}`,
        url: fileUri,
        dialogTitle: 'Share or Save Payslip',
      });

      return {
        success: true,
        fileName,
        uri: fileUri,
      };
    }

    // Web fallback: download blob via DOM anchor
    if (typeof window !== 'undefined') {
      const byteCharacters = atob(base64Data);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: 'application/pdf' });
      const blobUrl = window.URL.createObjectURL(blob);

      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(blobUrl);

      return {
        success: true,
        fileName,
        uri: blobUrl,
      };
    }

    return {
      success: true,
      fileName,
      uri: null,
    };
  },
};

