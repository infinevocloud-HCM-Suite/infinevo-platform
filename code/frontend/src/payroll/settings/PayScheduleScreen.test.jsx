import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { PayScheduleScreen } from './PayScheduleScreen.jsx';
import { payScheduleService } from './payScheduleService.js';
import { lopPolicyService } from './lopPolicyService.js';
import settingsReducer from './settingsSlice.js';

vi.mock('./payScheduleService.js', () => ({
  payScheduleService: {
    get: vi.fn(),
    save: vi.fn(),
    period: vi.fn(),
  },
}));

vi.mock('./lopPolicyService.js', () => ({
  lopPolicyService: {
    get: vi.fn(),
    save: vi.fn(),
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
      <PayScheduleScreen />
    </Provider>
  );
}

// Shaped as core/lop/LopPolicyResponse serialises it (camelCase, D-68).
const LOP_POLICY = {
  id: 'lop-1',
  tenantId: 'tenant-1',
  workingDayBasis: 'ORG_DAYS',
  configuredDaysPerMonth: 26,
  weekendsPayable: false,
  holidaysPayable: true,
  lopRounding: 'HALF_UP_0',
  effectiveFrom: '2026-11-01',
};

describe('PayScheduleScreen (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    payScheduleService.get.mockResolvedValue({
      exists: true,
      working_days: [1, 2, 3, 4, 5],
      pay_day_rule: 'LAST_DAY_OF_PERIOD',
      input_cutoff_day: 25,
      first_period_start: '2026-04-01',
    });

    lopPolicyService.get.mockResolvedValue(LOP_POLICY);

    payScheduleService.period.mockResolvedValue({
      start: '2026-10-01',
      end: '2026-10-31',
      cutoff_date: '2026-10-25',
      pay_date: '2026-10-31',
    });
  });

  it('renders initial schedule and LOP policy cards', async () => {
    renderScreen();

    await waitFor(() => {
      expect(screen.getByText('Pay Schedule')).toBeDefined();
      expect(screen.getByText('Loss of Pay (LOP) Basis')).toBeDefined();
      expect(screen.getByText('Period Preview')).toBeDefined();
    });
  });

  it('schedule save sends working_days as ISO numbers and only schedule keys', async () => {
    payScheduleService.save.mockResolvedValue({
      working_days: [1, 2, 3, 4, 5],
      pay_day_rule: 'LAST_DAY_OF_PERIOD',
      input_cutoff_day: 25,
      first_period_start: '2026-04-01',
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('save-schedule-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-schedule-button'));

    await waitFor(() => {
      expect(payScheduleService.save).toHaveBeenCalledTimes(1);
    });

    const payload = payScheduleService.save.mock.calls[0][0];
    expect(payload.working_days).toEqual([1, 2, 3, 4, 5]);
    payload.working_days.forEach((day) => expect(typeof day).toBe('number'));
    expect(payload.pay_day_rule).toBe('LAST_DAY_OF_PERIOD');
    expect(payload.input_cutoff_day).toBe(25);
    expect(payload.first_period_start).toBe('2026-04-01');

    // Ensure LOP fields are NOT in schedule payload
    expect(payload.working_day_basis).toBeUndefined();
    expect(payload.weekends_payable).toBeUndefined();
    expect(lopPolicyService.save).not.toHaveBeenCalled();
  });

  it('D-68: shows the loss-of-pay policy the server returns', async () => {
    renderScreen();

    // The configured-days field only shows for ORG_DAYS, so its value proves the basis was read.
    await waitFor(() => {
      expect(screen.getByTestId('lop-configured-days-input')).toBeDefined();
    });
    const configured = screen.getByTestId('lop-configured-days-input');
    expect(Number((configured.tagName === 'INPUT' ? configured : configured.querySelector('input')).value)).toBe(26);
    expect(screen.getByTestId('lop-weekends-switch').getAttribute('aria-checked')).toBe('false');
    expect(screen.getByTestId('lop-holidays-switch').getAttribute('aria-checked')).toBe('true');
    expect(screen.getByDisplayValue('2026-11-01')).toBeDefined();
    expect(screen.getByTitle('Half Up (Integer)')).toBeDefined();
  });

  it('D-68: basis save sends the LopPolicyRequest fields only, independent of schedule save', async () => {
    lopPolicyService.save.mockResolvedValue(LOP_POLICY);

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('lop-configured-days-input')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-lop-button'));

    await waitFor(() => {
      expect(lopPolicyService.save).toHaveBeenCalledTimes(1);
    });

    const payload = lopPolicyService.save.mock.calls[0][0];
    expect(payload).toEqual({
      workingDayBasis: 'ORG_DAYS',
      configuredDaysPerMonth: 26,
      weekendsPayable: false,
      holidaysPayable: true,
      lopRounding: 'HALF_UP_0',
      effectiveFrom: '2026-11-01',
    });
    expect(payScheduleService.save).not.toHaveBeenCalled();
  });

  it('renders "Save the schedule first" hint when period endpoint returns 409', async () => {
    const error409 = new Error('No pay schedule configured');
    error409.response = { status: 409, data: { message: 'No schedule' } };
    payScheduleService.period.mockRejectedValue(error409);

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('preview-error')).toBeDefined();
      expect(screen.getByText('Save the schedule first')).toBeDefined();
    });
  });

  it('renders period preview dates on successful fetch', async () => {
    payScheduleService.period.mockResolvedValue({
      start: '2026-10-01',
      end: '2026-10-31',
      cutoff_date: '2026-10-25',
      pay_date: '2026-10-31',
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('preview-descriptions')).toBeDefined();
      expect(screen.getByText('2026-10-01')).toBeDefined();
      expect(screen.getAllByText('2026-10-31').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('2026-10-25')).toBeDefined();
    });
  });
});
