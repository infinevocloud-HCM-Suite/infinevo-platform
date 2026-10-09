/* global process */
import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';

import { store } from '@shell/store';
import { AppShell } from '@shell/AppShell';
import { initAuth, getValidToken } from '@shell/auth/keycloak';
import { theme } from '@shared/theme';
import { setTokenProvider } from '@shared/api/client';
import { publicRoutes } from '@core';

/**
 * Bootstrap entry point for the frontend application.
 *
 * For public paths (W-46.7 §5a, e.g. /invitations/accept), renders the public component
 * directly inside ConfigProvider + BrowserRouter without calling initAuth(), Keycloak,
 * Redux store, or AppShell.
 *
 * For all other paths, initAuth() is called before anything renders (W-10).
 */
export function bootstrap(
  rootElement = typeof document !== 'undefined' ? document.getElementById('root') : null,
  pathname = typeof window !== 'undefined' ? window.location.pathname : '/',
) {
  if (!rootElement) return null;

  // A mail client may add a trailing slash; the invitation link must still skip sign-in (D-62).
  const path = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  const publicRoute = Array.isArray(publicRoutes)
    ? publicRoutes.find((r) => r.path === path)
    : null;

  if (publicRoute) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
      <React.StrictMode>
        <ConfigProvider theme={theme}>
          <BrowserRouter>
            {publicRoute.element}
          </BrowserRouter>
        </ConfigProvider>
      </React.StrictMode>,
    );
    return Promise.resolve(root);
  }

  return initAuth().then(() => {
    setTokenProvider(getValidToken);

    const root = ReactDOM.createRoot(rootElement);
    root.render(
      <React.StrictMode>
        <Provider store={store}>
          <ConfigProvider theme={theme}>
            <BrowserRouter>
              <AppShell />
            </BrowserRouter>
          </ConfigProvider>
        </Provider>
      </React.StrictMode>,
    );
    return root;
  });
}

// Auto-bootstrap when running in the browser
if (typeof document !== 'undefined' && document.getElementById('root')) {
  const isVitest = typeof process !== 'undefined' && Boolean(process.env?.VITEST);
  if (!isVitest) {
    bootstrap();
  }
}
