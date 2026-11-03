import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Select } from 'antd';
import { employeeService } from '../employee/employeeService.js';

/** How long typing must pause before the server is asked (D-80). */
export const SEARCH_DEBOUNCE_MS = 300;

/** How many matches one search returns; the user narrows by typing, never by scrolling a thousand rows. */
export const SEARCH_PAGE_SIZE = 20;

/** "First Last (EMP-001)", or whatever of it the row has. */
export function employeeOptionLabel(emp) {
  if (!emp) return '';
  const name = [emp.firstName, emp.lastName].filter(Boolean).join(' ').trim();
  if (name && emp.employeeNumber) return `${name} (${emp.employeeNumber})`;
  return name || emp.employeeNumber || '';
}

/**
 * Search-as-you-type employee picker for the leave screens (D-80). Each pause in typing asks
 * `GET /v1/employees?q=` for one page of matches, so a tenant of any size can pick anyone; the old
 * pickers loaded one page of 25 or 100 and silently missed the rest. A reply that arrives after a
 * newer search is dropped. The chosen employee keeps its label when a later search no longer
 * returns it.
 *
 * Works inside a `Form.Item` (which supplies `value` and `onChange`) or on its own.
 */
export function EmployeePicker({
  value,
  onChange,
  placeholder = 'Search employee by name or number',
  allowClear = true,
  style,
  id,
  ariaLabel,
  debounceMs = SEARCH_DEBOUNCE_MS,
  multiple = false,
}) {
  const [options, setOptions] = useState([]);
  // multiple (W-79.1): names of chosen employees, kept when a search does not return them; a
  // saved list of ids is named by reading each employee once.
  const [labels, setLabels] = useState({});
  const named = useRef(new Set());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [chosen, setChosen] = useState(null);
  const seq = useRef(0);
  const timer = useRef(null);
  const loaded = useRef(false);

  const runSearch = useCallback(async (q) => {
    const mine = ++seq.current;
    setLoading(true);
    setError(null);
    try {
      const res = await employeeService.list({ q: q || undefined, size: SEARCH_PAGE_SIZE });
      const rows = Array.isArray(res) ? res : res?.content || [];
      if (mine === seq.current) {
        setOptions(rows.map((emp) => ({ value: emp.id, label: employeeOptionLabel(emp) })));
      }
    } catch (err) {
      if (mine === seq.current) {
        setOptions([]);
        setError(
          err?.isForbidden
            ? 'You do not have permission to search employees.'
            : 'Employees could not be loaded.',
        );
      }
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  const search = useCallback(
    (q) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => runSearch(q), debounceMs);
    },
    [runSearch, debounceMs],
  );

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const loadFirstPage = () => {
    if (!loaded.current) {
      loaded.current = true;
      runSearch('');
    }
  };

  useEffect(() => {
    if (!multiple || !Array.isArray(value)) return;
    value
      .filter((empId) => !named.current.has(empId) && !labels[empId] && !options.some((o) => o.value === empId))
      .forEach(async (empId) => {
        named.current.add(empId);
        try {
          const emp = await employeeService.get(empId);
          setLabels((prev) => ({ ...prev, [empId]: employeeOptionLabel(emp) }));
        } catch {
          // left as its id; the server still holds the row
        }
      });
  }, [multiple, value, labels, options]);

  const handleChange = (next, option) => {
    if (multiple) {
      const picked = Array.isArray(option) ? option : [];
      setLabels((prev) => ({
        ...prev,
        ...Object.fromEntries(picked.filter((o) => o?.label).map((o) => [o.value, o.label])),
      }));
      onChange?.(next || [], option);
      return;
    }
    setChosen(next ? { value: next, label: option?.label } : null);
    onChange?.(next ?? undefined, option);
  };

  // The chosen employee stays labelled even when the latest search did not return it.
  let merged = options;
  if (multiple) {
    const missing = (Array.isArray(value) ? value : []).filter((v) => !options.some((o) => o.value === v));
    merged = [...missing.map((v) => ({ value: v, label: labels[v] || v })), ...options];
  } else if (chosen && chosen.value === value && !options.some((o) => o.value === chosen.value)) {
    merged = [chosen, ...options];
  }

  return (
    <Select
      id={id}
      aria-label={ariaLabel}
      mode={multiple ? 'multiple' : undefined}
      showSearch
      allowClear={allowClear}
      value={value}
      onChange={handleChange}
      onSearch={search}
      onFocus={loadFirstPage}
      onOpenChange={(open) => open && loadFirstPage()}
      filterOption={false}
      options={merged}
      loading={loading}
      placeholder={placeholder}
      notFoundContent={error || (loading ? 'Searching…' : 'No matching employee')}
      style={style}
    />
  );
}

EmployeePicker.propTypes = {
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.arrayOf(PropTypes.string)]),
  multiple: PropTypes.bool,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  allowClear: PropTypes.bool,
  style: PropTypes.object,
  id: PropTypes.string,
  ariaLabel: PropTypes.string,
  debounceMs: PropTypes.number,
};
