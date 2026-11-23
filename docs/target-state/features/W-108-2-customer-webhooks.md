# Feature: Customer Webhooks & Event Dispatcher

| Field | Value |
|---|---|
| **Feature ID** | `W-108.2` · `PLAT-WEBHOOK` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/frontend` (`src/core/developer`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for founder review |
| **Blocked by** | `W-108.1` (Customer API) |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `core/V202__customer_webhooks.sql` | 1 |
| Externally testable behaviour | Subscribes external webhook URLs to lifecycle events, dispatches payloads signed with HMAC-SHA256, and retries failed deliveries | 1 |
| Frontend area | `core/developer` | 1 |

---

## 2. Problem & Context

Currently, external systems must continuously poll Infinevo endpoints to detect changes (e.g. newly added employees, approved leaves, completed pay runs). There is no event-driven push architecture (webhooks) to notify customer applications in real-time.

---

## 3. Scope

**In scope:**
- PostgreSQL tables `core.webhook_subscription` and `core.webhook_delivery_log` with RLS.
- Supported event types:
  - `employee.created`, `employee.updated`, `employee.terminated`
  - `leave.applied`, `leave.approved`, `leave.rejected`
  - `timesheet.approved`
  - `payrun.paid`
- HMAC SHA-256 payload signing:
  - Each webhook request sends header: `X-Infinevo-Signature: t={timestamp},v1={hex_signature}`
  - Signature computed over `{timestamp}.{payload_json}` using the subscription secret.
- Async delivery & retry engine (on `worker` module):
  - Retries up to 5 times with exponential backoff on HTTP 4xx/5xx or network timeout.
  - Automatically deactivates subscription after 50 consecutive delivery failures.
- Frontend screen: `/settings/developer/webhooks` to create webhook subscriptions, view delivery logs, and send test ping events.

**Out of scope:**
- Inbound webhooks from third-party systems.

---

## 4. Flow

```
Platform Event (e.g. Leave Approved) --> ApplicationEventPublisher
                                    --> WebhookEventDispatcher
                                    --> Enqueues WebhookDeliveryTask in core.webhook_delivery_log
Worker Process                      --> Picks up task --> Signs payload with HMAC-SHA256
                                    --> HTTP POST to subscriber targetUrl
                                    --> Logs status code & response time; retries on failure
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V202__customer_webhooks.sql` | Create `core.webhook_subscription` and `core.webhook_delivery_log` with RLS | yes | yes |

### Table: `core.webhook_subscription`
```sql
CREATE TABLE core.webhook_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(100) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    secret_key VARCHAR(64) NOT NULL,
    event_types TEXT[] NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_webhook_sub_tenant ON core.webhook_subscription (tenant_id, is_active);

ALTER TABLE core.webhook_subscription ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.webhook_subscription
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

### Table: `core.webhook_delivery_log`
```sql
CREATE TABLE core.webhook_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    subscription_id UUID NOT NULL REFERENCES core.webhook_subscription(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    http_status_code INTEGER NULL,
    response_body TEXT NULL,
    delivered_at TIMESTAMPTZ NULL,
    next_retry_at TIMESTAMPTZ NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'RETRYING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_delivery_retry ON core.webhook_delivery_log (status, next_retry_at)
    WHERE status = 'RETRYING';

ALTER TABLE core.webhook_delivery_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.webhook_delivery_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

---

## 5a. Webhook Security & SSRF Protection

1. **SSRF Guard**:
   - `target_url` must strictly use scheme `https://` (HTTP rejected).
   - DNS resolution must be performed prior to dispatch and validated against blocked ranges:
     - Loopback (`127.0.0.0/8`, `::1`)
     - RFC 1918 Private networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
     - Link-Local and Cloud Metadata (`169.254.169.254`, `fe80::/10`)
   - Any URL resolving to a private or internal network is refused at subscription creation time and delivery time with `SSRF_ATTEMPT_DETECTED`.
2. **Signature & Replay Prevention**:
   - Header sent: `X-Infinevo-Signature: t={timestamp_epoch_ms},v1={hex_hmac_sha256}`.
   - Signature computed as `HMAC-SHA256(secret_key, timestamp + "." + payload_json)`.
   - Consumers verify timestamp is within $\pm 300$ seconds (5 minutes) to block replay attacks.
3. **Permissions**: Management requires `core.webhook.manage`; viewing requires `core.webhook.read`.

---

## 6. Verification

```bash
mvnw clean test -pl migration,core -am -Dtest="*Webhook*"
```
- Integration test `WebhookDeliveryIT`: verifies event dispatching, HMAC-SHA256 signature correctness, SSRF rejection of private IP addresses, retry scheduling on HTTP 500 mock response, and automatic deactivation upon reaching consecutive failure limit.
- Integration test `WebhookDeliveryIT`: verifies event dispatching, HMAC-SHA256 signature correctness, retry scheduling on HTTP 500 mock response, and automatic deactivation upon reaching consecutive failure limit.
