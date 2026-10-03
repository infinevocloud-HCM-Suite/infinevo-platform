import { describe, it, expect, vi, beforeEach } from 'vitest';
import fs from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiClient } from '@shared/api/client.js';
import { tenantService } from './tenantService.js';
import { impersonationService } from './impersonationService.js';
import { auditService } from './auditService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const __dirname = dirname(fileURLToPath(import.meta.url));

describe('tenantService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('list GETs /v1/tenants and returns the bare array', async () => {
    const rows = [{ tenant_id: 't-1', name: 'Acme' }];
    apiClient.get.mockResolvedValueOnce({ data: rows });
    expect(await tenantService.list()).toEqual(rows);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/tenants', { params: undefined });
  });

  it('get GETs /v1/tenants/{id}', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { tenant_id: 't-1' } });
    expect(await tenantService.get('t-1')).toEqual({ tenant_id: 't-1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/tenants/t-1');
  });

  it('create POSTs /v1/tenants with the body', async () => {
    const body = { name: 'Initech', country_code: 'IN' };
    apiClient.post.mockResolvedValueOnce({ data: { tenantId: 't-9' } });
    expect(await tenantService.create(body)).toEqual({ tenantId: 't-9' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/tenants', body);
  });

  it('subscription GETs /v1/tenants/{id}/subscription', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { status: 'ACTIVE' } });
    expect(await tenantService.subscription('t-1')).toEqual({ status: 'ACTIVE' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/tenants/t-1/subscription');
  });

  it('setModules PUTs { modules } without the impersonation header', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { modules: ['HRMS'] } });
    await tenantService.setModules('t-1', ['HRMS']);
    expect(apiClient.put).toHaveBeenCalledWith(
      '/v1/tenants/t-1/subscription/modules',
      { modules: ['HRMS'] },
      { skipImpersonation: true },
    );
  });

  it('setStatus PUTs { status } without the impersonation header', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { status: 'SUSPENDED' } });
    await tenantService.setStatus('t-1', 'SUSPENDED');
    expect(apiClient.put).toHaveBeenCalledWith(
      '/v1/tenants/t-1/subscription/status',
      { status: 'SUSPENDED' },
      { skipImpersonation: true },
    );
  });
});

describe('impersonationService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('open POSTs the email and reason to /v1/tenants/{id}/impersonations', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { sessionId: 's-1' } });
    expect(await impersonationService.open('t-1', { email: ' a@b.c ' }, 'ticket 42')).toEqual({ sessionId: 's-1' });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/tenants/t-1/impersonations',
      { reason: 'ticket 42', email: 'a@b.c' },
      { skipImpersonation: true },
    );
  });

  it('open sends userAccountId when given one', async () => {
    apiClient.post.mockResolvedValueOnce({ data: {} });
    await impersonationService.open('t-1', { userAccountId: 'u-1' }, 'why');
    expect(apiClient.post.mock.calls[0][1]).toEqual({ reason: 'why', userAccountId: 'u-1' });
  });

  it('open with no target sends only the reason (bootstrap)', async () => {
    apiClient.post.mockResolvedValueOnce({ data: {} });
    await impersonationService.open('t-1', null, 'set up');
    expect(apiClient.post.mock.calls[0][1]).toEqual({ reason: 'set up' });
  });

  it('close DELETEs /v1/impersonations/{id} without the impersonation header', async () => {
    apiClient.delete.mockResolvedValueOnce({ status: 204 });
    await impersonationService.close('s-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/impersonations/s-1', { skipImpersonation: true });
  });
});

describe('auditService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('search GETs /v1/audit and drops empty filters', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });
    await auditService.search({ entity: 'employee', actor: '', page: 0, size: 20 });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/audit', {
      params: { entity: 'employee', page: 0, size: 20 },
    });
  });
});

describe('admin services import no axios', () => {
  it.each(['tenantService.js', 'impersonationService.js', 'auditService.js'])('%s', (file) => {
    const source = fs.readFileSync(resolve(__dirname, file), 'utf8');
    expect(source).not.toMatch(/from\s+['"]axios/);
  });
});
