/**
 * Route groups, one per module.
 *
 * Only the groups a tenant is entitled to are registered - navigation is driven by
 * entitlement, not by hiding menu items on a page that still responds. W-11 supplies
 * the real entitlement state; until then everything is empty.
 */
export const routeGroups = {
  core: [],
  hrms: [],
  payroll: [],
};

export function routesFor(entitlements = []) {
  return entitlements.flatMap((module) => routeGroups[module] ?? []);
}
