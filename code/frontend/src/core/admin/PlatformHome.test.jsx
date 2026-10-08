import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PlatformHome } from './PlatformHome.jsx';
import { tenantService } from './tenantService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('./tenantService.js', () => ({
  tenantService: { getSummary: vi.fn(), resendAdminInvitation: vi.fn() },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

const summary = {
  total: 7,
  byStatus: { ACTIVE: 5, PAST_DUE: 0, SUSPENDED: 2, CANCELLED: 0 },
  createdLast30Days: 3,
  recent: [
    { id: 't-acme', name: 'Acme', createdAt: '2026-10-01T00:00:00Z', status: 'ACTIVE' },
    { id: 't-globex', name: 'Globex', createdAt: '2026-09-20T00:00:00Z', status: 'SUSPENDED' },
  ],
  waitingForAdmin: [
    {
      id: 't-initech',
      name: 'Initech',
      adminEmail: 'boss@initech.example',
      invitationStatus: 'EXPIRED',
      expiresAt: '2026-10-01T00:00:00Z',
    },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <PlatformHome />
    </MemoryRouter>,
  );
}

function tile(key) {
  return screen.getByTestId(`tile-${key}`);
}

describe('PlatformHome', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tenantService.getSummary.mockResolvedValue(summary);
    tenantService.resendAdminInvitation.mockResolvedValue(undefined);
  });

  it('renders the four tiles from the summary counts', async () => {
    renderPage();
    await screen.findByText('Needs attention');
    expect(within(tile('total')).getByText('7')).toBeDefined();
    expect(within(tile('active')).getByText('5')).toBeDefined();
    expect(within(tile('suspended')).getByText('2')).toBeDefined();
    expect(within(tile('waiting')).getByText('1')).toBeDefined();
    expect(screen.getByText('3 created in the last 30 days')).toBeDefined();
  });

  it('lists waiting admins and recent tenants, linking to the tenant page', async () => {
    renderPage();
    const initech = (await screen.findByText('Initech')).closest('tr');
    expect(within(initech).getByText('boss@initech.example')).toBeDefined();
    expect(within(initech).getByText('Expired')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Acme' }).getAttribute('href')).toBe('/admin/tenants/t-acme');
    expect(within(screen.getByText('Globex').closest('tr')).getByText('SUSPENDED')).toBeDefined();
  });

  it('Resend calls the service for that tenant, confirms and reloads', async () => {
    renderPage();
    await screen.findByText('Initech');
    fireEvent.click(screen.getByRole('button', { name: /resend invitation for initech/i }));
    await waitFor(() => expect(tenantService.resendAdminInvitation).toHaveBeenCalledWith('t-initech'));
    await waitFor(() => expect(successMsg).toHaveBeenCalled());
    await waitFor(() => expect(tenantService.getSummary).toHaveBeenCalledTimes(2));
  });

  it('a refused Resend shows the error', async () => {
    const err = { code: 'CONFLICT', message: 'nothing to resend' };
    tenantService.resendAdminInvitation.mockRejectedValueOnce(err);
    renderPage();
    await screen.findByText('Initech');
    fireEvent.click(screen.getByRole('button', { name: /resend invitation for initech/i }));
    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
    expect(successMsg).not.toHaveBeenCalled();
  });

  it('Create tenant goes to the create form', async () => {
    renderPage();
    await screen.findByText('Needs attention');
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants/new');
  });

  it('shows an empty state with Create tenant when there are no tenants', async () => {
    tenantService.getSummary.mockResolvedValue({
      total: 0,
      byStatus: { ACTIVE: 0, PAST_DUE: 0, SUSPENDED: 0, CANCELLED: 0 },
      createdLast30Days: 0,
      recent: [],
      waitingForAdmin: [],
    });
    renderPage();
    expect(await screen.findByText(/no tenants yet/i)).toBeDefined();
    expect(screen.queryByText('Needs attention')).toBeNull();
    expect(screen.getAllByRole('button', { name: /create tenant/i }).length).toBeGreaterThan(0);
  });

  it('a failed load shows the error with Retry', async () => {
    tenantService.getSummary.mockRejectedValueOnce({ message: 'boom' });
    renderPage();
    expect(await screen.findByText('Could not load the dashboard')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Needs attention')).toBeDefined();
  });
});
