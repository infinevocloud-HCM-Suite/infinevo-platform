import { useCallback, useState } from 'react';
import { employeeService } from '../employee/employeeService.js';

/**
 * Employee options for a picker `Select` (delegate, reassign, named approver).
 *
 * The search calls `GET /v1/employees`, which needs `core.employee.read`. A caller without it
 * used to get an empty picker and no reason; `error` now carries one for `notFoundContent`.
 *
 * @returns {{ options: Array<{ value: string, label: string }>, loading: boolean, error: string|null, search: (query?: string) => Promise<void> }}
 */
export function useEmployeeSearch() {
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const search = useCallback(async (query = '') => {
    setLoading(true);
    setError(null);
    try {
      const res = await employeeService.list({ q: query, size: 10 });
      setOptions(
        (res?.content || []).map((e) => ({
          value: e.id,
          label: `${e.employeeNumber} - ${[e.firstName, e.lastName].filter(Boolean).join(' ')}`,
        })),
      );
    } catch (err) {
      setOptions([]);
      setError(
        err?.isForbidden
          ? 'You do not have permission to search employees.'
          : err?.message || 'Employees could not be loaded.',
      );
    } finally {
      setLoading(false);
    }
  }, []);

  return { options, loading, error, search };
}
