import http from 'k6/http';
import { check, sleep } from 'k6';

// Configurable test parameters
const targetUrl = __ENV.TARGET_URL || 'http://localhost:8080';
const keycloakUrl = __ENV.KEYCLOAK_URL || 'http://localhost:8081';
const vus = parseInt(__ENV.VUS || '100', 10);
const duration = __ENV.DURATION || '1m';

export const options = {
  stages: [
    { duration: '10s', target: vus }, // Ramp up to 100 VUs
    { duration: '40s', target: vus }, // Sustain 100 VUs
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    // Platform SLA: error rate < 0.1% under 100 concurrent users
    http_req_failed: ['rate<0.001'],
    // Platform SLA: p95 latency under 500ms, p99 under 1500ms
    http_req_duration: ['p(95)<500', 'p(99)<1500'],
  },
};

// Setup: Mint or obtain token once per test run
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
      `Keycloak authentication failed (status: ${res.status}). Verify Keycloak is running at ${keycloakUrl} and dev-realm is imported. Response: ${res.body}`
    );
  }

  const body = JSON.parse(res.body);
  if (!body.access_token) {
    throw new Error('Keycloak authentication response did not contain an access_token');
  }

  return { token: body.access_token };
}

export default function (data) {
  if (!data || !data.token) {
    throw new Error('Load test execution halted: valid authentication token is required');
  }

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${data.token}`,
    },
  };

  // 1. Health endpoint probe
  const healthRes = http.get(`${targetUrl}/actuator/health`, { timeout: '3s' });
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });

  // 2. Profile / User identity endpoint (authenticated)
  const meRes = http.get(`${targetUrl}/api/v1/me`, params);
  check(meRes, {
    'me endpoint status is 200': (r) => r.status === 200,
    'me returns correct tenant': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body && body.tenantId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  // 3. Org & Department listing query (tests database indexing & HikariCP pool under load)
  const deptRes = http.get(`${targetUrl}/api/v1/departments`, params);
  check(deptRes, {
    'departments listing status is 200': (r) => r.status === 200,
  });

  // 4. Employee profile query if test employee ID is specified
  const employeeId = __ENV.TEST_EMPLOYEE_ID;
  if (employeeId) {
    const empRes = http.get(`${targetUrl}/api/v1/employees/${employeeId}`, params);
    check(empRes, {
      'employee profile lookup status is 200': (r) => r.status === 200,
    });
  }

  // Pacing: simulate realistic user click gap (100ms - 500ms)
  sleep(0.2);
}
