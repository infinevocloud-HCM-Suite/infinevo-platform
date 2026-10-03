import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { FbpPlanScreen } from './FbpPlanScreen.jsx';
import { fbpService } from './fbpService.js';
import settingsReducer from './settingsSlice.js';

vi.mock('./fbpService.js', () => ({
  fbpService: {
    plan: vi.fn(),
    savePlan: vi.fn(),
    lock: vi.fn(),
    unlock: vi.fn(),
    components: vi.fn(),
  },
}));

function renderScreen() {
  const store = configureStore({
    reducer: {
      settings: settingsReducer,
    },
  });

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <FbpPlanScreen />
      </MemoryRouter>
    </Provider>
  );
}

describe('FbpPlanScreen (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fbpService.plan.mockResolvedValue({
      exists: true,
      is_enabled: true,
      window_opens_on: '2026-04-01',
      window_closes_on: '2026-04-30',
      notify_on_release: true,
      notify_on_lock: false,
      reminder_days_before_close: [15, 7, 3, 1],
      is_locked: false,
      locked_at: null,
    });

    fbpService.components.mockResolvedValue([
      {
        id: 'comp-fbp-1',
        kind: 'EARNING',
        code: 'FUEL_ALLOW',
        name: 'Fuel Allowance',
        max_limit: 25000,
      },
    ]);
  });

  it('renders FBP plan details and components table', async () => {
    renderScreen();

    await waitFor(() => {
      expect(screen.getByText('Flexible Benefit Plan (FBP) Configuration')).toBeDefined();
      expect(screen.getByTestId('fbp-status-tag')).toBeDefined();
      expect(screen.getByText('Fuel Allowance')).toBeDefined();
      expect(screen.getByText('Manage in Salary Components')).toBeDefined();
    });
  });

  it('lock button calls /lock and flips state', async () => {
    fbpService.lock.mockResolvedValue({
      is_locked: true,
      locked_at: '2026-04-30T18:00:00Z',
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('lock-fbp-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('lock-fbp-button'));

    await waitFor(() => {
      expect(fbpService.lock).toHaveBeenCalledTimes(1);
    });
  });

  it('savePlan sends reminder_days_before_close as an integer array', async () => {
    fbpService.savePlan.mockResolvedValue({
      is_enabled: true,
      window_opens_on: '2026-04-01',
      window_closes_on: '2026-04-30',
      reminder_days_before_close: [15, 7, 3, 1],
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('save-fbp-plan-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-fbp-plan-button'));

    await waitFor(() => {
      expect(fbpService.savePlan).toHaveBeenCalledTimes(1);
    });

    const payload = fbpService.savePlan.mock.calls[0][0];
    expect(Array.isArray(payload.reminder_days_before_close)).toBe(true);
    expect(payload.reminder_days_before_close).toEqual([15, 7, 3, 1]);
    payload.reminder_days_before_close.forEach((d) => expect(typeof d).toBe('number'));
  });
});
