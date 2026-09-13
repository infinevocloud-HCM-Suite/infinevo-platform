// src/redux/actions/globalActions.js
export const setAuthLoading = (isLoading) => ({
  type: 'SET_AUTH_LOADING',
  payload: isLoading
});

export const registerSuccess = (user) => ({
  type: 'REGISTER_SUCCESS',
  payload: user
});