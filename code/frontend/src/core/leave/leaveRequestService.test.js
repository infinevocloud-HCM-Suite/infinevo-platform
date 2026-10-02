import { describe, it, expect, vi, beforeEach } from 'vitest';
import { leaveRequestService } from './leaveRequestService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('leaveRequestService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list on /v1/leave-requests with params', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'req-1', status: 'APPROVED' }] });
    const params = { employeeId: 'emp-1', status: 'APPROVED' };
    const res = await leaveRequestService.list(params);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-requests', { params });
    expect(res).toEqual([{ id: 'req-1', status: 'APPROVED' }]);
  });

  it('calls get on /v1/leave-requests/{id}', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'req-1' } });
    const res = await leaveRequestService.get('req-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-requests/req-1');
    expect(res).toEqual({ id: 'req-1' });
  });

  it('calls onBehalf on /v1/leave-requests/on-behalf', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'req-1', status: 'APPROVED' } });
    const payload = {
      employeeId: 'emp-1',
      leaveTypeId: 'type-1',
      fromDate: '2026-10-05',
      toDate: '2026-10-06',
      reason: 'Personal',
    };
    const res = await leaveRequestService.onBehalf(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/on-behalf', payload);
    expect(res).toEqual({ id: 'req-1', status: 'APPROVED' });
  });

  it('calls withdraw on /v1/leave-requests/{id}/withdraw with reason', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'req-1', status: 'WITHDRAWN' } });
    const res = await leaveRequestService.withdraw('req-1', 'Cancelled trip');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/req-1/withdraw', {
      reason: 'Cancelled trip',
    });
    expect(res).toEqual({ id: 'req-1', status: 'WITHDRAWN' });
  });

  it('calls cancel on /v1/leave-requests/{id}/cancel with reason', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'req-1', status: 'CANCELLED' } });
    const res = await leaveRequestService.cancel('req-1', 'Admin override');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/req-1/cancel', {
      reason: 'Admin override',
    });
    expect(res).toEqual({ id: 'req-1', status: 'CANCELLED' });
  });

  it('calls create on /v1/leave-requests', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'req-2', status: 'SUBMITTED' } });
    const payload = { leaveTypeId: 'type-1', fromDate: '2026-11-01', toDate: '2026-11-02' };
    const res = await leaveRequestService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests', payload);
    expect(res).toEqual({ id: 'req-2', status: 'SUBMITTED' });
  });

  it('calls submit on /v1/leave-requests/{id}/submit', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'req-2', status: 'PENDING' } });
    const res = await leaveRequestService.submit('req-2');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-requests/req-2/submit');
    expect(res).toEqual({ id: 'req-2', status: 'PENDING' });
  });
});
