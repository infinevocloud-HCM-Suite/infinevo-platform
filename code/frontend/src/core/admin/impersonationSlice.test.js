import { describe, it, expect } from 'vitest';
import reducer, { started, stopped } from './impersonationSlice.js';

const session = {
  sessionId: 'sess-1',
  tenantId: 'tenant-1',
  tenantName: 'Globex',
  userLabel: 'admin@globex.local',
  expiresAt: '2026-10-03T10:30:00Z',
};

describe('impersonationSlice', () => {
  it('starts with no session', () => {
    expect(reducer(undefined, { type: '@@init' })).toEqual({ session: null });
  });

  it('started stores the session', () => {
    const state = reducer(undefined, started(session));
    expect(state.session).toEqual(session);
  });

  it('started keeps only the five session fields', () => {
    const state = reducer(undefined, started({ ...session, extra: 'x' }));
    expect(state.session).toEqual(session);
  });

  it('stopped clears it', () => {
    const live = reducer(undefined, started(session));
    expect(reducer(live, stopped()).session).toBeNull();
  });

  it('uses the action types the shell dispatches by name', () => {
    expect(started.type).toBe('impersonation/started');
    expect(stopped.type).toBe('impersonation/stopped');
  });
});
