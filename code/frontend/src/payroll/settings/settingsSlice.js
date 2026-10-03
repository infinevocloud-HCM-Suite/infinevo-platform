import { createSlice } from '@reduxjs/toolkit';

const initialState = {
  paySchedule: null,
  lopPolicy: null,
  epf: null,
  esi: null,
  fbpPlan: null,
  loadedAt: null,
};

export const settingsSlice = createSlice({
  name: 'settings',
  initialState,
  reducers: {
    setPaySchedule(state, action) {
      state.paySchedule = action.payload;
    },
    setLopPolicy(state, action) {
      state.lopPolicy = action.payload;
    },
    setEpf(state, action) {
      state.epf = action.payload;
    },
    setEsi(state, action) {
      state.esi = action.payload;
    },
    setFbpPlan(state, action) {
      state.fbpPlan = action.payload;
    },
    resetSettings() {
      return initialState;
    },
  },
});

export const {
  setPaySchedule,
  setLopPolicy,
  setEpf,
  setEsi,
  setFbpPlan,
  resetSettings,
} = settingsSlice.actions;

export default settingsSlice.reducer;
