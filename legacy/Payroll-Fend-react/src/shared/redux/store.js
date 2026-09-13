import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./reducers/authReducer";
import globalReducer from "./reducers/globalReducer";


export default configureStore({
    reducer: {
        authReducer: authReducer,
        globalReducer: globalReducer,

    },
})