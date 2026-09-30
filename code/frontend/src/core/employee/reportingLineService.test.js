import { describe, it, expect, vi, beforeEach } from 'vitest';
import { reportingLineService } from './reportingLineService.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('reportingLineService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lines() hits /v1/employees/{id}/reporting-line', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [] });

    await reportingLineService.lines('emp-1', '2026-04-01');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/reporting-line', {
      params: { asOf: '2026-04-01' },
    });
  });

  it('managerChain() hits /v1/employees/{id}/manager-chain', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [] });

    await reportingLineService.managerChain('emp-1', '2026-04-01');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/manager-chain', {
      params: { asOf: '2026-04-01' },
    });
  });

  it('set() hits /v1/employees/{id}/reporting-line PUT with body', async () => {
    const body = { managerId: 'mgr-1', kind: 'PRIMARY', effectiveFrom: '2026-04-01' };
    apiClient.put.mockResolvedValueOnce({ data: body });

    const res = await reportingLineService.set('emp-1', body);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/employees/emp-1/reporting-line', body);
    expect(res).toEqual(body);
  });
});
