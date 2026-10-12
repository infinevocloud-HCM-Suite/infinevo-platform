import axios from 'axios';
import { config } from '../config.js';

/**
 * The one HTTP client. Every call goes through this.
 *
 * This exists in W-01 on purpose. The frozen Payroll frontend has no service layer at
 * all - screens call axios directly with a global URL constant and read the tenant from
 * local storage. Creating the layer before any screen exists is what stops that pattern
 * being copied back in during the port.
 *
 * Three things belong here and nowhere else:
 *   - the base URL, from configuration rather than a constant in a component
 *   - the auth token, attached once (Keycloak adapter, W-10)
 *   - error handling, reading the one ApiErrorResponse envelope the backend returns
 *
 * The tenant is deliberately NOT sent by the client. It is derived server-side from the
 * authenticated principal (W-08). A tenant the client can set is a tenant the client can
 * change.
 */

let tokenProvider = null;
let unauthorizedHandler = null;
let tenantSuspendedHandler = null;
let impersonationProvider = null;
let impersonationInvalidHandler = null;

/** The `X-Impersonation` header the backend reads (W-65.2, `TenantContextFilter`). */
export const IMPERSONATION_HEADER = 'X-Impersonation';

export function setTokenProvider(provider) {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export function setTenantSuspendedHandler(handler) {
  tenantSuspendedHandler = handler;
}

/**
 * Supplies the live impersonation session id, or null (W-65.3 §5). shared cannot import the
 * store, so the shell injects a reader of it - the same seam as the token provider.
 */
export function setImpersonationProvider(provider) {
  impersonationProvider = provider;
}

/** Called once when the server answers `403 IMPERSONATION_INVALID` (W-65.3 §5). */
export function setImpersonationInvalidHandler(handler) {
  impersonationInvalidHandler = handler;
}

// Aliases matching spec conventions
export function onUnauthorized(handler) {
  setUnauthorizedHandler(handler);
}

export function onTenantSuspended(handler) {
  setTenantSuspendedHandler(handler);
}

export const apiClient = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use(async (reqConfig) => {
  // Asking the provider each time - not caching a token read at startup - is what keeps
  // a 15-minute access token from being sent expired; the adapter refreshes it first.
  const token = tokenProvider ? await tokenProvider() : null;
  if (token) {
    reqConfig.headers.Authorization = `Bearer ${token}`;
  }
  // The one exception to the note above: while platform staff act inside a customer tenant the
  // session id goes out, and the server binds the session's tenant from it (W-65.2). The server
  // checks the session is the caller's and live; the client only carries it. Calls to the
  // platform's own endpoints (opening and closing a session) opt out with `skipImpersonation`.
  const sessionId =
    !reqConfig.skipImpersonation && typeof impersonationProvider === 'function'
      ? impersonationProvider()
      : null;
  if (sessionId) {
    reqConfig.headers[IMPERSONATION_HEADER] = sessionId;
  }
  return reqConfig;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data;
    const code = body?.code ?? 'INTERNAL';
    const isUnauthorized =
      code === 'UNAUTHORIZED' || code === 'UNAUTHENTICATED' || error.response?.status === 401;
    const isTenantSuspended = code === 'TENANT_SUSPENDED';
    const isImpersonationInvalid = code === 'IMPERSONATION_INVALID';

    if (isUnauthorized && typeof unauthorizedHandler === 'function') {
      try {
        // The code lets the shell tell "sign in again" from "no tenant bound" (D-63).
        unauthorizedHandler(code);
      } catch (err) {
        console.error('unauthorizedHandler threw error', err);
      }
    }

    if (isTenantSuspended && typeof tenantSuspendedHandler === 'function') {
      try {
        tenantSuspendedHandler();
      } catch (err) {
        console.error('tenantSuspendedHandler threw error', err);
      }
    }

    if (isImpersonationInvalid && typeof impersonationInvalidHandler === 'function') {
      try {
        impersonationInvalidHandler();
      } catch (err) {
        console.error('impersonationInvalidHandler threw error', err);
      }
    }

    // Callers can discriminate on `err.code` or helper booleans
    return Promise.reject({
      code,
      message: body?.message ?? 'Something went wrong',
      fieldErrors: body?.fieldErrors ?? {},
      traceId: body?.traceId,
      status: error.response?.status,
      isModuleNotEntitled: code === 'MODULE_NOT_ENTITLED',
      isTenantSuspended,
      isForbidden: code === 'FORBIDDEN',
      isUnauthorized,
      isImpersonationInvalid,
    });
  },
);
