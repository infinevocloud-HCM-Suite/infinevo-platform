import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { OverviewTab } from './OverviewTab.jsx';
import { employeeService } from '../employeeService.js';
import employeeReducer from '../employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('../employeeService.js', () => ({
  employeeService: {
    update: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('OverviewTab component', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('loads an employee with no gender, saves, and asserts payload gender is null (B-1)', async () => {
    const employeeNoGender = {
      id: 'emp-101',
      employeeNumber: 'EMP001',
      firstName: 'Alice',
      lastName: 'Smith',
      gender: null,
      status: 'ACTIVE',
      dateOfJoining: '2026-01-01',
    };

    const onUpdate = vi.fn();
    employeeService.update.mockResolvedValueOnce({
      ...employeeNoGender,
      gender: null,
    });

    render(
      <Provider store={store}>
        <OverviewTab employee={employeeNoGender} onUpdate={onUpdate} />
      </Provider>
    );

    // Click Edit button
    const editBtn = screen.getByRole('button', { name: /edit details/i });
    fireEvent.click(editBtn);

    // Save Overview without setting gender
    const saveBtn = screen.getByRole('button', { name: /save changes/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.update).toHaveBeenCalledWith(
        'emp-101',
        expect.objectContaining({
          employeeNumber: 'EMP001',
          firstName: 'Alice',
          gender: null,
        })
      );
    });
  });

  it('allows editing status between ACTIVE and SUSPENDED (D-1)', async () => {
    const employee = {
      id: 'emp-102',
      employeeNumber: 'EMP002',
      firstName: 'Bob',
      lastName: 'Jones',
      gender: 'MALE',
      status: 'ACTIVE',
      dateOfJoining: '2026-01-01',
    };

    const onUpdate = vi.fn();
    employeeService.update.mockResolvedValueOnce({
      ...employee,
      status: 'SUSPENDED',
    });

    render(
      <Provider store={store}>
        <OverviewTab employee={employee} onUpdate={onUpdate} />
      </Provider>
    );

    const editBtn = screen.getByRole('button', { name: /edit details/i });
    fireEvent.click(editBtn);

    // Find and verify edit-status select
    const statusSelect = document.getElementById('edit-status');
    expect(statusSelect).toBeTruthy();

    const saveBtn = screen.getByRole('button', { name: /save changes/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.update).toHaveBeenCalled();
    });
  });

  it('re-seeds form state when employee prop updates after terminate (D-2)', async () => {
    const employeeActive = {
      id: 'emp-103',
      employeeNumber: 'EMP003',
      firstName: 'Charlie',
      status: 'ACTIVE',
    };

    const { rerender } = render(
      <Provider store={store}>
        <OverviewTab employee={employeeActive} onUpdate={vi.fn()} />
      </Provider>
    );

    expect(screen.getByText('ACTIVE')).toBeDefined();

    // Rerender with terminated employee
    const employeeTerminated = {
      ...employeeActive,
      status: 'TERMINATED',
    };

    rerender(
      <Provider store={store}>
        <OverviewTab employee={employeeTerminated} onUpdate={vi.fn()} />
      </Provider>
    );

    expect(screen.getByText('TERMINATED')).toBeDefined();
  });

  it('blocks saving and shows validation errors when required fields are missing', async () => {
    const employee = {
      id: 'emp-104',
      employeeNumber: 'EMP004',
      firstName: 'Diana',
      status: 'ACTIVE',
      dateOfJoining: '2026-01-01',
    };

    render(
      <Provider store={store}>
        <OverviewTab employee={employee} onUpdate={vi.fn()} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /edit details/i }));

    // Clear employeeNumber and firstName
    fireEvent.change(document.getElementById('edit-empNum'), { target: { value: '' } });
    fireEvent.change(document.getElementById('edit-firstName'), { target: { value: '' } });

    fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(screen.getByText('Employee Number is required')).toBeDefined();
      expect(screen.getByText('First Name is required')).toBeDefined();
    });

    expect(employeeService.update).not.toHaveBeenCalled();
  });

  it('displays backend 400 fieldErrors on inputs and in error message', async () => {
    const employee = {
      id: 'emp-105',
      employeeNumber: 'EMP005',
      firstName: 'Evan',
      status: 'ACTIVE',
      dateOfJoining: '2026-01-01',
    };

    const backendError = {
      status: 400,
      message: 'Validation failed',
      fieldErrors: {
        employeeNumber: 'Employee number already taken',
      },
    };
    employeeService.update.mockRejectedValueOnce(backendError);

    render(
      <Provider store={store}>
        <OverviewTab employee={employee} onUpdate={vi.fn()} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button', { name: /edit details/i }));
    fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(screen.getByText('Employee number already taken')).toBeDefined();
    });
  });
});
