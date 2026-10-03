import { describe, it, expect, vi, beforeEach } from 'vitest';
import { leaveConsumptionService } from './leaveConsumptionService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('leaveConsumptionService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls rows on /v1/employees/{id}/leave-consumption with year', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: [{ id: 'cons-1', consumedDays: 2 }],
    });
    const res = await leaveConsumptionService.rows('emp-1', 2026);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/leave-consumption', {
      params: { year: 2026 },
    });
    expect(res).toEqual([{ id: 'cons-1', consumedDays: 2 }]);
  });

  it('calls lop on /v1/employees/{id}/lop with period', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: { employeeId: 'emp-1', period: '2026-10', totalLopDays: 1.5 },
    });
    const res = await leaveConsumptionService.lop('emp-1', '2026-10');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/lop', {
      params: { period: '2026-10' },
    });
    expect(res).toEqual({ employeeId: 'emp-1', period: '2026-10', totalLopDays: 1.5 });
  });
});
