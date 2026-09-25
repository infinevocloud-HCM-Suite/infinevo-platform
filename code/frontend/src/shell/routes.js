/**
 * Route groups, one per module (W-12.3).
 *
 * Routes are conditional on the navigation feed returned by GET /api/v1/navigation.
 * Only items in the feed are registered as routes — a route not in the feed is not
 * registered at all. There is no static array of routes per module, and no hidden
 * route that a tenant can reach by typing a URL.
 *
 * Module route arrays are registered here as modules are built. Each array element
 * is a react-router RouteObject: { path, element, children? }.
 */
export const routeGroups = {
  core: [],
  hrms: [],
  payroll: [],
};

/**
 * Build a flat list of RouteObjects from the navigation feed.
 *
 * @param {Array} feedItems - items from GET /api/v1/navigation
 * @param {Object} [routeGroupsOverride] - optional override for testing
 * @returns {Array} RouteObject[]
 */
export function routesFromFeed(feedItems = [], routeGroupsOverride = null) {
  const groups = routeGroupsOverride || routeGroups;
  const allRoutes = Object.values(groups).flat();

  // Only return routes whose path appears in the feed (top-level or nested)
  const feedPaths = collectPaths(feedItems);
  return allRoutes.filter((route) => feedPaths.has(route.path));
}

/**
 * Collect every path in the feed recursively into a Set.
 */
function collectPaths(items) {
  const paths = new Set();
  for (const item of items || []) {
    if (item.path) paths.add(item.path);
    if (item.children) {
      collectPaths(item.children).forEach((p) => paths.add(p));
    }
  }
  return paths;
}

/**
 * Legacy helper — kept for backward compatibility.
 * Prefer routesFromFeed when the navigation feed is available.
 */
export function routesFor(entitlements = []) {
  return entitlements.flatMap((module) => routeGroups[module] ?? []);
}
