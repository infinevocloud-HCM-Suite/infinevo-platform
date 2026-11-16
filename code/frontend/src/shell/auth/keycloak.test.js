/**
 * The tenant-change signal (W-12.3 §5): the real adapter module, with tokens arriving through
 * the two Keycloak callbacks it owns. No network - `init` is never called.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Capacitor } from '@capacitor/core';
import { keycloak, onTenantChange, logout, initAuth, getValidToken } from './keycloak.js';
import * as mobileAuth from '../../mobile/mobileAuth.js';

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

describe('native Capacitor mobile delegation', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('delegates initAuth to initMobileAuth when running on native container', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    const initMobileSpy = vi.spyOn(mobileAuth, 'initMobileAuth').mockResolvedValue({
      accessToken: 'mobile-token-abc',
      tokenParsed: { tenant_id: 'tenant-mob' },
    });
    vi.spyOn(mobileAuth, 'isMobileAuthenticated').mockReturnValue(true);
    vi.spyOn(mobileAuth, 'getMobileTokenParsed').mockReturnValue({ tenant_id: 'tenant-mob' });

    const result = await initAuth();
    expect(result).toBe(true);
    expect(initMobileSpy).toHaveBeenCalledTimes(1);
    expect(keycloak.authenticated).toBe(true);
    expect(keycloak.tokenParsed).toEqual({ tenant_id: 'tenant-mob' });
  });

  it('delegates getValidToken to getValidMobileToken when on native platform', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    const getMobileTokenSpy = vi
      .spyOn(mobileAuth, 'getValidMobileToken')
      .mockResolvedValue('valid-mobile-token-123');
    vi.spyOn(mobileAuth, 'getMobileTokenParsed').mockReturnValue({ sub: 'user-mob' });

    const token = await getValidToken();
    expect(token).toBe('valid-mobile-token-123');
    expect(getMobileTokenSpy).toHaveBeenCalledTimes(1);
    expect(keycloak.token).toBe('valid-mobile-token-123');
  });

  it('delegates logout to mobileLogout when on native platform', () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    const mobileLogoutSpy = vi.spyOn(mobileAuth, 'mobileLogout').mockImplementation(() => {});

    logout({ redirectUri: 'custom' });
    expect(mobileLogoutSpy).toHaveBeenCalledTimes(1);
  });
});
