import React from 'react';
import { EmployeeList } from './employee/EmployeeList.jsx';
import { EmployeeCreate } from './employee/EmployeeCreate.jsx';
import { EmployeePage } from './employee/EmployeePage.jsx';
import employeeReducer from './employee/employeeSlice.js';

import { Departments } from './org/Departments.jsx';
import { Designations } from './org/Designations.jsx';
import { WorkLocations } from './org/WorkLocations.jsx';
import { WorkLocationForm } from './org/WorkLocationForm.jsx';

// Employee, leave, holidays, organisation setup. Available to every tenant.
// Screens and slices land here as their work items are built.
export const routes = [
  { path: '/employees', element: React.createElement(EmployeeList) },
  { path: '/employees/new', element: React.createElement(EmployeeCreate) },
  { path: '/employees/:id', element: React.createElement(EmployeePage) },
  { path: '/org/departments', element: React.createElement(Departments) },
  { path: '/org/designations', element: React.createElement(Designations) },
  { path: '/org/work-locations', element: React.createElement(WorkLocations) },
  { path: '/org/work-locations/new', element: React.createElement(WorkLocationForm) },
  { path: '/org/work-locations/:id/edit', element: React.createElement(WorkLocationForm) },
];

export const reducers = {
  employee: employeeReducer,
};
