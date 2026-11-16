import { apiClient } from '../../shared/api/client.js';
import dayjs from 'dayjs';

/**
 * Mobile Leave Service (W-98.3 §4.2)
 * Wraps leave balances, request history, and bottom-sheet leave applications
 * for mobile touch clients.
 */
export const mobileLeaveService = {
  /**
   * Fetches caller's leave balances.
   * Returns array of balance objects with normalized numeric fields.
   */
  async getBalances() {
    const res = await apiClient.get('/v1/me/leave-balances');
    const data = res?.data !== undefined ? res.data : res;
    const list = Array.isArray(data) ? data : [];

    return list.map((item) => {
      const remaining = Number(item.remainingDays ?? 0);
      const pending = Number(item.pendingDays ?? 0);
      const consumed = Number(item.consumedDays ?? 0);
      const entitlement = Number(item.entitlementDays ?? 0);
      const accrued = Number(item.accruedDays ?? 0);
      const carried = Number(item.carriedForwardDays ?? 0);
      const total = entitlement + accrued + carried;
      const available =
        item.availableDays !== undefined
          ? Number(item.availableDays)
          : Math.max(0, remaining - pending);

      return {
        ...item,
        remainingDays: remaining,
        pendingDays: pending,
        consumedDays: consumed,
        entitlementDays: entitlement,
        totalDays: total > 0 ? total : consumed + remaining,
        availableDays: available,
        expired: !!item.expired,
      };
    });
  },

  /**
   * Fetches caller's past and pending leave requests, newest first.
   */
  async getRequests(params = { page: 0, size: 25 }) {
    const res = await apiClient.get('/v1/me/leave-requests', { params });
    const data = res?.data !== undefined ? res.data : res;
    const rows = Array.isArray(data) ? data : data?.content || [];
    const total = Array.isArray(data) ? data.length : data?.totalElements ?? rows.length;

    return {
      rows: rows.map((r) => ({
        ...r,
        daysCount: Number(r.daysCount ?? r.days ?? 1),
      })),
      total,
    };
  },

  /**
   * Submits a leave application.
   * Payload: { leaveTypeId, fromDate, toDate, isHalfDay, halfDayPeriod, reason, submit: true }
   */
  async applyLeave(payload) {
    const body = {
      leaveTypeId: payload.leaveTypeId,
      fromDate: payload.fromDate,
      toDate: payload.isHalfDay ? payload.fromDate : payload.toDate || payload.fromDate,
      isHalfDay: !!payload.isHalfDay,
      halfDayPeriod: payload.isHalfDay ? payload.halfDayPeriod || 'FIRST_HALF' : null,
      reason: payload.reason || '',
      submit: payload.submit !== undefined ? payload.submit : true,
    };

    const res = await apiClient.post('/v1/leave-requests', body);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Cancels a pending or approved leave request.
   */
  async cancelLeave(requestId, reason = 'Cancelled via mobile app') {
    if (!requestId) throw new Error('requestId is required to cancel leave');
    const res = await apiClient.post(`/v1/leave-requests/${requestId}/cancel`, { reason });
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Calculates the requested number of days between two dates.
   * Half-day always equals 0.5 days.
   */
  calculateRequestedDays(fromDate, toDate, isHalfDay = false) {
    if (isHalfDay) return 0.5;
    if (!fromDate) return 0;
    const start = dayjs(fromDate);
    const end = toDate ? dayjs(toDate) : start;
    if (!start.isValid() || !end.isValid() || end.isBefore(start)) return 0;
    return end.diff(start, 'day') + 1;
  },
};

