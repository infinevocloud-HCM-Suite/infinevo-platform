/**
 * The tenant-change signal (W-12.3 §5): the real adapter module, with tokens arriving through
 * the two Keycloak callbacks it owns. No network - `init` is never called.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { keycloak, onTenantChange, logout } from './keycloak.js';

function tokenFor(tenantId) {
  keycloak.tokenParsed = tenantId === undefined ? undefined : { sub: 'u1', tenant_id: tenantId };
}

describe('onTenantChange', () => {
  let stop;
  const listener = vi.fn();

  beforeEach(() => {
    listener.mockReset();
    if (stop) stop();
    stop = onTenantChange(listener);
  });

  it('does not fire for the first token, nor for a refresh of the same tenant', () => {
    tokenFor('acme');
    keycloak.onAuthSuccess();
    keycloak.onAuthRefreshSuccess();

    expect(listener).not.toHaveBeenCalled();
  });

  it('fires once with the new and previous tenant when a refreshed token changes tenant', () => {
    tokenFor('acme');
    keycloak.onAuthRefreshSuccess();

    tokenFor('globex');
    keycloak.onAuthRefreshSuccess();

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith('globex', 'acme');
  });

  it('a removed listener is not called', () => {
    tokenFor('globex');
    keycloak.onAuthRefreshSuccess();
    stop();
    stop = null;

    tokenFor('initech');
    keycloak.onAuthRefreshSuccess();

    expect(listener).not.toHaveBeenCalled();
  });
});

describe('logout', () => {
  it('calls keycloak.logout with redirectUri set to app root by default (D-108)', () => {
    const logoutSpy = vi.spyOn(keycloak, 'logout').mockImplementation(() => Promise.resolve());
    logout();
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(logoutSpy).toHaveBeenCalledWith({
      redirectUri: `${window.location.origin}/`,
    });
  });

  it('allows overriding redirectUri when options are provided', () => {
    const logoutSpy = vi.spyOn(keycloak, 'logout').mockImplementation(() => Promise.resolve());
    logout({ redirectUri: 'https://auth.infinevo.com/login' });
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(logoutSpy).toHaveBeenCalledWith({
      redirectUri: 'https://auth.infinevo.com/login',
    });
  });
});
