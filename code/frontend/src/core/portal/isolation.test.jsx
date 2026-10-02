import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
