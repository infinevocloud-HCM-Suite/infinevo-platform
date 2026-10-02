import { createSlice } from '@reduxjs/toolkit';

/**
 * Tax declaration state (W-47.3 §5): `{ fy, header, sections, items }`.
 *
 * A section is loaded on first expand (its component finds `sections[name]` empty and fetches)
 * and replaced by its save response. Changing `fy` drops the header and every section so the
 * next expand re-reads them. Nothing here is written to browser storage (W-45 §5b).
 *
 * `items` is the Section 6A catalogue, which the server filters by regime; `itemsRegime` names
 * the regime it was read for, so a regime switch is noticed and the catalogue re-read.
 */
const emptySections = () => ({
  housing: null,
  deductions: null,
  otherIncome: null,
  summary: null,
});

const initialState = {
  fy: null,
  header: null,
  sections: emptySections(),
  items: [],
  itemsRegime: null,
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
        state.sections = emptySections();
        state.items = [];
        state.itemsRegime = null;
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
      const payload = action.payload;
      if (Array.isArray(payload) || payload == null) {
        state.items = payload || [];
        return;
      }
      state.items = payload.items || [];
      state.itemsRegime = payload.regime ?? null;
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

export const selectTaxFy = (state) => state.tax?.fy ?? null;
export const selectTaxHeader = (state) => state.tax?.header ?? null;
export const selectTaxSection = (section) => (state) => state.tax?.sections?.[section] ?? null;
const NO_ITEMS = [];
export const selectTaxItems = (state) => state.tax?.items ?? NO_ITEMS;
export const selectTaxItemsRegime = (state) => state.tax?.itemsRegime ?? null;

export default taxSlice.reducer;
