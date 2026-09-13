import React, { useEffect, useState } from 'react';
import { Form, NavDropdown, Dropdown } from 'react-bootstrap';
// import { useSelector } from 'react-redux';
import { NavLink, useNavigate } from 'react-router-dom';
import { Tooltip } from 'antd';
// import userIcon from '../../../assets/images/user.png';
// import { getEmail, getName } from '../../../shared/helpers/mainHelper';
import { useLocation } from "react-router-dom";


// import { Tooltip } from 'bootstrap';

export default function Header() {
    // const authReducer = useSelector(state => state.authReducer);
    const [toggle, setToggle] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [searchActive, setSearchActive] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();



    // useEffect(() => {
    //     console.log(authReducer);
    // }, [authReducer]);



    // const handleSignoutClick = (e) => {
    //     e.preventDefault();
    //     localStorage.removeItem("__t");
    //     window.location.reload();
    // };

    // const handleSignoutClick = (e) => {
    //     e.preventDefault();
    //     localStorage.removeItem("__t");
    //     navigate('/login');
    // };

    // const handleSettingsClick = () => {
    //     console.log("Settings button clicked!");
    //     navigate('/settings');
    // };

    const handleSidebarMinimizeClick = async (e) => {
        async function getSidebarState() {
            return await localStorage.getItem("sidebarState");
        }
        let sidebarState = await getSidebarState();
        if (sidebarState === 'on') {
            setToggle(false);
            localStorage.setItem("sidebarState", "off");
            document.body.removeAttribute("data-kt-app-sidebar-minimize");
        }
        else {
            setToggle(true);
            localStorage.setItem("sidebarState", "on");
            document.body.setAttribute("data-kt-app-sidebar-minimize", "on");
        }
    };

    const handleSearchSubmit = (e) => {
        e.preventDefault();
        if (searchQuery.trim()) {
            navigate(`/search?query=${encodeURIComponent(searchQuery)}`);
            setSearchQuery('');
            setSearchActive(false);
        }
    };

    // const toggleSearch = () => {
    //     setSearchActive(!searchActive);
    // };

    return (
        <div id="kt_app_header" className="app-header d-flex flex-column flex-stack">
            <div className="d-flex align-items-center flex-stack flex-grow-1">
                <div className="app-header-logo d-flex align-items-center px-lg-11 mb-2"
                    id="kt_app_header_logo">

                    {/* <div className="btn btn-icon btn-active-color-primary w-35px h-35px ms-3 me-2 d-flex d-lg-none"
                        id="kt_app_sidebar_mobile_toggle">
                        <i className="ki-duotone ki-abstract-14 fs-2">
                            <span className="path1"></span>
                            <span className="path2"></span>
                        </i>
                    </div> */}

                    {/* Back button hidden on /all-settings */}
                    {location.pathname !== '/all-settings' ? (
                        <button
                            type="button"
                            className="btn btn-icon btn-sm btn-light-primary d-flex align-items-center me-2"
                            onClick={() => navigate('/all-settings')}
                            aria-label="Back to all settings"
                        >
                            <i className="ki-duotone ki-arrow-left fs-2">
                                <span className="path1"></span>
                                <span className="path2"></span>
                            </i>
                        </button>
                    ) : <button
                        type="button"
                        className="btn btn-icon btn-sm btn-light-primary d-flex align-items-center me-2"
                        onClick={() => navigate('/')}
                        aria-label="Home"
                    >
                        <i class="ki-duotone ki-home-3 fs-2">
                            <span class="path1"></span>
                            <span class="path2"></span>
                        </i>
                    </button>}

                    <h1 className='mb-0 d-flex align-items-center justify-content-center ps-2' style={{ borderLeft: '1px solid #ddd' }}>
                        <i class="ki-duotone ki-gear fs-1 me-2">
                            <i class="path1"></i>
                            <i class="path2"></i>
                        </i>
                        Settings
                    </h1>

                    {/* <div id="kt_app_sidebar_toggle"
                        onClick={handleSidebarMinimizeClick}
                        className={`app-sidebar-toggle btn btn-sm btn-icon btn-color-warning me-n2 d-none d-lg-flex ${toggle ? 'active' : ''}`}
                        data-kt-toggle="true" data-kt-toggle-state="active" data-kt-toggle-target="body"
                        data-kt-toggle-name="app-sidebar-minimize">
                        <i className="ki-duotone ki-exit-left fs-2x rotate-180">
                            <span className="path1"></span>
                            <span className="path2"></span>
                        </i>
                    </div> */}

                </div>









                <div className="app-navbar flex-grow-1 justify-content-end" id="kt_app_header_navbar">
                    {/* Search Bar */}


                    {/* <div id="kt_header_search" className="header-search d-flex align-items-center w-lg-350px ms-auto"
                        data-kt-search-keypress="true" data-kt-search-min-length="2" data-kt-search-enter="enter"
                        data-kt-search-layout="menu" data-kt-search-responsive="true" data-kt-menu-trigger="auto"
                        data-kt-menu-permanent="true" data-kt-menu-placement="bottom-start" data-kt-search="true"> */}

                    {/* <div data-kt-search-element="toggle" className="search-toggle-mobile d-flex d-lg-none align-items-center" onClick={toggleSearch}>
                            <div className="d-flex">
                                <i className="ki-duotone ki-magnifier fs-1"><span className="path1"></span><span className="path2"></span></i>
                            </div>
                        </div> */}

                    {/* <Form data-kt-search-element="form" className={`${searchActive ? 'd-block' : 'd-none'} d-lg-block w-100 position-relative mb-5 mb-lg-0`} onSubmit={handleSearchSubmit} autocomplete="off">
                            <input type="hidden" />

                            <i className="ki-duotone ki-magnifier search-icon fs-2 text-gray-500 position-absolute top-50 translate-middle-y ms-5">
                                <span className="path1"></span><span className="path2"></span>
                            </i>

                            <input
                                type="text"
                                className="search-input form-control form-control border-0 h-lg-40px ps-13"
                                name="search"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Search settings..."
                                data-kt-search-element="input"
                            />

                            <span className="search-spinner position-absolute top-50 end-0 translate-middle-y lh-0 d-none me-5" data-kt-search-element="spinner">
                                <span className="spinner-border h-15px w-15px align-middle text-gray-500"></span>
                            </span>

                            <span className="search-reset btn btn-flush btn-active-color-primary position-absolute top-50 end-0 translate-middle-y lh-0 d-none me-4" data-kt-search-element="clear">
                                <i className="ki-duotone ki-cross fs-2 fs-lg-1 me-0">
                                    <span className="path1"></span><span className="path2"></span>
                                </i>
                            </span>
                        </Form> */}

                    {/* <div data-kt-search-element="content" className="menu menu-sub menu-sub-dropdown py-7 px-7 overflow-hidden w-300px w-md-350px" data-kt-menu="true">
                            <div data-kt-search-element="wrapper">
                                <div data-kt-search-element="results" className="d-none">
                                    <div className="scroll-y mh-200px mh-lg-350px">
                                        <h3 className="fs-5 text-muted m-0 pb-5" data-kt-search-element="category-title">Users</h3>
                                        <a href="#" className="d-flex text-gray-900 text-hover-primary align-items-center mb-5">
                                            <div className="symbol symbol-40px me-4">
                                                <img src="/saul-html-pro/assets/media/avatars/300-6.jpg" alt="" />
                                            </div>
                                            <div className="d-flex flex-column justify-content-start fw-semibold">
                                                <span className="fs-6 fw-semibold">Karina Clark</span>
                                                <span className="fs-7 fw-semibold text-muted">Marketing Manager</span>
                                            </div>
                                        </a>
                                    </div>
                                </div>

                                <div className="" data-kt-search-element="main">
                                    <div className="d-flex flex-stack fw-semibold mb-4">
                                        <span className="text-muted fs-6 me-2">Recently Searched:</span>
                                        <div className="d-flex" data-kt-search-element="toolbar">
                                            <div data-kt-search-element="preferences-show" className="btn btn-icon w-20px btn-sm btn-active-color-primary me-2" data-bs-toggle="tooltip" title="Show search preferences">
                                                <i className="ki-duotone ki-setting-2 fs-2"><span className="path1"></span><span className="path2"></span></i>
                                            </div>
                                            <div data-kt-search-element="advanced-options-form-show" className="btn btn-icon w-20px btn-sm btn-active-color-primary me-n1" data-bs-toggle="tooltip" title="Show more search options">
                                                <i className="ki-duotone ki-down fs-2"></i>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="scroll-y mh-200px mh-lg-325px">
                                        <div className="d-flex align-items-center mb-5">
                                            <div className="symbol symbol-40px me-4">
                                                <span className="symbol-label bg-light">
                                                    <i className="ki-duotone ki-laptop fs-2 text-primary"><span className="path1"></span><span className="path2"></span></i>
                                                </span>
                                            </div>
                                            <div className="d-flex flex-column">
                                                <a href="#" className="fs-6 text-gray-800 text-hover-primary fw-semibold">BoomApp by Keenthemes</a>
                                                <span className="fs-7 text-muted fw-semibold">#45789</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div data-kt-search-element="empty" className="text-center d-none">
                                    <div className="pt-10 pb-10">
                                        <i className="ki-duotone ki-search-list fs-4x opacity-50"><span className="path1"></span><span className="path2"></span><span className="path3"></span></i>
                                    </div>
                                    <div className="pb-15 fw-semibold">
                                        <h3 className="text-gray-600 fs-5 mb-2">No result found</h3>
                                        <div className="text-muted fs-7">Please try again with a different query</div>
                                    </div>
                                </div>
                            </div>
                        </div> */}
                    {/* </div> */}





                    {/* <div class="app-navbar-item ms-3 ms-lg-4 me-lg-6">

                        <a href="/settings" class="btn btn-icon btn-custom btn-color-gray-600 btn-active-color-primary w-35px h-35px w-md-40px h-md-40px">
                          
                            <i class="ki-duotone ki-gear fs-2x"><span class="path1"></span><span class="path2"></span><span class="path3"></span><span class="path4"></span><span class="path5"></span></i>
                        </a>

                    </div> */}

                    <div className="app-navbar-item ms-3 ms-lg-4 me-lg-6">
                        <Tooltip
                            title="Close"
                            placement="bottom"
                            color="#bad3f0ff" // KI dark color
                            overlayClassName="kt-antd-tooltip" // Optional custom class
                        >
                            <button
                                className="btn btn-icon btn-custom btn-color-gray-600 btn-active-color-primary w-35px h-35px w-md-40px h-md-40px"
                                // onClick={() => navigate(-1)}
                                onClick={() => navigate('/onboarding-dashboard')}
                                style={{ cursor: "pointer" }}
                            >
                                <span className="svg-icon svg-icon-2x">
                                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <rect opacity="0.5" x="6" y="17.3137" width="16" height="2" rx="1" transform="rotate(-45 6 17.3137)" fill="currentColor" />
                                        <rect x="7.41422" y="6" width="16" height="2" rx="1" transform="rotate(45 7.41422 6)" fill="currentColor" />
                                    </svg>
                                </span>
                            </button>
                        </Tooltip>
                    </div>


                </div>
            </div>
        </div>
    );
}