import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import encoding from 'k6/encoding';

// Configurable test parameters
const targetUrl = __ENV.TARGET_URL || 'http://localhost:8080';
const workerUrl = __ENV.WORKER_URL || 'http://localhost:8082';
const keycloakUrl = __ENV.KEYCLOAK_URL || 'http://localhost:8081';
const employeeCount = parseInt(__ENV.EMPLOYEE_COUNT || '100', 10);

// Performance metrics
const payrunDurationTrend = new Trend('payrun_batch_duration_ms', true);
const payrunCompletedCount = new Counter('payrun_batch_completed_count');

export const options = {
  scenarios: {
    payrun_batch_submission: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    // 0% HTTP failure threshold across all requests
    http_req_failed: ['rate<0.001'],
    // Pay run batch completion SLA: 100-employee batch must complete within 60s
    payrun_batch_duration_ms: ['p(95)<60000'],
  },
};

// Setup: Mint or obtain authentication token once before test run
export function setup() {
  if (__ENV.AUTH_TOKEN) {
    return { token: __ENV.AUTH_TOKEN };
  }

  const tokenUrl = `${keycloakUrl}/realms/infinevo/protocol/openid-connect/token`;
  const payload = {
    grant_type: 'password',
    client_id: __ENV.KEYCLOAK_CLIENT_ID || 'infinevo-web',
    username: __ENV.TEST_USERNAME || 'admin.acme',
    password: __ENV.TEST_PASSWORD || 'local_dev_pw',
  };

  const res = http.post(tokenUrl, payload, { timeout: '10s' });
  if (res.status !== 200) {
    throw new Error(
      `Keycloak authentication failed with HTTP ${res.status}. Verify Keycloak at ${keycloakUrl}. Response: ${res.body}`
    );
  }

  const body = JSON.parse(res.body);
  if (!body.access_token) {
    throw new Error('Keycloak authentication response missing access_token');
  }

  return { token: body.access_token };
}

export default function (data) {
  if (!data || !data.token) {
    throw new Error('Load test execution halted: valid authentication token is required');
  }

  const tenantId = __ENV.TENANT_ID || '11111111-1111-1111-1111-111111111111';
  const jobId = `payrun-loadtest-${Date.now()}`;
  const queueName = 'payrun';

  const authHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': `Bearer ${data.token}`,
    'X-Tenant-ID': tenantId,
  };

  // 1. Worker Health Check
  const workerHealth = http.get(`${workerUrl}/actuator/health`, { timeout: '3s' });
  const workerOk = check(workerHealth, {
    'worker service is healthy': (r) => r.status === 200,
  });
  if (!workerOk) {
    fail(`Worker health check failed at ${workerUrl}/actuator/health (HTTP ${workerHealth.status})`);
  }

  // 2. Submit 100-Employee Pay Run Batch
  // Supports either Azure Storage Queue / Azurite REST API dispatch, or Platform REST API dispatch.
  const startTime = Date.now();
  const queueStorageUrl = __ENV.AZURE_QUEUE_URL || __ENV.QUEUE_URL;

  if (queueStorageUrl) {
    // Direct submission to Azure Storage Queue (or Azurite locally)
    const queueEnvelope = {
      messageId: `msg-${jobId}`,
      jobId: jobId,
      tenantId: tenantId,
      queueName: queueName,
      correlationId: `corr-${jobId}`,
      enqueuedAt: new Date().toISOString(),
      retryCount: 0,
      payload: JSON.stringify({
        batchSize: employeeCount,
        action: 'CALCULATE_PAYRUN',
        timestamp: new Date().toISOString(),
      }),
    };
    const base64Body = encoding.b64encode(JSON.stringify(queueEnvelope));
    const xmlMessage = `<QueueMessage><MessageText>${base64Body}</MessageText></QueueMessage>`;

    const queueRes = http.post(
      `${queueStorageUrl}/${queueName}/messages`,
      xmlMessage,
      {
        headers: { 'Content-Type': 'application/xml' },
        timeout: '10s',
      }
    );

    const queueCheck = check(queueRes, {
      'message accepted by Azure Storage Queue (HTTP 201)': (r) => r.status === 201,
    });
    if (!queueCheck) {
      fail(`Failed to enqueue pay run to Azure Storage Queue: HTTP ${queueRes.status} ${queueRes.body}`);
    }
  } else {
    // Standard Platform API submission (strictly requires HTTP 200 or 202; 404 is a failure)
    const apiPayload = JSON.stringify({
      jobId: jobId,
      tenantId: tenantId,
      employeeCount: employeeCount,
      action: 'CALCULATE_PAYRUN',
    });

    const submitUrl = `${targetUrl}/api/v1/payruns`;
    const submitRes = http.post(submitUrl, apiPayload, {
      headers: authHeaders,
      timeout: '10s',
    });

    const submitCheck = check(submitRes, {
      'payrun submission accepted (HTTP 200 or 202)': (r) => r.status === 200 || r.status === 202,
    });
    if (!submitCheck) {
      fail(`Pay run submission failed at ${submitUrl}: HTTP ${submitRes.status} (expected 200 or 202)`);
    }
  }

  // 3. Poll core.job_status via GET /api/v1/jobs/{jobId} until COMPLETED
  let isCompleted = false;
  let pollAttempts = 0;
  const maxAttempts = parseInt(__ENV.MAX_POLL_ATTEMPTS || '60', 10);
  const pollIntervalSeconds = 1;

  while (pollAttempts < maxAttempts) {
    pollAttempts++;
    sleep(pollIntervalSeconds);

    const pollRes = http.get(`${targetUrl}/api/v1/jobs/${jobId}`, {
      headers: authHeaders,
      timeout: '5s',
    });

    // Hard check: status endpoint must return 200 OK
    const pollStatusOk = check(pollRes, {
      'job status lookup returned 200': (r) => r.status === 200,
    });

    if (!pollStatusOk) {
      continue;
    }

    try {
      const jobData = JSON.parse(pollRes.body);
      if (jobData.status === 'COMPLETED') {
        isCompleted = true;
        break;
      }
      if (jobData.status === 'FAILED') {
        fail(`Pay run job ${jobId} failed in core.job_status: ${jobData.errorMessage}`);
      }
    } catch (e) {
      // Ignore transient parsing issue during poll interval
    }
  }

  // 4. Assert completion in core.job_status and record duration
  const completionVerified = check(null, {
    'payrun job completed in core.job_status within SLA': () => isCompleted === true,
  });

  if (!completionVerified) {
    fail(`Pay run job ${jobId} did not reach COMPLETED state within ${maxAttempts}s timeout`);
  }

  const durationMs = Date.now() - startTime;
  payrunDurationTrend.add(durationMs);
  payrunCompletedCount.add(1);
}
