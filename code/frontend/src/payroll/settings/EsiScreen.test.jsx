import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { EsiScreen } from './EsiScreen.jsx';
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
      <EsiScreen />
    </Provider>
  );
}

const FORBIDDEN_CEILING_REGEX = new RegExp(['15', '000'].join(''));
const FORBIDDEN_RATE_REGEX = new RegExp(['0', '\\.12'].join(''));
const FORBIDDEN_EPS_REGEX = new RegExp(['8', '\\.33'].join(''));

describe('EsiScreen (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('contains no numeric literals for statutory constants in component source', () => {
    const filePath = path.resolve(__dirname, 'EsiScreen.jsx');
    const sourceCode = fs.readFileSync(filePath, 'utf-8');

    expect(sourceCode).not.toMatch(FORBIDDEN_CEILING_REGEX);
    expect(sourceCode).not.toMatch(FORBIDDEN_RATE_REGEX);
    expect(sourceCode).not.toMatch(FORBIDDEN_EPS_REGEX);
  });

  it('renders DEFAULT banner when source is DEFAULT', async () => {
    statutoryService.get.mockResolvedValue({
      source: 'DEFAULT',
      is_enabled: false,
      employee_rate: '0.7500',
      employer_rate: '3.2500',
      wage_ceiling: '21000.0000',
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('default-source-banner')).toBeDefined();
      expect(screen.getByText('defaults, not yet saved')).toBeDefined();
    });
  });

  it('saves updated ESI settings', async () => {
    statutoryService.get.mockResolvedValue({
      source: 'DATABASE',
      is_enabled: false,
      employee_rate: '0.7500',
      employer_rate: '3.2500',
      wage_ceiling: '21000.0000',
    });

    statutoryService.save.mockResolvedValue({
      source: 'DATABASE',
      is_enabled: false,
      employee_rate: '0.7500',
      employer_rate: '3.2500',
      wage_ceiling: '21000.0000',
    });

    renderScreen();

    await waitFor(() => {
      expect(screen.getByTestId('save-esi-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-esi-button'));

    await waitFor(() => {
      expect(statutoryService.save).toHaveBeenCalledTimes(1);
    });

    const [kind, payload] = statutoryService.save.mock.calls[0];
    expect(kind).toBe('esi');
    expect(typeof payload.wage_ceiling).toBe('string');
  });
});
