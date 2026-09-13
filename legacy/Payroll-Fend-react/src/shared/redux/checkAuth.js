import { updateToken } from './reducers/authReducer';
import store from './store';
import { clearAddEmployeeDraft } from '../helpers/addEmployeeDraft';

export const checkAuthToken = async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const logoutValue = urlParams.get('logout');
  // const plan = urlParams.get("plan");

  // if (plan) {
  //   localStorage.setItem('plan', plan);
  // }

  // let hasLoggedOut = await localStorage.getItem('logout');

  if (logoutValue && logoutValue === 'yes') {
    localStorage.removeItem('__t');
    localStorage.removeItem('userType'); // clear portal type on URL-requested logout
    clearAddEmployeeDraft();
    // deleteCookie();
    // window.location.reload();
    // return false;
  }

  const token = localStorage.getItem('__t');
  // Read the parameter from the URL
  // const urlParams = new URLSearchParams(window.location.search);
  const paramValue = urlParams.get('redirect_url'); // Replace 'yourParamName' with the actual parameter name

  // Store the parameter value in localStorage if it exists
  if (paramValue) {
    localStorage.setItem('redirect_url', paramValue); // Replace 'yourStorageKey' with your preferred key name
  }



  if (token) {
    // const refreshToken = localStorage.getItem('__r');
    // console.log("I got the token ");
    await store.dispatch(updateToken(token));
    // const storeState = store.getState();
    // // console.log("I am the store state", storeState);
    // if (_.isEmpty(storeState.authReducer.userDetails.email)) {
    //   axios
    //     .post(`${GlobalConst.API_URL}/api/v1/entrance/user`, {})
    //     .then(op => {
    //       if (!_.isEmpty(op) && !_.isEmpty(op.data) && !_.isEmpty(op.data.message) && op.data.message === 'USER_INFO') {
    //         store.dispatch(updateUserDetails(op.data.result));
    //       }
    //     })
    //     .catch(()=>{
    //       errorMsg("Unable to get user details", "We were unable to get the user details, please login again.", true);
    //       localStorage.removeItem('__t');
    //     })
    // }
    // document.cookie = `session_token=${token};domain=${GlobalConst.COOKIE_DOMAIN};path=/;secure`;
    // document.cookie = `refresh_token=${refreshToken};domain=${GlobalConst.COOKIE_DOMAIN};path=/;secure`;
  }

};