import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Welcome } from './Welcome.jsx';
import * as meModule from '../auth/useMe.js';
import * as navigationModule from '../navigation/useNavigation.js';
import { apiClient } from '../../shared/api/client.js';
import { portalService } from '../portal/portalService.js';

function me(overrides = {}) {
  return vi.spyOn(meModule, 'useMe').mockReturnValue({
    displayName: 'Ana Acme',
    roles: [],
    email: null,
    welcomeSeen: false,
    loading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  });
}

function feed(overrides = {}) {
  return vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
    items: [
      { key: 'approvals', labelKey: 'nav.approvals', path: '/approvals' },
      { key: 'employees', labelKey: 'nav.employees', path: '/employees' },
    ],
    tenantName: 'Acme Ltd',
    tenantLogoUrl: null,
    tagline: 'People first',
    homePath: '/setup',
    loading: false,
    ...overrides,
  });
}

function renderWelcome(homePath = '/setup') {
  return render(
    <MemoryRouter initialEntries={['/welcome']}>
      <Routes>
        <Route path="/welcome" element={<Welcome homePath={homePath} />} />
        <Route path={homePath} element={<div>home page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function hrefsIn(testId) {
  return Array.from(screen.getByTestId(testId).querySelectorAll('a')).map((a) => a.getAttribute('href'));
}

describe('Welcome (W-73.8)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    feed();
    vi.spyOn(portalService, 'getPanels').mockResolvedValue([{ code: 'profile' }, { code: 'payslips' }]);
  });

  it('shows the tenant branding', () => {
    me({ roles: ['employee'] });
    renderWelcome();
    expect(screen.getByRole('heading', { name: 'Welcome to Acme Ltd' })).toBeDefined();
    expect(screen.getByText('People first')).toBeDefined();
    expect(screen.getByText('AL')).toBeDefined();
  });

  it('tenant admin: the three steps, linking into /setup, with 2/8 from the checklist', async () => {
    me({ roles: ['tenant-admin'] });
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { completedCount: 2, totalCount: 8 } });
    renderWelcome();

    expect(screen.getByTestId('welcome-card-admin')).toBeDefined();
    expect(screen.queryByTestId('welcome-card-employee')).toBeNull();
    expect(screen.queryByTestId('welcome-card-staff')).toBeNull();
    expect(hrefsIn('welcome-card-admin')).toEqual([
      '/setup#setup-step-row-work-location',
      '/setup#setup-step-row-pay-schedule',
      '/setup#setup-step-row-epf',
    ]);
    expect(screen.getByText('1. Add work location and employees')).toBeDefined();
    expect(screen.getByText('2. Pay schedule and salary components')).toBeDefined();
    expect(screen.getByText('3. Statutory settings')).toBeDefined();
    expect(await screen.findByText('2/8 setup steps done')).toBeDefined();
    expect(get).toHaveBeenCalledWith('/v1/setup-checklist');
  });

  it('tenant admin: a failed checklist call hides the progress and keeps the steps', async () => {
    me({ roles: ['tenant-admin'] });
    vi.spyOn(apiClient, 'get').mockRejectedValue({ status: 500 });
    renderWelcome();
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled());
    expect(screen.queryByTestId('welcome-setup-progress')).toBeNull();
    expect(screen.getByText('3. Statutory settings')).toBeDefined();
  });

  it('hr: "Where things are" with home, approvals and people; no setup card, no checklist call', () => {
    me({ roles: ['hr'] });
    const get = vi.spyOn(apiClient, 'get');
    renderWelcome('/hrms/dashboard');

    expect(hrefsIn('welcome-card-staff')).toEqual(['/hrms/dashboard', '/approvals', '/employees']);
    expect(screen.queryByTestId('welcome-card-admin')).toBeNull();
    expect(get).not.toHaveBeenCalled();
  });

  it('a link the feed does not name is left out', () => {
    feed({ items: [{ key: 'approvals', labelKey: 'nav.approvals', path: '/approvals' }] });
    me({ roles: ['manager'] });
    renderWelcome('/hrms/dashboard');
    expect(hrefsIn('welcome-card-staff')).toEqual(['/hrms/dashboard', '/approvals']);
  });

  it('employee: profile, payslips and leave links into /me; no setup card', async () => {
    me({ roles: ['employee'] });
    renderWelcome('/me');
    await waitFor(() =>
      expect(hrefsIn('welcome-card-employee')).toEqual(['/me/profile', '/me/payslips', '/me/leave/apply']),
    );
    expect(screen.queryByTestId('welcome-card-admin')).toBeNull();
    expect(screen.queryByTestId('welcome-card-staff')).toBeNull();
  });

  it('Go to my home marks the page seen and opens the home path', async () => {
    me({ roles: ['employee'], welcomeSeen: false });
    const mark = vi.spyOn(meModule, 'markWelcomeSeen').mockResolvedValue(undefined);
    renderWelcome('/me');
    fireEvent.click(screen.getByRole('button', { name: /Go to my home/ }));
    expect(await screen.findByText('home page')).toBeDefined();
    expect(mark).toHaveBeenCalledTimes(1);
  });

  it('opened later from Getting started: Go to my home writes nothing', async () => {
    me({ roles: ['employee'], welcomeSeen: true });
    const mark = vi.spyOn(meModule, 'markWelcomeSeen').mockResolvedValue(undefined);
    renderWelcome('/me');
    fireEvent.click(screen.getByRole('button', { name: /Go to my home/ }));
    expect(await screen.findByText('home page')).toBeDefined();
    expect(mark).not.toHaveBeenCalled();
  });

  it('a failed write still takes the user home', async () => {
    me({ roles: ['employee'], welcomeSeen: false });
    vi.spyOn(meModule, 'markWelcomeSeen').mockRejectedValue({ status: 500 });
    renderWelcome('/me');
    fireEvent.click(screen.getByRole('button', { name: /Go to my home/ }));
    expect(await screen.findByText('home page')).toBeDefined();
  });

  it('employee in a tenant without Payroll: no payslips link', async () => {
    portalService.getPanels.mockResolvedValue([{ code: 'profile' }, { code: 'leave' }]);
    me({ roles: ['employee'] });
    renderWelcome('/me');
    await waitFor(() => expect(portalService.getPanels).toHaveBeenCalled());
    await act(async () => {});
    expect(hrefsIn('welcome-card-employee')).toEqual(['/me/profile', '/me/leave/apply']);
  });

  it('a role with no card marks the page seen and goes straight home', async () => {
    me({ roles: ['platform-admin'], welcomeSeen: false });
    const mark = vi.spyOn(meModule, 'markWelcomeSeen').mockResolvedValue(undefined);
    renderWelcome('/admin/tenants');
    expect(await screen.findByText('home page')).toBeDefined();
    expect(mark).toHaveBeenCalledTimes(1);
  });
});
