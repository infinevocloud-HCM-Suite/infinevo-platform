import axios from 'axios';

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
/**
 * Supplies a currently-valid bearer token, or null when there is none.
 *
 * The shell registers the Keycloak adapter here at startup (main.jsx). It is registered
 * rather than imported so that `shared` keeps depending on nothing - the shell composes
 * shared, not the other way round - and so a test can hand in its own provider.
 */
let tokenProvider = null;

export function setTokenProvider(provider) {
  tokenProvider = provider;
}

export const apiClient = axios.create({
  baseURL:
    (typeof window !== 'undefined' && window.__ENV?.API_BASE_URL) ||
    import.meta.env.VITE_API_BASE_URL ||
    '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use(async (config) => {
  // Asking the provider each time - not caching a token read at startup - is what keeps
  // a 15-minute access token from being sent expired; the adapter refreshes it first.
  const token = tokenProvider ? await tokenProvider() : null;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Nothing tenant-shaped is added here, on purpose. See the note above.
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data;
    // The backend's ApiErrorResponse envelope - see shared/error/ApiErrorResponse.java
    return Promise.reject({
      code: body?.code ?? 'INTERNAL',
      message: body?.message ?? 'Something went wrong',
      fieldErrors: body?.fieldErrors ?? {},
      traceId: body?.traceId,
      status: error.response?.status,
    });
  },
);
