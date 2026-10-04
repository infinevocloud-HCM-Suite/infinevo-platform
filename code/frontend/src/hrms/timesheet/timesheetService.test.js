import { describe, it, expect, vi, beforeEach } from 'vitest';
import { timesheetService } from './timesheetService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const envelope = (data) => ({ data: { status: 'success', message: 'ok', data } });
const body = { week_start_date: '2026-09-28', projects: [] };

describe('timesheetService (W-48.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('mine sends the range and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([{ id: 'ts-1' }]));
    const result = await timesheetService.mine('2026-07-13', '2026-10-04');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/mine', {
      params: { from: '2026-07-13', to: '2026-10-04' },
    });
    expect(result).toEqual([{ id: 'ts-1' }]);
  });

  it('mine leaves out an absent range', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([]));
    await timesheetService.mine();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/mine', { params: {} });
  });

  it('week reads /me/timesheet and returns null when data is null', async () => {
    apiClient.get.mockResolvedValueOnce(envelope(null));
    const result = await timesheetService.week('2026-09-28');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/timesheet', { params: { weekStart: '2026-09-28' } });
    expect(result).toBeNull();

    apiClient.get.mockResolvedValueOnce(envelope({ id: 'ts-1' }));
    expect(await timesheetService.week('2026-09-28')).toEqual({ id: 'ts-1' });
  });

  it('create posts the body and unwraps data', async () => {
    apiClient.post.mockResolvedValueOnce(envelope({ id: 'ts-1', status: 'DRAFT' }));
    const result = await timesheetService.create(body);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/hrms/timesheets', body);
    expect(result).toEqual({ id: 'ts-1', status: 'DRAFT' });
  });

  it('replace puts the body on the id and unwraps data', async () => {
    apiClient.put.mockResolvedValueOnce(envelope({ id: 'ts-1' }));
    const result = await timesheetService.replace('ts-1', body);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/hrms/timesheets/ts-1', body);
    expect(result).toEqual({ id: 'ts-1' });
  });

  it('submit puts to /submit and unwraps data', async () => {
    apiClient.put.mockResolvedValueOnce(envelope({ id: 'ts-1', status: 'SUBMITTED' }));
    const result = await timesheetService.submit('ts-1');
    expect(apiClient.put).toHaveBeenCalledWith('/v1/hrms/timesheets/ts-1/submit');
    expect(result).toEqual({ id: 'ts-1', status: 'SUBMITTED' });
  });

  it('remove deletes the id', async () => {
    apiClient.delete.mockResolvedValueOnce({ status: 204 });
    await timesheetService.remove('ts-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/hrms/timesheets/ts-1');
  });

  it('myProjects reads /projects/mine and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([{ id: 'p-1', name: 'Apollo' }]));
    const result = await timesheetService.myProjects();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/projects/mine');
    expect(result).toEqual([{ id: 'p-1', name: 'Apollo' }]);
  });

  it('tasks reads the tasks of a project and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope([{ id: 't-1', title: 'Build' }]));
    const result = await timesheetService.tasks('p-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/projects/p-1/tasks');
    expect(result).toEqual([{ id: 't-1', title: 'Build' }]);
  });
});
