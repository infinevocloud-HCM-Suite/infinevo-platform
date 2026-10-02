import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import payrunReducer, {
  startPolling,
  stopPolling,
} from './payrunSlice.js';
import { payrunService } from './payrunService.js';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    get: vi.fn(),
  },
}));

describe('payrunSlice polling (W-47.2 §7)', () => {
  let store;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        payrun: payrunReducer,
      },
    });
  });

  afterEach(() => {
    store.dispatch(stopPolling());
    vi.useRealTimers();
  });

  it('starts polling on COMPUTING and updates byId', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-1',
      status: 'COMPUTING',
      progress_done: 1,
      progress_total: 10,
    });

    store.dispatch(startPolling('run-1'));
    await Promise.resolve();

    expect(payrunService.get).toHaveBeenCalledWith('run-1');
    expect(store.getState().payrun.byId['run-1']).toBeDefined();
    expect(store.getState().payrun.byId['run-1'].progress_done).toBe(1);
    expect(store.getState().payrun.polling.id).toBe('run-1');
    expect(store.getState().payrun.polling.active).toBe(true);
  });

  it('stops polling automatically when status changes to COMPUTED', async () => {
    // 1st tick: COMPUTING
    payrunService.get.mockResolvedValueOnce({
      id: 'run-1',
      status: 'COMPUTING',
      progress_done: 5,
      progress_total: 10,
    });
    // 2nd tick (after 3000ms): COMPUTED
    payrunService.get.mockResolvedValueOnce({
      id: 'run-1',
      status: 'COMPUTED',
      progress_done: 10,
      progress_total: 10,
    });

    store.dispatch(startPolling('run-1'));
    await Promise.resolve();
    expect(store.getState().payrun.polling.id).toBe('run-1');

    // Advance by 3000ms to trigger next poll
    await vi.advanceTimersByTimeAsync(3000);

    expect(payrunService.get).toHaveBeenCalledTimes(2);
    expect(store.getState().payrun.byId['run-1'].status).toBe('COMPUTED');
    // Polling has stopped and active state reset
    expect(store.getState().payrun.polling.id).toBeNull();
    expect(store.getState().payrun.polling.active).toBe(false);

    // Advancing further triggers no more calls
    await vi.advanceTimersByTimeAsync(3000);
    expect(payrunService.get).toHaveBeenCalledTimes(2);
  });

  it('stops polling automatically when status changes to FAILED', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-2',
      status: 'COMPUTING',
      progress_done: 2,
      progress_total: 10,
    });
    payrunService.get.mockResolvedValueOnce({
      id: 'run-2',
      status: 'FAILED',
      failure_reason: 'Worker timeout',
    });

    store.dispatch(startPolling('run-2'));
    await Promise.resolve();

    await vi.advanceTimersByTimeAsync(3000);

    expect(payrunService.get).toHaveBeenCalledTimes(2);
    expect(store.getState().payrun.byId['run-2'].status).toBe('FAILED');
    expect(store.getState().payrun.polling.id).toBeNull();
    expect(store.getState().payrun.polling.active).toBe(false);
  });

  it('clears active polling timer on stopPolling (simulating unmount)', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-3',
      status: 'COMPUTING',
      progress_done: 1,
      progress_total: 5,
    });

    store.dispatch(startPolling('run-3'));
    await Promise.resolve();

    expect(store.getState().payrun.polling.id).toBe('run-3');

    // Simulate unmount dispatching stopPolling
    store.dispatch(stopPolling());

    expect(store.getState().payrun.polling.id).toBeNull();
    expect(store.getState().payrun.polling.active).toBe(false);

    // Advancing time triggers no further get calls
    await vi.advanceTimersByTimeAsync(6000);
    expect(payrunService.get).toHaveBeenCalledTimes(1);
  });
});
