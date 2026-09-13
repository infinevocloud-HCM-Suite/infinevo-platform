# ARCHITECTURE — Infinevo Cloud HRMS + Payroll Suite

> Last updated: 2026-09-11

---

## 1. System Overview

The platform consists of **two independent Spring Boot microservices** and **two separate React frontends**, all backed by MySQL databases hosted on DigitalOcean. The services are designed to operate independently but share data through a secured REST API integration channel.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Infinevo Cloud Platform                        │
│                                                                         │
│  ┌───────────────────┐   REST (X-API-KEY)   ┌────────────────────────┐ │
│  │   HRMS Backend    │ ◄──────────────────► │  Payroll Backend       │ │
│  │  Spring Boot 3.2  │                       │  Spring Boot 3.2       │ │
│  │  Port: 1010       │                       │  Port: 3032 (dev)      │ │
│  │  DB: hrmstestdb   │                       │  DB: payrollDB         │ │
│  │  Auth: JWT        │                       │  Auth: Keycloak        │ │
│  └────────▲──────────┘                       └──────────▲─────────────┘ │
│           │                                             │               │
│  ┌────────┴──────────┐                       ┌──────────┴─────────────┐ │
│  │   HRMS Frontend   │                       │  Payroll Frontend      │ │
│  │  React + Vite     │                       │  React (CRA)           │ │
│  │  MUI + Bootstrap  │                       │  Ant Design + Redux    │ │
│  │  JWT auth         │                       │  Keycloak auth         │ │
│  └───────────────────┘                       └────────────────────────┘ │
│                                                                         │
│  ┌─────────────────────────┐   ┌──────────────────────────────────┐    │
│  │ MySQL (DigitalOcean)    │   │ External Services                │    │
│  │ 64.227.128.119:3306     │   │ - Cloudinary (media storage)     │    │
│  │ hrmstestdb              │   │ - Gmail SMTP / Brevo (email)     │    │
│  │ 157.245.96.121:3306     │   │ - Keycloak (payroll auth)        │    │
│  │ payrollDB               │   │   authentication.infinevocloud   │    │
│  └─────────────────────────┘   └──────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Application Ports & Profiles

| Service | Dev Port | Prod Port | Profile |
|---|---|---|---|
| HRMS Backend | `1010` | `1010` | (single profile) |
| Payroll Backend | `3032` | `3029` | `dev` / `prod` |
| HRMS Frontend | `5173` (Vite default) | — | — |
| Payroll Frontend | `3000` | — | — |

---

## 3. HRMS Backend Architecture

### Package Structure
```
com.phegondev.usersmanagementsystem/
├── bootstrap/          # Application startup data seeders
├── config/             # Security config, JWT filter, CORS, HashUtil, Cloudinary
├── controller/         # REST controllers (organized by domain)
│   ├── notificationconfig/
│   ├── timesheet/
│   └── useraccess/
├── dto/                # Data Transfer Objects (request/response bodies)
├── entity/             # JPA entities (organized by domain)
│   ├── notificationconfig/
│   ├── timesheet/
│   └── useraccess/
├── enumuration/        # Enums: LeaveRequestStatus, TimesheetStatus, etc.
├── exception/          # Custom exception classes
├── exceptions/         # Additional exception handlers
├── migration/          # One-time data migration classes (UserRoleMigration)
├── repository/         # Spring Data JPA repositories
│   ├── leaverequest/
│   └── timesheet/
├── scheduler/          # Spring @Scheduled tasks (notification reminders)
├── service/            # Service interfaces
│   ├── leaverequest/
│   ├── notificationconfig/
│   ├── schedular/
│   ├── timesheet/
│   └── useraccess/
└── serviceimpl/        # Service implementations
    ├── leaverequest/
    ├── notificationconfig/
    ├── schedular/
    ├── timeshhet/      # ⚠️ Typo in folder name ("timeshhet")
    └── useraccess/
```

### Request Flow
```
HTTP Request
    │
    ▼
SecurityConfig / JWTAuthFilter (validates JWT, sets SecurityContext)
    │
    ▼
REST Controller (validates input, maps to DTO)
    │
    ▼
Service Interface
    │
    ▼
ServiceImpl (business logic, calls repositories)
    │
    ▼
JPA Repository → MySQL (hrmstestdb)
```

### Security Model
- **JWT**: Custom `JWTAuthFilter` extracts token from `Authorization: Bearer` header.
- **RBAC**: Each route is guarded by action checks (e.g., `MANAGE_EMPLOYEES`). `UserActionMapping` entity links users to allowed action names.
- **Cross-service**: `IntegrateWithPayroll` controller on `/public/**` is excluded from JWT auth but requires MD5-hashed API key in `X-API-KEY` header.

---

## 4. Payroll Backend Architecture

### Package Structure
```
com.itsdev.payroll/
├── config/             # Security (Keycloak), WebClient, Cloudinary, Keycloak Admin
├── controller/         # REST controllers (organized by domain)
│   ├── claimsanddeclarations/
│   ├── dashboard/
│   ├── employee/
│   │   └── preview/
│   ├── leaveAndAttendance/
│   │   ├── attendance/
│   │   ├── holiday/
│   │   ├── leaveImport/
│   │   ├── onboarding/
│   │   └── preferences/
│   ├── organization/
│   ├── payRun/
│   │   ├── offCyclePayrun/
│   │   └── oneTimePayout/
│   ├── payruns/
│   ├── salarycomponents/
│   ├── statutorycomponents/
│   ├── taxCalculator/
│   ├── employeeTDS/
│   ├── employeeitdeclaration/
│   ├── employeereimbursement/
│   ├── leave/
│   ├── publicapi/
│   └── test/               # dev-only endpoints, currently unguarded
├── dto/                # DTOs per domain
├── entity/             # JPA entities (organized by domain)
│   ├── auth/
│   ├── claimsanddeclarations/
│   ├── employee/
│   ├── leaveAndAttedance/  # ⚠️ Typo: "Attedance" (missing 'n')
│   ├── organization/
│   ├── payRun/
│   ├── payruns/
│   ├── salarycomponents/
│   ├── statutorycomponents/
│   ├── taxCalculator/
│   ├── EmployeeITDeclaration/
│   ├── employeeTDS/
│   ├── employeereimbursement/
│   └── leave/
├── enumeration/        # Enums for pay run status, etc.
├── exception/          # Custom exceptions
├── mapper/             # Entity ↔ DTO mappers
├── repository/         # Spring Data JPA repositories
├── service/            # Service interfaces (mirrored from controller domain hierarchy)
├── serviceimpl/        # Service implementations
├── specs/              # JPA Specifications for dynamic queries
└── util/               # HashUtil, CSV helpers, etc.
```

### Request Flow
```
HTTP Request
    │
    ▼
SecurityConfig (validates Keycloak JWT via OAuth2 Resource Server)
    │
    ▼
REST Controller
    │
    ▼
Service Interface
    │
    ▼
ServiceImpl (business logic)
    │   ├── JPA Repository → MySQL (payrollDB)
    │   ├── WebClient → HRMS API (for leave data during pay run)
    │   └── Keycloak Admin Client (for user management)
    │
    ▼
Response DTO
```

### Security Model
- **Keycloak**: OAuth2 Resource Server validates JWTs issued by `https://authentication.infinevocloud.com/realms/HRMS`.
- **RBAC**: Organization-level roles mapped to actions via `OrganizationRoleAction` entity.
- **Cross-service**: WebClient configured with `X-API-KEY` header (MD5 hash of `api.key`).

---

## 5. Cross-Service Data Flow (HRMS ↔ Payroll Integration)

```
Payroll Service                              HRMS Service
─────────────────                            ─────────────────
EmployeePayRunServiceImpl
  .generateEmployeePayRuns()
        │
        ├── Collect employee emails from BasicDetails
        │
        ▼
IntegrateWithHrmsServiceImpl
  .fetchLeaves(emails, "July 2025")
        │
        ├── POST https://myhrms.infinevocloud.com/public/get-employee-leaves
        │   Headers: X-API-KEY: md5("12345AB")
        │   Body: { employeeEmails: [...], payPeriod: "July 2025" }
        │
        │                                    IntegrateWithPayroll controller
        │                                      1. Validate X-API-KEY
        │                                      2. Parse pay period → YearMonth
        │                                      3. For each email:
        │                                         - Find OurUsers by email → empId
        │                                         - Query EmployeeMonthlyLop
        │                                           (year, month, empId)
        │                                      4. Return totalLopDays per employee
        ◄────── Response: Map<String, Double> (email -> lopDays) ──────
        │
        ├── Per employee: deductionAmt = (monthlySalary / paidDays) * lopDays
        └── Set EmployeePayRun.netPay = originalNetPay - deductionAmt
```

---

## 6. HRMS Frontend Architecture

### Tech Stack
- **Build**: Vite + React 18 (JSX)
- **UI**: MUI v6, Bootstrap 5, Bootstrap Icons
- **Charts**: Chart.js, Recharts, MUI X Charts
- **HTTP**: Axios with JWT interceptor (`axiosInterceptor.js`)
- **Auth**: Context-based (`ContextProvider.jsx`) with role/action awareness

### Component Structure
```
src/
├── main.jsx               # App bootstrap, renders <App/>
├── App.jsx                # Router + role-based dashboard routing
├── axiosInterceptor.js    # Attaches JWT to all Axios requests
├── pages/
│   └── ChooseRole.jsx     # Multi-role selection screen
└── components/
    ├── auth/              # Login, Registration
    ├── context/           # Auth context, route guards
    ├── Sidebar/           # Navigation sidebar
    ├── adminDashboard/    # Admin-only screens
    ├── hrDashboard/       # HR screens
    ├── managerDashboard/  # Manager screens
    ├── supervisorDashboard/
    ├── reportingmanagerDashboard/
    ├── EmployeeDashboard/ # Employee self-service
    ├── employee/          # Employee CRUD
    ├── Leaves/            # Leave management
    ├── Projects/          # Project/task management
    ├── userManagement/    # RBAC management
    ├── service/           # API call functions
    └── config/            # baseURL config
```

---

## 7. Payroll Frontend Architecture

### Tech Stack
- **Build**: Create React App (react-scripts 5)
- **UI**: Ant Design 5, Bootstrap 5, ECharts
- **State**: Redux Toolkit
- **Auth**: Keycloak (`@react-keycloak/web`, `keycloak-js`)
- **HTTP**: Axios with Keycloak token interceptor

### Component Structure
```
src/
├── index.js               # App bootstrap, Keycloak init, Redux Provider
├── App.js                 # Root component
├── pages/
│   ├── authPages/         # Login, setup org, forgot/new password
│   ├── mainPages/         # All main app pages
│   │   ├── dashboardPage/
│   │   │   └── index.js   # 42KB / 884 lines - the live dashboard
│   │   └── dashboardcopy.js # 271KB / 2,738 lines - DEAD, imported nowhere
│   │   └── allSettingsPages/ # Settings screens
│   └── pageLayouts/       # Layouts (sidebar, settings, auth) + router
└── shared/
    ├── appConfig/         # API URL config
    ├── components/        # Shared UI (loaders, no-data, modals)
    ├── guards/            # Auth + login route guards
    ├── helpers/           # Axios interceptor, root loader, msg helper
    ├── redux/             # Redux store and slices
    └── services/          # authService, invitationService, reimbursementService, leaveStore
```

---

## 8. Directory Roles Summary

| Directory | Role |
|---|---|
| `HRMS_Backend/` | Core HRMS REST API. Manages employees, timesheets, leaves, attendance, notifications, user access. |
| `HRMS_Frontend/` | React (Vite) timesheet microsite. Primarily serves the timesheet-related employee and manager flows, alongside the full HRMS admin panel. |
| `Payroll-Bend-SBoot/` | Payroll REST API. Manages salary computation, pay runs, statutory compliance, tax declarations, and organization setup. |
| `Payroll-Fend-react/` | React (CRA) payroll UI. Handles payroll setup, employee onboarding, pay run processing, and settings management. |
| `docs/` | ARCHITECTURE.md, DB_SCHEMA.md, FEATURE_MAP.md, GAP_INVENTORY.md, CONVENTIONS.md, `features/`, `_archive/`. |

---

## 9. What Infinevo Cloud Is

> Merged from `PROJECT_OVERVIEW.md` on 2026-09-11. That file is now in `_archive/`.

Infinevo Cloud is an enterprise HR and Payroll platform built as **two independently
deployable microservices with a frontend each**. It covers the employee lifecycle from
onboarding and attendance through to payroll processing and statutory tax compliance.

The two services are deliberately decoupled. Historically their only runtime link was a
single secured REST call: Payroll pulled Loss-of-Pay (LOP) leave data from HRMS at pay-run
time. Since the Leave Management module landed (2026-08-28 → 2026-09-09), Payroll derives
LOP from its own `employee_leave_balance_consumption` table. See §5 and `GAP_INVENTORY.md`.

### Sub-project summary

| Project | Stack | Auth | Database | Port | Scale |
|---|---|---|---|---|---|
| `HRMS_Backend` | Java 21 · Spring Boot 3.2.4 | JWT (`jjwt` 0.12.5) | MySQL `hrmstestdb` | 1010 | 219 files · ~23k LOC · 25 controllers · 39 tables |
| `HRMS_Frontend` | React 18 · Vite · MUI v6 | JWT via React Context | — | 5173 | 75 files · ~42k LOC |
| `Payroll-Bend-SBoot` | Java 17 · Spring Boot 3.2.5 | Keycloak (OAuth2 RS) | MySQL `payrollDB` | 3032 dev / 3029 prod | 597 files · ~82k LOC · 61 controllers · 95 tables |
| `Payroll-Fend-react` | React 18 · CRA · Ant Design 5 · Redux | Keycloak SSO | — | 3000 | 216 files · ~116k LOC · 158 routes |

### Key technology decisions

| Decision | Rationale |
|---|---|
| JWT in HRMS, Keycloak in Payroll | HRMS was built first with custom JWT. Payroll came later and adopted enterprise SSO. Unifying these is a prerequisite for single-tenant-to-multi-tenant work — see `GAP_INVENTORY.md` BUG-001 |
| Vite (HRMS FE) vs CRA (Payroll FE) | HRMS FE adopted the modern toolchain; Payroll FE remains on `react-scripts` 5, which is no longer maintained |
| MUI (HRMS FE) vs Ant Design (Payroll FE) | Two different design systems. Nothing is shared between the frontends beyond React itself |
| WebClient for the cross-service call | Non-blocking, so the pay-run thread is not held during the HRMS call |
| `ddl-auto=update` in both services | Schema is managed by Hibernate with no migration tool. Simple in development, unsafe in production — see DEBT-002 |
| MD5 API key for cross-service auth | Shared secret `12345AB`, MD5-hashed before transmission. Committed in plaintext |
| Cloudinary for media | Both services store documents, proofs and logos in Cloudinary, under separate accounts |

### Table naming

The two services resolve entity names to table names **differently**. This is a frequent
source of confusion when reading `DB_SCHEMA.md` or writing queries.

| Service | Physical naming strategy | Effect |
|---|---|---|
| Payroll | `PhysicalNamingStrategyStandardImpl` (explicit) | No snake_case conversion. Class name used verbatim when `@Table` is absent — `FBP`, `IncomeTaxDeclaration`, `ProofOfInvestment` |
| HRMS | *(unset — Spring Boot default)* | CamelCase converted to snake_case — `EmployeeDocument` → `employee_document` |

### Where to look next

| Question | Document |
|---|---|
| What tables exist, and what columns? | `DB_SCHEMA.md` — all 131 tables |
| Which files implement a feature? | `FEATURE_MAP.md` — all four applications |
| What is known to be broken or owed? | `GAP_INVENTORY.md` — BUG-001–007, DEBT-001–014 |
| What rules must code follow? | `CONVENTIONS.md` |
| What is being worked on right now? | `agents/active-work.md` |
