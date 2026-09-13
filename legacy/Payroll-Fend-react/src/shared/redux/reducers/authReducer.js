import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import { jwtDecode } from "jwt-decode";
import _ from 'lodash';

// thunk to extract the user details from token and update the state.
export const updateToken = createAsyncThunk(
    'authReducer/updateToken',
    async (token) => {
        var decodedToken = jwtDecode(token);
        console.log("decoded token", decodedToken);
        return decodedToken;
    }
);

const authReducer = createSlice({
    name: 'authReducer',
    initialState: {
        userDetails: { name: '', preferred_username: '', email: '' },
        role: '',
        userActions: [],
        token: '',
    },
    reducers: {
        resetAuthState: (state, action) => {
            state.token = '';
            state.userDetails = { name: '', preferred_username: '', email: '' };
            state.role = '';
            state.userActions = [];
        },
        updateUserDetails: (state, action) => {
            state.userDetails = action.payload;
        }
    },
    extraReducers: (builder) => {
        builder.addCase(updateToken.fulfilled, (state, action) => {

            state.userDetails = {
                name: action.payload.name,
                preferred_username: action.payload.preferred_username,
                email: action.payload.email,
                
            };

            state.token = action.meta.arg;
        })
    }
})

export const { resetAuthState, updateUserDetails } = authReducer.actions;

export default authReducer.reducer;