import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LeaveRequests } from './LeaveRequests.jsx';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import * as useCanModule from '@shell/screens';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('./leaveRequestService.js', () => ({
  leaveRequestService: {
    list: vi.fn(),
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
  errorMsg: vi.fn(),
}));

describe('LeaveRequests component', () => {
  const mockRequests = [
    {
      id: 'req-1',
      employeeId: 'emp-1',
      leaveTypeId: 'type-1',
      fromDate: '2026-10-10',
      toDate: '2026-10-12',
      workingDays: 3,
      status: 'APPROVED',
      isHalfDay: false,
      createdAt: '2026-10-01T10:00:00Z',
    },
  ];

  const mockEmployees = [
    { id: 'emp-1', firstName: 'Bob', lastName: 'Williams', employeeNumber: 'EMP005' },
  ];

  const mockTypes = [
    { id: 'type-1', name: 'Annual Leave', code: 'AL' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.list.mockResolvedValue(mockEmployees);
    leaveTypeService.list.mockResolvedValue(mockTypes);
    leaveRequestService.list.mockResolvedValue({
      content: mockRequests,
      totalElements: 1,
    });
  });

  it('renders requests table with records, days, and status tags', async () => {
    render(
      <MemoryRouter>
        <LeaveRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Bob Williams')).toBeDefined();
      expect(screen.getByText('Annual Leave (AL)')).toBeDefined();
      expect(screen.getByText('3')).toBeDefined();
      expect(screen.getByText('Approved')).toBeDefined();
    });
  });

  it('navigates to /leave/requests/new when Record Leave button is clicked', async () => {
    render(
      <MemoryRouter>
        <LeaveRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Bob Williams')).toBeDefined();
    });

    const recordButton = screen.getByRole('button', { name: /Record Leave/i });
    fireEvent.click(recordButton);

    expect(mockNavigate).toHaveBeenCalledWith('/leave/requests/new');
  });
});
