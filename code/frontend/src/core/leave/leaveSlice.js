import { createSlice } from '@reduxjs/toolkit';

const leaveSlice = createSlice({
  name: 'leave',
  initialState: {
    types: [],
    loadedAt: null,
  },
  reducers: {
    setTypes(state, action) {
      state.types = action.payload || [];
      state.loadedAt = Date.now();
    },
    invalidateTypes(state) {
      state.types = [];
      state.loadedAt = null;
    },
  },
});

export const { setTypes, invalidateTypes } = leaveSlice.actions;
export default leaveSlice.reducer;
