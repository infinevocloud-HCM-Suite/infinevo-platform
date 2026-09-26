import Keycloak from 'keycloak-js';

/**
 * The one Keycloak adapter (W-10). Nothing else constructs a Keycloak instance.
 *
 * Configuration, in the order it is read - the same two-step the API base URL uses
 * (client.js:21-24, W-49): a runtime value injected into the page wins, so one built
 * image can run in dev and in Azure; the build-time VITE_* value is the fallback.
 *
 *   window.__ENV.KEYCLOAK_URL   / VITE_KEYCLOAK_URL        - e.g. http://localhost:8081
 *   window.__ENV.KEYCLOAK_REALM / VITE_KEYCLOAK_REALM      - defaults to 'infinevo'
 *   window.__ENV.KEYCLOAK_CLIENT_ID / VITE_KEYCLOAK_CLIENT_ID - defaults to 'infinevo-web'
 *
 * VITE_KEYCLOAK_URL is already supplied to the web container - infra/docker/compose.yml:212.
 * The realm and client id carry defaults matching infra/docker/keycloak/dev-realm.json:53,
 * so only the URL has to be set anywhere.
 *
 * `infinevo-web` is a PUBLIC client (dev-realm.json:56). There is no client secret and
 * none may be added: every VITE_* value is compiled into the browser bundle, so nothing
 * confidential can live here. The browser proves itself with PKCE instead.
 *
 * The tenant is not configured, stored or sent. It rides in the token's `tenant_id`
 * claim and is resolved server-side - see the note at client.js:16-18.
 */

const runtime = (typeof window !== 'undefined' && window.__ENV) || {};

export const keycloak = new Keycloak({
  url: runtime.KEYCLOAK_URL || import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
  realm: runtime.KEYCLOAK_REALM || import.meta.env.VITE_KEYCLOAK_REALM || 'infinevo',
  clientId: runtime.KEYCLOAK_CLIENT_ID || import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'infinevo-web',
});

/**
 * Seconds of remaining validity below which a token is refreshed. The realm issues a
 * 15-minute access token, so a request is never sent with less than half a minute left
 * on it - the alternative is a 401 in the middle of a session.
 */
const MIN_TOKEN_VALIDITY_SECONDS = 30;

/**
 * Runs before the app renders. `login-required` means an unauthenticated visitor is
 * redirected to Keycloak rather than shown a local login form - the platform has no
 * local login (W-10 deletes the /api/v1/auth/login exemption).
 */
export function initAuth() {
  return keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    // The silent-check-sso iframe is blocked by third-party-cookie rules in current
    // browsers and only produces spurious logouts; updateToken covers session validity.
    checkLoginIframe: false,
  });
}

/**
 * A token that is valid now, refreshing it first if it is about to expire. Returns null
 * when the refresh token itself has expired, having started a fresh login redirect.
 */
export async function getValidToken() {
  if (!keycloak.authenticated) {
    return null;
  }
  try {
    await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
  } catch {
    // The refresh token is gone too - the only honest move is a new login.
    keycloak.login();
    return null;
  }
  return keycloak.token ?? null;
}

export function logout() {
  return keycloak.logout();
}

/**
 * Tenant-change notifications (W-12.3 §5).
 *
 * The tenant rides in the token's `tenant_id` claim and nowhere else, so the only moment a
 * different tenant can appear is when a token arrives: at login (`onAuthSuccess`) or on a
 * refresh (`onAuthRefreshSuccess`). keycloak-js holds one callback per event, so this module
 * owns both and fans out to the listeners registered here. The navigation feed refetches on
 * this signal; a menu belonging to the previous tenant is otherwise left on screen.
 */
const tenantListeners = new Set();
let seenTenant = false;
let lastTenantId = null;

function currentTenantId() {
  return keycloak.tokenParsed?.tenant_id ?? null;
}

function tokenArrived() {
  const tenantId = currentTenantId();
  if (seenTenant && tenantId !== lastTenantId) {
    const previous = lastTenantId;
    lastTenantId = tenantId;
    tenantListeners.forEach((listener) => listener(tenantId, previous));
    return;
  }
  seenTenant = true;
  lastTenantId = tenantId;
}

keycloak.onAuthSuccess = tokenArrived;
keycloak.onAuthRefreshSuccess = tokenArrived;

/**
 * Calls `listener(newTenantId, previousTenantId)` whenever a token arrives carrying a
 * different tenant from the last one. Returns the function that removes the listener.
 */
export function onTenantChange(listener) {
  tenantListeners.add(listener);
  return () => tenantListeners.delete(listener);
}
