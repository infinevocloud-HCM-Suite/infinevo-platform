import React from 'react';
import { routes as coreRoutes } from '../core/index.js';
import { routes as hrmsRoutes } from '../hrms/index.js';
import { routes as payrollRoutes } from '../payroll/index.js';
import { PortalLayout } from './portal/PortalLayout.jsx';
import { ApplyLeave } from '../core/portal/ApplyLeave.jsx';
import { LeaveRequestDetail } from '../core/leave/LeaveRequestDetail.jsx';

/**
 * Self-service portal routes (W-25 §5, W-46.5 §5).
 * Mounted for authenticated users; renders panels returned dynamically from /api/v1/me/panels,
 * plus the self-service leave apply and request detail screens.
 */
export const portalRoutes = [
  { path: '/me', element: React.createElement(PortalLayout) },
  { path: '/me/:panelId', element: React.createElement(PortalLayout) },
  { path: '/me/leave/apply', element: React.createElement(ApplyLeave) },
  { path: '/me/leave/requests/:id', element: React.createElement(LeaveRequestDetail, { readOnly: true }) },
];

/**
 * Route groups, one per module, and the one way routes reach the router (W-12.3 §5, W-45 §5).
 *
 * A module registers its RouteObjects ({ path, element }) in its group as it is built. None of
 * them is mounted by being here: AppShell mounts the routes whose path appears in the navigation
 * feed returned by GET /api/v1/navigation, and the routes beneath such a path - the menu names
 * `/employees`, and `/employees/new` and `/employees/:id` are its screens, not menu items
 * (W-46.1 §5). A route outside every feed path is not registered at all, so no tenant reaches a
 * module's screens by typing a URL, and an empty feed is an empty route tree - there is no
 * static list of routes per module and no default.
 *
 * A child route is mounted on the parent's menu permission alone, so a child screen that needs
 * a different action code checks it itself with `useCan` and renders `NotEntitled`. Either way
 * the endpoint behind it refuses on its own (W-12.2).
 */
export const routeGroups = {
  core: coreRoutes || [],
  hrms: hrmsRoutes || [],
  payroll: payrollRoutes || [],
  portal: portalRoutes,
};

/**
 * The RouteObjects to mount for this feed: every registered route whose path the feed names,
 * at any depth, and every registered route one or more segments beneath such a path.
 *
 * @param {Array} feedItems items from GET /api/v1/navigation
 * @param {Object} [groups] the route groups to draw from; the module groups above by default
 * @returns {Array} RouteObject[]
 */
export function routesFromFeed(feedItems = [], groups = routeGroups) {
  const feedPaths = collectPaths(feedItems);
  const hasPayroll = Array.from(feedPaths).some(
    (prefix) => prefix && prefix !== '/' && (prefix === '/payroll' || prefix.startsWith('/payroll/')),
  );
  return Object.values(groups)
    .flat()
    .filter((route) => {
      if (feedPaths.has(route.path)) return true;
      for (const prefix of feedPaths) {
        if (prefix && prefix !== '/' && route.path.startsWith(`${prefix}/`)) {
          return true;
        }
      }
      if (hasPayroll && route.path.startsWith('/payroll/settings')) {
        return true;
      }
      return false;
    });
}

function collectPaths(items, into = new Set()) {
  for (const item of items || []) {
    if (item.path) into.add(item.path);
    if (item.children) collectPaths(item.children, into);
  }
  return into;
}
