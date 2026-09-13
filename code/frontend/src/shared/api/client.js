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
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
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
