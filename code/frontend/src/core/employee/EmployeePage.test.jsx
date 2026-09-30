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

  function renderPage(employeeId) {
    return render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/employees/${employeeId}`]}>
          <Routes>
            <Route path="/employees/:id" element={<EmployeePage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );
  }

  it('offers Reactivate and Terminate on a SUSPENDED employee, and Reactivate sends ACTIVE', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-susp-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'SUSPENDED',
    });
    employeeService.update.mockResolvedValueOnce({
      id: 'emp-susp-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'ACTIVE',
    });

    renderPage('emp-susp-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-reactivate-employee')).not.toBeNull();
    expect(document.getElementById('btn-terminate-employee')).not.toBeNull();

    fireEvent.click(document.getElementById('btn-reactivate-employee'));

    await waitFor(() => {
      const confirmBtn = document.querySelector('.ant-modal-confirm-btns .ant-btn-primary');
      expect(confirmBtn).not.toBeNull();
      fireEvent.click(confirmBtn);
    });

    await waitFor(() => {
      expect(employeeService.update).toHaveBeenCalledWith(
        'emp-susp-1',
        expect.objectContaining({ status: 'ACTIVE', terminationDate: null })
      );
    });
  });

  it('offers neither Reactivate nor Terminate on a TERMINATED employee - the status is terminal', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-term-1',
      employeeNumber: 'EMP-998',
      firstName: 'Peter',
      lastName: 'Tosh',
      status: 'TERMINATED',
    });

    renderPage('emp-term-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-reactivate-employee')).toBeNull();
    expect(document.getElementById('btn-terminate-employee')).toBeNull();
    expect(document.getElementById('btn-delete-employee')).not.toBeNull();
  });

  it('offers Terminate but not Reactivate on an ACTIVE employee', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-act-1',
      employeeNumber: 'EMP-997',
      firstName: 'Rita',
      lastName: 'Marley',
      status: 'ACTIVE',
    });

    renderPage('emp-act-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-terminate-employee')).not.toBeNull();
    expect(document.getElementById('btn-reactivate-employee')).toBeNull();
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
