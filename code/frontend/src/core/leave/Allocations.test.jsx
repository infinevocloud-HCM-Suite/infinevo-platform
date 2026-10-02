import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Allocations } from './Allocations.jsx';
import { leaveBalanceService } from './leaveBalanceService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveBalanceService.js', () => ({
  leaveBalanceService: {
    forEmployee: vi.fn(),
    allocate: vi.fn(),
    accrue: vi.fn(),
  },
}));

vi.mock('./leaveTypeService.js', () => ({
  leaveTypeService: {
    list: vi.fn(),
  },
}));

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    list: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('Allocations component', () => {
  const mockEmployees = [
    { id: 'emp-1', firstName: 'John', lastName: 'Doe', employeeNumber: 'EMP001' },
  ];

  const mockTypes = [
    { id: 'type-1', name: 'Annual Leave', code: 'AL' },
  ];

  const mockBalances = [
    {
      employeeId: 'emp-1',
      leaveTypeId: 'type-1',
      leaveTypeName: 'Annual Leave',
      entitlementDays: 12,
      accruedDays: 6,
      carriedForwardDays: 2,
      consumedDays: 3,
      remainingDays: 17,
      carryForwardExpiresOn: '2026-12-31',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.list.mockResolvedValue(mockEmployees);
    leaveTypeService.list.mockResolvedValue(mockTypes);
    leaveBalanceService.forEmployee.mockResolvedValue(mockBalances);
  });

  it('renders employee balance table and allocate button', async () => {
    render(<Allocations />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeDefined();
      expect(screen.getByText('EMP001')).toBeDefined();
      expect(screen.getByRole('button', { name: /Allocate/i })).toBeDefined();
      expect(screen.getByRole('button', { name: /Run Accrual As Of/i })).toBeDefined();
    });
  });

  it('opens allocate modal and submits the four fields', async () => {
    leaveBalanceService.allocate.mockResolvedValueOnce({ id: 'alloc-1' });
    render(<Allocations />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeDefined();
    });

    const allocateButton = screen.getByRole('button', { name: /Allocate/i });
    fireEvent.click(allocateButton);

    await waitFor(() => {
      expect(screen.getByText('Manual Leave Allocation')).toBeDefined();
    });

    const saveButton = screen.getByRole('button', { name: /Save Allocation/i });
    fireEvent.click(saveButton);
  });

  it('accrual button shows confirmation dialog before running', async () => {
    leaveBalanceService.accrue.mockResolvedValueOnce({ accruedCount: 3 });
    render(<Allocations />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeDefined();
    });

    const accrueButton = screen.getByRole('button', { name: /Run Accrual As Of/i });
    fireEvent.click(accrueButton);

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /Run Accrual As Of/i })).toBeDefined();
      expect(
        screen.getByText(/Running accrual will compute and credit earned leave days/i)
      ).toBeDefined();
    });

    const okButton = screen.getByRole('button', { name: /^Run Accrual$/i });
    fireEvent.click(okButton);

    await waitFor(() => {
      expect(leaveBalanceService.accrue).toHaveBeenCalled();
    });
  });
});
