import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LeaveBalanceDetail } from './LeaveBalanceDetail.jsx';
import { portalService } from '@shell/portal/portalService.js';

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getLeaveBalances: vi.fn(),
  },
}));

describe('LeaveBalanceDetail component (D-139 / UI-ENH-LV-01)', () => {
  const mockBalances = [
    {
      leaveTypeId: 'type-cl',
      leaveTypeCode: 'CL',
      leaveTypeName: 'Casual Leave',
      leaveYear: '2026',
      entitlementDays: 12,
      carriedForwardDays: 2,
      carryForwardExpiresOn: '2026-06-30',
      accruedDays: 0,
      consumedDays: 3,
      pendingDays: 2,
      availableDays: 9,
      remainingDays: 11,
      expired: false,
    },
    {
      leaveTypeId: 'type-sl',
      leaveTypeCode: 'SL',
      leaveTypeName: 'Sick Leave',
      leaveYear: '2026',
      entitlementDays: 10,
      carriedForwardDays: 0,
      accruedDays: 0,
      consumedDays: 1,
      pendingDays: 0,
      availableDays: 9,
      remainingDays: 9,
      expired: false,
    },
    {
      leaveTypeId: 'type-co',
      leaveTypeCode: 'CO',
      leaveTypeName: 'Comp Off',
      leaveYear: '2026',
      entitlementDays: 5,
      consumedDays: 5,
      remainingDays: 0,
      availableDays: 0,
      expired: true,
      leaveTypeValidTo: '2026-03-31',
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    portalService.getLeaveBalances.mockReset();
    portalService.getLeaveBalances.mockResolvedValue(mockBalances);
  });

  it('renders page header, period, and KPI cards correctly', async () => {
    render(
      <MemoryRouter>
        <LeaveBalanceDetail />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('My Leave Balances')).toBeDefined();
    });

    expect(screen.getByText('Leave Allocation Period: 2026')).toBeDefined();
    expect(screen.getByText('Total Annual Entitlement')).toBeDefined();
    expect(screen.getByText('Total Consumed')).toBeDefined();
    expect(screen.getAllByText('Pending Approval').length).toBeGreaterThan(0);
    expect(screen.getByText('Net Available Balance')).toBeDefined();

    // Entitlement: 12 + 10 = 22 for active allocations
    expect(screen.getByText('22')).toBeDefined();
    // Consumed: 3 + 1 = 4 for active allocations
    expect(screen.getByText('4')).toBeDefined();
    // Pending: 2 for active allocations
    expect(screen.getByText('2')).toBeDefined();
    // Available: 9 + 9 = 18
    expect(screen.getByText('18')).toBeDefined();
  });

  it('renders balance breakdown table with metadata and carry-forward expiry', async () => {
    render(
      <MemoryRouter>
        <LeaveBalanceDetail />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Casual Leave')).toBeDefined();
    });

    expect(screen.getByText('Sick Leave')).toBeDefined();
    expect(screen.getByText('Comp Off')).toBeDefined();
    expect(screen.getByText('Expired')).toBeDefined();

    // Carry forward with expiry badge
    expect(screen.getByText('Exp: 30 Jun 2026')).toBeDefined();
    // Pending approval badge
    expect(screen.getAllByText('2 days').length).toBeGreaterThan(0);
  });

  it('triggers onNavigate to /me/leave when Back to Leave button is clicked', async () => {
    const handleNavigate = vi.fn();
    render(
      <MemoryRouter>
        <LeaveBalanceDetail onNavigate={handleNavigate} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Back to Leave')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Back to Leave'));
    expect(handleNavigate).toHaveBeenCalledWith('/me/leave');
  });

  it('triggers onNavigate to /me/leave/apply when Apply for Leave button is clicked', async () => {
    const handleNavigate = vi.fn();
    render(
      <MemoryRouter>
        <LeaveBalanceDetail onNavigate={handleNavigate} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Apply for Leave')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Apply for Leave'));
    expect(handleNavigate).toHaveBeenCalledWith('/me/leave/apply');
  });

  it('renders error result on API failure with retry option', async () => {
    portalService.getLeaveBalances.mockRejectedValue(new Error('Network error'));

    render(
      <MemoryRouter>
        <LeaveBalanceDetail />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Could not load leave balances')).toBeDefined();
    });
    expect(screen.getByText('Network error')).toBeDefined();
    expect(screen.getByText('Try Again')).toBeDefined();
  });
});
