import { createSlice } from '@reduxjs/toolkit';

const initialMasters = {
  departments: {},
  designations: {},
  workLocations: {},
  raw: {
    departments: [],
    designations: [],
    workLocations: [],
  },
};

const employeeSlice = createSlice({
  name: 'employee',
  initialState: {
    masters: initialMasters,
    loadedAt: null,
  },
  reducers: {
    setMasters(state, action) {
      state.masters = {
        departments: action.payload.departments || {},
        designations: action.payload.designations || {},
        workLocations: action.payload.workLocations || {},
        raw: action.payload.raw || initialMasters.raw,
      };
      state.loadedAt = Date.now();
    },
    invalidateMasters(state) {
      state.masters = initialMasters;
      state.loadedAt = null;
    },
  },
});

export const { setMasters, invalidateMasters } = employeeSlice.actions;
export default employeeSlice.reducer;
