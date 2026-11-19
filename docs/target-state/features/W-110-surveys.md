# Feature: Surveys and Polls

| Field | Value |
|---|---|
| **Feature ID** | `W-110` · `HRMS-SURVEYS` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/surveys`, `src/shell/portal`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-110`; no employee feedback, pulse poll, or satisfaction survey mechanism exists today |
| **Status** | Ready |
| **Blocked by** | nothing (`core.employee` and self-service portal exist) |

---

## 1. What and why

Today in `infinevo-platform`, HR leadership has no built-in tool to gather structured feedback from employees:
1. **Pulse Checks & Engagement:** Companies cannot measure Employee Net Promoter Score (eNPS), job satisfaction, or manager effectiveness directly in the HCM.
2. **Third-Party Sprawl:** HR teams resort to external survey tools (Google Forms, SurveyMonkey, Typeform), resulting in fragmented employee identities, poor participation rates, and disconnected HR records.
3. **Anonymity vs Accountability:** External spreadsheets make it hard to enforce anonymous submissions while simultaneously guaranteeing that each employee votes only once.

This feature delivers **Surveys and Polls** in `hrms`:
1. **Survey Builder for HR:** HR officers create targeted pulse polls and multi-question surveys with rating scales (1–5), single/multi-select choices, and open-ended text.
2. **Targeted Delivery:** Surveys can target all employees or specific departments/locations with defined start and end dates.
3. **Employee Self-Service Portal (`/me/surveys`):** Employees see active surveys in their portal dashboard, complete responses seamlessly, and track submitted surveys.
4. **Guaranteed Confidentiality & Anti-Duplicate Voting:** For anonymous surveys, responder identities are decoupled from answers while a submission hash prevents duplicate entries.
5. **Real-Time Analytics:** HR views instant visual dashboards with participation rates, score averages, and choice breakdowns.

---

## 2. Size Cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` (reads `core.employee`, `core.department`, `core.work_location`) | 1 |
| Flyway migration | `hrms/V194__surveys.sql` (tables for survey definition, questions, and responses) | 1 |
| Externally testable behaviour | HR publishes a survey, eligible employees submit answers via `/me/surveys`, and HR views aggregated results | 1 |
| Frontend area | `src/hrms/surveys` (admin builder & analytics) and `src/shell/portal` (`MySurveys.jsx`) | 1 |

---

## 3. Scope

### In Scope
1. **Survey Definition (`hrms.survey`):**
   - Title, description/instructions, survey category (`PULSE_POLL`, `SATISFACTION`, `ONBOARDING_FEEDBACK`, `GENERAL`).
   - `is_anonymous`: When true, individual answers are permanently decoupled from employee records.
   - Target audience: `ALL_EMPLOYEES`, `DEPARTMENT`, or `WORK_LOCATION`.
   - Schedule: `start_date` and `end_date`.
   - Status lifecycle: `DRAFT`, `PUBLISHED`, `CLOSED`.
2. **Question Types (`hrms.survey_question`):**
   - `RATING_1_5`: 1 to 5 star/number rating.
   - `SINGLE_CHOICE`: Radio options (stored as JSON array in question definition).
   - `MULTIPLE_CHOICE`: Checkbox options (multiple selections allowed).
   - `TEXT`: Free-form text feedback.
   - Question ordering (`sort_order`) and `is_required` flag.
3. **Employee Submission (`hrms.survey_response`, `hrms.survey_answer`):**
   - Response records submission time and completion status.
   - Anti-duplicate vote check: prevents an employee from answering the same survey twice.
4. **Analytics & Aggregation API (`GET /api/v1/hrms/surveys/{id}/results`):**
   - Overall response count and participation percentage.
   - Average score for rating questions.
   - Percentage breakdown for single/multi-choice questions.
   - Text answer list (sanitized of author ID if anonymous).
5. **Frontend UI:**
   - HR Management: `/hrms/surveys` (Survey list, Question builder drawer, Results analytics view).
   - Employee Portal: `/me/surveys` (Available pending surveys, Clean survey response modal, Completed survey history).

### Out of Scope
1. **Recurring Automated Surveys (e.g., automated Day 30 / Day 90 surveys):** Scheduled triggers belong to workflow automation.
2. **AI Sentiment Analysis:** NLP sentiment analysis on free-text comments (can be added by AI helper `W-114`).

---

## 4. Domain Model & Database Schema

### Migration: `hrms/V194__surveys.sql`

```sql
-- Migration: V194__surveys.sql
-- Description: W-110 Surveys and polls management with PostgreSQL RLS isolation

-- 1. Survey Definition
CREATE TABLE hrms.survey (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    title VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    is_anonymous BOOLEAN NOT NULL DEFAULT false,
    target_type VARCHAR(32) NOT NULL DEFAULT 'ALL_EMPLOYEES',
    target_id UUID,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.survey ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_tenant_status ON hrms.survey(tenant_id, status, start_date, end_date);

-- 2. Survey Questions
CREATE TABLE hrms.survey_question (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    survey_id UUID NOT NULL REFERENCES hrms.survey(id) ON DELETE CASCADE,
    question_text VARCHAR(500) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    options_json JSONB,
    is_required BOOLEAN NOT NULL DEFAULT true,
    sort_order SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.survey_question ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_question
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_question_survey ON hrms.survey_question(tenant_id, survey_id, sort_order);

-- 3. Survey Responses
CREATE TABLE hrms.survey_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    survey_id UUID NOT NULL REFERENCES hrms.survey(id) ON DELETE CASCADE,
    employee_id UUID REFERENCES core.employee(id),
    submission_token VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_survey_response_token UNIQUE (tenant_id, survey_id, submission_token)
);

ALTER TABLE hrms.survey_response ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_response
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_response_survey ON hrms.survey_response(tenant_id, survey_id);

-- 4. Survey Answers
CREATE TABLE hrms.survey_answer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    response_id UUID NOT NULL REFERENCES hrms.survey_response(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES hrms.survey_question(id) ON DELETE CASCADE,
    rating_value INTEGER CHECK (rating_value >= 1 AND rating_value <= 5),
    selected_options JSONB,
    text_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE hrms.survey_answer ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_answer
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_answer_question ON hrms.survey_answer(tenant_id, question_id);
```

---

## 5. Backend Architecture & API Contracts

### Package: `com.infinevo.hrms.survey`

| Layer | Class | Responsibility |
|---|---|---|
| Entities | `Survey`, `SurveyQuestion`, `SurveyResponse`, `SurveyAnswer` | Hibernate entities mapped to `hrms` schema |
| Repositories | `SurveyRepository`, `SurveyQuestionRepository`, `SurveyResponseRepository` | Data persistence with tenant RLS isolation |
| Services | `SurveyService`, `SurveyAnalyticsService` | Survey authoring, publishing lifecycle, validation, and statistical aggregation |
| Controllers | `SurveyController` (`/api/v1/hrms/surveys`), `MySurveyController` (`/api/v1/me/surveys`) | Admin and employee self-service endpoints |

---

### Core API Contracts

#### Admin Endpoints (`/api/v1/hrms/surveys`)
- `GET /api/v1/hrms/surveys`: List surveys (status filter, participation counts).
- `POST /api/v1/hrms/surveys`: Create survey header and question items.
- `GET /api/v1/hrms/surveys/{id}`: Get survey definition with full questions list.
- `PATCH /api/v1/hrms/surveys/{id}/status`: Publish (`PUBLISHED`) or Close (`CLOSED`) survey.
- `GET /api/v1/hrms/surveys/{id}/results`: Aggregated results (eNPS, average ratings, choice counts).

#### Employee Portal Endpoints (`/api/v1/me/surveys`)
- `GET /api/v1/me/surveys/active`: List pending surveys available for the current employee to answer.
- `POST /api/v1/me/surveys/{id}/submit`: Submit answers for a survey.

---

## 6. Frontend Architecture

### 1. HR Administration (`code/frontend/src/hrms/surveys/`)
- `SurveyList.jsx`: Table of active/closed surveys with participant counts and status tags.
- `SurveyBuilderDrawer.jsx`: Multi-step drawer to add questions (Rating, Single-choice, Multi-choice, Text).
- `SurveyAnalytics.jsx`: Visual metrics cards with progress bars and score distribution charts.
- Registered route: `/hrms/surveys` and `/hrms/surveys/:id/results`.

### 2. Employee Self-Service (`code/frontend/src/shell/portal/panels/`)
- `MySurveys.jsx`: Tab within Employee Portal showing pending surveys with "Take Survey" button.
- `SurveyModal.jsx`: Clean, focus-mode modal for answering questions and submitting responses.

---

## 7. Impact on Standing Rules

- **`tenant_id` & RLS:** All 4 tables carry `tenant_id` and strict PostgreSQL Row Level Security policies.
- **Flyway Migrations:** Exactly 1 migration (`hrms/V194__surveys.sql`). Schema names explicitly qualified.
- **Precision & Data Safety:** Options and multi-select choices stored as valid JSONB arrays. Anti-tampering submission tokens guarantee single-vote integrity.
- **Indexes:** Indexed on `(tenant_id, status)` and `(tenant_id, survey_id)`.
- **Expand / Contract:** Creates new tables; zero alterations to existing tables.
- **Module Boundaries:** `hrms` references `core.employee` and `core.department` via clean foreign keys and service contracts.

---

## 8. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `SurveyServiceTest.java` (Unit) | Backend | Survey creation, question ordering, publishing validation, date window validation. |
| `SurveySubmissionTest.java` (Unit) | Backend | Verifies employee submission, prevents duplicate voting, and verifies anonymity decoupling. |
| `SurveyAnalyticsTest.java` (Unit) | Backend | Verifies statistical aggregations: average ratings, percentage distribution, and total completion rates. |
| `SurveyControllerTest.java` (Unit) | Backend | Verifies REST permissions (`hrms.survey.manage` vs employee self-service). |
| `SurveyBuilder.test.jsx` (Component) | Frontend | Verifies question builder interface, adding/removing choices, and save action. |
| `MySurveys.test.jsx` (Component) | Frontend | Verifies pending survey list, modal question answering, and submission confirmation. |

---

## 9. Verification Commands

```bash
# 1. Backend build, spotless and unit tests
cd code/backend
mvn spotless:check -pl hrms
mvn test -pl hrms "-Dtest=SurveyServiceTest,SurveySubmissionTest,SurveyAnalyticsTest,SurveyControllerTest"

# 2. Frontend lint and tests
cd ../frontend
npm run lint
npm test -- src/hrms/surveys/SurveyList.test.jsx
```

