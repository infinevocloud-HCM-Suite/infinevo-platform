import { employeeTabs as payrollEmployeeTabs } from '../payroll/index.js';

/**
 * The modules' employee page tabs, tagged with the module that owns them (D-66). AppShell hands
 * them to core's EmployeePage through ModuleEmployeeTabsProvider. HRMS contributes none yet; when
 * it exports `employeeTabs`, add them here tagged 'HRMS'.
 */
export const moduleEmployeeTabs = [
  ...(payrollEmployeeTabs || []).map((tab) => ({ ...tab, module: 'PAYROLL' })),
];
