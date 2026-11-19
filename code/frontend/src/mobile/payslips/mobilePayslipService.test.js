import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mobilePayslipService, isValidPdfBytes } from './mobilePayslipService.js';
import { apiClient } from '../../shared/api/client.js';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { Capacitor } from '@capacitor/core';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@capacitor/filesystem', () => ({
  Filesystem: {
    writeFile: vi.fn(),
    getUri: vi.fn(),
    deleteFile: vi.fn(),
    readdir: vi.fn(),
  },
  Directory: {
    Cache: 'CACHE',
    Documents: 'DOCUMENTS',
  },
}));

vi.mock('@capacitor/share', () => ({
  Share: {
    share: vi.fn(),
  },
}));

vi.mock('@capacitor/core', () => ({
  Capacitor: {
    isNativePlatform: vi.fn(),
  },
}));

function makePdfBuffer(header = '%PDF-1.4\n...') {
  const enc = new TextEncoder();
  return enc.encode(header).buffer;
}

describe('mobilePayslipService (W-98.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('isValidPdfBytes', () => {
    it('returns true for buffers starting with %PDF', () => {
      expect(isValidPdfBytes(makePdfBuffer('%PDF-1.7'))).toBe(true);
    });

    it('returns false for plain text or invalid headers', () => {
      expect(isValidPdfBytes(makePdfBuffer('Plain text'))).toBe(false);
      expect(isValidPdfBytes(new ArrayBuffer(0))).toBe(false);
      expect(isValidPdfBytes(null)).toBe(false);
    });
  });

  describe('getPayslips', () => {
    it('fetches and normalizes payslip list without converting amounts via Number()', async () => {
      const mockEnvelope = {
        data: {
          content: [
            {
              payrun_id: 'pr-101',
              period: '2026-10',
              paid_on: '2026-10-31',
              net_pay: '68500.00',
              status: 'PAID',
            },
            {
              payrun_id: 'pr-100',
              period: '2026-09',
              paid_on: '2026-09-30',
              net_pay: '68500.00',
              status: 'PAID',
            },
          ],
          totalElements: 2,
        },
      };
      apiClient.get.mockResolvedValueOnce(mockEnvelope);

      const res = await mobilePayslipService.getPayslips(0, 12);

      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips', {
        params: { page: 0, size: 12 },
      });
      expect(res.rows).toHaveLength(2);
      expect(res.total).toBe(2);
      // Amounts are never converted with Number()
      expect(res.latest.netPay).toBe('68500.00');
      expect(typeof res.latest.netPay).toBe('string');
      expect(res.latest).toEqual({
        payrunId: 'pr-101',
        period: '2026-10',
        paidOn: '2026-10-31',
        netPay: '68500.00',
        status: 'PAID',
      });
    });

    it('handles empty response gracefully', async () => {
      apiClient.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });

      const res = await mobilePayslipService.getPayslips();

      expect(res.rows).toEqual([]);
      expect(res.total).toBe(0);
      expect(res.latest).toBeNull();
    });
  });

  describe('getPayslipDetail', () => {
    it('fetches detailed payslip breakdown by payrunId', async () => {
      const mockDetail = {
        data: {
          run: { payrun_id: 'pr-101', period: '2026-10' },
          employer: { name: 'Infinevo Inc' },
          employee: { id: 'emp-1', name: 'Krushna' },
          totals: { net_pay: '68500.00' },
        },
      };
      apiClient.get.mockResolvedValueOnce(mockDetail);

      const res = await mobilePayslipService.getPayslipDetail('pr-101');

      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips/pr-101');
      expect(res.run.period).toBe('2026-10');
      expect(res.employee.name).toBe('Krushna');
      expect(res.totals.net_pay).toBe('68500.00');
    });

    it('throws error when payrunId is missing', async () => {
      await expect(mobilePayslipService.getPayslipDetail('')).rejects.toThrow(
        'Payrun ID is required'
      );
    });
  });

  describe('getLink', () => {
    it('requests signed link token for caller own payslip', async () => {
      apiClient.post.mockResolvedValueOnce({
        data: { token: 'signed-token-123', expires_at: '2026-11-19T12:05:00Z' },
      });

      const res = await mobilePayslipService.getLink('pr-101');

      expect(apiClient.post).toHaveBeenCalledWith('/v1/me/payslips/pr-101/link');
      expect(res.token).toBe('signed-token-123');
      expect(res.expiresAt).toBe('2026-11-19T12:05:00Z');
    });

    it('throws when payrunId is missing', async () => {
      await expect(mobilePayslipService.getLink('')).rejects.toThrow('Payrun ID is required');
    });
  });

  describe('downloadPdf', () => {
    it('downloads and validates real PDF binary response', async () => {
      const validBuffer = makePdfBuffer('%PDF-1.4\nBinary content...');
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/pdf' },
        data: validBuffer,
      });

      const buffer = await mobilePayslipService.downloadPdf('tok-1');
      expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payslips/open', {
        params: { t: 'tok-1', format: 'pdf' },
        responseType: 'arraybuffer',
      });
      expect(buffer).toBe(validBuffer);
    });

    it('rejects when response content-type is not application/pdf', async () => {
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/json' },
        data: makePdfBuffer('%PDF-1.4'),
      });

      await expect(mobilePayslipService.downloadPdf('tok-1')).rejects.toThrow(
        /expected application\/pdf/
      );
    });

    it('rejects when response does not start with %PDF magic header', async () => {
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/pdf' },
        data: makePdfBuffer('Text without pdf header'),
      });

      await expect(mobilePayslipService.downloadPdf('tok-1')).rejects.toThrow(
        /missing %PDF header/
      );
    });
  });

  describe('downloadAndSharePayslip', () => {
    it('writes PDF to Directory.Cache and invokes Share.share on native platform', async () => {
      Capacitor.isNativePlatform.mockReturnValue(true);
      apiClient.post.mockResolvedValueOnce({
        data: { token: 'valid-token' },
      });
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/pdf' },
        data: makePdfBuffer('%PDF-1.4\nTest PDF'),
      });
      Filesystem.writeFile.mockResolvedValueOnce({});
      Filesystem.getUri.mockResolvedValueOnce({ uri: 'file:///cache/payslip-2026-10.pdf' });
      Share.share.mockResolvedValueOnce({});

      const result = await mobilePayslipService.downloadAndSharePayslip({
        payrunId: 'pr-101',
        period: '2026-10',
      });

      expect(Filesystem.writeFile).toHaveBeenCalledWith(
        expect.objectContaining({
          path: 'payslip-2026-10.pdf',
          directory: Directory.Cache,
        })
      );
      // Ensures Directory.Documents is NOT used
      expect(Filesystem.writeFile).not.toHaveBeenCalledWith(
        expect.objectContaining({ directory: Directory.Documents })
      );
      expect(Filesystem.getUri).toHaveBeenCalledWith({
        path: 'payslip-2026-10.pdf',
        directory: Directory.Cache,
      });
      expect(Share.share).toHaveBeenCalledWith({
        title: 'Payslip - 2026-10',
        text: 'Official Payslip for 2026-10',
        url: 'file:///cache/payslip-2026-10.pdf',
        dialogTitle: 'Share or Save Payslip',
      });
      expect(result.success).toBe(true);
      expect(result.fileName).toBe('payslip-2026-10.pdf');
    });

    it('cleans up partial file from Directory.Cache on failure', async () => {
      Capacitor.isNativePlatform.mockReturnValue(true);
      apiClient.post.mockResolvedValueOnce({ data: { token: 'valid-token' } });
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/pdf' },
        data: makePdfBuffer('%PDF-1.4'),
      });
      Filesystem.writeFile.mockResolvedValueOnce({});
      Filesystem.getUri.mockRejectedValueOnce(new Error('Permission denied'));
      Filesystem.deleteFile.mockResolvedValueOnce({});

      await expect(
        mobilePayslipService.downloadAndSharePayslip({
          payrunId: 'pr-101',
          period: '2026-10',
        })
      ).rejects.toThrow('Permission denied');

      expect(Filesystem.deleteFile).toHaveBeenCalledWith({
        path: 'payslip-2026-10.pdf',
        directory: Directory.Cache,
      });
    });

    it('falls back to browser download when not native', async () => {
      Capacitor.isNativePlatform.mockReturnValue(false);
      apiClient.post.mockResolvedValueOnce({ data: { token: 'valid-token' } });
      apiClient.get.mockResolvedValueOnce({
        headers: { 'content-type': 'application/pdf' },
        data: makePdfBuffer('%PDF-1.4'),
      });

      const createObjectURL = vi.fn().mockReturnValue('blob:mock-url');
      const revokeObjectURL = vi.fn();
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = revokeObjectURL;

      const result = await mobilePayslipService.downloadAndSharePayslip({
        payrunId: 'pr-101',
        period: '2026-10',
      });

      expect(result.success).toBe(true);
      expect(createObjectURL).toHaveBeenCalled();
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
    });
  });

  describe('cleanup', () => {
    it('deletes cached payslip files from Directory.Cache on native platform', async () => {
      Capacitor.isNativePlatform.mockReturnValue(true);
      Filesystem.readdir.mockResolvedValueOnce({
        files: ['payslip-2026-10.pdf', 'other-cached.tmp', 'payslip-2026-09.pdf'],
      });
      Filesystem.deleteFile.mockResolvedValue({});

      await mobilePayslipService.cleanup();

      expect(Filesystem.readdir).toHaveBeenCalledWith({
        path: '',
        directory: Directory.Cache,
      });
      expect(Filesystem.deleteFile).toHaveBeenCalledWith({
        path: 'payslip-2026-10.pdf',
        directory: Directory.Cache,
      });
      expect(Filesystem.deleteFile).toHaveBeenCalledWith({
        path: 'payslip-2026-09.pdf',
        directory: Directory.Cache,
      });
      expect(Filesystem.deleteFile).not.toHaveBeenCalledWith(
        expect.objectContaining({ path: 'other-cached.tmp' })
      );
    });

    it('does nothing when not on native platform', async () => {
      Capacitor.isNativePlatform.mockReturnValue(false);
      await mobilePayslipService.cleanup();
      expect(Filesystem.readdir).not.toHaveBeenCalled();
    });
  });
});
