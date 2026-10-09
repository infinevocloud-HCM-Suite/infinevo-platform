import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { waitFor } from '@testing-library/react';
import { bootstrap } from './main.jsx';
import * as keycloakAuth from '@shell/auth/keycloak';
import * as apiClientModule from '@shared/api/client';

vi.mock('@shell/auth/keycloak', () => ({
  initAuth: vi.fn(),
  getValidToken: vi.fn(),
}));

vi.mock('@shared/api/client', () => ({
  setTokenProvider: vi.fn(),
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@shell/store', () => ({
  store: {
    getState: vi.fn().mockReturnValue({}),
    dispatch: vi.fn(),
    subscribe: vi.fn().mockReturnValue(() => {}),
  },
}));

vi.mock('@shell/AppShell', () => ({
  AppShell: () => <div data-testid="app-shell">App Shell</div>,
}));

describe('main.jsx bootstrap', () => {
  let container;

  beforeEach(() => {
    vi.clearAllMocks();
    container = document.createElement('div');
    container.id = 'root';
    document.body.appendChild(container);
  });

  afterEach(() => {
    if (container && container.parentNode) {
      container.parentNode.removeChild(container);
    }
  });

  it('at /invitations/accept, initAuth is NOT called and the public page renders', async () => {
    await bootstrap(container, '/invitations/accept');

    expect(keycloakAuth.initAuth).not.toHaveBeenCalled();
    expect(apiClientModule.setTokenProvider).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="app-shell"]')).toBeNull();
  });

  it('at /invitations/accept/ (trailing slash), the public page still renders without sign-in (D-62)', async () => {
    await bootstrap(container, '/invitations/accept/');

    expect(keycloakAuth.initAuth).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="app-shell"]')).toBeNull();
  });

  it('at /, initAuth IS called and the application shell renders', async () => {
    keycloakAuth.initAuth.mockResolvedValueOnce(true);

    await bootstrap(container, '/');

    expect(keycloakAuth.initAuth).toHaveBeenCalled();
    expect(apiClientModule.setTokenProvider).toHaveBeenCalledWith(keycloakAuth.getValidToken);
    await waitFor(() => {
      expect(container.querySelector('[data-testid="app-shell"]')).not.toBeNull();
    });
  });
});
