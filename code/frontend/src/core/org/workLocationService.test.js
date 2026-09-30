import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { workLocationService } from './workLocationService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('workLocationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list with activeOnly parameter and unwraps data from response', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'loc1', name: 'Headquarters' }] });
    const result = await workLocationService.list(true);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/work-locations', { params: { activeOnly: true } });
    expect(result).toEqual([{ id: 'loc1', name: 'Headquarters' }]);
  });

  it('gets a single location by id through list-then-find, returning null when missing', async () => {
    apiClient.get.mockResolvedValue({
      data: [
        { id: 'loc1', name: 'Headquarters' },
        { id: 'loc2', name: 'Bengaluru Office' },
      ],
    });

    const found = await workLocationService.get('loc2');
    expect(found).toEqual({ id: 'loc2', name: 'Bengaluru Office' });

    const missing = await workLocationService.get('unknown-id');
    expect(missing).toBeNull();
  });

  it('calls create, update and remove with correct paths and payload', async () => {
    const payload = {
      code: 'BLR',
      name: 'Bengaluru Office',
      city: 'Bengaluru',
      state: 'Karnataka',
      stateCode: 'KA',
      zipCode: '560001',
      countryCode: 'IN',
      filingAddress: true,
      active: true,
    };
    apiClient.post.mockResolvedValueOnce({ id: 'loc2', ...payload });
    await workLocationService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/work-locations', payload);

    apiClient.put.mockResolvedValueOnce({ id: 'loc2', ...payload, active: false });
    await workLocationService.update('loc2', { ...payload, active: false });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/work-locations/loc2', { ...payload, active: false });

    apiClient.delete.mockResolvedValueOnce({});
    await workLocationService.remove('loc2');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/work-locations/loc2');
  });
});
