import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LeaveRequestDetail } from './LeaveRequestDetail.jsx';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import { approvalService } from '../approvals/approvalService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveRequestService.js', () => ({
  leaveRequestService: {
    get: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('./leaveTypeService.js', () => ({
  leaveTypeService: {
    get: vi.fn(),
  },
}));

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    get: vi.fn(),
  },
}));

vi.mock('../approvals/approvalService.js', () => ({
  approvalService: {
    history: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('LeaveRequestDetail component', () => {
  const mockRequest = {
    id: 'req-1',
    employeeId: 'emp-1',
    leaveTypeId: 'type-1',
    fromDate: '2026-10-10',
    toDate: '2026-10-12',
    workingDays: 3,
    status: 'APPROVED',
    reason: 'Family emergency',
    onBehalf: false,
    approvalInstanceId: 'inst-1',
    createdAt: '2026-10-01T10:00:00Z',
    decidedAt: '2026-10-02T12:00:00Z',
  };

  const mockEmployee = {
    id: 'emp-1',
    firstName: 'Charlie',
    lastName: 'Brown',
    employeeNumber: 'EMP009',
  };

  const mockLeaveType = {
    id: 'type-1',
    name: 'Casual Leave',
    code: 'CL',
  };

  const mockHistory = [
    {
      id: 'step-1',
      stepName: 'Manager Review',
      status: 'APPROVED',
      decidedBy: 'Manager Dave',
      decidedAt: '2026-10-02T12:00:00Z',
      comment: 'Approved as requested',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    leaveRequestService.get.mockResolvedValue(mockRequest);
    employeeService.get.mockResolvedValue(mockEmployee);
    leaveTypeService.get.mockResolvedValue(mockLeaveType);
    approvalService.history.mockResolvedValue(mockHistory);
  });

  it('renders request details, employee, leave type and approval trail', async () => {
    render(
      <MemoryRouter initialEntries={['/leave/requests/req-1']}>
        <Routes>
          <Route path="/leave/requests/:id" element={<LeaveRequestDetail />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Charlie Brown (EMP009)')).toBeDefined();
      expect(screen.getByText('Casual Leave (CL)')).toBeDefined();
      expect(screen.getByText('Family emergency')).toBeDefined();
      expect(screen.getByText(/Manager Review - APPROVED/i)).toBeDefined();
      expect(screen.getByText(/Approved as requested/i)).toBeDefined();
    });
  });

  it('allows cancelling an approved leave with a reason', async () => {
    leaveRequestService.cancel.mockResolvedValueOnce({ id: 'req-1', status: 'CANCELLED' });

    render(
      <MemoryRouter initialEntries={['/leave/requests/req-1']}>
        <Routes>
          <Route path="/leave/requests/:id" element={<LeaveRequestDetail />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Cancel Leave/i })).toBeDefined();
    });

    const cancelBtn = screen.getByRole('button', { name: /Cancel Leave/i });
    fireEvent.click(cancelBtn);

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /Cancel Approved Leave/i })).toBeDefined();
    });

    const confirmBtn = screen.getByRole('button', { name: /Confirm Cancellation/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(leaveRequestService.cancel).toHaveBeenCalledWith('req-1', '');
    });
  });
});
