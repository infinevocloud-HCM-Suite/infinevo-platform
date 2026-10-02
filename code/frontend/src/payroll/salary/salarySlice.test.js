import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import salaryReducer, {
  fetchActiveComponents,
  invalidateComponents,
  setComponents,
} from './salarySlice.js';
import { componentService } from './componentService.js';

vi.mock('./componentService.js', () => ({
  componentService: {
    list: vi.fn(),
  },
}));

describe('salarySlice (W-47.1a §7)', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        salary: salaryReducer,
      },
    });
  });

  it('initial state has empty components and loadedAt is null', () => {
    const state = store.getState().salary;
    expect(state.components).toEqual({
      earnings: [],
      deductions: [],
      benefits: [],
      reimbursements: [],
    });
    expect(state.loadedAt).toBeNull();
  });

  it('fetchActiveComponents caches catalogue once and does not refetch if already loaded', async () => {
    const mockEarnings = [{ id: 'e1', code: 'BASIC' }];
    const mockDeductions = [{ id: 'd1', code: 'PF' }];
    const mockBenefits = [{ id: 'b1', code: 'MED' }];
    const mockReimbursements = [{ id: 'r1', code: 'FUEL' }];

    componentService.list
      .mockResolvedValueOnce(mockEarnings)
      .mockResolvedValueOnce(mockDeductions)
      .mockResolvedValueOnce(mockBenefits)
      .mockResolvedValueOnce(mockReimbursements);

    // First fetch
    await store.dispatch(fetchActiveComponents());

    expect(componentService.list).toHaveBeenCalledTimes(4);
    let state = store.getState().salary;
    expect(state.components.earnings).toEqual(mockEarnings);
    expect(state.loadedAt).not.toBeNull();

    // Second fetch should use cached state and not call componentService.list again
    await store.dispatch(fetchActiveComponents());
    expect(componentService.list).toHaveBeenCalledTimes(4);
  });

  it('invalidateComponents resets loadedAt so subsequent fetch refetches', async () => {
    store.dispatch(
      setComponents({
        earnings: [{ id: 'e1' }],
        deductions: [],
        benefits: [],
        reimbursements: [],
      })
    );

    let state = store.getState().salary;
    expect(state.loadedAt).not.toBeNull();

    store.dispatch(invalidateComponents());

    state = store.getState().salary;
    expect(state.loadedAt).toBeNull();

    componentService.list.mockResolvedValue([]);
    await store.dispatch(fetchActiveComponents());
    expect(componentService.list).toHaveBeenCalledTimes(4);
  });
});
