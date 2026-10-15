import { describe, it, expect, vi, beforeEach } from 'vitest';
import { form16Service } from './form16Service';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('form16Service', () => {
  const fy = '2026-27';
  const apiFy = '2026-2027';
  const employeeId = 'emp-uuid-1234';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getStatement', () => {
    it('calls GET officer Form 16 endpoint with encoded employeeId and normalized fy', async () => {
      const mockStatement = {
        financialYear: apiFy,
        assessmentYear: '2027-2028',
        final: false,
        totalDeducted: 5280.0,
      };
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockStatement } });

      const res = await form16Service.getStatement(employeeId, fy);
      expect(apiClient.get).toHaveBeenCalledWith(
        `/v1/payroll/employees/${employeeId}/form16/${apiFy}`
      );
      expect(res).toEqual(mockStatement);
    });
  });

  describe('getOwnStatement', () => {
    it('calls GET self-service Form 16 endpoint with normalized fy', async () => {
      const mockStatement = {
        financialYear: apiFy,
        assessmentYear: '2027-2028',
        final: false,
        totalDeducted: 5280.0,
      };
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockStatement } });

      const res = await form16Service.getOwnStatement(fy);
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/me/form16/${apiFy}`);
      expect(res).toEqual(mockStatement);
    });
  });

  describe('uploadPartA', () => {
    it('posts FormData with multipart header to officer part-a endpoint', async () => {
      const mockResult = {
        matched: 1,
        unmatched: [],
        skipped: [],
      };
      apiClient.post.mockResolvedValueOnce({ data: { status: 200, data: mockResult } });

      const mockFile = new Blob(['dummy zip content'], { type: 'application/zip' });
      const res = await form16Service.uploadPartA(fy, mockFile);

      expect(apiClient.post).toHaveBeenCalledWith(
        `/v1/payroll/form16/${apiFy}/part-a`,
        expect.any(FormData),
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );
      expect(res).toEqual(mockResult);
    });
  });

  describe('listPartA', () => {
    it('calls GET officer part-a list endpoint and unwraps array', async () => {
      const mockRows = [
        {
          employee_id: employeeId,
          document_id: 'doc-uuid-5678',
          source_file_name: 'ABCDE1234F_PartA.pdf',
          uploaded_at: '2026-10-15T12:00:00Z',
        },
      ];
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockRows } });

      const res = await form16Service.listPartA(fy);
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/payroll/form16/${apiFy}/part-a`);
      expect(res).toEqual(mockRows);
    });

    it('returns empty array if data is missing', async () => {
      apiClient.get.mockResolvedValueOnce({ data: {} });
      const res = await form16Service.listPartA(fy);
      expect(res).toEqual([]);
    });
  });

  describe('getOwnPartA', () => {
    it('calls GET self-service part-a endpoint and unwraps data', async () => {
      const mockPartAOwn = {
        document_id: 'doc-uuid-5678',
        link: 'https://example.com/download/part-a.pdf?token=xyz',
        expires_at: '2026-10-15T12:15:00Z',
      };
      apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockPartAOwn } });

      const res = await form16Service.getOwnPartA(fy);
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/me/form16/${apiFy}/part-a`);
      expect(res).toEqual(mockPartAOwn);
    });
  });

  describe('getDocumentDownloadLink', () => {
    it('calls GET documents link endpoint with documentId', async () => {
      const mockLink = {
        url: 'https://example.com/download/file.pdf?token=abc',
        expiresAt: '2026-10-15T12:15:00Z',
      };
      apiClient.get.mockResolvedValueOnce({ data: mockLink });

      const res = await form16Service.getDocumentDownloadLink('doc-123');
      expect(apiClient.get).toHaveBeenCalledWith('/v1/documents/doc-123/link');
      expect(res).toEqual(mockLink);
    });
  });
});
