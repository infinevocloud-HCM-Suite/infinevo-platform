import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MobileTabBar } from './MobileTabBar.jsx';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('MobileTabBar', () => {
  it('renders 4 standard tabs for employee without approval rights', () => {
    render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileTabBar canApproveOverride={false} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('tablist')).toBeTruthy();
    expect(screen.getByRole('tab', { name: /home/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /attendance/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /leave/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /payslips/i })).toBeTruthy();
    expect(screen.queryByRole('tab', { name: /approvals/i })).toBeNull();
  });

  it('renders 5 tabs including Approvals when user can approve', () => {
    render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileTabBar canApproveOverride={true} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('tab', { name: /approvals/i })).toBeTruthy();
  });

  it('marks Home tab as active when at /me', () => {
    render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileTabBar canApproveOverride={false} />
      </MemoryRouter>,
    );

    const homeTab = screen.getByRole('tab', { name: /home/i });
    expect(homeTab.getAttribute('aria-selected')).toBe('true');
    expect(homeTab.className).toContain('active');

    const leaveTab = screen.getByRole('tab', { name: /leave/i });
    expect(leaveTab.getAttribute('aria-selected')).toBe('false');
    expect(leaveTab.className).not.toContain('active');
  });

  it('marks Attendance tab as active when at /me/attendance', () => {
    render(
      <MemoryRouter initialEntries={['/me/attendance']}>
        <MobileTabBar canApproveOverride={false} />
      </MemoryRouter>,
    );

    const attendanceTab = screen.getByRole('tab', { name: /attendance/i });
    expect(attendanceTab.getAttribute('aria-selected')).toBe('true');
    expect(attendanceTab.className).toContain('active');
  });

  it('triggers navigation when clicking on a tab', () => {
    mockNavigate.mockReset();
    render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileTabBar canApproveOverride={true} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('tab', { name: /payslips/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/me/payslips');

    fireEvent.click(screen.getByRole('tab', { name: /approvals/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/me/approvals');
  });
});
