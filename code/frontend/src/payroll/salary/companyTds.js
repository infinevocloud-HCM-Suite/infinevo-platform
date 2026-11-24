import { useEffect, useState } from 'react';
import { statutoryService } from '../settings/statutoryService.js';

/**
 * Whether the company TDS switch (Payroll › Settings › TDS, D-141) is off. While it is off the employee's
 * TDS applicable tick changes nothing, so the card and both drawers grey it out and say so (D-145).
 * A failed read counts as on: the tick stays usable, as it was before the switch existed.
 */
export function useCompanyTdsOff(active = true) {
  const [off, setOff] = useState(false);

  useEffect(() => {
    if (!active) return undefined;
    let live = true;
    Promise.resolve()
      .then(() => statutoryService.get('tds'))
      .then((data) => {
        if (live) setOff(data?.enabled === false);
      })
      .catch(() => {
        if (live) setOff(false);
      });
    return () => {
      live = false;
    };
  }, [active]);

  return off;
}
