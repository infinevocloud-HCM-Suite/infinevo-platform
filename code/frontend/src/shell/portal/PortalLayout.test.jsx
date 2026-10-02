import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { PortalLayout } from './PortalLayout.jsx';
import { portalService } from './portalService.js';

describe('PortalLayout component (W-25 §7)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders exactly the panels returned from /me/panels', async () => {
    const acmePanels = [
      { code: 'profile', title: 'My Profile', displayOrder: 1, endpoint: '/api/v1/me/employee', requiredAction: 'core.employee.read_own' },
      { code: 'leave', title: 'My Leave', displayOrder: 2, endpoint: '/api/v1/me/leave-requests', requiredAction: 'core.leave.read_own' },
      { code: 'documents', title: 'My Documents', displayOrder: 3, endpoint: '/api/v1/me/documents', requiredAction: 'core.document.read_own' },
      { code: 'payslips', title: 'My Payslips', displayOrder: 4, endpoint: '/api/v1/me/payslips', requiredAction: 'payroll.payslip.read_own' },
    ];

    vi.spyOn(portalService, 'getPanels').mockResolvedValue(acmePanels);
    vi.spyOn(portalService, 'getProfile').mockResolvedValue({
      id: 'emp-1',
      employeeNumber: 'EMP001',
      firstName: 'Arun',
      lastName: 'Acme',
      status: 'ACTIVE',
    });

    render(
      <MemoryRouter initialEntries={['/me']}>
        <Routes>
          <Route path="/me" element={<PortalLayout />} />
          <Route path="/me/:panelId" element={<PortalLayout />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Employee Self-Service')).toBeDefined();
    });

    // Assert the 4 returned panels are rendered
    expect(screen.getByRole('tab', { name: /my profile/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my leave/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my documents/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my payslips/i })).toBeDefined();

    // Assert timesheet is NOT rendered (Acme does not have HRMS)
    expect(screen.queryByRole('tab', { name: /my timesheet/i })).toBeNull();
  });

  it('renders empty portal state when /me/panels returns an empty list', async () => {
    vi.spyOn(portalService, 'getPanels').mockResolvedValue([]);

    render(
      <MemoryRouter initialEntries={['/me']}>
        <Routes>
          <Route path="/me" element={<PortalLayout />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('portal-empty')).toBeDefined();
    });

    expect(screen.getByText('Self-Service Portal Unavailable')).toBeDefined();
    expect(screen.queryByRole('tab')).toBeNull();
  });

  it('renders all five panels when tenant is entitled to both modules (Globex)', async () => {
    const globexPanels = [
      { code: 'profile', title: 'My Profile', displayOrder: 1, endpoint: '/api/v1/me/employee', requiredAction: 'core.employee.read_own' },
      { code: 'leave', title: 'My Leave', displayOrder: 2, endpoint: '/api/v1/me/leave-requests', requiredAction: 'core.leave.read_own' },
      { code: 'documents', title: 'My Documents', displayOrder: 3, endpoint: '/api/v1/me/documents', requiredAction: 'core.document.read_own' },
      { code: 'payslips', title: 'My Payslips', displayOrder: 4, endpoint: '/api/v1/me/payslips', requiredAction: 'payroll.payslip.read_own' },
      { code: 'timesheet', title: 'My Timesheet', displayOrder: 5, endpoint: '/api/v1/me/timesheet', requiredAction: 'hrms.timesheet.read_own' },
    ];

    vi.spyOn(portalService, 'getPanels').mockResolvedValue(globexPanels);
    vi.spyOn(portalService, 'getProfile').mockResolvedValue({
      id: 'emp-2',
      employeeNumber: 'EMP002',
      firstName: 'Ravi',
      lastName: 'Kumar',
      status: 'ACTIVE',
    });

    render(
      <MemoryRouter initialEntries={['/me']}>
        <Routes>
          <Route path="/me" element={<PortalLayout />} />
          <Route path="/me/:panelId" element={<PortalLayout />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Employee Self-Service')).toBeDefined();
    });

    expect(screen.getByRole('tab', { name: /my profile/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my leave/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my documents/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my payslips/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /my timesheet/i })).toBeDefined();
  });

  it('refuses access when a direct URL requests an unheld panel', async () => {
    const acmePanels = [
      { code: 'profile', title: 'My Profile', displayOrder: 1, endpoint: '/api/v1/me/employee', requiredAction: 'core.employee.read_own' },
      { code: 'leave', title: 'My Leave', displayOrder: 2, endpoint: '/api/v1/me/leave-requests', requiredAction: 'core.leave.read_own' },
    ];

    vi.spyOn(portalService, 'getPanels').mockResolvedValue(acmePanels);

    render(
      <MemoryRouter initialEntries={['/me/timesheet']}>
        <Routes>
          <Route path="/me" element={<PortalLayout />} />
          <Route path="/me/:panelId" element={<PortalLayout />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('portal-panel-forbidden')).toBeDefined();
    });

    expect(screen.getByText('Panel Not Available')).toBeDefined();
  });
});
