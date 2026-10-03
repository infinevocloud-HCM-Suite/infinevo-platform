import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { TenantDetail, INVITATION_FORM_PATH } from './TenantDetail.jsx';
import impersonationReducer, { started } from './impersonationSlice.js';
import { tenantService } from './tenantService.js';
import { impersonationService } from './impersonationService.js';
import { auditService } from './auditService.js';
import * as screens from '@shell/screens';

vi.mock('./tenantService.js', () => ({
  tenantService: { get: vi.fn(), setModules: vi.fn(), setStatus: vi.fn() },
}));
vi.mock('./impersonationService.js', () => ({
  impersonationService: { open: vi.fn(), close: vi.fn() },
}));
vi.mock('./auditService.js', () => ({
  auditService: { search: vi.fn() },
}));

const globex = {
  tenant_id: 'tenant-globex',
  name: 'Globex',
  country_code: 'IN',
  timezone: 'Asia/Kolkata',
  status: 'ACTIVE',
  modules: ['HRMS', 'PAYROLL'],
  created_at: '2026-09-01T00:00:00Z',
  current_period_end: null,
  user_count: 4,
};

function setup({ overview = globex, session = null } = {}) {
  tenantService.get.mockResolvedValue(overview);
  const store = configureStore({ reducer: { impersonation: impersonationReducer } });
  if (session) store.dispatch(started(session));
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[`/admin/tenants/${overview.tenant_id}`]}>
        <Routes>
          <Route path="/admin/tenants/:id" element={<TenantDetail />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  return store;
}

function confirmDialog() {
  return document.querySelector('.ant-modal-confirm');
}

function confirmOk(label) {
  fireEvent.click(within(confirmDialog()).getByRole('button', { name: label }));
}

describe('TenantDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(screens, 'useCan').mockImplementation((code) => code === 'core.tenant.impersonate');
    tenantService.setModules.mockResolvedValue({});
    tenantService.setStatus.mockResolvedValue({});
    auditService.search.mockResolvedValue({ content: [], totalElements: 0 });
    document.body.querySelectorAll('.ant-modal-root').forEach((n) => n.remove());
  });

  it('asks before revoking a module, and only then calls setModules', async () => {
    setup();
    const hrms = await screen.findByRole('switch', { name: 'Module HRMS' });
    fireEvent.click(hrms);

    await waitFor(() => expect(confirmDialog()).not.toBeNull());
    expect(confirmDialog().textContent).toMatch(/Revoke HRMS from Globex/);
    expect(tenantService.setModules).not.toHaveBeenCalled();

    confirmOk('Revoke');
    await waitFor(() =>
      expect(tenantService.setModules).toHaveBeenCalledWith('tenant-globex', ['PAYROLL']),
    );
  });

  it('grants a module without asking', async () => {
    setup({ overview: { ...globex, modules: ['PAYROLL'] } });
    fireEvent.click(await screen.findByRole('switch', { name: 'Module HRMS' }));
    await waitFor(() =>
      expect(tenantService.setModules).toHaveBeenCalledWith('tenant-globex', ['PAYROLL', 'HRMS']),
    );
    expect(confirmDialog()).toBeNull();
  });

  it('asks before suspending, and only then calls setStatus', async () => {
    setup();
    const select = await screen.findByRole('combobox', { name: 'Subscription status' });
    fireEvent.mouseDown(select);
    fireEvent.click(await screen.findByTitle('SUSPENDED'));

    await waitFor(() => expect(confirmDialog()).not.toBeNull());
    expect(tenantService.setStatus).not.toHaveBeenCalled();

    confirmOk('Suspend');
    await waitFor(() =>
      expect(tenantService.setStatus).toHaveBeenCalledWith('tenant-globex', 'SUSPENDED'),
    );
  });

  it('"Act as" opens a session by email and reason and dispatches started', async () => {
    impersonationService.open.mockResolvedValue({
      sessionId: 'sess-1',
      tenantId: 'tenant-globex',
      userAccountId: 'ua-1',
      userEmail: 'admin@globex.local',
      expiresAt: '2026-10-03T10:30:00Z',
    });
    const store = setup();

    fireEvent.change(await screen.findByLabelText("User's email"), {
      target: { value: 'admin@globex.local' },
    });
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'ticket 42' } });
    fireEvent.click(screen.getByRole('button', { name: 'Act as' }));

    await waitFor(() =>
      expect(impersonationService.open).toHaveBeenCalledWith(
        'tenant-globex',
        { email: 'admin@globex.local' },
        'ticket 42',
      ),
    );
    await waitFor(() =>
      expect(store.getState().impersonation.session).toEqual({
        sessionId: 'sess-1',
        tenantId: 'tenant-globex',
        tenantName: 'Globex',
        userLabel: 'admin@globex.local',
        expiresAt: '2026-10-03T10:30:00Z',
      }),
    );
  });

  it('with zero user accounts hides the email, reads "Set up as admin", then links to the invitation form', async () => {
    impersonationService.open.mockResolvedValue({
      sessionId: 'sess-2',
      tenantId: 'tenant-globex',
      userAccountId: null,
      userEmail: null,
      expiresAt: '2026-10-03T10:30:00Z',
    });
    const store = setup({ overview: { ...globex, user_count: 0 } });

    await screen.findByRole('button', { name: 'Set up as admin' });
    expect(screen.queryByLabelText("User's email")).toBeNull();

    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'onboarding' } });
    fireEvent.click(screen.getByRole('button', { name: 'Set up as admin' }));

    await waitFor(() =>
      expect(impersonationService.open).toHaveBeenCalledWith('tenant-globex', null, 'onboarding'),
    );
    const link = await screen.findByRole('link', { name: /first admin/i });
    expect(link.getAttribute('href')).toBe(INVITATION_FORM_PATH);
    expect(store.getState().impersonation.session.sessionId).toBe('sess-2');
  });

  it('disables the audit tab, with the hint, while no session for this tenant is live', async () => {
    setup();
    const auditTab = await screen.findByRole('tab', { name: 'Audit' });
    expect(auditTab.getAttribute('aria-disabled')).toBe('true');
    expect(screen.getByText(/Audit opens while you act in this tenant/)).toBeDefined();
    expect(auditService.search).not.toHaveBeenCalled();
  });

  it('enables the audit tab and reads the trail once a session for this tenant is live', async () => {
    setup({
      session: {
        sessionId: 'sess-1',
        tenantId: 'tenant-globex',
        tenantName: 'Globex',
        userLabel: 'admin@globex.local',
        expiresAt: '2026-10-03T10:30:00Z',
      },
    });
    const auditTab = await screen.findByRole('tab', { name: 'Audit' });
    expect(auditTab.getAttribute('aria-disabled')).toBe('false');
    fireEvent.click(auditTab);
    await waitFor(() => expect(auditService.search).toHaveBeenCalledWith({ page: 0, size: 20 }));
  });

  it("keeps the acting message, the invitation link and the audit tab once the feed is the target's", async () => {
    // While acting, the feed is the target user's and never carries core.tenant.impersonate.
    screens.useCan.mockImplementation(() => false);
    setup({
      overview: { ...globex, user_count: 0 },
      session: {
        sessionId: 'sess-2',
        tenantId: 'tenant-globex',
        tenantName: 'Globex',
        userLabel: 'setup admin (no user yet)',
        expiresAt: '2026-10-03T10:30:00Z',
      },
    });

    expect(await screen.findByText(/You are acting as setup admin \(no user yet\) in Globex/)).toBeDefined();
    const link = screen.getByRole('link', { name: /first admin/i });
    expect(link.getAttribute('href')).toBe(INVITATION_FORM_PATH);
    expect(screen.queryByLabelText('Reason')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Set up as admin' })).toBeNull();
    const auditTab = screen.getByRole('tab', { name: 'Audit' });
    expect(auditTab.getAttribute('aria-disabled')).toBe('false');
  });

  it('hides the act-as card without the permission and with no session here', async () => {
    screens.useCan.mockImplementation(() => false);
    setup();
    await screen.findByRole('tab', { name: 'Audit' });
    expect(screen.queryByLabelText('Reason')).toBeNull();
    expect(screen.queryByText(/You are acting as/)).toBeNull();
  });

  it('shows the platform tenant with its tag and no switches, status or act-as', async () => {
    setup({ overview: { ...globex, tenant_id: '00000000-0000-0000-0000-000000000001', name: 'Infinevo', modules: [] } });
    const title = await screen.findAllByText('Infinevo');
    expect(title.length).toBeGreaterThan(0);
    expect(screen.getByText('platform')).toBeDefined();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.queryByRole('combobox', { name: 'Subscription status' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Act as' })).toBeNull();
    expect(within(document.body).queryByText('Act as')).toBeNull();
  });
});
