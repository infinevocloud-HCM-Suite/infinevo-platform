# Infinevo Cloud — High-Level Project Overview

> Last updated: 2026-08-12
> Platform: HRMS + Payroll Suite (Dual Microservice)

---

## What Is Infinevo Cloud?

**Infinevo Cloud** is an enterprise HR and Payroll management platform built as two independently deployable microservices. It serves organizations that need to manage the full employee lifecycle — from onboarding and attendance to payroll processing and tax compliance — through a unified but decoupled system.

The two services talk to each other through a single secured REST integration point: Payroll pulls **Loss-of-Pay (LOP) leave data** from HRMS every time a pay run is processed, ensuring salary deductions are accurate.

---

## Platform at a Glance

```
┌─────────────────────────────────────────────────────────────┐
│                    Infinevo Cloud Platform                  │
│                                                             │
│     HRMS Suite                    Payroll Suite             │
│   ─────────────              ─────────────────              │
│   Employee Mgmt               Org Setup                     │
│   Attendance                  Salary Components             │
│   Leaves & LOP        ◄───►  Pay Run Processing             │
│   Timesheets                  Statutory Compliance          │
│   Projects/Tasks              Tax Declarations              │
│   Notifications               Claims & Reimbursements       │
│                                                             │
│   Auth: JWT                   Auth: Keycloak                │
│   DB: hrmstestdb              DB: payrollDB                 │
│   Port: 1010                  Port: 3032 (dev)              │
└─────────────────────────────────────────────────────────────┘
```

---

## Four Sub-Projects — Brief Overview

---

### 1. 🟦 HRMS Backend (`HRMS_Backend/`)
**Language / Framework:** Java 21 · Spring Boot 3.2.4  
**Auth:** Spring Security + JWT (jjwt 0.12.5)  
**Database:** MySQL (`hrmstestdb`)  
**Port:** `1010`  
**Entry point:** `UsersmanagementsystemApplication.java`

**What it does:**  
The HRMS backend is the core of the HR system. It owns all employee data and HR processes — authentication, employee profiles, timesheets, attendance, leaves, overtime, projects, and automated notifications. It exposes a public API endpoint (`/public/get-employee-leaves`) consumed by the Payroll service to retrieve LOP data during pay run processing.

**Package structure:**
| Package | Responsibility |
|---|---|
| `config/` | Security (JWT filter), CORS, Cloudinary, HashUtil |
| `controller/` | REST API endpoints — organized by domain (timesheet, useraccess, notificationconfig) |
| `entity/` | JPA database entities |
| `dto/` | Request/response data transfer objects |
| `service/` + `serviceimpl/` | Business logic layer (Controller → Service → ServiceImpl → Repository) |
| `repository/` | Spring Data JPA repositories |
| `scheduler/` | Spring `@Scheduled` notification reminder jobs |
| `migration/` | One-time data seeding utilities |
| `bootstrap/` | App startup data initializers |
| `enumuration/` | Enums: `LeaveRequestStatus`, `TimesheetStatus` |
| `exception/` | Custom exception types |

**Key dependencies:**  
`spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `jjwt 0.12.5`, `cloudinary-http44`, `spring-boot-starter-mail`, `lombok`, `mysql-connector-j`

---

### 2. 🟩 HRMS Frontend (`HRMS_Frontend/`)
**Framework:** React 18 + Vite  
**UI Library:** MUI v6 + Bootstrap 5  
**Charts:** Chart.js, Recharts, MUI X Charts  
**Auth:** Context-based JWT (no Redux)  
**Entry point:** `src/main.jsx` → `App.jsx`

**What it does:**  
The HRMS frontend serves all HR workflows through a role-aware SPA. The app dynamically renders a different dashboard based on the user's active role (Admin, HR, Manager, Supervisor, Reporting Manager, Employee). Every route is protected at both the role level and the individual action-permission level via `ActionProtectedRoute`.

**Component structure:**
| Folder | Responsibility |
|---|---|
| `components/auth/` | Login, Registration forms |
| `components/context/` | Global auth context, role state, route guards (`ProtectedRoute`, `ActionProtectedRoute`) |
| `components/Sidebar/` | Navigation sidebar (role-aware menu) |
| `components/adminDashboard/` | Admin screens: leave mgmt, timesheets, holidays, employees, overtime |
| `components/hrDashboard/` | HR-specific dashboard |
| `components/managerDashboard/` | Manager dashboard + team timesheet approval |
| `components/supervisorDashboard/` | Supervisor dashboard |
| `components/reportingmanagerDashboard/` | Reporting manager dashboard + team view |
| `components/EmployeeDashboard/` | Employee self-service: timesheets, attendance, my projects |
| `components/employee/` | Employee CRUD (list, add, edit, view detail) |
| `components/Leaves/` | Leave application, balance, holiday calendar, overtime form |
| `components/Projects/` | Project and task management |
| `components/userManagement/` | RBAC management — users, roles, actions, mappings |
| `components/service/` | Axios API call functions |
| `components/config/` | Base API URL configuration |
| `pages/ChooseRole.jsx` | Multi-role selector (shown when user has > 1 role) |
| `axiosInterceptor.js` | Attaches JWT token to all outgoing Axios requests |

**Key dependencies:**  
`react-router-dom v7`, `@mui/material v6`, `axios`, `chart.js`, `recharts`, `jspdf`, `framer-motion`, `bootstrap`

---

### 3. 🟧 Payroll Backend (`Payroll-Bend-SBoot/`)
**Language / Framework:** Java 17 · Spring Boot 3.2.5  
**Auth:** Spring Security + Keycloak (OAuth2 Resource Server)  
**Database:** MySQL (`payrollDB`)  
**Ports:** `3032` (dev) · `3029` (prod)  
**Entry point:** `PayrollApplication.java`

**What it does:**  
The Payroll backend handles everything related to salary computation and compliance. It manages the full payroll cycle — from configuring an organization's salary components and statutory settings (EPF, ESI, Professional Tax), to running monthly pay runs with LOP-adjusted net pay calculations. It uses Keycloak for user authentication and the Keycloak Admin API to create users on invitation.

**Package structure:**
| Package | Responsibility |
|---|---|
| `config/` | Keycloak security, WebClient config (HRMS integration), Cloudinary, Keycloak Admin Client |
| `controller/` | REST endpoints — deeply nested by domain (employee, organization, payRun, salarycomponents, etc.) |
| `entity/` | JPA entities — organized by domain (`employee/`, `payruns/`, `organization/`, `salarycomponents/`, `statutorycomponents/`, `taxCalculator/`, `claimsanddeclarations/`) |
| `dto/` | DTOs per domain |
| `service/` + `serviceimpl/` | Business logic — mirrors controller hierarchy |
| `repository/` | Spring Data JPA repositories |
| `specs/` | JPA Specification classes for dynamic filtering |
| `mapper/` | Entity ↔ DTO mappers |
| `util/` | HashUtil (MD5), CSV helpers |
| `enumeration/` | Payroll-specific enums |

**Key dependencies:**  
`spring-boot-starter-oauth2-resource-server`, `keycloak-admin-client 26.0.6`, `spring-boot-starter-webflux` (WebClient), `cloudinary-http45`, `hibernate-types-60`, `commons-csv`, `spring-boot-starter-mail`

**Cross-service integration:**  
At pay run time, `EmployeePayRunServiceImpl` calls `IntegrateWithHrmsServiceImpl.fetchLeaves()` → WebClient POST to `HRMS /public/get-employee-leaves` with MD5-hashed API key. Response LOP days are used to compute final net pay deduction.

---

### 4. 🟥 Payroll Frontend (`Payroll-Fend-react/`)
**Framework:** React 18 (Create React App)  
**UI Library:** Ant Design 5 + Bootstrap 5  
**State:** Redux Toolkit  
**Charts:** ECharts  
**Auth:** Keycloak (`@react-keycloak/web`, `keycloak-js`)  
**Entry point:** `src/index.js` → `App.js` → `src/pages/pageLayouts/router.js`

**What it does:**  
The Payroll frontend handles the full payroll management experience — from organization setup to employee onboarding, salary configuration, pay run processing, and tax declarations. It uses Keycloak for SSO authentication. Route guards (`authGuard`, `loginGuard`) enforce auth at the page level.

**Structure:**
| Folder | Responsibility |
|---|---|
| `pages/authPages/login/` | Login, forgot password, create account screens |
| `pages/authPages/setupOrganization/` | Organization onboarding wizard |
| `pages/mainPages/dashboardPage/` | Main payroll dashboard (⚠️ `index.js` is 271KB — most UI lives here) |
| `pages/mainPages/allSettingsPages/` | Settings: org profile, work locations, designations |
| `pages/mainPages/organizationPage/` | Organization detail page |
| `pages/mainPages/userProfileSettings/` | User account and security tabs |
| `pages/pageLayouts/router.js` | All route definitions (React Router v6) |
| `pages/pageLayouts/sidebarLayout/` | Main app layout: header + sidebar + footer |
| `pages/pageLayouts/settingsLayout/` | Settings-specific layout |
| `shared/guards/` | `authGuard.js`, `loginGuard.js` — Keycloak-based guards |
| `shared/helpers/axiosInterceptor.js` | Injects Keycloak access token into Axios requests |
| `shared/helpers/rootLoader.js` | Fetches org/user bootstrap data on route load |
| `shared/redux/` | Redux Toolkit store + slices |
| `shared/components/` | Reusable UI: loaders, no-data states, modals |

**Key dependencies:**  
`antd v5`, `@react-keycloak/web`, `@reduxjs/toolkit`, `keycloak-js`, `react-router-dom v6`, `echarts-for-react`, `axios`, `formik`, `yup`, `sweetalert2`

---

## Cross-Service Integration Summary

```
HRMS Backend ──────────────────────────────── Payroll Backend
  Exposes:                                      Consumes:
  POST /public/get-employee-leaves    ◄────     IntegrateWithHrmsServiceImpl
  - Auth: X-API-KEY: md5("12345AB")            .fetchLeaves(emails, payPeriod)
  - Input: [emails], payPeriod                  called from:
  - Output: [{email, totalLopDays}]             EmployeePayRunServiceImpl
                                                .saveEmployeePayRun()

  LOP Deduction Formula (Payroll):
  deduction = (monthlySalary / paidDays) × lopDays
  netPay    = originalNetPay - deduction
```

---

## Key Technology Decisions

| Decision | Rationale |
|---|---|
| JWT in HRMS vs. Keycloak in Payroll | HRMS was built first with custom JWT. Payroll was built later leveraging enterprise SSO via Keycloak. |
| Vite (HRMS FE) vs. CRA (Payroll FE) | HRMS FE adopted modern Vite toolchain; Payroll FE uses older CRA (create-react-app). |
| MUI (HRMS FE) vs. Ant Design (Payroll FE) | Different design systems per frontend — not unified. |
| WebFlux / WebClient for cross-service call | Non-blocking HTTP client for Payroll → HRMS integration; avoids thread blocking during pay run. |
| `ddl-auto=update` (both services) | Schema managed by Hibernate — no migration tool. Simple but risky for production. |
| MD5 API Key for cross-service auth | Simple shared-secret approach. Plain key `12345AB` is MD5-hashed before transmission. |
| Cloudinary for media | Both services use Cloudinary for document/image storage (separate accounts). |

---

## Notable Code Health Observations

| Area | Observation |
|---|---|
| `dashboardPage/index.js` | 271KB single file — the entire Payroll module UI needs decomposition |
| Dual Timesheet entities | `Timesheet.*` and `Timesheets.*` coexist — old implementation not removed |
| Dual Leave Request entities | `LeaveRequest` and `LeaveRequests` coexist — same issue |
| LOP type mismatch | Payroll reads `Integer` for LOP days; HRMS sends `double` — fractional days truncated |
| Typos in package names | `timeshhet/` (HRMS), `leaveAndAttedance/` (Payroll), `EmployyePortalContoller.java` |
| No test coverage | Only one empty Spring test class found across all four projects |
| Credentials in properties | DB passwords, API keys, Keycloak secrets in `application.properties` — dev-only safe |
