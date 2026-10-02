import { createSlice } from '@reduxjs/toolkit';
import { payrunService } from './payrunService.js';

let pollingTimer = null;

const initialState = {
  byId: {},
  polling: {
    id: null,
    active: false,
  },
};

const payrunSlice = createSlice({
  name: 'payrun',
  initialState,
  reducers: {
    setRun(state, action) {
      if (action.payload?.id) {
        state.byId[action.payload.id] = action.payload;
      }
    },
    setRuns(state, action) {
      const runs = action.payload || [];
      runs.forEach((r) => {
        if (r?.id) {
          state.byId[r.id] = r;
        }
      });
    },
    setPollingState(state, action) {
      state.polling.id = action.payload.id;
      state.polling.active = action.payload.active;
    },
    clearPolling(state) {
      state.polling.id = null;
      state.polling.active = false;
    },
  },
});

export const { setRun, setRuns, setPollingState, clearPolling } = payrunSlice.actions;

/**
 * Thunk to cancel any active polling interval and reset polling state.
 */
export const stopPolling = () => (dispatch) => {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
  dispatch(clearPolling());
};

/**
 * Thunk to start polling a pay run every 3 seconds while status === 'COMPUTING'.
 * Stops automatically when status changes to anything other than 'COMPUTING' (e.g. COMPUTED or FAILED).
 */
export const startPolling = (id) => (dispatch) => {
  // Clear any existing polling interval first
  dispatch(stopPolling());

  const poll = async () => {
    try {
      const run = await payrunService.get(id);
      dispatch(setRun(run));
      if (!run || run.status !== 'COMPUTING') {
        dispatch(stopPolling());
      }
    } catch {
      dispatch(stopPolling());
    }
  };

  // Immediate check
  poll();

  pollingTimer = setInterval(poll, 3000);
  dispatch(setPollingState({ id, active: true }));
};

export default payrunSlice.reducer;
