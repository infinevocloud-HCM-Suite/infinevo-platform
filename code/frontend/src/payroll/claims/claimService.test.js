import { describe, it, expect, vi, beforeEach } from 'vitest';
import { claimService } from './claimService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const envelope = (data) => ({ data: { status: 'OK', message: 'done', data } });

describe('claimService (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('components reads the claimable list and unwraps data.data', async () => {
    const rows = [{ id: 'c1', code: 'FUEL', name: 'Fuel', max_limit: 2000 }];
    apiClient.get.mockResolvedValueOnce(envelope(rows));

    await expect(claimService.components()).resolves.toEqual(rows);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/reimbursement-claims/components');
  });

  it('submit posts the body to /v1/me/reimbursement-claims and unwraps data.data', async () => {
    const body = { reimbursement_id: 'c1', requested_amount: '2500.00', bill_date: '2026-09-30' };
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'claim-1', status: 'SUBMITTED' }));

    await expect(claimService.submit(body)).resolves.toEqual({ id: 'claim-1', status: 'SUBMITTED' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/me/reimbursement-claims', body);
  });

  it('listOwn and getOwn read the employee paths and unwrap data.data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([{ id: 'claim-1' }]));
    await expect(claimService.listOwn()).resolves.toEqual([{ id: 'claim-1' }]);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/me/reimbursement-claims');

    apiClient.get.mockResolvedValueOnce(envelope({ id: 'claim-1' }));
    await expect(claimService.getOwn('claim-1')).resolves.toEqual({ id: 'claim-1' });
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/me/reimbursement-claims/claim-1');
  });

  it('list sends only the filters given and returns the Page from data.data', async () => {
    const page = { content: [{ id: 'claim-1' }], totalElements: 1 };
    apiClient.get.mockResolvedValueOnce(envelope(page));

    const res = await claimService.list({
      employeeId: 'emp-1',
      status: 'APPROVED',
      from: '2026-09-01',
      to: '2026-09-30',
      page: 2,
      size: 25,
    });
    expect(res).toEqual(page);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/reimbursement-claims', {
      params: { employeeId: 'emp-1', status: 'APPROVED', from: '2026-09-01', to: '2026-09-30', page: 2, size: 25 },
    });

    apiClient.get.mockResolvedValueOnce(envelope(page));
    await claimService.list({ status: undefined, employeeId: '' });
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/reimbursement-claims', {
      params: { page: 0, size: 25 },
    });
  });

  it('get reads the officer path and unwraps data.data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ id: 'claim-9' }));
    await expect(claimService.get('claim-9')).resolves.toEqual({ id: 'claim-9' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/reimbursement-claims/claim-9');
  });

  it('searchEmployees reads the bare Page of core employees (no envelope)', async () => {
    const content = [{ id: 'e1', firstName: 'Asha', lastName: 'Rao', employeeNumber: 'E01' }];
    apiClient.get.mockResolvedValueOnce({ data: { content, totalElements: 1 } });

    await expect(claimService.searchEmployees('As')).resolves.toEqual(content);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', { params: { size: 20, q: 'As' } });
  });
});
