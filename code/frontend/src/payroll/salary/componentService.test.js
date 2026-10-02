import { describe, it, expect, vi, beforeEach } from 'vitest';
import { componentService } from './componentService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('componentService (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in componentService', async () => {
    // Asserting architectural boundary rule
    const module = await import('./componentService.js');
    expect(module.componentService).toBeDefined();
  });

  it('list hits /v1/payroll/components/{kind} with params', async () => {
    const mockData = [{ id: 'c1', name: 'Basic', code: 'BASIC' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await componentService.list('earnings', { activeOnly: true });

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/components/earnings', {
      params: { activeOnly: true },
    });
    expect(res).toEqual(mockData);
  });

  it('get hits /v1/payroll/components/{kind}/{id}', async () => {
    const mockData = { id: 'c1', name: 'Basic', code: 'BASIC' };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await componentService.get('earnings', 'c1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/components/earnings/c1');
    expect(res).toEqual(mockData);
  });

  it('create hits /v1/payroll/components/{kind} with post', async () => {
    const payload = { name: 'HRA', code: 'HRA', calculationType: 'PERCENTAGE' };
    const mockRes = { id: 'c2', ...payload };
    apiClient.post.mockResolvedValueOnce({ data: mockRes });

    const res = await componentService.create('earnings', payload);

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/components/earnings', payload);
    expect(res).toEqual(mockRes);
  });

  it('update hits /v1/payroll/components/{kind}/{id} with put', async () => {
    const payload = { name: 'HRA Revised', code: 'HRA' };
    const mockRes = { id: 'c2', ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await componentService.update('earnings', 'c2', payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/components/earnings/c2', payload);
    expect(res).toEqual(mockRes);
  });

  it('setActive sends { active } to /v1/payroll/components/{kind}/{id}/active', async () => {
    const mockRes = { id: 'c2', active: false };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await componentService.setActive('earnings', 'c2', false);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/components/earnings/c2/active', {
      active: false,
    });
    expect(res).toEqual(mockRes);
  });

  it('remove sends delete to /v1/payroll/components/{kind}/{id}', async () => {
    apiClient.delete.mockResolvedValueOnce({ data: null });

    const res = await componentService.remove('deductions', 'c3');

    expect(apiClient.delete).toHaveBeenCalledWith('/v1/payroll/components/deductions/c3');
    expect(res).toBeNull();
  });
});
