import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Preferences } from '@capacitor/preferences';
import { Browser } from '@capacitor/browser';
import {
  generateCodeVerifier,
  generateCodeChallenge,
  parseJwt,
  handleAuthCallbackUrl,
  clearStoredTokens,
  getValidMobileToken,
} from './mobileAuth.js';

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

  it('generates reproducible base64url code challenge from verifier', async () => {
    const verifier = 'test_verifier_string_with_enough_length_for_rfc7636_testing_123';
    const challenge1 = await generateCodeChallenge(verifier);
    const challenge2 = await generateCodeChallenge(verifier);
    expect(challenge1).toBe(challenge2);
    expect(challenge1).not.toContain('+');
    expect(challenge1).not.toContain('/');
    expect(challenge1).not.toContain('=');
  });

  it('parses JWT payload correctly without third-party library', () => {
    // Header: {"alg":"none"}, Payload: {"sub":"usr-123","tenant_id":"acme-corp","name":"Jane Doe"}
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

describe('mobileAuth Callback & Storage Flow', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
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

  it('successfully handles callback, closes browser and exchanges code for tokens', async () => {
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
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/protocol/openid-connect/token'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('code_verifier=test_code_verifier_123'),
      }),
    );

    const storedAccessToken = await Preferences.get({ key: 'infinevo_access_token' });
    expect(storedAccessToken.value).toBe('fake.access.token');
  });

  it('returns valid token from storage if not expiring soon', async () => {
    const validExpiry = Date.now() + 600000; // 10 minutes from now
    await Preferences.set({ key: 'infinevo_access_token', value: 'active_token_123' });
    await Preferences.set({ key: 'infinevo_token_expires_at', value: String(validExpiry) });

    const token = await getValidMobileToken();
    expect(token).toBe('active_token_123');
  });

  it('clears stored tokens on clearStoredTokens()', async () => {
    await Preferences.set({ key: 'infinevo_access_token', value: 'temp_token' });
    await clearStoredTokens();

    const stored = await Preferences.get({ key: 'infinevo_access_token' });
    expect(stored.value).toBeNull();
  });
});
