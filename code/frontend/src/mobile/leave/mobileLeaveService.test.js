import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mobileLeaveService } from './mobileLeaveService.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('mobileLeaveService (W-98.3 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getBalances', () => {
    it('normalizes balances and calculates available days correctly', async () => {
      const mockRaw = [
        {
          leaveTypeId: 'type-cl',
          leaveTypeCode: 'CL',
          leaveTypeName: 'Casual Leave',
          remainingDays: '10.0',
          pendingDays: '2.0',
          consumedDays: '4.0',
          entitlementDays: '14.0',
          accruedDays: '0.0',
          carriedForwardDays: '0.0',
          expired: false,
        },
        {
          leaveTypeId: 'type-sl',
          leaveTypeCode: 'SL',
          leaveTypeName: 'Sick Leave',
          remainingDays: '5.0',
          pendingDays: '0.0',
          consumedDays: '1.0',
          entitlementDays: '6.0',
          availableDays: '5.0',
          expired: false,
        },
      ];

      apiClient.get.mockResolvedValueOnce({ data: mockRaw });

      const balances = await mobileLeaveService.getBalances();
      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/leave-balances');
      expect(balances).toHaveLength(2);

      // CL check: remaining (10) - pending (2) = available (8)
      expect(balances[0].availableDays).toBe(8);
      expect(balances[0].remainingDays).toBe(10);
      expect(balances[0].pendingDays).toBe(2);
      expect(balances[0].consumedDays).toBe(4);
      expect(balances[0].totalDays).toBe(14);
      expect(balances[0].expired).toBe(false);

      // SL check: available explicitly provided as 5.0
      expect(balances[1].availableDays).toBe(5);
      expect(balances[1].totalDays).toBe(6);
    });

    it('handles empty balances response gracefully', async () => {
      apiClient.get.mockResolvedValueOnce({ data: null });
      const balances = await mobileLeaveService.getBalances();
      expect(balances).toEqual([]);
    });
  });

  describe('getRequests', () => {
    it('fetches leave requests and normalizes daysCount', async () => {
      const mockData = {
        content: [
          {
            id: 'req-1',
            leaveTypeCode: 'CL',
            fromDate: '2026-11-20',
            toDate: '2026-11-20',
            daysCount: 1,
            status: 'PENDING',
          },
        ],
        totalElements: 1,
      };

      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await mobileLeaveService.getRequests({ page: 0, size: 10 });
      expect(apiClient.get).toHaveBeenCalledWith('/v1/me/leave-requests', {
        params: { page: 0, size: 10 },
      });
      expect(res.rows).toHaveLength(1);
      expect(res.rows[0].daysCount).toBe(1);
      expect(res.total).toBe(1);
    });
  });

  describe('applyLeave', () => {
    it('sends formatted payload with submit: true by default', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { id: 'req-created-1', status: 'PENDING' } });

      const payload = {
        leaveTypeId: 'type-cl',
        fromDate: '2026-11-25',
        toDate: '2026-11-26',
        isHalfDay: false,
        reason: 'Personal errand',
      };

      const result = await mobileLeaveService.applyLeave(payload);
      expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests', {
        leaveTypeId: 'type-cl',
        fromDate: '2026-11-25',
        toDate: '2026-11-26',
        isHalfDay: false,
        halfDayPeriod: null,
        reason: 'Personal errand',
        submit: true,
      });
      expect(result.id).toBe('req-created-1');
    });

    it('syncs toDate to fromDate and sends lowercase "second" or "first" when isHalfDay is true', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { id: 'req-created-2', status: 'PENDING' } });

      const payload = {
        leaveTypeId: 'type-sl',
        fromDate: '2026-11-25',
        toDate: '2026-11-30',
        isHalfDay: true,
        halfDayPeriod: 'second',
        reason: 'Doctor checkup',
      };

      await mobileLeaveService.applyLeave(payload);
      expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests', {
        leaveTypeId: 'type-sl',
        fromDate: '2026-11-25',
        toDate: '2026-11-25',
        isHalfDay: true,
        halfDayPeriod: 'second',
        reason: 'Doctor checkup',
        submit: true,
      });
    });

    it('normalizes legacy FIRST_HALF/SECOND_HALF to lowercase first/second', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { id: 'req-created-3', status: 'PENDING' } });

      await mobileLeaveService.applyLeave({
        leaveTypeId: 'type-sl',
        fromDate: '2026-11-25',
        isHalfDay: true,
        halfDayPeriod: 'FIRST_HALF',
      });

      expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests', expect.objectContaining({
        halfDayPeriod: 'first',
      }));
    });
  });

  describe('withdrawLeave', () => {
    it('posts to withdraw endpoint with reason for PENDING request', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { id: 'req-1', status: 'WITHDRAWN' } });

      const result = await mobileLeaveService.withdrawLeave('req-1', 'Changed mind');
      expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/req-1/withdraw', {
        reason: 'Changed mind',
      });
      expect(result.status).toBe('WITHDRAWN');
    });

    it('throws error when requestId is missing', async () => {
      await expect(mobileLeaveService.withdrawLeave(null)).rejects.toThrow(
        'requestId is required to withdraw leave'
      );
    });
  });

  describe('cancelLeave', () => {
    it('posts to cancel endpoint with reason for APPROVED request', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { id: 'req-2', status: 'CANCELLED' } });

      const result = await mobileLeaveService.cancelLeave('req-2', 'Changed plans');
      expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/req-2/cancel', {
        reason: 'Changed plans',
      });
      expect(result.status).toBe('CANCELLED');
    });

    it('throws error when requestId is missing', async () => {
      await expect(mobileLeaveService.cancelLeave(null)).rejects.toThrow(
        'requestId is required to cancel leave'
      );
    });
  });

  describe('calculateRequestedDays', () => {
    it('returns 0.5 for half day', () => {
      expect(mobileLeaveService.calculateRequestedDays('2026-11-20', '2026-11-20', true)).toBe(0.5);
    });

    it('returns 1 for same-day full day', () => {
      expect(mobileLeaveService.calculateRequestedDays('2026-11-20', '2026-11-20', false)).toBe(1);
    });

    it('returns inclusive range for multi-day leaves', () => {
      expect(mobileLeaveService.calculateRequestedDays('2026-11-20', '2026-11-22', false)).toBe(3);
    });

    it('returns 0 for invalid or backwards range', () => {
      expect(mobileLeaveService.calculateRequestedDays('2026-11-25', '2026-11-20', false)).toBe(0);
      expect(mobileLeaveService.calculateRequestedDays(null, '2026-11-20', false)).toBe(0);
    });
  });
});
