/**
 * Where a carried-out action can be opened (W-114.9). One line per `actionCode`; a code with no line
 * shows no link. Every path is checked against the route tables (`src/shell/routes.js`,
 * `src/core/index.js`). Later parts (W-114.17, .18, .20) add their lines here; the card does not change.
 */
const leaveRequest = (result) =>
  result.leaveRequestId ? '/me/leave/requests/' + encodeURIComponent(result.leaveRequestId) : null;
// A WFH request has no page of its own: the portal's WFH tab lists it (W-114.17).
const wfhTab = (result) => (result.wfhRequestId ? '/me/wfh' : null);

const LINKS = {
  'core.leave.apply': leaveRequest,
  'core.leave.withdraw': leaveRequest,
  'core.leave.cancel': leaveRequest,
  'core.wfh.apply': wfhTab,
  'core.wfh.withdraw': wfhTab,
  'core.wfh.cancel': wfhTab,
  'core.approval.decide': (result) =>
    result.instanceId ? '/approvals/' + encodeURIComponent(result.instanceId) : null,
  'core.employee.create': (result) =>
    result.employeeId ? '/employees/' + encodeURIComponent(result.employeeId) : null,
};

/** The words of each link, kept next to its path so the card holds no action-specific text. */
const LABELS = {
  'core.leave.apply': 'Open the leave request',
  'core.approval.decide': 'Open the approval',
  'core.employee.create': 'Open the employee to set the manager and the rest',
  'core.leave.withdraw': 'Open the leave request',
  'core.leave.cancel': 'Open the leave request',
  'core.wfh.apply': 'Open work from home',
  'core.wfh.withdraw': 'Open work from home',
  'core.wfh.cancel': 'Open work from home',
};

const has = (map, key) => typeof key === 'string' && Object.prototype.hasOwnProperty.call(map, key);

/** The path to open what `actionCode` created, or null when there is none (or the result lacks its id). */
export function linkFor(actionCode, result) {
  if (!has(LINKS, actionCode) || !result) return null;
  try {
    const path = LINKS[actionCode](result);
    return typeof path === 'string' && path ? path : null;
  } catch {
    return null;
  }
}

/**
 * The server's link to read the item before deciding (W-114.18), or null. Only a plain app path is
 * followed: one leading slash, letters, digits, dashes and slashes - never another site.
 */
export function viewLinkFor(link) {
  return typeof link === 'string' && /^\/[A-Za-z0-9][A-Za-z0-9\-/]*$/.test(link) ? link : null;
}

/** The text of that link. */
export function linkLabelFor(actionCode) {
  return has(LABELS, actionCode) ? LABELS[actionCode] : 'Open it';
}
