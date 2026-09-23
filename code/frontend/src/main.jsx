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

// Authentication is resolved before anything renders (W-10). `login-required` sends an
// unauthenticated visitor to Keycloak, so no part of the app ever paints for someone who
// is not logged in - there is no guard component to forget on a route.
//
// A .then chain rather than top-level await: Vite's default build target is `modules`,
// which does not include top-level await, and the browser matrix is not worth widening
// for one call.
initAuth().then(() => {
  setTokenProvider(getValidToken);

  ReactDOM.createRoot(document.getElementById('root')).render(
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
});
