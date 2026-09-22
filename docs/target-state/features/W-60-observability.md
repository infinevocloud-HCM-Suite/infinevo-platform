# W-60 — Observability

> Structured logging, distributed tracing, metrics, health endpoints, and Azure operational dashboards (`PLAT-07`).
> Derived from `TEMPLATE-INFRA.md`.

| Field | Value |
|---|---|
| **Work item** | `W-60` · issue [#80](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/80) |
| **Kind** | Infra |
| **Stream / track** | Stream H — Security, operations & go-to-market · Track I |
| **Wave** | Wave 3 — Identity and tenancy |
| **Size / skill** | M · INFRA |
| **Owner** | BirenGit |
| **Blocked by** | `W-50` ([#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70)) |
| **Blocks** | `W-61` ([#81](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/81) — Alerting) |
| **Capabilities** | `PLAT-07` — structured logging, tracing, metrics, health endpoints, dashboards |
| **Decisions** | `D-02` (Worker on same image, isolated role) · `D-10` (Azure Container Apps) · `D-18` (India region) · `D-19` (10 × 100 scale) · `D-48` (Unified backend image) · `D-50` (Azure Storage Queue) · `D-56` (Tenant isolation) |
| **Gaps addressed** | Incident 3 (Token written to logs — prevent sensitive leaks in logs); `DEBT-018` |
| **Status** | **Approved** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-22 |

> **Hard rule 1:** No code is written until this spec is approved by the founder. (Approved 2026-09-22)

---

## 1. Problem

Today, backend logging produces unstructured standard console text without tenant identification or request correlation:
1. **No Correlation Across Boundaries:** HTTP requests handled by `app` cannot be linked to asynchronous jobs executed by `worker` or subsequent Postgres queries. There is no `X-Correlation-ID` filter or W3C `traceparent` propagation.
2. **Missing MDC Context:** `code/backend` contains zero references to SLF4J MDC. When log statements execute, they omit `tenant_id`, `correlation_id`, `user_id`, and `role`.
3. **No Metrics Instrumentation:** Spring Boot Actuator exposes only `health,info`. The Micrometer Prometheus registry is not included, leaving Container Apps with no scrapeable endpoint for JVM memory, thread pool exhaustion, or Hikari connection pool saturation.
4. **Missing Azure Telemetry:** `infra/azure` provisions Log Analytics (`law-infinevo-shared`), but lacks an Application Insights component and an Azure Dashboard/Workbook for production monitoring.

### Baseline

| Command | Exit | Output |
|---|---|---|
| `git grep "MDC" code/backend` | 1 | (0 matches — no MDC context tracking) |
| `curl -s http://localhost:8080/actuator/prometheus` | 404 | Not Found (No Prometheus metrics endpoint) |
| `az bicep build --file infra/azure/modules/appinsights.bicep` | 1 | File does not exist |

---

## 2. Scope

### In scope
- **Correlation ID & MDC Filter:** Servlet filter in `code/backend/shared` extracting or generating `X-Correlation-ID` and binding `tenant_id`, `correlation_id`, and `user_id` to SLF4J MDC with strict `finally` cleanup.
- **Queue Trace Context Propagation:** Enriching `QueueMessage` in `com.infinevo.core.queue` with `correlationId` so background worker jobs log under the originating request's correlation context.
- **Structured JSON Logging:** Production logging configuration using Logback JSON encoder formatting logs with ISO-8601 timestamps, severity, logger name, thread, `tenant_id`, `correlation_id`, and masked sensitive data.
- **Actuator & Metrics:** Exposing `/actuator/prometheus`, enabling JVM, HikariCP, and HTTP request metrics.
- **Health Probes:** Liveness and readiness endpoints with database and queue reachability checks.
- **Azure Monitoring Bicep:** `appinsights.bicep` (workspace-based Application Insights instance) and `dashboard.bicep` (Azure Monitor workbook/dashboard tracking 5xx rates, latency, and container CPU/memory).

### Out of scope
- **Alerting Rules & Pager Routing:** Owned by `W-61` (`PLAT-08`).
- **Distributed Tracing Collector (Jaeger / Tempo):** Out of scope; Container Apps and App Insights ingest native W3C trace contexts directly via stdout and Log Analytics.

---

## 3. What Gets Built

### Architecture & Context Flow

```
[ Client Request ] 
       │ (X-Correlation-ID or generated UUID)
       ▼
[ app.jar: CorrelationIdFilter ] ──► Populates SLF4J MDC (tenant_id, correlation_id)
       │
       ├──► Dispatches Background Job via AzureStorageQueueProducer (attaches correlationId)
       │                                     │
       │                                     ▼
       │                            [ Azure Storage Queue ]
       │                                     │
       │                                     ▼
       │                            [ worker.jar: PayrunQueueListener ]
       │                                     │
       │                                     └──► Binds correlationId to MDC & logs execution
       ▼
[ JSON Formatted Console Logs ] ──► [ Azure Container Apps stdout ] ──► [ Log Analytics / App Insights ]
```

### Files Changed & Created

| File | Change | Purpose |
|---|---|---|
| `code/backend/pom.xml` | Modify | Add versions for `logstash-logback-encoder` and `micrometer-registry-prometheus` |
| `code/backend/shared/pom.xml` | Modify | Add `micrometer-registry-prometheus`, `logstash-logback-encoder` dependencies |
| `code/backend/shared/src/main/java/com/infinevo/shared/logging/CorrelationIdFilter.java` | New | HTTP filter managing `X-Correlation-ID` request/response headers and MDC binding |
| `code/backend/shared/src/main/java/com/infinevo/shared/logging/MdcLoggingContext.java` | New | Helper for safe MDC population and guaranteed cleanup in try-with-resources |
| `code/backend/core/src/main/java/com/infinevo/core/queue/QueueMessage.java` | Modify | Add `correlationId` field to queue message envelope |
| `code/backend/worker/src/main/java/com/infinevo/worker/listener/PayrunQueueListener.java` | Modify | Bind `correlationId` from message to MDC during queue consumption |
| `code/backend/app/src/main/resources/application.yml` | Modify | Expose `prometheus` endpoint; configure structured logback format |
| `code/backend/worker/src/main/resources/application.yml` | Modify | Expose `prometheus` endpoint; configure structured logback format |
| `infra/azure/modules/appinsights.bicep` | New | Workspace-based Application Insights resource linked to `law-infinevo-shared` |
| `infra/azure/modules/dashboard.bicep` | New | Azure Monitor workbook monitoring HTTP latency, error rates, and CPU/memory |
| `infra/azure/main.bicep` | Modify | Wire `appinsights` and `dashboard` modules |

### What Is Not Touched
- Database schema (`code/backend/migration`): No SQL tables or migrations needed.
- Domain modules (`hrms`, `payroll`): Zero changes to business logic or entity models.
- Security authentication filter logic: `TenantContextFilter` remains strictly responsible for tenancy validation; `CorrelationIdFilter` executes upstream.

---

## 4. Proving It (Negative Testing)

| # | Deliberate Break | What Must Happen |
|---|---|---|
| 1 | Incoming request without `X-Correlation-ID` header | Filter must automatically generate a new UUID, set it in MDC, and return it in `X-Correlation-ID` response header. |
| 2 | Thread pool reuse in web container or worker | MDC must be completely empty after request/job completion; subsequent request on same thread must never inherit stale `tenant_id`. |
| 3 | Malformed or oversized payload in `QueueMessage` | `PayloadTooLargeException` thrown before message is published; correlation ID preserved in error log. |
| 4 | Database becomes unreachable | `/actuator/health/readiness` must return HTTP 503 `OUT_OF_SERVICE`. |

---

## 5. Verification

Run from repository root:

```bash
# 1. Verify build, dependencies, and formatting
cd code/backend
./mvnw clean compile -DskipTests
./mvnw spotless:check

# 2. Run unit and integration tests for correlation and metrics
./mvnw test -pl :shared,:core,:app,:worker

# 3. Validate Azure Bicep modules
az bicep build --file infra/azure/modules/appinsights.bicep
az bicep build --file infra/azure/modules/dashboard.bicep
az bicep build --file infra/azure/main.bicep
```

| Check | Expected |
|---|---|
| `CorrelationIdFilterTest` | Passes: verifies header propagation, UUID generation, and MDC cleanup |
| `QueueMessageTest` | Passes: verifies `correlationId` JSON serialization within 48 KB limit |
| `ActuatorEndpointIT` | Passes: `/actuator/prometheus` returns HTTP 200 with standard JVM metrics |
| `az bicep build` | Exit 0 with valid ARM JSON templates generated |

---

## 6. Gap Disposition & Standing Rules

### Gap Disposition

| Gap / Incident | Disposition |
|---|---|
| **Incident 3 (Token logged)** | **Fixed:** Logback masking pattern strips bearer tokens, passwords, and sensitive query params from structured JSON logs. |
| **`DEBT-018`** | **Discounted:** W-60 introduces zero database tables; no missing indexes or schema changes. |

### Standing Rules Impact

- **Tenant Isolation:** MDC explicitly tags `tenant_id` on every log line, matching `TenantContext`.
- **Database Migrations:** Creates no tables or migrations.
- **Module Boundaries:** All logging and MDC utilities reside in `shared`; no cross-module dependencies introduced.
- **Sensitive Data Redaction:** Masking rules configured in Logback prevent logging of bearer tokens, passwords, or signed URLs.

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **MDC Context Leakage Across Threads** | Medium | Strict `try-finally` block in `CorrelationIdFilter` and worker listeners ensuring `MDC.clear()` executes unconditionally. |
| **High Log Volume / Ingestion Costs** | Low | Default log level set to `INFO`; debug logs suppressed in production. Log Analytics 30-day retention (`D-18`). |
| **Queue Serialization Drift** | Low | `QueueMessage` maintains `@JsonProperty` annotations with backwards compatibility for existing payload fields. |

---

## 8. Rollback

If issues occur post-merge:
1. **Application Code:** Revert the Git merge commit.
2. **Infrastructure:** Redeploy `infra/azure/main.bicep` without `appinsights` and `dashboard` modules; Log Analytics workspace remains intact.
3. **No Database Rollback Required:** W-60 creates no database objects, sequences, or migrations.

---

## 9. Done When

1. HTTP requests to `app` return an `X-Correlation-ID` header, and console logs output valid JSON containing `timestamp`, `level`, `tenant_id`, `correlation_id`, and `message`.
2. A background job dispatched via `QueueProducer` carries `correlationId`, which is logged by `PayrunQueueListener` in `worker` under the same correlation ID.
3. Thread pool reuse is verified clean: no subsequent request inherits an uncleared `tenant_id` from MDC.
4. `curl -s http://localhost:8080/actuator/prometheus` returns HTTP 200 with scrapeable JVM and Hikari connection pool metrics.
5. `/actuator/health/readiness` returns HTTP 200 when healthy, and HTTP 503 when the database connection is interrupted.
6. Azure Bicep templates `infra/azure/modules/appinsights.bicep` and `infra/azure/modules/dashboard.bicep` compile with zero lint errors via `az bicep build`.

---

## Decisions Needed Before Implementation

**1. Structured Logging JSON Library**
- **(a) `logstash-logback-encoder` (Recommended):** Standard, highly mature, native support for nested MDC fields and sensitive data masking patterns.
- **(b) Spring Boot 3.4 standard JSON logging:** Built-in but offers less flexible custom field renaming and masking configuration.
- **Recommendation:** Option (a) for out-of-the-box Azure Log Analytics ingestion compatibility.

