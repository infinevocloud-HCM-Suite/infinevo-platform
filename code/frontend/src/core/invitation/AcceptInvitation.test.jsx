import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AcceptInvitation } from './AcceptInvitation.jsx';
import { publicInvitationService } from './publicInvitationService.js';

vi.mock('./publicInvitationService.js', () => ({
  publicInvitationService: {
    accept: vi.fn(),
    decline: vi.fn(),
  },
}));

describe('AcceptInvitation component', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();

    // Mock window.location
    delete window.location;
    window.location = {
      ...originalLocation,
      pathname: '/invitations/accept',
      search: '?token=secret-token-12345',
    };

    vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
  });

  afterEach(() => {
    window.location = originalLocation;
  });

  it('makes NO request on render, clears token from URL, and never stores token in storage', () => {
    render(<AcceptInvitation />);

    // Invariant: no API calls on initial render
    expect(publicInvitationService.accept).not.toHaveBeenCalled();
    expect(publicInvitationService.decline).not.toHaveBeenCalled();

    // Invariant: token stripped from URL
    expect(window.history.replaceState).toHaveBeenCalledWith(
      {},
      document.title,
      '/invitations/accept',
    );

    // Invariant: no token in localStorage or sessionStorage
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(Object.values(localStorage)).not.toContain('secret-token-12345');
    expect(Object.values(sessionStorage)).not.toContain('secret-token-12345');
  });

  it('accept posts the token and displays success state', async () => {
    publicInvitationService.accept.mockResolvedValueOnce({ message: 'Accepted' });

    render(<AcceptInvitation />);

    const acceptBtn = screen.getByRole('button', { name: /Accept Invitation/i });
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(publicInvitationService.accept).toHaveBeenCalledWith('secret-token-12345');
    });

    expect(screen.getByText('Invitation Accepted')).toBeDefined();
    expect(
      screen.getByText(/Accepted. Check your email to set your password, then sign in./i),
    ).toBeDefined();
    expect(screen.getByRole('link', { name: /Sign in/i }) || screen.getByText(/Sign in/i)).toBeDefined();
  });

  it('decline refuses a blank reason, then submits non-blank reason', async () => {
    publicInvitationService.decline.mockResolvedValueOnce({ message: 'Declined' });

    render(<AcceptInvitation />);

    const showDeclineBtn = screen.getByRole('button', { name: /Decline Invitation/i });
    fireEvent.click(showDeclineBtn);

    // Decline reason form shows
    const confirmDeclineBtn = screen.getByRole('button', { name: /Confirm Decline/i });
    fireEvent.click(confirmDeclineBtn);

    // Refuses blank reason
    await waitFor(() => {
      expect(screen.getByText('Reason must not be blank')).toBeDefined();
    });
    expect(publicInvitationService.decline).not.toHaveBeenCalled();

    // Enter reason and submit
    const textarea = document.getElementById('textarea-decline-reason');
    fireEvent.change(textarea, { target: { value: 'Joined another company' } });

    fireEvent.click(confirmDeclineBtn);

    await waitFor(() => {
      expect(publicInvitationService.decline).toHaveBeenCalledWith(
        'secret-token-12345',
        'Joined another company',
      );
    });

    expect(screen.getByText('Invitation Declined')).toBeDefined();
    expect(screen.getByText('Declined.')).toBeDefined();
  });

  it('409 error shows the one generic invalid message', async () => {
    publicInvitationService.accept.mockRejectedValueOnce({
      status: 409,
      code: 'CONFLICT',
      message: 'Token expired',
    });

    render(<AcceptInvitation />);

    const acceptBtn = screen.getByRole('button', { name: /Accept Invitation/i });
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(
        screen.getByText(
          'This invitation cannot be used. It may have expired, been used or been withdrawn. Ask for a new invitation.',
        ),
      ).toBeDefined();
    });
  });

  it('503 error shows "Try again in a few minutes"', async () => {
    publicInvitationService.accept.mockRejectedValueOnce({
      status: 503,
      code: 'INTERNAL',
      message: 'Keycloak unavailable',
    });

    render(<AcceptInvitation />);

    const acceptBtn = screen.getByRole('button', { name: /Accept Invitation/i });
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(screen.getByText('Try again in a few minutes')).toBeDefined();
    });
  });
});
