import { Capacitor } from '@capacitor/core';
import { Browser } from '@capacitor/browser';
import { App } from '@capacitor/app';
import { Preferences } from '@capacitor/preferences';
import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';
import { config } from '../shared/config.js';
import { mobilePayslipService } from './payslips/mobilePayslipService.js';

const KEY_ACCESS_TOKEN = 'infinevo_access_token';
const KEY_REFRESH_TOKEN = 'infinevo_refresh_token';
const KEY_ID_TOKEN = 'infinevo_id_token';
const KEY_EXPIRES_AT = 'infinevo_token_expires_at';
const KEY_PKCE_VERIFIER = 'infinevo_pkce_verifier';
const KEY_PKCE_STATE = 'infinevo_pkce_state';

const MIN_TOKEN_VALIDITY_SECONDS = 30;

let currentTokens = {
  accessToken: null,
  refreshToken: null,
  idToken: null,
  expiresAt: 0,
  tokenParsed: null,
};

let authPromiseResolve = null;
let authPromiseReject = null;
let appUrlListenerAttached = false;
let browserListener = null;

async function getSecureItem(key) {
  try {
    const res = await SecureStoragePlugin.get({ key });
    return res?.value || null;
  } catch {
    return null;
  }
}

async function setSecureItem(key, value) {
  if (value) {
    await SecureStoragePlugin.set({ key, value });
  } else {
    await SecureStoragePlugin.remove({ key }).catch(() => {});
  }
}

async function removeSecureItem(key) {
  try {
    await SecureStoragePlugin.remove({ key });
  } catch {
    // Ignore key not found
  }
}

/**
 * Base64-URL encode a buffer without padding.
 */
function base64UrlEncode(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Generate a random 64-character PKCE code verifier (RFC 7636).
 * Fails with clear error if crypto.getRandomValues is missing — no weaker fallback.
 */
export function generateCodeVerifier(length = 64) {
  const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  if (typeof crypto === 'undefined' || !crypto.getRandomValues) {
    throw new Error('Secure random generator (crypto.getRandomValues) is not available');
  }
  const randomValues = new Uint8Array(length);
  crypto.getRandomValues(randomValues);
  let result = '';
  for (let i = 0; i < length; i++) {
    result += charset[randomValues[i] % charset.length];
  }
  return result;
}

/**
 * Derive code challenge from verifier using SHA-256.
 * Fails with clear error if crypto.subtle.digest is missing — no weaker fallback.
 */
export async function generateCodeChallenge(verifier) {
  if (typeof crypto === 'undefined' || !crypto.subtle || !crypto.subtle.digest) {
    throw new Error('Cryptographic digest (crypto.subtle.digest) is not available');
  }
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(digest);
}

/**
 * Parse JWT payload without external library dependencies.
 */
export function parseJwt(token) {
  if (!token) return null;
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

/**
 * Persist tokens in Keystore/Keychain secure storage; non-secret metadata in Preferences.
 */
async function saveTokens(tokens) {
  currentTokens = {
    ...tokens,
    tokenParsed: parseJwt(tokens.accessToken),
  };
  await setSecureItem(KEY_ACCESS_TOKEN, tokens.accessToken || '');
  await setSecureItem(KEY_REFRESH_TOKEN, tokens.refreshToken || '');
  await setSecureItem(KEY_ID_TOKEN, tokens.idToken || '');
  await Preferences.set({ key: KEY_EXPIRES_AT, value: String(tokens.expiresAt || 0) });
}

/**
 * Load tokens from secure storage into memory.
 */
async function loadStoredTokens() {
  const accessToken = await getSecureItem(KEY_ACCESS_TOKEN);
  const refreshToken = await getSecureItem(KEY_REFRESH_TOKEN);
  const idToken = await getSecureItem(KEY_ID_TOKEN);
  const { value: expiresAtStr } = await Preferences.get({ key: KEY_EXPIRES_AT });

  if (accessToken) {
    currentTokens = {
      accessToken,
      refreshToken: refreshToken || null,
      idToken: idToken || null,
      expiresAt: Number(expiresAtStr) || 0,
      tokenParsed: parseJwt(accessToken),
    };
    return currentTokens;
  }
  return null;
}

/**
 * Clear stored credentials from secure storage and preferences.
 */
export async function clearStoredTokens() {
  currentTokens = {
    accessToken: null,
    refreshToken: null,
    idToken: null,
    expiresAt: 0,
    tokenParsed: null,
  };
  await removeSecureItem(KEY_ACCESS_TOKEN);
  await removeSecureItem(KEY_REFRESH_TOKEN);
  await removeSecureItem(KEY_ID_TOKEN);
  await Preferences.remove({ key: KEY_EXPIRES_AT });
  await Preferences.remove({ key: KEY_PKCE_VERIFIER });
  await Preferences.remove({ key: KEY_PKCE_STATE });
}

/**
 * Exchange auth code for tokens at Keycloak token endpoint.
 */
async function exchangeCodeForTokens(code, verifier) {
  const tokenUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/token`;
  const body = new URLSearchParams();
  body.append('grant_type', 'authorization_code');
  body.append('client_id', config.keycloak.clientId);
  body.append('code', code);
  body.append('redirect_uri', 'infinevo://auth-callback');
  body.append('code_verifier', verifier);

  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: body.toString(),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Token exchange failed (${response.status}): ${errorText}`);
  }

  const data = await response.json();
  const expiresAt = Date.now() + (Number(data.expires_in) || 900) * 1000;

  await saveTokens({
    accessToken: data.access_token,
    refreshToken: data.refresh_token,
    idToken: data.id_token,
    expiresAt,
  });

  return currentTokens;
}

/**
 * Handle incoming deep-link auth callback (infinevo://auth-callback?...).
 */
export async function handleAuthCallbackUrl(url) {
  if (!url || !url.startsWith('infinevo://auth-callback')) {
    return false;
  }

  try {
    const parsedUrl = new URL(url.replace('infinevo://', 'https://'));
    const code = parsedUrl.searchParams.get('code');
    const state = parsedUrl.searchParams.get('state');

    const { value: storedState } = await Preferences.get({ key: KEY_PKCE_STATE });
    const { value: verifier } = await Preferences.get({ key: KEY_PKCE_VERIFIER });

    if (!code || !state || state !== storedState || !verifier) {
      throw new Error('Invalid OAuth2 state or missing PKCE verifier');
    }

    try {
      await Browser.close();
    } catch {
      // Browser might already be closed by OS
    }

    await exchangeCodeForTokens(code, verifier);

    if (authPromiseResolve) {
      authPromiseResolve(currentTokens);
      authPromiseResolve = null;
      authPromiseReject = null;
    }
    return true;
  } catch (err) {
    if (authPromiseReject) {
      authPromiseReject(err);
      authPromiseResolve = null;
      authPromiseReject = null;
    }
    throw err;
  }
}

/**
 * Setup deep-link listener for native container.
 */
function setupDeepLinkListener() {
  if (appUrlListenerAttached) return;
  appUrlListenerAttached = true;

  App.addListener('appUrlOpen', (event) => {
    handleAuthCallbackUrl(event.url).catch(() => {});
  });

  // Handle launch URL on cold start
  App.getLaunchUrl().then((launchUrl) => {
    if (launchUrl?.url) {
      handleAuthCallbackUrl(launchUrl.url).catch(() => {});
    }
  }).catch(() => {});
}

/**
 * Launch native browser for PKCE authentication.
 */
export async function startMobileLogin() {
  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);
  const state = generateCodeVerifier(32);

  await Preferences.set({ key: KEY_PKCE_VERIFIER, value: verifier });
  await Preferences.set({ key: KEY_PKCE_STATE, value: state });

  const authUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/auth?` +
    new URLSearchParams({
      client_id: config.keycloak.clientId,
      response_type: 'code',
      scope: 'openid profile email',
      redirect_uri: 'infinevo://auth-callback',
      code_challenge: challenge,
      code_challenge_method: 'S256',
      state,
    }).toString();

  if (browserListener) {
    try {
      await browserListener.remove();
    } catch {
      // Ignore
    }
  }

  browserListener = await Browser.addListener('browserFinished', () => {
    if (authPromiseReject) {
      authPromiseReject(new Error('cancelled'));
      authPromiseResolve = null;
      authPromiseReject = null;
    }
  });

  await Browser.open({ url: authUrl, windowName: '_system' });
}

/**
 * Initialize native mobile authentication.
 */
export async function initMobileAuth() {
  setupDeepLinkListener();

  const stored = await loadStoredTokens();
  if (stored?.refreshToken) {
    const valid = await getValidMobileToken();
    if (valid) {
      return currentTokens;
    }
  }

  await clearStoredTokens();

  return new Promise((resolve, reject) => {
    authPromiseResolve = resolve;
    authPromiseReject = reject;
    startMobileLogin().catch(reject);
  });
}

/**
 * Retrieve a valid token, refreshing via refresh_token if within 30s of expiring.
 */
export async function getValidMobileToken() {
  if (!currentTokens.accessToken) {
    await loadStoredTokens();
  }

  if (!currentTokens.accessToken) {
    return null;
  }

  const now = Date.now();
  const bufferMs = MIN_TOKEN_VALIDITY_SECONDS * 1000;

  if (currentTokens.expiresAt && now < (currentTokens.expiresAt - bufferMs)) {
    return currentTokens.accessToken;
  }

  // Token is expiring or expired - attempt refresh
  if (currentTokens.refreshToken) {
    try {
      const tokenUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/token`;
      const body = new URLSearchParams();
      body.append('grant_type', 'refresh_token');
      body.append('client_id', config.keycloak.clientId);
      body.append('refresh_token', currentTokens.refreshToken);

      const response = await fetch(tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });

      if (response.ok) {
        const data = await response.json();
        const expiresAt = Date.now() + (Number(data.expires_in) || 900) * 1000;
        await saveTokens({
          accessToken: data.access_token,
          refreshToken: data.refresh_token || currentTokens.refreshToken,
          idToken: data.id_token || currentTokens.idToken,
          expiresAt,
        });
        return currentTokens.accessToken;
      }
    } catch {
      // Refresh failed
    }
  }

  // Refresh failed: clear tokens and start a fresh login flow
  await clearStoredTokens();
  await startMobileLogin().catch(() => {});
  return null;
}

/**
 * Mobile sign out:
 * 1. Revokes refresh token at Keycloak
 * 2. Ends SSO session with id_token_hint
 * 3. Clears secure storage
 * 4. Reloads window / shows sign in
 */
export async function mobileLogout() {
  if (!currentTokens.refreshToken || !currentTokens.idToken) {
    await loadStoredTokens();
  }
  const refreshToken = currentTokens.refreshToken;
  const idToken = currentTokens.idToken;

  if (refreshToken) {
    try {
      const revokeUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/revoke`;
      const body = new URLSearchParams();
      body.append('client_id', config.keycloak.clientId);
      body.append('token', refreshToken);
      body.append('token_type_hint', 'refresh_token');
      await fetch(revokeUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
    } catch {
      // Best effort
    }
  }

  if (idToken) {
    try {
      const logoutUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/logout?` +
        new URLSearchParams({
          id_token_hint: idToken,
          post_logout_redirect_uri: 'infinevo://auth-callback',
        }).toString();
      await Browser.open({ url: logoutUrl, windowName: '_system' });
    } catch {
      // Best effort
    }
  }

  await clearStoredTokens();

  try {
    await mobilePayslipService.cleanup();
  } catch {
    // Best effort cleanup
  }

  if (typeof window !== 'undefined' && window.location && typeof window.location.reload === 'function') {
    try {
      window.location.reload();
    } catch {
      // Ignore in JSDOM / non-browser test environment
    }
  }
}

export function isNativePlatform() {
  return Capacitor.isNativePlatform();
}

export function getMobileTokenParsed() {
  return currentTokens.tokenParsed;
}

export function isMobileAuthenticated() {
  return Boolean(currentTokens.accessToken);
}
