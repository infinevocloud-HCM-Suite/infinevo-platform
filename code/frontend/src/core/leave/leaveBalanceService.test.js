import { describe, it, expect, vi, beforeEach } from 'vitest';
import { leaveBalanceService } from './leaveBalanceService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('leaveBalanceService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls forEmployee on /v1/employees/{id}/leave-balances with asOf param', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: [{ employeeId: 'emp-1', remainingDays: 10 }],
    });
    const res = await leaveBalanceService.forEmployee('emp-1', '2026-12-31');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/leave-balances', {
      params: { asOf: '2026-12-31' },
    });
    expect(res).toEqual([{ employeeId: 'emp-1', remainingDays: 10 }]);
  });

  it('calls allocate on /v1/leave-allocations', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'alloc-1' } });
    const payload = {
      employeeId: 'emp-1',
      leaveTypeId: 'type-1',
      leaveYear: '2026',
      openingDays: 12,
    };
    const res = await leaveBalanceService.allocate(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-allocations', payload);
    expect(res).toEqual({ id: 'alloc-1' });
  });

  it('calls accrue on /v1/leave-allocations/accrue with asOf param', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { accruedCount: 5 } });
    const res = await leaveBalanceService.accrue('2026-06-30');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-allocations/accrue', null, {
      params: { asOf: '2026-06-30' },
    });
    expect(res).toEqual({ accruedCount: 5 });
  });
});
