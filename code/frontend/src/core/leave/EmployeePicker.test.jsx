import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EmployeePicker, employeeOptionLabel, SEARCH_PAGE_SIZE } from './EmployeePicker.jsx';
import { employeeService } from '../employee/employeeService.js';

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    list: vi.fn(),
  },
}));

const ASHA = { id: 'emp-1', firstName: 'Asha', lastName: 'Rao', employeeNumber: 'EMP-001' };
const RAVI = { id: 'emp-2', firstName: 'Ravi', lastName: 'Kumar', employeeNumber: 'EMP-002' };

describe('EmployeePicker (D-80)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    employeeService.list.mockImplementation(async ({ q }) => ({
      content: [ASHA, RAVI].filter((e) => !q || e.firstName.toLowerCase().includes(q.toLowerCase())),
    }));
  });

  it('labels an employee by name and number', () => {
    expect(employeeOptionLabel(ASHA)).toBe('Asha Rao (EMP-001)');
    expect(employeeOptionLabel({ employeeNumber: 'EMP-9' })).toBe('EMP-9');
    expect(employeeOptionLabel(null)).toBe('');
  });

  it('asks the server for the first page on focus, then once per pause in typing', async () => {
    render(<EmployeePicker ariaLabel="Employee" debounceMs={10} />);
    const input = screen.getByRole('combobox');

    fireEvent.focus(input);
    await waitFor(() => expect(employeeService.list).toHaveBeenCalledTimes(1));
    expect(employeeService.list).toHaveBeenLastCalledWith({ q: undefined, size: SEARCH_PAGE_SIZE });

    fireEvent.change(input, { target: { value: 'R' } });
    fireEvent.change(input, { target: { value: 'Ra' } });
    fireEvent.change(input, { target: { value: 'Rav' } });
    await waitFor(() => expect(employeeService.list).toHaveBeenCalledTimes(2));
    expect(employeeService.list).toHaveBeenLastCalledWith({ q: 'Rav', size: SEARCH_PAGE_SIZE });
    expect(await screen.findByText('Ravi Kumar (EMP-002)')).toBeDefined();
  });

  it('reports the chosen employee id', async () => {
    const onChange = vi.fn();
    render(<EmployeePicker onChange={onChange} debounceMs={10} />);
    const input = screen.getByRole('combobox');

    fireEvent.focus(input);
    fireEvent.mouseDown(input);
    fireEvent.click(await screen.findByText('Asha Rao (EMP-001)'));

    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls[0][0]).toBe('emp-1');
  });

  it('says why when the caller may not search employees', async () => {
    employeeService.list.mockRejectedValue({ isForbidden: true });
    render(<EmployeePicker debounceMs={10} />);
    const input = screen.getByRole('combobox');

    fireEvent.focus(input);
    fireEvent.mouseDown(input);
    expect(await screen.findByText('You do not have permission to search employees.')).toBeDefined();
  });
});
