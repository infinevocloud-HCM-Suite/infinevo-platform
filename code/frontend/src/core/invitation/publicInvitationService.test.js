import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { publicInvitationService } from './publicInvitationService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe('publicInvitationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls accept with token', async () => {
    const mockResponse = { message: 'Invitation accepted successfully' };
    apiClient.post.mockResolvedValueOnce({ data: mockResponse });

    const res = await publicInvitationService.accept('token-abc-123');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/invitations/accept', {
      token: 'token-abc-123',
    });
    expect(res).toEqual(mockResponse);
  });

  it('calls decline with token and reason', async () => {
    const mockResponse = { message: 'Invitation declined successfully' };
    apiClient.post.mockResolvedValueOnce({ data: mockResponse });

    const res = await publicInvitationService.decline('token-abc-123', 'Not interested at this time');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/invitations/decline', {
      token: 'token-abc-123',
      reason: 'Not interested at this time',
    });
    expect(res).toEqual(mockResponse);
  });
});
