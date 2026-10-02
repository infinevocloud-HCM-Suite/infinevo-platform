import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { employeeInvitationService } from './employeeInvitationService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('employeeInvitationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list without params', async () => {
    const mockData = [{ id: 'emp-inv-1', email: 'emp@example.com', status: 'PENDING' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await employeeInvitationService.list();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employee-invitations', { params: {} });
    expect(res).toEqual(mockData);
  });

  it('calls list with status and employeeId params', async () => {
    const mockData = [{ id: 'emp-inv-2', employeeId: 'emp-123', status: 'ACCEPTED' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await employeeInvitationService.list({
      status: 'ACCEPTED',
      employeeId: 'emp-123',
    });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employee-invitations', {
      params: { status: 'ACCEPTED', employeeId: 'emp-123' },
    });
    expect(res).toEqual(mockData);
  });

  it('calls create with employeeId', async () => {
    const payload = { employeeId: 'emp-456' };
    const mockCreated = { id: 'emp-inv-3', employeeId: 'emp-456', status: 'PENDING' };
    apiClient.post.mockResolvedValueOnce({ data: mockCreated });

    const res = await employeeInvitationService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/employee-invitations', payload);
    expect(res).toEqual(mockCreated);
  });

  it('calls resend with invitation id', async () => {
    const mockResent = { id: 'emp-inv-4', status: 'PENDING' };
    apiClient.post.mockResolvedValueOnce({ data: mockResent });

    const res = await employeeInvitationService.resend('emp-inv-4');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/employee-invitations/emp-inv-4/resend');
    expect(res).toEqual(mockResent);
  });

  it('calls revoke with invitation id', async () => {
    apiClient.post.mockResolvedValueOnce({ data: null });

    const res = await employeeInvitationService.revoke('emp-inv-5');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/employee-invitations/emp-inv-5/revoke');
    expect(res).toBeNull();
  });
});
