import { useEffect, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { apiClient } from '@shared/api/client';

/**
 * Where an entry for a month will post (D-144): `GET /v1/pay-inputs/posting/{period}`, which answers
 * `{ requested_period, posted_period, state }` from the same rule saving uses.
 *
 * - OPEN: posts to the month asked for.
 * - LOCKED: posts there too, but the month's run is locked or computed and must be recomputed.
 * - CLOSED: the month's run is approved; the entry is refused and `posted_period` is the month to use.
 */
export const POSTING_STATE = Object.freeze({ OPEN: 'OPEN', LOCKED: 'LOCKED', CLOSED: 'CLOSED' });

/** How long a grid waits after the last month change before asking (one call per distinct month). */
export const LOOKUP_DEBOUNCE_MS = 300;

export const postingService = {
  /** `period` is YYYY-MM. Resolves the bare reply (no envelope, snake_case). */
  async get(period) {
    const res = await apiClient.get(`/v1/pay-inputs/posting/${encodeURIComponent(period)}`);
    return res.data;
  },
};

/** "October 2026" from '2026-10'. */
export function monthName(period) {
  const d = dayjs(`${period}-01`);
  return d.isValid() ? d.format('MMMM YYYY') : String(period ?? '');
}

/**
 * The line an entry screen shows under its month, or null when there is nothing to say yet.
 * `tone` is 'info' (OPEN), 'warning' (LOCKED, amber) or 'error' (CLOSED, red).
 */
export function postingNote(posting) {
  if (!posting?.state) return null;
  const requested = monthName(posting.requested_period);
  const posted = monthName(posting.posted_period ?? posting.requested_period);
  switch (posting.state) {
    case POSTING_STATE.OPEN:
      return { tone: 'info', text: `Picked up by the ${posted} run` };
    case POSTING_STATE.LOCKED:
      return { tone: 'warning', text: `Picked up by the ${posted} run, which must be recomputed` };
    case POSTING_STATE.CLOSED:
      return { tone: 'error', text: `${requested} is closed. Enter this for ${posted}` };
    default:
      return null;
  }
}

/** Whether a posting answer refuses saving. */
export const isClosed = (posting) => posting?.state === POSTING_STATE.CLOSED;

/**
 * Posting answers for every distinct month in `periods` (YYYY-MM strings; blanks ignored), as an
 * object keyed by month. One request per distinct month, made `LOOKUP_DEBOUNCE_MS` after the list
 * last changed and remembered for the life of the component, so a 500-row grid all in October asks
 * once. A failed lookup (no `core.pay_input.read`, network) leaves that month out: the screen then
 * says nothing and the server still decides on save.
 */
export function usePostingPeriods(periods, { debounceMs = LOOKUP_DEBOUNCE_MS } = {}) {
  const cache = useRef(new Map());
  const [answers, setAnswers] = useState({});
  const key = [...new Set((periods ?? []).filter(Boolean))].sort().join(',');

  useEffect(() => {
    if (!key) return undefined;
    let live = true;
    const timer = setTimeout(() => {
      for (const period of key.split(',')) {
        let pending = cache.current.get(period);
        if (!pending) {
          pending = postingService.get(period).catch(() => {
            // Forget a failure, so a later change of month asks again.
            cache.current.delete(period);
            return null;
          });
          cache.current.set(period, pending);
        }
        pending.then((posting) => {
          if (live && posting) {
            setAnswers((prev) => (prev[period] === posting ? prev : { ...prev, [period]: posting }));
          }
        });
      }
    }, debounceMs);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [key, debounceMs]);

  return answers;
}

/** {@link usePostingPeriods} for one month: the answer, or null until it arrives. */
export function usePostingPeriod(period, options) {
  const answers = usePostingPeriods(period ? [period] : [], options);
  return (period && answers[period]) || null;
}
