import { describe, it, expect, vi, beforeEach } from 'vitest';
import { statutoryService } from './statutoryService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('statutoryService (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in statutoryService', async () => {
    const mod = await import('./statutoryService.js');
    expect(mod.statutoryService).toBeDefined();
  });

  it('get hits /v1/payroll/settings/{kind}', async () => {
    const mockData = { isEnabled: true, source: 'CUSTOM' };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await statutoryService.get('epf');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/settings/epf');
    expect(res).toEqual(mockData);
  });

  it('save puts to /v1/payroll/settings/{kind} with payload', async () => {
    const payload = { isEnabled: true, employeeRate: '12.0000' };
    const mockRes = { id: 'epf-1', ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await statutoryService.save('epf', payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/settings/epf', payload);
    expect(res).toEqual(mockRes);
  });
});
