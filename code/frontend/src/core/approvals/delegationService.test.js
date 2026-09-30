import { describe, it, expect, vi, beforeEach } from 'vitest';
import { delegationService } from './delegationService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('delegationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list with query parameters', async () => {
    apiClient.get.mockResolvedValueOnce([]);
    await delegationService.list({ employeeId: 'emp-1', activeOn: '2026-09-29' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approval-delegations', {
      params: { employeeId: 'emp-1', activeOn: '2026-09-29' },
    });
  });

  it('calls create with body', async () => {
    apiClient.post.mockResolvedValueOnce({ id: 'del-1' });
    await delegationService.create({
      delegateEmployeeId: 'emp-2',
      startsOn: '2026-10-01',
      endsOn: '2026-10-05',
      flowTypes: ['LEAVE'],
    });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/approval-delegations', {
      delegateEmployeeId: 'emp-2',
      startsOn: '2026-10-01',
      endsOn: '2026-10-05',
      flowTypes: ['LEAVE'],
    });
  });

  it('calls remove with delegation id', async () => {
    apiClient.delete.mockResolvedValueOnce({});
    await delegationService.remove('del-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/approval-delegations/del-1');
  });
});
