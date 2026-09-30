import React from 'react';
import { EmployeeList } from './employee/EmployeeList.jsx';
import { EmployeeCreate } from './employee/EmployeeCreate.jsx';
import { EmployeePage } from './employee/EmployeePage.jsx';
import employeeReducer from './employee/employeeSlice.js';

// Employee, leave, holidays, organisation setup. Available to every tenant.
// Screens and slices land here as their work items are built.
export const routes = [
  { path: '/employees', element: React.createElement(EmployeeList) },
  { path: '/employees/new', element: React.createElement(EmployeeCreate) },
  { path: '/employees/:id', element: React.createElement(EmployeePage) },
];

export const reducers = {
  employee: employeeReducer,
};
