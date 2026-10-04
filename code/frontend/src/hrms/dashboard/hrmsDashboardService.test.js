import { describe, it, expect, vi } from 'vitest';
import { hrmsDashboardService } from './hrmsDashboardService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

describe('hrmsDashboardService (W-48.6 §7)', () => {
  it('summary() hits /v1/hrms/dashboard and unwraps data.data', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: { status: 'OK', message: 'ok', data: { as_of: '2026-10-04' } },
    });
    await expect(hrmsDashboardService.summary()).resolves.toEqual({
      as_of: '2026-10-04',
    });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/dashboard');
  });
});
