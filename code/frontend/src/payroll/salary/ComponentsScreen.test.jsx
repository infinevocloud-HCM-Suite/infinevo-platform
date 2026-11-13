import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
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
    });
  });

  it('the earnings table has a Type column and no Code column (W-117)', async () => {
    componentService.list.mockResolvedValue([
      {
        id: 'comp-1',
        name: 'Basic Salary',
        code: 'BASIC',
        calculationType: 'FLAT',
        defaultValue: '50000',
        active: true,
      },
      {
        id: 'comp-2',
        name: 'Quarterly Incentive',
        code: 'QINC',
        calculationType: 'FLAT',
        defaultValue: '10000',
        variable: true,
        earningFrequency: 'QUARTERLY',
        active: true,
      },
    ]);
    renderScreen(true);

    await waitFor(() => {
      expect(screen.getByText('Quarterly Incentive')).toBeDefined();
    });
    expect(screen.getByRole('columnheader', { name: 'Type' })).toBeDefined();
    expect(screen.queryByRole('columnheader', { name: 'Code' })).toBeNull();
    expect(screen.queryByText('QINC')).toBeNull();
    const types = screen.getAllByTestId('component-type').map((tag) => tag.textContent);
    expect(types).toEqual(['Fixed', 'Variable · Quarterly']);
    // The earnings Tags column stays.
    expect(screen.getByRole('columnheader', { name: 'Tags' })).toBeDefined();
  });

  it('the benefits tab shows no Code column (W-117)', async () => {
    componentService.list.mockResolvedValue([
      { id: 'b-1', name: 'Meal Card', code: 'MEAL', calculationType: 'FLAT', defaultValue: '2200', active: true },
    ]);
    renderScreen(true);

    fireEvent.click(screen.getByRole('tab', { name: /benefits/i }));

    await waitFor(() => {
      expect(componentService.list).toHaveBeenCalledWith('benefits', { activeOnly: false });
      expect(screen.getAllByText('Meal Card').length).toBeGreaterThan(0);
    });
    const panel = screen.getByRole('tabpanel', { name: /benefits/i });
    expect(within(panel).queryByRole('columnheader', { name: 'Code' })).toBeNull();
    expect(within(panel).queryByText('MEAL')).toBeNull();
    expect(within(panel).getByRole('columnheader', { name: 'Tags' })).toBeDefined();
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
