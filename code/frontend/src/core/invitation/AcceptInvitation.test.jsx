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

const PASSWORD = 'Str0ng-Passw0rd!';

/** Fills both password fields and presses the accept button. */
function acceptWith(password, confirm = password) {
  fireEvent.change(document.getElementById('input-password'), { target: { value: password } });
  fireEvent.change(document.getElementById('input-confirm-password'), { target: { value: confirm } });
  fireEvent.click(screen.getByRole('button', { name: /Set password and accept/i }));
}

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

  it('D-88: the page asks for a password and shows the realm rules before anything is sent', () => {
    render(<AcceptInvitation />);

    expect(document.getElementById('input-password')).not.toBeNull();
    expect(document.getElementById('input-confirm-password')).not.toBeNull();
    expect(screen.getByText(/At least 10 characters/)).toBeDefined();
    expect(screen.queryByText(/second email/i)).toBeNull();
  });

  it('D-88: a blank, short or mismatched password never reaches the server', async () => {
    render(<AcceptInvitation />);

    fireEvent.click(screen.getByRole('button', { name: /Set password and accept/i }));
    expect(await screen.findByText('Choose a password')).toBeDefined();

    acceptWith('Short1!');
    expect(await screen.findByText('Use at least 10 characters')).toBeDefined();

    acceptWith(PASSWORD, 'Different-Passw0rd!');
    expect(await screen.findByText('The two passwords do not match')).toBeDefined();

    expect(publicInvitationService.accept).not.toHaveBeenCalled();
  });

  it('D-88: accepting sends the token and the password; a set password ends with Sign in', async () => {
    publicInvitationService.accept.mockResolvedValueOnce({ message: 'Accepted', outcome: 'PASSWORD_SET' });

    render(<AcceptInvitation />);
    acceptWith(PASSWORD);

    await waitFor(() => {
      expect(publicInvitationService.accept).toHaveBeenCalledWith('secret-token-12345', PASSWORD);
    });

    expect(await screen.findByText('Your password is set')).toBeDefined();
    expect(screen.getByText(/password you just chose/i)).toBeDefined();
    expect(document.getElementById('btn-sign-in').getAttribute('href')).toBe('/');
  });

  it('D-88: a password the server refuses is shown beside the field, and the form stays usable', async () => {
    publicInvitationService.accept.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Invalid password: must contain at least 1 special characters.',
    });
    publicInvitationService.accept.mockResolvedValueOnce({ outcome: 'PASSWORD_SET' });

    render(<AcceptInvitation />);
    acceptWith('NoSymbolsHere1');

    expect(
      await screen.findByText('Invalid password: must contain at least 1 special characters.'),
    ).toBeDefined();
    expect(document.getElementById('alert-error-message')).toBeNull();

    acceptWith(PASSWORD);
    expect(await screen.findByText('Your password is set')).toBeDefined();
    expect(publicInvitationService.accept).toHaveBeenCalledTimes(2);
  });

  it('an existing account: its password is kept; says to sign in or use Forgot password (D-62, D-88)', async () => {
    publicInvitationService.accept.mockResolvedValueOnce({ outcome: 'EXISTING_ACCOUNT' });

    render(<AcceptInvitation />);
    acceptWith(PASSWORD);

    expect(await screen.findByText(/already have an Infinevo account/i)).toBeDefined();
    expect(screen.getByText(/Forgot password\?/)).toBeDefined();
    expect(document.getElementById('btn-sign-in')).not.toBeNull();
  });

  it('a reply with no outcome falls back to the password-set message', async () => {
    publicInvitationService.accept.mockResolvedValueOnce({ message: 'Accepted' });

    render(<AcceptInvitation />);
    acceptWith(PASSWORD);

    expect(await screen.findByText('Your password is set')).toBeDefined();
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
    acceptWith(PASSWORD);

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
    acceptWith(PASSWORD);

    await waitFor(() => {
      expect(screen.getByText('Try again in a few minutes')).toBeDefined();
    });
  });
});
