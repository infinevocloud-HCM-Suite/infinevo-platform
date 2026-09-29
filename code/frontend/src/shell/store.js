import { configureStore } from '@reduxjs/toolkit';
import taxReducer from '@payroll/tax/taxSlice';

// Redux Toolkit, carried forward from the Payroll frontend.
// Module slices register here as they are built.
export const store = configureStore({
  reducer: {
    tax: taxReducer,
  },
});
