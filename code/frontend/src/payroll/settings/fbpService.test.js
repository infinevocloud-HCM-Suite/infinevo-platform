import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fbpService } from './fbpService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('fbpService (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in fbpService', async () => {
    const mod = await import('./fbpService.js');
    expect(mod.fbpService).toBeDefined();
  });

  it('plan hits /v1/payroll/fbp/plan', async () => {
    const mockPlan = { isEnabled: true, isLocked: false };
    apiClient.get.mockResolvedValueOnce({ data: mockPlan });

    const res = await fbpService.plan();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/fbp/plan');
    expect(res).toEqual(mockPlan);
  });

  it('savePlan puts to /v1/payroll/fbp/plan', async () => {
    const payload = { isEnabled: true };
    const mockRes = { id: 'p1', ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await fbpService.savePlan(payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/fbp/plan', payload);
    expect(res).toEqual(mockRes);
  });

  it('lock posts to /v1/payroll/fbp/plan/lock', async () => {
    const mockRes = { isLocked: true };
    apiClient.post.mockResolvedValueOnce({ data: mockRes });

    const res = await fbpService.lock();

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/fbp/plan/lock');
    expect(res).toEqual(mockRes);
  });

  it('unlock posts to /v1/payroll/fbp/plan/unlock', async () => {
    const mockRes = { isLocked: false };
    apiClient.post.mockResolvedValueOnce({ data: mockRes });

    const res = await fbpService.unlock();

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/fbp/plan/unlock');
    expect(res).toEqual(mockRes);
  });

  it('components hits /v1/payroll/fbp/components', async () => {
    const mockComps = [{ id: 'c1', name: 'Meal Pass' }];
    apiClient.get.mockResolvedValueOnce({ data: mockComps });

    const res = await fbpService.components();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/fbp/components');
    expect(res).toEqual(mockComps);
  });

  it('declaration hits /v1/payroll/employees/{id}/fbp-declaration with asOf', async () => {
    const mockDecl = { totalPool: '100000', lines: [] };
    apiClient.get.mockResolvedValueOnce({ data: mockDecl });

    const res = await fbpService.declaration('emp-1', '2026-10-01');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/fbp-declaration', {
      params: { asOf: '2026-10-01' },
    });
    expect(res).toEqual(mockDecl);
  });

  it('setDeclaration puts to /v1/payroll/employees/{id}/fbp-declaration with lines payload', async () => {
    const lines = [{ componentId: 'c1', annualAmount: '24000' }];
    const mockRes = { totalPool: '100000', lines };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await fbpService.setDeclaration('emp-1', lines);

    expect(apiClient.put).toHaveBeenCalledWith(
      '/v1/payroll/employees/emp-1/fbp-declaration',
      { lines }
    );
    expect(res).toEqual(mockRes);
  });
});
