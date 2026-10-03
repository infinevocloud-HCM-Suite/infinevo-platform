import { apiClient } from '@shared/api/client.js';
import { createService } from '@shared/api/createService.js';

/**
 * Tenant provisioning and subscription calls for platform staff (W-65.3 §5).
 *
 * Every reply here is a bare DTO, not the `{status, message, data}` envelope:
 *   - list, get: `TenantOverview` (snake_case: tenant_id, name, country_code, timezone, status,
 *     modules, created_at, current_period_end, user_count) - core TenantController
 *   - create: `TenantResponse` (camelCase: tenantId, name, ...) - core TenantController
 *   - subscription, setModules, setStatus: `SubscriptionResponse` - core SubscriptionController
 *
 * The two PUTs are platform-tenant endpoints, so they never carry `X-Impersonation`.
 */
const base = createService('/v1/tenants');

const PLATFORM_ONLY = { skipImpersonation: true };

export const tenantService = {
  ...base,

  /** `GET /v1/tenants/{id}/subscription` - answers only for the bound tenant, i.e. while acting in it. */
  async subscription(id) {
    const res = await apiClient.get(`/v1/tenants/${id}/subscription`);
    return res?.data !== undefined ? res.data : res;
  },

  /** `PUT /v1/tenants/{id}/subscription/modules` with `{ modules: ['HRMS', 'PAYROLL'] }`. */
  async setModules(id, modules) {
    const res = await apiClient.put(
      `/v1/tenants/${id}/subscription/modules`,
      { modules },
      PLATFORM_ONLY,
    );
    return res?.data !== undefined ? res.data : res;
  },

  /** `PUT /v1/tenants/{id}/subscription/status` with `{ status }`. */
  async setStatus(id, status) {
    const res = await apiClient.put(
      `/v1/tenants/${id}/subscription/status`,
      { status },
      PLATFORM_ONLY,
    );
    return res?.data !== undefined ? res.data : res;
  },
};
