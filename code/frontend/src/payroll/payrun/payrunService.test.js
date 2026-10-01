import { describe, it, expect, vi, beforeEach } from 'vitest';
import { payrunService } from './payrunService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('payrunService (W-47.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hits list endpoint with filtered params', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });

    await payrunService.list({
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
  });

  it('hits get endpoint by ID', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'run-1', period: '2026-10' } });

    const res = await payrunService.get('run-1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1');
    expect(res).toEqual({ id: 'run-1', period: '2026-10' });
  });

  it('hits create endpoint with period', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'run-2', period: '2026-10' } });

    const res = await payrunService.create({ period: '2026-10' });

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns', { period: '2026-10' });
    expect(res).toEqual({ id: 'run-2', period: '2026-10' });
  });

  it('hits createOffCycle endpoint with formatted payload', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'run-off', run_type: 'OFF_CYCLE' } });

    await payrunService.createOffCycle({
      payDate: '2026-10-15',
      employeeIds: ['e1', 'e2'],
      notes: 'Bonus run',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/off-cycle', {
      payDate: '2026-10-15',
      employeeIds: ['e1', 'e2'],
      notes: 'Bonus run',
    });
  });

  it('hits employees endpoint with inclusion filter and pagination', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [] } });

    await payrunService.employees('run-1', { inclusion: 'SKIPPED', page: 2, size: 25 });

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/employees', {
      params: {
        inclusion: 'SKIPPED',
        page: 2,
        size: 25,
      },
    });
  });

  it('hits lines endpoint for an employee', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { lines: [] } });

    await payrunService.lines('run-1', 'emp-1');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/employees/emp-1/lines');
  });

  it('hits compute endpoint and accepts 202 response', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { job_id: 'job-999' } });

    const res = await payrunService.compute('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/compute');
    expect(res).toEqual({ job_id: 'job-999' });
  });

  it('hits lock endpoint', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'run-1', status: 'LOCKED' } });

    await payrunService.lock('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/lock');
  });

  it('hits cancel endpoint', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'run-1', status: 'CANCELLED' } });

    await payrunService.cancel('run-1');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/cancel');
  });

  it('hits addInputs endpoint and posts an array', async () => {
    const rows = [
      { employeeId: 'e1', kind: 'BONUS', amount: 5000, sourceRef: 'REF1' },
      { employeeId: 'e2', kind: 'OVERTIME', amount: 1200, sourceRef: 'REF2' },
    ];
    apiClient.post.mockResolvedValueOnce({
      data: [
        { employee_id: 'e1', result: 'RECORDED', pay_input_id: 'pi-1' },
        { employee_id: 'e2', result: 'DUPLICATE', pay_input_id: null },
      ],
    });

    const res = await payrunService.addInputs('run-1', rows);

    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/inputs', rows);
    expect(res).toHaveLength(2);
  });

  it('hits searchEmployees via core HTTP path', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [{ id: 'e1', firstName: 'John' }] } });

    const res = await payrunService.searchEmployees('John');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', { params: { q: 'John' } });
    expect(res).toHaveLength(1);
  });
});
