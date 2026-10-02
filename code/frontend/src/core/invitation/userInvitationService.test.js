import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { userInvitationService } from './userInvitationService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('userInvitationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list without status param', async () => {
    const mockData = [{ id: 'inv-1', email: 'user@example.com', status: 'PENDING' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await userInvitationService.list();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/user-invitations', { params: {} });
    expect(res).toEqual(mockData);
  });

  it('calls list with status param', async () => {
    const mockData = [{ id: 'inv-2', email: 'user2@example.com', status: 'ACCEPTED' }];
    apiClient.get.mockResolvedValueOnce({ data: mockData });

    const res = await userInvitationService.list({ status: 'ACCEPTED' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/user-invitations', {
      params: { status: 'ACCEPTED' },
    });
    expect(res).toEqual(mockData);
  });

  it('calls create with body', async () => {
    const payload = { email: 'new@example.com', roleIds: ['role-1'] };
    const mockCreated = { id: 'inv-3', ...payload, status: 'PENDING' };
    apiClient.post.mockResolvedValueOnce({ data: mockCreated });

    const res = await userInvitationService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/user-invitations', payload);
    expect(res).toEqual(mockCreated);
  });

  it('calls resend with invitation id', async () => {
    const mockResent = { id: 'inv-4', status: 'PENDING' };
    apiClient.post.mockResolvedValueOnce({ data: mockResent });

    const res = await userInvitationService.resend('inv-4');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/user-invitations/inv-4/resend');
    expect(res).toEqual(mockResent);
  });

  it('calls revoke with invitation id', async () => {
    apiClient.post.mockResolvedValueOnce({ data: null });

    const res = await userInvitationService.revoke('inv-5');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/user-invitations/inv-5/revoke');
    expect(res).toBeNull();
  });
});
