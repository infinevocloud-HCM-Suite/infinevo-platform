import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Header } from './Header.jsx';
import * as keycloakModule from './auth/keycloak.js';
import * as navigationModule from './navigation/useNavigation.js';
import { apiClient } from '../shared/api/client.js';
import impersonationReducer, { started } from '../core/admin/impersonationSlice.js';
import { impersonationService } from '../core/admin/impersonationService.js';

describe('Header component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('displays user name from keycloak.tokenParsed', () => {
    keycloakModule.keycloak.tokenParsed = {
      name: 'Alice Cooper',
      preferred_username: 'acooper',
    };

    render(<Header />);
    expect(screen.getByText('Alice Cooper')).toBeDefined();
  });

  it('falls back to preferred_username when name is missing', () => {
    keycloakModule.keycloak.tokenParsed = {
      preferred_username: 'birengit',
    };

    render(<Header />);
    expect(screen.getByText('birengit')).toBeDefined();
  });

  it('calls logout() when logout button is clicked', () => {
    keycloakModule.keycloak.tokenParsed = {
      name: 'Alice Cooper',
    };
    const logoutSpy = vi.spyOn(keycloakModule, 'logout').mockImplementation(() => {});

    render(<Header />);
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);

    expect(logoutSpy).toHaveBeenCalledTimes(1);
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

  it('refetches the feed when a session starts, and Stop closes it, clears it and refetches', async () => {
    const refetch = vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
    const close = vi.spyOn(impersonationService, 'close').mockResolvedValue(undefined);
    const store = configureStore({ reducer: { impersonation: impersonationReducer } });
    renderWithStore(store);
    expect(refetch).not.toHaveBeenCalled();

    store.dispatch(started(session));
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(1));

    fireEvent.click(await screen.findByRole('button', { name: /stop/i }));
    await waitFor(() => expect(close).toHaveBeenCalledWith('sess-1'));
    await waitFor(() => expect(store.getState().impersonation.session).toBeNull());
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(2));
    expect(screen.queryByTestId('impersonation-banner')).toBeNull();
  });

  it('wires the API client to the store: header from the session, cleared on IMPERSONATION_INVALID', async () => {
    vi.spyOn(navigationModule, 'fetchNavigationFeed').mockResolvedValue({});
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
