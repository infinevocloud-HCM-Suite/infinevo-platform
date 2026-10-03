import { describe, it, expect } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import settingsReducer, {
  setPaySchedule,
  setLopPolicy,
  setEpf,
  setEsi,
  setFbpPlan,
  resetSettings,
} from './settingsSlice.js';

describe('settingsSlice (W-47.1b §7)', () => {
  it('stores and updates settings in Redux state', () => {
    const store = configureStore({
      reducer: { settings: settingsReducer },
    });

    expect(store.getState().settings.paySchedule).toBeNull();

    store.dispatch(setPaySchedule({ exists: true, workingDays: [1, 2, 3, 4, 5] }));
    expect(store.getState().settings.paySchedule.workingDays).toEqual([1, 2, 3, 4, 5]);

    store.dispatch(setLopPolicy({ workingDayBasis: 'ACTUAL_DAYS' }));
    expect(store.getState().settings.lopPolicy.workingDayBasis).toBe('ACTUAL_DAYS');

    store.dispatch(setEpf({ isEnabled: true, source: 'DEFAULT' }));
    expect(store.getState().settings.epf.source).toBe('DEFAULT');

    store.dispatch(setEsi({ isEnabled: true }));
    expect(store.getState().settings.esi.isEnabled).toBe(true);

    store.dispatch(setFbpPlan({ isEnabled: true, isLocked: false }));
    expect(store.getState().settings.fbpPlan.isEnabled).toBe(true);

    store.dispatch(resetSettings());
    expect(store.getState().settings.paySchedule).toBeNull();
  });
});
