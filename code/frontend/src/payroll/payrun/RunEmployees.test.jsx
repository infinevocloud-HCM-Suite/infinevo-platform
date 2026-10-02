import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RunEmployees } from './RunEmployees.jsx';
import { payrunService } from './payrunService.js';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    employees: vi.fn(),
    employeeNames: vi.fn(),
    lines: vi.fn().mockResolvedValue({ lines: [] }),
  },
}));

describe('RunEmployees component (W-47.2 §7)', () => {
  const sampleEmployees = [
    {
      id: 'ep-1',
      employee_id: 'emp-1',
      employee_number: 'EMP101',
      inclusion_status: 'INCLUDED',
      skip_reason: null,
      gross_earnings: 45000.0,
      net_pay: 40000.0,
    },
    {
      id: 'ep-2',
      employee_id: 'emp-2',
      employee_number: 'EMP102',
      inclusion_status: 'SKIPPED',
      skip_reason: 'NO_SALARY',
      gross_earnings: null,
      net_pay: null,
    },
    {
      id: 'ep-3',
      employee_id: 'emp-3',
      employee_number: 'EMP103',
      inclusion_status: 'SKIPPED',
      skip_reason: 'NO_BANK_DETAILS',
      gross_earnings: null,
      net_pay: null,
    },
    {
      id: 'ep-4',
      employee_id: 'emp-4',
      employee_number: 'EMP104',
      inclusion_status: 'SKIPPED',
      skip_reason: 'UNAUTHORIZED_ABSENCE_UNKNOWN',
      gross_earnings: null,
      net_pay: null,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    // The run's rows carry no name (EmployeePayRunResponse); core supplies them.
    payrunService.employeeNames.mockResolvedValue(
      new Map([
        ['emp-1', 'Priya Patel'],
        ['emp-2', 'Rohan Verma'],
        ['emp-3', 'Ananya Roy'],
        ['emp-4', 'David Gomez'],
      ])
    );
  });

  it('shows each name from core, looked up once for the whole page (W-47.2 §14.3)', async () => {
    payrunService.employees.mockResolvedValue({ content: sampleEmployees, totalElements: 4 });

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(
      () => {
        expect(screen.getByText('Priya Patel')).toBeDefined();
        expect(screen.getByText('David Gomez')).toBeDefined();
      },
      { timeout: 10000 }
    );
    expect(payrunService.employeeNames).toHaveBeenCalledTimes(1);
    expect(payrunService.employeeNames).toHaveBeenCalledWith(['emp-1', 'emp-2', 'emp-3', 'emp-4']);
  }, 40000);

  it('never shows the employee number in the Name column when no name is found', async () => {
    payrunService.employees.mockResolvedValue({ content: [sampleEmployees[0]], totalElements: 1 });
    payrunService.employeeNames.mockResolvedValue(new Map());

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(() => expect(payrunService.employeeNames).toHaveBeenCalled());
    const number = sampleEmployees[0].employee_number;
    expect(screen.getAllByText(number)).toHaveLength(1);
  });

  it('sends SKIPPED filter when filter is changed to Skipped', async () => {
    payrunService.employees.mockResolvedValue({
      content: sampleEmployees,
      totalElements: 4,
    });

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(() => {
      expect(payrunService.employees).toHaveBeenCalledWith('run-100', {
        inclusion: undefined,
        page: 0,
        size: 20,
      });
    });

    // Click on Skipped segment
    const skippedBtn = screen.getByRole('radio', { name: 'Skipped' });
    fireEvent.click(skippedBtn);

    await waitFor(() => {
      expect(payrunService.employees).toHaveBeenCalledWith('run-100', {
        inclusion: 'SKIPPED',
        page: 0,
        size: 20,
      });
    });
  }, 40000);

  it('renders reason sentence for the two known codes', async () => {
    payrunService.employees.mockResolvedValueOnce({
      content: sampleEmployees,
      totalElements: 4,
    });

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(() => {
      // NO_SALARY mapped
      expect(screen.getByText('no salary structure in force')).toBeDefined();
      // NO_BANK_DETAILS mapped
      expect(screen.getByText('no bank details')).toBeDefined();
    });
  });

  it('renders unknown skip reason code raw', async () => {
    payrunService.employees.mockResolvedValueOnce({
      content: sampleEmployees,
      totalElements: 4,
    });

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(() => {
      // UNAUTHORIZED_ABSENCE_UNKNOWN rendered raw verbatim
      expect(screen.getByText('UNAUTHORIZED_ABSENCE_UNKNOWN')).toBeDefined();
    });
  });

  it('opens LinesDrawer when row is clicked', async () => {
    payrunService.employees.mockResolvedValueOnce({
      content: sampleEmployees,
      totalElements: 4,
    });

    render(<RunEmployees payrunId="run-100" />);

    await waitFor(() => {
      expect(screen.getByText('Priya Patel')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Priya Patel'));

    await waitFor(() => {
      expect(payrunService.lines).toHaveBeenCalledWith('run-100', 'emp-1');
    });
  });
});
