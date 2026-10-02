import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { componentService } from './componentService.js';

export const fetchActiveComponents = createAsyncThunk(
  'salary/fetchActiveComponents',
  async (_, { getState }) => {
    const state = getState().salary;
    if (state?.loadedAt && Date.now() - state.loadedAt < 300000) {
      return state.components;
    }

    const [earnings, deductions, benefits, reimbursements] = await Promise.all([
      componentService.list('earnings', { activeOnly: true }),
      componentService.list('deductions', { activeOnly: true }),
      componentService.list('benefits', { activeOnly: true }),
      componentService.list('reimbursements', { activeOnly: true }),
    ]);

    return { earnings, deductions, benefits, reimbursements };
  }
);

const initialState = {
  components: {
    earnings: [],
    deductions: [],
    benefits: [],
    reimbursements: [],
  },
  loadedAt: null,
  loading: false,
  error: null,
};

export const salarySlice = createSlice({
  name: 'salary',
  initialState,
  reducers: {
    setComponents(state, action) {
      state.components = action.payload;
      state.loadedAt = Date.now();
      state.loading = false;
      state.error = null;
    },
    invalidateComponents(state) {
      state.loadedAt = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchActiveComponents.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchActiveComponents.fulfilled, (state, action) => {
        state.components = action.payload;
        state.loadedAt = Date.now();
        state.loading = false;
      })
      .addCase(fetchActiveComponents.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error?.message || 'Failed to load active components';
      });
  },
});

export const { setComponents, invalidateComponents } = salarySlice.actions;
export default salarySlice.reducer;
