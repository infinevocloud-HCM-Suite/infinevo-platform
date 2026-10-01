import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeeCreate } from './EmployeeCreate.jsx';
import employeeReducer from './employeeSlice.js';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';

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
    create: vi.fn(),
  },
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

function renderWithStore(ui) {
  const store = configureStore({
    reducer: { employee: employeeReducer },
  });
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>
  );
}

describe('EmployeeCreate component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orgMasterService.all.mockResolvedValue({
      departments: {},
      designations: {},
      workLocations: {},
      raw: { departments: [], designations: [], workLocations: [] },
    });
  });

  it('blocks submit when required fields are missing', async () => {
    renderWithStore(<EmployeeCreate />);

    const submitBtn = screen.getByRole('button', { name: /create employee/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Employee number is required')).toBeDefined();
      expect(screen.getByText('First name is required')).toBeDefined();
      expect(screen.getByText('Date of joining is required')).toBeDefined();
    });

    expect(employeeService.create).not.toHaveBeenCalled();
  });

  it('submits valid data and navigates to new employee id', async () => {
    employeeService.create.mockResolvedValueOnce({
      id: 'new-emp-101',
      employeeNumber: 'EMP001',
      firstName: 'Asha',
    });

    renderWithStore(<EmployeeCreate />);

    fireEvent.change(screen.getByPlaceholderText('e.g. EMP001'), {
      target: { value: 'EMP001' },
    });
    fireEvent.change(screen.getByPlaceholderText('First name'), {
      target: { value: 'Asha' },
    });

    // DatePicker change simulation
    const dateInput = screen.getByPlaceholderText('Select date');
    fireEvent.change(dateInput, { target: { value: '2026-04-01' } });
    fireEvent.keyDown(dateInput, { key: 'Enter', code: 'Enter' });

    const submitBtn = screen.getByRole('button', { name: /create employee/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(employeeService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          employeeNumber: 'EMP001',
          firstName: 'Asha',
          dateOfJoining: '2026-04-01',
        })
      );
      expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-101');
    });
  });
});
