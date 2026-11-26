import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client';
import { form24QService } from './form24QService';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const ok = (data) => ({ data: { data } });

describe('form24QService (W-89)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists a quarter\'s challans with the year in YYYY-YYYY form', async () => {
    apiClient.get.mockResolvedValue(ok([{ id: 'c1' }]));
    expect(await form24QService.listChallans('2026-27', 'Q3')).toEqual([{ id: 'c1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/compliance/challans', {
      params: { financialYear: '2026-2027', quarter: 'Q3' },
    });
  });

  it('allocates one run\'s amounts as tax, with surcharge and cess zero', async () => {
    apiClient.post.mockResolvedValue(ok({ id: 'c1' }));
    await form24QService.allocate('c1', 'run-1', [{ employeeId: 'e1', amount: 1200.5 }]);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/compliance/challans/c1/allocate', {
      payRunId: 'run-1',
      employeeAllocations: [{ employeeId: 'e1', taxAmount: 1200.5, surchargeAmount: 0, cessAmount: 0 }],
    });
  });

  it('marks a quarter filed with its token and reopens it', async () => {
    apiClient.post.mockResolvedValue(ok({ quarter: 'Q2' }));
    await form24QService.markFiled('2026-27', 'Q2', '123456789012345', '2026-10-30');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/compliance/challans/returns/2026-2027/Q2', {
      tokenNumber: '123456789012345',
      filedOn: '2026-10-30',
    });
    await form24QService.reopen('2026-27', 'Q2');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/payroll/compliance/challans/returns/2026-2027/Q2');
  });

  it('downloads the Form 138 file as a blob', async () => {
    const blob = new Blob(['x']);
    apiClient.get.mockResolvedValue({ data: blob });
    expect(await form24QService.exportAnnexure1('2026-27', 'Q3', 'TXT')).toBe(blob);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/compliance/24q/2026-2027/Q3/annexure-1/export', {
      params: { format: 'TXT' },
      responseType: 'blob',
    });
  });

  it('reads no deductor as null, and passes other errors on', async () => {
    apiClient.get.mockRejectedValueOnce({ status: 404, message: 'not configured' });
    expect(await form24QService.getDeductor()).toBeNull();
    apiClient.get.mockRejectedValueOnce({ status: 500, message: 'boom' });
    await expect(form24QService.getDeductor()).rejects.toEqual({ status: 500, message: 'boom' });
  });
});
