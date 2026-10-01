import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Calendars } from './Calendars.jsx';
import { holidayCalendarService } from './holidayCalendarService.js';
import { workLocationService } from '../org/workLocationService.js';
import employeeReducer from '../employee/employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./holidayCalendarService.js', () => ({
  holidayCalendarService: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('../org/workLocationService.js', () => ({
  workLocationService: {
    list: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('Calendars component', () => {
  let store;
  const currentYear = new Date().getFullYear();

  const mockLocations = [
    { id: 'loc-1', name: 'Mumbai Office', code: 'MUM' },
    { id: 'loc-2', name: 'Bengaluru Office', code: 'BLR' },
  ];

  const mockCalendars = [
    {
      id: 'cal-1',
      name: 'India Standard 2026',
      isDefault: true,
      workLocationIds: ['loc-1', 'loc-2'],
      holidays: [
        { id: 'h-1', name: 'Republic Day', from: `${currentYear}-01-26`, to: `${currentYear}-01-26` },
        { id: 'h-2', name: 'Independence Day', from: `${currentYear}-08-15`, to: `${currentYear}-08-15` },
      ],
    },
    {
      id: 'cal-2',
      name: 'New York Holidays',
      isDefault: false,
      workLocationIds: [],
      holidays: [],
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
    holidayCalendarService.list.mockResolvedValue(mockCalendars);
    holidayCalendarService.get.mockResolvedValue(mockCalendars[0]);
    holidayCalendarService.create.mockResolvedValue({ id: 'cal-3', name: 'New Cal' });
    holidayCalendarService.update.mockResolvedValue({ id: 'cal-1', name: 'Updated Cal' });
    holidayCalendarService.remove.mockResolvedValue({});

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders NotEntitled when user lacks core.holiday.read', () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => perm !== 'core.holiday.read');

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays']}>
          <Calendars />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('renders table with calendar details, locations, and holiday count', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays']}>
          <Calendars />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('India Standard 2026')).toBeDefined();
      expect(screen.getByText('Default')).toBeDefined();
      expect(screen.getByText('Mumbai Office')).toBeDefined();
      expect(screen.getByText('Bengaluru Office')).toBeDefined();
      expect(screen.getByText('New York Holidays')).toBeDefined();
      expect(screen.getByText('None (Applies to no locations)')).toBeDefined();
    });
  });

  it('opens drawer on Add Calendar click, submits valid data, and calls create', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays']}>
          <Calendars />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('India Standard 2026')).toBeDefined();
    });

    const addBtn = document.getElementById('btn-create-calendar');
    expect(addBtn).toBeTruthy();
    fireEvent.click(addBtn);

    await waitFor(() => {
      expect(screen.getByText('Create Holiday Calendar')).toBeDefined();
    });

    const nameInput = document.getElementById('input-calendar-name');
    fireEvent.change(nameInput, { target: { value: 'UK Calendar' } });

    const saveBtn = document.getElementById('btn-save-calendar');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(holidayCalendarService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'UK Calendar',
          isDefault: false,
          workLocationIds: [],
        })
      );
    });
  });

  it('loads calendar on edit click and calls update on submit', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays']}>
          <Calendars />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('India Standard 2026')).toBeDefined();
    });

    const editBtn = document.getElementById('btn-edit-calendar-cal-1');
    expect(editBtn).toBeTruthy();
    fireEvent.click(editBtn);

    await waitFor(() => {
      expect(screen.getByText('Edit Holiday Calendar')).toBeDefined();
    });

    const nameInput = document.getElementById('input-calendar-name');
    fireEvent.change(nameInput, { target: { value: 'India Standard Updated' } });

    const saveBtn = document.getElementById('btn-save-calendar');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(holidayCalendarService.update).toHaveBeenCalledWith(
        'cal-1',
        expect.objectContaining({
          name: 'India Standard Updated',
        })
      );
    });
  });

  it('hides write actions when user lacks core.holiday.manage', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => perm === 'core.holiday.read');

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/holidays']}>
          <Calendars />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('India Standard 2026')).toBeDefined();
    });

    expect(document.getElementById('btn-create-calendar')).toBeNull();
    expect(document.getElementById('btn-edit-calendar-cal-1')).toBeNull();
    expect(document.getElementById('btn-delete-calendar-cal-1')).toBeNull();
  });
});
