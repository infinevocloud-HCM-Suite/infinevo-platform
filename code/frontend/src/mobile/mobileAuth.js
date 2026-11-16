import { Capacitor } from '@capacitor/core';
import { Browser } from '@capacitor/browser';
import { App } from '@capacitor/app';
import { Preferences } from '@capacitor/preferences';
import { config } from '../shared/config.js';

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
 */
export function generateCodeVerifier(length = 64) {
  const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  const randomValues = new Uint8Array(length);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(randomValues);
  } else {
    for (let i = 0; i < length; i++) {
      randomValues[i] = Math.floor(Math.random() * 256);
    }
  }
  let result = '';
  for (let i = 0; i < length; i++) {
    result += charset[randomValues[i] % charset.length];
  }
  return result;
}

/**
 * Derive code challenge from verifier using SHA-256.
 */
export async function generateCodeChallenge(verifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  if (typeof crypto !== 'undefined' && crypto.subtle && crypto.subtle.digest) {
    const digest = await crypto.subtle.digest('SHA-256', data);
    return base64UrlEncode(digest);
  }
  // Fallback for mock/test environments without SubtleCrypto
  return btoa(verifier).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
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
 * Persist tokens in Capacitor Preferences storage.
 */
async function saveTokens(tokens) {
  currentTokens = {
    ...tokens,
    tokenParsed: parseJwt(tokens.accessToken),
  };
  await Preferences.set({ key: KEY_ACCESS_TOKEN, value: tokens.accessToken || '' });
  await Preferences.set({ key: KEY_REFRESH_TOKEN, value: tokens.refreshToken || '' });
  await Preferences.set({ key: KEY_ID_TOKEN, value: tokens.idToken || '' });
  await Preferences.set({ key: KEY_EXPIRES_AT, value: String(tokens.expiresAt || 0) });
}

/**
 * Load tokens from Capacitor Preferences storage into memory.
 */
async function loadStoredTokens() {
  const { value: accessToken } = await Preferences.get({ key: KEY_ACCESS_TOKEN });
  const { value: refreshToken } = await Preferences.get({ key: KEY_REFRESH_TOKEN });
  const { value: idToken } = await Preferences.get({ key: KEY_ID_TOKEN });
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
 * Clear stored credentials.
 */
export async function clearStoredTokens() {
  currentTokens = {
    accessToken: null,
    refreshToken: null,
    idToken: null,
    expiresAt: 0,
    tokenParsed: null,
  };
  await Preferences.remove({ key: KEY_ACCESS_TOKEN });
  await Preferences.remove({ key: KEY_REFRESH_TOKEN });
  await Preferences.remove({ key: KEY_ID_TOKEN });
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

  await Browser.open({ url: authUrl, windowName: '_system' });
}

/**
 * Initialize native mobile authentication.
 */
export async function initMobileAuth() {
  setupDeepLinkListener();

  const stored = await loadStoredTokens();
  if (stored?.accessToken) {
    // Check if current token or refreshed token is valid
    const valid = await getValidMobileToken();
    if (valid) {
      return stored;
    }
  }

  // Need user login
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

  // Both tokens invalid/expired
  await clearStoredTokens();
  return null;
}

/**
 * Mobile sign out.
 */
export async function mobileLogout() {
  const idToken = currentTokens.idToken;
  await clearStoredTokens();

  if (idToken) {
    try {
      const logoutUrl = `${config.keycloak.url}/realms/${config.keycloak.realm}/protocol/openid-connect/logout?` +
        new URLSearchParams({
          id_token_hint: idToken,
          post_logout_redirect_uri: 'infinevo://auth-callback',
        }).toString();
      await fetch(logoutUrl, { method: 'GET' });
    } catch {
      // Best effort logout
    }
  }

  if (typeof window !== 'undefined') {
    window.location.reload();
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

