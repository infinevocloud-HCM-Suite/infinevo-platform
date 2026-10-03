import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PolicyForm } from './PolicyForm.jsx';
import { leaveTypeService } from './leaveTypeService.js';

vi.mock('./leaveTypeService.js', () => ({
  leaveTypeService: {
    savePolicy: vi.fn(),
  },
}));

vi.mock('../org/departmentService.js', () => ({
  departmentService: { list: vi.fn().mockResolvedValue([]) },
}));
vi.mock('../org/designationService.js', () => ({
  designationService: { list: vi.fn().mockResolvedValue([]) },
}));
vi.mock('../org/workLocationService.js', () => ({
  workLocationService: { list: vi.fn().mockResolvedValue([]) },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('PolicyForm component', () => {
  const dummyLeaveType = {
    id: 'type-1',
    name: 'Annual Leave',
    code: 'AL',
    policy: {
      annualDays: 15,
      accrualEnabled: true,
      accrualFrequency: 'MONTHLY',
      accrualUnits: 1.25,
      resetEnabled: true,
      resetFrequency: 'CALENDAR_YEAR',
      carryForwardEnabled: true,
      carryForwardCap: 5,
      carryForwardExpiresAfterMonths: 3,
      exceedBalanceMode: 'YEAR_END_LIMIT',
      exceedBalanceLimitDays: 10,
      gender: 'ALL',
      eligibility: [],
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reveals negative balance limit field when YEAR_END_LIMIT is set or selected', async () => {
    const { rerender } = render(<PolicyForm leaveType={dummyLeaveType} />);

    // Since exceedBalanceMode is YEAR_END_LIMIT initially, the limit field must be present
    await waitFor(() => {
      expect(screen.getByText(/Negative Balance Limit/i)).toBeDefined();
    });

    // When mode is NO_LIMIT, the field should not be in document
    const noLimitLeaveType = {
      ...dummyLeaveType,
      policy: {
        ...dummyLeaveType.policy,
        exceedBalanceMode: 'NO_LIMIT',
        exceedBalanceLimitDays: null,
      },
    };
    rerender(<PolicyForm leaveType={noLimitLeaveType} />);

    await waitFor(() => {
      expect(screen.queryByText(/Negative Balance Limit/i)).toBeNull();
    });
  });

  it('submits form and calls leaveTypeService.savePolicy', async () => {
    leaveTypeService.savePolicy.mockResolvedValueOnce({ success: true });
    const onSuccess = vi.fn();

    render(<PolicyForm leaveType={dummyLeaveType} onSuccess={onSuccess} />);

    const saveButton = screen.getByRole('button', { name: /Save Policy/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(leaveTypeService.savePolicy).toHaveBeenCalledWith(
        'type-1',
        expect.objectContaining({
          annualDays: 15,
          exceedBalanceMode: 'YEAR_END_LIMIT',
          exceedBalanceLimitDays: 10,
        })
      );
      expect(onSuccess).toHaveBeenCalled();
    });
  });
});
