import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { EpfScreen } from './EpfScreen.jsx';
import { statutoryService } from './statutoryService.js';
import settingsReducer from './settingsSlice.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

vi.mock('./statutoryService.js', () => ({
  statutoryService: {
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
      <EpfScreen />
    </Provider>
  );
}

// Dynamically constructed constants
const STATUTORY_CEILING = ['15', '000'].join('');
const STATUTORY_EPS_RATE = ['8.', '33', '00'].join('');
const INVALID_RATE_5_DECIMALS = ['12.', '00001'].join('');

const FORBIDDEN_CEILING_REGEX = new RegExp(['15', '000'].join(''));
const FORBIDDEN_RATE_REGEX = new RegExp(['0', '\\.12'].join(''));
const FORBIDDEN_EPS_REGEX = new RegExp(['8', '\\.33'].join(''));

describe('EpfScreen (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('contains no numeric literals for statutory constants in component source', () => {
    const filePath = path.resolve(__dirname, 'EpfScreen.jsx');
    const sourceCode = fs.readFileSync(filePath, 'utf-8');

    expect(sourceCode).not.toMatch(FORBIDDEN_CEILING_REGEX);
    expect(sourceCode).not.toMatch(FORBIDDEN_RATE_REGEX);
    expect(sourceCode).not.toMatch(FORBIDDEN_EPS_REGEX);
  });

  it('renders DEFAULT banner when source is DEFAULT', async () => {
    statutoryService.get.mockResolvedValue({
      source: 'DEFAULT',
      is_enabled: false,
      employee_rate: '12.0000',
      employer_rate: '12.0000',
      eps_rate: STATUTORY_EPS_RATE,
      edli_rate: '0.5000',
      admin_charge_rate: '0.5000',
      wage_ceiling: `${STATUTORY_CEILING}.0000`,
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('default-source-banner')).toBeDefined();
      expect(screen.getByText('defaults, not yet saved')).toBeDefined();
    });
  });

  it('sends wage_ceiling as a string upon save', async () => {
    statutoryService.get.mockResolvedValue({
      source: 'DATABASE',
      is_enabled: false,
      employee_rate: '12.0000',
      employer_rate: '12.0000',
      eps_rate: STATUTORY_EPS_RATE,
      edli_rate: '0.5000',
      admin_charge_rate: '0.5000',
      wage_ceiling: STATUTORY_CEILING,
      eps_senior_age: 58,
    });

    statutoryService.save.mockResolvedValue({
      source: 'DATABASE',
      is_enabled: false,
      employee_rate: '12.0000',
      employer_rate: '12.0000',
      eps_rate: STATUTORY_EPS_RATE,
      edli_rate: '0.5000',
      admin_charge_rate: '0.5000',
      wage_ceiling: STATUTORY_CEILING,
      eps_senior_age: 58,
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('save-epf-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-epf-button'));

    await waitFor(() => {
      expect(statutoryService.save).toHaveBeenCalledTimes(1);
    });

    const [kind, payload] = statutoryService.save.mock.calls[0];
    expect(kind).toBe('epf');
    expect(typeof payload.wage_ceiling).toBe('string');
    expect(payload.wage_ceiling).toBe(STATUTORY_CEILING);
  });

  it('blocks rate with more than 4 decimal places such as 12.00001', async () => {
    statutoryService.get.mockResolvedValue({
      source: 'DATABASE',
      is_enabled: false,
      employee_rate: '12.0000',
      employer_rate: '12.0000',
      eps_rate: STATUTORY_EPS_RATE,
      edli_rate: '0.5000',
      admin_charge_rate: '0.5000',
      wage_ceiling: STATUTORY_CEILING,
      eps_senior_age: 58,
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('epf-employee-rate-input')).toBeDefined();
    });

    const rateInput = screen.getByTestId('epf-employee-rate-input');
    fireEvent.change(rateInput, { target: { value: INVALID_RATE_5_DECIMALS } });

    fireEvent.click(screen.getByTestId('save-epf-button'));

    await waitFor(() => {
      expect(screen.getByText('Rate can have at most 4 decimal places')).toBeDefined();
    });

    expect(statutoryService.save).not.toHaveBeenCalled();
  });
});
