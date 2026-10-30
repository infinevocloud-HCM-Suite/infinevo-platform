import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { StatutoryProfileCard } from './StatutoryProfileCard.jsx';
import { statutoryProfileService } from './statutoryProfileService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./statutoryProfileService.js', () => ({
  statutoryProfileService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

describe('StatutoryProfileCard component (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('loads profile on open and PUT sends all ten fields on submit', async () => {
    statutoryProfileService.get.mockResolvedValueOnce({
      eligibleForPf: true,
      eligibleForPt: true,
      eligibleForLwf: false,
      eligibleForEsi: true,
      eligibleForEps: true,
      contributesEpsOnHigherWages: false,
      director: false,
      pfAccountNumber: 'MH/BAN/0012345/000/0001234',
      uan: '100123456789',
      esiNumber: '1100012345',
    });
    statutoryProfileService.save.mockResolvedValueOnce({ id: 'p1' });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    expect(statutoryProfileService.get).toHaveBeenCalledWith('emp-1');

    await waitFor(() => {
      expect(screen.getByDisplayValue('100123456789')).toBeDefined();
    });

    const saveBtn = screen.getByRole('button', { name: /save statutory details/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(statutoryProfileService.save).toHaveBeenCalledTimes(1);
      const call = statutoryProfileService.save.mock.calls[0];
      expect(call[0]).toBe('emp-1');

      const payload = call[1];
      expect(payload).toHaveProperty('eligibleForPf', true);
      expect(payload).toHaveProperty('eligibleForPt', true);
      expect(payload).toHaveProperty('eligibleForLwf', false);
      expect(payload).toHaveProperty('eligibleForEsi', true);
      expect(payload).toHaveProperty('eligibleForEps', true);
      expect(payload).toHaveProperty('contributesEpsOnHigherWages', false);
      expect(payload).toHaveProperty('director', false);
      expect(payload).toHaveProperty('pfAccountNumber', 'MH/BAN/0012345/000/0001234');
      expect(payload).toHaveProperty('uan', '100123456789');
      expect(payload).toHaveProperty('esiNumber', '1100012345');
      expect(Object.keys(payload)).toHaveLength(10);
    });
  });

  it('refuses an 11-digit UAN before save and caps the three inputs at 16 / 32 / 32 (W-75.2)', async () => {
    statutoryProfileService.get.mockResolvedValueOnce({
      eligibleForPf: true,
      eligibleForEsi: true,
      uan: '10012345678',
      pfAccountNumber: '',
      esiNumber: '',
    });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('10012345678')).toBeDefined();
    });
    expect(screen.getByPlaceholderText('000000000000').getAttribute('maxlength')).toBe('16');
    expect(screen.getByPlaceholderText('PF Account Number').getAttribute('maxlength')).toBe('32');
    expect(screen.getByPlaceholderText('ESI Number').getAttribute('maxlength')).toBe('32');

    fireEvent.click(screen.getByRole('button', { name: /save statutory details/i }));

    await waitFor(() => {
      expect(screen.getByText('UAN must be exactly 12 digits')).toBeDefined();
    });
    expect(statutoryProfileService.save).not.toHaveBeenCalled();
  });

  it('shows a 400 field error from the server under its field (W-75.1)', async () => {
    statutoryProfileService.get.mockResolvedValueOnce({ eligibleForPf: true, pfAccountNumber: 'MH/BAN/1' });
    statutoryProfileService.save.mockRejectedValueOnce({
      code: 'VALIDATION_FAILED',
      message: 'Validation failed',
      fieldErrors: { pfAccountNumber: 'PF account number must be at most 32 characters' },
    });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('MH/BAN/1')).toBeDefined();
    });
    fireEvent.click(screen.getByRole('button', { name: /save statutory details/i }));

    await waitFor(() => {
      expect(screen.getByText('PF account number must be at most 32 characters')).toBeDefined();
    });
  });
});
