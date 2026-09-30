import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { departmentService } from './departmentService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('departmentService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list with activeOnly parameter and unwraps data from response', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'd1', name: 'Finance' }] });
    const result = await departmentService.list(true);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/departments', { params: { activeOnly: true } });
    expect(result).toEqual([{ id: 'd1', name: 'Finance' }]);
  });

  it('calls create with body payload', async () => {
    const payload = { code: 'ENG', name: 'Engineering', active: true };
    apiClient.post.mockResolvedValueOnce({ id: 'd2', ...payload });
    const result = await departmentService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/departments', payload);
    expect(result.id).toBe('d2');
  });

  it('calls update and remove on specific department id', async () => {
    const payload = { code: 'ENG', name: 'Engineering Dept', active: false };
    apiClient.put.mockResolvedValueOnce({ id: 'd2', ...payload });
    await departmentService.update('d2', payload);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/departments/d2', payload);

    apiClient.delete.mockResolvedValueOnce({});
    await departmentService.remove('d2');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/departments/d2');
  });
});
