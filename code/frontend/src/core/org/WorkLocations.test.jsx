import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { WorkLocations } from './WorkLocations.jsx';
import { workLocationService } from './workLocationService.js';
import employeeReducer from '../employee/employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./workLocationService.js', () => ({
  workLocationService: {
    list: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

describe('WorkLocations component', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });

    workLocationService.list.mockResolvedValue([
      {
        id: 'loc-1',
        code: 'HQ',
        name: 'Headquarters',
        city: 'Mumbai',
        state: 'Maharashtra',
        stateCode: 'MH',
        filingAddress: true,
        active: true,
      },
      {
        id: 'loc-2',
        code: 'BLR',
        name: 'Bengaluru Branch',
        city: 'Bengaluru',
        state: 'Karnataka',
        stateCode: 'KA',
        filingAddress: false,
        active: true,
      },
    ]);

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders locations table, shows filing address badge, and disables delete on filing address', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <WorkLocations />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Headquarters')).toBeDefined();
      expect(document.getElementById('tag-filing-address')).toBeDefined();
      expect(screen.getByText('Mumbai')).toBeDefined();
      const deleteFilingBtn = document.getElementById('btn-delete-location-loc-1');
      expect(deleteFilingBtn.disabled).toBe(true);
      const deleteNormalBtn = document.getElementById('btn-delete-location-loc-2');
      expect(deleteNormalBtn.disabled).toBe(false);
    });
  });

  it('handles 409 conflict when deleting location in use and offers deactivate', async () => {
    const conflictError = new Error('Location is assigned to active employees.');
    conflictError.status = 409;
    conflictError.code = 'CONFLICT';
    workLocationService.remove.mockRejectedValueOnce(conflictError);
    workLocationService.update.mockResolvedValueOnce({});

    render(
      <Provider store={store}>
        <MemoryRouter>
          <WorkLocations />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Bengaluru Branch')).toBeDefined();
    });

    const deleteBtn = document.getElementById('btn-delete-location-loc-2');
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      const confirmOk = screen.getByText('Yes');
      fireEvent.click(confirmOk);
    });

    await waitFor(() => {
      expect(screen.getByText('Location is assigned to active employees.')).toBeDefined();
      const deactivateBtn = document.getElementById('btn-deactivate-instead');
      expect(deactivateBtn).toBeDefined();
      fireEvent.click(deactivateBtn);
    });

    await waitFor(() => {
      expect(workLocationService.update).toHaveBeenCalledWith('loc-2', expect.objectContaining({ active: false }));
    });
  });
});
