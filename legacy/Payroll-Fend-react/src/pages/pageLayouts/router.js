import { createBrowserRouter } from "react-router-dom";
import { ConfigProvider, theme } from "antd";
import { useState, useEffect } from 'react';

import AuthGuard from '../../shared/guards/authGuard';
import LoginGuard from "../../shared/guards/loginGuard";
import OrganizationGuard from "../../shared/guards/organizationGuard";

import AuthLayout from "./authLayout";
import ErrorPage from "../mainPages/errorPage";

import Login from "../authPages/login";
import ForgotPasswordPage from "../authPages/login/forgotPasswordPage";
import NewPassword from "../authPages/login/NewPassword";
//import Register from "../authPages/register";
import CreateAccountPage from "../authPages/login/createAccountPage";
import { rootLoader } from "../../shared/helpers/rootLoader";
import OnboardingDashboard from "../mainPages/dashboardPage/onboardingDashboard";

import DashboardPage from "../mainPages/dashboardPage";
import DashboardLayout from "../pageLayouts/dashboardLayout";
import SidebarLayout from "../pageLayouts/sidebarLayout";
import { Outlet } from "react-router-dom";
import OrganisationForm from "../mainPages/allSettingsPages/profile";
import SettingsLayout from "../pageLayouts/settingsLayout";
// /import OrganizationPage from "../mainPages/allSettingsPages";
import AllSettingsComponents from "../mainPages/allSettingsPages/allSettingsComponents";
import OrganisationProfile from "../mainPages/allSettingsPages/profile";
import Designations from "../mainPages/allSettingsPages/designations";
import Departments from "../mainPages/allSettingsPages/departments";
import Employee from "../mainPages/employee";
import ImportEmployee from "../mainPages/employee/importEmployee";
import AddEmployee from "../mainPages/employee/addEmployee";
import ViewEmployee from "../mainPages/employee/viewEmployee";
import OrganizationRegister from "../mainPages/organizationRegister";
import ImportDesignations from "../mainPages/allSettingsPages/designations/importDesignations";
import WorkLocations from "../mainPages/allSettingsPages/workLocations";
import ImportWorkLocations from "../mainPages/allSettingsPages/workLocations/importWorkLocations";
import AddWorkLocation from "../mainPages/allSettingsPages/workLocations/addWorkLocations";
import EditWorkLocationForm from "../mainPages/allSettingsPages/workLocations/editWorkLocations";
import EditBasicDetails from "../mainPages/employee/editBasicDetails";
import EditPersonalDetails from "../mainPages/employee/editPersonalDetails";
import EditStatutoryDetails from "../mainPages/employee/editStatutoryDetails";
import EditPaymentDetails from "../mainPages/employee/editPaymentDetails";
import EditSalaryDetails from "../mainPages/employee/editSalaryDetails";
import PayRuns from "../mainPages/payRuns";
import AddOneTimePayoutDetails from "../mainPages/payRuns/addOneTimePayoutDetails";
import ImportOneTimePayrunData from "../mainPages/payRuns/importOneTimePayrunData";
import Preview from "../mainPages/payRuns/preview";
import AddOffCycleDetails from "../mainPages/payRuns/addOffCycleDetails";
import ManageOrganization from "../mainPages/organizationRegister/manageOrganization";
import ImportDepartments from "../mainPages/allSettingsPages/departments/importDepartments";
import TaxDetails from "../mainPages/allSettingsPages/taxes/taxDetails";
import Users from "../mainPages/allSettingsPages/users";
import InviteUser from "../mainPages/allSettingsPages/users/inviteUser";
import EditUser from "../mainPages/allSettingsPages/users/editUser";
import PaySchedules from "../mainPages/allSettingsPages/paySchedules";
import PayScheduleView from "../mainPages/allSettingsPages/paySchedules/viewPaySchedules";
import EditPaySchedule from "../mainPages/allSettingsPages/paySchedules/editPaySchedule";
import StatutoryComponents from "../mainPages/allSettingsPages/statutoryComponents";
import EPF from "../mainPages/allSettingsPages/statutoryComponents/epf";
import EditEPF from "../mainPages/allSettingsPages/statutoryComponents/editEPF";
import ViewESI from "../mainPages/allSettingsPages/statutoryComponents/viewESI";
import FormESI from "../mainPages/allSettingsPages/statutoryComponents/formESI";
import EditESI from "../mainPages/allSettingsPages/statutoryComponents/editESI";
import ProfessionalTax from "../mainPages/allSettingsPages/statutoryComponents/viewProfessionalTax";
import SalaryComponents from "../mainPages/allSettingsPages/salaryComponents";
import Deduction from "../mainPages/allSettingsPages/salaryComponents/deductions";
import Benefits from "../mainPages/allSettingsPages/salaryComponents/benifits";
import Reimbursement from "../mainPages/allSettingsPages/salaryComponents/reimbursement";
import EditEarning from "../mainPages/allSettingsPages/salaryComponents/editEarning";
import AddNewEarning from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewEarning";
import AddNewCorrection from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewCorrection";
import AddNewBenefits from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewBenefits";
import AddNewDeduction from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewDeduction";
import AddNewReimbursement from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewReimbursement";
import AddNewCustomEarning from "../mainPages/allSettingsPages/salaryComponents/addComponents/addNewCustomEarning";
import EditDeduction from "../mainPages/allSettingsPages/salaryComponents/editDeduction";
import EditBenefit from "../mainPages/allSettingsPages/salaryComponents/editBenifits";
import EditReimbursement from "../mainPages/allSettingsPages/salaryComponents/editReimbursement";
import Preferences from "../mainPages/allSettingsPages/employeePortal/preference";
import SetupNewOrganization from "../mainPages/organizationRegister/setupNewOrganization";
import Roles from "../mainPages/allSettingsPages/users/roles";
import ViewRole from "../mainPages/allSettingsPages/users/viewRole";
import CreateNewRole from "../mainPages/allSettingsPages/users/newRole";
import BasicDetails from "../mainPages/employee/basicDetails";
import SalaryDetails from "../mainPages/employee/salaryDetails";
import PersonalDetails from "../mainPages/employee/personalDetails";
import PaymentInformation from "../mainPages/employee/paymentInformation";
import EmployeeLayout from "./employeeLayout";
import MyProfile from "../mainPages/userPortal/userProfile";
import Home from "../mainPages/userPortal/home";
import MySalaryDetails from "../mainPages/userPortal/userSalaryDetails";
import ChangePassword from "../mainPages/userPortal/ChangePassword";
import EditRole from "../mainPages/allSettingsPages/users/editRole";
import PayslipGenerator from "../mainPages/userPortal/payslipGenerator";
import PublicPayslipDownload from "../mainPages/userPortal/publicPayslipDownload";
import ImportBasicDetails from "../mainPages/employee/importBasicDetails";
import LeaveAttendanceSetup from "../mainPages/allSettingsPages/leaveAttendence/leaveAttendenceSetup";
import LeaveTypes from "../mainPages/allSettingsPages/leaveAttendence/leaveTypes";
import AddLeaveType from "../mainPages/allSettingsPages/leaveAttendence/addLeaveTypes";
import AddHoliday from "../mainPages/allSettingsPages/leaveAttendence/addHoliday";
import Holidays from "../mainPages/allSettingsPages/leaveAttendence/holidays";
import Attendance from "../mainPages/allSettingsPages/leaveAttendence/attendence";
import ImportLeaveBalance from "../mainPages/allSettingsPages/leaveAttendence/importLeaveBalance";
import EditLeaveType from "../mainPages/allSettingsPages/leaveAttendence/editLeaveType";
import AttendancePreferences from "../mainPages/allSettingsPages/leaveAttendence/attendencePreferences";
import Summary from "../mainPages/payRuns/summary";
import EmployeePortalLogin from "../authPages/login/employeePortalLogin";
import Form16 from "../mainPages/taxesAndForms/form16";
import GenerateForm16 from "../mainPages/taxesAndForms/form16/generateForm16";
import TaxCalculator from "../mainPages/taxesAndForms/taxCalculator";
import UserTaxCalculator from "../mainPages/userPortal/taxation/userTaxCalculator";
import UserPOI from "../mainPages/userPortal/taxation/userPOI";
import OverviewPOI from "../mainPages/userPortal/taxation/userPOIView";
import EditUserPOI from "../mainPages/userPortal/taxation/editUserPOI";
import UnsubmittedList from "../mainPages/approval/unsubmittedList";
import ProofOfInvestment from "../mainPages/approval/proofOfInvestment";
import PtDetails from "../mainPages/allSettingsPages/statutoryComponents/ptDetails";
import InvestmentDeclaration from "../mainPages/employee/investmentDeclaration";
import ITDeclaration from "../mainPages/allSettingsPages/claimsAndDeclaration/itDeclaration";
import ProofOfInvestmentSettings from "../mainPages/allSettingsPages/claimsAndDeclaration/proofOfInvestment";
import SalaryDetailsTab from "../mainPages/employee/SalaryDetailsTab";
import InvestmentsTab from "../mainPages/employee/InvestmentsTab";
import LoanTab from "../mainPages/employee/LoanTab";
import PayrollFormsTab from "../mainPages/employee/PayrollFormsTab";
import OverviewTab from "../mainPages/employee/OverviewTab";
import UserInvestmentDeclaration from "../mainPages/userPortal/userInvestment/userInvestmentDeclaration";
import UserInvestment from "../mainPages/userPortal/userInvestment";
import UserInvestmentProof from "../mainPages/userPortal/userInvestment/userInvestmentProofTab";
import CompareTaxRegimes from "../mainPages/userPortal/userInvestment/compareTaxRegimes";
import UserProofEdit from "../mainPages/userPortal/userInvestment/userProofEdit";
import ReimbursementPage from "../mainPages/userPortal/Reimbursement/ReimbursementPage";
import ApprovalView from "../mainPages/approval/approvalView";
import AdminInvestment from "../mainPages/employee/InvestmentsTab";
import AdminInvestmentDeclaration from "../mainPages/employee/adminInvestmentDeclaration";
import AdminInvestmentProofTab from "../mainPages/employee/adminInvestmentProofTab";
import InvestmentsAndProofsLayout from "../mainPages/employee/InvestmentsAndProofsLayout";
import AdminProofEdit from "../mainPages/employee/adminProofEdit";
import AdminCompareTaxRegimes from "../mainPages/employee/adminCompareTaxRegime";
import ViewPayslip from "../mainPages/payRuns/viewPayslip";
import EditReviseSalary from "../mainPages/employee/editReviseSalary";
import SalaryRevisionApproval from "../mainPages/approval/salaryRevisionApproval";
import ViewReviseSalary from "../mainPages/approval/viewReviseSalary";
import SalaryRevisionDetails from "../mainPages/employee/salaryRevisonDetails";
import EditSalaryRevision from "../mainPages/employee/editSalaryRevision";
import AcceptInvite from "../mainPages/acceptInvite";
import AdminReimbursementPage from "../mainPages/adminReimbursement/AdminReimbursementPage";
import MarkLeavesOverviewPage from "../mainPages/markLeaves/markLeavesOverviewPage";
import MarkLeavesTakenPage from "../mainPages/markLeaves/markLeavesTakenPage";
import MarkLeaveAddEmploy from "../mainPages/markLeaves/markLeaveAddEmploy";
import EmployeeDeductionList from "../mainPages/deduction/deduction";
import GridDeduction from "../mainPages/deduction/gridDeduction";
import MyDeductions from "../mainPages/userPortal/myDeductions/myDeductions";
import LeaveAllocation from "../mainPages/leaveManagement/leaveAllocation/leaveAllocation";
import MarkLeaveTaken from "../mainPages/leaveManagement/markLeaveTaken/markLeaveTaken";




const LoadingScreen = () => (
    <div className="fixed inset-0 bg-background/50 backdrop-blur-sm flex items-center justify-center">
        <div>Loading...</div>
    </div>
);

// Create a wrapper component that will handle theme loading
// const ThemedLayout = ({ children }) => {
//     // Don't use useState initially for the theme to avoid the derivative calculation error
//     const [themeConfig, setThemeConfig] = useState({
//         algorithm: [theme.defaultAlgorithm],
//         token: {
//             fontFamily: `Inter, Helvetica, "sans-serif"`,
//             fontSize: '1rem',
//             fontWeight: 400
//         }
//     });

//     useEffect(() => {
//         try {
//             const themeMode = localStorage.getItem("data-bs-theme");
//             setThemeConfig(prev => ({
//                 ...prev,
//                 algorithm: [themeMode !== 'light' ? theme.darkAlgorithm : theme.defaultAlgorithm]
//             }));
//         } catch (error) {
//             console.error("Error loading theme:", error);
//         }
//     }, []);

//     return (
//         <ConfigProvider theme={themeConfig}>
//             {children}
//         </ConfigProvider>
//     );
// };


// Create a wrapper component that will handle theme loading
const ThemedLayout = ({ children }) => {
    // Initialize theme based on localStorage, defaulting to light if null
    const getInitialTheme = () => {
        try {
            const themeMode = localStorage.getItem("data-bs-theme");
            // If null or not set, default to light theme
            if (themeMode === null || themeMode === undefined) {
                localStorage.setItem("data-bs-theme", "light");
                return theme.defaultAlgorithm;
            }
            // Otherwise use the stored preference
            return themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm;
        } catch (error) {
            console.error("Error loading theme:", error);
            return theme.defaultAlgorithm;
        }
    };

    const [themeConfig, setThemeConfig] = useState({
        algorithm: [getInitialTheme()],
        token: {
            fontFamily: `Inter, Helvetica, "sans-serif"`,
            fontSize: '1rem',
            fontWeight: 400
        }
    });

    useEffect(() => {
        try {
            const themeMode = localStorage.getItem("data-bs-theme");
            // console.log("themeMode", themeMode);

            // Update theme algorithm based on stored value
            const selectedAlgorithm = themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm;

            setThemeConfig(prev => ({
                ...prev,
                algorithm: [selectedAlgorithm]
            }));
        } catch (error) {
            console.error("Error loading theme:", error);
        }
    }, []);

    return (
        <ConfigProvider theme={themeConfig}>
            {children}
        </ConfigProvider>
    );
};


// Create a component for the Dashboard with theme
// const ThemedDashboardLayout = () => (
//     <ThemedLayout>
//         <DashboardLayout />
//     </ThemedLayout>
// );

const ThemedSidebarLayout = () => (
    <ThemedLayout>
        <SidebarLayout />
    </ThemedLayout>
);

const ThemedSettingsLayout = () => (
    <ThemedLayout>
        <SettingsLayout />
    </ThemedLayout>
);


const ThemedOrgLayout = () => (
    <ThemedLayout>
        <Outlet />  {/* renders child pages inside theme */}
    </ThemedLayout>
);






const ThemedEmployeeLayout = () => (
    <ThemedLayout>
        <EmployeeLayout />
    </ThemedLayout>
);


export const router = createBrowserRouter([
    {
        path: '/',
        element: <AuthGuard allowedRole="admin" />,
        errorElement: <ErrorPage />,
        // loader: rootLoader,
        children: [
            {
                path: '/',
                element: <OrganizationGuard />,
                children: [
                    {
                        path: '/',
                        element: <ThemedSidebarLayout />,
                        children: [


                            {
                                path: '/dashboard',
                                element: <DashboardPage />,
                            },
                            {
                                path: 'onboarding-dashboard',
                                element: <OnboardingDashboard />,
                            },
                            {
                                path: 'employees',
                                element: <Employee />,
                            },
                            {
                                path: 'employees/add',
                                element: <AddEmployee />
                            },
                            {
                                path: '/employees/add/basic-details',
                                element: <BasicDetails />
                            },
                            {
                                path: '/employees/add/salary-details',
                                element: <SalaryDetails />
                            },
                            {
                                path: '/employees/add/personal-details',
                                element: <PersonalDetails />
                            },
                            {
                                path: '/employees/add/payment-details',
                                element: <PaymentInformation />
                            },
                            // , {
                            //     path: "/employees/view/:id",
                            //     element: <ViewEmployee />,
                            //     children: [
                            //         { index: true, element: <OverviewTab /> },
                            //         { path: "salary-details", element: <SalaryDetailsTab /> },
                            //         { path: "investments-and-proofs", element: <AdminInvestment /> },
                            //         { path: "payroll-and-forms", element: <PayrollFormsTab /> },
                            //         { path: "loan", element: <LoanTab /> }
                            //     ]
                            // },

                            // In your router file (App.jsx or similar)
                            {
                                path: "/employees/view/:id",
                                element: <ViewEmployee />,
                                children: [
                                    { index: true, element: <OverviewTab /> },
                                    { path: "salary-details", element: <SalaryDetailsTab /> },
                                    {
                                        path: "investments-and-proofs",
                                        element: <InvestmentsAndProofsLayout />, // New layout component
                                        children: [
                                            {
                                                index: true,
                                                element: <AdminInvestment />
                                            },
                                            {
                                                path: "declaration",
                                                element: <AdminInvestmentDeclaration />
                                            },
                                            {
                                                path: "proof",
                                                element: <AdminInvestmentProofTab />
                                            },
                                            {
                                                path: "proof/proof-edit",
                                                element: <AdminProofEdit />
                                            },
                                            {
                                                path: "admin-tax-compare-regimes",
                                                element: <AdminCompareTaxRegimes />
                                            }
                                        ]
                                    },
                                    { path: "payroll-and-forms", element: <PayrollFormsTab /> },
                                    { path: "loan", element: <LoanTab /> }
                                ]
                            },


                            {
                                path: '/employees/edit-basic/:id',
                                element: <EditBasicDetails />
                            },

                            {
                                path: '/employees/edit-statutory/:id',
                                element: <EditStatutoryDetails />
                            },
                            {
                                path: '/employees/edit-personal/:id',
                                element: <EditPersonalDetails />
                            },
                            {
                                path: '/employees/edit-payment/:id',
                                element: <EditPaymentDetails />
                            },
                            {
                                path: '/employees/edit-salary/:id',
                                element: <EditSalaryDetails />
                            },
                            {
                                path: '/employees/edit-revise-salary/:id',
                                element: <EditReviseSalary />
                            },


                            {
                                path: '/employees/revision-salary-details/:id',
                                element: <SalaryRevisionDetails />
                            },
                            {
                                path: '/employees/edit-salary-revision/:id',
                                element: <EditSalaryRevision />
                            }


                            , {
                                path: 'importbasicdetails',
                                element: <ImportBasicDetails />
                            },
                            {
                                path: '/payruns',
                                element: <PayRuns />
                            },
                            {
                                path: '/admin-reimbursements',
                                element: <AdminReimbursementPage />
                            },
                            {
                                path: '/leave-allocation',
                                element: <LeaveAllocation />
                            },
                            {
                                path: '/mark-leaves',
                                element: <MarkLeavesOverviewPage />
                            },
                            {
                                path: '/mark-leave-taken',
                                element: <MarkLeavesTakenPage />
                            },
                            {
                                path: '/mark-leaves-taken',
                                element: <MarkLeavesTakenPage />
                            },
                            {
                                path: '/markleaveaddemploy',
                                element: <MarkLeaveAddEmploy />
                            },
                            {
                                path: '/mark-leave-add-employee',
                                element: <MarkLeaveAddEmploy />
                            },
                            {
                                path: '/addOneTimePayoutDetails',
                                element: <AddOneTimePayoutDetails />
                            },
                            {
                                path: '/addOneTimePayoutDetails/import',
                                element: <ImportOneTimePayrunData />
                            },
                            {
                                path: '/addOffCycleDetails',
                                element: <AddOffCycleDetails />
                            },
                            {
                                path: '/preview/:payrunId',
                                element: <Preview />
                            },
                            {
                                path: '/summary/:payrunId',
                                element: <Summary />
                            },
                            {
                                path: '/view-payslip/:payrunId/:employeeId',
                                element: <ViewPayslip />
                            },

                            {
                                path: '/form16s',
                                element: <Form16 />
                            },
                            {
                                path: '/form16s/generate',
                                element: <GenerateForm16 />
                            },
                            {
                                path: '/tax-calculator',
                                element: <TaxCalculator />
                            },


                            {
                                path: '/proof-of-investment',
                                element: <ProofOfInvestment />
                            },
                            {
                                path: "/proof-of-investment/approval-view/:id",
                                element: <ApprovalView />
                            },
                            {
                                path: '/proof-of-investment/unsubmitted-list',
                                element: <UnsubmittedList />
                            },
                            {
                                path: '/investment-declaration',
                                element: <InvestmentDeclaration />
                            },
                            {
                                path: '/salary-revision-approvals',
                                element: <SalaryRevisionApproval />
                            },
                            {
                                path: '/view-revise-salary/:id',
                                element: <ViewReviseSalary />
                            },
                            {
                                path: '/employee-deductions',
                                element: <EmployeeDeductionList />
                            },
                            {
                                path: '/employee-deductions/bulk-add',
                                element: <GridDeduction />
                            },


                        ],
                    },
                    {
                        path: "/",
                        element: <ThemedSettingsLayout />,
                        children: [

                            {
                                index: true,
                                path: 'organisation-profile',
                                element: <OrganisationProfile />
                            }
                            , {
                                index: true,
                                path: 'departments',
                                element: <Departments />
                            },
                            {
                                index: true,
                                path: 'departments/import',
                                element: <ImportDepartments />
                            },

                            ,
                            {
                                index: true,
                                path: 'designations',
                                element: <Designations />
                            },
                            {
                                index: true,
                                path: 'designations/import',
                                element: <ImportDesignations />
                            },

                            {
                                path: '/all-settings',
                                element: <AllSettingsComponents />,
                            },
                            {
                                path: 'work-locations',
                                element: <WorkLocations />,
                            },
                            {
                                path: "work-locations/edit/:workLocationId",
                                element: <EditWorkLocationForm />,
                            },

                            {
                                path: 'work-locations/new',
                                element: <AddWorkLocation />,
                            },
                            {
                                path: 'work-locations/import',
                                element: <ImportWorkLocations />,
                            },
                            {
                                path: 'tax-details',
                                element: <TaxDetails />,
                            },
                            {
                                path: "users",
                                element: <Users />,
                            },
                            {
                                path: "users/invite",
                                element: <InviteUser />,
                            },
                            {
                                path: "users/edit",
                                element: <EditUser />,
                            },
                            //REPLACE THE ABOVE ROUTE WITH THE BELOW ONE TO PASS ID
                            //                     {
                            //   path: "users/edit/:id",  // 👈 now it accepts an ID
                            //   element: <EditUser />,
                            // }

                            {
                                path: "taxes",
                                element: <TaxDetails />,
                            },
                            {
                                path: "roles",
                                element: <Roles />,
                            },
                            {
                                path: "roles/new",
                                element: <CreateNewRole />,
                            },
                            {
                                path: "roles/view",
                                element: <ViewRole />,
                            },
                            {
                                path: "roles/edit/:id",
                                element: <EditRole />,
                            }
                            ,

                            {
                                path: "pay-schedules",
                                element: <PaySchedules />,
                            },

                            {
                                path: "pay-schedules/view",
                                element: <PayScheduleView />,
                            },


                            // {
                            //     path: "pay-schedules/edit/:payScheduleId",
                            //     element: <EditPaySchedule />,
                            // },

                            {
                                path: "/pay-schedules/edit/:id",
                                element: <EditPaySchedule />
                            },


                            {
                                path: "statutory-components",
                                element: <StatutoryComponents />,
                            },


                            {
                                path: "statutory-components/epf",
                                element: <EPF />,
                            },
                            {
                                path: "statutory-components/epf/edit",
                                element: <EditEPF />,
                            },


                            {
                                path: "statutory-components/esi",
                                element: <ViewESI />,
                            },

                            {
                                path: "statutory-components/esi/form",
                                element: <FormESI />,
                            },

                            {
                                path: "statutory-components/esi/edit",
                                element: <EditESI />,
                            },



                            {
                                path: "statutory-components/professional-tax",
                                element: <ProfessionalTax />,
                            },
                            {
                                path: "statutory-components/professional-tax/pt-details/:taxId",
                                element: <PtDetails />,
                            },

                            {
                                path: "salary-components",
                                element: <SalaryComponents />,
                            },

                            {
                                path: "salary-components/deductions",
                                element: <Deduction />,
                            },


                            {
                                path: "salary-components/benefits",
                                element: <Benefits />,
                            },



                            {
                                path: "salary-components/reimbursements",
                                element: <Reimbursement />,
                            },

                            {
                                path: "salary-components/edit/:earningId",
                                element: <EditEarning />,
                            },

                            {
                                path: "salary-components/deduction/edit/:id",
                                element: <EditDeduction />,
                            },


                            {
                                path: "salary-components/benefits/edit/:key",
                                element: <EditBenefit />,
                            },


                            {
                                path: "salary-components/reimbursements/edit/:id",
                                element: <EditReimbursement />,
                            },


                            {
                                path: "/salary-components/add/earning",
                                element: <AddNewEarning />,
                            },

                            {
                                path: "salary-components/add/correction",
                                element: <AddNewCorrection />,
                            },


                            {
                                path: "salary-components/add/benefits",
                                element: <AddNewBenefits />,
                            },


                            {
                                path: "salary-components/add/deduction",
                                element: <AddNewDeduction />,
                            },


                            {
                                path: "salary-components/add/reimbursement",
                                element: <AddNewReimbursement />,
                            },


                            {
                                path: "salary-components/add/custom-earning",
                                element: <AddNewCustomEarning />,
                            },


                            {
                                path: "employee-portal/preferences",
                                element: <Preferences />,
                            },
                            {
                                path: "/leave-attendance-setup",
                                element: <LeaveAttendanceSetup />
                            },
                            {
                                path: "/leave-types",
                                element: <LeaveTypes />
                            },
                            {
                                path: "/leave-types/add",
                                element: <AddLeaveType />
                            },
                            {
                                path: "/leave-types/edit/:id",
                                element: <EditLeaveType />

                            },
                            {
                                path: "/holidays",
                                element: <Holidays />
                            },
                            {
                                path: "/holidays/add",
                                element: <AddHoliday />
                            },
                            {
                                path: "/attendance",
                                element: <Attendance />
                            },
                            {
                                path: "/attendence-preferences",
                                element: <AttendancePreferences />
                            },
                            {
                                path: "/leave-balance/import",
                                element: <ImportLeaveBalance />
                            },
                            {

                                path: "/it-declaration",

                                element: <ITDeclaration />

                            },
                            {
                                path: "/proof-of-investment",
                                element: <ProofOfInvestmentSettings />
                            }





                        ]
                    },
                ]
            },

        ],
    },



    {
        element: <ThemedLayout><AuthLayout /></ThemedLayout>,
        errorElement: <ErrorPage />,
        children: [
            {
                path: "/accept-invite",
                element: <AcceptInvite />,
            }
        ]
    },

    {
        element: <LoginGuard />,
        errorElement: <ErrorPage />,
        children: [
            {
                element: <ThemedLayout><AuthLayout /></ThemedLayout>,
                children: [
                    {
                        path: "/login",
                        element: <Login />,
                    },
                    // {
                    //     path: "/register",
                    //     element: <Register />,
                    // },
                    {
                        path: "/forgot-password",
                        element: <ForgotPasswordPage />,
                    },
                    {
                        path: "/new-password",
                        element: <NewPassword />,
                    },


                    {
                        path: "/create-new-account",
                        element: <CreateAccountPage />,
                    },

                    // {
                    //     path: "/create-new-organization",
                    //     element: <OrganizationRegister />
                    //     ,
                    // },
                    // {
                    //     path:"/setup-new-organization/:organizationId",
                    //     element:<SetupNewOrganization/>,
                    // }





                ]
            },

        ],
    },

    {
        path: '/',
        element: <AuthGuard allowedRole="admin" />,
        children: [
            {
                path: '/',
                element: <ThemedOrgLayout />,
                children: [
                    { path: '/create-new-organization', element: <OrganizationRegister /> },
                    { path: '/manage-organization', element: <ManageOrganization /> },
                    {
                        path: "/setup-new-organization/:organizationId",
                        element: <SetupNewOrganization />,
                    }
                ]
            }
        ]
    }
    ,


    {
        path: '/',
        element: <AuthGuard allowedRole="employee" />,
        children: [
            {
                path: '/',
                element: <ThemedEmployeeLayout />,
                children: [
                    { path: "/home", element: <Home /> },
                    { path: "/userProfile", element: <MyProfile /> },
                    { path: "/user-salary-details", element: <MySalaryDetails /> },
                    // {path: "personal-details", element: <MyPersonalDetails />},
                    // {path: "payment-information", element: <MyPaymentInformation />},
                    // {path: "documents", element: <MyDocuments />},

                    { path: "/payslips/:payrunId", element: <PayslipGenerator /> },
                    {
                        path: '/user-tax-calculator', element: <UserTaxCalculator />
                    },
                    {
                        path: '/user-poi', element: <UserPOI />

                    },
                    {
                        path: 'overview-poi', element: <OverviewPOI />
                    },
                    {
                        path: "/edit-user-poi", element: <EditUserPOI />

                    },
                    {
                        path: '/user-investment', element: <UserInvestment />
                    },
                    {
                        path: '/user-investment-declaration', element: <UserInvestmentDeclaration />
                    },
                    {
                        path: '/user-investment/proof', element: <UserInvestmentProof />
                    },
                    {
                        path: '/compare-tax-regimes', element: <CompareTaxRegimes />
                    },
                    {
                        path: '/user-proof-edit', element: <UserProofEdit />
                    },
                    {
                        path: '/change-password', element: <ChangePassword />
                    },
                    {
                        path: '/reimbursement', element: <ReimbursementPage />
                    },
                    {
                        path: '/my-deductions', element: <MyDeductions />
                    }
                ],
            },
        ],
    },



    {
        element: <LoginGuard />,
        errorElement: <ErrorPage />,
        children: [
            {
                element: <ThemedLayout><AuthLayout /></ThemedLayout>,
                children: [
                    {
                        path: "/login",
                        element: <Login />,
                    },

                    {
                        path: "/employeePortalLogin",
                        element: <EmployeePortalLogin />,
                    },

                    {
                        path: "/forgot-password",
                        element: <ForgotPasswordPage />,
                    },
                    {
                        path: "/new-password",
                        element: <NewPassword />,
                    },


                    {
                        path: "/create-new-account",
                        element: <CreateAccountPage />,
                    },












                ]
            }
        ]
    },
    {
        path: '/public/payslips/:payrunId/:employeeId',
        element: <ThemedLayout><PublicPayslipDownload /></ThemedLayout>
    }
]);