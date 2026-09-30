import { configureStore } from '@reduxjs/toolkit';
import { reducers as coreReducers } from '../core/index.js';
import { reducers as hrmsReducers } from '../hrms/index.js';
import { reducers as payrollReducers } from '../payroll/index.js';

/**
 * Global Redux store (W-45 §5).
 * Module slices register here as they are built.
 */
export function getModuleReducers() {
  return {
    ...(coreReducers || {}),
    ...(hrmsReducers || {}),
    ...(payrollReducers || {}),
  };
}

export function createStore(extraReducers = {}) {
  const allReducers = {
    ...getModuleReducers(),
    ...extraReducers,
  };

  // If no module slices have registered yet, provide a fallback reducer
  const reducerMap = Object.keys(allReducers).length > 0
    ? allReducers
    : { _empty: (state = {}) => state };

  return configureStore({
    reducer: reducerMap,
  });
}

export const store = createStore();
