import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { StrictMode } from 'react';
import { render, screen, act } from '@testing-library/react';
import { ShellBoundary } from './ShellBoundary.jsx';
import * as clientModule from '../shared/api/client.js';
import * as keycloakModule from './auth/keycloak.js';

describe('ShellBoundary component', () => {
  let capturedUnauthorizedHandler = null;
  let capturedSuspendedHandler = null;

  afterEach(() => {
    window.history.replaceState(null, '', '/');
  });

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(clientModule, 'setUnauthorizedHandler').mockImplementation((fn) => {
      capturedUnauthorizedHandler = fn;
    });
    vi.spyOn(clientModule, 'setTenantSuspendedHandler').mockImplementation((fn) => {
      capturedSuspendedHandler = fn;
    });
  });

  it('renders children normally when not suspended', () => {
    render(
      <ShellBoundary>
        <div data-testid="child-content">Normal Content</div>
      </ShellBoundary>,
    );
    expect(screen.getByTestId('child-content')).toBeDefined();
    expect(screen.getByText('Normal Content')).toBeDefined();
  });

  it('replaces content with Suspended screen when TENANT_SUSPENDED occurs', () => {
    render(
      <ShellBoundary>
        <div data-testid="child-content">Normal Content</div>
      </ShellBoundary>,
    );

    expect(screen.getByText('Normal Content')).toBeDefined();

    act(() => {
      if (capturedSuspendedHandler) {
        capturedSuspendedHandler();
      }
    });

    expect(screen.queryByText('Normal Content')).toBeNull();
    expect(screen.getByText('Subscription Suspended')).toBeDefined();
  });

  it('calls keycloak.login() when UNAUTHORIZED event occurs', () => {
    const loginSpy = vi.spyOn(keycloakModule.keycloak, 'login').mockImplementation(() => {});

    render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    act(() => {
      if (capturedUnauthorizedHandler) {
        capturedUnauthorizedHandler();
      }
    });

    expect(loginSpy).toHaveBeenCalledTimes(1);
    expect(loginSpy.mock.calls[0][0].redirectUri).toContain('relogin=1');
  });

  it('TENANT_NOT_BOUND shows the access-problem screen and never signs in again (D-63)', () => {
    const loginSpy = vi.spyOn(keycloakModule.keycloak, 'login').mockImplementation(() => {});

    render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    act(() => {
      capturedUnauthorizedHandler('TENANT_NOT_BOUND');
    });

    expect(loginSpy).not.toHaveBeenCalled();
    expect(screen.queryByText('Content')).toBeNull();
    expect(screen.getByText('Your account is not linked to one company')).toBeDefined();
    expect(document.getElementById('btn-access-problem-logout')).not.toBeNull();
  });

  it('a 401 right after returning from a re-login stops on a screen instead of looping (D-63)', () => {
    const loginSpy = vi.spyOn(keycloakModule.keycloak, 'login').mockImplementation(() => {});
    window.history.replaceState(null, '', '/employees?relogin=1');

    render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    // The flag is read once and dropped from the address bar.
    expect(window.location.search).toBe('');

    act(() => {
      capturedUnauthorizedHandler('UNAUTHENTICATED');
    });

    expect(loginSpy).not.toHaveBeenCalled();
    expect(screen.getByText('We could not sign you in')).toBeDefined();
  });

  it('TENANT_NOT_BOUND wins over the re-login flag: the no-company screen, not the sign-in one (D-63)', () => {
    window.history.replaceState(null, '', '/employees?relogin=1');
    render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    act(() => {
      capturedUnauthorizedHandler('TENANT_NOT_BOUND');
    });

    expect(screen.getByText('Your account is not linked to one company')).toBeDefined();
  });

  it('the flag survives StrictMode double effects (D-63)', () => {
    const loginSpy = vi.spyOn(keycloakModule.keycloak, 'login').mockImplementation(() => {});
    window.history.replaceState(null, '', '/employees?relogin=1');
    render(
      <StrictMode>
        <ShellBoundary>
          <div>Content</div>
        </ShellBoundary>
      </StrictMode>,
    );

    act(() => {
      capturedUnauthorizedHandler('UNAUTHENTICATED');
    });

    expect(loginSpy).not.toHaveBeenCalled();
    expect(screen.getByText('We could not sign you in')).toBeDefined();
  });

  it('after a successful reply, a later 401 in the same tab re-logs in once more (D-63)', async () => {
    const loginSpy = vi.spyOn(keycloakModule.keycloak, 'login').mockImplementation(() => {});
    window.history.replaceState(null, '', '/employees?relogin=1');
    render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    // Any 2xx through the client proves the sign-in worked.
    const fulfilled = clientModule.apiClient.interceptors.response.handlers
      .filter(Boolean)
      .map((h) => h.fulfilled);
    act(() => {
      fulfilled.forEach((fn) => fn && fn({ status: 200, data: {} }));
    });

    act(() => {
      capturedUnauthorizedHandler('UNAUTHENTICATED');
    });

    expect(loginSpy).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('We could not sign you in')).toBeNull();
  });

  it('clears suspended state on the next feed fetch', async () => {
    const { fetchNavigationFeed } = await import('./navigation/useNavigation.js');
    const navModule = await import('./navigation/navigationService.js');
    vi.spyOn(navModule, 'fetchNavigation').mockResolvedValue({ data: { items: [], actions: [] } });

    render(
      <ShellBoundary>
        <div data-testid="child-content">Normal Content</div>
      </ShellBoundary>,
    );

    act(() => {
      if (capturedSuspendedHandler) {
        capturedSuspendedHandler();
      }
    });

    expect(screen.queryByText('Normal Content')).toBeNull();
    expect(screen.getByText('Subscription Suspended')).toBeDefined();

    await act(async () => {
      await fetchNavigationFeed();
    });

    expect(screen.getByText('Normal Content')).toBeDefined();
    expect(screen.queryByText('Subscription Suspended')).toBeNull();
  });

  it('unregisters handlers on unmount', () => {
    const { unmount } = render(
      <ShellBoundary>
        <div>Content</div>
      </ShellBoundary>,
    );

    unmount();

    expect(clientModule.setUnauthorizedHandler).toHaveBeenCalledWith(null);
    expect(clientModule.setTenantSuspendedHandler).toHaveBeenCalledWith(null);
  });
});
