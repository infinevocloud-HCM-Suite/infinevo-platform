import { describe, it, expect, vi, beforeEach } from 'vitest';
import { deductionService } from './deductionService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const envelope = (data) => ({ data: { status: 'OK', message: 'done', data } });

describe('deductionService (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('enter posts the lines as the body and unwraps data.data', async () => {
    const lines = [{ employee_id: 'e1', period: '2026-10', deduction_type: 'DAMAGE', amount: '500.00' }];
    apiClient.post.mockResolvedValueOnce(envelope({ count: 1, rows: [{ id: 'd1' }] }));

    await expect(deductionService.enter(lines)).resolves.toEqual({ count: 1, rows: [{ id: 'd1' }] });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/employee-deductions', lines);
  });

  it('list sends only the filters given and returns the Page from data.data', async () => {
    const page = { content: [{ id: 'd1' }], totalElements: 1 };
    apiClient.get.mockResolvedValueOnce(envelope(page));

    const res = await deductionService.list({
      employeeId: 'e1',
      period: '2026-10',
      status: 'POSTED',
      deductionType: 'PENALTY',
      page: 1,
      size: 50,
    });
    expect(res).toEqual(page);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employee-deductions', {
      params: { employeeId: 'e1', period: '2026-10', status: 'POSTED', deductionType: 'PENALTY', page: 1, size: 50 },
    });

    apiClient.get.mockResolvedValueOnce(envelope(page));
    await deductionService.list();
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/employee-deductions', {
      params: { page: 0, size: 25 },
    });
  });

  it('get reads one deduction and unwraps data.data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ id: 'd1' }));
    await expect(deductionService.get('d1')).resolves.toEqual({ id: 'd1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employee-deductions/d1');
  });

  it('reverse sends DELETE with the reason as a query parameter', async () => {
    apiClient.delete.mockResolvedValueOnce(envelope({ id: 'd1', status: 'REVERSED' }));

    await expect(deductionService.reverse('d1', 'Entered twice')).resolves.toEqual({
      id: 'd1',
      status: 'REVERSED',
    });
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/payroll/employee-deductions/d1', {
      params: { reason: 'Entered twice' },
    });
  });

  it('listOwn reads the employee path and unwraps data.data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([{ id: 'd1' }]));
    await expect(deductionService.listOwn()).resolves.toEqual([{ id: 'd1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/employee-deductions');
  });
});
