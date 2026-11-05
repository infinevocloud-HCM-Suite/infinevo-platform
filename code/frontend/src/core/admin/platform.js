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

/** `SubscriptionPlan` (W-84.1). A subscription may have none. */
export const PLANS = ['STARTER', 'GROWTH', 'ENTERPRISE'];

export const PLAN_LABELS = {
  STARTER: 'Starter',
  GROWTH: 'Growth',
  ENTERPRISE: 'Enterprise',
};

/** `SupportPlan` (W-84.1). A subscription may have none. */
export const SUPPORT_PLANS = ['STANDARD', 'PRIORITY'];

export const SUPPORT_PLAN_LABELS = {
  STANDARD: 'Standard',
  PRIORITY: 'Priority',
};

function isLimit(limit) {
  return typeof limit === 'number' && Number.isFinite(limit);
}

/** `"N / M"` when the tenant has an employee limit, `"N"` when it has none (W-84.1). */
export function employeesText(count, limit) {
  const n = count ?? 0;
  return isLimit(limit) ? `${n} / ${limit}` : `${n}`;
}

/** True when the tenant has an employee limit and is above it (W-84.1). */
export function overLimit(count, limit) {
  return isLimit(limit) && Number(count ?? 0) > limit;
}
