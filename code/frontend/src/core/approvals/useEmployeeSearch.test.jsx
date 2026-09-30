import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEmployeeSearch } from './useEmployeeSearch.js';
import { employeeService } from '../employee/employeeService.js';

vi.mock('../employee/employeeService.js', () => ({
  employeeService: { list: vi.fn() },
}));

describe('useEmployeeSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps the page to options labelled with number and name', async () => {
    employeeService.list.mockResolvedValueOnce({
      content: [{ id: 'emp-1', employeeNumber: 'E-101', firstName: 'Asha', lastName: 'Rao' }],
    });
    const { result } = renderHook(() => useEmployeeSearch());

    await act(() => result.current.search('ash'));

    expect(employeeService.list).toHaveBeenCalledWith({ q: 'ash', size: 10 });
    expect(result.current.options).toEqual([{ value: 'emp-1', label: 'E-101 - Asha Rao' }]);
    expect(result.current.error).toBeNull();
  });

  it('says why the picker is empty when the caller may not list employees', async () => {
    employeeService.list.mockRejectedValueOnce({ code: 'FORBIDDEN', isForbidden: true, status: 403 });
    const { result } = renderHook(() => useEmployeeSearch());

    await act(() => result.current.search(''));

    expect(result.current.options).toEqual([]);
    expect(result.current.error).toBe('You do not have permission to search employees.');
  });

  it('clears the reason on the next successful search', async () => {
    employeeService.list.mockRejectedValueOnce({ code: 'INTERNAL', message: 'Something went wrong' });
    employeeService.list.mockResolvedValueOnce({ content: [] });
    const { result } = renderHook(() => useEmployeeSearch());

    await act(() => result.current.search(''));
    expect(result.current.error).toBe('Something went wrong');

    await act(() => result.current.search(''));
    expect(result.current.error).toBeNull();
  });
});
