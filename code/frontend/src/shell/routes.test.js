/**
 * routesFromFeed (W-12.3 §5): only a route whose path the feed names is mounted, at any depth,
 * and an empty feed mounts nothing.
 */
import { describe, it, expect } from 'vitest';
import { routesFromFeed, routeGroups } from './routes.js';

const groups = {
  core: [
    { path: '/employees', element: 'Employees' },
    { path: '/org/departments', element: 'Departments' },
    { path: '/roles', element: 'Roles' },
  ],
  hrms: [{ path: '/timesheets', element: 'Timesheets' }],
  payroll: [],
};

describe('routesFromFeed', () => {
  it('mounts only the routes the feed names, including nested paths', () => {
    const feed = [
      { key: 'core.org', path: '/org', children: [{ key: 'core.org.departments', path: '/org/departments' }] },
      { key: 'core.roles', path: '/roles' },
    ];

    expect(routesFromFeed(feed, groups).map((r) => r.path)).toEqual(['/org/departments', '/roles']);
  });

  it('mounts nothing for an empty feed', () => {
    expect(routesFromFeed([], groups)).toEqual([]);
    expect(routesFromFeed(undefined, groups)).toEqual([]);
  });

  it('draws from the module route groups by default, which start empty', () => {
    expect(Object.values(routeGroups).flat()).toEqual([]);
    expect(routesFromFeed([{ key: 'core.roles', path: '/roles' }])).toEqual([]);
  });
});
