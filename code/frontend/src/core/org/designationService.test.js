import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { designationService } from './designationService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('designationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list with activeOnly parameter and unwraps data from response', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'des1', name: 'Software Engineer' }] });
    const result = await designationService.list(false);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/designations', { params: { activeOnly: false } });
    expect(result).toEqual([{ id: 'des1', name: 'Software Engineer' }]);
  });

  it('calls create, update and remove with correct paths', async () => {
    const payload = { code: 'MGR', name: 'Manager', active: true };
    apiClient.post.mockResolvedValueOnce({ id: 'des2', ...payload });
    await designationService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/designations', payload);

    apiClient.put.mockResolvedValueOnce({ id: 'des2', ...payload, active: false });
    await designationService.update('des2', { ...payload, active: false });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/designations/des2', { ...payload, active: false });

    apiClient.delete.mockResolvedValueOnce({});
    await designationService.remove('des2');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/designations/des2');
  });
});
