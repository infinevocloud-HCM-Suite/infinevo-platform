import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { OffCycleCreate } from './OffCycleCreate.jsx';
import { payrunService } from './payrunService.js';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    searchEmployees: vi.fn(),
    createOffCycle: vi.fn(),
    addInputs: vi.fn(),
  },
}));

describe('OffCycleCreate component (W-47.2 §7)', () => {
  const mockEmployees = [
    {
      id: 'emp-1',
      employeeNumber: 'E01',
      firstName: 'Alice',
      lastName: 'Smith',
    },
    {
      id: 'emp-2',
      employeeNumber: 'E02',
      firstName: 'Bob',
      lastName: 'Jones',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    payrunService.searchEmployees.mockResolvedValue(mockEmployees);
  });

  it('renders 400 error naming each unconsidered employee', async () => {
    payrunService.createOffCycle.mockRejectedValueOnce({
      status: 400,
      message: 'Some employees are not considered in this run',
      unconsideredEmployees: ['Alice Smith (terminated)', 'Bob Jones (no active bank)'],
    });

    render(
      <MemoryRouter>
        <OffCycleCreate />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Create Off-Cycle Pay Run')).toBeDefined();
    });

    // Enter payment date
    const dateInput = screen.getByPlaceholderText('Select payment date');
    fireEvent.change(dateInput, { target: { value: '2026-10-25' } });
    fireEvent.keyDown(dateInput, { key: 'Enter' });

    // Select employee via hidden form logic or direct submit
    // In our component, we can simulate step 1 submission after selecting date and employees
    const submitBtn = screen.getByRole('button', { name: /create run & enter inputs/i });

    // Since selectedEmployees is empty initially, button is disabled. Select employee:
    const select = screen.getByRole('combobox');
    fireEvent.mouseDown(select);

    await waitFor(() => {
      expect(screen.getByText('E01 — Alice Smith')).toBeDefined();
    });
    fireEvent.click(screen.getByText('E01 — Alice Smith'));

    expect(submitBtn.hasAttribute('disabled')).toBe(false);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Alice Smith (terminated)')).toBeDefined();
      expect(screen.getByText('Bob Jones (no active bank)')).toBeDefined();
    });
  });

  it('advances to step 2, posts [{employeeId, kind, amount, sourceRef}], and marks DUPLICATE row', async () => {
    payrunService.createOffCycle.mockResolvedValueOnce({
      id: 'run-off-1',
      period: '2026-10',
      run_type: 'OFF_CYCLE',
    });

    render(
      <MemoryRouter>
        <OffCycleCreate />
      </MemoryRouter>
    );

    // Pick date
    const dateInput = screen.getByPlaceholderText('Select payment date');
    fireEvent.change(dateInput, { target: { value: '2026-10-20' } });
    fireEvent.keyDown(dateInput, { key: 'Enter' });

    // Pick employee
    const select = screen.getByRole('combobox');
    fireEvent.mouseDown(select);
    await waitFor(() => {
      expect(screen.getByText('E01 — Alice Smith')).toBeDefined();
    });
    fireEvent.click(screen.getByText('E01 — Alice Smith'));

    const submitBtn = screen.getByRole('button', { name: /create run & enter inputs/i });
    fireEvent.click(submitBtn);

    // Step 2 is now active!
    await waitFor(() => {
      expect(screen.getByText(/Step 2: Tagged Inputs for Run 2026-10/i)).toBeDefined();
    });

    // Fill in amount in inputs grid
    const amountInput = screen.getByPlaceholderText('0.00');
    fireEvent.change(amountInput, { target: { value: '7500' } });

    // Mock addInputs returning DUPLICATE
    payrunService.addInputs.mockResolvedValueOnce([
      {
        employee_id: 'emp-1',
        source_ref: 'OFF_REF_1',
        result: 'DUPLICATE',
        pay_input_id: null,
      },
    ]);

    // Set sourceRef to match mock
    const refInput = screen.getByPlaceholderText('e.g. BONUS_2026_01');
    fireEvent.change(refInput, { target: { value: 'OFF_REF_1' } });

    const saveBtn = screen.getByRole('button', { name: /save inputs/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(payrunService.addInputs).toHaveBeenCalledWith('run-off-1', [
        {
          employeeId: 'emp-1',
          kind: 'ONE_TIME_PAYOUT',
          amount: 7500,
          sourceRef: 'OFF_REF_1',
        },
      ]);
      // Verify DUPLICATE tag is displayed on the row
      expect(screen.getByText('DUPLICATE')).toBeDefined();
    });
  });
});
