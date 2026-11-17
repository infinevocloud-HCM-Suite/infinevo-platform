import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mobilePayslipService } from './mobilePayslipService.js';
import { apiClient } from '../../shared/api/client.js';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { Capacitor } from '@capacitor/core';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

vi.mock('@capacitor/filesystem', () => ({
  Filesystem: {
    writeFile: vi.fn(),
    getUri: vi.fn(),
  },
  Directory: {
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

describe('mobilePayslipService (W-98.4 §6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getPayslips', () => {
    it('fetches and normalizes payslip list with latest identification', async () => {
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

      const res = await mobilePayslipService.getPayslips(0, 10);

      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips', {
        params: { page: 0, size: 10 },
      });
      expect(res.rows).toHaveLength(2);
      expect(res.total).toBe(2);
      expect(res.latest).toEqual({
        payrunId: 'pr-101',
        period: '2026-10',
        paidOn: '2026-10-31',
        netPay: 68500,
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
          totals: { net_pay: 68500 },
        },
      };
      apiClient.get.mockResolvedValueOnce(mockDetail);

      const res = await mobilePayslipService.getPayslipDetail('pr-101');

      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips/pr-101');
      expect(res.run.period).toBe('2026-10');
      expect(res.employee.name).toBe('Krushna');
    });

    it('throws error when payrunId is missing', async () => {
      await expect(mobilePayslipService.getPayslipDetail('')).rejects.toThrow(
        'Payrun ID is required'
      );
    });
  });

  describe('downloadAndSharePayslip', () => {
    it('saves PDF and invokes Share.share on native platform', async () => {
      Capacitor.isNativePlatform.mockReturnValue(true);
      Filesystem.writeFile.mockResolvedValueOnce({ uri: 'file:///docs/Payslip_2026-10.pdf' });
      Filesystem.getUri.mockResolvedValueOnce({ uri: 'file:///docs/Payslip_2026-10.pdf' });
      Share.share.mockResolvedValueOnce({});

      const mockData = {
        run: { period: '2026-10', paid_on: '2026-10-31' },
        employer: { name: 'Infinevo Inc' },
        employee: { name: 'Krushna', number: 'EMP-001' },
        totals: { net_pay: 68500, gross_earnings: 75000, total_deductions: 6500 },
        days: { payable_days: 31, lop_days: 0 },
      };

      const result = await mobilePayslipService.downloadAndSharePayslip({
        payrunId: 'pr-101',
        period: '2026-10',
        payslipData: mockData,
      });

      expect(Filesystem.writeFile).toHaveBeenCalledWith(
        expect.objectContaining({
          path: 'Payslip_2026-10.pdf',
          directory: Directory.Documents,
        })
      );
      expect(Filesystem.getUri).toHaveBeenCalledWith({
        path: 'Payslip_2026-10.pdf',
        directory: Directory.Documents,
      });
      expect(Share.share).toHaveBeenCalledWith({
        title: 'Payslip - 2026-10',
        text: 'Official Payslip for 2026-10',
        url: 'file:///docs/Payslip_2026-10.pdf',
        dialogTitle: 'Share or Save Payslip',
      });
      expect(result.success).toBe(true);
      expect(result.fileName).toBe('Payslip_2026-10.pdf');
    });

    it('falls back to browser download when not native', async () => {
      Capacitor.isNativePlatform.mockReturnValue(false);

      const mockData = {
        run: { period: '2026-10' },
        employee: { name: 'Krushna' },
        totals: { net_pay: 68500 },
      };

      // Mock URL.createObjectURL and URL.revokeObjectURL in jsdom
      const createObjectURL = vi.fn().mockReturnValue('blob:mock-url');
      const revokeObjectURL = vi.fn();
      window.URL.createObjectURL = createObjectURL;
      window.URL.revokeObjectURL = revokeObjectURL;

      const result = await mobilePayslipService.downloadAndSharePayslip({
        payrunId: 'pr-101',
        period: '2026-10',
        payslipData: mockData,
      });

      expect(result.success).toBe(true);
      expect(createObjectURL).toHaveBeenCalled();
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
    });
  });
});

