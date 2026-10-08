/**
 * English text for the navigation feed's label keys (W-12.3, W-45 §2 "Internationalisation").
 *
 * The feed carries only `labelKey`; this is the one place a key becomes words. A second language
 * is another map of the same shape. `navLabels.test.js` fails the build when the
 * backend catalogues name a key that is missing here.
 */
export const NAV_LABELS = {
  'nav.approvals': 'Approvals',
  'nav.approvals.definitions': 'Approval workflows',
  'nav.approvals.delegations': 'Delegations',
  'nav.approvals.inbox': 'Inbox',
  'nav.audit': 'Audit log',
  'nav.departments': 'Departments',
  'nav.designations': 'Designations',
  'nav.employeeInvitations': 'Employee invitations',
  'nav.employees': 'Employees',
  'nav.holidays': 'Holidays',
  'nav.hrms': 'HRMS',
  'nav.hrms.attendance': 'Attendance',
  'nav.hrms.attendance_log': 'Attendance log',
  'nav.hrms.attendance_settings': 'Attendance settings',
  'nav.hrms.dashboard': 'HR dashboard',
  'nav.hrms.my_work': 'My work',
  'nav.hrms.overtime_requests': 'Overtime requests',
  'nav.hrms.projects': 'Projects',
  'nav.hrms.regularizations': 'Regularisations',
  'nav.hrms.timesheet_review': 'Timesheet review',
  'nav.hrms.timesheets': 'Timesheets',
  'nav.leave': 'Leave',
  'nav.leave.allocations': 'Leave allocations',
  'nav.leave.import': 'Leave import',
  'nav.leave.requests': 'Leave requests',
  'nav.leave.types': 'Leave types',
  'nav.locations': 'Work locations',
  'nav.organisation': 'Organisation',
  'nav.payroll': 'Payroll',
  'nav.payroll.claims': 'Reimbursement claims',
  'nav.payroll.dashboard': 'Payroll dashboard',
  'nav.payroll.deductions': 'Deductions',
  'nav.payroll.prior_payroll': 'Prior payroll',
  'nav.payroll.runs': 'Payroll runs',
  'nav.people': 'People',
  'nav.roles': 'Roles',
  'nav.settings': 'Settings',
  'nav.settings.company': 'Company profile',
  'nav.setup': 'Setup',
  'nav.tenants': 'Tenants',
  'nav.userInvitations': 'User invitations',
};

const MODULE_PREFIXES = new Set(['nav', 'hrms', 'payroll', 'core']);

/**
 * The words for `labelKey`. A key not in the map still reads as words, never as a raw key:
 * `nav.hrms.shift_roster` → "Shift roster", `nav.payGroups` → "Pay groups". A value with no dot
 * is already text and is returned as it is.
 */
export function navLabel(labelKey) {
  if (!labelKey) return '';
  const known = NAV_LABELS[labelKey];
  if (known) return known;
  if (!labelKey.includes('.')) return labelKey;
  const segments = labelKey.split('.');
  const parts = segments.filter((p) => !MODULE_PREFIXES.has(p));
  // `nav.hrms` names only a module; its last segment is still a word.
  const words = (parts.length ? parts : segments.slice(-1))
    .join(' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : labelKey;
}
