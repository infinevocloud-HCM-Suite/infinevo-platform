import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Select } from 'antd';
import { claimService } from './claimService.js';
import { employeeLabel } from './claimLabels.js';

/**
 * Employee picker over core's `GET /v1/employees?q=` (W-47.4 §5), shared by the claim list filter,
 * the deduction list filter and the deduction grid. A failed search leaves the options empty; the
 * picker never invents rows.
 */
export function EmployeeSelect({ value, onChange, placeholder = 'Search employee', status, style, ariaLabel }) {
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);

  const search = useCallback(async (q) => {
    const mine = ++seq.current;
    setLoading(true);
    try {
      const rows = await claimService.searchEmployees(q);
      if (mine === seq.current) {
        setOptions((rows || []).map((emp) => ({ value: emp.id, label: employeeLabel(emp) })));
      }
    } catch {
      if (mine === seq.current) setOptions([]);
    } finally {
      if (mine === seq.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    search('');
  }, [search]);

  return (
    <Select
      showSearch
      allowClear
      value={value}
      onChange={(v) => onChange?.(v ?? undefined)}
      onSearch={search}
      filterOption={false}
      options={options}
      loading={loading}
      placeholder={placeholder}
      status={status}
      style={style}
      aria-label={ariaLabel}
    />
  );
}

EmployeeSelect.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  status: PropTypes.string,
  style: PropTypes.object,
  ariaLabel: PropTypes.string,
};
