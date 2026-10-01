import { describe, it, expect } from 'vitest';
import employeeReducer, { setMasters, invalidateMasters } from './employeeSlice.js';

describe('employeeSlice', () => {
  const initial = {
    masters: {
      departments: {},
      designations: {},
      workLocations: {},
      raw: {
        departments: [],
        designations: [],
        workLocations: [],
      },
    },
    loadedAt: null,
  };

  it('stores masters and records loadedAt timestamp', () => {
    const payload = {
      departments: { 'd-1': 'Engineering' },
      designations: { 'ds-1': 'Lead Engineer' },
      workLocations: { 'w-1': 'Bengaluru HQ' },
      raw: {
        departments: [{ id: 'd-1', name: 'Engineering' }],
        designations: [{ id: 'ds-1', name: 'Lead Engineer' }],
        workLocations: [{ id: 'w-1', name: 'Bengaluru HQ' }],
      },
    };

    const state = employeeReducer(initial, setMasters(payload));

    expect(state.masters.departments).toEqual({ 'd-1': 'Engineering' });
    expect(state.masters.designations).toEqual({ 'ds-1': 'Lead Engineer' });
    expect(state.masters.workLocations).toEqual({ 'w-1': 'Bengaluru HQ' });
    expect(state.loadedAt).toBeTypeOf('number');
  });

  it('invalidateMasters clears masters and resets loadedAt to null', () => {
    const loadedState = {
      masters: {
        departments: { 'd-1': 'Engineering' },
        designations: {},
        workLocations: {},
        raw: { departments: [], designations: [], workLocations: [] },
      },
      loadedAt: Date.now(),
    };

    const nextState = employeeReducer(loadedState, invalidateMasters());

    expect(nextState.masters.departments).toEqual({});
    expect(nextState.loadedAt).toBeNull();
  });
});
