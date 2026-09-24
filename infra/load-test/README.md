# Platform Load Testing (PLAT-14 / W-63)

This directory contains the repeatable load testing suite and performance baseline definition for the Infinevo HCM Platform.

Per founder decision (`docs/target-state/07-decisions.md:129`), full-scale enterprise load testing is deliberately sized to the operational scale:
> *"PLAT-14 load testing: Prove capacity at scale — Downgrade. A 100-employee pay run and ~100 concurrent users is a modest target."*

---

## 1. Scenarios

1. **`concurrent-users.js`**:
   - Simulates 100 concurrent virtual users (VUs) browsing platform endpoints over a 60-second ramp and sustain cycle.
   - Navigates `/actuator/health`, `/api/v1/me`, `/api/v1/departments` (database indexing / pool test), and employee profile query with cached bearer token authentication.
   - Enforces p95 latency < 500ms, p99 < 1500ms, and error rate < 0.1%.

2. **`payrun-batch.js`**:
   - Simulates 100-employee batch pay run execution through the asynchronous queue pipeline:
     `Azure Storage Queue ('payrun') → PayrunQueueListener → core.job_status ('COMPLETED')`
   - Dispatches a standard `QueueMessage<String>` envelope to Azure Storage Queue (or Azurite locally at `/${queueName}/messages`).
   - Polls `GET /api/v1/jobs/{jobId}` until the job record reaches `COMPLETED` status with 100% progress.
   - Enforces a 100-employee batch processing completion SLA of `< 60 seconds`.

3. **`regression`**:
   - Executes both `concurrent-users` and `payrun-batch` sequentially as an end-to-end regression validation run.

---

## 2. Usage

### Local Execution (Docker)

```bash
# 1. Start the local stack
docker compose -f infra/docker/compose.yml up -d

# 2. Build the load test container
docker build -t infinevo-loadtest:latest infra/load-test

# 3. Run the concurrent users scenario
docker run --rm --network host infinevo-loadtest:latest concurrent-users

# 4. Run the payrun batch scenario
docker run --rm --network host infinevo-loadtest:latest payrun-batch

# 5. Run the full regression test suite
docker run --rm --network host infinevo-loadtest:latest regression
```

### Direct CLI Execution (if k6 is installed locally)

```bash
k6 run infra/load-test/scenarios/concurrent-users.js
k6 run infra/load-test/scenarios/payrun-batch.js
```

### Execution Against Cloud Environment (Azure dev)

```bash
# 1. Run 100 concurrent users against web endpoints
docker run --rm \
  -e TARGET_URL="https://app.infinevo.dev" \
  -e KEYCLOAK_URL="https://auth.infinevo.dev" \
  infinevo-loadtest:latest concurrent-users

# 2. Run payrun batch against worker and Azure Storage Queue
docker run --rm \
  -e TARGET_URL="https://app.infinevo.dev" \
  -e WORKER_URL="https://worker.infinevo.dev" \
  -e KEYCLOAK_URL="https://auth.infinevo.dev" \
  -e AZURE_QUEUE_URL="https://<storage-account>.queue.core.windows.net" \
  infinevo-loadtest:latest payrun-batch
```

---

## 3. Performance Baseline & Thresholds

Target thresholds and recorded metrics for a **100-employee pay run plus 100 concurrent users**:

| Metric | Type | Target Threshold / SLA | Baseline Result |
|---|---|---|---|
| HTTP Error Rate (`http_req_failed`) | Contractual SLA | `< 0.1%` (0 HTTP 5xx) | Recorded upon run |
| p95 Response Time (`http_req_duration`) | Contractual SLA | `< 500 ms` | Recorded upon run |
| p99 Response Time (`http_req_duration`) | Contractual SLA | `< 1500 ms` | Recorded upon run |
| 100-Employee Payrun Batch (`payrun_batch_duration_ms`) | Contractual SLA | `< 60,000 ms` | Recorded upon run |
| Payrun Batch Job Verification | Contractual SLA | `COMPLETED` in `core.job_status` | Recorded upon run |
| Median Latency p50 (`http_req_duration`) | Informational Metric | None (Recorded metric) | Recorded upon run |
| Sustained Throughput (`req/s`) | Informational Metric | None (Recorded metric) | Recorded upon run |

---

## 4. Deliberate Breaks (Verification)

1. **Pool Starvation Break**: Set `maximum-pool-size: 1` in `application.yml` and run `concurrent-users`. Expected: test fails with timeout and `http_req_failed` threshold violation (> 0.1%).
2. **Latency Injection Break**: Add artificial sleep on test target. Expected: test fails `http_req_duration p(95) < 500ms`.
3. **Invalid Auth Break**: Pass invalid credentials or expired token. Expected: assertion failure on 401 unauthenticated.
