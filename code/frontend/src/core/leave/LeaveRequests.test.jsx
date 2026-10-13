import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LeaveRequests } from './LeaveRequests.jsx';
import { leaveRequestService } from './leaveRequestService.js';
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
      employeeName: 'Bob Williams',
      leaveTypeId: 'type-1',
      leaveTypeName: 'Annual Leave',
      fromDate: '2026-10-10',
      toDate: '2026-10-12',
      workingDays: 3,
      status: 'APPROVED',
      isHalfDay: false,
      createdAt: '2026-10-01T10:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.list.mockResolvedValue({ content: [] });
    leaveRequestService.list.mockResolvedValue({
      content: mockRequests,
      totalElements: 1,
    });
  });

  it('labels rows from the reply: employee and leave type names, dates as dates (D-80)', async () => {
    render(
      <MemoryRouter>
        <LeaveRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Bob Williams')).toBeDefined();
      expect(screen.getByText('Annual Leave')).toBeDefined();
      expect(screen.getByText('3')).toBeDefined();
      expect(screen.getByText('Approved')).toBeDefined();
    });
    expect(screen.getByText('10 Oct 2026 to 12 Oct 2026')).toBeDefined();
    expect(screen.getByText('1 Oct 2026')).toBeDefined();
    expect(screen.queryByText('emp-1')).toBeNull();
    expect(screen.queryByText('type-1')).toBeNull();
    // No client lookup of employees just to name the rows.
    expect(employeeService.list).not.toHaveBeenCalled();
  });

  it('a row the server could not name shows a dash, never the id', async () => {
    leaveRequestService.list.mockResolvedValueOnce({
      content: [{ ...mockRequests[0], employeeName: null, leaveTypeName: null }],
      totalElements: 1,
    });
    render(
      <MemoryRouter>
        <LeaveRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Approved')).toBeDefined();
    });
    expect(screen.queryByText('emp-1')).toBeNull();
    expect(screen.queryByText('type-1')).toBeNull();
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

  it('labels a DRAFT request "Draft", never the raw code (D-86)', async () => {
    leaveRequestService.list.mockResolvedValueOnce({
      content: [{ ...mockRequests[0], status: 'DRAFT' }],
      totalElements: 1,
    });
    render(
      <MemoryRouter>
        <LeaveRequests />
      </MemoryRouter>
    );
    await waitFor(() => {
      expect(screen.getByText('Draft')).toBeDefined();
    });
    expect(screen.queryByText('DRAFT')).toBeNull();
  });
});
