import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { apiClient } from '../../shared/api/client.js';
import { keycloak } from '../auth/keycloak.js';

export const NavigationContext = createContext(null);

let globalNavigationState = {
  items: [],
  actions: [],
  loading: false,
  error: null,
};

const subscribers = new Set();

function notifySubscribers() {
  subscribers.forEach((callback) => callback(globalNavigationState));
}

export function setNavigationFeed(feed) {
  globalNavigationState = {
    items: feed?.items || [],
    actions: feed?.actions || [],
    loading: false,
    error: null,
  };
  notifySubscribers();
}

/**
 * Fetches the navigation feed from GET /api/v1/navigation (W-12.3 §5).
 */
export async function fetchNavigationFeed() {
  globalNavigationState = { ...globalNavigationState, loading: true, error: null };
  notifySubscribers();
  try {
    const endpoint = apiClient.defaults?.baseURL?.endsWith('/api')
      ? '/v1/navigation'
      : '/api/v1/navigation';
    const response = await apiClient.get(endpoint);
    const data = response.data || {};
    globalNavigationState = {
      items: data.items || [],
      actions: data.actions || [],
      loading: false,
      error: null,
    };
    notifySubscribers();
    return globalNavigationState;
  } catch (err) {
    globalNavigationState = {
      items: [],
      actions: [],
      loading: false,
      error: err,
    };
    notifySubscribers();
    throw err;
  }
}

/**
 * Navigation provider to wrap the shell or provide test fixtures.
 */
export function NavigationProvider({ children, value }) {
  const [state, setState] = useState(value || globalNavigationState);

  useEffect(() => {
    if (value) {
      setState(value);
      return;
    }
    const update = (newState) => setState(newState);
    subscribers.add(update);
    return () => subscribers.delete(update);
  }, [value]);

  return (
    <NavigationContext.Provider value={value || state}>
      {children}
    </NavigationContext.Provider>
  );
}

/**
 * Hook to access navigation feed (items, actions, loading, error, refetch).
 * Fetches once after login and refetches on tenant switch.
 */
export function useNavigation() {
  const context = useContext(NavigationContext);
  const [localState, setLocalState] = useState(context || globalNavigationState);
  const tenantRef = useRef(keycloak?.tokenParsed?.tenant_id);

  useEffect(() => {
    if (context) {
      setLocalState(context);
      return;
    }

    const update = (newState) => setLocalState(newState);
    subscribers.add(update);

    // Initial fetch once after login if not already populated or loading
    if (globalNavigationState.items.length === 0 && !globalNavigationState.loading) {
      fetchNavigationFeed().catch(() => {});
    }

    return () => subscribers.delete(update);
  }, [context]);

  // Refetch on tenant change
  useEffect(() => {
    if (context) return;

    const checkTenant = () => {
      const activeTenant = keycloak?.tokenParsed?.tenant_id;
      if (activeTenant && activeTenant !== tenantRef.current) {
        tenantRef.current = activeTenant;
        fetchNavigationFeed().catch(() => {});
      }
    };

    if (typeof window !== 'undefined') {
      window.addEventListener('infinevo:tenant-switched', checkTenant);
      return () => {
        window.removeEventListener('infinevo:tenant-switched', checkTenant);
      };
    }
  }, [context]);

  const active = context || localState;
  return {
    items: active.items || [],
    actions: active.actions || [],
    loading: !!active.loading,
    error: active.error || null,
    refetch: fetchNavigationFeed,
  };
}
