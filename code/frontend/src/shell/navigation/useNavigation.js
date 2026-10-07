import PropTypes from 'prop-types';
import { createContext, useContext, useState, useEffect, createElement } from 'react';
import { fetchNavigation } from './navigationService.js';
import { onTenantChange } from '../auth/keycloak.js';

/**
 * The navigation feed (W-12.3 §5): what the server says this caller may see, and nothing else.
 *
 *   GET /api/v1/navigation -> { items: [...ordered...], actions: [...caller's codes...], homePath }
 *
 * `homePath` (D-35) is where the server says this caller lands after login; null when absent.
 *
 * One store for the whole shell. It is fetched once after login and again whenever the
 * signed-in tenant changes - the signal for that is the Keycloak adapter's token callbacks
 * (keycloak.js, onTenantChange), which is the only place a new tenant_id claim can appear.
 * Until the first response arrives the feed is empty and `loading`; a failed call leaves it
 * empty. There is no default menu behind either state.
 */

export const NavigationContext = createContext(null);

const EMPTY = Object.freeze({
  items: [],
  actions: [],
  modules: [],
  tenantName: null,
  homePath: null,
  loading: false,
  loaded: false,
  error: null,
});

let state = EMPTY;
const subscribers = new Set();
const feedFetchSubscribers = new Set();

function publish(next) {
  state = next;
  subscribers.forEach((listener) => listener(state));
}

/** Test seam: forget everything, as if the page had just loaded. */
export function resetNavigationFeed() {
  publish(EMPTY);
}

/** Subscribe to navigation feed fetch attempts (e.g. to clear suspended boundary). */
export function onFeedFetch(listener) {
  feedFetchSubscribers.add(listener);
  return () => feedFetchSubscribers.delete(listener);
}

/** Fetches the feed and publishes it to every mounted hook. Rejects with the API error. */
export async function fetchNavigationFeed() {
  publish({ ...state, loading: true, error: null });
  feedFetchSubscribers.forEach((fn) => {
    try {
      fn();
    } catch {
      // ignore listener error
    }
  });
  try {
    const response = await fetchNavigation();
    const data = response?.data || {};
    publish({
      items: Array.isArray(data.items) ? data.items : [],
      actions: Array.isArray(data.actions) ? data.actions : [],
      modules: Array.isArray(data.modules) ? data.modules : [],
      tenantName: typeof data.tenantName === 'string' ? data.tenantName : null,
      homePath: typeof data.homePath === 'string' && data.homePath.startsWith('/') ? data.homePath : null,
      loading: false,
      loaded: true,
      error: null,
    });
    return state;
  } catch (err) {
    publish({
      items: [],
      actions: [],
      modules: [],
      tenantName: null,
      homePath: null,
      loading: false,
      loaded: true,
      error: err,
    });
    throw err;
  }
}

/**
 * Supplies a fixed feed to everything beneath it. The shell does not use this - it reads the
 * live store - but a screen test can hand its component exactly the actions it wants.
 */
export function NavigationProvider({ children, value }) {
  return createElement(NavigationContext.Provider, { value }, children);
}

NavigationProvider.propTypes = {
  children: PropTypes.node,
  value: PropTypes.shape({
    items: PropTypes.array,
    actions: PropTypes.oneOfType([PropTypes.array, PropTypes.instanceOf(Set)]),
    modules: PropTypes.array,
    tenantName: PropTypes.string,
    homePath: PropTypes.string,
    loading: PropTypes.bool,
    error: PropTypes.object,
  }).isRequired,
};

/**
 * The feed as the shell sees it: `items`, `actions`, `modules`, `tenantName`, `homePath`, `loading`, `error`,
 * and `refetch`.
 *
 * `loading` is true from the first render until the first fetch settles - not only while a
 * request is in flight - so the shell never reads "not asked yet" as "the feed is empty".
 *
 * The first mounted hook triggers the one fetch after login; every hook refetches when the
 * tenant changes. Inside a NavigationProvider the provided value is returned unchanged.
 */
export function useNavigation() {
  const provided = useContext(NavigationContext);
  const [live, setLive] = useState(state);

  useEffect(() => {
    if (provided) {
      return undefined;
    }
    subscribers.add(setLive);
    setLive(state);
    if (!state.loaded && !state.loading) {
      fetchNavigationFeed().catch(() => {});
    }
    const stopWatchingTenant = onTenantChange(() => {
      fetchNavigationFeed().catch(() => {});
    });
    return () => {
      subscribers.delete(setLive);
      stopWatchingTenant();
    };
  }, [provided]);

  const active = provided || live;
  return {
    items: active.items || [],
    actions: active.actions || [],
    modules: active.modules || [],
    tenantName: active.tenantName || null,
    homePath: active.homePath || null,
    loading: !!active.loading || (!provided && !active.loaded),
    error: active.error || null,
    refetch: fetchNavigationFeed,
  };
}
