import axios from 'axios';
import { jwtDecode } from "jwt-decode";
import moment from 'moment';
import _ from 'lodash';
import { GlobalConst } from '../appConfig/globalConst';
import { clearAddEmployeeDraft } from './addEmployeeDraft';
let instance = axios.create();

// import { useDispatch } from 'react-redux';
// import { resetAuthState } from '../redux/reducers/authReducer';

axios.interceptors.request.use(
    async (config) => {
        const token = await localStorage.getItem('__t');
        if (token) {
            //check if the token has expired
            var decodedToken = jwtDecode(token);
            // console.log("I am the token expiry", checkTokenExpiry(decodedToken.exp));
            // console.log("I am decoded token", decodedToken);

            if (checkTokenExpiry(decodedToken.exp)) {
                console.log("Token expired need to get new token")
                let newToken = await getNewToken();
                config.headers['Authorization'] = `Bearer ${newToken}`;
            }
            else {
                config.headers['Authorization'] = `Bearer ${token}`;
            }

        }
        // config.headers['Content-Type'] = 'application/json';
        return config;
    },
    error => {
        Promise.reject(error)
    }
)

const checkTokenExpiry = (expiryValue) => {
    let a = moment(expiryValue * 1000), b = moment();
    if (a.diff(b, "minutes") <= 0) {
        return true;
    }
    else {
        return false;
    }
}

const getNewToken = async () => {
    // let dispatch = useDispatch();
    // "client_secret": GlobalConst.CLIENT_SECRET,
    // console.log("I am in the new token ");
    // const payload = qs.stringify({
    //     "grant_type": "refresh_token", // refresh_token
    //     "client_id": GlobalConst.CLIENT_ID,
    //     "refresh_token": localStorage.getItem("__r")
    // });
    // const headers = { headers: { "Authorization": `Bearer ${localStorage.getItem("__t")}` } };
    const refreshToken = localStorage.getItem("__r");

    const params = new URLSearchParams();
    params.append("grant_type", "refresh_token");
    params.append("client_id", GlobalConst.CLIENT_ID);
    params.append("refresh_token", refreshToken);

    return instance.post(`${GlobalConst.AUTH_URL}/realms/${GlobalConst.REALM}/protocol/openid-connect/token`, params, { headers: { "Content-Type": "application/x-www-form-urlencoded" } })
        .then(async (op) => {
            if (!_.isEmpty(op) && !_.isEmpty(op.data)) {
                // console.log("I am the op::", op)
                // let tokenUpdated = false;
                if (op.data.access_token) {
                    await localStorage.setItem("__t", op.data.access_token);
                    await localStorage.setItem("__r", op.data.refresh_token);
                }
                return op.data.access_token;
                // dispatch(updateToken(op.data.access_token));
                // setLoginModalStatus(false);
            }
        })
        .catch(e => {

            // if (!_.isEmpty(e) && !_.isEmpty(e.response) && !_.isEmpty(e.response.status) && e.response.status == 400) {
            // console.log("I am the catch")
            // dispatch(resetAuthState());
            // localStorage.clear();

            // capture the user url before loging out the user
            // const urlParams = new URLSearchParams(window.location.search);
            //   const logoutValue = urlParams.get('logout');

            localStorage.removeItem('__t');
            localStorage.removeItem('__r');
            localStorage.removeItem('userType'); // clear portal type on forced logout
            clearAddEmployeeDraft();
            // deleteCookie();
            localStorage.setItem('redirect_url', window.location);

            window.location.reload();

            // }
            console.log("exception: ", e.response);
        });
}



