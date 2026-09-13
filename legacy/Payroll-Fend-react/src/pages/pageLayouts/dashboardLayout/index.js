import _ from 'lodash';
import Header from "./header";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { useDispatch, useSelector } from "react-redux";
import { Outlet } from "react-router-dom";
import Footer from "./footer";
import Swal from 'sweetalert2';
import withReactContent from "sweetalert2-react-content";

const MySwal = withReactContent(Swal);

export default function DashboardLayout() {
    const dispatch = useDispatch();
    const loaderState = useSelector(state => state.globalReducer.loaderState);
    
    return (
        <>
            <div className="d-flex flex-column flex-root">
                <div className="page d-flex flex-row flex-column-fluid">
                  
                    
                    <div className="wrapper d-flex flex-column flex-row-fluid" id="kt_wrapper">
                        <Header />
                        <Outlet />
                        <Footer />
                    </div>
                </div>
            </div>

            {loaderState && <Loader />}
        </>
    );
}