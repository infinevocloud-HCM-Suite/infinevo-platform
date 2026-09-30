import { describe, it, expect } from 'vitest';
import { store, createStore } from './store.js';

describe('shell store', () => {
  it('creates a valid Redux store instance', () => {
    expect(store).toBeDefined();
    expect(typeof store.dispatch).toBe('function');
    expect(typeof store.getState).toBe('function');
  });

  it('includes reducers exported by module index or extraReducers', () => {
    const dummyReducer = (state = { initialized: true }) => state;
    const customStore = createStore({ customModule: dummyReducer });

    const state = customStore.getState();
    expect(state.customModule).toEqual({ initialized: true });
  });
});
