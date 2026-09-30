import { configureStore } from '@reduxjs/toolkit';
import payroll from '@payroll';

// Redux Toolkit, carried forward from the Payroll frontend.
// Module slices register here via their module entrypoint as they are built.
export const store = configureStore({
  reducer: {
    tax: payroll.reducer,
  },
});
