import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Allocations, leaveYearWindow } from './Allocations.jsx';
import { leaveBalanceService } from './leaveBalanceService.js';
import { companyProfileService } from '../settings/companyProfileService.js';
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

vi.mock('../settings/companyProfileService.js', () => ({
  companyProfileService: { get: vi.fn().mockResolvedValue({ name: 'Globex', leaveYearStartMonth: 4 }) },
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

    const employeeSelect = screen.getByLabelText('Employee');
    fireEvent.mouseDown(employeeSelect);
    fireEvent.click(await screen.findByText('John Doe (EMP001)'));

    // Select leave type
    const leaveTypeSelect = screen.getByLabelText('Leave Type');
    fireEvent.mouseDown(leaveTypeSelect);
    fireEvent.click(await screen.findByText('Annual Leave (AL)'));

    // Set opening days input
    const spinbuttons = screen.getAllByRole('spinbutton');
    const openingDaysInput = spinbuttons[spinbuttons.length - 1];
    fireEvent.change(openingDaysInput, { target: { value: '10' } });

    const saveButton = screen.getByRole('button', { name: /Save Allocation/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(leaveBalanceService.allocate).toHaveBeenCalledWith(
        expect.objectContaining({
          employeeId: 'emp-1',
          leaveTypeId: 'type-1',
          openingDays: 10,
          // D-93: an April-cycle tenant's leave year runs 1 April to 31 March, not January to December.
          yearStartDate: expect.stringMatching(/^\d{4}-04-01$/),
          yearEndDate: expect.stringMatching(/^\d{4}-03-31$/),
        })
      );
    });
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

describe('Allocations when the company profile cannot be read (D-93)', () => {
  it('assumes the server default, April, and says so beside Leave Year', async () => {
    companyProfileService.get.mockRejectedValueOnce(new Error('boom'));
    render(<Allocations />);
    await waitFor(() => {
      expect(companyProfileService.get).toHaveBeenCalled();
    });
    fireEvent.click(screen.getByRole('button', { name: /Allocate/i }));
    await waitFor(() => {
      expect(screen.getByText(/assuming the leave year starts in April/i)).toBeDefined();
    });
  });
});

describe('leaveYearWindow (D-93)', () => {
  it('runs January to December for a January start and April to March for an April start', () => {
    expect(leaveYearWindow(2026, 1)).toEqual({ yearStartDate: '2026-01-01', yearEndDate: '2026-12-31' });
    expect(leaveYearWindow(2026, 4)).toEqual({ yearStartDate: '2026-04-01', yearEndDate: '2027-03-31' });
  });
});
