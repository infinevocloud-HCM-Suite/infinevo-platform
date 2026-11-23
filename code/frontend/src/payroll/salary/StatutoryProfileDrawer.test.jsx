import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { StatutoryProfileDrawer } from './StatutoryProfileDrawer.jsx';
import { statutoryProfileService } from './statutoryProfileService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

vi.mock('./statutoryProfileService.js', () => ({
  statutoryProfileService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

// Saved before D-141, so no tdsApplicable.
const PROFILE = {
  eligibleForPf: true,
  eligibleForEps: true,
  contributesEpsOnHigherWages: false,
  eligibleForPt: true,
  eligibleForEsi: false,
  eligibleForLwf: false,
  director: true,
  pfAccountNumber: 'MH/BAN/0012345/000/0001234',
  uan: '100123456789',
  esiNumber: null,
};

function checkbox(id) {
  return document.getElementById(id);
}

function renderDrawer(props = {}) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  render(
    <StatutoryProfileDrawer
      open
      onClose={onClose}
      onSaved={onSaved}
      employeeId="emp-1"
      profile={PROFILE}
      {...props}
    />
  );
  return { onClose, onSaved };
}

describe('StatutoryProfileDrawer (D-143)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fills the ticks and numbers from the profile', async () => {
    renderDrawer();

    expect(screen.getByTestId('statutory-profile-drawer')).toBeDefined();
    await waitFor(() => expect(document.getElementById('sp-input-uan').value).toBe('100123456789'));
    expect(checkbox('sp-chk-statutory-pf').checked).toBe(true);
    expect(checkbox('sp-chk-statutory-eps').checked).toBe(true);
    expect(checkbox('sp-chk-statutory-pt').checked).toBe(true);
    expect(checkbox('sp-chk-statutory-esi').checked).toBe(false);
    expect(checkbox('sp-chk-statutory-director').checked).toBe(true);
    expect(document.getElementById('sp-input-pf-account-number').value).toBe('MH/BAN/0012345/000/0001234');
    expect(document.getElementById('sp-input-esi-number').value).toBe('');
    expect(screen.getByText('Off: no TDS is deducted for this employee in pay runs')).toBeDefined();
  });

  it('reads a missing tdsApplicable as ticked', async () => {
    renderDrawer();

    await waitFor(() => expect(checkbox('sp-chk-statutory-tds').checked).toBe(true));
  });

  it('saves the full payload with tdsApplicable false after unticking, then closes', async () => {
    const saved = { ...PROFILE, id: 'p1', tdsApplicable: false };
    statutoryProfileService.save.mockResolvedValueOnce(saved);
    const { onClose, onSaved } = renderDrawer();

    await waitFor(() => expect(checkbox('sp-chk-statutory-tds').checked).toBe(true));
    fireEvent.click(checkbox('sp-chk-statutory-tds'));
    fireEvent.click(screen.getByTestId('statutory-profile-save'));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(statutoryProfileService.save).toHaveBeenCalledWith('emp-1', {
      eligibleForPf: true,
      eligibleForPt: true,
      eligibleForLwf: false,
      eligibleForEsi: false,
      eligibleForEps: true,
      contributesEpsOnHigherWages: false,
      director: true,
      pfAccountNumber: 'MH/BAN/0012345/000/0001234',
      uan: '100123456789',
      esiNumber: null,
      tdsApplicable: false,
    });
    expect(successMsg).toHaveBeenCalledWith('Success', 'Statutory details saved');
    expect(onSaved).toHaveBeenCalledWith(saved);
  });

  it('keeps the drawer open and shows the error when the save fails', async () => {
    const err = { code: 'VALIDATION', message: 'UAN must be exactly 12 digits' };
    statutoryProfileService.save.mockRejectedValueOnce(err);
    const { onClose, onSaved } = renderDrawer();

    await waitFor(() => expect(document.getElementById('sp-input-uan').value).toBe('100123456789'));
    fireEvent.click(screen.getByTestId('statutory-profile-save'));

    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
    expect(onClose).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
    expect(successMsg).not.toHaveBeenCalled();
    expect(screen.getByTestId('statutory-profile-drawer')).toBeDefined();
  });

  it('blocks the save on a bad UAN while EPF is ticked', async () => {
    renderDrawer();

    await waitFor(() => expect(document.getElementById('sp-input-uan').value).toBe('100123456789'));
    fireEvent.change(document.getElementById('sp-input-uan'), { target: { value: '123' } });
    fireEvent.click(screen.getByTestId('statutory-profile-save'));

    await waitFor(() => expect(screen.getByText('UAN must be exactly 12 digits')).toBeDefined());
    expect(statutoryProfileService.save).not.toHaveBeenCalled();
  });
});
