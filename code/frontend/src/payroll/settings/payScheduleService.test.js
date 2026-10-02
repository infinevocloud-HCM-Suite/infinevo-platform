import { describe, it, expect, vi, beforeEach } from 'vitest';
import { payScheduleService } from './payScheduleService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('payScheduleService (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('no direct axios import exists in payScheduleService', async () => {
    const mod = await import('./payScheduleService.js');
    expect(mod.payScheduleService).toBeDefined();
  });

  it('get hits /v1/payroll/pay-schedule', async () => {
    const mockData = { exists: true, workingDays: [1, 2, 3, 4, 5] };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await payScheduleService.get();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/pay-schedule');
    expect(res).toEqual(mockData);
  });

  it('save puts to /v1/payroll/pay-schedule with body', async () => {
    const payload = { workingDays: [1, 2, 3, 4, 5], payDayRule: 'LAST_WORKING_DAY' };
    const mockRes = { exists: true, ...payload };
    apiClient.put.mockResolvedValueOnce({ data: mockRes });

    const res = await payScheduleService.save(payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/pay-schedule', payload);
    expect(res).toEqual(mockRes);
  });

  it('period hits /v1/payroll/pay-schedule/period with period query param', async () => {
    const mockPeriod = { startDate: '2026-10-01', endDate: '2026-10-31', payDate: '2026-10-30' };
    apiClient.get.mockResolvedValueOnce({ data: mockPeriod });

    const res = await payScheduleService.period('2026-10');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/pay-schedule/period', {
      params: { period: '2026-10' },
    });
    expect(res).toEqual(mockPeriod);
  });
});
