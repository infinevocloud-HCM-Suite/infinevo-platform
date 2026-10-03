import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EmployeeLeave } from './EmployeeLeave.jsx';
import { leaveConsumptionService } from './leaveConsumptionService.js';
import { employeeService } from '../employee/employeeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveConsumptionService.js', () => ({
  leaveConsumptionService: {
    rows: vi.fn(),
    lop: vi.fn(),
  },
}));

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    get: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  errorMsg: vi.fn(),
}));

describe('EmployeeLeave component', () => {
  const mockEmployee = {
    id: 'emp-1',
    firstName: 'Diana',
    lastName: 'Prince',
    employeeNumber: 'EMP042',
  };

  const mockConsumption = [
    {
      id: 'cons-1',
      consumedOn: '2026-05-10',
      period: '2026-05',
      consumedDays: 2.0,
      reason: 'Vacation',
      leaveRequestId: 'req-12345678',
      reversesId: null,
    },
  ];

  const mockLop = {
    employeeId: 'emp-1',
    period: '2026-10',
    totalLopDays: 1.5,
    deltaRows: [
      {
        id: 'lop-1',
        period: '2026-10',
        lopDays: 1.5,
        reversesId: null,
        createdAt: '2026-10-15T10:00:00Z',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.get.mockResolvedValue(mockEmployee);
    leaveConsumptionService.rows.mockResolvedValue(mockConsumption);
    leaveConsumptionService.lop.mockResolvedValue(mockLop);
  });

  it('renders employee consumption ledger and monthly LOP metrics', async () => {
    render(
      <MemoryRouter initialEntries={['/leave/employees/emp-1']}>
        <Routes>
          <Route path="/leave/employees/:id" element={<EmployeeLeave />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/Leave & Loss of Pay: Diana Prince/i)).toBeDefined();
      expect(screen.getByText('EMP042')).toBeDefined();
      expect(screen.getByText('Monthly Loss of Pay (LOP)')).toBeDefined();
      expect(screen.getByText('Consumption Ledger')).toBeDefined();
      expect(screen.getByText('Vacation')).toBeDefined();
      expect(screen.getByText('Debit')).toBeDefined();
      expect(screen.getByText('Deduction')).toBeDefined();
    });
  });
});
