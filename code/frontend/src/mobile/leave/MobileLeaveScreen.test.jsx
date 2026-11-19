import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MobileLeaveScreen } from './MobileLeaveScreen.jsx';
import { mobileLeaveService } from './mobileLeaveService.js';

vi.mock('./mobileLeaveService.js', () => ({
  mobileLeaveService: {
    getBalances: vi.fn(),
    getRequests: vi.fn(),
    withdrawLeave: vi.fn(),
    cancelLeave: vi.fn(),
    calculateRequestedDays: vi.fn(() => 1),
    applyLeave: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('MobileLeaveScreen component (W-98.3 §7)', () => {
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
      fromDate: '2029-12-01',
      toDate: '2029-12-01',
      daysCount: 0.5,
      isHalfDay: true,
      halfDayPeriod: 'first',
      status: 'APPROVED',
      reason: 'Future checkup',
    },
    {
      id: 'req-3',
      leaveTypeName: 'Casual Leave',
      leaveTypeCode: 'CL',
      fromDate: '2020-01-01',
      toDate: '2020-01-01',
      daysCount: 1,
      isHalfDay: false,
      status: 'APPROVED',
      reason: 'Old leave',
    },
    {
      id: 'req-4',
      leaveTypeName: 'Sick Leave',
      leaveTypeCode: 'SL',
      fromDate: '2026-11-10',
      toDate: '2026-11-10',
      daysCount: 1,
      isHalfDay: false,
      status: 'REJECTED',
      reason: 'Denied',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    mobileLeaveService.getBalances.mockResolvedValue(sampleBalances);
    mobileLeaveService.getRequests.mockResolvedValue({ rows: sampleRequests, total: 4 });
  });

  it('renders balance cards carousel and recent requests list', async () => {
    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('mobile-leave-screen')).toBeTruthy();
      expect(screen.getByTestId('leave-balance-carousel')).toBeTruthy();
      expect(screen.getAllByTestId('leave-balance-card')).toHaveLength(2);
      expect(screen.getAllByTestId('leave-request-item')).toHaveLength(4);
      expect(screen.getByTestId('fab-apply-leave')).toBeTruthy();
    });

    expect(screen.getAllByText('Casual Leave').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Sick Leave').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Pending')).toBeTruthy();
    expect(screen.getAllByText('Approved')).toHaveLength(2);
  });

  it('renders empty state with "No leave requests yet" when there are no requests', async () => {
    mobileLeaveService.getBalances.mockResolvedValueOnce([]);
    mobileLeaveService.getRequests.mockResolvedValueOnce({ rows: [], total: 0 });

    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByText('No leave balance allocations found.')).toBeTruthy();
      expect(screen.getByText('No leave requests yet')).toBeTruthy();
    });
  });

  it('renders distinct error view with retry button on network failure, never looking like empty list', async () => {
    mobileLeaveService.getBalances.mockRejectedValueOnce(new Error('Network disconnected'));
    mobileLeaveService.getRequests.mockRejectedValueOnce(new Error('Network disconnected'));

    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('mobile-leave-error')).toBeTruthy();
      expect(screen.getByText('Failed to load leave records')).toBeTruthy();
      expect(screen.getByTestId('leave-retry-btn')).toBeTruthy();
      expect(screen.queryByText('No leave requests yet')).toBeNull();
    });

    // Clicking retry calls loadData
    mobileLeaveService.getBalances.mockResolvedValueOnce(sampleBalances);
    mobileLeaveService.getRequests.mockResolvedValueOnce({ rows: sampleRequests, total: 4 });
    fireEvent.click(screen.getByTestId('leave-retry-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('mobile-leave-screen')).toBeTruthy();
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

  it('shows Withdraw only on PENDING request and calls withdrawLeave on confirmation', async () => {
    mobileLeaveService.withdrawLeave.mockResolvedValueOnce({ id: 'req-1', status: 'WITHDRAWN' });

    render(<MobileLeaveScreen />);

    const withdrawBtn = await screen.findByTestId('btn-withdraw-leave');
    expect(withdrawBtn).toBeTruthy();

    fireEvent.click(withdrawBtn);

    // Wait for popconfirm to show "Yes, Withdraw"
    await waitFor(() => {
      const okBtn = screen.getByText('Yes, Withdraw');
      expect(okBtn).toBeTruthy();
      fireEvent.click(okBtn);
    });

    await waitFor(() => {
      expect(mobileLeaveService.withdrawLeave).toHaveBeenCalledWith(
        'req-1',
        'Withdrawn from mobile app'
      );
    });
  });

  it('shows Cancel only on future APPROVED request and calls cancelLeave on confirmation', async () => {
    mobileLeaveService.cancelLeave.mockResolvedValueOnce({ id: 'req-2', status: 'CANCELLED' });

    render(<MobileLeaveScreen />);

    const cancelBtn = await screen.findByTestId('btn-cancel-leave');
    expect(cancelBtn).toBeTruthy();

    fireEvent.click(cancelBtn);

    // Wait for popconfirm to show "Yes, Cancel"
    await waitFor(() => {
      const okBtn = screen.getByText('Yes, Cancel');
      expect(okBtn).toBeTruthy();
      fireEvent.click(okBtn);
    });

    await waitFor(() => {
      expect(mobileLeaveService.cancelLeave).toHaveBeenCalledWith(
        'req-2',
        'Cancelled from mobile app'
      );
    });
  });

  it('shows neither Withdraw nor Cancel for past approved or rejected requests', async () => {
    render(<MobileLeaveScreen />);

    await waitFor(() => {
      expect(screen.getAllByTestId('leave-request-item')).toHaveLength(4);
    });

    // Exactly 1 withdraw button (for req-1 PENDING) and 1 cancel button (for req-2 future APPROVED)
    expect(screen.getAllByTestId('btn-withdraw-leave')).toHaveLength(1);
    expect(screen.getAllByTestId('btn-cancel-leave')).toHaveLength(1);
  });
});
