import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
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

  // D-90: the server requires validFrom; the form must not let it stay blank.
  it('refuses to save a leave type without Valid From', async () => {
    render(<LeaveTypes />);
    await waitFor(() => {
      expect(screen.getByText('Casual Leave')).toBeDefined();
    });
    fireEvent.click(screen.getByRole('button', { name: /Add Leave Type/i }));
    const dialog = await screen.findByRole('dialog', { name: /Add Leave Type/i });

    fireEvent.click(within(dialog).getByRole('button', { name: /Create|Save/i }));

    await waitFor(() => {
      expect(screen.getByText('Valid From is required')).toBeDefined();
    });
    expect(leaveTypeService.create).not.toHaveBeenCalled();
  });

  // D-86: LeaveUnit has DAYS only; the form must not offer Hours.
  it('offers Days as the only unit', async () => {
    render(<LeaveTypes />);
    await waitFor(() => {
      expect(screen.getByText('Casual Leave')).toBeDefined();
    });
    fireEvent.click(screen.getByRole('button', { name: /Add Leave Type/i }));
    const dialog = await screen.findByRole('dialog', { name: /Add Leave Type/i });
    const unit = within(dialog).getByLabelText('Unit');
    fireEvent.mouseDown(unit);
    await waitFor(() => expect(document.querySelectorAll('.ant-select-item-option').length).toBeGreaterThan(0));
    const options = [...document.querySelectorAll('.ant-select-item-option')].map((o) => o.textContent);
    expect(options).toContain('Days');
    expect(options).not.toContain('Hours');
  });
});
