import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ApplyLeave } from './ApplyLeave.jsx';
import { MyRequests } from './MyRequests.jsx';
import { EditOwnSection } from './EditOwnSection.jsx';
import { portalService } from '@shell/portal/portalService.js';
import { leaveTypeService } from '../leave/leaveTypeService.js';
import { leaveRequestService } from '../leave/leaveRequestService.js';

vi.mock('@shell/screens', () => ({
  useCan: vi.fn().mockReturnValue(true),
  NotEntitled: vi.fn(({ action }) => <div data-testid="not-entitled">{action}</div>),
}));

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getProfile: vi.fn(),
    getPanels: vi.fn(),
    getLeaveRequests: vi.fn(),
  },
}));

vi.mock('../leave/leaveTypeService.js', () => ({
  leaveTypeService: {
    eligible: vi.fn(),
    list: vi.fn(),
  },
}));

vi.mock('../leave/leaveRequestService.js', () => ({
  leaveRequestService: {
    create: vi.fn(),
    submit: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('../employee/tabs/SectionTab.jsx', () => ({
  SectionTab: vi.fn(({ employeeId }) => (
    <div data-testid="section-tab" data-employee-id={employeeId}>
      SectionTab
    </div>
  )),
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('Employee Portal Isolation (W-46.5 §7, §9)', () => {
  const callerProfile = { id: 'emp-caller-me-101', name: 'Self Employee' };

  beforeEach(() => {
    vi.restoreAllMocks();
    portalService.getProfile.mockReset();
    portalService.getPanels.mockReset();
    portalService.getLeaveRequests.mockReset();
    leaveTypeService.eligible.mockReset();
    leaveTypeService.list.mockReset();
    leaveRequestService.create.mockReset();
    leaveRequestService.submit.mockReset();
    leaveRequestService.withdraw.mockReset();
    leaveRequestService.cancel.mockReset();

    portalService.getProfile.mockResolvedValue(callerProfile);
    portalService.getPanels.mockResolvedValue([{ key: 'leave', balances: [] }]);
    portalService.getLeaveRequests.mockResolvedValue([]);
    leaveTypeService.eligible.mockResolvedValue([]);
  });

  it('ApplyLeave only queries eligible types for the authenticated caller (me)', async () => {
    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(portalService.getProfile).toHaveBeenCalled();
    });

    expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-caller-me-101');
    // Ensure it NEVER called leaveTypeService.list (which is admin)
    expect(leaveTypeService.list).not.toHaveBeenCalled();
  });

  it('ApplyLeave submit payload does not contain an employeeId (caller isolation)', async () => {
    leaveTypeService.eligible.mockResolvedValue([
      { id: 'type-al', name: 'Annual Leave', code: 'AL' },
    ]);
    leaveRequestService.create.mockResolvedValue({ id: 'req-new-1', status: 'PENDING' });

    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-caller-me-101');
    });

    // Select leave type
    const select = screen.getByRole('combobox');
    fireEvent.mouseDown(select);
    fireEvent.click(await screen.findByText('Annual Leave (AL)'));

    // Fill dates
    const datePickers = screen.getAllByPlaceholderText('Select date');
    fireEvent.change(datePickers[0], { target: { value: '2026-11-01' } });
    fireEvent.keyDown(datePickers[0], { key: 'Enter' });
    fireEvent.change(datePickers[1], { target: { value: '2026-11-02' } });
    fireEvent.keyDown(datePickers[1], { key: 'Enter' });

    // Fill reason
    const reasonInput = screen.getByPlaceholderText(/Enter reason for leave/i);
    fireEvent.change(reasonInput, { target: { value: 'Personal leave' } });

    // Submit
    const submitBtn = screen.getByRole('button', { name: /Submit/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveRequestService.create).toHaveBeenCalled();
    });

    const payload = leaveRequestService.create.mock.calls[0][0];
    expect(payload.employeeId).toBeUndefined();
    expect(payload.leaveTypeId).toBe('type-al');
    expect(payload.submit).toBe(true);
  });

  it('MyRequests only calls /me/leave/requests and does not pass external employee IDs', async () => {
    render(
      <MemoryRouter>
        <MyRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(portalService.getLeaveRequests).toHaveBeenCalled();
    });

    // portalService.getLeaveRequests takes no employeeId parameter
    expect(portalService.getLeaveRequests).toHaveBeenCalledWith();
  });

  it('MyRequests actions (submit, withdraw, cancel) operate strictly on target request ID without employee ID', async () => {
    const sampleRequests = [
      {
        id: 'req-draft-1',
        leaveTypeName: 'Annual Leave',
        fromDate: '2026-11-10',
        toDate: '2026-11-11',
        workingDays: 2,
        status: 'DRAFT',
      },
      {
        id: 'req-pending-2',
        leaveTypeName: 'Sick Leave',
        fromDate: '2026-11-15',
        toDate: '2026-11-16',
        workingDays: 2,
        status: 'PENDING',
      },
      {
        id: 'req-approved-3',
        leaveTypeName: 'Casual Leave',
        fromDate: '2026-12-01',
        toDate: '2026-12-02',
        workingDays: 2,
        status: 'APPROVED',
      },
    ];

    portalService.getLeaveRequests.mockResolvedValue(sampleRequests);
    leaveRequestService.submit.mockResolvedValue({});
    leaveRequestService.withdraw.mockResolvedValue({});
    leaveRequestService.cancel.mockResolvedValue({});

    const { container } = render(
      <MemoryRouter>
        <MyRequests requests={sampleRequests} />
      </MemoryRouter>
    );

    // 1. Test Submit action on draft request
    const submitBtn = screen.getByRole('button', { name: /Submit/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveRequestService.submit).toHaveBeenCalledWith('req-draft-1');
    });

    // 2. Test Withdraw action on pending request
    const withdrawBtn = screen.getByRole('button', { name: /Withdraw/i });
    fireEvent.click(withdrawBtn);

    await waitFor(() => {
      expect(screen.getByText('Withdraw Leave Request')).toBeDefined();
    });

    const withdrawReason = screen.getByPlaceholderText(/reason/i);
    fireEvent.change(withdrawReason, { target: { value: 'Need to cancel trip' } });

    const confirmWithdraw = screen.getByRole('button', { name: /withdraw request/i });
    fireEvent.click(confirmWithdraw);

    await waitFor(() => {
      expect(leaveRequestService.withdraw).toHaveBeenCalledWith('req-pending-2', 'Need to cancel trip');
    });

    // 3. Test Cancel action on approved request
    const cancelBtn = container.querySelector('#btn-cancel-req-approved-3');
    fireEvent.click(cancelBtn);

    await waitFor(() => {
      expect(screen.getByText('Cancel Approved Leave')).toBeDefined();
    });

    const cancelReason = screen.getByPlaceholderText(/reason/i);
    fireEvent.change(cancelReason, { target: { value: 'Plans postponed' } });

    const confirmCancel = screen.getByRole('button', { name: /cancel leave/i });
    fireEvent.click(confirmCancel);

    await waitFor(() => {
      expect(leaveRequestService.cancel).toHaveBeenCalledWith('req-approved-3', 'Plans postponed');
    });
  });

  it('EditOwnSection resolves employee ID strictly from caller profile', async () => {
    render(
      <MemoryRouter>
        <EditOwnSection />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(portalService.getProfile).toHaveBeenCalled();
    });

    const tabs = screen.getAllByTestId('section-tab');
    for (const tab of tabs) {
      expect(tab.getAttribute('data-employee-id')).toBe('emp-caller-me-101');
    }
  });
});
