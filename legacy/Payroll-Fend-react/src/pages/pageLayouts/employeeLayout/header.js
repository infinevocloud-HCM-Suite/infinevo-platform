import React, { useEffect, useState } from 'react';
import { Form, NavDropdown, Dropdown } from 'react-bootstrap';
import { useSelector } from 'react-redux';
import { NavLink, useNavigate } from 'react-router-dom';
import { Tooltip } from 'antd';
import userIcon from '../../../assets/images/user.png';
import { getEmail, getName } from '../../../shared/helpers/mainHelper';
import axios from 'axios';
import { GlobalConst } from '../../../shared/appConfig/globalConst';
import { errorMsg } from '../../../shared/helpers/msgHelper';
import { useDispatch } from "react-redux";
import { resetAuthState } from '../../../shared/redux/reducers/authReducer';
import { clearAddEmployeeDraft } from '../../../shared/helpers/addEmployeeDraft';

export default function Header() {
    const dispatch = useDispatch();

    const authReducer = useSelector(state => state.authReducer);
    const [toggle, setToggle] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [searchActive, setSearchActive] = useState(false);
    const [orgDropdownOpen, setOrgDropdownOpen] = useState(false);
    const [orgSearchQuery, setOrgSearchQuery] = useState('');
    // const [selectedOrg, setSelectedOrg] = useState({ 
    //     id: '60045990091', 
    //     name: 'Bellilon Pvt Ltd', 
    //     status: 'TRIAL' 
    // });

    const [selectedOrg, setSelectedOrg] = useState(null);
    const [organizations, setOrganizations] = useState([]);
    const [loadingOrgs, setLoadingOrgs] = useState(false);
    const [error, setError] = useState(null);
    const [isMobile, setIsMobile] = useState(window.innerWidth < 992);
    const navigate = useNavigate();

    useEffect(() => {
        console.log(authReducer);

        const handleResize = () => {
            setIsMobile(window.innerWidth < 992);
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [authReducer]);


  


   









    const handleSignoutClick = (e) => {
        e.preventDefault();
        localStorage.removeItem("__t");
        localStorage.removeItem("__r");  // clear token from storage
        localStorage.removeItem("organizationId"); // clear selected org
        localStorage.removeItem("redirect_url"); // clear redirect url
        localStorage.removeItem("userType"); // clear portal type
        clearAddEmployeeDraft();
        dispatch(resetAuthState());       // clear redux state
        navigate("/employeePortalLogin");               // redirect
    };

   

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

    const toggleSearch = () => {
        setSearchActive(!searchActive);
    };



  
  




    return (
        <div id="kt_app_header" className="app-header d-flex flex-column flex-stack">
            <div className="d-flex align-items-center flex-stack flex-grow-1">
                <div className="app-header-logo d-flex align-items-center flex-stack px-lg-11 mb-2"
                    id="kt_app_header_logo">
                    <div className="btn btn-icon btn-active-color-primary w-35px h-35px ms-3 me-2 d-flex d-lg-none active" id="kt_app_sidebar_mobile_toggle">
                        <i className="ki-duotone ki-abstract-14 fs-2"><span className="path1"></span><span className="path2"></span></i>
                    </div>
                    <a href="/" className="app-sidebar-logo">
                        <h2 className='mb-0'>INFINEVOCLOUD</h2>
                    </a>

                    <div id="kt_app_sidebar_toggle" onClick={handleSidebarMinimizeClick}
                        className={`app-sidebar-toggle btn btn-sm btn-icon btn-color-warning me-n2 d-none d-lg-flex ${toggle ? 'active' : ''}`}
                        data-kt-toggle="true" data-kt-toggle-state="active" data-kt-toggle-target="body"
                        data-kt-toggle-name="app-sidebar-minimize">
                        <i className="ki-duotone ki-exit-left fs-2x rotate-180">
                            <span className="path1"></span>
                            <span className="path2"></span>
                        </i>
                    </div>
                </div>

                <div className="app-navbar flex-grow-1 justify-content-end" id="kt_app_header_navbar">
                    {/* Organization Dropdown */}
                   
                    {/* Search Bar */}
                    <div id="kt_header_search" className="header-search d-flex align-items-center w-lg-350px ms-5"
                        data-kt-search-keypress="true" data-kt-search-min-length="2" data-kt-search-enter="enter"
                        data-kt-search-layout="menu" data-kt-search-responsive="true" data-kt-menu-trigger="auto"
                        data-kt-menu-permanent="true" data-kt-menu-placement="bottom-start" data-kt-search="true">

                        <div data-kt-search-element="toggle" className="search-toggle-mobile d-flex d-lg-none align-items-center" onClick={toggleSearch}>
                            <div className="d-flex">
                                <i className="ki-duotone ki-magnifier fs-1"><span className="path1"></span><span className="path2"></span></i>
                            </div>
                        </div>

                        <Form data-kt-search-element="form" className={`${searchActive ? 'd-block' : 'd-none'} d-lg-block w-100 position-relative mb-5 mb-lg-0`} onSubmit={handleSearchSubmit} autoComplete="off">
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
                                placeholder="Search..."
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
                        </Form>

                        <div data-kt-search-element="content" className="menu menu-sub menu-sub-dropdown py-7 px-7 overflow-hidden w-300px w-md-350px" data-kt-menu="true">
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
                                        {/* More search results would go here */}
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
                                        {/* More recent searches would go here */}
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
                        </div>
                    </div>

                    

                    {/* User Dropdown */}
                    <div className="app-navbar-item mx-3 ms-lg-4 me-lg-6" id="kt_header_user_menu_toggle">
                        <NavDropdown title={
                            <div className="cursor-pointer symbol symbol-30px symbol-lg-30px">
                                <img src={authReducer.userDetails.profileImage ? authReducer.userDetails.profileImage : userIcon} alt="user" />
                            </div>
                        } id="user-settings-nav-dropdown" className="nav-dropdown-hide-menu-padding">
                            <div className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-800 menu-state-bg menu-state-color fw-semibold py-4 fs-6 w-275px show"
                                data-kt-menu="true">
                                <div className="menu-item px-3">
                                    <div className="menu-content d-flex align-items-center px-3">
                                        <div className="symbol symbol-50px me-5">
                                            <img alt="user" src={authReducer.userDetails.profileImage ? authReducer.userDetails.profileImage : userIcon} />
                                        </div>
                                        <div className="d-flex flex-column">
                                            <div className="fw-bold d-flex align-items-center fs-5 ellipsis">{getName(authReducer)}</div>
                                            <div className="fw-semibold text-muted fs-7 d-inline-block text-truncate" style={{ width: '90%' }}>
                                                <Tooltip title={getEmail(authReducer)}>{getEmail(authReducer)}</Tooltip>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="separator my-2"></div>
                                <div className="menu-item px-5">
                                    <NavLink to="/userProfile" className={`menu-link px-5`}>My Profile</NavLink>
                                </div>
                                <div className="separator my-2"></div>
                                <div className="menu-item px-5">
                                    <NavLink to="/change-password" className={`menu-link px-5`}>Change Password</NavLink>
                                </div>
                                <div className="separator my-2"></div>
                                <div className="menu-item px-5" data-kt-menu-trigger="{default: 'click', lg: 'hover'}"
                                    data-kt-menu-placement="left-start" data-kt-menu-offset="-15px, 0">
                                    <Dropdown>
                                        <Dropdown.Toggle as={"a"} id="theme-mode" className="menu-link px-5" bsPrefix="theme-mode">
                                            <span className="menu-title position-relative">Mode
                                                <span className="ms-5 position-absolute translate-middle-y top-50 end-0">
                                                    <i className="ki-duotone ki-night-day theme-light-show fs-2">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                        <span className="path3"></span>
                                                        <span className="path4"></span>
                                                        <span className="path5"></span>
                                                        <span className="path6"></span>
                                                        <span className="path7"></span>
                                                        <span className="path8"></span>
                                                        <span className="path9"></span>
                                                        <span className="path10"></span>
                                                    </i>
                                                    <i className="ki-duotone ki-moon theme-dark-show fs-2">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>
                                                </span>
                                            </span>
                                        </Dropdown.Toggle>
                                        <Dropdown.Menu className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-title-gray-700 menu-icon-gray-500 menu-active-bg menu-state-color fw-semibold py-4 fs-base w-150px">
                                            <Dropdown.Item eventKey="light-theme" as={"a"} className="px-3 my-0" bsPrefix="menu-item">
                                                <span className='menu-link py-2'>
                                                    <span className="menu-icon" data-kt-element="icon">
                                                        <i className="ki-duotone ki-night-day fs-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                            <span className="path3"></span>
                                                            <span className="path4"></span>
                                                            <span className="path5"></span>
                                                            <span className="path6"></span>
                                                            <span className="path7"></span>
                                                            <span className="path8"></span>
                                                            <span className="path9"></span>
                                                            <span className="path10"></span>
                                                        </i>
                                                    </span>
                                                    <span className="menu-title">Light</span>
                                                </span>
                                            </Dropdown.Item>
                                            <Dropdown.Item eventKey="dark-theme" as={"a"} className="px-3 my-0" bsPrefix="menu-item">
                                                <span className='menu-link py-2'>
                                                    <span className="menu-icon" data-kt-element="icon">
                                                        <i className="ki-duotone ki-moon fs-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                    </span>
                                                    <span className="menu-title">Dark</span>
                                                </span>
                                            </Dropdown.Item>
                                            <Dropdown.Item eventKey="system-theme" as={"a"} className="px-3 my-0" bsPrefix="menu-item">
                                                <span className='menu-link py-2'>
                                                    <span className="menu-icon" data-kt-element="icon">
                                                        <i className="ki-duotone ki-screen fs-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                            <span className="path3"></span>
                                                            <span className="path4"></span>
                                                        </i>
                                                    </span>
                                                    <span className="menu-title">System</span>
                                                </span>
                                            </Dropdown.Item>
                                        </Dropdown.Menu>
                                    </Dropdown>
                                </div>
                                <div className="menu-item px-5">
                                    <span
                                        className="menu-link px-5 cursor-pointer"
                                        onClick={handleSignoutClick}
                                    >
                                        Sign Out
                                    </span>
                                </div>
                            </div>
                        </NavDropdown>
                    </div>
                </div>
            </div>
        </div>
    );
}