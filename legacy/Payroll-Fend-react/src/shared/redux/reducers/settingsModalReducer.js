import { createSlice } from "@reduxjs/toolkit";
// import _ from 'lodash';
 
const SettingsModalReducer = createSlice({
    name: 'SettingsModalReducer',
    initialState: {
        modalState: false
    },
    reducers: {
        setModalState: (state, action) => {
            state.modalState = action.payload;
        }
    }
})
 
export const { setModalState } = SettingsModalReducer.actions;
 
export default SettingsModalReducer.reducer;