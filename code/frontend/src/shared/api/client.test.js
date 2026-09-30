import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  apiClient,
  setUnauthorizedHandler,
  setTenantSuspendedHandler,
} from './client.js';

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
