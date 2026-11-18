# Feature: Performance Review Cycles & Manager Ratings

| Field | Value |
|---|---|
| **Feature ID** | `W-106.1` · `HRMS-PERFORMANCE` |
| **Promoted to** | `docs/target-state/features/W-106-1-performance-review-cycles.md` — **`W-106-1` with hyphens**, never `W-106.1` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/performance`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-106` Performance reviews and goals") |
| **Status** | **Complete (`feature/krushna-specs`, 100% tests/lint green)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap — this ticket splits

`W-106` as ticketed ("Performance reviews and goals: Review cycles, goals, manager ratings. Size L") spans two distinct testable capabilities:
1. Long-term goals & OKR tracking (goal setting, quantitative metrics, periodic employee progress updates).
2. Time-bound performance appraisal review cycles (cycle configuration, employee self-appraisal, manager ratings on a 1–5 scale, qualitative feedback, and cycle sign-off).

To adhere to the size cap (at most 1 externally testable behaviour and 1 Flyway migration per spec), `W-106` splits into two focused tickets:

### Proposed split

| Ticket | Scope | Size | Status |
|---|---|:---:|---|
| **`W-106.1`** (this spec) | **Performance Review Cycles & Manager Ratings** — Review cycle definition (Q1–Q4 / Annual), automatic review instance creation for eligible employees, employee self-assessment, manager rating (1–5 scale) & feedback submission, and HR review cycle closure & progress tracking (`hrms`). | **M** | **Complete** (`feature/krushna-specs`, 100% tests/lint green) |
| **`W-106.2`** | **Employee Goals & OKRs Tracking** — Individual and team goals, target metrics, key results progress %, status lifecycle (`DRAFT`, `IN_PROGRESS`, `ACHIEVED`, `CANCELLED`), and alignment to performance reviews (`hrms`). | **M** | Queued (`dev-krushna`) |

---

## 1. Problem

Today, the platform has zero performance appraisal or review management capabilities:
1. **No review cycle orchestration**: HR has no mechanism to initiate periodic performance appraisals (quarterly or annual reviews). Performance evaluations are tracked offline in spreadsheets or unstandardized emails.
2. **Missing manager evaluation workflow**: While `core.reporting_line` tracks reporting managers (`code/backend/core/src/main/java/com/infinevo/core/org/ReportingLine.java`), managers cannot view evaluation forms, rate direct reports on competencies, or document feedback within the system.
3. **No self-service appraisal portal**: Employees have no interface in self-service (`/me`) to complete self-evaluations, reflect on achievements, or view final feedback approved by management.
4. **Competitor parity**: Leading HR platforms (Keka, Darwinbox, Lattice) provide structured review cycles with self-reviews, manager 1–5 rating scales, private feedback, and cycle completion dashboards.

This ticket (`W-106.1`) delivers performance review cycles, self-appraisals, manager ratings, and review progress administration.

---

## 2. Scope

### In scope
- **Review Cycle Administration (`hrms.performance_cycle`)**:
  - HR defines review cycles: Cycle Name (e.g. "Q4 2026 Annual Performance Review"), review period (`period_start`, `period_end`), self-review deadline, and manager review deadline.
  - Rating scale: Standardized 1–5 numerical rating scale (1 = Needs Improvement, 2 = Partially Meets, 3 = Meets Expectations, 4 = Exceeds Expectations, 5 = Outstanding).
  - Cycle status lifecycle: `DRAFT` $\rightarrow$ `ACTIVE` $\rightarrow$ `CLOSED`.
- **Review Instance Generation (`hrms.performance_review`)**:
  - Activating a cycle generates review instances for all active employees linked to their primary reporting manager (derived from `core.reporting_line`).
  - Self-review submission: Employee inputs self-rating (1–5) and qualitative achievements comments (`PENDING_SELF_REVIEW` $\rightarrow$ `PENDING_MANAGER_REVIEW`).
  - Manager evaluation: Reporting manager inputs manager rating (1–5) and performance feedback comments (`PENDING_MANAGER_REVIEW` $\rightarrow$ `COMPLETED`).
- **HR & Manager Screens**:
  - HR Review Cycle Management (`/hrms/performance-cycles`): cycle list, progress meters ("42 of 50 reviews completed — 84%"), average rating metrics, and cycle closure.
  - Manager Team Reviews (`/hrms/performance-reviews`): list of direct reports' pending and completed reviews with evaluation drawer.
- **Employee Self-Service**:
  - My Reviews panel in Employee Portal (`/me/reviews` or `/me` Reviews panel): displays active review prompt with self-evaluation modal and historical completed reviews.
- **Permissions**:
  - `hrms.performance.manage` granted to `tenant-admin` and `hr`.
  - `hrms.performance.review_team` granted to `manager`, `tenant-admin`, and `hr`.
  - `hrms.performance.read_own` and `hrms.performance.update_own` granted to all employee-linked roles (`employee`, `manager`, `hr`, `payroll-officer`, `finance`).

### Out of scope
- Continuous goal / OKR tracking and progress percentages (owned by `W-106.2`).
- 360-degree peer feedback and multi-rater matrix reviews (future enhancement).
- Automated compensation / salary revision linking based on ratings (handled manually by payroll officers).

---

## 3. Flow

```
[HR Creates & Activates Performance Cycle]
                     │
                     ▼
[Review Instances Generated for All Active Employees with Reporting Manager]
                     │
                     ▼
[Stage 1: Employee Self-Appraisal]
  • Employee visits /me/reviews
  • Fills Self-Rating (1–5) & Achievements Comments
  • Submits ──► Status becomes PENDING_MANAGER_REVIEW
                     │
                     ▼
[Stage 2: Manager Evaluation]
  • Reporting Manager visits /hrms/performance-reviews
  • Inspects Employee's Self-Rating & Comments
  • Inputs Manager Rating (1–5) & Performance Feedback
  • Submits ──► Status becomes COMPLETED
                     │
                     ▼
[Stage 3: Cycle Progress & Closure]
  • HR monitors completion rate on /hrms/performance-cycles
  • HR clicks "Close Cycle" once all reviews completed or deadline reached
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/hrms`)

| Layer | File | Change |
|---|---|---|
| Entity | `com.infinevo.hrms.performance.PerformanceCycle.java` | **New**. Entity mapped to `hrms.performance_cycle`. |
| Entity | `com.infinevo.hrms.performance.PerformanceReview.java` | **New**. Entity mapped to `hrms.performance_review`. |
| Enum | `com.infinevo.hrms.performance.PerformanceCycleStatus.java` | **New**. `DRAFT`, `ACTIVE`, `CLOSED`. |
| Enum | `com.infinevo.hrms.performance.PerformanceReviewStatus.java` | **New**. `PENDING_SELF_REVIEW`, `PENDING_MANAGER_REVIEW`, `COMPLETED`. |
| Repository | `com.infinevo.hrms.performance.PerformanceCycleRepository.java` | **New**. Query review cycles by tenant and status. |
| Repository | `com.infinevo.hrms.performance.PerformanceReviewRepository.java` | **New**. Query reviews by cycle, employee, manager, and status. |
| Service | `com.infinevo.hrms.performance.PerformanceReviewService.java` | **New**. Interface for cycle creation, review generation, self-submission, manager rating, and summary metrics. |
| ServiceImpl | `com.infinevo.hrms.performance.PerformanceReviewServiceImpl.java` | **New**. Service implementation enforcing deadlines, reporting line manager lookups, and score validations (1–5). |
| Controller | `com.infinevo.hrms.performance.PerformanceCycleController.java` | **New**. Endpoints for cycle management (`/api/v1/performance/cycles`). |
| Controller | `com.infinevo.hrms.performance.PerformanceReviewController.java` | **New**. Endpoints for self-reviews and manager evaluations (`/api/v1/performance/reviews`). |
| Navigation | `com.infinevo.hrms.navigation.HrmsNavigation.java` | Register `hrms.performance_cycles` and `hrms.performance_reviews` in HRMS navigation feed (`file:code/backend/hrms/src/main/java/com/infinevo/hrms/navigation/HrmsNavigation.java:30-80`). |

### 4.2 API contract

| Method | Path | Request Body | Response Body | Auth / Permission |
|---|---|---|---|---|
| `GET` | `/api/v1/performance/cycles` | — | `List<PerformanceCycleResponse>` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles` | `CreateCycleRequest` | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles/{id}/activate` | — | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles/{id}/close` | — | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/cycles/{id}/summary` | — | `CycleSummaryResponse` (total, completed %, avg rating) | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/reviews/my` | — | `List<PerformanceReviewResponse>` | `hrms.performance.read_own` |
| `PATCH` | `/api/v1/performance/reviews/{id}/self-submit` | `SelfReviewRequest` (selfRating 1–5, comments) | `PerformanceReviewResponse` | `hrms.performance.update_own` (own review only) |
| `GET` | `/api/v1/performance/reviews/team` | — | `List<PerformanceReviewResponse>` | `hrms.performance.review_team` |
| `PATCH` | `/api/v1/performance/reviews/{id}/manager-submit` | `ManagerReviewRequest` (managerRating 1–5, comments) | `PerformanceReviewResponse` | `hrms.performance.review_team` (assigned manager only) |

### 4.3 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| `src/hrms/performance/performanceService.js` | **New**. Axios API client for cycles, self-reviews, and manager reviews. |
| `src/hrms/performance/PerformanceCyclesScreen.jsx` | **New**. HR cycle admin screen: cycle list, status tags, Create Cycle modal, progress bars, and Close Cycle button. |
| `src/hrms/performance/TeamReviewsScreen.jsx` | **New**. Manager review screen under HRMS › Performance Reviews: table of direct reports, self-review summary, and evaluation drawer with 1–5 Star/Radio ratings. |
| `src/shell/portal/panels/MyReviews.jsx` | **New**. Employee self-service panel on `/me`: active review banner, self-appraisal modal, and historical completed rating feedback. |
| `src/shell/portal/PortalLayout.jsx` | Add "Reviews" tab to employee self-service portal items feed. |

---

## 5. Database Changes

> **Flyway script:** `code/backend/migration/src/main/resources/db/migration/hrms/V193__performance_review_cycles.sql`

```sql
-- Schema: hrms
-- Version: V193
-- Feature: W-106.1 Performance review cycles and manager ratings

CREATE TABLE hrms.performance_cycle (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name                        VARCHAR(150) NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    self_review_deadline        DATE NOT NULL,
    manager_review_deadline     DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rating_scale_max            INTEGER NOT NULL DEFAULT 5,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_perf_cycle_dates CHECK (period_end >= period_start),
    CONSTRAINT chk_perf_cycle_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    CONSTRAINT chk_perf_cycle_scale CHECK (rating_scale_max BETWEEN 3 AND 10)
);

CREATE INDEX idx_perf_cycle_tenant_dates 
    ON hrms.performance_cycle (tenant_id, period_start DESC, period_end DESC);

ALTER TABLE hrms.performance_cycle ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.performance_cycle
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE TABLE hrms.performance_review (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id),
    cycle_id                    UUID NOT NULL REFERENCES hrms.performance_cycle(id) ON DELETE CASCADE,
    employee_id                 UUID NOT NULL REFERENCES core.employee(id),
    manager_id                  UUID NOT NULL REFERENCES core.employee(id),
    self_rating                 INTEGER,
    self_comments               TEXT,
    self_submitted_at           TIMESTAMPTZ,
    manager_rating              INTEGER,
    manager_comments            TEXT,
    manager_submitted_at        TIMESTAMPTZ,
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING_SELF_REVIEW',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_perf_review_status CHECK (status IN ('PENDING_SELF_REVIEW', 'PENDING_MANAGER_REVIEW', 'COMPLETED')),
    CONSTRAINT chk_perf_self_rating CHECK (self_rating IS NULL OR self_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_perf_mgr_rating CHECK (manager_rating IS NULL OR manager_rating BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uk_perf_review_tenant_cycle_emp 
    ON hrms.performance_review (tenant_id, cycle_id, employee_id);

CREATE INDEX idx_perf_review_tenant_manager 
    ON hrms.performance_review (tenant_id, manager_id, status);

CREATE INDEX idx_perf_review_tenant_emp 
    ON hrms.performance_review (tenant_id, employee_id, status);

ALTER TABLE hrms.performance_review ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.performance_review
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Seed role grants
CREATE OR REPLACE FUNCTION core.grant_performance_actions(p_tenant_id UUID)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN RETURN; END IF;
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('tenant-admin', 'hrms.performance.manage'),
        ('tenant-admin', 'hrms.performance.review_team'),
        ('hr', 'hrms.performance.manage'),
        ('hr', 'hrms.performance.review_team'),
        ('manager', 'hrms.performance.review_team'),
        ('manager', 'hrms.performance.read_own'),
        ('manager', 'hrms.performance.update_own'),
        ('employee', 'hrms.performance.read_own'),
        ('employee', 'hrms.performance.update_own'),
        ('payroll-officer', 'hrms.performance.read_own'),
        ('payroll-officer', 'hrms.performance.update_own'),
        ('finance', 'hrms.performance.read_own'),
        ('finance', 'hrms.performance.update_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r ON r.tenant_id = p_tenant_id AND r.code = grants.role_code AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

SELECT core.grant_performance_actions(t.tenant_id) FROM core.tenant t;
```

- [x] Standing rule: Schema named explicitly on every statement (`hrms.performance_cycle`, `hrms.performance_review`).
- [x] Standing rule: `tenant_id` present as leading index column on all indexes.
- [x] Standing rule: `ENABLE ROW LEVEL SECURITY;` + `CREATE POLICY tenant_isolation` using fail-safe `CASE` format.
- [x] Standing rule: Money/BigDecimal rule: N/A (numerical integer rating scale 1–5).

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.hrms.performance.PerformanceReviewServiceImplTest.java` | Cycle activation generates reviews for active employees, validates rating bounds (1–5), verifies transitions from `PENDING_SELF_REVIEW` to `PENDING_MANAGER_REVIEW` to `COMPLETED`. |
| Unit | `com.infinevo.hrms.performance.PerformanceSecurityGuardTest.java` | Employee can only submit self-review for their own record; manager can only submit ratings for direct reports; HR can manage cycles. |
| Integration | `com.infinevo.hrms.performance.PerformanceRlsIT.java` | Verifies tenant isolation across cycles and reviews under `app_user`. |
| Component | `src/hrms/performance/PerformanceCyclesScreen.test.jsx` | Tests cycle listing, modal submission, and Close Cycle action. |
| Component | `src/hrms/performance/TeamReviewsScreen.test.jsx` | Tests manager evaluation drawer, rating selection (1–5), and submit button. |
| Component | `src/shell/portal/panels/MyReviews.test.jsx` | Renders self-review prompt and tests self-rating submission. |

---

## 7. Verification

Verification commands and steps:

```bash
# 1. Run hrms performance unit and integration tests
./mvnw -pl hrms test -Dtest='PerformanceReviewServiceImplTest,PerformanceSecurityGuardTest,PerformanceRlsIT'

# 2. Run frontend performance tests
cd code/frontend
npm test -- src/hrms/performance/ src/shell/portal/panels/MyReviews.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Action | Expected Result |
|---|---|---|
| Cycle Activation | Call `POST /api/v1/performance/cycles/{id}/activate` | Cycle status changes to `ACTIVE`, creates a `performance_review` row for every active employee with their reporting manager. |
| Self-Review Submission | Employee submits self-rating (4) and comments | Row updates `self_rating=4`, `status='PENDING_MANAGER_REVIEW'`. |
| Manager Rating | Manager submits rating (5) and review feedback | Row updates `manager_rating=5`, `status='COMPLETED'`. |
| Unauthorized Access | Employee attempts to rate another peer | Endpoint rejects with `403 Forbidden`. |
| Cycle Summary Metrics | Call `GET /api/v1/performance/cycles/{id}/summary` | Returns accurate counts of completed vs pending reviews and tenant average rating. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Employee has no assigned manager when cycle activates | Medium | Fall back `manager_id` to the tenant's primary HR administrator with an alert banner on the cycle summary page. |
| Manager fails to submit review before deadline | Medium | HR has override permissions (`hrms.performance.manage`) to complete or reassign overdue reviews. |
| Ratings entered outside 1–5 range | Low | Enforced by both database check constraints (`CHECK (self_rating BETWEEN 1 AND 5)`) and backend validation annotations. |

---

## 9. Rollback

1. Flyway script is forward-only; if rolled back prior to general adoption, cycles can be set to `CLOSED` or deleted via cascade without touching payroll or attendance tables.
2. Frontend performance menu items can be disabled dynamically by revoking `hrms.performance.manage` and `hrms.performance.review_team` action codes in Keycloak.

