import { describe, it, expect, vi, beforeEach } from 'vitest';
import { attendanceService } from './attendanceService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

const bare = (data) => ({ data });
const B = '/v1/hrms/attendance';

describe('attendanceService (W-48.4 §7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('today returns the bare body', async () => {
    const body = {
      date: '2026-10-04',
      openSession: null,
      sessions: [],
      workedMinutes: 0,
    };
    apiClient.get.mockResolvedValueOnce(bare(body));
    expect(await attendanceService.today()).toEqual(body);
    expect(apiClient.get).toHaveBeenCalledWith(`${B}/today`);
  });

  it('clockIn and clockOut post and return bare', async () => {
    apiClient.post.mockResolvedValueOnce(bare({ id: 's1' }));
    expect(await attendanceService.clockIn()).toEqual({ id: 's1' });
    expect(apiClient.post).toHaveBeenCalledWith(`${B}/clock-in`);
    apiClient.post.mockResolvedValueOnce(bare({ status: 'PRESENT' }));
    expect(await attendanceService.clockOut()).toEqual({ status: 'PRESENT' });
    expect(apiClient.post).toHaveBeenCalledWith(`${B}/clock-out`);
  });

  it('mySessions sends the range', async () => {
    apiClient.get.mockResolvedValueOnce(bare([{ id: 's1' }]));
    expect(await attendanceService.mySessions('2026-10-01', '2026-10-31')).toEqual([{ id: 's1' }]);
    expect(apiClient.get).toHaveBeenCalledWith(`${B}/sessions/mine`, {
      params: { from: '2026-10-01', to: '2026-10-31' },
    });
  });

  it('allSessions sends employeeId only when given', async () => {
    apiClient.get.mockResolvedValue(bare([]));
    await attendanceService.allSessions('2026-09-28', '2026-10-04');
    expect(apiClient.get).toHaveBeenLastCalledWith(`${B}/sessions`, {
      params: { from: '2026-09-28', to: '2026-10-04' },
    });
    await attendanceService.allSessions('2026-09-28', '2026-10-04', 'e1');
    expect(apiClient.get).toHaveBeenLastCalledWith(`${B}/sessions`, {
      params: { from: '2026-09-28', to: '2026-10-04', employeeId: 'e1' },
    });
  });

  it('preferences and savePreferences return bare', async () => {
    apiClient.get.mockResolvedValueOnce(bare({ isDefault: true }));
    expect(await attendanceService.preferences()).toEqual({ isDefault: true });
    expect(apiClient.get).toHaveBeenCalledWith(`${B}/preferences`);
    apiClient.put.mockResolvedValueOnce(bare({ isDefault: false }));
    expect(await attendanceService.savePreferences({ a: 1 })).toEqual({
      isDefault: false,
    });
    expect(apiClient.put).toHaveBeenCalledWith(`${B}/preferences`, { a: 1 });
  });
});
