/**
 * Mapping of setup step codes to target application routes and required permissions.
 *
 * Every code in the backend's SetupStepCatalogue has an entry (D-65). A step's action is the one
 * the navigation feed asks for before it mounts the step's route, so Open never leads to Not found.
 */
export const stepLinks = {
  WORK_LOCATION: '/org/work-locations',
  EMPLOYEE: '/employees',
  PAY_SCHEDULE: '/payroll/settings/pay-schedule',
  PRIOR_PAYROLL: '/payroll/prior-payroll',
  SALARY_COMPONENTS: '/payroll/components',
  EPF: '/payroll/settings/epf',
  ESI: '/payroll/settings/esi',
  PROFESSIONAL_TAX: '/payroll/settings/professional-tax',
};

export const stepActions = {
  WORK_LOCATION: 'core.org.read',
  EMPLOYEE: 'core.employee.read',
  PAY_SCHEDULE: 'payroll.settings.manage',
  PRIOR_PAYROLL: 'payroll.run.read',
  SALARY_COMPONENTS: 'payroll.structure.read',
  EPF: 'payroll.settings.manage',
  ESI: 'payroll.settings.manage',
  PROFESSIONAL_TAX: 'payroll.settings.manage',
};
