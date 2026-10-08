import { OverviewTab } from './tabs/OverviewTab.jsx';
import { SectionTab } from './tabs/SectionTab.jsx';
import { ReportingLineTab } from './tabs/ReportingLineTab.jsx';
import { DocumentsTab } from './tabs/DocumentsTab.jsx';

/**
 * Registerable tabs for EmployeePage (W-46.1 Decision 2).
 * Other feature tickets (W-47.1 for Salary, W-21 for Documents) can append tabs to this list.
 */
export const employeeTabs = [
  {
    key: 'overview',
    label: 'Overview',
    render: ({ employee, setEmployee }) => (
      <OverviewTab employee={employee} onUpdate={setEmployee} />
    ),
  },
  {
    key: 'personal',
    label: 'Personal',
    render: ({ employee }) => (
      <SectionTab employeeId={employee.id} sectionName="personal" />
    ),
  },
  {
    key: 'contact',
    label: 'Contact',
    render: ({ employee }) => (
      <SectionTab employeeId={employee.id} sectionName="contact" />
    ),
  },
  {
    key: 'identification',
    label: 'Identification',
    render: ({ employee }) => (
      <SectionTab employeeId={employee.id} sectionName="identification" />
    ),
  },
  {
    key: 'employment',
    label: 'Employment',
    render: ({ employee }) => (
      <SectionTab employeeId={employee.id} sectionName="employment" />
    ),
  },
  {
    key: 'bank',
    label: 'Bank',
    render: ({ employee }) => (
      <SectionTab employeeId={employee.id} sectionName="bank" />
    ),
  },
  {
    key: 'reporting-line',
    label: 'Reporting Line',
    render: ({ employee }) => (
      <ReportingLineTab employeeId={employee.id} />
    ),
  },
  {
    key: 'documents',
    label: 'Documents',
    render: ({ employee }) => (
      <DocumentsTab employeeId={employee.id} />
    ),
  },
];
