import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { InvitationTable } from './InvitationTable.jsx';

describe('InvitationTable component', () => {
  const mockInvitations = [
    {
      id: 'inv-1',
      email: 'pending@example.com',
      status: 'PENDING',
      expiresAt: '2026-10-15T12:00:00Z',
      createdAt: '2026-10-01T12:00:00Z',
    },
    {
      id: 'inv-2',
      email: 'accepted@example.com',
      status: 'ACCEPTED',
      expiresAt: '2026-10-10T12:00:00Z',
      createdAt: '2026-09-25T12:00:00Z',
    },
    {
      id: 'inv-3',
      email: 'revoked@example.com',
      status: 'REVOKED',
      expiresAt: '2026-10-12T12:00:00Z',
      createdAt: '2026-09-28T12:00:00Z',
    },
  ];

  it('renders invitation rows and status tags', () => {
    render(<InvitationTable data={mockInvitations} />);

    expect(screen.getByText('pending@example.com')).toBeDefined();
    expect(screen.getByText('accepted@example.com')).toBeDefined();
    expect(screen.getByText('revoked@example.com')).toBeDefined();

    expect(screen.getByText('Pending')).toBeDefined();
    expect(screen.getByText('Accepted')).toBeDefined();
    expect(screen.getByText('Revoked')).toBeDefined();
  });

  it('shows resend and revoke buttons only for PENDING status', () => {
    render(<InvitationTable data={mockInvitations} />);

    // Only inv-1 is PENDING
    expect(document.getElementById('btn-resend-inv-1')).not.toBeNull();
    expect(document.getElementById('btn-revoke-inv-1')).not.toBeNull();

    expect(document.getElementById('btn-resend-inv-2')).toBeNull();
    expect(document.getElementById('btn-revoke-inv-2')).toBeNull();

    expect(document.getElementById('btn-resend-inv-3')).toBeNull();
    expect(document.getElementById('btn-revoke-inv-3')).toBeNull();
  });

  it('calls onResend when Resend button is clicked', () => {
    const handleResend = vi.fn();
    render(<InvitationTable data={mockInvitations} onResend={handleResend} />);

    const resendBtn = document.getElementById('btn-resend-inv-1');
    fireEvent.click(resendBtn);

    expect(handleResend).toHaveBeenCalledWith('inv-1', mockInvitations[0]);
  });

  it('confirms before calling onRevoke', async () => {
    const handleRevoke = vi.fn();
    render(<InvitationTable data={mockInvitations} onRevoke={handleRevoke} />);

    const revokeBtn = document.getElementById('btn-revoke-inv-1');
    fireEvent.click(revokeBtn);

    // Popconfirm shows
    expect(screen.getByText('Are you sure you want to revoke this invitation?')).toBeDefined();
    expect(handleRevoke).not.toHaveBeenCalled();

    const confirmBtn = document.getElementById('btn-confirm-revoke-inv-1') || screen.getByText('Yes, revoke');
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(handleRevoke).toHaveBeenCalledWith('inv-1', mockInvitations[0]);
    });
  });

  it('W-73.4: shows a Kind column only when asked', () => {
    const { rerender } = render(<InvitationTable data={mockInvitations} />);
    expect(screen.queryByText('Kind')).toBeNull();

    rerender(
      <InvitationTable
        showKind
        data={[
          { ...mockInvitations[0], kind: 'USER' },
          { ...mockInvitations[1], kind: 'EMPLOYEE' },
        ]}
      />,
    );
    expect(screen.getByText('Kind')).toBeDefined();
    expect(screen.getByText('User')).toBeDefined();
    expect(screen.getByText('Employee')).toBeDefined();
  });

  it('renders custom emptyText when no invitations exist', () => {
    render(<InvitationTable data={[]} emptyText="No pending invitations" />);
    expect(screen.getByText('No pending invitations')).toBeDefined();
  });
});
