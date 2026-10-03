import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from 'antd';
import { useCan } from '@shell/screens';
import { dashboardService } from './dashboardService.js';

/**
 * Summarises the payroll steps of `/setup` (W-47.5 §5, §13 decision 1): it counts the steps with
 * `module = PAYROLL` that are neither completed nor skipped and links to the checklist. It renders
 * nothing without `core.tenant.read`, when nothing is left, or when the call fails.
 */
export function SetupCard() {
  const canRead = useCan('core.tenant.read');
  const [counts, setCounts] = useState(null);

  useEffect(() => {
    if (!canRead) return undefined;
    let live = true;
    dashboardService
      .setupChecklist()
      .then((data) => {
        const payroll = (data?.steps ?? []).filter((s) => s.module === 'PAYROLL');
        const left = payroll.filter((s) => !s.completed && !s.skipped).length;
        if (live) setCounts({ left, total: payroll.length });
      })
      .catch(() => {
        if (live) setCounts(null);
      });
    return () => {
      live = false;
    };
  }, [canRead]);

  if (!canRead || !counts || counts.left === 0) return null;

  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      message={`${counts.left} of ${counts.total} payroll setup steps left`}
      action={<Link to="/setup">Open setup</Link>}
    />
  );
}
