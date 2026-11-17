import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { StatutoryProfileCard } from './StatutoryProfileCard.jsx';
import { statutoryProfileService } from './statutoryProfileService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./statutoryProfileService.js', () => ({
  statutoryProfileService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

const PROFILE = {
  eligibleForPf: true,
  eligibleForPt: true,
  eligibleForLwf: false,
  eligibleForEsi: false,
  eligibleForEps: true,
  contributesEpsOnHigherWages: false,
  director: true,
  pfAccountNumber: 'MH/BAN/0012345/000/0001234',
  uan: '100123456789',
  esiNumber: '',
};

function value(label) {
  const th = screen.getByText(label).closest('th');
  return th.nextElementSibling.textContent;
}

describe('StatutoryProfileCard - read view (W-76.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('shows the loaded ticks and numbers', async () => {
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    render(<StatutoryProfileCard employeeId="emp-1" />);

    expect(statutoryProfileService.get).toHaveBeenCalledWith('emp-1');
    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(value("Employees' Provident Fund (EPF)")).toBe('Yes');
    expect(value('Employee State Insurance (ESI)')).toBe('No');
    expect(value('Director')).toBe('Yes');
    expect(value('PF account number')).toBe('MH/BAN/0012345/000/0001234');
    expect(value('ESI number')).toBe('Not set');
  });

  it('D-141: shows TDS applicable Yes when the flag is true', async () => {
    statutoryProfileService.get.mockResolvedValueOnce({ ...PROFILE, tdsApplicable: true });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(screen.getByTestId('statutory-tds-applicable').textContent).toBe('Yes');
    expect(value('TDS applicable')).toBe('Yes');
  });

  it('D-141: shows TDS applicable No when the flag is false', async () => {
    statutoryProfileService.get.mockResolvedValueOnce({ ...PROFILE, tdsApplicable: false });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(screen.getByTestId('statutory-tds-applicable').textContent).toBe('No');
  });

  it('has no inputs and no Save, and points the officer to the drawer', async () => {
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    const { container } = render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(container.querySelectorAll('input')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
    expect(screen.getByTestId('statutory-edit-hint').textContent).toBe('Edit in Set up salary or Revise');
    expect(statutoryProfileService.save).not.toHaveBeenCalled();
  });

  it('shows no edit hint to a read-only role such as finance', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(screen.queryByTestId('statutory-edit-hint')).toBeNull();
  });
});
