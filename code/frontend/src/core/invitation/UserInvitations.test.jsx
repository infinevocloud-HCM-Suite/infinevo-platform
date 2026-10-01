import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { UserInvitations } from './UserInvitations.jsx';
import { userInvitationService } from './userInvitationService.js';
import { apiClient } from '@shared/api/client.js';
import * as shellScreens from '@shell/screens';

vi.mock('./userInvitationService.js', () => ({
  userInvitationService: {
    list: vi.fn(),
    create: vi.fn(),
    resend: vi.fn(),
    revoke: vi.fn(),
  },
}));

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('UserInvitations component', () => {
  const mockRoles = [
    { id: 'role-1', name: 'Tenant Admin', code: 'tenant-admin' },
    { id: 'role-2', name: 'HR Officer', code: 'hr' },
  ];

  const mockInvitations = [
    {
      id: 'inv-1',
      email: 'alex@example.com',
      status: 'PENDING',
      expiresAt: '2026-10-15T12:00:00Z',
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    userInvitationService.list.mockResolvedValue(mockInvitations);
    apiClient.get.mockResolvedValue({ data: mockRoles });
  });

  it('renders NotEntitled when user lacks core.user.manage', () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(false);

    render(<UserInvitations />);
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    expect(userInvitationService.list).not.toHaveBeenCalled();
  });

  it('renders invitation list when user has core.user.manage', async () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(true);

    render(<UserInvitations />);

    expect(screen.getByRole('heading', { name: /User Invitations/i })).toBeDefined();
    await waitFor(() => {
      expect(userInvitationService.list).toHaveBeenCalled();
      expect(screen.getByText('alex@example.com')).toBeDefined();
    });
  });

  it('opens modal, validates required fields, and submits email and roleIds', async () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(true);
    userInvitationService.create.mockResolvedValue({ id: 'new-inv', status: 'PENDING' });

    render(<UserInvitations />);

    const inviteBtn = screen.getByRole('button', { name: /Invite User/i });
    fireEvent.click(inviteBtn);

    // Modal opens
    expect(screen.getByText('Invite Company User')).toBeDefined();
    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/v1/roles');
    });

    const submitBtn = screen.getByRole('button', { name: /Send Invitation/i });
    fireEvent.click(submitBtn);

    // Validation errors show
    await waitFor(() => {
      expect(screen.getByText('Email is required')).toBeDefined();
      expect(screen.getByText('At least one role is required')).toBeDefined();
    });
    expect(userInvitationService.create).not.toHaveBeenCalled();

    // Fill in email
    const emailInput = document.getElementById('input-invite-email');
    fireEvent.change(emailInput, { target: { value: 'bob@example.com' } });

    // Open role selector and select role
    const selectElem = document.getElementById('select-invite-roles');
    fireEvent.mouseDown(selectElem);

    await waitFor(() => {
      expect(screen.getByText('Tenant Admin')).toBeDefined();
    });
    fireEvent.click(screen.getByText('Tenant Admin'));

    // Submit valid form
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(userInvitationService.create).toHaveBeenCalledWith({
        email: 'bob@example.com',
        roleIds: ['role-1'],
      });
    });
  });
});
