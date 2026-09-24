# W-63 — Load Testing & Baseline

> Specification for platform load testing and performance baselines.
> Prepared according to `TEMPLATE-INFRA.md`.

| Field | Value |
|---|---|
| **Work item** | `W-63` · issue [#83](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/83) |
| **Kind** | Infra |
| **Stream / track** | Stream H — Security, operations & go-to-market |
| **Wave** | Wave 8 — Completion and hardening |
| **Size / skill** | S — INFRA |
| **Owner** | |
| **Blocked by** | `W-54` ([#74](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/74)) |
| **Blocks** | Go-to-market readiness |
| **Capabilities** | `PLAT-14` |
| **Decisions** | `07-decisions.md:129` (`PLAT-14` downgrade), `D-18` (Central India region), `D-50` (Azure Storage Queue), `W-55` (Hikari pool limits) |
| **Gaps addressed** | `DEBT-019` (N+1 query loop regression guard) |
| **Status** | **Approved** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-24 |

> Hard rule 1: no code is written until this spec is approved. (Approved 2026-09-24)

---

## 1. Problem

The platform currently lacks an automated, repeatable load testing suite and a recorded performance baseline. Without them, changes to Hibernate mappings, queries, or container resources cannot be evaluated for performance regressions prior to go-live.

Per founder decision (`07-decisions.md:129`), full-scale enterprise stress testing is deliberately downgraded to match target scale:
> *"PLAT-14 load testing: Prove capacity at scale — Downgrade. A 100-employee pay run and ~100 concurrent users is a modest target."*

### Current Baseline

| Command | Exit | Output |
|---|---|---|
| `ls -d infra/load-test 2>/dev/null` | 1 | No test suite directory exists |
| `docker compose -f infra/docker/compose.yml ps` | 0 | Running local stack, but zero load test traffic applied |

---

## 2. Scope

### In scope
- A lightweight, containerized load test harness using **k6** in `infra/load-test/`.
- Two core traffic scenarios:
  1. **Concurrent Users Scenario**: 100 virtual users (VUs) simulating portal navigation (authentication token exchange, `/actuator/health`, `/api/v1/me`, `/api/v1/departments`, and employee profile query).
  2. **Pay Run Queue Scenario**: Enqueuing and processing a 100-employee pay run job via the asynchronous queue pipeline:
     `Azure Storage Queue ('payrun') → PayrunQueueListener → core.job_status ('COMPLETED')`.
- Automated threshold assertions:
  - Error rate: < 0.1% (HTTP 5xx or unhandled exceptions).
  - Web API latency: p95 < 500ms, p99 < 1500ms under 100 concurrent VUs.
  - Pay run batch completion time recorded for 100 employees with SLA < 60 seconds (`payrun_batch_duration_ms`).
  - Strict verification that the pay run job reaches `COMPLETED` state in `core.job_status`.
- Markdown baseline output report generator comparing results against thresholds.

### Out of scope
- High-scale distributed testing (>1,000 VUs) or multi-region testing (prohibited by `07-decisions.md:129`).
- Commercial cloud testing SaaS integrations (e.g. k6 Cloud, BlazeMeter).
- Complex payroll calculation algorithms (owned by `W-33` / `W-34`; `W-63` tests batch ingestion and queue processing throughput).

---

## 3. What gets built

```
infra/load-test/
├── Dockerfile                  # Lightweight container packaging k6 and scenarios
├── README.md                   # Execution runbook and baseline thresholds
├── run.sh                      # Shell wrapper for local or CI execution
└── scenarios/
    ├── concurrent-users.js     # 100 virtual users browsing API & profile endpoints
    └── payrun-batch.js         # Concurrent queue submission for 100-employee pay run
```

### Exact files

| File | Change |
|---|---|
| `infra/load-test/Dockerfile` | New container image definition based on `grafana/k6:latest` |
| `infra/load-test/run.sh` | Shell runner script passing target host, credentials, and scenario duration |
| `infra/load-test/scenarios/concurrent-users.js` | k6 JavaScript test script defining the 100 VU ramp-up and endpoint calls |
| `infra/load-test/scenarios/payrun-batch.js` | k6 script generating 100-employee pay run queue messages |
| `infra/load-test/README.md` | Runbook, target thresholds, and recorded baseline table |

### What is NOT touched
- Application production code in `code/backend/` and `code/frontend/`.
- Existing database migration scripts in `code/backend/migration/`.
- Azure Bicep templates in `infra/azure/`.

---

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Temporarily reduce web connection pool `maximum-pool-size: 1` in `application.yml` | `run.sh` fails with HTTP 500 or timeout error rate threshold violation (> 0.1%) |
| 2 | Force endpoint latency delay > 2000ms | k6 threshold assertion fails: `http_req_duration p(95) < 500` breached |
| 3 | Target invalid/unauthenticated endpoint | Authentication check fails and test runner exits non-zero |
| 4 | Force payrun batch processing delay > 60s | k6 threshold assertion fails: `payrun_batch_duration_ms p(95) < 60000` breached |

---

## 5. Verification

Exact verification commands runnable on a machine with Docker Compose:

```bash
# 1. Start local stack
docker compose -f infra/docker/compose.yml up -d

# 2. Build the load test container
docker build -t infinevo-loadtest:test infra/load-test

# 3. Run the 100-user concurrent scenario against local stack
docker run --rm --network host infinevo-loadtest:test run-scenario concurrent-users --vus 100 --duration 1m

# 4. Run the payrun batch scenario
docker run --rm --network host infinevo-loadtest:test run-scenario payrun-batch --employees 100

# 5. Run the full regression test suite
docker run --rm --network host infinevo-loadtest:test run-scenario regression
```

| Check | Type | Target / Expected | Verification Status |
|---|---|---|---|
| HTTP 5xx errors (`http_req_failed`) | Contractual SLA | < 0.1% | Verified via k6 threshold |
| p95 latency (`http_req_duration`) | Contractual SLA | < 500ms | Verified via k6 threshold |
| p99 latency (`http_req_duration`) | Contractual SLA | < 1500ms | Verified via k6 threshold |
| 100-employee pay run batch SLA | Contractual SLA | < 60,000ms | Verified via k6 threshold |
| 100-employee pay run status | Contractual SLA | `COMPLETED` in `core.job_status` | Verified via k6 check assertion |
| Median latency p50 (`http_req_duration`) | Informational Metric | None (Recorded metric) | Measured and reported by k6 |
| Sustained Throughput (`req/s`) | Informational Metric | None (Recorded metric) | Measured and reported by k6 |

---

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-019` (N+1 query loops) | Guarded. The p95 < 500ms threshold under 100 concurrent users prevents un-batched query loops from going unnoticed. |

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Test execution overwhelms local developer workstation | Medium | Default scenario duration capped at 60s; burst ramp-up gradual (10s). |
| Virtual users blocked by Keycloak rate limits | Low | Service realm client configured with standard dev bearer token reuse during the run. |

---

## 8. Rollback

- Revert directory `infra/load-test/`.
- No database schemas, migrations, or cloud resources are altered by this test suite.

---

## 9. Done when

1. `infra/load-test/` contains working, linted k6 scripts for 100 concurrent users and 100-employee pay run.
2. `docker build -t infinevo-loadtest:test infra/load-test` succeeds.
3. Test suite runs against local Docker stack and records p50, p95, p99, throughput, and error rates into `infra/load-test/README.md`.
4. Automated thresholds enforce < 0.1% error rate, < 500ms p95 latency, and < 60s payrun batch completion.

---

## Decisions needed before implementation

**1. Load testing tool selection**
- **(a)** `k6` (Recommended). Distributed as a single binary/Docker container, native ES6 JavaScript test scripts, lightweight memory footprint, native terminal and JSON reporting.
- **(b)** `Locust`. Python-based, requires Python runtime and pip dependencies.
- **Recommend (a)** because `k6` runs without installing a Python environment and integrates directly into CI.

**2. Authentication in load test scenarios**
- **(a)** Service account token caching (Recommended). Authenticate once per VU and reuse bearer token across requests with periodic refresh.
- **(b)** Authenticate on every single request.
- **Recommend (a)** to test platform API and database performance rather than artificially saturating Keycloak.
