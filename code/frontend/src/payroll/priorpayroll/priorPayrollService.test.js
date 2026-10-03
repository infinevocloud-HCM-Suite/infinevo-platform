import { describe, it, expect, vi, beforeEach } from 'vitest';
import { priorPayrollService } from './priorPayrollService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const envelope = (data, status = 200) => ({ data: { status, message: 'ok', data } });

describe('priorPayrollService (W-47.6 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('template returns raw CSV text without envelope', async () => {
    const csv = 'employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay\n';
    apiClient.get.mockResolvedValueOnce({ data: csv });

    const result = await priorPayrollService.template();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/prior-payroll/template', {
      responseType: 'text',
    });
    expect(result).toBe(csv);
  });

  it('upload posts FormData with kind=EMPLOYEE_DOCUMENT and multipart header and returns document id', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'doc-1', fileName: 'prior.csv' } });
    const file = new File(['a,b'], 'prior.csv', { type: 'text/csv' });

    const docId = await priorPayrollService.upload(file);

    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/documents',
      expect.any(FormData),
      {
        params: { kind: 'EMPLOYEE_DOCUMENT' },
        headers: { 'Content-Type': 'multipart/form-data' },
      }
    );
    const formArg = apiClient.post.mock.calls[0][1];
    expect(formArg.get('file')).toBe(file);
    expect(docId).toBe('doc-1');
  });

  it('import converts display financialYear to API format and returns unwrapped result', async () => {
    const importPayload = {
      id: 'imp-1',
      rows_total: 10,
      rows_imported: 8,
      rows_failed: 2,
    };
    apiClient.post.mockResolvedValueOnce(envelope(importPayload));

    const result = await priorPayrollService.import({
      documentId: 'doc-1',
      financialYear: '2026-27',
      dryRun: true,
    });

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/prior-payroll-imports', {
      documentId: 'doc-1',
      financialYear: '2026-2027',
      dryRun: true,
    });
    expect(result).toEqual(importPayload);
  });

  it('imports returns unwrapped page of imports', async () => {
    const pageData = { content: [{ id: 'imp-1' }], totalElements: 1 };
    apiClient.get.mockResolvedValueOnce(envelope(pageData));

    const result = await priorPayrollService.imports(1, 20);

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/prior-payroll-imports', {
      params: { page: 1, size: 20 },
    });
    expect(result).toEqual(pageData);
  });

  it('months calls endpoint with converted fy and returns unwrapped page', async () => {
    const pageData = { content: [{ id: 'm-1', period: '2026-04' }], totalElements: 1 };
    apiClient.get.mockResolvedValueOnce(envelope(pageData));

    const result = await priorPayrollService.months('2026-27', 0, 50);

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/prior-payroll', {
      params: { fy: '2026-2027', page: 0, size: 50 },
    });
    expect(result).toEqual(pageData);
  });

  it('remove deletes the specified prior payroll month', async () => {
    apiClient.delete.mockResolvedValueOnce({ status: 204 });

    await priorPayrollService.remove('m-1');

    expect(apiClient.delete).toHaveBeenCalledWith('/v1/payroll/prior-payroll/m-1');
  });

  it('status returns unwrapped status data with converted fy', async () => {
    const statusData = {
      financial_year: '2026-2027',
      missing_periods: ['2026-04'],
      setup_step_skipped: false,
    };
    apiClient.get.mockResolvedValueOnce(envelope(statusData));

    const result = await priorPayrollService.status('2026-27');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/prior-payroll/status', {
      params: { fy: '2026-2027' },
    });
    expect(result).toEqual(statusData);
  });

  it('errorFileLink returns the bare url without envelope', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: { url: 'https://storage/link/err.csv', expiresAt: '2026-10-02T19:00:00Z' },
    });

    const url = await priorPayrollService.errorFileLink('doc-9');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/documents/doc-9/link');
    expect(url).toBe('https://storage/link/err.csv');
  });
});
