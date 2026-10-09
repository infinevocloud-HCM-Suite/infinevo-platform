import { apiClient } from '@shared/api/client.js';
import { createService } from '@shared/api/createService.js';

/**
 * Tenant provisioning and subscription calls for platform staff (W-65.3 §5).
 *
 * Every reply here is a bare DTO, not the `{status, message, data}` envelope:
 *   - list, get: `TenantOverview` (snake_case: tenant_id, name, country_code, timezone, status,
 *     modules, created_at, current_period_end, user_count, admin_invitation: { email, status }
 *     with status PENDING | ACCEPTED | NONE) - core TenantController
 *   - create: body `TenantRequest` (snake_case: name, country_code, timezone,
 *     leave_year_start_month, modules, optional admin_email); reply `TenantResponse`
 *     (camelCase: tenantId, name, ..., adminInvitationId) - core TenantController
 *   - subscription, setModules, setStatus: `SubscriptionResponse` - core SubscriptionController
 *   - getSummary: `TenantSummaryResponse` (camelCase: total, byStatus, createdLast30Days,
 *     recent[{ id, name, createdAt, status }], waitingForAdmin[{ id, name, adminEmail,
 *     invitationStatus PENDING | EXPIRED, expiresAt }]) - core TenantController (W-73.2)
 *   - resendAdminInvitation: no body, `204` - core TenantController (W-73.2)
 *   - countryTemplates: `[{ countryCode, sections, version }]` - core CountryTemplateController (W-73.9)
 *   - applyTemplate: `{ countryCode, applied, skipped }` - core TenantController (W-73.9)
 *
 * list, get, create and the two PUTs are platform-tenant endpoints (`core.tenant.provision`), so
 * they never carry `X-Impersonation`: with it the server binds the customer tenant and checks the
 * acted-as user's permissions, and answers 403. Only `subscription` keeps the header.
 */
const base = createService('/v1/tenants');

const PLATFORM_ONLY = { skipImpersonation: true };

export const tenantService = {
  ...base,

  /** `GET /v1/tenants` - every tenant, platform staff only. */
  async list(params) {
    const res = await apiClient.get(base.basePath, { params, ...PLATFORM_ONLY });
    return res?.data !== undefined ? res.data : res;
  },

  /** `GET /v1/tenants/{id}` - one tenant's overview, platform staff only. */
  async get(id) {
    const res = await apiClient.get(`${base.basePath}/${id}`, PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

  /** `POST /v1/tenants` - provision a tenant, platform staff only. */
  async create(body) {
    const res = await apiClient.post(base.basePath, body, PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

  /** `GET /v1/tenants/summary` - the platform dashboard's figures, platform staff only (W-73.2). */
  async getSummary() {
    const res = await apiClient.get(`${base.basePath}/summary`, PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

  /** `POST /v1/tenants/{id}/admin-invitation/resend` - send the tenant's administrator invitation again. */
  async resendAdminInvitation(id) {
    await apiClient.post(`${base.basePath}/${id}/admin-invitation/resend`, null, PLATFORM_ONLY);
  },

  /**
   * `GET /v1/reference/country-templates` - the countries with a template, `[{ countryCode, sections,
   * version }]` (W-73.9).
   */
  async countryTemplates() {
    const res = await apiClient.get('/v1/reference/country-templates', PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * `POST /v1/tenants/{id}/apply-template` - apply the tenant's country template where it has nothing of its
   * own; reply `{ countryCode, applied: [section], skipped: [section] }` (W-73.9).
   */
  async applyTemplate(id) {
    const res = await apiClient.post(`${base.basePath}/${id}/apply-template`, null, PLATFORM_ONLY);
    return res?.data !== undefined ? res.data : res;
  },

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
