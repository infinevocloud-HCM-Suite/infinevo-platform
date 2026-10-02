import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import dayjs from 'dayjs';
import { CalendarHolidays } from './CalendarHolidays.jsx';
import { holidayCalendarService } from './holidayCalendarService.js';
import employeeReducer from '../employee/employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./holidayCalendarService.js', () => ({
  holidayCalendarService: {
    get: vi.fn(),
    addHoliday: vi.fn(),
    removeHoliday: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('CalendarHolidays component', () => {
  let store;
  const currentYear = dayjs().year();

  const mockCalendar = {
    id: 'cal-100',
    name: 'India Primary Calendar',
    isDefault: true,
    workLocationIds: ['loc-1'],
    holidays: [
      {
        id: 'h-1',
        name: 'New Year Day',
        from: `${currentYear}-01-01`,
        to: `${currentYear}-01-01`,
        isRestricted: false,
      },
      {
        id: 'h-2',
        name: 'Festival Holiday',
        from: `${currentYear}-03-15`,
        to: `${currentYear}-03-15`,
        isRestricted: true,
      },
      {
        id: 'h-3',
        name: 'Past Holiday',
        from: `${currentYear - 1}-10-02`,
        to: `${currentYear - 1}-10-02`,
        isRestricted: false,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });

    holidayCalendarService.get.mockResolvedValue(mockCalendar);
    holidayCalendarService.addHoliday.mockResolvedValue({ id: 'h-4', name: 'Added Holiday' });
    holidayCalendarService.removeHoliday.mockResolvedValue({});

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders NotEntitled when user lacks core.holiday.read', () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => perm !== 'core.holiday.read');

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays/cal-100']}>
          <Routes>
            <Route path="/holidays/:id" element={<CalendarHolidays />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('renders calendar details and current year holidays', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays/cal-100']}>
          <Routes>
            <Route path="/holidays/:id" element={<CalendarHolidays />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getAllByText('India Primary Calendar').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('New Year Day')).toBeDefined();
      expect(screen.getByText('Festival Holiday')).toBeDefined();
      expect(screen.getByText('Restricted')).toBeDefined();
      expect(screen.getByText('Regular')).toBeDefined();
      expect(screen.queryByText('Past Holiday')).toBeNull();
    });
  });

  it('opens add holiday modal and submits payload to holidayCalendarService.addHoliday', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays/cal-100']}>
          <Routes>
            <Route path="/holidays/:id" element={<CalendarHolidays />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getAllByText('India Primary Calendar').length).toBeGreaterThanOrEqual(1);
    });

    const addBtn = document.getElementById('btn-add-holiday');
    expect(addBtn).toBeTruthy();
    fireEvent.click(addBtn);

    await waitFor(() => {
      expect(screen.getByText('Restricted / Optional Holiday')).toBeDefined();
    });

    const nameInput = document.getElementById('input-holiday-name');
    fireEvent.change(nameInput, { target: { value: 'Diwali' } });

    const fromInput = document.getElementById('picker-holiday-from');
    fireEvent.change(fromInput, { target: { value: `${currentYear}-11-12` } });
    fireEvent.keyDown(fromInput, { key: 'Enter', keyCode: 13 });

    const submitBtn = document.getElementById('btn-submit-holiday');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(holidayCalendarService.addHoliday).toHaveBeenCalledWith(
        'cal-100',
        expect.objectContaining({
          name: 'Diwali',
          from: `${currentYear}-11-12`,
        })
      );
    });
  });

  it('hides add and delete actions when user lacks core.holiday.manage', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => perm === 'core.holiday.read');

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays/cal-100']}>
          <Routes>
            <Route path="/holidays/:id" element={<CalendarHolidays />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getAllByText('India Primary Calendar').length).toBeGreaterThanOrEqual(1);
    });

    expect(document.getElementById('btn-add-holiday')).toBeNull();
    expect(document.getElementById('btn-delete-holiday-h-1')).toBeNull();
  });
});
