import PropTypes from 'prop-types';
import { useState, useEffect } from 'react';
import { setUnauthorizedHandler, setTenantSuspendedHandler } from '../shared/api/client.js';
import { onFeedFetch } from './navigation/useNavigation.js';
import { keycloak } from './auth/keycloak.js';
import { Suspended } from './screens/Suspended.jsx';

/**
 * Shell error boundary (W-45 §5).
 * Listens for tenant suspension and authorization failures from apiClient.
 * Renders Suspended full-page when the tenant is suspended.
 */
export function ShellBoundary({ children }) {
  const [suspended, setSuspended] = useState(false);

  useEffect(() => {
    let loginTriggered = false;
    setUnauthorizedHandler(() => {
      if (!loginTriggered && keycloak && typeof keycloak.login === 'function') {
        loginTriggered = true;
        keycloak.login();
      }
    });

    setTenantSuspendedHandler(() => {
      setSuspended(true);
    });

    const unsubscribeFeed = onFeedFetch(() => {
      setSuspended(false);
    });

    return () => {
      setUnauthorizedHandler(null);
      setTenantSuspendedHandler(null);
      unsubscribeFeed();
    };
  }, []);

  if (suspended) {
    return <Suspended />;
  }

  return children;
}

ShellBoundary.propTypes = {
  children: PropTypes.node,
};
