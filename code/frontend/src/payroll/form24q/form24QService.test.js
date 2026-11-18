import { describe, it, expect, vi, beforeEach } from 'vitest';
import { form24QService } from './form24QService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('form24QService (W-89.4)', () => {
  const fy = '2026-27';
  const apiFy = '2026-2027';
  const quarter = 'Q1';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('listChallans', () => {
    it('calls GET /v1/payroll/compliance/challans with normalized FY and quarter', async () => {
      const mockList = [{ id: 'ch-1', bsrCode: '0510304', challanSerialNo: '00123' }];
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockList } });

      const res = await form24QService.listChallans(fy, quarter);
      expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/compliance/challans', {
        params: { financialYear: apiFy, quarter: 'Q1' },
      });
      expect(res).toEqual(mockList);
    });

    it('returns empty array if response data is missing', async () => {
      apiClient.get.mockResolvedValueOnce({ data: {} });
      const res = await form24QService.listChallans(fy);
      expect(res).toEqual([]);
    });
  });

  describe('createChallan', () => {
    it('normalizes FY and posts challan payload', async () => {
      const payload = {
        financialYear: fy,
        quarter: 'Q1',
        bsrCode: '0510304',
        challanSerialNo: '00123',
        tenderDate: '2026-05-07',
        taxAmount: 10000,
        cessAmount: 400,
        totalAmount: 10400,
        minorHead: '200',
      };
      const mockCreated = { id: 'ch-new', ...payload, financialYear: apiFy };
      apiClient.post.mockResolvedValueOnce({ data: { status: 201, data: mockCreated } });

      const res = await form24QService.createChallan(payload);
      expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/compliance/challans', {
        ...payload,
        financialYear: apiFy,
      });
      expect(res).toEqual(mockCreated);
    });
  });

  describe('allocateChallan', () => {
    it('calls POST /v1/payroll/compliance/challans/{id}/allocations', async () => {
      const payload = { payRunId: 'pr-1', employeeAllocations: [] };
      apiClient.post.mockResolvedValueOnce({ data: { status: 200, data: { allocated: true } } });

      const res = await form24QService.allocateChallan('ch-1', payload);
      expect(apiClient.post).toHaveBeenCalledWith(
        '/v1/payroll/compliance/challans/ch-1/allocations',
        payload
      );
      expect(res).toEqual({ allocated: true });
    });
  });

  describe('getAnnexure1Preview', () => {
    it('calls GET /v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1', async () => {
      const mockPreview = {
        financialYear: apiFy,
        quarter: 'Q1',
        totalDeducteesCount: 5,
        totalTdsDeducted: 50000,
        totalTdsDeposited: 50000,
        isReconciled: true,
      };
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockPreview } });

      const res = await form24QService.getAnnexure1Preview(fy, quarter);
      expect(apiClient.get).toHaveBeenCalledWith(
        `/v1/payroll/compliance/24q/${apiFy}/Q1/annexure-1`
      );
      expect(res).toEqual(mockPreview);
    });
  });

  describe('exportAnnexure1', () => {
    it('calls GET export endpoint with format CSV and responseType blob', async () => {
      const mockBlob = new Blob(['sample csv'], { type: 'text/csv' });
      apiClient.get.mockResolvedValueOnce({ data: mockBlob });

      const res = await form24QService.exportAnnexure1(fy, quarter, 'CSV');
      expect(apiClient.get).toHaveBeenCalledWith(
        `/v1/payroll/compliance/24q/${apiFy}/Q1/annexure-1/export`,
        {
          params: { format: 'CSV' },
          responseType: 'blob',
        }
      );
      expect(res).toBe(mockBlob);
    });
  });

  describe('getAnnexure2Preview', () => {
    it('calls GET /v1/payroll/compliance/24q/{fy}/annexure-2', async () => {
      const mockPreview = {
        financialYear: apiFy,
        totalEmployeesCount: 12,
        totalGrossSalary: 1200000,
      };
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockPreview } });

      const res = await form24QService.getAnnexure2Preview(fy);
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/payroll/compliance/24q/${apiFy}/annexure-2`);
      expect(res).toEqual(mockPreview);
    });
  });

  describe('exportAnnexure2', () => {
    it('calls GET /v1/payroll/compliance/24q/{fy}/annexure-2/export with responseType blob', async () => {
      const mockBlob = new Blob(['sample a2 csv'], { type: 'text/csv' });
      apiClient.get.mockResolvedValueOnce({ data: mockBlob });

      const res = await form24QService.exportAnnexure2(fy, 'TXT');
      expect(apiClient.get).toHaveBeenCalledWith(
        `/v1/payroll/compliance/24q/${apiFy}/annexure-2/export`,
        {
          params: { format: 'TXT' },
          responseType: 'blob',
        }
      );
      expect(res).toBe(mockBlob);
    });
  });
});
