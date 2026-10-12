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
    // Shaped as FbpPlanResponse / FbpComponentResponse (payroll/fbp) serialise them.
    fbpService.plan.mockResolvedValue({
      id: 'plan-1',
      exists: true,
      isEnabled: true,
      windowOpensOn: '2026-04-01',
      windowClosesOn: '2026-04-30',
      isLocked: false,
      lockedAt: null,
      notifyOnRelease: true,
      notifyOnLock: false,
      reminderDaysBeforeClose: [15, 7, 3, 1],
    });

    fbpService.components.mockResolvedValue([
      {
        kind: 'EARNING',
        id: 'comp-fbp-1',
        code: 'FUEL_ALLOW',
        name: 'Fuel Allowance',
        maxLimit: 25000,
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

  it('D-67: an enabled plan from the server reads as Enabled, with its dates and limits loaded', async () => {
    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('fbp-status-tag').textContent).toBe('Enabled');
    });
    expect(screen.getByTestId('fbp-enabled-switch').getAttribute('aria-checked')).toBe('true');
    expect(screen.getByDisplayValue('2026-04-01')).toBeDefined();
    expect(screen.getByDisplayValue('2026-04-30')).toBeDefined();
    expect(screen.getByText('₹25000')).toBeDefined();
  });

  it('D-67: a locked plan reads as Locked and offers Unlock', async () => {
    fbpService.plan.mockResolvedValue({
      id: 'plan-1',
      exists: true,
      isEnabled: true,
      windowOpensOn: '2026-04-01',
      windowClosesOn: '2026-04-30',
      isLocked: true,
      lockedAt: '2026-04-30T18:00:00Z',
      notifyOnRelease: true,
      notifyOnLock: true,
      reminderDaysBeforeClose: [5, 1],
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('fbp-status-tag').textContent).toBe('Locked');
    });
    expect(screen.getByText('Unlock Plan')).toBeDefined();
  });

  it('lock button calls /lock and flips state', async () => {
    fbpService.lock.mockResolvedValue({
      id: 'plan-1',
      exists: true,
      isEnabled: true,
      windowOpensOn: '2026-04-01',
      windowClosesOn: '2026-04-30',
      isLocked: true,
      lockedAt: '2026-04-30T18:00:00Z',
      notifyOnRelease: true,
      notifyOnLock: false,
      reminderDaysBeforeClose: [15, 7, 3, 1],
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('lock-fbp-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('lock-fbp-button'));

    await waitFor(() => {
      expect(fbpService.lock).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(screen.getByTestId('fbp-status-tag').textContent).toBe('Locked');
    });
  });

  it('D-67: savePlan sends the FbpPlanRequest fields, reminder days as an integer array', async () => {
    fbpService.savePlan.mockResolvedValue({
      id: 'plan-1',
      exists: true,
      isEnabled: true,
      windowOpensOn: '2026-04-01',
      windowClosesOn: '2026-04-30',
      isLocked: false,
      lockedAt: null,
      notifyOnRelease: true,
      notifyOnLock: false,
      reminderDaysBeforeClose: [15, 7, 3, 1],
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
    expect(payload).toEqual({
      isEnabled: true,
      windowOpensOn: '2026-04-01',
      windowClosesOn: '2026-04-30',
      notifyOnRelease: true,
      notifyOnLock: false,
      reminderDaysBeforeClose: [15, 7, 3, 1],
    });
    payload.reminderDaysBeforeClose.forEach((d) => expect(typeof d).toBe('number'));
  });
});
