/**
 * The shell against the feed (W-12.3 §7): AppShell renders exactly the returned items, an empty
 * feed renders an empty shell rather than a default menu, and a tenant change refetches.
 *
 * The real AppShell, useNavigation and routes are rendered. Only the two edges are replaced:
 * the HTTP client (what the server returned) and the Keycloak adapter (when a token arrived).
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), defaults: { baseURL: '/api' } },
  setUnauthorizedHandler: vi.fn(),
  setTenantSuspendedHandler: vi.fn(),
}));

vi.mock('../auth/keycloak.js', () => {
  const listeners = new Set();
  return {
    keycloak: { tokenParsed: { tenant_id: 'acme' } },
    onTenantChange: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    fireTenantChange: (tenantId) => listeners.forEach((listener) => listener(tenantId, 'acme')),
    listenerCount: () => listeners.size,
  };
});

import { apiClient } from '../../shared/api/client.js';
import { fireTenantChange, listenerCount } from '../auth/keycloak.js';
import { AppShell } from '../AppShell.jsx';
import { resetNavigationFeed } from './useNavigation.js';

const ACME_FEED = {
  items: [
    {
      key: 'core.org',
      labelKey: 'nav.organisation',
      path: '/org/departments',
      children: [{ key: 'core.org.departments', labelKey: 'nav.departments', path: '/org/departments' }],
    },
    { key: 'core.roles', labelKey: 'nav.roles', path: '/roles' },
  ],
  actions: ['core.org.read', 'core.role.read'],
};

const GLOBEX_FEED = {
  items: [{ key: 'core.audit', labelKey: 'nav.audit', path: '/audit' }],
  actions: ['core.audit.read'],
};

function renderedMenuLabels(container) {
  return Array.from(container.querySelectorAll('.ant-menu-item, .ant-menu-submenu-title')).map((el) =>
    el.textContent.trim(),
  );
}

function renderShell() {
  return render(
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>,
  );
}

describe('AppShell over the navigation feed', () => {
  beforeEach(() => {
    resetNavigationFeed();
    apiClient.get.mockReset();
  });

  it('renders exactly the returned items, in order', async () => {
    apiClient.get.mockResolvedValueOnce({ data: ACME_FEED });
    const { container } = renderShell();

    await screen.findByText('nav.roles');

    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/navigation');
    expect(renderedMenuLabels(container)).toEqual(['nav.organisation', 'nav.roles']);
    expect(container.querySelector('.ant-skeleton')).toBeNull();
  });

  it('shows a skeleton while loading and no items behind it', async () => {
    let resolve;
    apiClient.get.mockReturnValueOnce(new Promise((r) => (resolve = r)));
    const { container } = renderShell();

    expect(container.querySelector('.ant-skeleton')).not.toBeNull();
    expect(renderedMenuLabels(container)).toEqual([]);

    await act(async () => resolve({ data: ACME_FEED }));
    await screen.findByText('nav.roles');
    expect(container.querySelector('.ant-skeleton')).toBeNull();
  });

  it('shows neither NoModules nor NotFound before the first fetch settles', async () => {
    let resolve;
    apiClient.get.mockReturnValueOnce(new Promise((r) => (resolve = r)));

    // Watch from before the first commit: the very first render, before the fetch effect has
    // run, is the one that used to read "not asked yet" as "empty feed".
    const host = document.body.appendChild(document.createElement('div'));
    const seen = [];
    const observer = new MutationObserver((records) => {
      // A node shown and then replaced survives only in removedNodes, text intact.
      records.forEach((record) =>
        [...record.addedNodes, ...record.removedNodes].forEach((node) => seen.push(node.textContent)),
      );
    });
    observer.observe(host, { childList: true, subtree: true });

    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>,
      { container: host },
    );
    // "/" is not in ACME_FEED, so NotFound is the right answer once the feed has arrived:
    // stop watching before it does.
    await act(async () => {});
    seen.push(host.textContent);
    observer.disconnect();

    expect(host.querySelector('.ant-layout-content .ant-skeleton')).not.toBeNull();
    expect(seen.some((text) => text.includes('No Modules Available'))).toBe(false);
    expect(seen.some((text) => text.includes('404'))).toBe(false);

    await act(async () => resolve({ data: ACME_FEED }));
    await screen.findByText('nav.roles');
  });

  it('renders an empty shell for an empty feed, not a default menu', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { items: [], actions: [] } });
    const { container } = renderShell();

    await waitFor(() => expect(container.querySelector('.ant-skeleton')).toBeNull());

    expect(renderedMenuLabels(container)).toEqual([]);
    expect(screen.getByText('Infinevo')).toBeTruthy();
  });

  it('renders an empty shell when the call fails', async () => {
    apiClient.get.mockRejectedValueOnce({ code: 'INTERNAL', status: 500 });
    const { container } = renderShell();

    await waitFor(() => expect(container.querySelector('.ant-skeleton')).toBeNull());

    expect(renderedMenuLabels(container)).toEqual([]);
  });

  it('refetches when a token for a different tenant arrives, and drops the old menu', async () => {
    apiClient.get.mockResolvedValueOnce({ data: ACME_FEED }).mockResolvedValueOnce({ data: GLOBEX_FEED });
    const { container } = renderShell();
    await screen.findByText('nav.roles');
    expect(listenerCount()).toBeGreaterThan(0);

    await act(async () => fireTenantChange('globex'));

    await screen.findByText('nav.audit');
    expect(apiClient.get).toHaveBeenCalledTimes(2);
    expect(renderedMenuLabels(container)).toEqual(['nav.audit']);
    expect(screen.queryByText('nav.roles')).toBeNull();
  });

  it('stops listening for tenant changes when unmounted', async () => {
    apiClient.get.mockResolvedValueOnce({ data: ACME_FEED });
    const { unmount } = renderShell();
    await screen.findByText('nav.roles');
    const before = listenerCount();

    unmount();

    expect(listenerCount()).toBe(before - 1);
  });
});
