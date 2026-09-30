import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MasterTable } from './MasterTable.jsx';
import employeeReducer, { setMasters } from '../employee/employeeSlice.js';
import * as useCanModule from '@shell/screens';

describe('MasterTable component', () => {
  let mockService;
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    mockService = {
      list: vi.fn().mockResolvedValue([
        { id: '1', code: 'FIN', name: 'Finance', active: true, createdAt: '2026-01-01T00:00:00Z' },
        { id: '2', code: 'HR', name: 'Human Resources', active: false, createdAt: '2026-01-02T00:00:00Z' },
      ]),
      create: vi.fn().mockResolvedValue({ id: '3', code: 'ENG', name: 'Engineering', active: true }),
      update: vi.fn().mockResolvedValue({ id: '1', code: 'FIN', name: 'Finance Dept', active: false }),
      remove: vi.fn().mockResolvedValue({}),
    };

    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders master rows and adds new record, invalidating masters cache on write', async () => {
    store.dispatch(
      setMasters({
        departments: { 1: 'Finance' },
        designations: {},
        workLocations: {},
        raw: { departments: [{ id: '1', name: 'Finance' }] },
      })
    );
    expect(store.getState().employee.loadedAt).not.toBeNull();

    render(
      <Provider store={store}>
        <MasterTable title="Departments" service={mockService} />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Finance')).toBeDefined();
      expect(screen.getByText('Human Resources')).toBeDefined();
    });

    // Add new department
    const codeInput = document.getElementById('input-new-code');
    const nameInput = document.getElementById('input-new-name');
    const addButton = document.getElementById('btn-add-master');

    fireEvent.change(codeInput, { target: { value: 'ENG' } });
    fireEvent.change(nameInput, { target: { value: 'Engineering' } });
    fireEvent.click(addButton);

    await waitFor(() => {
      expect(mockService.create).toHaveBeenCalledWith({
        code: 'ENG',
        name: 'Engineering',
        active: true,
      });
      expect(store.getState().employee.loadedAt).toBeNull();
      expect(store.getState().employee.masters.raw.departments).toEqual([]);
    });
  });

  it('handles 409 conflict on delete and offers deactivate instead', async () => {
    const conflictError = new Error('Department in use by employees.');
    conflictError.status = 409;
    conflictError.code = 'CONFLICT';
    mockService.remove.mockRejectedValueOnce(conflictError);

    render(
      <Provider store={store}>
        <MasterTable title="Departments" service={mockService} />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Finance')).toBeDefined();
    });

    const deleteBtn = document.getElementById('btn-delete-1');
    fireEvent.click(deleteBtn);

    // Popconfirm confirm button
    await waitFor(() => {
      const confirmOk = screen.getByText('Yes');
      fireEvent.click(confirmOk);
    });

    // Conflict modal opens
    await waitFor(() => {
      expect(screen.getByText('Department in use by employees.')).toBeDefined();
      const deactivateBtn = document.getElementById('btn-deactivate-instead');
      expect(deactivateBtn).toBeTruthy();
      fireEvent.click(deactivateBtn);
    });

    await waitFor(() => {
      expect(mockService.update).toHaveBeenCalledWith('1', {
        code: 'FIN',
        name: 'Finance',
        active: false,
      });
    });
  });

  it('hides write actions and add input when user lacks core.org.manage', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);

    render(
      <Provider store={store}>
        <MasterTable title="Departments" service={mockService} />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Finance')).toBeDefined();
    });

    expect(document.getElementById('input-new-code')).toBeNull();
    expect(document.getElementById('btn-edit-1')).toBeNull();
    expect(document.getElementById('btn-delete-1')).toBeNull();
  });
});
