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
      esiNumber: '11000123456789012',
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
      expect(payload).toHaveProperty('esiNumber', '11000123456789012');
      expect(Object.keys(payload)).toHaveLength(10);
    });
  });
});
