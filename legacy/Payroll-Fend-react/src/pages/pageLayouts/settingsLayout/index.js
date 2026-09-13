import { Outlet } from "react-router-dom";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import Footer from "./footer";
import Header from "./header";
import Sidebar from "./sidebar";
import { useSelector } from "react-redux";
import { OrganizationProvider, useOrganization } from "../../../shared/organization/OrganizationContext";

function SettingsLayoutContent() {

    const globalReducer = useSelector(state => state.globalReducer);
    const { ready } = useOrganization();

    if (!ready) {
        return <Loader />;
    }

    return (

        <>
            <div className="d-flex flex-column flex-root app-root" id="kt_app_root">
                <div className="app-page flex-column flex-column-fluid" id="kt_app_page">
                    <Header />
                    <div className="app-wrapper flex-column flex-row-fluid" id="kt_app_wrapper">
                        <Sidebar />
                        <div className="app-main flex-column flex-row-fluid" id="kt_app_main">
                            <div className="d-flex flex-column flex-column-fluid">
                                <Outlet />
                            </div>
                            <Footer />
                        </div>
                    </div>
                </div>
            </div>
            {globalReducer.loaderState && <Loader />}
        </>
    );
}

export default function SidebarLayout() {
    return (
        <OrganizationProvider mode="admin">
            <SettingsLayoutContent />
        </OrganizationProvider>
    );
}