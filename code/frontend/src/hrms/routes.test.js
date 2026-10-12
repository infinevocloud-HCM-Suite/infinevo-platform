/**
 * D-72: the request detail screens an approver opens from /approvals mount with the inbox, so a manager whose
 * feed has no employee-only request menu still reaches them. The backend GETs admit core.approval.decide
 * (RegularizationController, OvertimeRequestController).
 */
import { describe, it, expect } from 'vitest';
import { routes } from './index.js';
import { routesFromFeed } from '../shell/routes.js';

const mounted = (feed) => routesFromFeed(feed, { hrms: routes }).map((r) => r.path);

describe('hrms routes (D-72)', () => {
  it('a feed with only /approvals mounts the regularization and overtime detail screens', () => {
    const paths = mounted([{ key: 'core.approvals', path: '/approvals' }]);
    expect(paths).toContain('/hrms/regularizations/:id');
    expect(paths).toContain('/hrms/overtime-requests/:id');
  });

  it('it mounts nothing else of HRMS: the lists stay behind their own menu items', () => {
    const paths = mounted([{ key: 'core.approvals', path: '/approvals' }]);
    expect(paths).toEqual(['/hrms/regularizations/:id', '/hrms/overtime-requests/:id']);
  });

  it('HR menu items for everyone\'s requests mount the two log screens', () => {
    const paths = mounted([
      { key: 'hrms.regularizations_all', path: '/hrms/regularizations/all' },
      { key: 'hrms.overtime_all', path: '/hrms/overtime-requests/all' },
    ]);
    expect(paths).toEqual(expect.arrayContaining(['/hrms/regularizations/all', '/hrms/overtime-requests/all']));
  });

  it('a row opened from an "All" list mounts its detail for a reader with neither inbox nor own requests', () => {
    // payroll-officer holds core.attendance.read and core.overtime.read, but not core.approval.decide.
    const paths = mounted([
      { key: 'hrms.regularizations_all', path: '/hrms/regularizations/all' },
      { key: 'hrms.overtime_all', path: '/hrms/overtime-requests/all' },
    ]);
    expect(paths).toEqual(expect.arrayContaining(['/hrms/regularizations/:id', '/hrms/overtime-requests/:id']));
  });
});
