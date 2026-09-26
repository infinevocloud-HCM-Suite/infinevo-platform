/**
 * Route groups, one per module, and the one way routes reach the router (W-12.3 §5).
 *
 * A module registers its RouteObjects ({ path, element }) in its group as it is built. None of
 * them is mounted by being here: AppShell mounts exactly the routes whose path appears in the
 * navigation feed returned by GET /api/v1/navigation. A route that is not in the feed is not
 * registered at all, so no tenant reaches a screen by typing its URL, and an empty feed is an
 * empty route tree - there is no static list of routes per module and no default.
 */
export const routeGroups = {
  core: [],
  hrms: [],
  payroll: [],
};

/**
 * The RouteObjects to mount for this feed: every registered route whose path the feed names,
 * at any depth.
 *
 * @param {Array} feedItems items from GET /api/v1/navigation
 * @param {Object} [groups] the route groups to draw from; the module groups above by default
 * @returns {Array} RouteObject[]
 */
export function routesFromFeed(feedItems = [], groups = routeGroups) {
  const feedPaths = collectPaths(feedItems);
  return Object.values(groups)
    .flat()
    .filter((route) => feedPaths.has(route.path));
}

function collectPaths(items, into = new Set()) {
  for (const item of items || []) {
    if (item.path) into.add(item.path);
    if (item.children) collectPaths(item.children, into);
  }
  return into;
}
