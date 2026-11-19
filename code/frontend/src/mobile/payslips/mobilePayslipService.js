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
 * Validates that binary buffer begins with %PDF magic bytes (0x25, 0x50, 0x44, 0x46).
 */
export function isValidPdfBytes(buffer) {
  if (!buffer) return false;
  const bytes = new Uint8Array(buffer instanceof ArrayBuffer ? buffer : buffer.buffer || buffer);
  if (bytes.byteLength < 4) return false;
  return (
    bytes[0] === 0x25 && // %
    bytes[1] === 0x50 && // P
    bytes[2] === 0x44 && // D
    bytes[3] === 0x46    // F
  );
}

/**
 * Converts ArrayBuffer / Uint8Array to base64 string.
 */
function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer instanceof ArrayBuffer ? buffer : buffer.buffer || buffer);
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  if (typeof btoa === 'function') {
    return btoa(binary);
  }
  if (typeof globalThis !== 'undefined' && globalThis.Buffer) {
    return globalThis.Buffer.from(binary, 'binary').toString('base64');
  }
  return '';
}

/**
 * Mobile Payslip Service (W-98.4)
 * Handles retrieving published payslips, detailed breakdown,
 * and saving/sharing real signed PDF documents via Capacitor native plugins.
 */
export const mobilePayslipService = {
  /**
   * Fetches published payslips, newest first.
   * Money values are never converted with Number() - kept as received.
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
      netPay: r.net_pay ?? r.netPay ?? '0.00',
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
   * Requests a short-lived link token for caller's own payslip (W-98.4 §4).
   *
   * @param {string} payrunId
   * @returns {Promise<{ token: string, expiresAt: string }>}
   */
  async getLink(payrunId) {
    if (!payrunId) {
      throw new Error('Payrun ID is required');
    }
    const res = await apiClient.post(
      `/v1/me/payslips/${encodeURIComponent(payrunId)}/link`
    );
    const data = unwrapEnvelope(res);
    return {
      token: data?.token,
      expiresAt: data?.expires_at || data?.expiresAt,
    };
  },

  /**
   * Downloads binary payslip PDF bytes through signed open endpoint.
   * Validates application/pdf content type and %PDF header magic bytes.
   *
   * @param {string} token link token
   * @returns {Promise<ArrayBuffer>}
   */
  async downloadPdf(token) {
    if (!token) {
      throw new Error('Link token is required');
    }
    const res = await apiClient.get('/v1/payroll/payslips/open', {
      params: { t: token, format: 'pdf' },
      responseType: 'arraybuffer',
    });

    const contentType =
      res?.headers?.['content-type'] || res?.headers?.['Content-Type'] || '';
    if (!contentType.includes('application/pdf')) {
      throw new Error(
        `Invalid response: expected application/pdf, received ${contentType}`
      );
    }

    const buffer = res?.data;
    if (!isValidPdfBytes(buffer)) {
      throw new Error('Invalid PDF content: missing %PDF header');
    }

    return buffer;
  },

  /**
   * Downloads and opens native share sheet for the given payslip.
   * 1. Requests signed link token from backend
   * 2. Downloads real PDF bytes and verifies magic header
   * 3. Writes to app-private cache directory (Directory.Cache)
   * 4. Shares file URI via native share sheet
   *
   * @param {Object} params
   * @param {string} params.payrunId
   * @param {string} [params.period]
   * @returns {Promise<{ success: boolean, fileName: string, uri: string|null }>}
   */
  async downloadAndSharePayslip({ payrunId, period }) {
    if (!payrunId) {
      throw new Error('Payrun ID is required');
    }

    const safePeriod = period || dayjs().format('YYYY-MM');
    const fileName = `payslip-${safePeriod}.pdf`;

    const { token } = await this.getLink(payrunId);
    const pdfBuffer = await this.downloadPdf(token);

    const isNative = Capacitor.isNativePlatform();

    if (isNative) {
      try {
        const base64Data = arrayBufferToBase64(pdfBuffer);

        // 1. Write file to app-private Cache directory (Directory.Cache)
        await Filesystem.writeFile({
          path: fileName,
          data: base64Data,
          directory: Directory.Cache,
        });

        // 2. Resolve native file URI
        const uriResult = await Filesystem.getUri({
          path: fileName,
          directory: Directory.Cache,
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
      } catch (err) {
        // Clean up partial file on failure
        await Filesystem.deleteFile({
          path: fileName,
          directory: Directory.Cache,
        }).catch(() => {});
        throw err;
      }
    }

    // Web fallback: download blob via DOM anchor
    if (typeof window !== 'undefined') {
      const blob = new Blob([pdfBuffer], { type: 'application/pdf' });
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

  /**
   * Cleans up cached payslip files from app-private Cache directory (Directory.Cache).
   * Called on logout and on app startup (W-98.4 §2).
   */
  async cleanup() {
    if (!Capacitor.isNativePlatform()) return;
    try {
      const res = await Filesystem.readdir({
        path: '',
        directory: Directory.Cache,
      });
      const files = res?.files || [];
      for (const file of files) {
        const name = typeof file === 'string' ? file : file?.name;
        if (name && (name.startsWith('payslip-') || name.endsWith('.pdf'))) {
          await Filesystem.deleteFile({
            path: name,
            directory: Directory.Cache,
          }).catch(() => {});
        }
      }
    } catch {
      // Best-effort cleanup
    }
  },
};
