import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { Helmet } from "react-helmet-async";
import { LiaRupeeSignSolid } from "react-icons/lia";
import { FaDiscourse } from "react-icons/fa";
import { PiStudentDuotone } from "react-icons/pi";
import _ from 'lodash';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg } from "../../../shared/helpers/msgHelper";
import { setLoaderState } from "../../../shared/redux/reducers/globalReducer";
import terms2 from '../../../assets/images/terms-2.png';
import IndustryList from "../../../shared/appConfig/industryList";
import userprofile from '../../../assets/images/userprofile.png';
import Loader from "../../../shared/components/loaders/fullPageLoader";
import employeeData from "../../../shared/appConfig/employeeData";
import { getDecodedToken } from "../../../shared/helpers/tokenHelper";

export default function MyProfile() {
    const dispatch = useDispatch();
    const [signingIn, setSigningIn] = useState(false);
    const [userData, setUserData] = useState({
        name: "",
        designation: "",
        employeeId: "",
        fatherName: "",
        address: "",
        email: "",
        gender: "",
        department: "",
        dateOfJoining: "",
        office: "",
        paymentMode: "",
        pan: "",
        dob: "",
        mobile: "",
        // Additional fields from DTOs
        employeeNumber: "",
        workMail: "",
        personalMail: "",
        employeeStatus: "",
        bankAccountNumber: "",
        bankName: "",
        ifscCode: "",
        accountHolderName: "",
        bankAccountType: "",
        ctc: "",
        monthlySalary: "",
        differentlyAbledType: "",
        isEligibleForFullIncomeTaxExemption: "",
        uan: "",
        pfAccountNumber: "",

    });

    // Get user email from localStorage or context (assuming it's stored after login)
    // const workEmail = localStorage.getItem("workEmail") || "krusha@gmail.com"; // Fallback for demo

    useEffect(() => {
        fetchEmployeeData();
    }, []);

    const employeeId = getDecodedToken()?.sub;

    const fetchEmployeeData = async () => {
        try {
            setSigningIn(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: {
                    // workEmail: workEmail
                    employeeId: employeeId
                },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    // organizationId:149059
                },
            });

            if (response.data && response.data.data) {
                const employeeData = response.data.data;

                // Format the address from ResidentialAddressDTO
                const address = employeeData.personalDetail?.presentResidentialAddress ?
                    `${employeeData.personalDetail.presentResidentialAddress.addressLine1 || ''}${employeeData.personalDetail.presentResidentialAddress.addressLine2 ?
                            ', ' + employeeData.personalDetail.presentResidentialAddress.addressLine2 : ''
                        }${employeeData.personalDetail.presentResidentialAddress.city ?
                            ', ' + employeeData.personalDetail.presentResidentialAddress.city : ''
                        }${employeeData.personalDetail.presentResidentialAddress.state ?
                            ', ' + employeeData.personalDetail.presentResidentialAddress.state : ''
                        }${employeeData.personalDetail.presentResidentialAddress.zipCode ?
                            ' - ' + employeeData.personalDetail.presentResidentialAddress.zipCode : ''
                        }`.trim() :
                    '';

                // Format date of birth
                const dob = employeeData.personalDetail?.dateOfBirth ?
                    new Date(employeeData.personalDetail.dateOfBirth).toLocaleDateString('en-GB') :
                    '';

                // Format date of joining
                const doj = employeeData.basicDetails?.dateOfJoining ?
                    new Date(employeeData.basicDetails.dateOfJoining).toLocaleDateString('en-GB') :
                    '';

                // Set the user data with formatted values and proper field mapping
                setUserData({
                    // BasicDetailsDTO fields
                    name: `${employeeData.basicDetails?.firstName || ''} ${employeeData.basicDetails?.middleName || ''} ${employeeData.basicDetails?.lastName || ''}`.trim(),
                    designation: employeeData.basicDetails?.designationName || employeeData.basicDetails?.designation || "",
                    employeeId: employeeData.basicDetails?.employeeNumber || "",
                    employeeNumber: employeeData.basicDetails?.employeeNumber || "",
                    gender: employeeData.basicDetails?.gender || "",
                    department: employeeData.basicDetails?.departmentName || employeeData.basicDetails?.department || "",
                    dateOfJoining: doj,
                    office: employeeData.basicDetails?.workLocationName || employeeData.basicDetails?.workLocation || "",
                    mobile: employeeData.basicDetails?.mobile || "",
                    workMail: employeeData.basicDetails?.workMail || "",
                    employeeStatus: employeeData.basicDetails?.employeeStatus || "",
                    uan: employeeData.basicDetails?.uan || "",
                    pfAccountNumber: employeeData.basicDetails?.pfAccountNumber || "",



                    // EmployeePersonalDetailDTO fields
                    fatherName: employeeData.personalDetail?.fatherName || "",
                    pan: employeeData.personalDetail?.pan || "",
                    personalMail: employeeData.personalDetail?.personalMail || "",
                    dob: dob,
                    differentlyAbledType: employeeData.personalDetail?.differentlyAbledType || "",
                    isEligibleForFullIncomeTaxExemption: employeeData.personalDetail?.isEligibleForFullIncomeTaxExemption || false,
                    address: address,

                    // EmployeeBankDetailDTO fields
                    paymentMode: employeeData.bankDetail?.paymentMode?.toLowerCase() || "",
                    bankAccountNumber: employeeData.bankDetail?.bankAccountNumber || "",
                    bankName: employeeData.bankDetail?.bankName || "",
                    ifscCode: employeeData.bankDetail?.ifscCode || "",
                    accountHolderName: employeeData.bankDetail?.accountHolderName || "",
                    bankAccountType: employeeData.bankDetail?.bankAccountType || "",

                    // CtcStructureDTO fields
                    ctc: employeeData.ctcStructure?.ctc || "",
                    monthlySalary: employeeData.ctcStructure?.monthlySalary || "",

                    // Combined email (work mail takes priority)
                    email: employeeData.basicDetails?.workMail || employeeData.personalDetails?.personalMail || ""
                });
            } else {
                errorMsg("Error", "Unexpected response format from server", true);
            }
        } catch (error) {
            console.error("API Error:", error);

            if (error.response) {
                // Server responded with error status
                errorMsg("Error", error.response.data?.message || "Failed to load employee data", true);
            } else if (error.request) {
                // Request was made but no response received
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
            } else {
                // Something else happened
                errorMsg("Error", "An unexpected error occurred", true);
            }
        } finally {
            setSigningIn(false);
        }
    };

    const handleDownloadProfile = () => {
        // Implement download profile functionality
        console.log("Download profile clicked");
    };

    const handleShareProfile = () => {
        // Implement share profile functionality
        console.log("Share profile clicked");
    };

    const handlePrintProfile = () => {
        // Implement print profile functionality
        window.print();
    };

    return (
        <>
            <Helmet>
                <title>My Profile | HRMS InfiNevoCloud</title>
            </Helmet>

            <div id="kt_app_toolbar" className="app-toolbar pt-5">
                <div id="kt_app_toolbar_container" className="app-container container-fluid d-flex align-items-stretch">
                    <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
                        <div className="page-title d-flex flex-column gap-1 me-3 mb-2">

                            <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">
                                My Profile
                            </h1>
                        </div>
                    </div>
                </div>
            </div>

            <div id="kt_app_content" className="app-content flex-column-fluid">
                <div id="kt_app_content_container" className="app-container container-fluid">
                    {/* Profile Header Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-body pt-9 pb-0">
                            <div className="d-flex flex-wrap flex-sm-nowrap mb-3">
                                <div className="me-7 mb-4">
                                    <div className="symbol symbol-100px symbol-lg-160px symbol-fixed position-relative">
                                        <img src={userprofile} alt="Profile" />
                                        <div className="position-absolute translate-middle bottom-0 start-100 mb-6 bg-success rounded-circle border border-4 border-body h-20px w-20px"></div>
                                    </div>
                                </div>

                                <div className="flex-grow-1">
                                    <div className="d-flex justify-content-between align-items-start flex-wrap mb-2">
                                        <div className="d-flex flex-column">
                                            <div className="d-flex align-items-center mb-2">
                                                <a href="#" className="text-gray-900 text-hover-primary fs-2 fw-bold me-1">
                                                    {userData.name || "Loading..."}
                                                </a>
                                                <a href="#">
                                                    <i className="ki-duotone ki-verify fs-1 text-primary">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>
                                                </a>
                                            </div>

                                            <div className="d-flex flex-wrap fw-semibold fs-6 mb-4 pe-2">
                                                <div className="d-flex align-items-center text-gray-400 text-hover-primary me-5 mb-2">
                                                    <i className="ki-duotone ki-profile-circle fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                        <span className="path3"></span>
                                                    </i>
                                                    {userData.designation || "Loading..."}
                                                </div>
                                                <div className="d-flex align-items-center text-gray-400 text-hover-primary me-5 mb-2">
                                                    <i className="ki-duotone ki-geolocation fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>
                                                    {userData.office || "Loading..."}
                                                </div>
                                                <div className="d-flex align-items-center text-gray-400 text-hover-primary mb-2">
                                                    <i className="ki-duotone ki-sms fs-4 me-1">
                                                        <span className="path1"></span>
                                                        <span className="path2"></span>
                                                    </i>
                                                    {userData.email || "Loading..."}
                                                </div>
                                            </div>
                                        </div>

                                        <div className="d-flex my-4">


                                            <div className="me-0">
                                                <button className="btn btn-sm btn-icon btn-bg-light btn-active-color-primary" data-kt-menu-trigger="click" data-kt-menu-placement="bottom-end">
                                                    <i className="bi bi-three-dots fs-3"></i>
                                                </button>

                                                <div className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-800 menu-state-bg-light-primary fw-semibold w-200px py-3" data-kt-menu="true">
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3" onClick={handleDownloadProfile}>
                                                            Download Profile
                                                        </a>
                                                    </div>
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3" onClick={handleShareProfile}>
                                                            Share Profile
                                                        </a>
                                                    </div>
                                                    <div className="menu-item px-3">
                                                        <a href="#" className="menu-link px-3" onClick={handlePrintProfile}>
                                                            Print Profile
                                                        </a>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="d-flex flex-wrap flex-stack">
                                        <div className="d-flex flex-column flex-grow-1 pe-8">
                                            <div className="d-flex flex-wrap">
                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-calendar fs-2 text-primary me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{userData.dateOfJoining || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Date of Joining</div>
                                                </div>

                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-badge fs-2 text-info me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{userData.employeeId || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Employee ID</div>
                                                </div>

                                                <div className="border border-gray-300 border-dashed rounded min-w-125px py-3 px-4 me-6 mb-3">
                                                    <div className="d-flex align-items-center">
                                                        <i className="ki-duotone ki-profile-user fs-2 text-success me-2">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                        <div className="fs-2 fw-bold">{userData.department || "Loading..."}</div>
                                                    </div>
                                                    <div className="fw-semibold fs-6 text-gray-400">Department</div>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="d-flex align-items-center w-200px w-sm-300px flex-column mt-3">
                                            <div className="d-flex justify-content-between w-100 mt-auto mb-2">
                                                <span className="fw-semibold fs-6 text-gray-400">Profile Completion</span>
                                                <span className="fw-bold fs-6">85%</span>
                                            </div>
                                            <div className="h-5px mx-3 w-100 bg-light mb-3">
                                                <div className="bg-success rounded h-5px" role="progressbar" style={{ width: "85%" }} aria-valuenow="85" aria-valuemin="0" aria-valuemax="100"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <ul className="nav nav-stretch nav-line-tabs nav-line-tabs-2x border-transparent fs-5 fw-bold">
                                <li className="nav-item mt-2">
                                    <a className="nav-link text-active-primary ms-0 me-10 py-5 active" href="#">
                                        Personal Information
                                    </a>
                                </li>
                                <li className="nav-item mt-2">
                                    <a className="nav-link text-active-primary ms-0 me-10 py-5" href="#">
                                        Employment Details
                                    </a>
                                </li>
                                <li className="nav-item mt-2">
                                    <a className="nav-link text-active-primary ms-0 me-10 py-5" href="#">
                                        Documents
                                    </a>
                                </li>

                            </ul>
                        </div>
                    </div>

                    {/* Personal Information Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-header cursor-pointer">
                            <div className="card-title m-0">
                                <h3 className="fw-bold m-0">Personal Information</h3>
                            </div>
                        </div>

                        <div className="card-body p-9">
                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Full Name</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.name || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Date of Birth</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.dob || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Mobile Number</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.mobile || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Personal Email</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.personalMail || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Father's Name</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.fatherName || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Gender</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.gender || "Loading..."}</span>
                                </div>
                            </div>


                        </div>
                    </div>

                    {/* Employee Details Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-body p-9">
                            <div className="d-flex align-items-center mb-7">
                                <h2 className="fw-bold text-gray-900 me-3">{userData.name || "Loading..."}</h2>
                                <span className="badge badge-light-primary fs-7">{userData.designation || "Loading..."}, {userData.employeeId || "Loading..."}</span>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Employee ID</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.employeeId || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Work Email</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.workMail || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Department</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.department || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Designation</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.designation || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Work Location</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.office || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Date of Joining</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.dateOfJoining || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Employee Status</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.employeeStatus || "Loading..."}</span>
                                </div>
                            </div>

                            <div className="separator separator-dashed my-7"></div>

                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Address</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.address || "Loading..."}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Payment Information Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-header">
                            <h3 className="card-title align-items-start flex-column">
                                <span className="card-label fw-bold text-dark">Payment Information</span>
                            </h3>
                        </div>
                        <div className="card-body p-9">
                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">Payment Mode</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.paymentMode || "Loading..."}</span>
                                </div>
                            </div>

                            {userData.paymentMode === 'banktransfer' && (
                                <>
                                    <div className="row mb-7">
                                        <label className="col-lg-4 fw-semibold text-muted">Bank Name</label>
                                        <div className="col-lg-8">
                                            <span className="fw-bold fs-6 text-gray-800">{userData.bankName || "Loading..."}</span>
                                        </div>
                                    </div>

                                    <div className="row mb-7">
                                        <label className="col-lg-4 fw-semibold text-muted">Account Holder Name</label>
                                        <div className="col-lg-8">
                                            <span className="fw-bold fs-6 text-gray-800">{userData.accountHolderName || "Loading..."}</span>
                                        </div>
                                    </div>

                                    <div className="row mb-7">
                                        <label className="col-lg-4 fw-semibold text-muted">Account Number</label>
                                        <div className="col-lg-8">
                                            <span className="fw-bold fs-6 text-gray-800">{userData.bankAccountNumber || "Loading..."}</span>
                                        </div>
                                    </div>

                                    <div className="row mb-7">
                                        <label className="col-lg-4 fw-semibold text-muted">IFSC Code</label>
                                        <div className="col-lg-8">
                                            <span className="fw-bold fs-6 text-gray-800">{userData.ifscCode || "Loading..."}</span>
                                        </div>
                                    </div>

                                    <div className="row mb-7">
                                        <label className="col-lg-4 fw-semibold text-muted">Account Type</label>
                                        <div className="col-lg-8">
                                            <span className="fw-bold fs-6 text-gray-800">{userData.bankAccountType || "Loading..."}</span>
                                        </div>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>

                    {/* Statutory Details Section */}
                    <div className="card mb-5 mb-xl-10">
                        <div className="card-header">
                            <h3 className="card-title align-items-start flex-column">
                                <span className="card-label fw-bold text-dark">Statutory Details</span>
                            </h3>
                        </div>
                        <div className="card-body p-9">
                            <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">PAN</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.pan || "Loading..."}</span>
                                </div>
                            </div>

                            {/* <div className="row mb-7">
                                <label className="col-lg-4 fw-semibold text-muted">UAN</label>
                                <div className="col-lg-8">
                                    <span className="fw-bold fs-6 text-gray-800">{userData.uan || "Loading..."}</span>
                                </div>
                            </div> */}


                        </div>
                    </div>




                </div>
            </div>

            {signingIn && <Loader />}
        </>
    );
}