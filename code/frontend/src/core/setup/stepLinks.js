/**
 * Mapping of setup step codes to target application routes and required permissions.
 *
 * Payroll codes (PAY_SCHEDULE, SALARY_COMPONENTS, EPF, ESI, PROFESSIONAL_TAX)
 * receive their destination paths when W-47 ships.
 */
export const stepLinks = {
  WORK_LOCATION: '/org/work-locations',
  EMPLOYEE: '/employees',
};

export const stepActions = {
  WORK_LOCATION: 'core.org.read',
  EMPLOYEE: 'core.employee.read',
};
