import { employeeTabs as payrollEmployeeTabs } from '../payroll/index.js';
import { employeeTabs as hrmsEmployeeTabs } from '../hrms/index.js';

/**
 * The modules' employee page tabs, tagged with the module that owns them (D-66). AppShell hands
 * them to core's EmployeePage through ModuleEmployeeTabsProvider.
 */
export const moduleEmployeeTabs = [
  ...(payrollEmployeeTabs || []).map((tab) => ({ ...tab, module: 'PAYROLL' })),
  ...(hrmsEmployeeTabs || []).map((tab) => ({ ...tab, module: 'HRMS' })),
];
