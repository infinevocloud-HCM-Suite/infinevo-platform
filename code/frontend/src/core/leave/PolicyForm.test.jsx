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
  // D-70: the wire values LeavePolicyResponse sends - the enums' @JsonValue (AccrualFrequency,
  // ResetFrequency, ExceedBalanceMode), not their Java constant names.
  const dummyLeaveType = {
    id: 'type-1',
    name: 'Annual Leave',
    code: 'AL',
    policy: {
      annualDays: 15,
      accrualEnabled: true,
      accrualFrequency: 'monthly',
      accrualUnits: 1.25,
      resetEnabled: true,
      resetFrequency: 'yearly',
      carryForwardEnabled: true,
      carryForwardCap: 5,
      carryForwardExpiresAfterMonths: 3,
      exceedBalanceMode: 'yearEndLimit',
      exceedBalanceLimitDays: 10,
      gender: 'ALL',
      eligibility: [],
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reveals negative balance limit field when the server says yearEndLimit', async () => {
    const { rerender } = render(<PolicyForm leaveType={dummyLeaveType} />);

    // Since exceedBalanceMode is yearEndLimit initially, the limit field must be present
    await waitFor(() => {
      expect(screen.getByText(/Negative Balance Limit/i)).toBeDefined();
    });

    // When mode is noLimit, the field should not be in document
    const noLimitLeaveType = {
      ...dummyLeaveType,
      policy: {
        ...dummyLeaveType.policy,
        exceedBalanceMode: 'noLimit',
        exceedBalanceLimitDays: null,
      },
    };
    rerender(<PolicyForm leaveType={noLimitLeaveType} />);

    await waitFor(() => {
      expect(screen.queryByText(/Negative Balance Limit/i)).toBeNull();
    });
  });

  it('submits form and calls leaveTypeService.savePolicy, keeping the yearEndLimit limit', async () => {
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
          exceedBalanceMode: 'yearEndLimit',
          exceedBalanceLimitDays: 10,
          accrualFrequency: 'monthly',
          resetFrequency: 'yearly',
        })
      );
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('defaults a new policy to the wire values', async () => {
    leaveTypeService.savePolicy.mockResolvedValueOnce({ success: true });
    render(<PolicyForm leaveType={{ id: 'type-2', name: 'Sick', code: 'SL', policy: null }} />);

    fireEvent.click(screen.getByRole('button', { name: /Save Policy/i }));

    await waitFor(() => {
      expect(leaveTypeService.savePolicy).toHaveBeenCalledWith(
        'type-2',
        expect.objectContaining({ exceedBalanceMode: 'noLimit', resetFrequency: 'yearly' })
      );
    });
  });
});
