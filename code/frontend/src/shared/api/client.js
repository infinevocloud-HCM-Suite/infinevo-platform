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

export function setTokenProvider(provider) {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export function setTenantSuspendedHandler(handler) {
  tenantSuspendedHandler = handler;
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
  // Nothing tenant-shaped is added here, on purpose. See the note above.
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

    if (isUnauthorized && typeof unauthorizedHandler === 'function') {
      try {
        unauthorizedHandler();
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
    });
  },
);
