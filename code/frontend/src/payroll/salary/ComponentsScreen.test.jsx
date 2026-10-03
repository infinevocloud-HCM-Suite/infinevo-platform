import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { ComponentsScreen } from './ComponentsScreen.jsx';
import { componentService } from './componentService.js';
import salaryReducer from './salarySlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./componentService.js', () => ({
  componentService: {
    list: vi.fn(),
    setActive: vi.fn(),
    remove: vi.fn(),
  },
}));

function renderScreen(canManage = true) {
  const store = configureStore({
    reducer: {
      salary: salaryReducer,
    },
  });

  vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
    if (action === 'payroll.structure.read') return true;
    if (action === 'payroll.structure.manage') return canManage;
    return false;
  });

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <ComponentsScreen />
      </MemoryRouter>
    </Provider>
  );
}

describe('ComponentsScreen component (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    componentService.list.mockResolvedValue([
      {
        id: 'comp-1',
        name: 'Basic Salary',
        code: 'BASIC',
        calculationType: 'FLAT',
        defaultValue: '50000',
        active: true,
      },
    ]);
  });

  it('renders four tabs: Earnings, Deductions, Benefits, Reimbursements', async () => {
    renderScreen(true);

    expect(screen.getByRole('tab', { name: /earnings/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /deductions/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /benefits/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /reimbursements/i })).toBeDefined();

    await waitFor(() => {
      expect(screen.getByText('Basic Salary')).toBeDefined();
      expect(screen.getByText('BASIC')).toBeDefined();
    });
  });

  it('switch calls setActive with toggled value', async () => {
    componentService.setActive.mockResolvedValueOnce({ id: 'comp-1', active: false });

    renderScreen(true);

    await waitFor(() => {
      expect(screen.getByRole('switch')).toBeDefined();
    });

    const toggle = screen.getByRole('switch');
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(componentService.setActive).toHaveBeenCalledWith('earnings', 'comp-1', false);
    });
  });

  it('"Add" button is present with payroll.structure.manage and absent without it', async () => {
    const { unmount } = renderScreen(true);
    expect(screen.getByRole('button', { name: /add earning/i })).toBeDefined();
    unmount();

    renderScreen(false);
    expect(screen.queryByRole('button', { name: /add earning/i })).toBeNull();
  });
});
