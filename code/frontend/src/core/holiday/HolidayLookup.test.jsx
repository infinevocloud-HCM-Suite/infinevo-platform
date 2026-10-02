import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { HolidayLookup } from './HolidayLookup.jsx';
import { holidayService } from './holidayService.js';
import { workLocationService } from '../org/workLocationService.js';
import employeeReducer from '../employee/employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./holidayService.js', () => ({
  holidayService: {
    between: vi.fn(),
  },
}));

vi.mock('../org/workLocationService.js', () => ({
  workLocationService: {
    list: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  errorMsg: vi.fn(),
}));

describe('HolidayLookup component', () => {
  let store;

  const mockLocations = [
    { id: 'loc-1', name: 'Mumbai Office', code: 'MUM' },
    { id: 'loc-2', name: 'Bengaluru Office', code: 'BLR' },
  ];

  const mockResults = [
    {
      id: 'h-1',
      name: 'Republic Day',
      from: '2026-01-26',
      to: '2026-01-26',
      isRestricted: false,
    },
    {
      id: 'h-2',
      name: 'Holi',
      from: '2026-03-04',
      to: '2026-03-04',
      isRestricted: true,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });

    workLocationService.list.mockResolvedValue(mockLocations);
    holidayService.between.mockResolvedValue(mockResults);

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders NotEntitled when user lacks core.holiday.read', () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);

    render(
      <Provider store={store}>
        <MemoryRouter>
          <HolidayLookup />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('renders lookup filters and table headers', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <HolidayLookup />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Holiday Range Lookup')).toBeDefined();
      expect(document.getElementById('btn-submit-lookup')).toBeTruthy();
      expect(document.getElementById('picker-lookup-range')).toBeTruthy();
    });
  });

  it('calls holidayService.between and displays results', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <HolidayLookup />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(document.getElementById('btn-submit-lookup')).toBeTruthy();
    });

    const submitBtn = document.getElementById('btn-submit-lookup');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(holidayService.between).toHaveBeenCalled();
      expect(screen.getByText('Republic Day')).toBeDefined();
      expect(screen.getByText('Holi')).toBeDefined();
      expect(screen.getByText('Restricted')).toBeDefined();
      expect(screen.getByText('Regular')).toBeDefined();
    });
  });
});
