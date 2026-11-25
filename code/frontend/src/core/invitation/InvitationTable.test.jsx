import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { InvitationTable } from './InvitationTable.jsx';

describe('InvitationTable component', () => {
  const mockInvitations = [
    {
      id: 'inv-1',
      email: 'pending@example.com',
      status: 'PENDING',
      // Far ahead on purpose: a pending row past its expiry offers Delete instead (D-22).
      expiresAt: '2099-10-15T12:00:00Z',
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

  it('D-22: offers Delete on revoked rows only, and only when onDelete is given', async () => {
    const { rerender } = render(<InvitationTable data={mockInvitations} />);
    expect(document.getElementById('btn-delete-inv-3')).toBeNull();

    const handleDelete = vi.fn();
    rerender(<InvitationTable data={mockInvitations} onDelete={handleDelete} />);
    expect(document.getElementById('btn-delete-inv-1')).toBeNull(); // pending: revoke first
    expect(document.getElementById('btn-delete-inv-2')).toBeNull(); // accepted: the account's record
    const deleteBtn = document.getElementById('btn-delete-inv-3');
    expect(deleteBtn).not.toBeNull();

    fireEvent.click(deleteBtn);
    expect(screen.getByText('Remove this invitation from the list? This cannot be undone.')).toBeDefined();
    expect(handleDelete).not.toHaveBeenCalled();
    fireEvent.click(document.getElementById('btn-confirm-delete-inv-3') || screen.getByText('Yes, delete'));
    await waitFor(() => {
      expect(handleDelete).toHaveBeenCalledWith('inv-3', mockInvitations[2]);
    });
  });

  it('D-22: a pending row whose expiry has passed offers Delete instead of Resend/Revoke', () => {
    const stale = { id: 'inv-4', email: 'late@example.com', status: 'PENDING', expiresAt: '2020-01-01T00:00:00Z' };
    render(<InvitationTable data={[stale]} onDelete={vi.fn()} onRevoke={vi.fn()} />);
    expect(document.getElementById('btn-delete-inv-4')).not.toBeNull();
    expect(document.getElementById('btn-revoke-inv-4')).toBeNull();
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
