import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LeaveTypes } from './LeaveTypes.jsx';
import { leaveTypeService } from './leaveTypeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveTypeService.js', () => ({
  leaveTypeService: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    savePolicy: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('LeaveTypes component', () => {
  const mockTypes = [
    {
      id: 'type-1',
      name: 'Casual Leave',
      code: 'CL',
      isPaid: true,
      unit: 'DAYS',
      allowHalfDay: true,
      isActive: true,
      policy: {
        annualDays: 12,
        exceedBalanceMode: 'markAsLOP',
      },
    },
    {
      id: 'type-2',
      name: 'Sick Leave',
      code: 'SL',
      isPaid: false,
      unit: 'DAYS',
      allowHalfDay: false,
      isActive: true,
      policy: null,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    leaveTypeService.list.mockResolvedValue(mockTypes);
  });

  it('renders leave types table with headers and data', async () => {
    render(<LeaveTypes />);

    await waitFor(() => {
      expect(screen.getByText('Casual Leave')).toBeDefined();
      expect(screen.getByText('CL')).toBeDefined();
      expect(screen.getByText('Sick Leave')).toBeDefined();
      expect(screen.getByText('SL')).toBeDefined();
      expect(screen.getAllByText('Paid').length).toBeGreaterThan(0);
      expect(screen.getByText('Unpaid')).toBeDefined();
      // D-70: the wire value markAsLOP, shown as its label in red.
      expect(screen.getByText('Mark as LOP').closest('.ant-tag').className).toMatch(/red/);
      expect(screen.getByText('Not configured')).toBeDefined();
    });
  });

  it('opens create leave type drawer when clicking Add Leave Type', async () => {
    render(<LeaveTypes />);

    await waitFor(() => {
      expect(screen.getByText('Casual Leave')).toBeDefined();
    });

    const addButton = screen.getByRole('button', { name: /Add Leave Type/i });
    fireEvent.click(addButton);

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /Add Leave Type/i })).toBeDefined();
      expect(screen.getByPlaceholderText(/e.g. Annual Leave, Sick Leave/i)).toBeDefined();
    });
  });
});
