import { describe, it, expect, vi, beforeEach } from 'vitest';
import { payrunService } from './payrunService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

// The axios response as apiClient returns it, carrying the server's PayRunApiResponse envelope.
const envelope = (data, status = 200) => ({ data: { status, message: 'ok', data } });

describe('payrunService (W-47.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hits list endpoint with filtered params and returns the page from the envelope', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ content: [{ id: 'run-1' }], totalElements: 1 }));

    const res = await payrunService.list({
      status: 'LOCKED',
      runType: 'REGULAR',
      page: 1,
      size: 10,
    });

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns', {
      params: {
        status: 'LOCKED',
        runType: 'REGULAR',
        page: 1,
        size: 10,
      },
    });
    expect(res).toEqual({ content: [{ id: 'run-1' }], totalElements: 1 });
  });

  it('hits get endpoint by ID and returns the run, not the envelope', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ id: 'run-1', period: '2026-10', status: 'DRAFT' }));

    const res = await payrunService.get('run-1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1');
    // The envelope's numeric `status` must not stand in for the run's status.
    expect(res).toEqual({ id: 'run-1', period: '2026-10', status: 'DRAFT' });
  });

  it('hits create endpoint with period', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'run-2', period: '2026-10' }, 201));

    const res = await payrunService.create({ period: '2026-10' });

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns', { period: '2026-10' });
    expect(res).toEqual({ id: 'run-2', period: '2026-10' });
  });

  it('hits createOffCycle endpoint with formatted payload', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'run-off', run_type: 'OFF_CYCLE' }, 201));

    const res = await payrunService.createOffCycle({
      payDate: '2026-10-15',
      employeeIds: ['e1', 'e2'],
      notes: 'Festival payout',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/off-cycle', {
      payDate: '2026-10-15',
      employeeIds: ['e1', 'e2'],
      notes: 'Festival payout',
    });
    expect(res).toEqual({ id: 'run-off', run_type: 'OFF_CYCLE' });
  });

  it('hits employees endpoint with inclusion filter and pagination', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ content: [], totalElements: 0 }));

    const res = await payrunService.employees('run-1', { inclusion: 'SKIPPED', page: 2, size: 25 });

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/employees', {
      params: {
        inclusion: 'SKIPPED',
        page: 2,
        size: 25,
      },
    });
    expect(res).toEqual({ content: [], totalElements: 0 });
  });

  it('hits lines endpoint for an employee', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ employee_id: 'emp-1', lines: [] }));

    const res = await payrunService.lines('run-1', 'emp-1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/employees/emp-1/lines');
    expect(res).toEqual({ employee_id: 'emp-1', lines: [] });
  });

  it('hits compute endpoint and accepts 202 response', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ job_id: 'job-999' }, 202));

    const res = await payrunService.compute('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/compute');
    expect(res).toEqual({ job_id: 'job-999' });
  });

  it('hits lock endpoint', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'run-1', status: 'LOCKED' }));

    const res = await payrunService.lock('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/lock');
    expect(res.status).toBe('LOCKED');
  });

  it('hits cancel endpoint', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'run-1', status: 'CANCELLED' }));

    const res = await payrunService.cancel('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/cancel');
    expect(res.status).toBe('CANCELLED');
  });

  it('hits approve endpoint with no body (W-36.2)', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'run-1', status: 'APPROVED' }));

    const res = await payrunService.approve('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/approve');
    expect(res.status).toBe('APPROVED');
  });

  it('hits pay endpoint with paid_on (W-36.2)', async () => {
    apiClient.post.mockResolvedValueOnce(
      envelope({ id: 'run-1', status: 'PAID', paid_on: '2026-10-31', notified: 2 })
    );

    const res = await payrunService.pay('run-1', '2026-10-31');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/pay', {
      paid_on: '2026-10-31',
    });
    expect(res).toEqual({ id: 'run-1', status: 'PAID', paid_on: '2026-10-31', notified: 2 });
  });

  it('hits addInputs endpoint and posts an array', async () => {
    const rows = [
      { employeeId: 'e1', kind: 'ONE_TIME_PAYOUT', amount: 5000, sourceRef: 'REF1' },
      { employeeId: 'e2', kind: 'OVERTIME', amount: 1200, sourceRef: 'REF2' },
    ];
    apiClient.post.mockResolvedValueOnce(
      envelope(
        [
          { employee_id: 'e1', source_ref: 'REF1', result: 'RECORDED', pay_input_id: 'pi-1' },
          { employee_id: 'e2', source_ref: 'REF2', result: 'DUPLICATE', pay_input_id: null },
        ],
        201
      )
    );

    const res = await payrunService.addInputs('run-1', rows);

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/inputs', rows);
    expect(res).toHaveLength(2);
    expect(res[1].result).toBe('DUPLICATE');
  });

  it('hits searchEmployees via core HTTP path, which answers a bare page', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [{ id: 'e1', firstName: 'John' }] } });

    const res = await payrunService.searchEmployees('John');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', { params: { q: 'John' } });
    expect(res).toHaveLength(1);
  });
});
