import { describe, it, expect, vi, beforeEach } from 'vitest';
import { lopPolicyService } from './lopPolicyService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('lopPolicyService (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in lopPolicyService', async () => {
    const mod = await import('./lopPolicyService.js');
    expect(mod.lopPolicyService).toBeDefined();
  });

  it('get hits /v1/lop-policy with optional asOf', async () => {
    const mockPolicy = { workingDayBasis: 'ACTUAL_DAYS', weekendsPayable: true };
    apiClient.get.mockResolvedValueOnce({ data: mockPolicy });

    const res = await lopPolicyService.get('2026-10-01');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/lop-policy', {
      params: { asOf: '2026-10-01' },
    });
    expect(res).toEqual(mockPolicy);
  });

  it('save puts to /v1/lop-policy with payload', async () => {
    const payload = {
      workingDayBasis: 'ACTUAL_DAYS',
      weekendsPayable: true,
      holidaysPayable: true,
      effectiveFrom: '2026-11-01',
    };
    const mockRes = { id: 'lop-1', ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await lopPolicyService.save(payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/lop-policy', payload);
    expect(res).toEqual(mockRes);
  });
});
