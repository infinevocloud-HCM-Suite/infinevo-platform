import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import reportWebVitals from './reportWebVitals';
import { Provider } from 'react-redux';
import store from './shared/redux/store';

import './assets/css/plugins.bundle.css';
import './assets/css/styles.css';

import './shared/helpers/axiosInterceptor';

import { checkAuthToken } from './shared/redux/checkAuth';

const root = ReactDOM.createRoot(document.getElementById('root'));

const renderApp = async () => {
  await checkAuthToken();

  root.render(
      <Provider store={store}>
        <App />
      </Provider>
  );
};

renderApp();

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
