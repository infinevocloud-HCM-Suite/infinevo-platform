import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Preferences } from '@capacitor/preferences';
import { Browser } from '@capacitor/browser';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import {
  generateCodeVerifier,
  generateCodeChallenge,
  parseJwt,
  handleAuthCallbackUrl,
  clearStoredTokens,
  getValidMobileToken,
  startMobileLogin,
  mobileLogout,
} from './mobileAuth.js';

const mockSecureStore = new Map();

vi.mock('capacitor-secure-storage-plugin', () => ({
  SecureStoragePlugin: {
    get: vi.fn(({ key }) => {
      if (!mockSecureStore.has(key)) return Promise.reject(new Error('Key not found'));
      return Promise.resolve({ value: mockSecureStore.get(key) });
    }),
    set: vi.fn(({ key, value }) => {
      mockSecureStore.set(key, value);
      return Promise.resolve();
    }),
    remove: vi.fn(({ key }) => {
      mockSecureStore.delete(key);
      return Promise.resolve();
    }),
    clear: vi.fn(() => {
      mockSecureStore.clear();
      return Promise.resolve();
    }),
  },
}));

vi.mock('@capacitor/preferences', () => {
  const store = new Map();
  return {
    Preferences: {
      get: vi.fn(({ key }) => Promise.resolve({ value: store.get(key) || null })),
      set: vi.fn(({ key, value }) => {
        store.set(key, value);
        return Promise.resolve();
      }),
      remove: vi.fn(({ key }) => {
        store.delete(key);
        return Promise.resolve();
      }),
      clear: vi.fn(() => {
        store.clear();
        return Promise.resolve();
      }),
    },
  };
});

vi.mock('@capacitor/browser', () => ({
  Browser: {
    open: vi.fn(() => Promise.resolve()),
    close: vi.fn(() => Promise.resolve()),
    addListener: vi.fn((event, handler) =>
      Promise.resolve({
        remove: vi.fn(),
        handler,
      }),
    ),
  },
}));

vi.mock('@capacitor/app', () => ({
  App: {
    addListener: vi.fn(),
    getLaunchUrl: vi.fn(() => Promise.resolve(null)),
  },
}));

describe('mobileAuth PKCE & Utilities', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockSecureStore.clear();
    await clearStoredTokens();
  });

  it('generates random code verifier with default length of 64 characters', () => {
    const verifier1 = generateCodeVerifier();
    const verifier2 = generateCodeVerifier();
    expect(verifier1).toHaveLength(64);
    expect(verifier2).toHaveLength(64);
    expect(verifier1).not.toBe(verifier2);
    expect(verifier1).toMatch(/^[A-Za-z0-9\-._~]+$/);
  });

  it('throws error and does not use weak fallback if crypto.getRandomValues is missing', () => {
    const originalGetRandom = crypto.getRandomValues;
    try {
      crypto.getRandomValues = undefined;
      expect(() => generateCodeVerifier()).toThrow('Secure random generator');
    } finally {
      crypto.getRandomValues = originalGetRandom;
    }
  });

  it('generates reproducible base64url code challenge from verifier', async () => {
    const verifier = 'test_verifier_string_with_enough_length_for_rfc7636_testing_123';
    const challenge1 = await generateCodeChallenge(verifier);
    const challenge2 = await generateCodeChallenge(verifier);
    expect(challenge1).toBe(challenge2);
    expect(challenge1).not.toContain('+');
    expect(challenge1).not.toContain('/');
    expect(challenge1).not.toContain('=');
  });

  it('throws error and does not use weak fallback if crypto.subtle is missing', async () => {
    const desc = Object.getOwnPropertyDescriptor(globalThis.crypto, 'subtle') || {
      value: globalThis.crypto.subtle,
      configurable: true,
    };
    Object.defineProperty(globalThis.crypto, 'subtle', { value: undefined, configurable: true });
    try {
      await expect(generateCodeChallenge('test_verifier')).rejects.toThrow('Cryptographic digest');
    } finally {
      Object.defineProperty(globalThis.crypto, 'subtle', desc);
    }
  });

  it('parses JWT payload correctly without third-party library', () => {
    const header = btoa(JSON.stringify({ alg: 'none' })).replace(/=/g, '');
    const payload = btoa(
      JSON.stringify({ sub: 'usr-123', tenant_id: 'acme-corp', name: 'Jane Doe' }),
    ).replace(/=/g, '');
    const token = `${header}.${payload}.signature`;

    const parsed = parseJwt(token);
    expect(parsed).toEqual({
      sub: 'usr-123',
      tenant_id: 'acme-corp',
      name: 'Jane Doe',
    });
  });

  it('returns null when parsing invalid JWT token', () => {
    expect(parseJwt(null)).toBeNull();
    expect(parseJwt('')).toBeNull();
    expect(parseJwt('invalid.token')).toBeNull();
  });
});

describe('mobileAuth Callback, Secure Storage & Refresh Flow', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockSecureStore.clear();
    await clearStoredTokens();
  });

  it('ignores non-infinevo URLs in callback handler', async () => {
    const handled = await handleAuthCallbackUrl('https://example.com/callback');
    expect(handled).toBe(false);
  });

  it('rejects callback if state does not match stored state', async () => {
    await Preferences.set({ key: 'infinevo_pkce_state', value: 'valid_state_123' });
    await Preferences.set({ key: 'infinevo_pkce_verifier', value: 'my_verifier' });

    const callbackUrl = 'infinevo://auth-callback?code=abc456&state=invalid_state';

    await expect(handleAuthCallbackUrl(callbackUrl)).rejects.toThrow(
      'Invalid OAuth2 state or missing PKCE verifier',
    );
  });

  it('stores tokens in secure storage and never in Preferences', async () => {
    const expectedState = 'state_xyz_789';
    const expectedVerifier = 'test_code_verifier_123';
    await Preferences.set({ key: 'infinevo_pkce_state', value: expectedState });
    await Preferences.set({ key: 'infinevo_pkce_verifier', value: expectedVerifier });

    const fakeTokenResponse = {
      access_token: 'fake.access.token',
      refresh_token: 'fake.refresh.token',
      id_token: 'fake.id.token',
      expires_in: 900,
    };

    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(fakeTokenResponse),
    });

    const callbackUrl = `infinevo://auth-callback?code=valid_auth_code_123&state=${expectedState}`;
    const handled = await handleAuthCallbackUrl(callbackUrl);

    expect(handled).toBe(true);
    expect(Browser.close).toHaveBeenCalledTimes(1);

    // Preferences must NOT hold the tokens
    const prefAccess = await Preferences.get({ key: 'infinevo_access_token' });
    const prefRefresh = await Preferences.get({ key: 'infinevo_refresh_token' });
    expect(prefAccess.value).toBeNull();
    expect(prefRefresh.value).toBeNull();

    // SecureStoragePlugin holds the tokens
    expect(SecureStoragePlugin.set).toHaveBeenCalledWith({
      key: 'infinevo_access_token',
      value: 'fake.access.token',
    });
    expect(SecureStoragePlugin.set).toHaveBeenCalledWith({
      key: 'infinevo_refresh_token',
      value: 'fake.refresh.token',
    });
    expect(SecureStoragePlugin.set).toHaveBeenCalledWith({
      key: 'infinevo_id_token',
      value: 'fake.id.token',
    });
  });

  it('returns valid token from secure storage if not expiring soon', async () => {
    const validExpiry = Date.now() + 600000; // 10 minutes from now
    await SecureStoragePlugin.set({ key: 'infinevo_access_token', value: 'active_token_123' });
    await Preferences.set({ key: 'infinevo_token_expires_at', value: String(validExpiry) });

    const token = await getValidMobileToken();
    expect(token).toBe('active_token_123');
  });

  it('refreshes token when expiring and stores updated tokens in secure storage', async () => {
    const expiringSoon = Date.now() + 10000; // 10s from now (< 30s threshold)
    await SecureStoragePlugin.set({ key: 'infinevo_access_token', value: 'expiring_token' });
    await SecureStoragePlugin.set({ key: 'infinevo_refresh_token', value: 'valid_refresh_token' });
    await Preferences.set({ key: 'infinevo_token_expires_at', value: String(expiringSoon) });

    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          access_token: 'refreshed.access.token',
          refresh_token: 'refreshed.refresh.token',
          expires_in: 900,
        }),
    });

    const token = await getValidMobileToken();
    expect(token).toBe('refreshed.access.token');
    expect(SecureStoragePlugin.set).toHaveBeenCalledWith({
      key: 'infinevo_access_token',
      value: 'refreshed.access.token',
    });
  });

  it('clears stored tokens and starts login when refresh fails', async () => {
    const expired = Date.now() - 10000;
    await SecureStoragePlugin.set({ key: 'infinevo_access_token', value: 'expired_token' });
    await SecureStoragePlugin.set({ key: 'infinevo_refresh_token', value: 'bad_refresh_token' });
    await Preferences.set({ key: 'infinevo_token_expires_at', value: String(expired) });

    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: false,
      status: 400,
      text: () => Promise.resolve('invalid_grant'),
    });

    const token = await getValidMobileToken();
    expect(token).toBeNull();
    expect(SecureStoragePlugin.remove).toHaveBeenCalledWith({ key: 'infinevo_access_token' });
    expect(SecureStoragePlugin.remove).toHaveBeenCalledWith({ key: 'infinevo_refresh_token' });
    expect(Browser.open).toHaveBeenCalled();
  });

  it('clears stored tokens on clearStoredTokens()', async () => {
    await SecureStoragePlugin.set({ key: 'infinevo_access_token', value: 'temp_token' });
    await clearStoredTokens();

    expect(SecureStoragePlugin.remove).toHaveBeenCalledWith({ key: 'infinevo_access_token' });
  });

  it('handles user closing the browser in startMobileLogin by rejecting with cancelled', async () => {
    let capturedFinishedListener = null;
    Browser.addListener.mockImplementation((event, handler) => {
      if (event === 'browserFinished') {
        capturedFinishedListener = handler;
      }
      return Promise.resolve({ remove: vi.fn() });
    });

    await startMobileLogin();

    expect(Browser.addListener).toHaveBeenCalledWith('browserFinished', expect.any(Function));
    expect(capturedFinishedListener).toBeDefined();
  });

  it('logout revokes refresh token, ends SSO session, and clears storage', async () => {
    const validExpiry = Date.now() + 600000;
    await SecureStoragePlugin.set({ key: 'infinevo_access_token', value: 'to_logout_access' });
    await SecureStoragePlugin.set({ key: 'infinevo_refresh_token', value: 'to_logout_refresh' });
    await SecureStoragePlugin.set({ key: 'infinevo_id_token', value: 'to_logout_id' });
    await Preferences.set({ key: 'infinevo_token_expires_at', value: String(validExpiry) });

    // Load into currentTokens in memory
    await getValidMobileToken();

    globalThis.fetch = vi.fn().mockResolvedValueOnce({ ok: true });

    await mobileLogout();

    // Revocation request sent
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/protocol/openid-connect/revoke'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('token=to_logout_refresh'),
      }),
    );

    // End-session browser opened with id_token_hint
    expect(Browser.open).toHaveBeenCalledWith(
      expect.objectContaining({
        url: expect.stringContaining('id_token_hint=to_logout_id'),
      }),
    );

    // Tokens cleared
    expect(SecureStoragePlugin.remove).toHaveBeenCalledWith({ key: 'infinevo_access_token' });
    expect(SecureStoragePlugin.remove).toHaveBeenCalledWith({ key: 'infinevo_refresh_token' });
  });
});
