import { createSlice } from "@reduxjs/toolkit";
// import _ from 'lodash';

const globalReducer = createSlice({
    name: 'globalReducer',
    initialState: {
        loaderState: false
    },
    reducers: {
        setLoaderState: (state, action) => {
            state.loaderState = action.payload;
        }
    }
})

export const { setLoaderState } = globalReducer.actions;

export default globalReducer.reducer;