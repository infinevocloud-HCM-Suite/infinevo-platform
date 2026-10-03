import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert } from 'antd';
import { Link } from 'react-router-dom';
import dayjs from 'dayjs';
import { priorPayrollService } from './priorPayrollService';
import { currentFy } from '../tax/financialYear';

export function MidYearBanner({ fy = currentFy() }) {
  const [missing, setMissing] = useState([]);
  const [skipped, setSkipped] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    priorPayrollService
      .status(fy)
      .then((data) => {
        if (!active) return;
        setMissing(Array.isArray(data?.missing_periods) ? data.missing_periods : []);
        setSkipped(Boolean(data?.setup_step_skipped));
        setLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [fy]);

  if (loading || skipped || !missing.length) {
    return null;
  }

  const months = missing
    .map((p) => dayjs(`${p}-01`).format('MMM YYYY'))
    .join(', ');

  return (
    <Alert
      type="warning"
      showIcon
      style={{ marginBottom: 16 }}
      message={`Payroll for ${months} is not loaded. Tax already deducted in those months will be charged again.`}
      action={<Link to="/payroll/prior-payroll">Load prior payroll</Link>}
    />
  );
}

MidYearBanner.propTypes = {
  fy: PropTypes.string,
};
