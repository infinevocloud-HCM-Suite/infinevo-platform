import { describe, it, expect, vi, beforeEach } from 'vitest';
import { reviewService } from './reviewService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({ apiClient: { get: vi.fn() } }));

const envelope = (data) => ({ data: { status: 'success', message: 'ok', data } });
const page = { content: [{ id: 'ts-1' }], page: 0, size: 20, total_elements: 1, total_pages: 1 };

describe('reviewService (W-48.3 §7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('managed sends its filters and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope(page));
    const r = await reviewService.managed({
      from: '2026-09-07',
      status: 'SUBMITTED',
      projectId: 'p-1',
      employeeId: '',
      page: 1,
      size: 20,
    });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/managed', {
      params: { from: '2026-09-07', status: 'SUBMITTED', projectId: 'p-1', page: 1, size: 20 },
    });
    expect(r).toEqual(page);
  });

  it('team sends employeeId and paging', async () => {
    apiClient.get.mockResolvedValueOnce(envelope(page));
    await reviewService.team({ employeeId: 'e-1', page: 0, size: 20 });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/team', {
      params: { employeeId: 'e-1', page: 0, size: 20 },
    });
  });

  it('all hits the base list', async () => {
    apiClient.get.mockResolvedValueOnce(envelope(page));
    expect(await reviewService.all({ to: '2026-10-04' })).toEqual(page);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets', { params: { to: '2026-10-04' } });
  });

  it('get and entry read one item and unwrap data', async () => {
    apiClient.get.mockResolvedValueOnce(envelope({ id: 'ts-1' }));
    expect(await reviewService.get('ts-1')).toEqual({ id: 'ts-1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/ts-1');
    apiClient.get.mockResolvedValueOnce(envelope({ id: 'pe-1' }));
    expect(await reviewService.entry('pe-1')).toEqual({ id: 'pe-1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/hrms/timesheets/project-entries/pe-1');
  });
});
