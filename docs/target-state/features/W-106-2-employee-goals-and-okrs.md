# Feature: Employee Goals & OKRs Tracking

| Field | Value |
|---|---|
| **Feature ID** | `W-106.2` · `HRMS-GOALS` |
| **Promoted to** | `docs/target-state/features/W-106-2-employee-goals-and-okrs.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/performance`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-106` Performance reviews and goals") |
| **Status** | **Complete (`feature/krushna-specs`, 100% tests/lint green)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `hrms` | 1 | **Pass (1 module)** |
| Flyway migration | `V194__employee_goals_and_okrs.sql` (`hrms`) | 1 | **Pass (1 migration)** |
| Externally testable behaviour | Employees create and update progress on individual/team goals and OKRs; managers monitor direct reports' goals and completion rates; goals associate with performance review cycles | 1 | **Pass (1 behaviour)** |
| Frontend area | `hrms/performance` (`TeamGoalsScreen.jsx`), `shell/portal` (`MyGoals.jsx`) | 2 | **Pass (HRMS operational + Self-service portal)** |

---

## 1. Problem

While `W-106.1` established periodic appraisal review cycles and manager 1–5 competency ratings, organizations require continuous goal tracking and Objectives & Key Results (OKRs) between review cycles:
1. **Lack of continuous objective tracking**: Employees had no structured system to set measurable goals, track key results progress %, and record periodic check-ins.
2. **Missing manager visibility**: Managers could not track ongoing team priorities or assess completion metrics ahead of performance appraisal cycles.
3. **Review disconnection**: Performance evaluations were conducted in isolation from concrete quantifiable metrics and goals delivered throughout the evaluation period.

`W-106.2` closes this gap by implementing full Employee Goals & OKR Key Results tracking in HRMS and Employee Self-Service.

---

## 2. Scope

### In scope
- **Goal Data Model & Lifecycle (`hrms.employee_goal`)**:
  - Scopes: `INDIVIDUAL`, `TEAM`, `COMPANY`.
  - Measurable Metric Types: `PERCENTAGE` (0–100%), `NUMERIC` (current vs target value), `MILESTONE` (done/not done).
  - Status Lifecycle: `DRAFT` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `ACHIEVED` / `CANCELLED`.
  - Automatic progress calculation and auto-completion to `ACHIEVED` at 100%.
  - Optional association with `hrms.performance_cycle`.
- **Backend API & Authorization**:
  - `GET /api/v1/hrms/goals/me` (`hrms.goal.read_own`) — Caller's goals.
  - `POST /api/v1/hrms/goals/me` (`hrms.goal.update_own`) — Create new goal.
  - `PATCH /api/v1/hrms/goals/me/{id}/progress` (`hrms.goal.update_own`) — Update progress and notes.
  - `GET /api/v1/hrms/goals/team` (`hrms.goal.review_team`) — Direct reports' goals.
  - `GET /api/v1/hrms/goals/employee/{id}` (`hrms.goal.review_team`) — Goals by employee.
  - `GET /api/v1/hrms/goals/cycle/{id}` (`hrms.goal.read_own`) — Goals linked to review cycle.
- **Frontend Employee Portal (`MyGoals.jsx`)**:
  - Overview statistics: Active Goals, Achieved Goals, Average Progress %.
  - Goal list table with progress bars, due dates, and overdue badges.
  - "Add Goal" modal with date picker and metric type selector.
  - "Check-in" modal with progress slider and achievement notes.
- **Frontend Manager Monitor (`TeamGoalsScreen.jsx`)**:
  - Team goals overview with member search, category filter, and status filter.
  - Drawer displaying complete goal details and metrics.
- **Portal & Navigation Wiring**:
  - `GoalsPanelProvider.java` registered with order 11.
  - Route `/hrms/team-goals` added under HRMS navigation feed.

