import { describe, it, expect, vi, beforeEach } from 'vitest';
import { dashboardService } from './dashboardService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

describe('dashboardService (W-47.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('summary(2026) sends fy=2026 and unwraps data.data', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { status: 'OK', message: 'ok', data: { months: [] } } });
    await expect(dashboardService.summary(2026)).resolves.toEqual({ months: [] });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/dashboard', { params: { fy: 2026 } });
  });

  it('setupChecklist reads the bare reply as data', async () => {
    const body = { steps: [{ code: 'PAY_SCHEDULE', module: 'PAYROLL', completed: false, skipped: false }] };
    apiClient.get.mockResolvedValueOnce({ data: body });
    await expect(dashboardService.setupChecklist()).resolves.toEqual(body);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/setup-checklist');
  });
});
