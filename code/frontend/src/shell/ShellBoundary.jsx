import PropTypes from 'prop-types';
import { useState, useEffect, useRef } from 'react';
import { apiClient, setUnauthorizedHandler, setTenantSuspendedHandler } from '../shared/api/client.js';
import { onFeedFetch } from './navigation/useNavigation.js';
import { keycloak } from './auth/keycloak.js';
import { Suspended } from './screens/Suspended.jsx';
import { AccessProblem } from './screens/AccessProblem.jsx';

/**
 * The query flag a re-login carries back from Keycloak (D-63). Its presence on arrival means the
 * last page already sent the browser to sign in once; a second 401 then stops on a screen instead of
 * redirecting again, which is what turned one bad answer into an endless sign-in loop.
 */
export const RELOGIN_PARAM = 'relogin';

function arrivedFromRelogin() {
  try {
    return new URLSearchParams(window.location.search).get(RELOGIN_PARAM) === '1';
  } catch {
    return false;
  }
}

/** The current address with `relogin=1`, kept on the URL Keycloak returns to. */
function reloginReturnUrl() {
  const url = new URL(window.location.href);
  url.hash = '';
  url.searchParams.set(RELOGIN_PARAM, '1');
  return url.toString();
}

/** Drops `relogin=1` from the address bar once the page has loaded with it. */
function forgetReloginFlag() {
  try {
    const url = new URL(window.location.href);
    if (url.searchParams.has(RELOGIN_PARAM)) {
      url.searchParams.delete(RELOGIN_PARAM);
      window.history.replaceState(window.history.state, document.title, url.toString());
    }
  } catch {
    // The flag only steers the next 401; leaving it in the address bar is harmless.
  }
}

/**
 * Shell error boundary (W-45 §5).
 * Listens for tenant suspension and authorization failures from apiClient.
 * Renders Suspended full-page when the tenant is suspended.
 *
 * A 401 sends the browser to sign in once (D-63). It does not when the server says no tenant is bound
 * — signing in again returns the same account to the same answer — nor when this page was itself the
 * return from a sign-in: both stop on {@link AccessProblem} with Sign out. The return flag is read
 * during render, not in the effect, so React's StrictMode double effect cannot lose it; and the first
 * successful API reply clears it, so a later, ordinary session expiry in the same tab re-logs in again.
 */
export function ShellBoundary({ children }) {
  const [suspended, setSuspended] = useState(false);
  const [accessProblem, setAccessProblem] = useState(null);
  const relogged = useRef(null);
  if (relogged.current === null) {
    relogged.current = arrivedFromRelogin();
  }

  useEffect(() => {
    let loginTriggered = false;
    forgetReloginFlag();

    // Once anything succeeds, the sign-in worked: a later 401 is a fresh expiry, not a loop.
    const responses = apiClient?.interceptors?.response;
    const successInterceptor = responses
      ? responses.use((response) => {
          relogged.current = false;
          return response;
        })
      : null;

    setUnauthorizedHandler((code) => {
      if (loginTriggered) {
        return;
      }
      if (code === 'TENANT_NOT_BOUND') {
        setAccessProblem('noTenant');
        return;
      }
      if (relogged.current) {
        setAccessProblem('authLoop');
        return;
      }
      if (keycloak && typeof keycloak.login === 'function') {
        loginTriggered = true;
        keycloak.login({ redirectUri: reloginReturnUrl() });
      }
    });

    setTenantSuspendedHandler(() => {
      setSuspended(true);
    });

    const unsubscribeFeed = onFeedFetch(() => {
      setSuspended(false);
    });

    return () => {
      if (responses && successInterceptor !== null) {
        responses.eject(successInterceptor);
      }
      setUnauthorizedHandler(null);
      setTenantSuspendedHandler(null);
      unsubscribeFeed();
    };
  }, []);

  if (accessProblem) {
    return <AccessProblem kind={accessProblem} />;
  }

  if (suspended) {
    return <Suspended />;
  }

  return children;
}

ShellBoundary.propTypes = {
  children: PropTypes.node,
};
