import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeePage } from './EmployeePage.jsx';
import { employeeService } from './employeeService.js';
import employeeReducer from './employeeSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./employeeService.js', () => ({
  employeeService: {
    get: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    section: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn().mockResolvedValue({ departments: {}, designations: {}, workLocations: {} }),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('EmployeePage component', () => {
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

  it('renders Reactivate button when employee is TERMINATED and calls update', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-term-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'TERMINATED',
    });
    employeeService.update.mockResolvedValueOnce({
      id: 'emp-term-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'ACTIVE',
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/employees/emp-term-1']}>
          <Routes>
            <Route path="/employees/:id" element={<EmployeePage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('EMP-999')).toBeDefined();
      expect(document.getElementById('btn-reactivate-employee')).toBeDefined();
    });

    fireEvent.click(document.getElementById('btn-reactivate-employee'));

    await waitFor(() => {
      const confirmBtn = document.querySelector('.ant-modal-confirm-btns .ant-btn-primary');
      expect(confirmBtn).toBeDefined();
      fireEvent.click(confirmBtn);
    });

    await waitFor(() => {
      expect(employeeService.update).toHaveBeenCalledWith(
        'emp-term-1',
        expect.objectContaining({ status: 'ACTIVE', terminationDate: null })
      );
    });
  });

  it('hides Identification, Bank and Reporting Line tabs when read permissions are missing', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-norm-1',
      employeeNumber: 'EMP-100',
      firstName: 'Alice',
      lastName: 'Wonder',
      status: 'ACTIVE',
    });

    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => {
      if (
        perm === 'core.employee_identification.read' ||
        perm === 'core.employee_bank.read' ||
        perm === 'core.org.read'
      ) {
        return false;
      }
      return true;
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/employees/emp-norm-1']}>
          <Routes>
            <Route path="/employees/:id" element={<EmployeePage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Overview')).toBeDefined();
      expect(screen.getByText('Personal')).toBeDefined();
      expect(screen.getByText('Contact')).toBeDefined();
      expect(screen.getByText('Employment')).toBeDefined();
      expect(screen.queryByText('Identification')).toBeNull();
      expect(screen.queryByText('Bank')).toBeNull();
      expect(screen.queryByText('Reporting Line')).toBeNull();
    });
  });
});
