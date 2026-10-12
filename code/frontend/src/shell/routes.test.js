/**
 * routesFromFeed (W-12.3 §5, W-46.1 §5): a route is mounted when the feed names its path, at
 * any depth, or names a path above it; an empty feed mounts nothing.
 */
import { describe, it, expect } from 'vitest';
import { routesFromFeed, routeGroups, portalRoutes } from './routes.js';

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

  it('mounts the routes beneath a feed path, and nothing that merely shares its prefix', () => {
    const withChildren = {
      core: [
        { path: '/employees', element: 'List' },
        { path: '/employees/new', element: 'Create' },
        { path: '/employees/:id', element: 'Page' },
        { path: '/employees-archive', element: 'Archive' },
        { path: '/roles', element: 'Roles' },
      ],
    };

    const mounted = routesFromFeed([{ key: 'core.employee', path: '/employees' }], withChildren);

    expect(mounted.map((r) => r.path)).toEqual(['/employees', '/employees/new', '/employees/:id']);
  });

  it('does not mount a parent because the feed names its child', () => {
    const feed = [{ key: 'core.employee.new', path: '/employees/new' }];
    const registered = {
      core: [
        { path: '/employees', element: 'List' },
        { path: '/employees/new', element: 'Create' },
      ],
    };

    expect(routesFromFeed(feed, registered).map((r) => r.path)).toEqual(['/employees/new']);
  });

  it('W-73.4: mounts a redirect with the path it names in mountWith, and not without it', () => {
    const registered = {
      core: [
        { path: '/users', element: 'Users' },
        { path: '/invitations/users', mountWith: '/users', element: 'Redirect' },
      ],
    };

    expect(routesFromFeed([{ key: 'core.users', path: '/users' }], registered).map((r) => r.path)).toEqual([
      '/users',
      '/invitations/users',
    ]);
    expect(routesFromFeed([{ key: 'core.roles', path: '/roles' }], registered)).toEqual([]);
  });

  it('mounts nothing for an empty feed', () => {
    expect(routesFromFeed([], groups)).toEqual([]);
    expect(routesFromFeed(undefined, groups)).toEqual([]);
  });

  it('draws from the module route groups by default', () => {
    expect(routeGroups).toHaveProperty('core');
    expect(routeGroups).toHaveProperty('hrms');
    expect(routeGroups).toHaveProperty('payroll');
    expect(routesFromFeed([{ key: 'core.nowhere', path: '/nowhere' }])).toEqual([]);
    const employeeRoutes = routesFromFeed([{ key: 'core.employees', path: '/employees' }]);
    expect(employeeRoutes.map((r) => r.path)).toEqual([
      '/employees',
      '/employees/new',
      '/employees/import',
      '/employees/:id',
      '/employees/:employeeId/tax-declaration/:fy',
    ]);
  });

  it('D-72: mounts the leave request detail for a manager whose feed names only /approvals', () => {
    const paths = routesFromFeed([{ key: 'core.approvals', path: '/approvals' }]).map((r) => r.path);
    expect(paths).toContain('/leave/requests/:id');
    expect(paths).not.toContain('/leave/requests');
    expect(paths).not.toContain('/leave/requests/new');
  });

  it('D-73: mounts the Roles and Audit log screens when the feed names them', () => {
    expect(routesFromFeed([{ key: 'core.roles', path: '/roles' }]).map((r) => r.path)).toEqual(['/roles']);
    expect(routesFromFeed([{ key: 'core.audit', path: '/audit' }]).map((r) => r.path)).toEqual(['/audit']);
  });
});

describe('portalRoutes', () => {
  it('D-74: registers the payslip page beneath the payslips panel', () => {
    expect(portalRoutes.map((r) => r.path)).toContain('/me/payslips/:payrunId');
  });
});
