import PropTypes from 'prop-types';
import { useEffect, useRef, useState } from 'react';
import { Select, Spin } from 'antd';
import { projectService } from './projectService.js';

export const MIN_CHARS = 2;
const DEBOUNCE_MS = 300;

/**
 * Employee search over `/v1/hrms/employees/assignable?q=` (W-48.1 §5). Debounced; nothing is asked under two
 * characters. `initialLabel` shows the current choice's name before any search has run.
 */
export function EmployeePicker({ value, onChange, initialLabel, placeholder = 'Search employee', ...rest }) {
  const [options, setOptions] = useState(value && initialLabel ? [{ value, label: initialLabel }] : []);
  const [loading, setLoading] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  const onSearch = (q) => {
    clearTimeout(timer.current);
    if (!q || q.trim().length < MIN_CHARS) return;
    timer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const list = (await projectService.assignable(q.trim())) || [];
        setOptions(
          list.map((e) => ({
            value: e.employee_id,
            label: e.employee_number ? `${e.name} (${e.employee_number})` : e.name,
          }))
        );
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    }, DEBOUNCE_MS);
  };

  return (
    <Select
      showSearch
      allowClear
      filterOption={false}
      value={value}
      onChange={onChange}
      onSearch={onSearch}
      options={options}
      placeholder={placeholder}
      notFoundContent={loading ? <Spin size="small" /> : null}
      style={{ width: '100%' }}
      {...rest}
    />
  );
}

EmployeePicker.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  initialLabel: PropTypes.string,
  placeholder: PropTypes.string,
};
