import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Header, initialsOf } from './Header.jsx';
import * as keycloakModule from './auth/keycloak.js';
import * as meModule from './auth/useMe.js';
import * as navigationModule from './navigation/useNavigation.js';
import { apiClient } from '../shared/api/client.js';
import impersonationReducer, { started } from '../core/admin/impersonationSlice.js';
import { impersonationService } from '../core/admin/impersonationService.js';

/** The `/me` answer the header reads; the hook is stubbed so no request leaves the test. */
function me(overrides = {}) {
  return vi.spyOn(meModule, 'useMe').mockReturnValue({
    displayName: '',
    roles: [],
    email: null,
    loading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  });
}

describe('Header component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    keycloakModule.keycloak.tokenParsed = {};
  });

  it('shows the display name from /me', () => {
    me({ displayName: 'Gita Rao' });
    keycloakModule.keycloak.tokenParsed = { name: 'Token Name' };

    render(<Header />);
    expect(screen.getByText('Gita Rao')).toBeDefined();
    expect(screen.queryByText('Token Name')).toBeNull();
  });

  it('falls back to the token name, then preferred_username, until /me answers', () => {
    me();
    keycloakModule.keycloak.tokenParsed = { name: 'Alice Cooper', preferred_username: 'acooper' };
    const { unmount } = render(<Header />);
    expect(screen.getByText('Alice Cooper')).toBeDefined();
    unmount();

    keycloakModule.keycloak.tokenParsed = { preferred_username: 'birengit' };
    render(<Header />);
    expect(screen.getByText('birengit')).toBeDefined();
  });

  it('the user menu offers Sign out, which calls logout()', async () => {
    me({ displayName: 'Alice Cooper' });
    const logoutSpy = vi.spyOn(keycloakModule, 'logout').mockImplementation(() => {});

    render(<Header />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    fireEvent.click(await screen.findByText('Sign out'));

    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });

  it('D-75: the user menu offers My profile when the shell passes onMyProfile, and it opens the profile', async () => {
    me({ displayName: 'Esha Employee' });
    const onMyProfile = vi.fn();

    render(<Header onMyProfile={onMyProfile} />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    fireEvent.click(await screen.findByText('My profile'));

    expect(onMyProfile).toHaveBeenCalledTimes(1);
  });

  it('D-75: no My profile when the shell passes no onMyProfile', async () => {
    me({ displayName: 'Harish HR' });

    render(<Header />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));

    expect(await screen.findByText('Sign out')).toBeDefined();
    expect(screen.queryByText('My profile')).toBeNull();
  });
});

describe('Header branding (W-73.1 §7)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    keycloakModule.keycloak.tokenParsed = {};
    me();
  });

  it('initialsOf takes the first letter of up to two words', () => {
    expect(initialsOf('Acme Ltd')).toBe('AL');
    expect(initialsOf('Infinevo')).toBe('I');
    expect(initialsOf('  Globex   Industries  Inc ')).toBe('GI');
    expect(initialsOf('')).toBe('');
    expect(initialsOf(undefined)).toBe('');
  });

  it('shows the initials circle when there is no logo, and the name - never an empty box', () => {
    render(<Header tenantName="Acme Ltd" />);
    expect(screen.getByTestId('tenant-initials').textContent).toBe('AL');
    expect(screen.queryByTestId('tenant-logo')).toBeNull();
    expect(screen.getByTestId('tenant-display').textContent).toContain('Acme Ltd');
  });

  it('shows the logo instead of the initials when the feed carries a link', () => {
    render(<Header tenantName="Acme Ltd" tenantLogoUrl="/api/v1/documents/download?t=x" />);
    const logo = screen.getByTestId('tenant-logo');
    expect(logo.getAttribute('src')).toBe('/api/v1/documents/download?t=x');
    expect(logo.getAttribute('alt')).toBe('Acme Ltd logo');
    expect(screen.queryByTestId('tenant-initials')).toBeNull();
  });

  it('a logo that fails to load refetches the feed once and falls back to the initials', async () => {
    const refetch = vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    render(<Header tenantName="Acme Ltd" tenantLogoUrl="/api/v1/documents/download?t=expired" />);

    fireEvent.error(screen.getByTestId('tenant-logo'));

    await waitFor(() => expect(screen.getByTestId('tenant-initials').textContent).toBe('AL'));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('a logo that keeps failing refetches once, not once per new link, and the initials stay', async () => {
    const refetch = vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    const { rerender } = render(<Header tenantName="Acme Ltd" tenantLogoUrl="/d?t=first" />);

    fireEvent.error(screen.getByTestId('tenant-logo'));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(1));

    // The refetch signed a new link to the same broken file.
    rerender(<Header tenantName="Acme Ltd" tenantLogoUrl="/d?t=second" />);
    fireEvent.error(await screen.findByTestId('tenant-logo'));

    await waitFor(() => expect(screen.getByTestId('tenant-initials').textContent).toBe('AL'));
    rerender(<Header tenantName="Acme Ltd" tenantLogoUrl="/d?t=third" />);
    fireEvent.error(await screen.findByTestId('tenant-logo'));
    await waitFor(() => expect(screen.getByTestId('tenant-initials').textContent).toBe('AL'));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('another tenant gets its own one retry', async () => {
    const refetch = vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    const { rerender } = render(<Header tenantName="Acme Ltd" tenantLogoUrl="/d?t=acme" />);
    fireEvent.error(screen.getByTestId('tenant-logo'));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(1));

    rerender(<Header tenantName="Globex" tenantLogoUrl="/d?t=globex" />);
    fireEvent.error(await screen.findByTestId('tenant-logo'));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(2));
  });

  it('hides the tagline when it is null and shows it under the name when set', () => {
    const { unmount } = render(<Header tenantName="Acme Ltd" tagline={null} />);
    expect(screen.queryByTestId('tenant-tagline')).toBeNull();
    unmount();

    render(<Header tenantName="Acme Ltd" tagline="People first" />);
    expect(screen.getByTestId('tenant-tagline').textContent).toBe('People first');
  });

  it('shows one chip per role, as words', () => {
    me({ displayName: 'Gita Rao', roles: ['hr', 'payroll-officer', 'shift_lead'] });
    render(<Header tenantName="Acme Ltd" />);

    const chips = Array.from(screen.getByTestId('role-chips').querySelectorAll('.ant-tag')).map((el) =>
      el.textContent.trim(),
    );
    expect(chips).toEqual(['HR', 'Payroll officer', 'Shift lead']);
  });

  it('shows no chip row for a user with no roles', () => {
    me({ displayName: 'Gita Rao', roles: [] });
    render(<Header tenantName="Acme Ltd" />);
    expect(screen.queryByTestId('role-chips')).toBeNull();
  });
});

describe('Header impersonation banner (W-65.3)', () => {
  const session = {
    sessionId: 'sess-1',
    tenantId: 'tenant-globex',
    tenantName: 'Globex',
    userLabel: 'admin@globex.local',
    expiresAt: '2026-10-03T10:30:00Z',
  };

  function renderWithStore(store) {
    return render(
      <Provider store={store}>
        <Header />
      </Provider>,
    );
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    keycloakModule.keycloak.tokenParsed = { name: 'Staff' };
    me();
  });

  it('shows no banner without a session', () => {
    const store = configureStore({ reducer: { impersonation: impersonationReducer } });
    renderWithStore(store);
    expect(screen.queryByTestId('impersonation-banner')).toBeNull();
  });

  it('shows "Acting as {user} in {tenant} until {time}" while a session is live', () => {
    const store = configureStore({ reducer: { impersonation: impersonationReducer } });
    store.dispatch(started(session));
    renderWithStore(store);
    const banner = screen.getByTestId('impersonation-banner');
    expect(banner.textContent).toMatch(/Acting as admin@globex\.local in Globex until /);
  });

  it('refetches the feed and /me when a session starts, and Stop closes it, clears it and refetches', async () => {
    const refetch = vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    const refetchMe = vi.spyOn(meModule, 'fetchMe').mockResolvedValue({});
    const close = vi.spyOn(impersonationService, 'close').mockResolvedValue(undefined);
    const store = configureStore({ reducer: { impersonation: impersonationReducer } });
    renderWithStore(store);
    expect(refetch).not.toHaveBeenCalled();
    expect(refetchMe).not.toHaveBeenCalled();

    store.dispatch(started(session));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(1));
    expect(refetchMe).toHaveBeenCalledTimes(1);

    fireEvent.click(await screen.findByRole('button', { name: /stop/i }));
    await waitFor(() => expect(close).toHaveBeenCalledWith('sess-1'));
    await waitFor(() => expect(store.getState().impersonation.session).toBeNull());
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(2));
    expect(refetchMe).toHaveBeenCalledTimes(2);
    expect(screen.queryByTestId('impersonation-banner')).toBeNull();
  });

  it('wires the API client to the store: header from the session, cleared on IMPERSONATION_INVALID', async () => {
    vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    vi.spyOn(meModule, 'fetchMe').mockResolvedValue({});
    const store = configureStore({ reducer: { impersonation: impersonationReducer } });
    store.dispatch(started(session));
    renderWithStore(store);

    const request = apiClient.interceptors.request.handlers[0];
    const cfg = await request.fulfilled({ headers: {} });
    expect(cfg.headers['X-Impersonation']).toBe('sess-1');

    const response = apiClient.interceptors.response.handlers[0];
    await expect(
      response.rejected({ response: { status: 403, data: { code: 'IMPERSONATION_INVALID' } } }),
    ).rejects.toMatchObject({ isImpersonationInvalid: true });
    expect(store.getState().impersonation.session).toBeNull();
  });
});
