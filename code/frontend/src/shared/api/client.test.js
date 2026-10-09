import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import {
  apiClient,
  setUnauthorizedHandler,
  setTenantSuspendedHandler,
  setImpersonationProvider,
  setImpersonationInvalidHandler,
  IMPERSONATION_HEADER,
} from './client.js';
import impersonationReducer, { started } from '../../core/admin/impersonationSlice.js';

describe('apiClient response interceptor', () => {
  const responseInterceptor = apiClient.interceptors.response.handlers[0];

  beforeEach(() => {
    setUnauthorizedHandler(null);
    setTenantSuspendedHandler(null);
  });

  it('maps backend ApiErrorResponse into code and convenience booleans', async () => {
    const errorResponse = {
      response: {
        status: 403,
        data: {
          code: 'MODULE_NOT_ENTITLED',
          message: 'Tenant not entitled to module',
          fieldErrors: { module: 'Not subscribed' },
          traceId: 'trace-1234',
        },
      },
    };

    await expect(responseInterceptor.rejected(errorResponse)).rejects.toEqual({
      code: 'MODULE_NOT_ENTITLED',
      message: 'Tenant not entitled to module',
      fieldErrors: { module: 'Not subscribed' },
      traceId: 'trace-1234',
      status: 403,
      isModuleNotEntitled: true,
      isTenantSuspended: false,
      isForbidden: false,
      isUnauthorized: false,
      isImpersonationInvalid: false,
    });
  });

  it('calls onUnauthorized handler exactly once on UNAUTHORIZED or 401 status', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    const errorResponse = {
      response: {
        status: 401,
        data: {
          code: 'UNAUTHORIZED',
          message: 'Token expired',
        },
      },
    };

    await expect(responseInterceptor.rejected(errorResponse)).rejects.toMatchObject({
      isUnauthorized: true,
      status: 401,
    });

    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith('UNAUTHORIZED');
  });

  it('calls onUnauthorized handler on UNAUTHENTICATED code even without 401 status', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    const errorResponse = {
      response: {
        status: 400,
        data: {
          code: 'UNAUTHENTICATED',
          message: 'Invalid credentials or session',
        },
      },
    };

    await expect(responseInterceptor.rejected(errorResponse)).rejects.toMatchObject({
      isUnauthorized: true,
      code: 'UNAUTHENTICATED',
    });

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('calls onTenantSuspended handler exactly once on TENANT_SUSPENDED code', async () => {
    const handler = vi.fn();
    setTenantSuspendedHandler(handler);

    const errorResponse = {
      response: {
        status: 403,
        data: {
          code: 'TENANT_SUSPENDED',
          message: 'Subscription suspended',
        },
      },
    };

    await expect(responseInterceptor.rejected(errorResponse)).rejects.toMatchObject({
      isTenantSuspended: true,
      code: 'TENANT_SUSPENDED',
    });

    expect(handler).toHaveBeenCalledTimes(1);
  });
});

describe('apiClient impersonation (W-65.3)', () => {
  const requestInterceptor = apiClient.interceptors.request.handlers[0];
  const responseInterceptor = apiClient.interceptors.response.handlers[0];
  let store;

  const session = {
    sessionId: 'sess-1',
    tenantId: 'tenant-globex',
    tenantName: 'Globex',
    userLabel: 'admin@globex.local',
    expiresAt: '2026-10-03T10:30:00Z',
  };

  beforeEach(() => {
    store = configureStore({ reducer: { impersonation: impersonationReducer } });
    // The wiring the shell header does: read the session from the store, clear it on rejection.
    setImpersonationProvider(() => store.getState().impersonation.session?.sessionId ?? null);
    setImpersonationInvalidHandler(() => store.dispatch({ type: 'impersonation/stopped' }));
  });

  afterEach(() => {
    setImpersonationProvider(null);
    setImpersonationInvalidHandler(null);
  });

  it('sends no X-Impersonation header while the store holds no session', async () => {
    const cfg = await requestInterceptor.fulfilled({ headers: {} });
    expect(cfg.headers[IMPERSONATION_HEADER]).toBeUndefined();
  });

  it('sends X-Impersonation with the session id while a session is live', async () => {
    store.dispatch(started(session));
    const cfg = await requestInterceptor.fulfilled({ headers: {} });
    expect(IMPERSONATION_HEADER).toBe('X-Impersonation');
    expect(cfg.headers['X-Impersonation']).toBe('sess-1');
  });

  it('leaves the header off a call that opts out with skipImpersonation', async () => {
    store.dispatch(started(session));
    const cfg = await requestInterceptor.fulfilled({ headers: {}, skipImpersonation: true });
    expect(cfg.headers['X-Impersonation']).toBeUndefined();
  });

  it('clears the session on 403 IMPERSONATION_INVALID, and the next call goes out without it', async () => {
    store.dispatch(started(session));
    await expect(
      responseInterceptor.rejected({
        response: { status: 403, data: { code: 'IMPERSONATION_INVALID', message: 'expired' } },
      }),
    ).rejects.toMatchObject({ code: 'IMPERSONATION_INVALID', isImpersonationInvalid: true, status: 403 });

    expect(store.getState().impersonation.session).toBeNull();
    const cfg = await requestInterceptor.fulfilled({ headers: {} });
    expect(cfg.headers['X-Impersonation']).toBeUndefined();
  });

  it('keeps the session on any other 403', async () => {
    store.dispatch(started(session));
    await expect(
      responseInterceptor.rejected({ response: { status: 403, data: { code: 'FORBIDDEN' } } }),
    ).rejects.toMatchObject({ isImpersonationInvalid: false });
    expect(store.getState().impersonation.session).toEqual(session);
  });
});
