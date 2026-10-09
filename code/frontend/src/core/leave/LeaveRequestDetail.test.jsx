import dayjs from 'dayjs';
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
    list: vi.fn(),
    eligible: vi.fn(),
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

  // D-71: shaped like ApprovalHistoryResponse - the steps are ApprovalHistoryStepResponse records,
  // stepIndex 0-based, decision APPROVED | REJECTED | null (pending), assigneeName nullable.
  const mockHistory = {
    instanceId: 'inst-1',
    flowType: 'LEAVE',
    status: 'IN_PROGRESS',
    subjectTable: 'leave_request',
    subjectId: 'req-1',
    subjectEmployeeId: 'emp-1',
    startedAt: '2026-10-01T10:00:00Z',
    completedAt: null,
    steps: [
      {
        stepId: 'step-1',
        stepIndex: 0,
        itemRef: null,
        approverKind: 'REPORTING_MANAGER',
        assigneeEmployeeId: 'emp-mgr',
        assigneeName: 'Manager Dave',
        delegatedFromEmployeeId: null,
        escalatedFromEmployeeId: null,
        reassignedFromEmployeeId: null,
        reassignReason: null,
        decision: 'APPROVED',
        comment: 'Approved as requested',
        approvedAmount: null,
        decidedAt: '2026-10-02T12:00:00Z',
        createdAt: '2026-10-01T10:00:00Z',
      },
      {
        stepId: 'step-2',
        stepIndex: 1,
        itemRef: null,
        approverKind: 'ROLE',
        assigneeEmployeeId: null,
        assigneeName: null,
        delegatedFromEmployeeId: null,
        escalatedFromEmployeeId: null,
        reassignedFromEmployeeId: null,
        reassignReason: null,
        decision: null,
        comment: null,
        approvedAmount: null,
        decidedAt: null,
        createdAt: '2026-10-02T12:00:00Z',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    leaveRequestService.get.mockResolvedValue(mockRequest);
    employeeService.get.mockResolvedValue(mockEmployee);
    leaveTypeService.get.mockResolvedValue(mockLeaveType);
    leaveTypeService.list.mockResolvedValue([mockLeaveType]);
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
      expect(screen.getByText(/Approved as requested/i)).toBeDefined();
    });
  });

  it('D-71: renders each approval step from the fields the server sends', async () => {
    render(
      <MemoryRouter initialEntries={['/leave/requests/req-1']}>
        <Routes>
          <Route path="/leave/requests/:id" element={<LeaveRequestDetail />} />
        </Routes>
      </MemoryRouter>
    );

    const decided = dayjs('2026-10-02T12:00:00Z').format('D MMM YYYY, HH:mm');
    await waitFor(() => {
      expect(screen.getByText('Step 1 — Approved')).toBeDefined();
    });
    expect(screen.getByText(`by Manager Dave · ${decided}`)).toBeDefined();
    expect(screen.getByText('Step 2 — Pending')).toBeDefined();
    expect(screen.getByText('Awaiting decision')).toBeDefined();
    const dots = document.querySelectorAll('.ant-timeline-item-head');
    expect(dots[0].className).toMatch(/green/);
    expect(dots[1].className).toMatch(/blue/);
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

  it('D-72: a manager from /approvals sees the request even when the side calls are refused, and goes back to /approvals', async () => {
    // A manager holds core.leave.read_team and core.approval.decide, not core.leave.read or manage.
    vi.spyOn(useCanModule, 'useCan').mockImplementation(
      (code) => code === 'core.leave.read_team' || code === 'core.approval.decide'
    );
    const forbidden = Object.assign(new Error('Forbidden'), { response: { status: 403 } });
    employeeService.get.mockRejectedValue(forbidden);
    leaveTypeService.list.mockRejectedValue(forbidden);
    leaveTypeService.eligible.mockRejectedValue(forbidden);
    approvalService.history.mockRejectedValue(forbidden);

    render(
      <MemoryRouter initialEntries={['/leave/requests/req-1']}>
        <Routes>
          <Route path="/leave/requests/:id" element={<LeaveRequestDetail />} />
          <Route path="/approvals" element={<div>Approvals inbox</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.getByText('Family emergency')).toBeDefined());
    expect(screen.queryByRole('button', { name: /Cancel Leave/i })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Back/i }));
    await waitFor(() => expect(screen.getByText('Approvals inbox')).toBeDefined());
  });
});
