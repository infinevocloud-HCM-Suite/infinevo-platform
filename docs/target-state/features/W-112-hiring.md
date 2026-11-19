# Feature: Hiring & Recruitment Management

| Field | Value |
|---|---|
| **Feature ID** | `W-112` · `HRMS-HIRING` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/hiring`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-112`; legacy HRMS had no recruitment module; employee onboarding required manual re-entry |
| **Status** | Ready |
| **Blocked by** | nothing (`core.employee`, `core.org`, and `core.user` exist) |

---

## 1. What and why

Today in `infinevo-platform`, the human resource management lifecycle starts only *after* an employee is hired. When a team needs to recruit:
1. **Disconnected Tracking:** Job openings, candidate resumes, and interview feedback are tracked informally via spreadsheets, email threads, and messaging apps.
2. **Missing Visibility:** Department heads and HR leaders cannot see how many positions are open, how many candidates are in each stage of the pipeline, or where candidate bottlenecks occur.
3. **Subjective Feedback:** Interviewers lack a structured scorecard interface to record ratings and hiring recommendations.
4. **Manual Re-entry Friction:** When an offer is accepted, HR administrators must manually re-type the candidate's personal information, contact details, work location, department, designation, and compensation into `core.employee` to create the employee record.

This feature delivers the **Hiring & Recruitment Management** module inside `hrms`:
1. **Job Openings & Requisitions:** Create, manage, and track job openings by department, designation, work location, and required headcount.
2. **Candidate Pipeline & Stage Tracking:** Track candidate applications across standard hiring stages (`APPLIED`, `SCREENING`, `INTERVIEWING`, `OFFERED`, `HIRED`, `REJECTED`).
3. **Structured Interviews & Scorecards:** Schedule interview rounds, assign internal interviewers, and collect standardized feedback scorecards with ratings and recommendations.
4. **Job Offers & One-Click Employee Conversion:** Issue job offers with CTC details and, upon acceptance, convert the hired candidate directly into a new `core.employee` record without re-typing.

---

## 2. Size Cap & Progressive Phasing

| Axis | Whole Module | Sub-Phase 1 (`W-112.1`) | Limit |
|---|:---:|:---:|:---:|
| Backend module | `hrms` | `hrms` | 1 |
| Flyway migration | 1 aggregate script (`V193__hiring_schema.sql`) | 1 script (`V193__job_openings.sql`) | 1 |
| Externally testable behaviour | HR manages job openings, candidate pipeline, interviews, and converts hired candidates to employees | HR creates, publishes, and tracks job openings with required headcount | 1 |
| Frontend area | `src/hrms/hiring` | `src/hrms/hiring/openings` | 1 |

### Progressive Implementation Phases (Split Breakdown)
To ensure incremental delivery and strict adherence to the size cap during development, `W-112` is organized into 4 progressive slices:

* **`W-112.1` — Job Openings & Requisitions:** Opening creation, status lifecycle (`DRAFT`, `OPEN`, `ON_HOLD`, `FILLED`, `CLOSED`), department/designation links, and headcount tracking.
* **`W-112.2` — Candidate Pipeline & Applications:** Candidate directory, resume metadata, and stage-by-stage application progression board.
* **`W-112.3` — Interview Scheduling & Scorecards:** Interview rounds, interviewer assignment, scorecard submission (ratings 1–5, recommendation, notes).
* **`W-112.4` — Job Offers & One-Click Employee Conversion:** Offer creation, candidate acceptance, and automated conversion into `core.employee` draft.

---

## 3. Scope

### In Scope
1. **Job Openings (`hrms.job_opening`):**
   - Title, job code (e.g. `JOB-2026-001`), department (`core.department`), designation (`core.designation`), work location (`core.work_location`).
   - Openings count (headcount target) and filled count.
   - Employment type: `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERN`.
   - Salary range: `min_salary`, `max_salary` (`NUMERIC(19,4)`).
   - Experience required (min/max years) and job description markdown.
   - Hiring manager (`core.employee`) and recruiters.
   - Status: `DRAFT`, `OPEN`, `ON_HOLD`, `FILLED`, `CLOSED`.
2. **Candidate Profiles (`hrms.candidate`):**
   - First name, last name, email, phone, current company, current title, total experience years, notice period days, resume file URL/path.
3. **Job Applications (`hrms.job_application`):**
   - Unique application of a candidate to a specific job opening.
   - Stages: `APPLIED`, `SCREENING`, `INTERVIEWING`, `OFFERED`, `HIRED`, `REJECTED`, `WITHDRAWN`.
   - Rejection reason tracking for compliance and analytics.
4. **Interviews & Scorecards (`hrms.interview`, `hrms.interview_feedback`):**
   - Interview round name (e.g., "Technical Round 1", "System Design", "HR Culture Fit").
   - Scheduled date and time, duration (minutes), meeting URL.
   - Assigned interviewers (`core.employee`).
   - Scorecard: Overall rating (1 to 5 stars), recommendation (`STRONG_YES`, `YES`, `NEUTRAL`, `NO`), strengths, weaknesses, and private notes.
5. **Job Offers (`hrms.job_offer`):**
   - Designation, department, location, proposed joining date, offered annual CTC (`NUMERIC(19,4)`), validity date.
   - Status: `DRAFT`, `SENT`, `ACCEPTED`, `DECLINED`, `EXPIRED`.
6. **One-Click Convert to Employee (`POST /api/v1/hrms/offers/{id}/convert-to-employee`):**
   - Converts an `ACCEPTED` offer into a new `core.employee` record.
   - Pre-fills first/last name, email, work phone, department, designation, work location, and joining date.
   - Sets employee status to `ACTIVE` (or pending onboarding) without duplicate manual entry.

### Out of Scope
1. **Public Career Website & External Job Board Syndication:** Direct XML feed generation or API push to LinkedIn/Naukri/Indeed (handled in future integration stream `W-108`).
2. **Automated AI Resume Parsing:** PDF/DOCX resume text extraction and skill keyword matching (can be augmented by AI helper `W-114`).
3. **E-Signature Provider Integration:** DocuSign / Adobe Sign webhook integration for formal legal signature execution.

---

## 4. Domain Model & Database Schema

### Schema: `hrms`

```
┌────────────────────┐          ┌──────────────────────┐
│  hrms.job_opening  │ 1      * │ hrms.job_application │
│────────────────────│──────────│──────────────────────│
│ id                 │          │ id                   │
│ title, code, dept  │          │ opening_id, cand_id  │
│ target_openings    │          │ current_stage        │
│ status             │          │ rejection_reason     │
└────────────────────┘          └──────────┬───────────┘
                                           │ 1
                                           │
                                           │ *
┌────────────────────┐ 1        * ┌────────┴───────────┐
│   hrms.candidate   │────────────│    hrms.interview    │
│────────────────────│            │──────────────────────│
│ id                 │            │ id, application_id   │
│ name, email, phone │            │ round_name, time     │
│ resume_url         │            │ status               │
└────────────────────┘            └──────────┬───────────┘
                                             │ 1
                                             │ *
                                  ┌──────────┴───────────┐
                                  │hrms.interview_feedback│
                                  │──────────────────────│
                                  │ interviewer_id       │
                                  │ rating, recommend    │
                                  └──────────────────────┘
```

### Migration: `hrms/V193__hiring_schema.sql`

```sql
-- Migration: V193__hiring_schema.sql
-- Description: W-112 Recruitment and hiring management tables with strict RLS isolation

-- 1. Job Openings
CREATE TABLE hrms.job_opening (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    opening_code VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    department_id UUID REFERENCES core.department(id),
    designation_id UUID REFERENCES core.designation(id),
    work_location_id UUID REFERENCES core.work_location(id),
    hiring_manager_id UUID REFERENCES core.employee(id),
    employment_type VARCHAR(32) NOT NULL DEFAULT 'FULL_TIME',
    openings_count INTEGER NOT NULL DEFAULT 1,
    filled_count INTEGER NOT NULL DEFAULT 0,
    min_salary NUMERIC(19,4),
    max_salary NUMERIC(19,4),
    min_experience_years INTEGER DEFAULT 0,
    max_experience_years INTEGER,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    target_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_job_opening_code UNIQUE (tenant_id, opening_code)
);

ALTER TABLE hrms.job_opening ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.job_opening
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_job_opening_tenant_status ON hrms.job_opening(tenant_id, status);

-- 2. Candidates
CREATE TABLE hrms.candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    email VARCHAR(120) NOT NULL,
    phone VARCHAR(32),
    current_company VARCHAR(120),
    current_title VARCHAR(120),
    experience_years NUMERIC(4,1),
    notice_period_days INTEGER,
    resume_url VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_candidate_email UNIQUE (tenant_id, email)
);

ALTER TABLE hrms.candidate ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.candidate
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_candidate_tenant_email ON hrms.candidate(tenant_id, email);

-- 3. Job Applications
CREATE TABLE hrms.job_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    job_opening_id UUID NOT NULL REFERENCES hrms.job_opening(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES hrms.candidate(id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    rejection_reason VARCHAR(255),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_job_application UNIQUE (tenant_id, job_opening_id, candidate_id)
);

ALTER TABLE hrms.job_application ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.job_application
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_job_application_stage ON hrms.job_application(tenant_id, job_opening_id, stage);

-- 4. Interviews
CREATE TABLE hrms.interview (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    application_id UUID NOT NULL REFERENCES hrms.job_application(id) ON DELETE CASCADE,
    round_name VARCHAR(60) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 45,
    interviewer_id UUID NOT NULL REFERENCES core.employee(id),
    meeting_link VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.interview ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.interview
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_interview_app_time ON hrms.interview(tenant_id, application_id, scheduled_at);

-- 5. Interview Feedback
CREATE TABLE hrms.interview_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    interview_id UUID NOT NULL REFERENCES hrms.interview(id) ON DELETE CASCADE,
    interviewer_id UUID NOT NULL REFERENCES core.employee(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    recommendation VARCHAR(32) NOT NULL,
    strengths TEXT,
    weaknesses TEXT,
    comments TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_interview_interviewer_feedback UNIQUE (tenant_id, interview_id, interviewer_id)
);

ALTER TABLE hrms.interview_feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.interview_feedback
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);

-- 6. Job Offers
CREATE TABLE hrms.job_offer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    application_id UUID NOT NULL REFERENCES hrms.job_application(id) ON DELETE CASCADE,
    designation_id UUID NOT NULL REFERENCES core.designation(id),
    department_id UUID NOT NULL REFERENCES core.department(id),
    work_location_id UUID NOT NULL REFERENCES core.work_location(id),
    offered_ctc NUMERIC(19,4) NOT NULL,
    target_join_date DATE NOT NULL,
    offer_valid_until DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    converted_employee_id UUID REFERENCES core.employee(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.job_offer ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.job_offer
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_job_offer_app ON hrms.job_offer(tenant_id, application_id);
```

---

## 5. Backend Architecture & API Contracts

### Package: `com.infinevo.hrms.hiring`

| Layer | Class / Interface | Responsibility |
|---|---|---|
| Entities | `JobOpening`, `Candidate`, `JobApplication`, `Interview`, `InterviewFeedback`, `JobOffer` | Hibernate entities mapped to tables in `hrms` schema |
| Repositories | `JobOpeningRepository`, `CandidateRepository`, `JobApplicationRepository`, `InterviewRepository`, `JobOfferRepository` | Spring Data JPA repositories with tenant-scoped queries |
| Services | `JobOpeningService`, `CandidateService`, `InterviewService`, `JobOfferService` | Business logic, lifecycle validation, stage transitions, and employee conversion |
| Controllers | `JobOpeningController`, `CandidateController`, `InterviewController`, `JobOfferController` | REST API endpoints under `/api/v1/hrms/...` |

---

### Core API Endpoints

| Method | Path | Action Permission | Description |
|---|---|---|---|
| `GET` | `/api/v1/hrms/job-openings` | `hrms.hiring.read` | List job openings with stage count summaries and department filters |
| `POST` | `/api/v1/hrms/job-openings` | `hrms.hiring.manage` | Create new job opening requisition |
| `GET` | `/api/v1/hrms/job-openings/{id}` | `hrms.hiring.read` | Get opening details, job description, and application pipeline |
| `PUT` | `/api/v1/hrms/job-openings/{id}` | `hrms.hiring.manage` | Update job opening fields |
| `PATCH` | `/api/v1/hrms/job-openings/{id}/status` | `hrms.hiring.manage` | Change status (`OPEN`, `ON_HOLD`, `FILLED`, `CLOSED`) |
| `GET` | `/api/v1/hrms/candidates` | `hrms.hiring.read` | Search and list candidates across openings |
| `POST` | `/api/v1/hrms/candidates` | `hrms.hiring.manage` | Add candidate profile with resume URL |
| `POST` | `/api/v1/hrms/job-openings/{id}/applications` | `hrms.hiring.manage` | Link candidate to opening in `APPLIED` stage |
| `PATCH` | `/api/v1/hrms/applications/{id}/stage` | `hrms.hiring.manage` | Move application to next stage (e.g. `SCREENING` $\to$ `INTERVIEWING`) |
| `POST` | `/api/v1/hrms/applications/{id}/interviews` | `hrms.hiring.manage` | Schedule interview round with assigned interviewer |
| `POST` | `/api/v1/hrms/interviews/{id}/feedback` | `hrms.hiring.interview` | Submit interview scorecard rating and recommendation |
| `POST` | `/api/v1/hrms/applications/{id}/offers` | `hrms.hiring.manage` | Generate job offer with offered CTC and target join date |
| `POST` | `/api/v1/hrms/offers/{id}/convert-to-employee` | `hrms.hiring.manage` | Converts `ACCEPTED` offer into `core.employee` record |

---

## 6. Frontend Architecture

### Directory: `code/frontend/src/hrms/hiring/`

```
code/frontend/src/hrms/hiring/
├── openings/
│   ├── JobOpeningList.jsx         # Openings table with stage counters, filters, and status tags
│   ├── JobOpeningDrawer.jsx       # Requisition creation and edit form
│   └── JobOpeningDetail.jsx       # Kanban pipeline stage board for a single opening
├── candidates/
│   ├── CandidateList.jsx          # Searchable candidate table with resume links
│   └── CandidateDrawer.jsx        # Candidate creation and resume upload
├── interviews/
│   ├── InterviewList.jsx          # Scheduled interviews calendar/list
│   └── ScorecardModal.jsx         # 5-star rating, recommendation radio group, feedback text
└── offers/
    ├── OfferList.jsx              # Active offers table with accept/decline actions
    └── ConvertEmployeeModal.jsx   # Review pre-filled employee details and confirm creation
```

### Route Registrations in `code/frontend/src/hrms/index.js`:
- `/hrms/hiring/openings`
- `/hrms/hiring/openings/:id`
- `/hrms/hiring/candidates`
- `/hrms/hiring/interviews`
- `/hrms/hiring/offers`

### Main Navigation Integration
Registered in HRMS navigation menu under **"Hiring"** with icon `<UserAddOutlined />` or `<SolutionOutlined />`.

---

## 7. Impact on Standing Rules

- **`tenant_id` & RLS:** Every table (`hrms.job_opening`, `hrms.candidate`, `hrms.job_application`, `hrms.interview`, `hrms.interview_feedback`, `hrms.job_offer`) carries `tenant_id` with active row-level security (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`) and tenant isolation policies.
- **Flyway Migrations:** All statements are schema-qualified (`hrms.`, `core.`, `reference.`). No un-scoped table creation. Never uses `ddl-auto`.
- **Precision:** Salary and CTC figures (`min_salary`, `max_salary`, `offered_ctc`) are `NUMERIC(19,4)` in PostgreSQL and `BigDecimal` / `Money` in Java.
- **Indexes:** Indexed on `tenant_id` plus primary query columns (`(tenant_id, status)`, `(tenant_id, email)`, `(tenant_id, job_opening_id, stage)`).
- **Expand / Contract:** Clean additions with safe defaults; zero alterations to existing tables.
- **Module Boundaries:** `hrms` accesses `core.department`, `core.designation`, `core.work_location`, and `core.employee` via defined Spring service interfaces (`EmployeeService.createDraft()`), keeping module boundaries decoupled.

---

## 8. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `JobOpeningServiceTest.java` (Unit) | Backend | Requisition creation, status progression, target openings vs filled counts. |
| `JobApplicationServiceTest.java` (Unit) | Backend | Stage transitions (`APPLIED` $\to$ `INTERVIEWING` $\to$ `OFFERED`), duplicate application prevention. |
| `InterviewServiceTest.java` (Unit) | Backend | Interview scheduling, interviewer conflict check, scorecard calculation and aggregation. |
| `JobOfferServiceTest.java` (Unit) | Backend | Offer generation, validity expiration, and one-click conversion into `core.employee`. |
| `HiringIT.java` (Integration) | Backend | End-to-end recruitment flow with seeded tenant, employee conversion, and tenant isolation validation. |
| `JobOpeningList.test.jsx` (Component) | Frontend | Openings table rendering, search/filter, and creation drawer trigger. |
| `JobOpeningDetail.test.jsx` (Component) | Frontend | Kanban pipeline drag-and-drop / stage move action. |
| `ScorecardModal.test.jsx` (Component) | Frontend | Scorecard submission and validation rules. |

---

## 9. Verification Commands

```bash
# 1. Backend format and unit/integration tests
cd code/backend
mvn spotless:check -pl hrms
mvn test -pl hrms "-Dtest=JobOpeningServiceTest,JobApplicationServiceTest,InterviewServiceTest,JobOfferServiceTest,HiringIT"

# 2. Frontend lint and tests
cd ../frontend
npm run lint
npm test -- src/hrms/hiring/openings/JobOpeningList.test.jsx
```

