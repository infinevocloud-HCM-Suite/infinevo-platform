import { createSlice } from '@reduxjs/toolkit';

/**
 * The one live impersonation session, or null (W-65.3 §5).
 *
 * Held in memory only - never in storage (W-45 §5b). A reload forgets it, and the server
 * expires it after 30 minutes regardless (W-65.2). The API client reads `session.sessionId`
 * through the store to send `X-Impersonation`; the shell header reads the rest for its banner.
 *
 * session: { sessionId, tenantId, tenantName, userLabel, expiresAt }
 */
const impersonationSlice = createSlice({
  name: 'impersonation',
  initialState: { session: null },
  reducers: {
    started(state, action) {
      const { sessionId, tenantId, tenantName, userLabel, expiresAt } = action.payload || {};
      state.session = sessionId
        ? { sessionId, tenantId, tenantName, userLabel, expiresAt }
        : null;
    },
    stopped(state) {
      state.session = null;
    },
  },
});

export const { started, stopped } = impersonationSlice.actions;
export default impersonationSlice.reducer;
