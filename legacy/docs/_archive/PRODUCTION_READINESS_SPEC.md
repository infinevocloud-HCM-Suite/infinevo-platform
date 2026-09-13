# Production Readiness Specification — Infinevo Cloud HRMS & Payroll Suite

> **Document Version:** 1.0  
> **Last Updated:** 2026-08-12  
> **Classification:** Internal — Principal Architecture Specification  
> **Status:** Active  
> **Scope:** HRMS Backend · HRMS Frontend · Payroll Backend · Payroll Frontend

---

## Current Readiness Assessment

| Dimension | Current State | Target |
|---|---|---|
| Core Application Logic | ~65% complete | 100% |
| Enterprise Readiness | ~35% complete | 100% |
| Security & Compliance | ❌ Not ready | ✅ Production-grade |
| Multi-Tenancy | ❌ Absent | ✅ Row-level isolation |
| Test Coverage | ❌ 0% | ✅ >70% |
| Observability | ❌ None | ✅ Full-stack tracing |

---

## Section 1 — Executive Strategy & Architectural Prerequisites

### 1.1 Keycloak SSO Unification

**Current Problem:**  
HRMS uses a custom JWT implementation (jjwt 0.12.5) with its own `OurUsers` table, while Payroll uses Keycloak (OAuth2 Resource Server). This creates session mismatches during cross-application navigation and forces employees to authenticate twice.

**Target Architecture:**  
A single Keycloak realm (`HRMS`) becomes the sole Identity Provider (IdP) for both services. All JWT issuance, validation, refresh, and revocation is handled exclusively by Keycloak.

```
                        Keycloak Realm: HRMS
                   (authentication.infinevocloud.com)
                              │
              ┌───────────────┼───────────────┐
              ▼                               ▼
       HRMS Backend                   Payroll Backend
  (OAuth2 Resource Server)       (OAuth2 Resource Server)
              │                               │
       HRMS Frontend                  Payroll Frontend
   (Keycloak-JS / PKCE)          (@react-keycloak/web)
```

**Migration Steps:**
1. Remove `JWTAuthFilter.java`, `SecurityConfig` JWT configuration, and `PasswordResetToken` flow from HRMS
2. Register HRMS Backend as a Keycloak OAuth2 Resource Server (same as Payroll)
3. Retain `OurUsers` table for HRMS-specific profile data only — delegate all auth to Keycloak
4. Migrate password reset to Keycloak's built-in reset flow
5. Update `HRMS_Frontend` to use `keycloak-js` with PKCE flow, replacing the custom login form
6. Unify `companyUser.keycloakUserId` ↔ `OurUsers.email` as the cross-service identity anchor

---

### 1.2 Multi-Tenant Schema & Row-Level Isolation

**Current Problem:**  
Core HRMS tables (`our_users`, `attendance`, `timesheets`, `leave_requests`, `projects`, `tasks`) have no `org_id` tenant discriminator. All data is globally accessible. A single database breach exposes all customer data.

**Target Architecture:**  
Every entity in both services must carry `org_id` as a non-nullable column. Hibernate `@TenantId` filter or Spring Data `@Query` scoping enforces that no query can cross tenant boundaries.

**Implementation Standard:**

```java
// Base entity — all domain entities must extend this
@MappedSuperclass
public abstract class TenantAwareEntity {

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;
}

// Example: Hibernate multi-tenancy filter
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "orgId", type = Long.class))
@Filter(name = "tenantFilter", condition = "org_id = :orgId")
@Entity
public class OurUsers extends TenantAwareEntity { ... }
```

**Tenancy Strategy Decision:**

| Strategy | Description | Recommendation |
|---|---|---|
| Shared DB, Row-Level | `org_id` column on every table, query-scoped | ✅ Adopt first (lowest migration cost) |
| Schema-per-Tenant | Separate MySQL schema per org | Future option for premium tier |
| DB-per-Tenant | Separate database per org | Enterprise on-premise option only |

**HRMS Tables Requiring `org_id` Addition:**
`our_users`, `employee`, `attendance`, `timesheet`, `timesheets`, `leave_requests`, `leave_type`, `leave_balance`, `holiday`, `project`, `task`, `overtime_request`, `notification`, `role_reminder_config`, `report`

---

### 1.3 `BigDecimal` Financial Precision Standard

**Current Problem (BUG-003):**  
HRMS computes LOP days as `Double` (e.g., `0.5` for half-day leave). Payroll's `LeaveResponseDTO` maps `totalLeaves` to `Integer`, truncating `0.5` → `0`. An employee taking a half-day unpaid leave receives full salary. This is a direct payroll calculation defect.

**Mandatory Standard:**  
All financial fields — leave balances, LOP days, CTC components, earnings, deductions, net pay, EPF/ESI contributions, and Professional Tax — must use `BigDecimal` with explicit scale.

```java
// ✅ Correct — all financial fields
@Column(precision = 19, scale = 4)
private BigDecimal netPay;

@Column(precision = 10, scale = 2)
private BigDecimal lopDays;        // Supports 0.5, 0.25 half/quarter days

// ❌ Incorrect — never use for financial values
private Double netPay;
private Integer totalLeaves;
private Float lopDays;
```

**Rounding Rule:**  
Use `RoundingMode.HALF_UP` for all monetary calculations. Use `RoundingMode.HALF_EVEN` (Banker's rounding) for high-volume tax slab computations.

**Fields Requiring Immediate Conversion:**
- `EmployeeMonthlyLop.lopDays` → `BigDecimal`
- `LeaveResponseDTO.totalLeaves` → `BigDecimal`
- `EmployeePayRun.lop`, `netPay`, `monthlySalary`, `totalEarnings`, `totalDeductions` → `BigDecimal`
- `CtcStructure.annualCtc`, `monthlySalary` → `BigDecimal`
- `Earning.amountOrPercentage`, `Deduction.amount` → `BigDecimal`

---

### 1.4 Asynchronous Service Decoupling

**Current Problem:**  
During pay run processing, `EmployeePayRunServiceImpl` makes a **synchronous blocking WebClient call** to HRMS (`POST /public/get-employee-leaves`). If HRMS is down, overloaded, or slow, the entire pay run fails or hangs. This is a single point of failure in a critical financial workflow.

**Target Architecture — Two Options:**

**Option A: Pre-Calculated Snapshot Table (Recommended — Lower Complexity)**
```
HRMS: Every time a leave is approved or LOP is finalized for a month:
  → Writes to `employee_monthly_lop` table
  → Publishes event to Redis Stream: `lop.finalized`

Payroll: Subscribes to `lop.finalized` stream
  → Writes to local `hrms_lop_snapshot` table (org_id, emp_email, year, month, lop_days BigDecimal)
  → Pay run reads from local snapshot — no runtime HRMS call needed
```

**Option B: Kafka / Redis Streams Event Bus**
```
HRMS publishes:    LeaveApproved    → topic: hrms.leave.events
                   LOPFinalized     → topic: hrms.lop.events

Payroll consumes:  hrms.lop.events  → updates local lop_snapshot table
                   Pay run uses local snapshot only
```

**Immediate Fix (Temporary — while async is built):**  
Add circuit breaker (`Resilience4j`) around the HRMS WebClient call so pay run degrades gracefully (uses `0` LOP days with a warning flag) instead of failing completely.

---

## Section 2 — Enterprise Feature Baseline

### 2.1 Payslip PDF Engine

**Requirement:** Every completed pay run must generate a per-employee payslip PDF containing:
- Organization logo, name, and registered address
- Employee name, ID, designation, department, PAN
- Pay period, pay date
- Earnings breakdown (Basic, HRA, LTA, Special Allowance, Variable)
- Deductions breakdown (EPF, ESI, PT, TDS, LOP deduction)
- Net pay (in figures and words)
- Employer contribution summary

**Architecture:**
```
Pay Run Finalized Event
        │
        ▼
Redis Queue: payslip.generation.queue
        │
        ▼
Worker (BullMQ / Celery): PDF Generator Service
  - Template engine: JasperReports (Java) or Puppeteer (Node)
  - Stores PDF to S3: /{orgId}/payslips/{year}/{month}/{employeeId}.pdf
  - Generates pre-signed URL (24-hour expiry)
        │
        ▼
Email Dispatcher: JavaMailSender / Brevo API
  - Sends payslip email to employee work email
  - Attaches PDF or embeds pre-signed download link
```

---

### 2.2 Tax Form 16 Engine

**Requirement:** Annual generation of Form 16 (Part A + Part B) for all employees at financial year-end (April of each year).

**Architecture:**
- Part A: Auto-populated from TDS deducted across all 12 pay runs
- Part B: Populated from `IncomeTaxDeclaration` + `ProofOfInvestment` + salary breakdown
- Batch generation triggered as a scheduled job on April 1 each year
- PDFs stored to S3 and emailed to employees
- HR dashboard shows Form 16 generation status per employee

---

### 2.3 Full & Final (F&F) Settlement Engine

**Requirement:** When an employee exits, the system must calculate and process:
- Unpaid salary for partial month
- Earned leave encashment (unused balance × daily rate)
- Gratuity computation (if eligible: `15/26 × last basic × years of service`)
- Bonus / variable pay recovery (if applicable)
- PF settlement initiation
- Final payslip generation and bank payout

**Trigger:** HR marks employee status as `EXITED` in HRMS → triggers F&F settlement workflow in Payroll.

---

### 2.4 Direct Bank Payout API Integration

**Target Banks:** ICICI, HDFC, Axis (NEFT / IMPS / RTGS bulk payment APIs)

**Flow:**
```
Pay Run Approved
      │
      ▼
Bank Payout Service
  - Validates employee bank details (IFSC, account number)
  - Generates bulk payment file (bank-specific format)
  - Submits via bank API with dual approval (Maker-Checker model)
  - Polls for transaction status
  - Updates EmployeePayRun.paymentStatus → PAID / FAILED
  - Triggers payslip email on PAID confirmation
```

**Security:** Maker-Checker dual approval required. No single user can approve and execute a bulk payment.

---

### 2.5 Mobile Employee Self-Service (ESS)

**Requirement:** React Native or Progressive Web App (PWA) allowing employees to:
- View payslips and download PDF
- Check leave balance and apply for leaves
- Clock in / clock out for attendance
- Submit timesheet entries
- View Form 16
- Chat with Conversational ESS Agent

---

## Section 3 — Cloud Infrastructure Architecture

### 3.1 Kubernetes Cluster (AWS EKS / GCP GKE)

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                      │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ HRMS Backend │  │Payroll Backend│  │  Worker Pods     │  │
│  │  Deployment  │  │  Deployment  │  │ (PDF / Email /   │  │
│  │  HPA: 2–10   │  │  HPA: 2–10   │  │  AuditBot)       │  │
│  │  pods        │  │  pods        │  │  HPA: 1–5        │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ HRMS FE      │  │ Payroll FE   │  │   Keycloak       │  │
│  │  (nginx)     │  │  (nginx)     │  │   (StatefulSet)  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                             │
│  Ingress: nginx-ingress-controller + cert-manager (TLS)    │
└─────────────────────────────────────────────────────────────┘
```

**Horizontal Pod Autoscaler (HPA) Policy:**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: hrms-backend-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: hrms-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70   # Scale out when CPU > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

---

### 3.2 Database Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Database Layer                          │
│                                                          │
│  Application Pods                                        │
│       │                                                  │
│       ▼                                                  │
│  PgBouncer / RDS Proxy  ◄── Connection Pool (max: 200)   │
│       │                                                  │
│       ├──► Primary DB (Read/Write) ── Sync Replica       │
│       │                                   │              │
│       └──► Read Replica 1               Failover         │
│            (Reports, Analytics,                          │
│             Attendance Queries)                          │
└──────────────────────────────────────────────────────────┘
```

**PgBouncer Configuration:**
- Pool mode: `transaction` (recommended for JPA/Hibernate)
- Max client connections: 200 per service
- Server pool size: 20 per database

**Read Replica Routing:**  
Use Spring's `@ReadOnlyTransaction` routing to direct all `SELECT` queries for reports, dashboards, and attendance logs to the read replica. All writes go to the primary.

---

### 3.3 Redis Cluster

**Two Redis roles:**

| Role | Purpose | Implementation |
|---|---|---|
| **Cache** | Session data, org config, Keycloak token introspection cache, leave balance cache | Redis `GET/SET` with TTL |
| **Task Queue** | PDF payslip generation, Form 16 compilation, bulk email dispatch, AuditBot job scheduling | BullMQ (Node) or Celery (Python) |

**Queue Architecture:**
```
┌─────────────────────────────────────────────┐
│              Redis Cluster                  │
│                                             │
│  Queues:                                    │
│  ├── payslip.generation      (priority: 2)  │
│  ├── form16.generation       (priority: 2)  │
│  ├── email.dispatch          (priority: 3)  │
│  ├── auditbot.run            (priority: 1)  │
│  └── lop.sync.events         (stream)       │
│                                             │
│  Workers (auto-scaled pods):                │
│  ├── pdf-worker (2–5 replicas)              │
│  ├── email-worker (1–3 replicas)            │
│  └── audit-worker (1–2 replicas)            │
└─────────────────────────────────────────────┘
```

---

### 3.4 Secure Document Storage (AWS S3 / GCP Cloud Storage)

**Current problem:** Cloudinary stores employee documents with public or long-lived URLs — any URL leak exposes sensitive data.

**Target architecture:**
```
Upload Flow:
  Employee uploads document
        │
        ▼
  Backend generates pre-signed PUT URL (15 min expiry)
        │
        ▼
  Browser uploads directly to S3 (bypasses backend)
        │
        ▼
  Backend records S3 object key in DB (not URL)

Download Flow:
  Employee requests document
        │
        ▼
  Backend validates auth + org_id ownership
        │
        ▼
  Backend generates pre-signed GET URL (1 hour expiry)
        │
        ▼
  Returns URL to frontend — URL expires automatically
```

**S3 Bucket Structure:**
```
infinevo-cloud-{env}/
├── {orgId}/
│   ├── employees/{employeeId}/documents/
│   ├── employees/{employeeId}/profile-photo/
│   ├── payslips/{year}/{month}/{employeeId}.pdf
│   ├── form16/{financialYear}/{employeeId}.pdf
│   └── tax-proofs/{employeeId}/{year}/
```

**Encryption:** Server-Side Encryption with KMS (SSE-KMS). Separate KMS key per organization for premium tier.

---

### 3.5 CI/CD Pipeline with Flyway Integration

```
Developer Push
      │
      ▼
GitHub Actions / GitLab CI
      │
      ├── Unit & Integration Tests (JUnit / Jest)
      ├── Code Coverage Gate (>70% required)
      ├── SAST Security Scan (Sonar / Snyk)
      │
      ▼
Docker Image Build & Push (ECR / GCR)
      │
      ▼
Flyway Migration Job (runs before app deployment)
  - Connects to target DB
  - Applies pending versioned SQL scripts
  - Fails deployment if migration fails
      │
      ▼
Kubernetes Rolling Deployment (zero-downtime)
      │
      ▼
Post-Deploy Smoke Tests
```

**Flyway Script Convention:**
```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_org_id_to_our_users.sql
├── V3__add_org_id_to_attendance.sql
├── V4__bigdecimal_lop_column_conversion.sql
├── V5__add_hrms_lop_snapshot_table.sql
└── V6__add_employee_monthly_lop_index.sql
```

---

## Section 4 — AI Agent Architecture & Capabilities

### 4.1 AuditBot — Pre-Payroll Anomaly Detection Agent

**Purpose:** Runs automatically 72 hours before every scheduled pay run. Scans all employee pay data for anomalies before the CFO/HR approves the run.

**Trigger:** Scheduled job (cron) or manual trigger from payroll admin dashboard.

**Checks Performed:**

| Check | Anomaly Detected | Action |
|---|---|---|
| Leave approval status | Unapproved leaves in pay period | Flag employee — block pay run |
| LOP mismatch | HRMS LOP ≠ Payroll snapshot LOP | Alert HR — show delta |
| CTC anomaly | Net pay deviation >15% from last month | Flag for review |
| Bank detail change | Bank account changed within 30 days | Require dual verification |
| New employee salary | First pay run for employee | Notify HR to verify CTC |
| Duplicate employee | Same PAN in multiple org IDs | Cross-tenant integrity check |

**Architecture:**
```
Cron: T-72h before pay run
        │
        ▼
AuditBot Worker (Redis Queue: auditbot.run)
        │
        ├── Fetches all employees in pay run
        ├── Runs anomaly checks (rule-based + ML scoring)
        ├── Generates Audit Report
        │
        ▼
HITL Gate: HR / Finance approves or overrides each flag
        │
        ▼
Pay run proceeds only if HITL gate is cleared
        │
Result stored in: audit_log table (org_id, payrun_id, check_name, result, approved_by)
```

**Security Rules:**
- All AuditBot decisions are logged with `org_id`, timestamp, and user who approved/overrode
- AuditBot cannot modify any financial record — read-only access only
- HITL override requires a manager-level Keycloak role (`PAYROLL_MANAGER`)

---

### 4.2 Conversational ESS Agent (WhatsApp / Slack)

**Purpose:** 24/7 employee self-service chatbot allowing employees to query payslips, leave balances, and HR policies without raising a ticket.

**Capabilities:**

| Query Type | Example | Response |
|---|---|---|
| Payslip | "Show me my July 2025 payslip" | Returns PDF download link (pre-signed URL) |
| Leave balance | "How many earned leaves do I have?" | Returns current balance from `employee_leave_balance` |
| LOP status | "Was I marked LOP in June?" | Returns LOP days from `employee_monthly_lop` |
| Policy query | "What is the leave encashment policy?" | Answers from RAG vector DB of HR policy documents |
| Attendance | "Was I present yesterday?" | Returns attendance status from HRMS |

**Architecture:**
```
Employee (WhatsApp / Slack)
        │
        ▼
Webhook → ESS Agent Gateway
        │
        ├── Auth: Validates employee identity via Keycloak token or phone OTP
        ├── Intent Classification (LLM)
        ├── Data Fetch (scoped strictly by org_id + employee_id)
        ├── RAG lookup (org-specific policy vector DB)
        │
        ▼
Response formatted for channel (WhatsApp / Slack Block Kit)
```

**Isolation Rules:**
- Vector DB is partitioned by `org_id` — no cross-tenant document retrieval
- All prompts and responses are logged to `agent_audit_log` table
- No PII (PAN, bank account) is ever returned in chat — only summary data or secure links

---

### 4.3 Tax Proof Verification Agent (OCR / Multimodal)

**Purpose:** Automate verification of investment proof documents (rent receipts, 80C investment proofs, medical bills) submitted during tax declaration.

**Capabilities:**
- Extract key fields from uploaded PDFs/images: amount, date, landlord name, PAN
- Validate rent receipts: cross-check declared HRA vs. submitted rent amount
- Flag mismatches or suspicious documents for HR review
- Auto-approve documents that pass all validation rules

**Architecture:**
```
Employee uploads proof document (PDF / image)
        │
        ▼
S3 Upload → triggers Lambda / Cloud Function
        │
        ▼
OCR Agent (Google Document AI / AWS Textract / GPT-4 Vision)
        │
        ├── Extracts: amount, date, PAN, landlord name, IFSC
        ├── Validates against declared values in IncomeTaxDeclaration
        ├── Confidence score: if > 0.9 → auto-approve
        │                    if 0.5–0.9 → flag for HR review
        │                    if < 0.5 → reject with reason
        ▼
Result stored in: ProofOfInvestmentDocument.verificationStatus
HITL: HR sees all flagged documents in review dashboard
```

**HITL Guardrail:**  
No AI agent can auto-approve a document that changes an employee's tax liability by more than ₹50,000. These must be manually reviewed by HR/Finance.

---

## Section 5 — Prioritized 4-Phase Implementation Roadmap

### Phase 1 — Security, Tenant Isolation & Financial Precision
**Target Window: Weeks 1–6**  
_Nothing in Phase 2 begins until all P0 items in Phase 1 are resolved and verified._

| Item | Task | Priority | Owner | Notes |
|---|---|---|---|---|
| 1.1 | Keycloak SSO Unification across HRMS & Payroll | `[P0 - Blocker]` | Backend Lead | Remove custom JWT from HRMS; adopt OAuth2 RS |
| 1.2 | Multi-Tenant Schema & Row Isolation (`org_id`) | `[P0 - Blocker]` | Backend Lead | Add `org_id` to all HRMS entities; Hibernate filter |
| 1.3 | Fix BUG-003 & Financial Precision Audit (`BigDecimal` Conversion) | `[P0 - Blocker]` | Backend Lead | Convert all financial fields; fix LOP DTO type |
| 1.4 | Flyway Database Migration Pipeline Setup | `[P1 - High]` | DevOps | Disable `ddl-auto=update`; write V1–V6 scripts |
| 1.5 | Secrets Management (HashiCorp Vault / AWS Secrets Manager) | `[P1 - High]` | DevOps | Rotate all credentials; inject via env vars |

**Phase 1 Exit Criteria:**
- Zero hardcoded credentials in any properties file
- All HRMS queries return only data matching the authenticated user's `org_id`
- Pay run with `0.5` LOP days produces correct deduction (not rounded to `0`)
- All schema changes tracked in Flyway migration scripts

---

### Phase 2 — Core Enterprise Engines & Asynchronous Queueing
**Target Window: Weeks 7–12**

| Item | Task | Priority | Owner | Notes |
|---|---|---|---|---|
| 2.1 | Pre-Payroll Anomaly AI Agent (AuditBot) | `[P0 - Blocker]` | AI/Backend | Rule-based first; ML scoring in v2 |
| 2.2 | Redis / BullMQ Task Queue for PDF Payslips & Form 16 | `[P1 - High]` | Backend/DevOps | JasperReports or Puppeteer PDF engine |
| 2.3 | Async Event Messaging (Redis Streams) for Leave/LOP Sync | `[P1 - High]` | Backend Lead | Replace synchronous WebClient call |
| 2.4 | Full & Final (F&F) Settlement Engine | `[P1 - High]` | Backend Lead | Gratuity, leave encashment, final payslip |
| 2.5 | Secure S3 Pre-Signed Storage Service | `[P2 - Medium]` | Backend/DevOps | Replace Cloudinary for sensitive documents |

**Phase 2 Exit Criteria:**
- Pay run does not fail if HRMS is offline (uses async LOP snapshot)
- Payslip PDFs auto-generated and emailed within 1 hour of pay run approval
- AuditBot report generated and HITL gate enforced before every pay run

---

### Phase 3 — Codebase Hardening & Refactoring
**Target Window: Weeks 13–18**

| Item | Task | Priority | Owner | Notes |
|---|---|---|---|---|
| 3.1 | Decompose Monolithic Frontend Bundle (`dashboardPage/index.js` >270KB) | `[P1 - High]` | FE Lead | Split into feature modules with lazy loading |
| 3.2 | Deprecate Duplicate Entities (`LeaveRequest` / `Timesheets`) | `[P2 - Medium]` | Backend Lead | Data migration scripts; remove legacy code |
| 3.3 | Conversational ESS AI Agent (Slack / WhatsApp) | `[P1 - High]` | AI Team | Phase 1: Slack; Phase 2: WhatsApp Business API |
| 3.4 | Automated Integration Test Suite (>70% Coverage Target) | `[P1 - High]` | QA / All | JUnit 5 for BE; Jest + Cypress for FE |

**Phase 3 Exit Criteria:**
- Payroll Frontend initial bundle size < 500KB (gzipped)
- Zero duplicate entity classes in either backend service
- Integration tests pass in CI/CD gate on every PR
- ESS Agent responds accurately to top 20 common employee queries

---

### Phase 4 — Direct Banking, High-Load Scale & Production Launch
**Target Window: Weeks 19–24**

| Item | Task | Priority | Owner | Notes |
|---|---|---|---|---|
| 4.1 | Direct Bank Payout API Integration (ICICI / HDFC / Axis) | `[P1 - High]` | Backend Lead | Maker-Checker dual approval model |
| 4.2 | PgBouncer Proxy Pool & PostgreSQL Read Replicas | `[P1 - High]` | DevOps | Transaction pool mode; read replica for reports |
| 4.3 | OCR Tax Proof Verification AI Agent | `[P1 - High]` | AI Team | Google Document AI or AWS Textract |
| 4.4 | Kubernetes HPA, Distributed Tracing (Prometheus/Grafana), Load Testing (K6) | `[P1 - High]` | DevOps | K6 target: 1000 concurrent users, p95 < 500ms |
| 4.5 | Third-Party Penetration Testing & Security Compliance Audit | `[P0 - Blocker]` | Security | OWASP Top 10 + VAPT report required before go-live |

**Phase 4 Exit Criteria:**
- Load test: 1,000 concurrent users with p95 response time < 500ms
- Zero critical or high findings from penetration test (or all mitigated)
- Direct bank payout tested end-to-end in UAT environment
- Full distributed tracing visible in Grafana for all 4 services

---

## Appendix — Technology Stack Reference

| Layer | Technology |
|---|---|
| Auth | Keycloak 26.x (both services) |
| API Protocol | REST (current) → gRPC (future, for inter-service) |
| Message Bus | Redis Streams (MVP) → Apache Kafka (scale) |
| Task Queue | BullMQ (Node workers) / Celery (Python workers) |
| DB Migration | Flyway (Java services) |
| Connection Pool | PgBouncer (self-managed) / RDS Proxy (AWS) |
| Object Storage | AWS S3 / GCP Cloud Storage (SSE-KMS) |
| Container Orchestration | AWS EKS / GCP GKE |
| Monitoring | Prometheus + Grafana + Loki |
| Tracing | OpenTelemetry + Jaeger / Grafana Tempo |
| Load Testing | K6 |
| Security Scanning | OWASP ZAP + Snyk + SonarQube |
| PDF Generation | JasperReports (Java) / Puppeteer (Node) |
| OCR | Google Document AI / AWS Textract |
| LLM / AI | OpenAI GPT-4 / Google Gemini (ESS Agent, AuditBot) |
| Vector DB | Pinecone / pgvector (org_id partitioned) |
| Secrets | HashiCorp Vault / AWS Secrets Manager |
