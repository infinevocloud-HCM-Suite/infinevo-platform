import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { ShellBoundary } from './ShellBoundary.jsx';
import * as clientModule from '../shared/api/client.js';
import * as keycloakModule from './auth/keycloak.js';

describe('ShellBoundary component', () => {
  let capturedUnauthorizedHandler = null;
  let capturedSuspendedHandler = null;

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
