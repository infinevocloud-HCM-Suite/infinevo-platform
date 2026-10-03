/**
 * The Infinevo platform tenant's fixed id (W-65.1 §4, `PlatformTenant.DEFAULT_PLATFORM_TENANT_ID`,
 * created by `core/V082`). The tenant overview carries no "platform" flag, so the console
 * recognises the row by this id.
 */
export const PLATFORM_TENANT_ID = '00000000-0000-0000-0000-000000000001';

export function isPlatformTenant(tenantId) {
  return tenantId === PLATFORM_TENANT_ID;
}

/** The two sellable modules (`PlatformModule`). Core is not a module. */
export const MODULES = ['HRMS', 'PAYROLL'];

/** `SubscriptionStatus`. */
export const STATUSES = ['ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'];

export const STATUS_COLORS = {
  ACTIVE: 'green',
  PAST_DUE: 'orange',
  SUSPENDED: 'red',
  CANCELLED: 'default',
};
