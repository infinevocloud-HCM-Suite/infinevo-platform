import { Helmet } from "react-helmet-async";
import userImge from '../../../assets/images/user.png';
import { useEffect, useState } from "react";
import _ from 'lodash';
import { useDispatch } from "react-redux";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import moment from "moment";
import { updateUserDetails } from "../../../shared/redux/reducers/authReducer";
import EditProfileDetailsTab from "./Tabs/editProfileDetailsTab";
import EditSecurityDetailsTab from "./Tabs/editsecurityDetailsTab";

export default function YourAccount() {

    const dispatch = useDispatch();
    const [userData, setUserData] = useState({
        name: '',
        designation: '',
        branch: '',
        email: '',
        userImage: '',
        lastLogin: ''
    });
    const [activeTab, setActiveTab] = useState('overview')
    
    useEffect(() => {
        getUserDetails();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const getUserDetails = (updateRedux) => {
        axios
            .get(`${GlobalConst.API_URL}/api/v1/entrance/user-info`)
            .then(op => {
                if (!_.isEmpty(op) && !_.isEmpty(op.data) && !_.isEmpty(op.data.result)) {
                    setUserData({
                        name: op.data.result.userNicename,
                        designation: op.data.result.userDesignation,
                        branch: op.data.result.branchName,
                        email: op.data.result.userEmail,
                        userImage: op.data.result.userProfileImg,
                        lastLogin: op.data.result.dtLastLogin,
                    });
                    if (updateRedux) {
                        dispatch(updateUserDetails(op.data.result));
                    }
                }
            })
            .catch(e => {
                console.log("Exception: ", e);
            });
    }

    const handleTabClick = (tabKey) => {
        setActiveTab(tabKey);
    }

    return (
        <>
            <Helmet>
                <title>Your Profile | Digicom</title>
            </Helmet>
            <div id="kt_app_toolbar" className="app-toolbar pt-9">
                <div id="kt_app_toolbar_container"
                    className="app-container container-fluid d-flex align-items-stretch">
                    <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
                        <div className="page-title d-flex flex-column gap-1 me-3 mb-2">
                            <ul className="breadcrumb breadcrumb-separatorless fw-semibold mb-6">
                                <li className="breadcrumb-item text-gray-700 fw-bold lh-1">
                                    <a href="/" className="text-gray-500">
                                        <i className="ki-duotone ki-home fs-3 text-gray-400 me-n1"></i>
                                    </a>
                                </li>
                                <li className="breadcrumb-item">
                                    <i className="ki-duotone ki-right fs-4 text-gray-700 mx-n1"></i>
                                </li>
                                <li className="breadcrumb-item text-gray-700 fw-bold lh-1">Account Settings</li>
                            </ul>
                            <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">Your Account</h1>
                        </div>
                    </div>
                </div>
            </div>
            <div id="kt_app_content" className="app-content flex-column-fluid">
                <div id="kt_app_content_container" className="app-container container-fluid">
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-body pt-9 pb-0">
                            <div className="d-flex flex-wrap flex-sm-nowrap mb-3 align-items-center">
                                <div className="me-7 mb-4">
                                    <div className="symbol symbol-100px symbol-lg-100px symbol-fixed position-relative">
                                        <img src={userData.userImage ? userData.userImage : userImge} alt="User Profile" />
                                    </div>
                                </div>

                                <div className="flex-grow-1">
                                    <div className="d-flex justify-content-between align-items-start flex-wrap">
                                        <div className="d-flex flex-column">
                                            <div className="d-flex align-items-center mb-2">
                                                <span className="text-gray-900 text-hover-primary fs-2 fw-bold me-1">{userData.name ? userData.name : userData.email}</span>
                                                <span>
                                                    <i className="ki-duotone ki-verify fs-1 text-primary">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>
                                                </span>
                                                {userData.branch ? <span className="badge badge-light-dark fw-bold ms-2 fs-8 py-1 px-3">From branch {userData.branch}</span> : <></>}
                                            </div>

                                            <div className="d-flex flex-wrap fw-semibold fs-6 pe-2">
                                                {userData.designation ? <span className="d-flex align-items-center text-gray-400 me-5 mb-2">
                                                    <i className="ki-duotone ki-profile-circle fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                        <span className="path3"></span>
                                                    </i>{userData.designation}</span> : <></>}

                                                {userData.email ? <span className="d-flex align-items-center text-gray-400 mb-2 me-5">
                                                    <i className="ki-duotone ki-sms fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>{userData.email}</span> : <></>}

                                                {userData.lastLogin ? <span className="d-flex align-items-center text-gray-400 mb-2">
                                                    <i className="ki-duotone ki-calendar fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>Last login {moment(userData.lastLogin).fromNow()}</span> : <></>}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <ul className="nav nav-stretch nav-line-tabs nav-line-tabs-2x border-transparent fs-5 fw-bold">
                                <li className="nav-item mt-2">
                                    <span className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === 'overview' ? 'active' : ''}`} role="button" onClick={()=>handleTabClick('overview')}>Overview</span>
                                </li>
                                <li className="nav-item mt-2">
                                    <span className={`nav-link text-active-primary ms-0 me-10 py-5 ${activeTab === 'security' ? 'active' : ''}`} role="button" onClick={()=>handleTabClick('security')}>Security</span>
                                </li>
                            </ul>
                        </div>
                    </div>

                    {activeTab==='overview' && <EditProfileDetailsTab userData={userData} setUserData={setUserData} getUserDetails={getUserDetails} />}
                    {activeTab==='security' && <EditSecurityDetailsTab userData={userData} setUserData={setUserData} getUserDetails={getUserDetails} />}

                    {/* <div className="card mb-5 mb-xl-10" id="kt_profile_details_view">
                        <div className="card-header cursor-pointer">
                            <div className="card-title m-0">
                                <h3 className="fw-bold m-0">Edit Profile Details</h3>
                            </div>
                        </div>

                        <div className="card-body p-9">
                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Full Name</label>
                                <div className="col-lg-8">
                                    <input type="text" name="fname" className="form-control form-control-lg form-control-solid mb-3 mb-lg-0" placeholder="Full Name" value={userData.name} onChange={handleNameChange} />
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Profile Image</label>
                                <div className="col-lg-8 fv-row">


                                    <ImgCrop rotationSlider>
                                        <Upload
                                            listType="picture-card"
                                            maxCount={1}
                                            fileList={fileList}
                                            onChange={onChange}
                                            onPreview={onPreview}
                                            beforeUpload={() => {
                                                return false;
                                            }}
                                        >
                                            Click here to upload
                                        </Upload>
                                    </ImgCrop>
                                    <div className="form-text">Allowed file types: png, jpg, jpeg.</div>
                                </div>
                            </div>
                            <div className="card-footer py-6 px-9">
                                <div className="row">
                                    <div className="col-lg-4"></div>
                                    <div className="col-lg-8">
                                        <button type="button" onClick={handleSaveClick} className="btn btn-sm btn-primary" disabled={updatingRecord} data-kt-indicator={(updatingRecord) ? "on" : "off"}>
                                            <span className="indicator-label"><FiSave className="me-2" />Save Changes</span>
                                            <span className="indicator-progress">Saving...
                                                <span className="spinner-border spinner-border-sm align-middle ms-2"></span></span>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div> */}
                </div>
            </div>
        </>
    )
}