import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EditOwnSection } from './EditOwnSection.jsx';
import { useCan } from '@shell/screens';
import { portalService } from '@shell/portal/portalService.js';

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(),
}));

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getProfile: vi.fn(),
  },
}));

vi.mock('../employee/tabs/SectionTab.jsx', () => ({
  SectionTab: vi.fn(({ employeeId, sectionName, permissionOverride }) => (
    <div data-testid={`section-tab-${sectionName}`} data-employee-id={employeeId} data-perm={permissionOverride}>
      SectionTab: {sectionName} for {employeeId}
    </div>
  )),
}));

describe('EditOwnSection component (W-46.5 §5, §7)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useCan.mockReset();
    portalService.getProfile.mockReset();
  });

  it('renders null when caller lacks core.employee.update_own permission', () => {
    useCan.mockReturnValue(false);

    const { container } = render(<EditOwnSection employeeId="emp-123" />);
    expect(container.firstChild).toBeNull();
    expect(useCan).toHaveBeenCalledWith('core.employee.update_own');
  });

  it('renders personal and contact tabs with caller ID and permissionOverride', async () => {
    useCan.mockReturnValue(true);
    portalService.getProfile.mockResolvedValue({ id: 'emp-me-456' });

    render(<EditOwnSection />);

    await waitFor(() => {
      expect(screen.getByTestId('edit-own-section')).toBeDefined();
    });

    expect(portalService.getProfile).toHaveBeenCalled();
    expect(screen.getByText('Personal Details')).toBeDefined();
    expect(screen.getByText('Contact Details')).toBeDefined();

    const personalTab = screen.getByTestId('section-tab-personal');
    expect(personalTab.getAttribute('data-employee-id')).toBe('emp-me-456');
    expect(personalTab.getAttribute('data-perm')).toBe('core.employee.update_own');

    // Assert only personal and contact exist, no other tabs like job, compensation, emergency
    expect(screen.queryByText(/job/i)).toBeNull();
    expect(screen.queryByText(/compensation/i)).toBeNull();
    expect(screen.queryByText(/payroll/i)).toBeNull();
    expect(screen.queryByText(/emergency/i)).toBeNull();
  });

  it('uses passed employeeId prop without calling portalService.getProfile', async () => {
    useCan.mockReturnValue(true);

    render(<EditOwnSection employeeId="emp-direct-789" />);

    expect(screen.getByTestId('edit-own-section')).toBeDefined();
    expect(portalService.getProfile).not.toHaveBeenCalled();

    const personalTab = screen.getByTestId('section-tab-personal');
    expect(personalTab.getAttribute('data-employee-id')).toBe('emp-direct-789');
    expect(personalTab.getAttribute('data-perm')).toBe('core.employee.update_own');
  });
});
