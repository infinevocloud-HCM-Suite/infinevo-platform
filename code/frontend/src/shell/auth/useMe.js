import { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client.js';
import { onTenantChange } from './keycloak.js';

/**
 * Who is signed in, as the server says (W-73.1 §5):
 *
 *   GET /api/v1/me -> { displayName, roles: [...codes...], email, welcomeSeen, ... }
 *
 * `welcomeSeen` (W-73.8) is false only when the server says so in as many words: a failed call,
 * or a reply without it, counts as seen, so nobody is held on the welcome page by an outage.
 *
 * One store for the whole shell, like the navigation feed: fetched once after login, again when the
 * signed-in tenant changes, and again whenever the header asks (an act-as session starting or
 * stopping changes who "me" is). Until the first response arrives `displayName` is empty and
 * `roles` is empty; a failed call leaves both empty - the header then shows the token's name and
 * no chips, never an error.
 */

const EMPTY = Object.freeze({
  displayName: '',
  roles: [],
  email: null,
  welcomeSeen: true,
  loading: false,
  loaded: false,
  error: null,
});

let state = EMPTY;
const subscribers = new Set();

function publish(next) {
  state = next;
  subscribers.forEach((listener) => listener(state));
}

/** Test seam: forget everything, as if the page had just loaded. */
export function resetMe() {
  publish(EMPTY);
}

/** Fetches `/me` and publishes it to every mounted hook. Rejects with the API error. */
export async function fetchMe() {
  publish({ ...state, loading: true, error: null });
  try {
    const response = await apiClient.get('/v1/me');
    const data = response?.data || {};
    publish({
      displayName: typeof data.displayName === 'string' ? data.displayName : '',
      roles: Array.isArray(data.roles) ? data.roles.filter((r) => typeof r === 'string') : [],
      email: typeof data.email === 'string' ? data.email : null,
      welcomeSeen: data.welcomeSeen !== false,
      loading: false,
      loaded: true,
      error: null,
    });
    return state;
  } catch (err) {
    publish({ ...EMPTY, loaded: true, error: err });
    throw err;
  }
}

/**
 * Dismisses the welcome page (W-73.8): `PUT /api/v1/me/welcome-seen`. Every mounted hook sees
 * `welcomeSeen: true` at once, before the server answers, so the root path stops sending the user
 * back to `/welcome`; a failed write rejects, and the page is shown again on the next sign-in.
 */
export async function markWelcomeSeen() {
  publish({ ...state, welcomeSeen: true });
  await apiClient.put('/v1/me/welcome-seen');
}

/**
 * `displayName`, `roles`, `email`, `welcomeSeen`, `loading`, `error` and `refetch`. The first mounted hook
 * triggers the one fetch after login; every hook refetches when the tenant changes.
 */
export function useMe() {
  const [live, setLive] = useState(state);

  useEffect(() => {
    subscribers.add(setLive);
    setLive(state);
    if (!state.loaded && !state.loading) {
      fetchMe().catch(() => {});
    }
    const stopWatchingTenant = onTenantChange(() => {
      fetchMe().catch(() => {});
    });
    return () => {
      subscribers.delete(setLive);
      stopWatchingTenant();
    };
  }, []);

  return {
    displayName: live.displayName || '',
    roles: live.roles || [],
    email: live.email || null,
    welcomeSeen: live.welcomeSeen !== false,
    loading: !!live.loading || !live.loaded,
    error: live.error || null,
    refetch: fetchMe,
  };
}
