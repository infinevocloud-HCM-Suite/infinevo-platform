import { createSlice } from '@reduxjs/toolkit';

const initialState = {
  fy: null,
  header: null,
  sections: {
    housing: null,
    deductions: null,
    otherIncome: null,
    summary: null,
  },
  items: [],
  loading: false,
  error: null,
};

export const taxSlice = createSlice({
  name: 'tax',
  initialState,
  reducers: {
    setFy(state, action) {
      if (state.fy !== action.payload) {
        state.fy = action.payload;
        state.header = null;
        state.sections = {
          housing: null,
          deductions: null,
          otherIncome: null,
          summary: null,
        };
        state.error = null;
      }
    },
    setHeader(state, action) {
      state.header = action.payload;
    },
    setSectionData(state, action) {
      const { section, data } = action.payload;
      if (section && state.sections[section] !== undefined) {
        state.sections[section] = data;
      }
    },
    setItems(state, action) {
      state.items = action.payload || [];
    },
    setLoading(state, action) {
      state.loading = Boolean(action.payload);
    },
    setError(state, action) {
      state.error = action.payload;
    },
    resetTaxState() {
      return initialState;
    },
  },
});

export const {
  setFy,
  setHeader,
  setSectionData,
  setItems,
  setLoading,
  setError,
  resetTaxState,
} = taxSlice.actions;

export default taxSlice.reducer;
