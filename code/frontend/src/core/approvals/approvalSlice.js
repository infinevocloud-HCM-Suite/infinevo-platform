import { createSlice } from '@reduxjs/toolkit';

const approvalSlice = createSlice({
  name: 'approvals',
  initialState: {
    pendingCount: 0,
  },
  reducers: {
    setPendingCount(state, action) {
      state.pendingCount = Math.max(0, action.payload ?? 0);
    },
    decrementPendingCount(state) {
      state.pendingCount = Math.max(0, state.pendingCount - 1);
    },
  },
});

export const { setPendingCount, decrementPendingCount } = approvalSlice.actions;
export default approvalSlice.reducer;
