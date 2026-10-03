import { describe, it, expect } from 'vitest';
import leaveReducer, { setTypes, invalidateTypes } from './leaveSlice.js';

describe('leaveSlice', () => {
  it('handles setTypes and sets loadedAt', () => {
    const initialState = { types: [], loadedAt: null };
    const dummyTypes = [{ id: 'type-1', name: 'Annual Leave' }];
    const nextState = leaveReducer(initialState, setTypes(dummyTypes));
    expect(nextState.types).toEqual(dummyTypes);
    expect(nextState.loadedAt).toBeTypeOf('number');
  });

  it('handles invalidateTypes', () => {
    const activeState = { types: [{ id: 'type-1' }], loadedAt: 123456789 };
    const nextState = leaveReducer(activeState, invalidateTypes());
    expect(nextState.types).toEqual([]);
    expect(nextState.loadedAt).toBeNull();
  });
});
