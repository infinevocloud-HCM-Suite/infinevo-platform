import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { userService } from './userService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), put: vi.fn(), post: vi.fn() },
}));

describe('userService (W-73.4)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists users, passing q only when given', async () => {
    apiClient.get.mockResolvedValue({ data: [{ id: 'u-1' }] });
    expect(await userService.list()).toEqual([{ id: 'u-1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/users', { params: {} });
    await userService.list({ q: 'asha' });
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/users', { params: { q: 'asha' } });
  });

  it('puts the complete role set', async () => {
    apiClient.put.mockResolvedValue({ data: { userAccountId: 'u-1' } });
    await userService.setRoles('u-1', ['r-1', 'r-2']);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/users/u-1/roles', { roleIds: ['r-1', 'r-2'] });
  });

  it('disables and enables through their own endpoints', async () => {
    apiClient.post.mockResolvedValue({});
    await userService.disable('u-1');
    await userService.enable('u-1');
    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/v1/users/u-1/disable');
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/v1/users/u-1/enable');
  });

  it('leaves platform-admin out of the role picker', async () => {
    apiClient.get.mockResolvedValue({
      data: [
        { id: 'r-1', code: 'employee' },
        { id: 'r-2', code: 'platform-admin' },
      ],
    });
    expect(await userService.roles()).toEqual([{ id: 'r-1', code: 'employee' }]);
  });
});
