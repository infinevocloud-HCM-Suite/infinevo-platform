import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MobileLeaveScreen } from './MobileLeaveScreen.jsx';
import { mobileLeaveService } from './mobileLeaveService.js';

vi.mock('./mobileLeaveService.js', () => ({
  mobileLeaveService: {
    getBalances: vi.fn(),
    getRequests: vi.fn(),
    cancelLeave: vi.fn(),
    calculateRequestedDays: vi.fn(() => 1),
    applyLeave: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('MobileLeaveScreen component (W-98.3 §6)', () => {
  const sampleBalances = [
    {
      leaveTypeId: 'type-cl',
      leaveTypeCode: 'CL',
      leaveTypeName: 'Casual Leave',
      availableDays: 5,
      consumedDays: 2,
      totalDays: 10,
      pendingDays: 1,
      expired: false,
    },
    {
      leaveTypeId: 'type-sl',
      leaveTypeCode: 'SL',
      leaveTypeName: 'Sick Leave',
      availableDays: 4,
      consumedDays: 1,
      totalDays: 8,
      pendingDays: 0,
      expired: false,
    },
  ];

  const sampleRequests = [
    {
      id: 'req-1',
      leaveTypeName: 'Casual Leave',
      leaveTypeCode: 'CL',
      fromDate: '2026-11-20',
      toDate: '2026-11-20',
      daysCount: 1,
      isHalfDay: false,
      status: 'PENDING',
      reason: 'Doctor appointment',
    },
    {
      id: 'req-2',
      leaveTypeName: 'Sick Leave',
      leaveTypeCode: 'SL',
      fromDate: '2026-10-10',
      toDate: '2026-10-10',
      daysCount: 0.5,
      isHalfDay: true,
      halfDayPeriod: 'FIRST_HALF',
      status: 'APPROVED',
      reason: 'Fever',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    mobileLeaveService.getBalances.mockResolvedValue(sampleBalances);
    mobileLeaveService.getRequests.mockResolvedValue({ rows: sampleRequests, total: 2 });
  });

  it('renders balance cards carousel and recent requests list', async () => {
    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('mobile-leave-screen')).toBeTruthy();
      expect(screen.getByTestId('leave-balance-carousel')).toBeTruthy();
      expect(screen.getAllByTestId('leave-balance-card')).toHaveLength(2);
      expect(screen.getAllByTestId('leave-request-item')).toHaveLength(2);
      expect(screen.getByTestId('fab-apply-leave')).toBeTruthy();
    });

    expect(screen.getAllByText('Casual Leave').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Sick Leave').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Pending')).toBeTruthy();
    expect(screen.getByText('Approved')).toBeTruthy();
  });

  it('renders empty states when there are no balances or requests', async () => {
    mobileLeaveService.getBalances.mockResolvedValueOnce([]);
    mobileLeaveService.getRequests.mockResolvedValueOnce({ rows: [], total: 0 });

    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByText('No leave balance allocations found.')).toBeTruthy();
      expect(screen.getByText('No leave requests submitted yet.')).toBeTruthy();
    });
  });

  it('opens apply leave bottom sheet on FAB button click', async () => {
    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('fab-apply-leave')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('fab-apply-leave'));

    await waitFor(() => {
      expect(screen.getByTestId('apply-leave-drawer')).toBeTruthy();
    });
  });

  it('cancels pending leave request on confirm', async () => {
    mobileLeaveService.cancelLeave.mockResolvedValueOnce({ id: 'req-1', status: 'CANCELLED' });

    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-cancel-leave')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-cancel-leave'));

    // Wait for popconfirm to pop and click Yes, Cancel
    await waitFor(() => {
      const okBtn = screen.getByText('Yes, Cancel');
      expect(okBtn).toBeTruthy();
      fireEvent.click(okBtn);
    });

    await waitFor(() => {
      expect(mobileLeaveService.cancelLeave).toHaveBeenCalledWith(
        'req-1',
        'Cancelled from mobile app'
      );
    });
  });
});
