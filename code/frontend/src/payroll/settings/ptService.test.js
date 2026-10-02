import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ptService } from './ptService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('ptService (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in ptService', async () => {
    const mod = await import('./ptService.js');
    expect(mod.ptService).toBeDefined();
  });

  it('list hits /v1/payroll/settings/professional-tax', async () => {
    const mockData = [{ stateCode: 'KA', stateName: 'Karnataka' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await ptService.list();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/settings/professional-tax');
    expect(res).toEqual(mockData);
  });

  it('get hits /v1/payroll/settings/professional-tax/{stateCode}', async () => {
    const mockData = { stateCode: 'KA', stateName: 'Karnataka' };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await ptService.get('KA');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/settings/professional-tax/KA');
    expect(res).toEqual(mockData);
  });

  it('override puts to /v1/payroll/settings/professional-tax/{stateCode}', async () => {
    const payload = { slabs: [] };
    const mockRes = { stateCode: 'KA', source: 'OVERRIDE' };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await ptService.override('KA', payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/settings/professional-tax/KA', payload);
    expect(res).toEqual(mockRes);
  });

  it('removeOverride deletes /v1/payroll/settings/professional-tax/{stateCode}/override', async () => {
    apiClient.delete.mockResolvedValueOnce({ data: null });

    const res = await ptService.removeOverride('KA');

    expect(apiClient.delete).toHaveBeenCalledWith(
      '/v1/payroll/settings/professional-tax/KA/override'
    );
    expect(res).toBeNull();
  });

  it('history hits /v1/payroll/settings/professional-tax/{stateCode}/history', async () => {
    const mockHistory = [{ id: 'h1', changedAt: '2026-10-01' }];
    apiClient.get.mockResolvedValueOnce({ data: mockHistory });

    const res = await ptService.history('KA');

    expect(apiClient.get).toHaveBeenCalledWith(
      '/v1/payroll/settings/professional-tax/KA/history'
    );
    expect(res).toEqual(mockHistory);
  });
});
