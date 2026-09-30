import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { WorkLocationForm } from './WorkLocationForm.jsx';
import { workLocationService } from './workLocationService.js';
import employeeReducer from '../employee/employeeSlice.js';

import * as useCanModule from '@shell/screens';

vi.mock('./workLocationService.js', () => ({
  workLocationService: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}));

describe('WorkLocationForm component', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });
  });

  it('renders NotEntitled when user lacks core.org.manage', () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/new']}>
          <Routes>
            <Route path="/org/work-locations/new" element={<WorkLocationForm />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('blocks submission when required name and code are missing', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/new']}>
          <Routes>
            <Route path="/org/work-locations/new" element={<WorkLocationForm />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const submitBtn = document.getElementById('btn-submit-location');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Location name is required')).toBeDefined();
      expect(screen.getByText('Location code is required')).toBeDefined();
    });

    expect(workLocationService.create).not.toHaveBeenCalled();
  });

  it('submits valid data when creating location', async () => {
    workLocationService.create.mockResolvedValueOnce({ id: 'loc-new' });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/new']}>
          <Routes>
            <Route path="/org/work-locations/new" element={<WorkLocationForm />} />
            <Route path="/org/work-locations" element={<div>Locations List</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    fireEvent.change(document.getElementById('input-name'), { target: { value: 'Bengaluru Office' } });
    fireEvent.change(document.getElementById('input-code'), { target: { value: 'BLR' } });
    fireEvent.change(document.getElementById('input-addressLine1'), { target: { value: '100 Tech Park' } });
    fireEvent.change(document.getElementById('input-city'), { target: { value: 'Bengaluru' } });
    fireEvent.change(document.getElementById('input-state'), { target: { value: 'Karnataka' } });
    fireEvent.change(document.getElementById('input-stateCode'), { target: { value: 'KA' } });
    fireEvent.change(document.getElementById('input-zipCode'), { target: { value: '560001' } });
    fireEvent.click(document.getElementById('switch-filingAddress'));

    const submitBtn = document.getElementById('btn-submit-location');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(workLocationService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Bengaluru Office',
          code: 'BLR',
          addressLine1: '100 Tech Park',
          city: 'Bengaluru',
          state: 'Karnataka',
          stateCode: 'KA',
          zipCode: '560001',
          countryCode: 'IN',
          filingAddress: true,
        })
      );
    });
  });

  it('loads existing location on edit mode', async () => {
    workLocationService.get.mockResolvedValueOnce({
      id: 'loc-1',
      name: 'Mumbai HQ',
      code: 'MUM',
      addressLine1: '1 Marine Drive',
      city: 'Mumbai',
      state: 'Maharashtra',
      stateCode: 'MH',
      zipCode: '400001',
      countryCode: 'IN',
      filingAddress: true,
      active: true,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/loc-1/edit']}>
          <Routes>
            <Route path="/org/work-locations/:id/edit" element={<WorkLocationForm />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(workLocationService.get).toHaveBeenCalledWith('loc-1');
      expect(screen.getByDisplayValue('Mumbai HQ')).toBeDefined();
      expect(screen.getByDisplayValue('MUM')).toBeDefined();
      expect(screen.getByDisplayValue('1 Marine Drive')).toBeDefined();
    });
  });

  it('renders NotFound component when location id is not found', async () => {
    workLocationService.get.mockResolvedValueOnce(null);

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/unknown-999/edit']}>
          <Routes>
            <Route path="/org/work-locations/:id/edit" element={<WorkLocationForm />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(workLocationService.get).toHaveBeenCalledWith('unknown-999');
      expect(screen.getByText('404')).toBeDefined();
      expect(screen.getByText(/page you visited does not exist/i)).toBeDefined();
    });
  });

  it('allows saving location without address fields (backend treats them as optional)', async () => {
    workLocationService.get.mockResolvedValueOnce({
      id: 'loc-no-addr',
      name: 'Remote Branch',
      code: 'REM',
      addressLine1: null,
      city: null,
      state: null,
      stateCode: null,
      zipCode: null,
      countryCode: null,
      filingAddress: false,
      active: true,
    });
    workLocationService.update.mockResolvedValueOnce({ id: 'loc-no-addr' });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/org/work-locations/loc-no-addr/edit']}>
          <Routes>
            <Route path="/org/work-locations/:id/edit" element={<WorkLocationForm />} />
            <Route path="/org/work-locations" element={<div>Locations List</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('Remote Branch')).toBeDefined();
    });

    const submitBtn = document.getElementById('btn-submit-location');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(workLocationService.update).toHaveBeenCalledWith(
        'loc-no-addr',
        expect.objectContaining({
          name: 'Remote Branch',
          code: 'REM',
        })
      );
    });
  });
});
