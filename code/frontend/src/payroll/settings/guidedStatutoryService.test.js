import { describe, it, expect, vi, beforeEach } from 'vitest';
import { guidedStatutoryService } from './guidedStatutoryService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('guidedStatutoryService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getReadiness calls GET /v1/payroll/settings/guided-setup and unwraps data', async () => {
    const mockData = { epfConfigured: false, operatingStates: [] };
    apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: mockData } });

    const result = await guidedStatutoryService.getReadiness();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/settings/guided-setup');
    expect(result).toEqual(mockData);
  });

  it('applySetup calls POST /v1/payroll/settings/guided-setup and unwraps data', async () => {
    const payload = { epf: { registered: true } };
    const mockResponse = { allStatutoryConfigured: true };
    apiClient.post.mockResolvedValueOnce({ data: { status: 200, data: mockResponse } });

    const result = await guidedStatutoryService.applySetup(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/settings/guided-setup', payload);
    expect(result).toEqual(mockResponse);
  });
});

