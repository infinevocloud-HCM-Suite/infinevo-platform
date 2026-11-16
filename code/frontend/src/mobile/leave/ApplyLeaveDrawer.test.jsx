import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ApplyLeaveDrawer } from './ApplyLeaveDrawer.jsx';
import { mobileLeaveService } from './mobileLeaveService.js';

vi.mock('./mobileLeaveService.js', () => ({
  mobileLeaveService: {
    applyLeave: vi.fn(),
    calculateRequestedDays: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('ApplyLeaveDrawer component (W-98.3 §6)', () => {
  const mockBalances = [
    {
      leaveTypeId: 'type-cl',
      leaveTypeCode: 'CL',
      leaveTypeName: 'Casual Leave',
      availableDays: 5,
      expired: false,
    },
    {
      leaveTypeId: 'type-sl',
      leaveTypeCode: 'SL',
      leaveTypeName: 'Sick Leave',
      availableDays: 2,
      expired: false,
    },
    {
      leaveTypeId: 'type-expired',
      leaveTypeCode: 'EX',
      leaveTypeName: 'Expired Leave',
      availableDays: 10,
      expired: true,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    mobileLeaveService.calculateRequestedDays.mockReturnValue(1);
  });

  it('renders drawer with fields when open', () => {
    render(
      <ApplyLeaveDrawer
        open={true}
        onClose={vi.fn()}
        balances={mockBalances}
      />
    );

    expect(screen.getByTestId('apply-leave-drawer')).toBeTruthy();
    expect(screen.getByText('Apply for Leave')).toBeTruthy();
    expect(screen.getByTestId('select-leave-type')).toBeTruthy();
    expect(screen.getByTestId('switch-half-day')).toBeTruthy();
    expect(screen.getByTestId('input-leave-reason')).toBeTruthy();
    expect(screen.getByTestId('btn-submit-leave')).toBeTruthy();
  });

  it('filters out expired leave types from selectable options', () => {
    render(
      <ApplyLeaveDrawer
        open={true}
        onClose={vi.fn()}
        balances={mockBalances}
      />
    );

    // Expired leave type should not appear as valid option
    expect(screen.queryByText('Expired Leave')).toBeNull();
  });

  it('disables submit button and displays alert when requested days exceed available balance', () => {
    // Return 6 requested days which exceeds Casual Leave (5 available)
    mobileLeaveService.calculateRequestedDays.mockReturnValue(6);

    render(
      <ApplyLeaveDrawer
        open={true}
        onClose={vi.fn()}
        balances={mockBalances}
      />
    );

    const submitBtn = screen.getByTestId('btn-submit-leave');
    expect(submitBtn.hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('insufficient-balance-alert')).toBeTruthy();
  });

  it('submits form successfully and calls onSuccess and onClose', async () => {
    mobileLeaveService.applyLeave.mockResolvedValueOnce({ id: 'new-req-1', status: 'PENDING' });
    const handleSuccess = vi.fn();
    const handleClose = vi.fn();

    render(
      <ApplyLeaveDrawer
        open={true}
        onClose={handleClose}
        balances={mockBalances}
        onSuccess={handleSuccess}
      />
    );

    // Fill in reason
    fireEvent.change(screen.getByTestId('input-leave-reason'), {
      target: { value: 'Vacation with family' },
    });

    const submitBtn = screen.getByTestId('btn-submit-leave');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mobileLeaveService.applyLeave).toHaveBeenCalledWith(
        expect.objectContaining({
          leaveTypeId: 'type-cl',
          reason: 'Vacation with family',
          submit: true,
        })
      );
      expect(handleSuccess).toHaveBeenCalled();
      expect(handleClose).toHaveBeenCalled();
    });
  });

  it('displays error banner when application fails on server', async () => {
    mobileLeaveService.applyLeave.mockRejectedValueOnce({
      response: { data: { message: 'Overlapping leave exists' } },
    });

    render(
      <ApplyLeaveDrawer
        open={true}
        onClose={vi.fn()}
        balances={mockBalances}
      />
    );

    fireEvent.change(screen.getByTestId('input-leave-reason'), {
      target: { value: 'Test reason' },
    });

    fireEvent.click(screen.getByTestId('btn-submit-leave'));

    await waitFor(() => {
      expect(screen.getByTestId('apply-leave-error')).toBeTruthy();
      expect(screen.getByText('Overlapping leave exists')).toBeTruthy();
    });
  });
});

