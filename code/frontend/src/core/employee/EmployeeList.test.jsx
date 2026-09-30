import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeeList } from './EmployeeList.jsx';
import employeeReducer from './employeeSlice.js';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import * as useCanModule from '@shell/screens';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('./employeeService.js', () => ({
  employeeService: {
    list: vi.fn(),
  },
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn(),
  },
}));

function renderWithStore(ui, { initialState } = {}) {
  const store = configureStore({
    reducer: { employee: employeeReducer },
    preloadedState: initialState,
  });
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>
  );
}

describe('EmployeeList component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orgMasterService.all.mockResolvedValue({
      departments: { 'd-1': 'Engineering' },
      designations: {},
      workLocations: {},
      raw: { departments: [], designations: [], workLocations: [] },
    });
    employeeService.list.mockResolvedValue({
      content: [
        {
          id: 'emp-1',
          employeeNumber: 'E-101',
          firstName: 'Asha',
          lastName: 'Rao',
          status: 'ACTIVE',
          workEmail: 'asha@work.com',
          departmentId: 'd-1',
          dateOfJoining: '2026-04-01',
        },
      ],
      totalElements: 1,
    });
  });

  it('renders table rows, sends search q, and handles row navigation', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
      if (action === 'core.employee.create') return true;
      if (action === 'core.employee.delete') return true;
      return true;
    });

    renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
      expect(screen.getByText('Asha Rao')).toBeDefined();
    });

    // Search query
    const searchInput = screen.getByPlaceholderText('Search employees...');
    fireEvent.change(searchInput, { target: { value: 'Asha' } });
    fireEvent.keyDown(searchInput, { key: 'Enter', code: 'Enter' });

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'Asha' })
      );
    });

    // Row click navigates to /employees/:id
    const row = screen.getByTestId('employee-row-emp-1');
    fireEvent.click(row);
    expect(mockNavigate).toHaveBeenCalledWith('/employees/emp-1');
  });

  it('does not render "Include deleted" switch when user lacks core.employee.delete', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
      if (action === 'core.employee.delete') return false;
      return true;
    });

    renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
    });

    expect(screen.queryByText('Include deleted:')).toBeNull();
  });

  it('sends status filter when status select is changed', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

    const { container } = renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
    });

    employeeService.list.mockClear();

    const selector = container.querySelector('#select-status-filter .ant-select-selector') || container.querySelector('#select-status-filter');
    fireEvent.mouseDown(selector);

    await waitFor(() => {
      const suspendedOption = document.querySelector('.ant-select-item-option[title="Suspended"]') || screen.getByText('Suspended');
      expect(suspendedOption).toBeTruthy();
      fireEvent.click(suspendedOption);
    });

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'SUSPENDED' })
      );
    });
  });

  it('shows the error and a retry instead of an empty table when the load fails', async () => {
    employeeService.list.mockReset();
    employeeService.list.mockRejectedValueOnce({ code: 'FORBIDDEN', message: 'You do not have access', status: 403 });
    employeeService.list.mockResolvedValueOnce({ content: [], totalElements: 0 });

    renderWithStore(<EmployeeList />);

    expect(await screen.findByText('Employees could not be loaded')).toBeDefined();
    expect(screen.getByText('You do not have access')).toBeDefined();

    fireEvent.click(document.getElementById('btn-retry-employees'));

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledTimes(2);
      expect(screen.queryByText('Employees could not be loaded')).toBeNull();
    });
  });
});
