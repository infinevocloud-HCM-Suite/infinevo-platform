import { describe, it, expect } from 'vitest';
import approvalReducer, {
  setPendingCount,
  decrementPendingCount,
} from './approvalSlice.js';

describe('approvalSlice', () => {
  it('handles setPendingCount and decrementPendingCount', () => {
    let state = approvalReducer(undefined, { type: '@@INIT' });
    expect(state.pendingCount).toBe(0);

    state = approvalReducer(state, setPendingCount(5));
    expect(state.pendingCount).toBe(5);

    state = approvalReducer(state, decrementPendingCount());
    expect(state.pendingCount).toBe(4);

    state = approvalReducer(state, setPendingCount(-1));
    expect(state.pendingCount).toBe(0);

    state = approvalReducer(state, decrementPendingCount());
    expect(state.pendingCount).toBe(0);
  });
});
