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

export default function DashboardPage() {

    return (
        <>
            <Helmet>
                <title>Dashboard | HRMS InfiNevoCloud</title>
            </Helmet>
            <div class="d-flex flex-column flex-column-fluid">

                <div class="content fs-6 d-flex flex-column-fluid" id="kt_content">

                    <div class="container">

                        <div class="row g-0 g-xl-5 g-xxl-8">
                            <div class="col-xl-4">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-body pb-0">

                                        <div class="d-flex flex-column h-100">

                                            <h3 class="text-dark text-center fs-1 fw-bolder pt-15 lh-lg">Payroll
                                                <br /> Service Dashboard</h3>


                                            <div class="text-center pt-7">
                                                <a href="#" class="btn btn-primary fw-bolder fs-6 px-7 py-3" data-bs-toggle="modal" data-bs-target="#kt_modal_create_app">Create App</a>
                                            </div>


                                            <div class="flex-grow-1 bgi-no-repeat bgi-size-contain bgi-position-x-center bgi-position-y-bottom card-rounded-bottom h-200px" style={{backgroundImage:`url(${terms2})`}}></div>

                                        </div>

                                    </div>

                                </div>

                            </div>
                            <div class="col-xl-8">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-header border-0 pt-5">
                                        <h3 class="card-title align-items-start flex-column">
                                            <span class="card-label fw-bolder text-dark fs-3">Campaign Leaders</span>
                                            <span class="text-muted mt-2 fw-bold fs-6">890,344 Sales</span>
                                        </h3>
                                        <div class="card-toolbar">
                                            <ul class="nav nav-pills nav-pills-sm nav-light">
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 active fw-bolder me-2" data-bs-toggle="tab" href="#kt_tab_pane_1_1">Day</a>
                                                </li>
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 fw-bolder me-2" data-bs-toggle="tab" href="#kt_tab_pane_1_2">Week</a>
                                                </li>
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 fw-bolder" data-bs-toggle="tab" href="#kt_tab_pane_1_3">Month</a>
                                                </li>
                                            </ul>
                                        </div>
                                    </div>


                                    <div class="card-body pt-2 pb-0 mt-n3">
                                        <div class="tab-content mt-5" id="myTabTables1">

                                            <div class="tab-pane fade show active" id="kt_tab_pane_1_1" role="tabpanel" aria-labelledby="kt_tab_pane_1_1">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-200px"></th>
                                                                <th class="p-0 min-w-100px"></th>
                                                                <th class="p-0 min-w-40px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <th class="px-0 py-3">
                                                                    <div class="symbol symbol-65px me-5">
                                                                        <span class="symbol-label bg-light-primary">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-primary">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M16,15.6315789 L16,12 C16,10.3431458 14.6568542,9 13,9 L6.16183229,9 L6.16183229,5.52631579 C6.16183229,4.13107011 7.29290239,3 8.68814808,3 L20.4776218,3 C21.8728674,3 23.0039375,4.13107011 23.0039375,5.52631579 L23.0039375,13.1052632 L23.0206157,17.786793 C23.0215995,18.0629336 22.7985408,18.2875874 22.5224001,18.2885711 C22.3891754,18.2890457 22.2612702,18.2363324 22.1670655,18.1421277 L19.6565168,15.6315789 L16,15.6315789 Z" fill="#000000" />
                                                                                    <path d="M1.98505595,18 L1.98505595,13 C1.98505595,11.8954305 2.88048645,11 3.98505595,11 L11.9850559,11 C13.0896254,11 13.9850559,11.8954305 13.9850559,13 L13.9850559,18 C13.9850559,19.1045695 13.0896254,20 11.9850559,20 L4.10078614,20 L2.85693427,21.1905292 C2.65744295,21.3814685 2.34093638,21.3745358 2.14999706,21.1750444 C2.06092565,21.0819836 2.01120804,20.958136 2.01120804,20.8293182 L2.01120804,18.32426 C1.99400175,18.2187196 1.98505595,18.1104045 1.98505595,18 Z M6.5,14 C6.22385763,14 6,14.2238576 6,14.5 C6,14.7761424 6.22385763,15 6.5,15 L11.5,15 C11.7761424,15 12,14.7761424 12,14.5 C12,14.2238576 11.7761424,14 11.5,14 L6.5,14 Z M9.5,16 C9.22385763,16 9,16.2238576 9,16.5 C9,16.7761424 9.22385763,17 9.5,17 L11.5,17 C11.7761424,17 12,16.7761424 12,16.5 C12,16.2238576 11.7761424,16 11.5,16 L9.5,16 Z" fill="#000000" opacity="0.3" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Top Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-primary">
                                                                                <div class="progress-bar bg-primary" role="progressbar" style={{width: "46%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">46%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-65px">
                                                                        <span class="symbol-label bg-light-warning">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-warning">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <rect fill="#000000" x="4" y="4" width="7" height="7" rx="1.5" />
                                                                                        <path d="M5.5,13 L9.5,13 C10.3284271,13 11,13.6715729 11,14.5 L11,18.5 C11,19.3284271 10.3284271,20 9.5,20 L5.5,20 C4.67157288,20 4,19.3284271 4,18.5 L4,14.5 C4,13.6715729 4.67157288,13 5.5,13 Z M14.5,4 L18.5,4 C19.3284271,4 20,4.67157288 20,5.5 L20,9.5 C20,10.3284271 19.3284271,11 18.5,11 L14.5,11 C13.6715729,11 13,10.3284271 13,9.5 L13,5.5 C13,4.67157288 13.6715729,4 14.5,4 Z M14.5,13 L18.5,13 C19.3284271,13 20,13.6715729 20,14.5 L20,18.5 C20,19.3284271 19.3284271,20 18.5,20 L14.5,20 C13.6715729,20 13,19.3284271 13,18.5 L13,14.5 C13,13.6715729 13.6715729,13 14.5,13 Z" fill="#000000" opacity="0.3" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Popular Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, VueJS, Laravel</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-warning">
                                                                                <div class="progress-bar bg-warning" role="progressbar" style={{width: "87%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">87%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="px-0 py-3">
                                                                    <div class="symbol symbol-65px">
                                                                        <span class="symbol-label bg-light-success">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-success">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M18,8 L16,8 C15.4477153,8 15,7.55228475 15,7 C15,6.44771525 15.4477153,6 16,6 L18,6 L18,4 C18,3.44771525 18.4477153,3 19,3 C19.5522847,3 20,3.44771525 20,4 L20,6 L22,6 C22.5522847,6 23,6.44771525 23,7 C23,7.55228475 22.5522847,8 22,8 L20,8 L20,10 C20,10.5522847 19.5522847,11 19,11 C18.4477153,11 18,10.5522847 18,10 L18,8 Z M9,11 C6.790861,11 5,9.209139 5,7 C5,4.790861 6.790861,3 9,3 C11.209139,3 13,4.790861 13,7 C13,9.209139 11.209139,11 9,11 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                                    <path d="M0.00065168429,20.1992055 C0.388258525,15.4265159 4.26191235,13 8.98334134,13 C13.7712164,13 17.7048837,15.2931929 17.9979143,20.2 C18.0095879,20.3954741 17.9979143,21 17.2466999,21 C13.541124,21 8.03472472,21 0.727502227,21 C0.476712155,21 -0.0204617505,20.45918 0.00065168429,20.1992055 Z" fill="#000000" fill-rule="nonzero" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">New Users</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-success">
                                                                                <div class="progress-bar bg-success" role="progressbar" style={{width: "53%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">53%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="px-0 py-3">
                                                                    <div class="symbol symbol-65px">
                                                                        <span class="symbol-label bg-light-danger">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-danger">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <path d="M5,3 L6,3 C6.55228475,3 7,3.44771525 7,4 L7,20 C7,20.5522847 6.55228475,21 6,21 L5,21 C4.44771525,21 4,20.5522847 4,20 L4,4 C4,3.44771525 4.44771525,3 5,3 Z M10,3 L11,3 C11.5522847,3 12,3.44771525 12,4 L12,20 C12,20.5522847 11.5522847,21 11,21 L10,21 C9.44771525,21 9,20.5522847 9,20 L9,4 C9,3.44771525 9.44771525,3 10,3 Z" fill="#000000" />
                                                                                        <rect fill="#000000" opacity="0.3" transform="translate(17.825568, 11.945519) rotate(-19.000000) translate(-17.825568, -11.945519)" x="16.3255682" y="2.94551858" width="3" height="18" rx="1" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Weekly Bestsellers</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-danger">
                                                                                <div class="progress-bar bg-danger" role="progressbar" style={{width: "92%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">92%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>


                                            <div class="tab-pane fade" id="kt_tab_pane_1_2" role="tabpanel" aria-labelledby="kt_tab_pane_1_2">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-200px"></th>
                                                                <th class="p-0 min-w-100px"></th>
                                                                <th class="p-0 min-w-40px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px me-3">
                                                                        <span class="symbol-label bg-light-success">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-success">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M18,8 L16,8 C15.4477153,8 15,7.55228475 15,7 C15,6.44771525 15.4477153,6 16,6 L18,6 L18,4 C18,3.44771525 18.4477153,3 19,3 C19.5522847,3 20,3.44771525 20,4 L20,6 L22,6 C22.5522847,6 23,6.44771525 23,7 C23,7.55228475 22.5522847,8 22,8 L20,8 L20,10 C20,10.5522847 19.5522847,11 19,11 C18.4477153,11 18,10.5522847 18,10 L18,8 Z M9,11 C6.790861,11 5,9.209139 5,7 C5,4.790861 6.790861,3 9,3 C11.209139,3 13,4.790861 13,7 C13,9.209139 11.209139,11 9,11 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                                    <path d="M0.00065168429,20.1992055 C0.388258525,15.4265159 4.26191235,13 8.98334134,13 C13.7712164,13 17.7048837,15.2931929 17.9979143,20.2 C18.0095879,20.3954741 17.9979143,21 17.2466999,21 C13.541124,21 8.03472472,21 0.727502227,21 C0.476712155,21 -0.0204617505,20.45918 0.00065168429,20.1992055 Z" fill="#000000" fill-rule="nonzero" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">New Users</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-success">
                                                                                <div class="progress-bar bg-success" role="progressbar" style={{width: "53%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">53%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px me-3">
                                                                        <span class="symbol-label bg-light-danger">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-danger">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <path d="M5,3 L6,3 C6.55228475,3 7,3.44771525 7,4 L7,20 C7,20.5522847 6.55228475,21 6,21 L5,21 C4.44771525,21 4,20.5522847 4,20 L4,4 C4,3.44771525 4.44771525,3 5,3 Z M10,3 L11,3 C11.5522847,3 12,3.44771525 12,4 L12,20 C12,20.5522847 11.5522847,21 11,21 L10,21 C9.44771525,21 9,20.5522847 9,20 L9,4 C9,3.44771525 9.44771525,3 10,3 Z" fill="#000000" />
                                                                                        <rect fill="#000000" opacity="0.3" transform="translate(17.825568, 11.945519) rotate(-19.000000) translate(-17.825568, -11.945519)" x="16.3255682" y="2.94551858" width="3" height="18" rx="1" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Weekly Bestsellers</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-danger">
                                                                                <div class="progress-bar bg-danger" role="progressbar" style={{width: "92%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">92%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px me-3">
                                                                        <span class="symbol-label bg-light-primary">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-primary">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M16,15.6315789 L16,12 C16,10.3431458 14.6568542,9 13,9 L6.16183229,9 L6.16183229,5.52631579 C6.16183229,4.13107011 7.29290239,3 8.68814808,3 L20.4776218,3 C21.8728674,3 23.0039375,4.13107011 23.0039375,5.52631579 L23.0039375,13.1052632 L23.0206157,17.786793 C23.0215995,18.0629336 22.7985408,18.2875874 22.5224001,18.2885711 C22.3891754,18.2890457 22.2612702,18.2363324 22.1670655,18.1421277 L19.6565168,15.6315789 L16,15.6315789 Z" fill="#000000" />
                                                                                    <path d="M1.98505595,18 L1.98505595,13 C1.98505595,11.8954305 2.88048645,11 3.98505595,11 L11.9850559,11 C13.0896254,11 13.9850559,11.8954305 13.9850559,13 L13.9850559,18 C13.9850559,19.1045695 13.0896254,20 11.9850559,20 L4.10078614,20 L2.85693427,21.1905292 C2.65744295,21.3814685 2.34093638,21.3745358 2.14999706,21.1750444 C2.06092565,21.0819836 2.01120804,20.958136 2.01120804,20.8293182 L2.01120804,18.32426 C1.99400175,18.2187196 1.98505595,18.1104045 1.98505595,18 Z M6.5,14 C6.22385763,14 6,14.2238576 6,14.5 C6,14.7761424 6.22385763,15 6.5,15 L11.5,15 C11.7761424,15 12,14.7761424 12,14.5 C12,14.2238576 11.7761424,14 11.5,14 L6.5,14 Z M9.5,16 C9.22385763,16 9,16.2238576 9,16.5 C9,16.7761424 9.22385763,17 9.5,17 L11.5,17 C11.7761424,17 12,16.7761424 12,16.5 C12,16.2238576 11.7761424,16 11.5,16 L9.5,16 Z" fill="#000000" opacity="0.3" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Top Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-primary">
                                                                                <div class="progress-bar bg-primary" role="progressbar" style={{width: "46%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">46%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px me-3">
                                                                        <span class="symbol-label bg-light-warning">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-warning">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <rect fill="#000000" x="4" y="4" width="7" height="7" rx="1.5" />
                                                                                        <path d="M5.5,13 L9.5,13 C10.3284271,13 11,13.6715729 11,14.5 L11,18.5 C11,19.3284271 10.3284271,20 9.5,20 L5.5,20 C4.67157288,20 4,19.3284271 4,18.5 L4,14.5 C4,13.6715729 4.67157288,13 5.5,13 Z M14.5,4 L18.5,4 C19.3284271,4 20,4.67157288 20,5.5 L20,9.5 C20,10.3284271 19.3284271,11 18.5,11 L14.5,11 C13.6715729,11 13,10.3284271 13,9.5 L13,5.5 C13,4.67157288 13.6715729,4 14.5,4 Z M14.5,13 L18.5,13 C19.3284271,13 20,13.6715729 20,14.5 L20,18.5 C20,19.3284271 19.3284271,20 18.5,20 L14.5,20 C13.6715729,20 13,19.3284271 13,18.5 L13,14.5 C13,13.6715729 13.6715729,13 14.5,13 Z" fill="#000000" opacity="0.3" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Popular Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, VueJS, Laravel</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-warning">
                                                                                <div class="progress-bar bg-warning" role="progressbar" style={{width: "87%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">87%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>


                                            <div class="tab-pane fade" id="kt_tab_pane_1_3" role="tabpanel" aria-labelledby="kt_tab_pane_1_3">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-200px"></th>
                                                                <th class="p-0 min-w-100px"></th>
                                                                <th class="p-0 min-w-40px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px bg-light-warning me-3">
                                                                        <span class="symbol-label">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-warning">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <rect fill="#000000" x="4" y="4" width="7" height="7" rx="1.5" />
                                                                                        <path d="M5.5,13 L9.5,13 C10.3284271,13 11,13.6715729 11,14.5 L11,18.5 C11,19.3284271 10.3284271,20 9.5,20 L5.5,20 C4.67157288,20 4,19.3284271 4,18.5 L4,14.5 C4,13.6715729 4.67157288,13 5.5,13 Z M14.5,4 L18.5,4 C19.3284271,4 20,4.67157288 20,5.5 L20,9.5 C20,10.3284271 19.3284271,11 18.5,11 L14.5,11 C13.6715729,11 13,10.3284271 13,9.5 L13,5.5 C13,4.67157288 13.6715729,4 14.5,4 Z M14.5,13 L18.5,13 C19.3284271,13 20,13.6715729 20,14.5 L20,18.5 C20,19.3284271 19.3284271,20 18.5,20 L14.5,20 C13.6715729,20 13,19.3284271 13,18.5 L13,14.5 C13,13.6715729 13.6715729,13 14.5,13 Z" fill="#000000" opacity="0.3" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Popular Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, VueJS, Laravel</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-warning">
                                                                                <div class="progress-bar bg-warning" role="progressbar" style={{width: "87%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">87%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px bg-light-success me-3">
                                                                        <span class="symbol-label">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-success">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M18,8 L16,8 C15.4477153,8 15,7.55228475 15,7 C15,6.44771525 15.4477153,6 16,6 L18,6 L18,4 C18,3.44771525 18.4477153,3 19,3 C19.5522847,3 20,3.44771525 20,4 L20,6 L22,6 C22.5522847,6 23,6.44771525 23,7 C23,7.55228475 22.5522847,8 22,8 L20,8 L20,10 C20,10.5522847 19.5522847,11 19,11 C18.4477153,11 18,10.5522847 18,10 L18,8 Z M9,11 C6.790861,11 5,9.209139 5,7 C5,4.790861 6.790861,3 9,3 C11.209139,3 13,4.790861 13,7 C13,9.209139 11.209139,11 9,11 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                                    <path d="M0.00065168429,20.1992055 C0.388258525,15.4265159 4.26191235,13 8.98334134,13 C13.7712164,13 17.7048837,15.2931929 17.9979143,20.2 C18.0095879,20.3954741 17.9979143,21 17.2466999,21 C13.541124,21 8.03472472,21 0.727502227,21 C0.476712155,21 -0.0204617505,20.45918 0.00065168429,20.1992055 Z" fill="#000000" fill-rule="nonzero" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">New Users</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-success">
                                                                                <div class="progress-bar bg-success" role="progressbar" style={{width: "53%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">53%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px bg-light-primary me-3">
                                                                        <span class="symbol-label">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-primary">
                                                                                <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <path d="M16,15.6315789 L16,12 C16,10.3431458 14.6568542,9 13,9 L6.16183229,9 L6.16183229,5.52631579 C6.16183229,4.13107011 7.29290239,3 8.68814808,3 L20.4776218,3 C21.8728674,3 23.0039375,4.13107011 23.0039375,5.52631579 L23.0039375,13.1052632 L23.0206157,17.786793 C23.0215995,18.0629336 22.7985408,18.2875874 22.5224001,18.2885711 C22.3891754,18.2890457 22.2612702,18.2363324 22.1670655,18.1421277 L19.6565168,15.6315789 L16,15.6315789 Z" fill="#000000" />
                                                                                    <path d="M1.98505595,18 L1.98505595,13 C1.98505595,11.8954305 2.88048645,11 3.98505595,11 L11.9850559,11 C13.0896254,11 13.9850559,11.8954305 13.9850559,13 L13.9850559,18 C13.9850559,19.1045695 13.0896254,20 11.9850559,20 L4.10078614,20 L2.85693427,21.1905292 C2.65744295,21.3814685 2.34093638,21.3745358 2.14999706,21.1750444 C2.06092565,21.0819836 2.01120804,20.958136 2.01120804,20.8293182 L2.01120804,18.32426 C1.99400175,18.2187196 1.98505595,18.1104045 1.98505595,18 Z M6.5,14 C6.22385763,14 6,14.2238576 6,14.5 C6,14.7761424 6.22385763,15 6.5,15 L11.5,15 C11.7761424,15 12,14.7761424 12,14.5 C12,14.2238576 11.7761424,14 11.5,14 L6.5,14 Z M9.5,16 C9.22385763,16 9,16.2238576 9,16.5 C9,16.7761424 9.22385763,17 9.5,17 L11.5,17 C11.7761424,17 12,16.7761424 12,16.5 C12,16.2238576 11.7761424,16 11.5,16 L9.5,16 Z" fill="#000000" opacity="0.3" />
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Top Authors</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-primary">
                                                                                <div class="progress-bar bg-primary" role="progressbar" style={{width: "46%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">46%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th class="ps-0 py-3">
                                                                    <div class="symbol symbol-65px bg-light-danger me-3">
                                                                        <span class="symbol-label">

                                                                            <span class="svg-icon svg-icon-1 svg-icon-danger">
                                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                                        <path d="M5,3 L6,3 C6.55228475,3 7,3.44771525 7,4 L7,20 C7,20.5522847 6.55228475,21 6,21 L5,21 C4.44771525,21 4,20.5522847 4,20 L4,4 C4,3.44771525 4.44771525,3 5,3 Z M10,3 L11,3 C11.5522847,3 12,3.44771525 12,4 L12,20 C12,20.5522847 11.5522847,21 11,21 L10,21 C9.44771525,21 9,20.5522847 9,20 L9,4 C9,3.44771525 9.44771525,3 10,3 Z" fill="#000000" />
                                                                                        <rect fill="#000000" opacity="0.3" transform="translate(17.825568, 11.945519) rotate(-19.000000) translate(-17.825568, -11.945519)" x="16.3255682" y="2.94551858" width="3" height="18" rx="1" />
                                                                                    </g>
                                                                                </svg>
                                                                            </span>

                                                                        </span>
                                                                    </div>
                                                                </th>
                                                                <td class="ps-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Weekly Bestsellers</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML/CSS/JS, Python</span>
                                                                </td>
                                                                <td>
                                                                    <div class="d-flex flex-column w-100 me-3">
                                                                        <div class="d-flex flex-stack mb-2">
                                                                            <span class="text-dark me-2 fs-6 fw-bolder">Progress</span>
                                                                        </div>
                                                                        <div class="d-flex align-items-center">
                                                                            <div class="progress h-6px w-100 bg-light-danger">
                                                                                <div class="progress-bar bg-danger" role="progressbar" style={{width: "92%"}} aria-valuenow="50" aria-valuemin="0" aria-valuemax="100"></div>
                                                                            </div>
                                                                            <span class="text-muted fs-7 fw-bold ps-3">92%</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>

                                        </div>
                                    </div>
                                </div>

                            </div>
                        </div>


                        <div class="row g-0 g-xl-5 g-xxl-8">
                            <div class="col-xl-4">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-header align-items-center border-0 mt-5">
                                        <h3 class="card-title align-items-start flex-column">
                                            <span class="fw-bolder text-dark fs-3">Sales Share</span>
                                            <span class="text-muted mt-2 fw-bold fs-6">890,344 Sales</span>
                                        </h3>
                                        <div class="card-toolbar">

                                            <button type="button" class="btn btn-sm btn-icon btn-color-primary btn-active-light-primary" data-kt-menu-trigger="click" data-kt-menu-placement="bottom-end" data-kt-menu-flip="top-end">

                                                <span class="svg-icon svg-icon-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                            <rect x="5" y="5" width="5" height="5" rx="1" fill="#000000" />
                                                            <rect x="14" y="5" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="5" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="14" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                        </g>
                                                    </svg>
                                                </span>

                                            </button>

                                            <div class="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-600 menu-state-bg-light-primary fw-bold w-200px" data-kt-menu="true">
                                                <div class="menu-item px-3">
                                                    <div class="menu-content fs-6 text-dark fw-bolder px-3 py-4">Manage</div>
                                                </div>
                                                <div class="separator mb-3 opacity-75"></div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Add User</a>
                                                </div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Add Role</a>
                                                </div>
                                                <div class="menu-item px-3" data-kt-menu-trigger="hover" data-kt-menu-placement="right-start" data-kt-menu-flip="left-start, top">
                                                    <a href="#" class="menu-link px-3">
                                                        <span class="menu-title">Add Group</span>
                                                        <span class="menu-arrow"></span>
                                                    </a>
                                                    <div class="menu-sub menu-sub-dropdown w-200px py-4">
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Admin Group</a>
                                                        </div>
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Staff Group</a>
                                                        </div>
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Member Group</a>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Reports</a>
                                                </div>
                                                <div class="separator mt-3 opacity-75"></div>
                                                <div class="menu-item px-3">
                                                    <div class="menu-content px-3 py-3">
                                                        <a class="btn btn-primary fw-bold btn-sm px-4" href="#">Create New</a>
                                                    </div>
                                                </div>
                                            </div>


                                        </div>
                                    </div>


                                    <div class="card-body pt-12">

                                        <div class="d-flex flex-center position-relative bgi-no-repeat bgi-size-contain bgi-position-x-center bgi-position-y-center h-175px">
                                            <div class="fw-bolder fs-1 text-gray-800 position-absolute">8,345</div>
                                            <canvas id="kt_stats_widget_1_chart"></canvas>
                                        </div>


                                        <div class="d-flex justify-content-around pt-18">

                                            <div class="">
                                                <span class="fw-bolder text-gray-800">48% SNT</span>
                                                <span class="bg-info w-25px h-5px d-block rounded mt-1"></span>
                                            </div>


                                            <div class="">
                                                <span class="fw-bolder text-gray-800">20% REX</span>
                                                <span class="bg-primary w-25px h-5px d-block rounded mt-1"></span>
                                            </div>


                                            <div class="">
                                                <span class="fw-bolder text-gray-800">32% SAP</span>
                                                <span class="bg-warning w-25px h-5px d-block rounded mt-1"></span>
                                            </div>

                                        </div>

                                    </div>

                                </div>

                            </div>
                            <div class="col-xl-8">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-header align-items-center border-0 mt-5">
                                        <h3 class="card-title align-items-start flex-column">
                                            <span class="fw-bolder text-dark fs-3">Milestones</span>
                                            <span class="text-muted mt-2 fw-bold fs-6">890,344 Sales</span>
                                        </h3>
                                        <div class="card-toolbar">

                                            <button type="button" class="btn btn-sm btn-icon btn-color-primary btn-active-light-primary" data-kt-menu-trigger="click" data-kt-menu-placement="bottom-end" data-kt-menu-flip="top-end">

                                                <span class="svg-icon svg-icon-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                            <rect x="5" y="5" width="5" height="5" rx="1" fill="#000000" />
                                                            <rect x="14" y="5" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="5" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="14" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                        </g>
                                                    </svg>
                                                </span>

                                            </button>

                                            <div class="menu menu-sub menu-sub-dropdown menu-column w-300px w-lg-350px p-5" data-kt-menu="true">

                                                <div class="input-group input-group-solid mb-5">
                                                    <div class="input-group-prepend">
                                                        <span class="input-group-text">

                                                            <span class="svg-icon svg-icon-4">
                                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                        <rect x="0" y="0" width="24" height="24" />
                                                                        <path d="M14.2928932,16.7071068 C13.9023689,16.3165825 13.9023689,15.6834175 14.2928932,15.2928932 C14.6834175,14.9023689 15.3165825,14.9023689 15.7071068,15.2928932 L19.7071068,19.2928932 C20.0976311,19.6834175 20.0976311,20.3165825 19.7071068,20.7071068 C19.3165825,21.0976311 18.6834175,21.0976311 18.2928932,20.7071068 L14.2928932,16.7071068 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                        <path d="M11,16 C13.7614237,16 16,13.7614237 16,11 C16,8.23857625 13.7614237,6 11,6 C8.23857625,6 6,8.23857625 6,11 C6,13.7614237 8.23857625,16 11,16 Z M11,18 C7.13400675,18 4,14.8659932 4,11 C4,7.13400675 7.13400675,4 11,4 C14.8659932,4 18,7.13400675 18,11 C18,14.8659932 14.8659932,18 11,18 Z" fill="#000000" fill-rule="nonzero" />
                                                                    </g>
                                                                </svg>
                                                            </span>

                                                        </span>
                                                    </div>
                                                    <input type="text" class="form-control ps-0" name="search" value="" placeholder="Filter reports" />
                                                </div>


                                                <ul class="nav nav-line-tabs nav-line-tabs-2x border-light fw-bold mb-5">
                                                    <li class="nav-item">
                                                        <a class="nav-link active" data-bs-toggle="tab" href="#kt_dropdown_2_tab_1">Today</a>
                                                    </li>
                                                    <li class="nav-item">
                                                        <a class="nav-link" data-bs-toggle="tab" href="#kt_dropdown_2_tab_2">Last Week</a>
                                                    </li>
                                                </ul>


                                                <div class="tab-content">

                                                    <div class="tab-pane active" id="kt_dropdown_2_tab_1">
                                                        <ul class="menu menu-custom menu-column menu-rounded menu-title-gray-600 menu-icon-muted menu-hover-bg-light-primary menu-active-bg-light-primary fw-bold">
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <path d="M5.85714286,2 L13.7364114,2 C14.0910962,2 14.4343066,2.12568431 14.7051108,2.35473959 L19.4686994,6.3839416 C19.8056532,6.66894833 20,7.08787823 20,7.52920201 L20,20.0833333 C20,21.8738751 19.9795521,22 18.1428571,22 L5.85714286,22 C4.02044787,22 4,21.8738751 4,20.0833333 L4,3.91666667 C4,2.12612489 4.02044787,2 5.85714286,2 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                                    <path d="M10.782158,15.8052934 L15.1856088,12.7952868 C15.4135806,12.6394552 15.4720618,12.3283211 15.3162302,12.1003494 C15.2814587,12.0494808 15.2375842,12.0054775 15.1868178,11.970557 L10.783367,8.94156929 C10.5558531,8.78507001 10.2445489,8.84263875 10.0880496,9.07015268 C10.0307022,9.15352258 10,9.25233045 10,9.35351969 L10,15.392514 C10,15.6686564 10.2238576,15.892514 10.5,15.892514 C10.6006894,15.892514 10.699033,15.8621141 10.782158,15.8052934 Z" fill="#000000" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Web &amp; App History</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M6,2 L18,2 C18.5522847,2 19,2.44771525 19,3 L19,13 C19,13.5522847 18.5522847,14 18,14 L6,14 C5.44771525,14 5,13.5522847 5,13 L5,3 C5,2.44771525 5.44771525,2 6,2 Z M13.8,4 C13.1562,4 12.4033,4.72985286 12,5.2 C11.5967,4.72985286 10.8438,4 10.2,4 C9.0604,4 8.4,4.88887193 8.4,6.02016349 C8.4,7.27338783 9.6,8.6 12,10 C14.4,8.6 15.6,7.3 15.6,6.1 C15.6,4.96870845 14.9396,4 13.8,4 Z" fill="#000000" opacity="0.3" />
                                                                                <path d="M3.79274528,6.57253826 L12,12.5 L20.2072547,6.57253826 C20.4311176,6.4108595 20.7436609,6.46126971 20.9053396,6.68513259 C20.9668779,6.77033951 21,6.87277228 21,6.97787787 L21,17 C21,18.1045695 20.1045695,19 19,19 L5,19 C3.8954305,19 3,18.1045695 3,17 L3,6.97787787 C3,6.70173549 3.22385763,6.47787787 3.5,6.47787787 C3.60510559,6.47787787 3.70753836,6.51099993 3.79274528,6.57253826 Z" fill="#000000" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Activity and Timeline</span>
                                                                    <span class="menu-badge badge badge-light-danger fw-bold">new</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M22,17 L22,21 C22,22.1045695 21.1045695,23 20,23 L4,23 C2.8954305,23 2,22.1045695 2,21 L2,17 L6.27924078,17 L6.82339262,18.6324555 C7.09562072,19.4491398 7.8598984,20 8.72075922,20 L15.381966,20 C16.1395101,20 16.8320364,19.5719952 17.1708204,18.8944272 L18.118034,17 L22,17 Z" fill="#000000" />
                                                                                <path d="M2.5625,15 L5.92654389,9.01947752 C6.2807805,8.38972356 6.94714834,8 7.66969497,8 L16.330305,8 C17.0528517,8 17.7192195,8.38972356 18.0734561,9.01947752 L21.4375,15 L18.118034,15 C17.3604899,15 16.6679636,15.4280048 16.3291796,16.1055728 L15.381966,18 L8.72075922,18 L8.17660738,16.3675445 C7.90437928,15.5508602 7.1401016,15 6.27924078,15 L2.5625,15 Z" fill="#000000" opacity="0.3" />
                                                                                <path d="M11.1288761,0.733697713 L11.1288761,2.69017121 L9.12120481,2.69017121 C8.84506244,2.69017121 8.62120481,2.91402884 8.62120481,3.19017121 L8.62120481,4.21346991 C8.62120481,4.48961229 8.84506244,4.71346991 9.12120481,4.71346991 L11.1288761,4.71346991 L11.1288761,6.66994341 C11.1288761,6.94608579 11.3527337,7.16994341 11.6288761,7.16994341 C11.7471877,7.16994341 11.8616664,7.12798964 11.951961,7.05154023 L15.4576222,4.08341738 C15.6683723,3.90498251 15.6945689,3.58948575 15.5161341,3.37873564 C15.4982803,3.35764848 15.4787093,3.33807751 15.4576222,3.32022374 L11.951961,0.352100892 C11.7412109,0.173666017 11.4257142,0.199862688 11.2472793,0.410612793 C11.1708299,0.500907473 11.1288761,0.615386087 11.1288761,0.733697713 Z" fill="#000000" fill-rule="nonzero" transform="translate(11.959697, 3.661508) rotate(-270.000000) translate(-11.959697, -3.661508)" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Business Features</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <circle fill="#000000" cx="9" cy="15" r="6" />
                                                                                <path d="M8.8012943,7.00241953 C9.83837775,5.20768121 11.7781543,4 14,4 C17.3137085,4 20,6.6862915 20,10 C20,12.2218457 18.7923188,14.1616223 16.9975805,15.1987057 C16.9991904,15.1326658 17,15.0664274 17,15 C17,10.581722 13.418278,7 9,7 C8.93357256,7 8.86733422,7.00080962 8.8012943,7.00241953 Z" fill="#000000" opacity="0.3" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Accessibility Settings</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M7,14 C7,16.7614237 9.23857625,19 12,19 C14.7614237,19 17,16.7614237 17,14 C17,12.3742163 15.3702913,9.86852817 12,6.69922982 C8.62970872,9.86852817 7,12.3742163 7,14 Z M12,21 C8.13400675,21 5,17.8659932 5,14 C5,11.4226712 7.33333333,8.08933783 12,4 C16.6666667,8.08933783 19,11.4226712 19,14 C19,17.8659932 15.8659932,21 12,21 Z" fill="#000000" fill-rule="nonzero" />
                                                                                <path d="M12,4 C16.6666667,8.08933783 19,11.4226712 19,14 C19,17.8659932 15.8659932,21 12,21 L12,4 Z" fill="#000000" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Data &amp; Personalisation</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <rect x="0" y="0" width="24" height="24" />
                                                                                    <path d="M13.2070325,4 C13.0721672,4.47683179 13,4.97998812 13,5.5 C13,8.53756612 15.4624339,11 18.5,11 C19.0200119,11 19.5231682,10.9278328 20,10.7929675 L20,17 C20,18.6568542 18.6568542,20 17,20 L7,20 C5.34314575,20 4,18.6568542 4,17 L4,7 C4,5.34314575 5.34314575,4 7,4 L13.2070325,4 Z" fill="#000000" />
                                                                                    <circle fill="#000000" opacity="0.3" cx="18.5" cy="5.5" r="2.5" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">General Preference</span>
                                                                </a>
                                                            </li>
                                                        </ul>
                                                    </div>


                                                    <div class="tab-pane" id="kt_dropdown_2_tab_2">
                                                        <ul class="menu menu-custom menu-column menu-rounded menu-title-gray-600 menu-icon-muted menu-hover-bg-light-primary menu-active-bg-light-primary fw-bold">
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link active px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M6,2 L18,2 C18.5522847,2 19,2.44771525 19,3 L19,13 C19,13.5522847 18.5522847,14 18,14 L6,14 C5.44771525,14 5,13.5522847 5,13 L5,3 C5,2.44771525 5.44771525,2 6,2 Z M13.8,4 C13.1562,4 12.4033,4.72985286 12,5.2 C11.5967,4.72985286 10.8438,4 10.2,4 C9.0604,4 8.4,4.88887193 8.4,6.02016349 C8.4,7.27338783 9.6,8.6 12,10 C14.4,8.6 15.6,7.3 15.6,6.1 C15.6,4.96870845 14.9396,4 13.8,4 Z" fill="#000000" opacity="0.3" />
                                                                                <path d="M3.79274528,6.57253826 L12,12.5 L20.2072547,6.57253826 C20.4311176,6.4108595 20.7436609,6.46126971 20.9053396,6.68513259 C20.9668779,6.77033951 21,6.87277228 21,6.97787787 L21,17 C21,18.1045695 20.1045695,19 19,19 L5,19 C3.8954305,19 3,18.1045695 3,17 L3,6.97787787 C3,6.70173549 3.22385763,6.47787787 3.5,6.47787787 C3.60510559,6.47787787 3.70753836,6.51099993 3.79274528,6.57253826 Z" fill="#000000" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Activity and Timeline</span>
                                                                    <span class="menu-badge badge badge-danger fw-bold">new</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M22,17 L22,21 C22,22.1045695 21.1045695,23 20,23 L4,23 C2.8954305,23 2,22.1045695 2,21 L2,17 L6.27924078,17 L6.82339262,18.6324555 C7.09562072,19.4491398 7.8598984,20 8.72075922,20 L15.381966,20 C16.1395101,20 16.8320364,19.5719952 17.1708204,18.8944272 L18.118034,17 L22,17 Z" fill="#000000" />
                                                                                <path d="M2.5625,15 L5.92654389,9.01947752 C6.2807805,8.38972356 6.94714834,8 7.66969497,8 L16.330305,8 C17.0528517,8 17.7192195,8.38972356 18.0734561,9.01947752 L21.4375,15 L18.118034,15 C17.3604899,15 16.6679636,15.4280048 16.3291796,16.1055728 L15.381966,18 L8.72075922,18 L8.17660738,16.3675445 C7.90437928,15.5508602 7.1401016,15 6.27924078,15 L2.5625,15 Z" fill="#000000" opacity="0.3" />
                                                                                <path d="M11.1288761,0.733697713 L11.1288761,2.69017121 L9.12120481,2.69017121 C8.84506244,2.69017121 8.62120481,2.91402884 8.62120481,3.19017121 L8.62120481,4.21346991 C8.62120481,4.48961229 8.84506244,4.71346991 9.12120481,4.71346991 L11.1288761,4.71346991 L11.1288761,6.66994341 C11.1288761,6.94608579 11.3527337,7.16994341 11.6288761,7.16994341 C11.7471877,7.16994341 11.8616664,7.12798964 11.951961,7.05154023 L15.4576222,4.08341738 C15.6683723,3.90498251 15.6945689,3.58948575 15.5161341,3.37873564 C15.4982803,3.35764848 15.4787093,3.33807751 15.4576222,3.32022374 L11.951961,0.352100892 C11.7412109,0.173666017 11.4257142,0.199862688 11.2472793,0.410612793 C11.1708299,0.500907473 11.1288761,0.615386087 11.1288761,0.733697713 Z" fill="#000000" fill-rule="nonzero" transform="translate(11.959697, 3.661508) rotate(-270.000000) translate(-11.959697, -3.661508)" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Business Features</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <circle fill="#000000" cx="9" cy="15" r="6" />
                                                                                <path d="M8.8012943,7.00241953 C9.83837775,5.20768121 11.7781543,4 14,4 C17.3137085,4 20,6.6862915 20,10 C20,12.2218457 18.7923188,14.1616223 16.9975805,15.1987057 C16.9991904,15.1326658 17,15.0664274 17,15 C17,10.581722 13.418278,7 9,7 C8.93357256,7 8.86733422,7.00080962 8.8012943,7.00241953 Z" fill="#000000" opacity="0.3" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Accessibility Settings</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <path d="M7,14 C7,16.7614237 9.23857625,19 12,19 C14.7614237,19 17,16.7614237 17,14 C17,12.3742163 15.3702913,9.86852817 12,6.69922982 C8.62970872,9.86852817 7,12.3742163 7,14 Z M12,21 C8.13400675,21 5,17.8659932 5,14 C5,11.4226712 7.33333333,8.08933783 12,4 C16.6666667,8.08933783 19,11.4226712 19,14 C19,17.8659932 15.8659932,21 12,21 Z" fill="#000000" fill-rule="nonzero" />
                                                                                <path d="M12,4 C16.6666667,8.08933783 19,11.4226712 19,14 C19,17.8659932 15.8659932,21 12,21 L12,4 Z" fill="#000000" />
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Data &amp; Personalisation</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <rect x="0" y="0" width="24" height="24" />
                                                                                    <path d="M13.2070325,4 C13.0721672,4.47683179 13,4.97998812 13,5.5 C13,8.53756612 15.4624339,11 18.5,11 C19.0200119,11 19.5231682,10.9278328 20,10.7929675 L20,17 C20,18.6568542 18.6568542,20 17,20 L7,20 C5.34314575,20 4,18.6568542 4,17 L4,7 C4,5.34314575 5.34314575,4 7,4 L13.2070325,4 Z" fill="#000000" />
                                                                                    <circle fill="#000000" opacity="0.3" cx="18.5" cy="5.5" r="2.5" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">General Preference</span>
                                                                </a>
                                                            </li>
                                                            <li class="menu-item py-1">
                                                                <a href="#" class="menu-link px-3">
                                                                    <span class="menu-icon">

                                                                        <span class="svg-icon svg-icon-1">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <path d="M5.85714286,2 L13.7364114,2 C14.0910962,2 14.4343066,2.12568431 14.7051108,2.35473959 L19.4686994,6.3839416 C19.8056532,6.66894833 20,7.08787823 20,7.52920201 L20,20.0833333 C20,21.8738751 19.9795521,22 18.1428571,22 L5.85714286,22 C4.02044787,22 4,21.8738751 4,20.0833333 L4,3.91666667 C4,2.12612489 4.02044787,2 5.85714286,2 Z" fill="#000000" fill-rule="nonzero" opacity="0.3" />
                                                                                    <path d="M10.782158,15.8052934 L15.1856088,12.7952868 C15.4135806,12.6394552 15.4720618,12.3283211 15.3162302,12.1003494 C15.2814587,12.0494808 15.2375842,12.0054775 15.1868178,11.970557 L10.783367,8.94156929 C10.5558531,8.78507001 10.2445489,8.84263875 10.0880496,9.07015268 C10.0307022,9.15352258 10,9.25233045 10,9.35351969 L10,15.392514 C10,15.6686564 10.2238576,15.892514 10.5,15.892514 C10.6006894,15.892514 10.699033,15.8621141 10.782158,15.8052934 Z" fill="#000000" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </span>
                                                                    <span class="menu-title">Web &amp; App History</span>
                                                                </a>
                                                            </li>
                                                        </ul>
                                                    </div>

                                                </div>

                                            </div>


                                        </div>
                                    </div>


                                    <div class="card-body pt-0">
                                        <div class="d-flex flex-wrap flex-xxl-nowrap justify-content-center justify-content-md-start pt-4">

                                            <div class="me-sm-10 me-0">
                                                <ul class="nav flex-column nav-pills nav-pills-custom">
                                                    <li class="nav-item mb-3">
                                                        <a class="nav-link active w-225px h-70px" data-bs-toggle="pill" id="kt_stats_widget_2_tab_1" href="#kt_stats_widget_2_tab_1_content">
                                                            <div class="nav-icon me-3">
                                                                <img alt="" src="assets/media/svg/logo/gray/aven.svg" class="default" />
                                                                <img alt="" src="assets/media/svg/logo/colored/aven.svg" class="active" />
                                                            </div>
                                                            <div class="ps-1">
                                                                <span class="nav-text text-gray-600 fw-bolder fs-6">Man&amp;Flower SaaS</span>
                                                                <span class="text-muted fw-bold d-block pt-1">HR Solutions</span>
                                                            </div>
                                                        </a>
                                                    </li>
                                                    <li class="nav-item mb-3">
                                                        <a class="nav-link w-225px h-70px" data-bs-toggle="pill" id="kt_stats_widget_2_tab_2" href="#kt_stats_widget_2_tab_2_content">
                                                            <div class="nav-icon me-3">
                                                                <img alt="" src="assets/media/svg/logo/gray/tower.svg" class="default" />
                                                                <img alt="" src="assets/media/svg/logo/colored/tower.svg" class="active" />
                                                            </div>
                                                            <div class="ps-1">
                                                                <span class="nav-text text-gray-600 fw-bolder fs-6">Building Studio</span>
                                                                <span class="text-muted fw-bold d-block pt-1">HR Solutions</span>
                                                            </div>
                                                        </a>
                                                    </li>
                                                    <li class="nav-item mb-3">
                                                        <a class="nav-link w-225px h-70px" data-bs-toggle="pill" id="kt_stats_widget_2_tab_3" href="#kt_stats_widget_2_tab_3_content">
                                                            <div class="nav-icon me-3">
                                                                <img alt="" src="assets/media/svg/logo/gray/fox-hub-2.svg" class="default" />
                                                                <img alt="" src="assets/media/svg/logo/colored/fox-hub-2.svg" class="active" />
                                                            </div>
                                                            <div class="ps-1">
                                                                <span class="nav-text text-gray-600 fw-bolder fs-6">Foxy Solutions</span>
                                                                <span class="text-muted fw-bold d-block pt-1">HR Solutions</span>
                                                            </div>
                                                        </a>
                                                    </li>
                                                    <li class="nav-item mb-5">
                                                        <a class="nav-link w-225px h-70px" data-bs-toggle="pill" id="kt_stats_widget_2_tab_4" href="#kt_stats_widget_2_tab_4_content">
                                                            <div class="nav-icon me-3">
                                                                <img alt="" src="assets/media/svg/logo/gray/kanba.svg" class="default" />
                                                                <img alt="" src="assets/media/svg/logo/colored/kanba.svg" class="active" />
                                                            </div>
                                                            <div class="ps-1">
                                                                <span class="nav-text text-gray-600 fw-bolder fs-6">MyStreams</span>
                                                                <span class="text-muted fw-bold d-block pt-1">HR Solutions</span>
                                                            </div>
                                                        </a>
                                                    </li>
                                                </ul>
                                            </div>


                                            <div class="tab-content flex-grow-1">

                                                <div class="tab-pane fade show active" id="kt_stats_widget_2_tab_1_content">

                                                    <div class="d-flex justify-content-center mb-10">

                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Sale</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$650</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Commission</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$2,040</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Refers</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">8,926</span>
                                                        </div>

                                                    </div>


                                                    <div id="kt_stats_widget_2_chart_1"></div>

                                                </div>


                                                <div class="tab-pane fade" id="kt_stats_widget_2_tab_2_content">

                                                    <div class="d-flex justify-content-center mb-10">

                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Sale</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$1250</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Commission</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$5,000</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Refers</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">4,926</span>
                                                        </div>

                                                    </div>


                                                    <div id="kt_stats_widget_2_chart_2" ></div>

                                                </div>


                                                <div class="tab-pane fade" id="kt_stats_widget_2_tab_3_content">

                                                    <div class="d-flex justify-content-center mb-10">

                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Sale</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$350</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Comission</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$1,200</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Refers</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">5,500</span>
                                                        </div>

                                                    </div>


                                                    <div id="kt_stats_widget_2_chart_3" ></div>

                                                </div>


                                                <div class="tab-pane fade" id="kt_stats_widget_2_tab_4_content">

                                                    <div class="d-flex justify-content-center mb-10">

                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Sale</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$450</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Comission</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">$6,500</span>
                                                        </div>


                                                        <div class="px-10">
                                                            <span class="text-muted fw-bold fs-7">Refers</span>
                                                            <span class="text-gray-800 fw-bolder fs-3 d-block">500</span>
                                                        </div>

                                                    </div>


                                                    <div id="kt_stats_widget_2_chart_4" ></div>

                                                </div>

                                            </div>

                                        </div>
                                    </div>

                                </div>

                            </div>
                        </div>


                        <div class="row g-0 g-xl-5 g-xxl-8">
                            <div class="col-xl-4">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-header align-items-center border-0 mt-5">
                                        <h3 class="card-title align-items-start flex-column">
                                            <span class="fw-bolder text-dark fs-3">Timeline</span>
                                            <span class="text-muted mt-2 fw-bold fs-6">Updates &amp; notifications</span>
                                        </h3>
                                        <div class="card-toolbar">

                                            <button type="button" class="btn btn-sm btn-icon btn-color-primary btn-active-light-primary" data-kt-menu-trigger="click" data-kt-menu-placement="bottom-end" data-kt-menu-flip="top-end">

                                                <span class="svg-icon svg-icon-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                            <rect x="5" y="5" width="5" height="5" rx="1" fill="#000000" />
                                                            <rect x="14" y="5" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="5" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                            <rect x="14" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                        </g>
                                                    </svg>
                                                </span>

                                            </button>

                                            <div class="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-600 menu-state-bg-light-primary fw-bold w-200px" data-kt-menu="true">
                                                <div class="menu-item px-3">
                                                    <div class="menu-content fs-6 text-dark fw-bolder px-3 py-4">Manage</div>
                                                </div>
                                                <div class="separator mb-3 opacity-75"></div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Add User</a>
                                                </div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Add Role</a>
                                                </div>
                                                <div class="menu-item px-3" data-kt-menu-trigger="hover" data-kt-menu-placement="right-start" data-kt-menu-flip="left-start, top">
                                                    <a href="#" class="menu-link px-3">
                                                        <span class="menu-title">Add Group</span>
                                                        <span class="menu-arrow"></span>
                                                    </a>
                                                    <div class="menu-sub menu-sub-dropdown w-200px py-4">
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Admin Group</a>
                                                        </div>
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Staff Group</a>
                                                        </div>
                                                        <div class="menu-item px-3">
                                                            <a href="#" class="menu-link px-3">Member Group</a>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div class="menu-item px-3">
                                                    <a href="#" class="menu-link px-3">Reports</a>
                                                </div>
                                                <div class="separator mt-3 opacity-75"></div>
                                                <div class="menu-item px-3">
                                                    <div class="menu-content px-3 py-3">
                                                        <a class="btn btn-primary fw-bold btn-sm px-4" href="#">Create New</a>
                                                    </div>
                                                </div>
                                            </div>


                                        </div>
                                    </div>


                                    <div class="card-body pt-3">

                                        <div class="timeline-label">

                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">08:42</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-warning fs-1"></i>
                                                </div>


                                                <div class="fw-mormal timeline-content text-muted ps-3">Outlines keep you honest. And keep structure</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">10:00</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-success fs-1"></i>
                                                </div>


                                                <div class="timeline-content d-flex">
                                                    <span class="fw-bolder text-gray-800 ps-3">AEOL meeting</span>
                                                </div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">14:37</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-danger fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-bolder text-gray-800 ps-3">Make deposit
                                                    <a href="#" class="text-primary">USD 700</a>. to ESL</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">16:50</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-primary fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-mormal text-muted ps-3">Indulging in poorly driving and keep structure keep great</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">21:03</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-danger fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-bold text-gray-800 ps-3">New order placed
                                                    <a href="#" class="text-primary">#XF-2356</a>.</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">16:50</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-primary fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-mormal text-muted ps-3">Indulging in poorly driving and keep structure keep great</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">21:03</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-danger fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-bold text-gray-800 ps-3">New order placed
                                                    <a href="#" class="text-primary">#XF-2356</a>.</div>

                                            </div>


                                            <div class="timeline-item">

                                                <div class="timeline-label fw-bolder text-gray-800 fs-6">10:30</div>


                                                <div class="timeline-badge">
                                                    <i class="fa fa-genderless text-success fs-1"></i>
                                                </div>


                                                <div class="timeline-content fw-mormal text-muted ps-3">Finance KPI Mobile app launch preparion meeting</div>

                                            </div>

                                        </div>

                                    </div>

                                </div>

                            </div>
                            <div class="col-xl-8">

                                <div class="card card-stretch mb-5 mb-xxl-8">

                                    <div class="card-header border-0 pt-5">
                                        <h3 class="card-title align-items-start flex-column">
                                            <span class="card-label fw-bolder text-dark fs-3">Achievement</span>
                                            <span class="text-muted mt-2 fw-bold fs-6">890,344 Sales</span>
                                        </h3>
                                        <div class="card-toolbar">
                                            <ul class="nav nav-pills nav-pills-sm nav-light">
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 active fw-bolder me-2" data-bs-toggle="tab" href="#kt_tab_pane_2_1">Day</a>
                                                </li>
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 fw-bolder me-2" data-bs-toggle="tab" href="#kt_tab_pane_2_2">Week</a>
                                                </li>
                                                <li class="nav-item">
                                                    <a class="nav-link btn btn-active-light btn-color-muted py-2 px-4 fw-bolder" data-bs-toggle="tab" href="#kt_tab_pane_2_3">Month</a>
                                                </li>
                                            </ul>
                                        </div>
                                    </div>


                                    <div class="card-body pt-3 pb-0 mt-n3">
                                        <div class="tab-content mt-4" id="myTabTables2">

                                            <div class="tab-pane fade show active" id="kt_tab_pane_2_1" role="tabpanel" aria-labelledby="kt_tab_pane_2_1">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-150px"></th>
                                                                <th class="p-0 min-w-120px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-50px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1 me-5">
                                                                        <span class="symbol-label bg-light-primary align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/001-boy.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Brad Simmons</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, CSS Coding</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-primary">+28%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-danger align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/018-girl-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Jessie Clarcson</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Most Successful</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-danger">+52%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-warning align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/047-girl-25.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Lebron Wayde</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Users</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$3,400,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-warning">+34%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-success align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/043-boy-18.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Kevin Leonard</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Userss</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$35,600,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-success">+230%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="px-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-info align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/024-boy-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Randy Trent</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Business Analyst</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$45,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-info">+340%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>


                                            <div class="tab-pane fade" id="kt_tab_pane_2_2" role="tabpanel" aria-labelledby="kt_tab_pane_2_2">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-150px"></th>
                                                                <th class="p-0 min-w-120px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-50px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td class="p-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1 me-5">
                                                                        <span class="symbol-label bg-light-warning align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/047-girl-25.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Lebron Wayde</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Users</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$3,400,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-warning">+34%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-success align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/043-boy-18.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Kevin Leonard</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Userss</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$35,600,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-success">+230%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-info align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/024-boy-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Randy Trent</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Business Analyst</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$45,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-info">+340%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 py-3">
                                                                    <div class="symbol symbol-55px me-5 mt-1">
                                                                        <span class="symbol-label bg-light-primary align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/001-boy.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Brad Simmons</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, CSS Coding</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-primary">+28%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 py-3">
                                                                    <div class="symbol symbol-55px mt-1">
                                                                        <span class="symbol-label bg-light-danger align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/018-girl-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Jessie Clarcson</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Most Successful</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-danger">+52%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>


                                            <div class="tab-pane fade" id="kt_tab_pane_2_3" role="tabpanel" aria-labelledby="kt_tab_pane_2_3">

                                                <div class="table-responsive">
                                                    <table class="table table-borderless align-middle">
                                                        <thead>
                                                            <tr>
                                                                <th class="p-0 w-50px"></th>
                                                                <th class="p-0 min-w-150px"></th>
                                                                <th class="p-0 min-w-120px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-70px"></th>
                                                                <th class="p-0 min-w-50px"></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td class="p-0 pb-3 pt-1">
                                                                    <div class="symbol symbol-55px mt-3 me-5">
                                                                        <span class="symbol-label bg-light-danger align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/018-girl-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Jessie Clarcson</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Most Successful</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-danger">+52%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 pb-3 pt-1">
                                                                    <div class="symbol symbol-55px mt-3">
                                                                        <span class="symbol-label bg-light-warning align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/047-girl-25.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Lebron Wayde</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Users</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$3,400,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-warning">+34%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 pb-3 pt-1">
                                                                    <div class="symbol symbol-55px mt-3">
                                                                        <span class="symbol-label bg-light-success align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/043-boy-18.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Kevin Leonard</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Awesome Userss</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$35,600,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-success">+230%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 pb-3 pt-1">
                                                                    <div class="symbol symbol-55px me-5 mt-3">
                                                                        <span class="symbol-label bg-light-primary align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/001-boy.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Brad Simmons</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">HTML, CSS Coding</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$1,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-primary">+28%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="p-0 pb-3 pt-1">
                                                                    <div class="symbol symbol-55px mt-3">
                                                                        <span class="symbol-label bg-light-info align-items-end">
                                                                            <img alt="Logo" src="assets/media/svg/avatars/024-boy-9.svg" class="mh-40px" />
                                                                        </span>
                                                                    </div>
                                                                </td>
                                                                <td class="px-0">
                                                                    <a href="#" class="text-gray-800 fw-bolder text-hover-primary fs-6">Randy Trent</a>
                                                                    <span class="text-muted fw-bold d-block mt-1">Business Analyst</span>
                                                                </td>
                                                                <td></td>
                                                                <td class="text-end">
                                                                    <span class="text-gray-800 fw-bolder d-block fs-6">$45,200,000</span>
                                                                    <span class="text-muted fw-bold d-block mt-1 fs-7">Paid</span>
                                                                </td>
                                                                <td class="text-end">
                                                                    <span class="fw-bolder text-info">+340%</span>
                                                                </td>
                                                                <td class="text-end pe-0">
                                                                    <a href="#" class="btn btn-icon btn-bg-light btn-active-primary btn-sm">

                                                                        <span class="svg-icon svg-icon-4">
                                                                            <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                    <polygon points="0 0 24 0 24 24 0 24" />
                                                                                    <rect fill="#000000" opacity="0.5" transform="translate(12.000000, 12.000000) rotate(-90.000000) translate(-12.000000, -12.000000)" x="11" y="5" width="2" height="14" rx="1" />
                                                                                    <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                                </g>
                                                                            </svg>
                                                                        </span>

                                                                    </a>
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                            </div>

                                        </div>
                                    </div>

                                </div>

                            </div>
                        </div>



                        <div class="modal fade" id="kt_modal_create_app" tabindex="-1" aria-hidden="true">
                            <div class="modal-dialog modal-dialog-centered mw-1000px">
                                <div class="modal-content">
                                    <div class="container px-10 py-10">
                                        <div class="modal-header py-2 d-flex justify-content-end border-0">

                                            <div class="btn btn-icon btn-sm btn-light-primary" data-bs-dismiss="modal">

                                                <span class="svg-icon svg-icon-2">
                                                    <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                        <g transform="translate(12.000000, 12.000000) rotate(-45.000000) translate(-12.000000, -12.000000) translate(4.000000, 4.000000)" fill="#000000">
                                                            <rect fill="#000000" x="0" y="7" width="16" height="2" rx="1" />
                                                            <rect fill="#000000" opacity="0.5" transform="translate(8.000000, 8.000000) rotate(-270.000000) translate(-8.000000, -8.000000)" x="0" y="7" width="16" height="2" rx="1" />
                                                        </g>
                                                    </svg>
                                                </span>

                                            </div>

                                        </div>
                                        <div class="modal-body">

                                            <div class="stepper stepper-1 d-flex flex-column flex-xl-row flex-row-fluid" id="kt_modal_create_app_stepper">

                                                <div class="d-flex justify-content-center justify-content-xl-start flex-row-auto w-100 w-xl-300px">

                                                    <div class="stepper-nav d-flex flex-column pt-5">

                                                        <div class="stepper-item current" data-kt-stepper-element="nav">
                                                            <div class="stepper-wrapper">
                                                                <div class="stepper-icon">
                                                                    <i class="stepper-check fas fa-check"></i>
                                                                    <span class="stepper-number">1</span>
                                                                </div>
                                                                <div class="stepper-label">
                                                                    <h3 class="stepper-title">App Basics</h3>
                                                                    <div class="stepper-desc">Name your App</div>
                                                                </div>
                                                            </div>
                                                        </div>


                                                        <div class="stepper-item" data-kt-stepper-element="nav">
                                                            <div class="stepper-wrapper">
                                                                <div class="stepper-icon">
                                                                    <i class="stepper-check fas fa-check"></i>
                                                                    <span class="stepper-number">2</span>
                                                                </div>
                                                                <div class="stepper-label">
                                                                    <h3 class="stepper-title">App Framework</h3>
                                                                    <div class="stepper-desc">Define your app framework</div>
                                                                </div>
                                                            </div>
                                                        </div>


                                                        <div class="stepper-item" data-kt-stepper-element="nav">
                                                            <div class="stepper-wrapper">
                                                                <div class="stepper-icon">
                                                                    <i class="stepper-check fas fa-check"></i>
                                                                    <span class="stepper-number">3</span>
                                                                </div>
                                                                <div class="stepper-label">
                                                                    <h3 class="stepper-title">App Database</h3>
                                                                    <div class="stepper-desc">Select the app database type</div>
                                                                </div>
                                                            </div>
                                                        </div>


                                                        <div class="stepper-item" data-kt-stepper-element="nav">
                                                            <div class="stepper-wrapper">
                                                                <div class="stepper-icon">
                                                                    <i class="stepper-check fas fa-check"></i>
                                                                    <span class="stepper-number">4</span>
                                                                </div>
                                                                <div class="stepper-label">
                                                                    <h3 class="stepper-title">App Storage</h3>
                                                                    <div class="stepper-desc">Select the app storage type</div>
                                                                </div>
                                                            </div>
                                                        </div>


                                                        <div class="stepper-item" data-kt-stepper-element="nav">
                                                            <div class="stepper-wrapper">
                                                                <div class="stepper-icon">
                                                                    <i class="stepper-check fas fa-check"></i>
                                                                    <span class="stepper-number">5</span>
                                                                </div>
                                                                <div class="stepper-label">
                                                                    <h3 class="stepper-title">Completed!</h3>
                                                                    <div class="stepper-desc">Review and Submit</div>
                                                                </div>
                                                            </div>
                                                        </div>

                                                    </div>

                                                </div>


                                                <div class="d-flex flex-row-fluid justify-content-center">

                                                    <form class="pb-5 w-100 w-md-400px w-xl-500px" novalidate="novalidate" id="kt_modal_create_app_form">

                                                        <div class="pb-5 current" data-kt-stepper-element="content">
                                                            <div class="w-100">

                                                                <div class="pb-5 pb-lg-10">
                                                                    <h3 class="fw-bolder text-dark display-6">App Basics</h3>
                                                                </div>


                                                                <div class="fv-row mb-12">
                                                                    <label class="fs-6 fw-bolder text-dark form-label">Your App Name</label>
                                                                    <input type="text" class="form-control form-control-lg form-control-solid" name="appname" placeholder="" value="" />
                                                                </div>


                                                                <div class="fv-row">

                                                                    <label class="d-flex flex-stack mb-6 cursor-pointer">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-primary">

                                                                                    <span class="svg-icon svg-icon-1 svg-icon-primary">
                                                                                        <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                                <rect x="0" y="0" width="24" height="24" />
                                                                                                <path d="M13,18.9450712 L13,20 L14,20 C15.1045695,20 16,20.8954305 16,22 L8,22 C8,20.8954305 8.8954305,20 10,20 L11,20 L11,18.9448245 C9.02872877,18.7261967 7.20827378,17.866394 5.79372555,16.5182701 L4.73856106,17.6741866 C4.36621808,18.0820826 3.73370941,18.110904 3.32581341,17.7385611 C2.9179174,17.3662181 2.88909597,16.7337094 3.26143894,16.3258134 L5.04940685,14.367122 C5.46150313,13.9156769 6.17860937,13.9363085 6.56406875,14.4106998 C7.88623094,16.037907 9.86320756,17 12,17 C15.8659932,17 19,13.8659932 19,10 C19,7.73468744 17.9175842,5.65198725 16.1214335,4.34123851 C15.6753081,4.01567657 15.5775721,3.39010038 15.903134,2.94397499 C16.228696,2.49784959 16.8542722,2.4001136 17.3003976,2.72567554 C19.6071362,4.40902808 21,7.08906798 21,10 C21,14.6325537 17.4999505,18.4476269 13,18.9450712 Z" fill="#000000" fill-rule="nonzero" />
                                                                                                <circle fill="#000000" opacity="0.3" cx="12" cy="10" r="6" />
                                                                                            </g>
                                                                                        </svg>
                                                                                    </span>

                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Quick Online Courses</span>
                                                                                <span class="fs-7 text-muted">Creating a clear text structure is just one SEO</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" checked="checked" name="app_option_1" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack mb-6 cursor-pointer">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-danger">

                                                                                    <span class="svg-icon svg-icon-1 svg-icon-danger">
                                                                                        <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                                <rect x="5" y="5" width="5" height="5" rx="1" fill="#000000" />
                                                                                                <rect x="14" y="5" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                                                                <rect x="5" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                                                                <rect x="14" y="14" width="5" height="5" rx="1" fill="#000000" opacity="0.3" />
                                                                                            </g>
                                                                                        </svg>
                                                                                    </span>

                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Face to Face Discussions</span>
                                                                                <span class="fs-7 text-muted">Creating a clear text structure is just one aspect</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_1" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack mb-6 cursor-pointer">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-success">

                                                                                    <span class="svg-icon svg-icon-1 svg-icon-success">
                                                                                        <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                                            <path d="M9,8 C8.44771525,8 8,8.44771525 8,9 L8,15 C8,15.5522847 8.44771525,16 9,16 L15,16 C15.5522847,16 16,15.5522847 16,15 L16,9 C16,8.44771525 15.5522847,8 15,8 L9,8 Z M9,6 L15,6 C16.6568542,6 18,7.34314575 18,9 L18,15 C18,16.6568542 16.6568542,18 15,18 L9,18 C7.34314575,18 6,16.6568542 6,15 L6,9 C6,7.34314575 7.34314575,6 9,6 Z" fill="#000000" fill-rule="nonzero" />
                                                                                            <path d="M9,8 C8.44771525,8 8,8.44771525 8,9 L8,15 C8,15.5522847 8.44771525,16 9,16 L15,16 C15.5522847,16 16,15.5522847 16,15 L16,9 C16,8.44771525 15.5522847,8 15,8 L9,8 Z" fill="#000000" opacity="0.3" />
                                                                                            <path d="M9,18 L15,18 L15,20.5 C15,21.3284271 14.3284271,22 13.5,22 L10.5,22 C9.67157288,22 9,21.3284271 9,20.5 L9,18 Z" fill="#000000" opacity="0.3" />
                                                                                            <path d="M10.5,2 L13.5,2 C14.3284271,2 15,2.67157288 15,3.5 L15,6 L9,6 L9,3.5 C9,2.67157288 9.67157288,2 10.5,2 Z" fill="#000000" opacity="0.3" />
                                                                                        </svg>
                                                                                    </span>

                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Full Intro Training</span>
                                                                                <span class="fs-7 text-muted">Creating a clear text structure copywriting</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_1" value="1" />
                                                                        </span>
                                                                    </label>

                                                                </div>

                                                            </div>
                                                        </div>


                                                        <div class="pb-5" data-kt-stepper-element="content">
                                                            <div class="w-100">

                                                                <div class="pb-10 pb-lg-15">
                                                                    <h3 class="fw-bolder text-dark display-6">App Framework</h3>
                                                                </div>


                                                                <div class="fv-row">
                                                                    <label class="fs-6 fw-bolder text-dark mb-7">Select your app framework</label>

                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-warning">
                                                                                    <i class="fab fa-html5 text-warning fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">HTML5</span>
                                                                                <span class="fs-7 text-muted">Base Web Projec</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" checked="checked" name="app_option_2" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-success">
                                                                                    <i class="fab fa-react text-success fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">ReactJS</span>
                                                                                <span class="fs-7 text-muted">Robust and flexible app framework</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_2" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-danger">
                                                                                    <i class="fab fa-angular text-danger fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Angular</span>
                                                                                <span class="fs-7 text-muted">Powerful data mangement</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_2" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-primary">
                                                                                    <i class="fab fa-vuejs text-primary fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Vue</span>
                                                                                <span class="fs-7 text-muted">Lightweight and responsive framework</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_2" value="1" />
                                                                        </span>
                                                                    </label>

                                                                </div>

                                                            </div>
                                                        </div>


                                                        <div class="pb-5" data-kt-stepper-element="content">
                                                            <div class="w-100">

                                                                <div class="pb-10 pb-lg-15">
                                                                    <h3 class="fw-bolder text-dark display-6">App Database</h3>
                                                                </div>


                                                                <div class="fv-row mb-12">
                                                                    <label class="fs-6 fw-bolder text-dark form-label">App Databse Name Name</label>
                                                                    <input type="text" class="form-control form-control-lg form-control-solid" name="dbname" placeholder="" value="db_name" />
                                                                </div>


                                                                <div class="fv-row">
                                                                    <label class="fs-6 fw-bolder text-dark mb-7">Select your app database solution</label>

                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-success">
                                                                                    <i class="fas fa-database text-success fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">MySQL</span>
                                                                                <span class="fs-7 text-muted">Basic MySQL database</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_3" checked="checked" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-danger">
                                                                                    <i class="fab fa-google text-danger fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Firebase</span>
                                                                                <span class="fs-7 text-muted">Google based app data management</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_3" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-warning">
                                                                                    <i class="fab fa-amazon text-warning fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">DynamoDB</span>
                                                                                <span class="fs-7 text-muted">Amazon Fast NoSQL Database</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_3" value="1" />
                                                                        </span>
                                                                    </label>

                                                                </div>

                                                            </div>
                                                        </div>


                                                        <div class="pb-5" data-kt-stepper-element="content">
                                                            <div class="w-100">

                                                                <div class="pb-10 pb-lg-15">
                                                                    <h3 class="fw-bolder text-dark display-6">App Storage</h3>
                                                                </div>


                                                                <div class="fv-row">
                                                                    <label class="fs-6 fw-bolder text-dark mb-7">Select your app storage solution</label>

                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-primary">
                                                                                    <i class="fab fa-linux text-primary fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Basic Server</span>
                                                                                <span class="fs-7 text-muted">Linux based server storage</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" checked="checked" name="app_option_4" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-warning">
                                                                                    <i class="fab fa-aws text-warning fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">AWS</span>
                                                                                <span class="fs-7 text-muted">Amazon Web Services</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_4" value="1" />
                                                                        </span>
                                                                    </label>


                                                                    <label class="d-flex flex-stack cursor-pointer mb-6">
                                                                        <span class="d-flex align-items-center me-2">
                                                                            <span class="symbol symbol-50px me-6">
                                                                                <span class="symbol-label bg-light-success">
                                                                                    <i class="fab fa-google text-success fs-2x"></i>
                                                                                </span>
                                                                            </span>
                                                                            <span class="d-flex flex-column">
                                                                                <span class="fw-bolder fs-6">Google</span>
                                                                                <span class="fs-7 text-muted">Google Cloud Storage</span>
                                                                            </span>
                                                                        </span>
                                                                        <span class="form-check form-check-custom form-check-solid">
                                                                            <input class="form-check-input" type="radio" name="app_option_4" value="1" />
                                                                        </span>
                                                                    </label>

                                                                </div>

                                                            </div>
                                                        </div>


                                                        <div class="pb-5" data-kt-stepper-element="content">
                                                            <div class="w-100">

                                                                <div class="pb-10 pb-lg-15">
                                                                    <h3 class="fw-bolder text-dark display-6">Complete</h3>
                                                                    <div class="text-muted fw-bold fs-3">Review your setup to kickstart your app!</div>
                                                                </div>


                                                                <h4 class="fw-bolder mb-3">App Basics</h4>
                                                                <div class="text-gray-600 fw-bold lh-lg mb-8">
                                                                    <div>App Name</div>
                                                                    <div>Face to Face Discussions</div>
                                                                </div>


                                                                <h4 class="fw-bolder mb-3">App Framework</h4>
                                                                <div class="text-gray-600 fw-bold lh-lg mb-8">
                                                                    <div>HTML5</div>
                                                                </div>


                                                                <h4 class="fw-bolder mb-3">App Database</h4>
                                                                <div class="text-gray-600 fw-bold lh-lg mb-8">
                                                                    <div>dn_name</div>
                                                                    <div>Firebase</div>
                                                                </div>


                                                                <h4 class="fw-bolder mb-3">App Storage</h4>
                                                                <div class="text-gray-600 fw-bold lh-lg mb-8">
                                                                    <div>Basic Server</div>
                                                                </div>

                                                            </div>
                                                        </div>


                                                        <div class="d-flex justify-content-between pt-10">
                                                            <div class="mr-2">
                                                                <button type="button" class="btn btn-lg btn-light-primary fw-bolder py-4 pe-8 me-3" data-kt-stepper-action="previous">

                                                                    <span class="svg-icon svg-icon-3 me-1">
                                                                        <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                <polygon points="0 0 24 0 24 24 0 24" />
                                                                                <rect fill="#000000" opacity="0.3" transform="translate(15.000000, 12.000000) scale(-1, 1) rotate(-90.000000) translate(-15.000000, -12.000000)" x="14" y="7" width="2" height="10" rx="1" />
                                                                                <path d="M3.7071045,15.7071045 C3.3165802,16.0976288 2.68341522,16.0976288 2.29289093,15.7071045 C1.90236664,15.3165802 1.90236664,14.6834152 2.29289093,14.2928909 L8.29289093,8.29289093 C8.67146987,7.914312 9.28105631,7.90106637 9.67572234,8.26284357 L15.6757223,13.7628436 C16.0828413,14.136036 16.1103443,14.7686034 15.7371519,15.1757223 C15.3639594,15.5828413 14.7313921,15.6103443 14.3242731,15.2371519 L9.03007346,10.3841355 L3.7071045,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(9.000001, 11.999997) scale(-1, -1) rotate(90.000000) translate(-9.000001, -11.999997)" />
                                                                            </g>
                                                                        </svg>
                                                                    </span>
                                                                    Previous</button>
                                                            </div>
                                                            <div>
                                                                <button type="button" class="btn btn-lg btn-primary fw-bolder py-4 ps-8 me-3" data-kt-stepper-action="submit">Submit

                                                                    <span class="svg-icon svg-icon-3 ms-2">
                                                                        <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                <polygon points="0 0 24 0 24 24 0 24" />
                                                                                <rect fill="#000000" opacity="0.5" transform="translate(8.500000, 12.000000) rotate(-90.000000) translate(-8.500000, -12.000000)" x="7.5" y="7.5" width="2" height="9" rx="1" />
                                                                                <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                            </g>
                                                                        </svg>
                                                                    </span>
                                                                </button>
                                                                <button type="button" class="btn btn-lg btn-primary fw-bolder py-4 ps-8 me-3" data-kt-stepper-action="next">Next Step

                                                                    <span class="svg-icon svg-icon-3 ms-1">
                                                                        <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                                            <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                                                                <polygon points="0 0 24 0 24 24 0 24" />
                                                                                <rect fill="#000000" opacity="0.5" transform="translate(8.500000, 12.000000) rotate(-90.000000) translate(-8.500000, -12.000000)" x="7.5" y="7.5" width="2" height="9" rx="1" />
                                                                                <path d="M9.70710318,15.7071045 C9.31657888,16.0976288 8.68341391,16.0976288 8.29288961,15.7071045 C7.90236532,15.3165802 7.90236532,14.6834152 8.29288961,14.2928909 L14.2928896,8.29289093 C14.6714686,7.914312 15.281055,7.90106637 15.675721,8.26284357 L21.675721,13.7628436 C22.08284,14.136036 22.1103429,14.7686034 21.7371505,15.1757223 C21.3639581,15.5828413 20.7313908,15.6103443 20.3242718,15.2371519 L15.0300721,10.3841355 L9.70710318,15.7071045 Z" fill="#000000" fill-rule="nonzero" transform="translate(14.999999, 11.999997) scale(1, -1) rotate(90.000000) translate(-14.999999, -11.999997)" />
                                                                            </g>
                                                                        </svg>
                                                                    </span>
                                                                </button>
                                                            </div>
                                                        </div>

                                                    </form>

                                                </div>

                                            </div>

                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>


                        <div class="modal fade" id="kt_modal_select_location" data-backdrop="static" tabindex="-1" role="dialog" aria-hidden="true">
                            <div class="modal-dialog modal-xl" role="document">
                                <div class="modal-content">
                                    <div class="modal-header">
                                        <h5 class="modal-title">Select Location</h5>

                                        <div class="btn btn-icon btn-sm btn-active-light-primary ms-2" data-bs-dismiss="modal">

                                            <span class="svg-icon svg-icon-2x">
                                                <svg xmlns="http://www.w3.org/2000/svg"  width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                                    <g transform="translate(12.000000, 12.000000) rotate(-45.000000) translate(-12.000000, -12.000000) translate(4.000000, 4.000000)" fill="#000000">
                                                        <rect fill="#000000" x="0" y="7" width="16" height="2" rx="1" />
                                                        <rect fill="#000000" opacity="0.5" transform="translate(8.000000, 8.000000) rotate(-270.000000) translate(-8.000000, -8.000000)" x="0" y="7" width="16" height="2" rx="1" />
                                                    </g>
                                                </svg>
                                            </span>

                                        </div>

                                    </div>
                                    <div class="modal-body">
                                        <div id="kt_modal_select_location_map" class="map h-450px"></div>
                                    </div>
                                    <div class="modal-footer">
                                        <button type="button" class="btn btn-light-primary" data-bs-dismiss="modal">Cancel</button>
                                        <button id="submit" type="button" class="btn btn-primary" data-bs-dismiss="modal">Apply</button>
                                    </div>
                                </div>
                            </div>
                        </div>


                    </div>

                </div>

            </div>


        </>
    );
}