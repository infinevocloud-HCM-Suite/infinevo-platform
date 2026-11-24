import { describe, it, expect, vi, beforeEach } from 'vitest';
import { taxRecalcFailureService } from './taxRecalcFailureService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('taxRecalcFailureService (D-151)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('open reads the list out of the envelope, with or without a year', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: [{ id: 'f1' }] } });
    expect(await taxRecalcFailureService.open('2026-2027')).toEqual([{ id: 'f1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/tax-recalc-failures', { params: { fy: '2026-2027' } });

    apiClient.get.mockResolvedValueOnce({ data: { status: 200 } });
    expect(await taxRecalcFailureService.open()).toEqual([]);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/tax-recalc-failures', { params: {} });
  });

  it('retry posts to the failure and returns the new computation', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { status: 200, data: { annual_tax: 1000 } } });

    expect(await taxRecalcFailureService.retry('f1')).toEqual({ annual_tax: 1000 });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/tax-recalc-failures/f1/retry');
  });
});
