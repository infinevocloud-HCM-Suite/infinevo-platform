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

  it('calls accept with the token and the chosen password (D-88)', async () => {
    const mockResponse = { message: 'Invitation accepted successfully', outcome: 'PASSWORD_SET' };
    apiClient.post.mockResolvedValueOnce({ data: mockResponse });

    const res = await publicInvitationService.accept('token-abc-123', 'Str0ng-Passw0rd!');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/invitations/accept', {
      token: 'token-abc-123',
      password: 'Str0ng-Passw0rd!',
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
