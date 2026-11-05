/**
 * Text of the Employees page badge (W-84.2): "N of M employees" when the plan sets a limit,
 * "N employees" when it does not.
 */
export function limitBadgeText(used, limit) {
  if (typeof limit === 'number') {
    return `${used} of ${limit} employees`;
  }
  return `${used} employees`;
}

/** True when the plan sets a limit and every place on it is taken. */
export function limitReached(used, limit) {
  return typeof limit === 'number' && typeof used === 'number' && used >= limit;
}

/** The server's error code when a create or reactivate would pass the plan's limit. */
export const EMPLOYEE_LIMIT_REACHED = 'EMPLOYEE_LIMIT_REACHED';
