import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setupService } from './setupService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('setupService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('get() calls GET /v1/setup-checklist and returns data', async () => {
    const mockData = {
      steps: [{ code: 'WORK_LOCATION', label: 'Work location' }],
      completedCount: 1,
      skippedCount: 0,
      totalCount: 7,
      newCount: 0,
      progressPercentage: 14,
    };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await setupService.get();

    expect(apiClient.get).toHaveBeenCalledWith('/v1/setup-checklist');
    expect(res).toEqual(mockData);
  });

  it('skip() calls POST /v1/setup-checklist/{stepCode}/skip with reason body and returns data', async () => {
    const mockStep = {
      code: 'EPF',
      label: 'EPF',
      skipped: true,
      skipReason: 'Handled externally',
    };
    apiClient.post.mockResolvedValueOnce({ data: mockStep });

    const res = await setupService.skip('EPF', 'Handled externally');

    expect(apiClient.post).toHaveBeenCalledWith('/v1/setup-checklist/EPF/skip', {
      reason: 'Handled externally',
    });
    expect(res).toEqual(mockStep);
  });
});
