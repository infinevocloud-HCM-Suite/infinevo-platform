import { describe, it, expect, vi, beforeEach } from 'vitest';
import { taxSettingsService } from './taxSettingsService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('taxSettingsService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches tax declaration settings for a financial year', async () => {
    const mockData = { financial_year: '2026-27', is_open: true };
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await taxSettingsService.get('2026-27');
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/payroll/tax-declaration/settings/2026-27');
    expect(res).toEqual(mockData);
  });

  it('saves tax declaration settings with body', async () => {
    const body = { window_opens_on: '2026-04-01', window_closes_on: '2026-04-30' };
    const mockResponse = { financial_year: '2026-27', ...body };
    apiClient.put.mockResolvedValueOnce({ data: mockResponse });

    const res = await taxSettingsService.save('2026-27', body);
    expect(apiClient.put).toHaveBeenCalledWith('/api/v1/payroll/tax-declaration/settings/2026-27', body);
    expect(res).toEqual(mockResponse);
  });
});
