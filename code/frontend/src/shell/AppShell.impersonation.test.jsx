import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { createStore } from './store.js';
import { AppShell } from './AppShell.jsx';
import * as navigationModule from './navigation/useNavigation.js';
import * as clientModule from '../shared/api/client.js';
import { IMPERSONATION_HEADER } from '../shared/api/client.js';

const globex = {
  tenant_id: 'tenant-globex',
  name: 'Globex',
  country_code: 'IN',
  timezone: 'Asia/Kolkata',
  status: 'ACTIVE',
  modules: ['HRMS'],
  created_at: '2026-09-01T00:00:00Z',
  current_period_end: null,
  user_count: 4,
};

const session = {
  sessionId: 'sess-1',
  tenantId: 'tenant-globex',
  tenantName: 'Globex',
  userLabel: 'ann@globex.test',
  expiresAt: '2026-10-03T12:30:00Z',
};

/** The customer's feed while acting: no core.tenants item. */
const customerFeed = [{ key: 'employees', labelKey: 'Employees', path: '/employees' }];

function renderShell(path, { withSession, items = customerFeed }) {
  vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({ items, loading: false });
  const store = createStore();
  // By action type, the way the shell reaches the slice: by name, not by import.
  if (withSession) store.dispatch({ type: 'impersonation/started', payload: session });
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <AppShell />
      </MemoryRouter>
    </Provider>,
  );
  return store;
}

/**
 * The real services and the real request interceptor run; only the network is replaced. The
 * adapter sees the headers exactly as they would leave the browser, so the tests below prove
 * which calls carry `X-Impersonation` rather than trusting a mocked service.
 */
const originalAdapter = clientModule.apiClient.defaults.adapter;
let adapter;

function reply(config) {
  let data = { content: [], totalElements: 0 };
  if (config.url === '/v1/tenants') data = [globex];
  else if (config.url === '/v1/tenants/tenant-globex') data = globex;
  return Promise.resolve({ data, status: 200, statusText: 'OK', headers: {}, config });
}

/** Every request the adapter saw for `url`, as `{ method, impersonation }`. */
function sent(url) {
  return adapter.mock.calls
    .map(([config]) => config)
    .filter((config) => config.url === url)
    .map((config) => ({
      method: config.method,
      impersonation: config.headers?.[IMPERSONATION_HEADER] ?? null,
    }));
}

describe('AppShell admin routes during impersonation (W-65.3)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    adapter = vi.fn(reply);
    clientModule.apiClient.defaults.adapter = adapter;
    vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue(undefined);
  });

  afterEach(() => {
    clientModule.apiClient.defaults.adapter = originalAdapter;
  });

  it('keeps /admin/tenants mounted while a session is live and the feed lacks core.tenants', async () => {
    renderShell('/admin/tenants', { withSession: true });
    expect(await screen.findByRole('heading', { name: 'Tenants' }, { timeout: 5000 })).toBeDefined();
    expect(screen.queryByText('404')).toBeNull();
    // The menu still follows the feed: no tenants item appears.
    expect(screen.queryByRole('link', { name: /tenants/i })).toBeNull();
    // The list is a platform endpoint: it goes out without the session header.
    await waitFor(() => expect(sent('/v1/tenants')).toEqual([{ method: 'get', impersonation: null }]));
  });

  it('keeps /admin/tenants/new mounted while a session is live', async () => {
    renderShell('/admin/tenants/new', { withSession: true });
    expect(await screen.findByRole('heading', { name: /tenant/i }, { timeout: 5000 })).toBeDefined();
    expect(screen.queryByText('404')).toBeNull();
  });

  it('mounts /admin/tenants/:id and enables the audit tab when the session tenant matches', async () => {
    renderShell('/admin/tenants/tenant-globex', { withSession: true });
    const auditTab = await screen.findByRole('tab', { name: 'Audit' }, { timeout: 5000 });
    expect(auditTab.getAttribute('aria-disabled')).not.toBe('true');
    expect(screen.queryByText('404')).toBeNull();
    // The overview is a platform endpoint: no session header, or the server checks the target.
    expect(sent('/v1/tenants/tenant-globex')).toEqual([{ method: 'get', impersonation: null }]);

    // The audit trail reads the acted-as tenant, so it must carry the header.
    fireEvent.click(auditTab);
    await waitFor(() =>
      expect(sent('/v1/audit')).toContainEqual({ method: 'get', impersonation: 'sess-1' }),
    );
  });

  it('keeps the routes mounted even when the customer feed is empty', async () => {
    renderShell('/admin/tenants', { withSession: true, items: [] });
    expect(await screen.findByRole('heading', { name: 'Tenants' }, { timeout: 5000 })).toBeDefined();
    expect(screen.queryByText('No Modules Available')).toBeNull();
  });

  it.each(['/admin/tenants', '/admin/tenants/new', '/admin/tenants/tenant-globex'])(
    'does not mount %s with no session and no core.tenants in the feed',
    (path) => {
      renderShell(path, { withSession: false });
      expect(screen.getByText('404')).toBeDefined();
      expect(sent('/v1/tenants')).toEqual([]);
      expect(sent('/v1/tenants/tenant-globex')).toEqual([]);
    },
  );
});
