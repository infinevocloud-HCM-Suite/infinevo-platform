import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Where the frontend answers. The dev stack (infra/docker/compose.yml) serves Vite on 5173. */
export const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173';

/** Every seeded user shares one password (D-36). */
export const PASSWORD = process.env.E2E_PASSWORD || 'Local_dev_pw1!';

const E2E_DIR = fileURLToPath(new URL('..', import.meta.url));

/** The saved browser state for a role, written by `auth.setup.js`. */
export function authFile(role) {
  return path.join(E2E_DIR, '.auth', `${role}.json`);
}

/**
 * The roles the suite signs in as, one project each. `homes` lists every path the navigation feed may
 * name as the role's home (NavigationService.homePath): the tenant admin lands on `/setup` while the
 * checklist has an open step and on `/payroll/dashboard` once it is finished.
 */
export const ROLES = {
  employee: {
    username: 'employee.globex',
    tenant: 'Globex',
    homes: ['/hrms/dashboard', '/me'],
    employeeNumber: 'GLBX-0001',
    displayName: 'Gautam Employee',
  },
  manager: {
    username: 'manager.globex',
    tenant: 'Globex',
    homes: ['/approvals'],
  },
  hr: {
    username: 'hr.globex',
    tenant: 'Globex',
    homes: ['/hrms/dashboard'],
  },
  'payroll-officer': {
    username: 'payroll.globex',
    tenant: 'Globex',
    homes: ['/payroll/dashboard'],
  },
  finance: {
    username: 'finance.globex',
    tenant: 'Globex',
    homes: ['/payroll/dashboard'],
  },
  'tenant-admin': {
    username: 'admin.globex',
    tenant: 'Globex',
    homes: ['/setup', '/payroll/dashboard'],
  },
  'platform-admin': {
    username: 'staff.infinevo',
    tenant: 'Infinevo (platform)',
    homes: ['/admin'],
  },
};

/**
 * Seeded users the suite does not sign in as today. Acme holds Payroll only; add a role here and in
 * ROLES to cover it (see README "Adding a role").
 */
export const OTHER_USERS = {
  'admin.acme': { role: 'tenant-admin', tenant: 'Acme' },
  'employee.acme': { role: 'employee', tenant: 'Acme' },
};

/** Homes that are dashboards: the landing test expects at least one figure on them. */
export const DASHBOARD_PATHS = ['/hrms/dashboard', '/payroll/dashboard', '/admin'];

export function isDashboard(pathname) {
  return DASHBOARD_PATHS.includes(pathname);
}
