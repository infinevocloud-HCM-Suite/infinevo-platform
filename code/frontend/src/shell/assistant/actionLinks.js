/**
 * Where a carried-out action can be opened (W-114.9). One line per `actionCode`; a code with no line
 * shows no link. Every path is checked against the route tables (`src/shell/routes.js`,
 * `src/core/index.js`). Later parts (W-114.17, .18, .20) add their lines here; the card does not change.
 */
const LINKS = {
  'core.leave.apply': (result) =>
    result.leaveRequestId ? '/me/leave/requests/' + encodeURIComponent(result.leaveRequestId) : null,
};

/** The words of each link, kept next to its path so the card holds no action-specific text. */
const LABELS = {
  'core.leave.apply': 'Open the leave request',
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

/** The text of that link. */
export function linkLabelFor(actionCode) {
  return has(LABELS, actionCode) ? LABELS[actionCode] : 'Open it';
}
