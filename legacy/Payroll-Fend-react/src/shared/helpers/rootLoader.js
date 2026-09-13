import axios from "axios";
import _ from 'lodash';
import store from "../redux/store";
import { errorMsg } from "./msgHelper";
import { GlobalConst } from "../appConfig/globalConst";
import { updateUserDetails } from "../redux/reducers/authReducer";
import { setLoaderState } from "../redux/reducers/globalReducer";
// import { fetchDataStart, fetchDataSuccess, fetchDataFailure } from "../redux/slices/apiSlice";

export const rootLoader = async () => {
    let token = await localStorage.getItem("__t");
    let dataStore = await store.getState();
    // let authData =  dataStore.authReducer;

    if (!_.isEmpty(token) && _.isEmpty(dataStore.authReducer.userDetails.email)) {

        try {
            // store.dispatch(fetchDataStart());
            // setTimeout(async()=>{
            store.dispatch(setLoaderState(true));
            const response = await axios.get(`${GlobalConst.API_URL}/get-complete-profile`, {}); // Replace with your API endpoint

            const data = response.data;
            store.dispatch(setLoaderState(false));
            if (!_.isEmpty(data)) {
                await store.dispatch(updateUserDetails(data));
            }
            else {
                errorMsg("Unable to get user details. We were unable to retrieve the user details for some reason. The application will now log out.", true);
                // localStorage.removeItem("__t");
                // window.location.reload();
                return false;
            }
            return data; // Optionally return data for the component
        // }, 12000);
            // store.dispatch(fetchDataSuccess(data));

            
        } catch (error) {
            console.error("Error fetching data:", error);
            store.dispatch(setLoaderState(false));
            // store.dispatch(fetchDataFailure(error.message));
            // throw new Response("Failed to fetch data.", { status: 500 });
            errorMsg("Unable to get user details. We were unable to retrieve the user details for some reason. The application will now log out.", true);
            // localStorage.removeItem("__t");
            // window.location.reload();
            return false;
        }
    }
    else {
        return false;
    }
};