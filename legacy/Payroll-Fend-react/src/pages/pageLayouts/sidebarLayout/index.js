import { Outlet } from "react-router-dom";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import Footer from "./footer";
import Header from "./header";
import Sidebar from "./sidebar";
import { useSelector } from "react-redux";
import { useState } from 'react';
import { OrganizationProvider, useOrganization } from "../../../shared/organization/OrganizationContext";

function SidebarLayoutContent() {

    const globalReducer = useSelector(state => state.globalReducer);
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const { ready } = useOrganization();

    const toggleSidebar = () => {
        setIsSidebarOpen(!isSidebarOpen);
    };

    const closeSidebar = () => {
        setIsSidebarOpen(false);
    };

    // Every page under this layout reads organizationId from localStorage on mount;
    // holding the outlet here until it's resolved is what removes the "default-org-id" race.
    if (!ready) {
        return <Loader />;
    }

    return (

        <>
            <div className="d-flex flex-column flex-root app-root" id="kt_app_root">
                <div className="app-page flex-column flex-column-fluid" id="kt_app_page">
                    <Header toggleSidebar={toggleSidebar} />
                    <div className="app-wrapper flex-column flex-row-fluid" id="kt_app_wrapper">
                        <Sidebar isSidebarOpen={isSidebarOpen} closeSidebar={closeSidebar} />
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
            <SidebarLayoutContent />
        </OrganizationProvider>
    );
}