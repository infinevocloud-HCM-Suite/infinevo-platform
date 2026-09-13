import _ from 'lodash';
// export const GlobalConst = {
//     BASE_URL: `${(!_.isEmpty(process.env.PUBLIC_URL)) ? process.env.PUBLIC_URL : process.env.REACT_APP_BASE_URL}`,
//     API_URL: `${process.env.REACT_APP_API_URL}`,
//     QUOTE_URL: `${process.env.REACT_APP_QUOTE_API_URL}`,
//     SUPPORT_EMAIL: "info@infinevocloud.com"
// }

export const GlobalConst = {
    BASE_URL: `${(!_.isEmpty(process.env.PUBLIC_URL)) ? process.env.PUBLIC_URL : process.env.REACT_APP_BASE_URL}`,
    API_URL: `${process.env.REACT_APP_API_URL}`,
    AUTH_URL: `${process.env.REACT_APP_AUTH_URL}`,
    QUOTE_URL: `${process.env.REACT_APP_QUOTE_API_URL}`,
    SUPPORT_EMAIL: "info@infinevocloud.com",

    // Docker Dev
    CLIENT_ID: "react-app",
    REALM: "HRMS"

    
}