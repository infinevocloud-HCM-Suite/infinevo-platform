import { describe, it, expect, vi, beforeEach } from 'vitest';
import { salaryService } from './salaryService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('salaryService (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('asOf passes asOf query parameter to /v1/payroll/employees/{id}/salary', async () => {
    const mockSalary = { id: 'v1', annualCtc: '600000', monthlyCtc: '50000' };
    apiClient.get.mockResolvedValueOnce({ data: mockSalary });

    const res = await salaryService.asOf('emp-1', '2026-10-01');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/salary', {
      params: { asOf: '2026-10-01' },
    });
    expect(res).toEqual(mockSalary);
  });

  it('versions fetches history from /v1/payroll/employees/{id}/salary/versions', async () => {
    const mockVersions = [{ id: 'v1' }, { id: 'v2' }];
    apiClient.get.mockResolvedValueOnce({ data: mockVersions });

    const res = await salaryService.versions('emp-1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/salary/versions');
    expect(res).toEqual(mockVersions);
  });

  it('create posts to /v1/payroll/employees/{id}/salary', async () => {
    const payload = { annualCtc: '600000', effectiveFrom: '2026-10-01' };
    const mockCreated = { id: 'v1', ...payload };
    apiClient.post.mockResolvedValueOnce({ data: mockCreated });

    const res = await salaryService.create('emp-1', payload);

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/salary', payload);
    expect(res).toEqual(mockCreated);
  });

  it('revise posts to /v1/payroll/employees/{id}/salary/revisions', async () => {
    const payload = { annualCtc: '660000', effectiveFrom: '2026-11-01' };
    const mockRevised = { id: 'v2', ...payload, changeInPercent: 10 };
    apiClient.post.mockResolvedValueOnce({ data: mockRevised });

    const res = await salaryService.revise('emp-1', payload);

    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/payroll/employees/emp-1/salary/revisions',
      payload
    );
    expect(res).toEqual(mockRevised);
  });

  it('update puts to /v1/payroll/employees/{id}/salary/versions/{versionId}', async () => {
    const payload = { annualCtc: '660000', effectiveFrom: '2026-11-01' };
    const mockUpdated = { id: 'v2', ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockUpdated });

    const res = await salaryService.update('emp-1', 'v2', payload);

    expect(apiClient.put).toHaveBeenCalledWith(
      '/v1/payroll/employees/emp-1/salary/versions/v2',
      payload
    );
    expect(res).toEqual(mockUpdated);
  });

  it('cancel sends DELETE to /v1/payroll/employees/{id}/salary/versions/{versionId}', async () => {
    apiClient.delete.mockResolvedValueOnce({ data: null });

    const res = await salaryService.cancel('emp-1', 'v2');

    expect(apiClient.delete).toHaveBeenCalledWith(
      '/v1/payroll/employees/emp-1/salary/versions/v2'
    );
    expect(res).toBeNull();
  });
});
