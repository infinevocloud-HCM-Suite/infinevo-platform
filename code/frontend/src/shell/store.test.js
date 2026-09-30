import { describe, it, expect } from 'vitest';
import { store, createStore } from './store.js';

describe('shell store', () => {
  it('creates a valid Redux store instance', () => {
    expect(store).toBeDefined();
    expect(typeof store.dispatch).toBe('function');
    expect(typeof store.getState).toBe('function');
  });

  it('includes reducers exported by core/index.js in the built store', () => {
    const state = store.getState();
    expect(state.employee).toBeDefined();
  });

  it('supports injecting extra reducers into createStore', () => {
    const dummyReducer = (state = { initialized: true }) => state;
    const customStore = createStore({ customModule: dummyReducer });

    const state = customStore.getState();
    expect(state.customModule).toEqual({ initialized: true });
    expect(state.employee).toBeDefined();
  });
});
