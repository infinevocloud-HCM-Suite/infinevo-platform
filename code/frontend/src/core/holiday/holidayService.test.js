import { describe, it, expect, vi, beforeEach } from 'vitest';
import { holidayService } from './holidayService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('holidayService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls between with workLocationId, from, and to', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'hol-1', name: 'Gandhi Jayanti' }] });
    const res = await holidayService.between({
      workLocationId: 'loc-blr',
      from: '2026-10-01',
      to: '2026-10-31',
    });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/holidays', {
      params: {
        workLocationId: 'loc-blr',
        from: '2026-10-01',
        to: '2026-10-31',
      },
    });
    expect(res).toEqual([{ id: 'hol-1', name: 'Gandhi Jayanti' }]);
  });

  it('calls between with empty params', async () => {
    apiClient.get.mockResolvedValueOnce([]);
    await holidayService.between();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/holidays', {
      params: {},
    });
  });
});
