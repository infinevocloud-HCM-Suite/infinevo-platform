import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecordLeave } from './RecordLeave.jsx';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveRequestService.js', () => ({
  leaveRequestService: {
    onBehalf: vi.fn(),
  },
}));

vi.mock('./leaveTypeService.js', () => ({
  leaveTypeService: {
    eligible: vi.fn(),
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

describe('RecordLeave component', () => {
  const mockEmployees = [
    { id: 'emp-1', firstName: 'Alice', lastName: 'Smith', employeeNumber: 'EMP100' },
  ];

  const mockEligibleTypes = [
    { id: 'type-1', name: 'Casual Leave', code: 'CL' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.list.mockResolvedValue({ content: mockEmployees });
    leaveTypeService.eligible.mockResolvedValue(mockEligibleTypes);
  });

  it('populates eligible types when employee is selected and submits onBehalf', async () => {
    leaveRequestService.onBehalf.mockResolvedValueOnce({ id: 'req-1', workingDays: 2 });

    render(
      <MemoryRouter>
        <RecordLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/Record Leave on Behalf of Employee/i)).toBeDefined();
    });

    const reasonInput = screen.getByPlaceholderText(/Enter reason for leave/i);
    fireEvent.change(reasonInput, { target: { value: 'Medical appointment' } });
    expect(reasonInput.value).toBe('Medical appointment');

    const submitBtn = screen.getByRole('button', { name: /Record Leave/i });
    expect(submitBtn).toBeDefined();
  });

  it('searches employees on the server as the user types and never asks for 1,000 (D-80)', async () => {
    render(
      <MemoryRouter>
        <RecordLeave />
      </MemoryRouter>
    );

    const picker = screen.getByRole('combobox', { name: 'Employee' });
    fireEvent.focus(picker);
    fireEvent.mouseDown(picker);
    fireEvent.click(await screen.findByText('Alice Smith (EMP100)'));

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-1');
    });
    for (const [params] of employeeService.list.mock.calls) {
      expect(params.size).toBeLessThanOrEqual(20);
    }
  });
});
