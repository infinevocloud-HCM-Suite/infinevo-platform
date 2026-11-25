import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { StatutoryProfileCard } from './StatutoryProfileCard.jsx';
import { statutoryProfileService } from './statutoryProfileService.js';
import { statutoryService } from '../settings/statutoryService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./statutoryProfileService.js', () => ({
  statutoryProfileService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

vi.mock('../settings/statutoryService.js', () => ({
  statutoryService: {
    get: vi.fn().mockResolvedValue({ enabled: true, source: 'PERSISTED' }),
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
    expect(value('TDS applicable')).toContain('Yes');
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

  it('D-143: shows Edit instead of the hint when the parent passes canManage', async () => {
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    render(<StatutoryProfileCard employeeId="emp-1" canManage onEdit={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    const edit = screen.getByTestId('statutory-edit');
    expect(edit.id).toBe('btn-edit-statutory');
    expect(edit.textContent).toContain('Edit');
    expect(screen.queryByTestId('statutory-edit-hint')).toBeNull();
  });

  it('D-143: shows no Edit without canManage', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    render(<StatutoryProfileCard employeeId="emp-1" canManage={false} onEdit={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(screen.queryByTestId('statutory-edit')).toBeNull();
  });

  it('D-143: Edit calls onEdit with the profile the card shows', async () => {
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);
    const onEdit = vi.fn();

    render(<StatutoryProfileCard employeeId="emp-1" canManage onEdit={onEdit} />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    fireEvent.click(screen.getByTestId('statutory-edit'));
    expect(onEdit).toHaveBeenCalledWith(PROFILE);
    expect(statutoryProfileService.save).not.toHaveBeenCalled();
  });

  it('D-145: says new employees start as TDS applicable, and no company note while TDS is on', async () => {
    statutoryProfileService.get.mockResolvedValueOnce(PROFILE);

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByText('100123456789')).toBeDefined());
    expect(statutoryService.get).toHaveBeenCalledWith('tds');
    expect(screen.getByTestId('tds-new-employee-help').textContent).toBe('New employees start as TDS applicable');
    expect(screen.queryByTestId('tds-company-off-note')).toBeNull();
  });

  it('D-145: with company TDS off the TDS row says the setting waits for it', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: false, source: 'PERSISTED' });
    statutoryProfileService.get.mockResolvedValueOnce({ ...PROFILE, tdsApplicable: true });

    render(<StatutoryProfileCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByTestId('tds-company-off-note')).toBeDefined());
    expect(screen.getByTestId('tds-company-off-note').textContent).toBe(
      'Company TDS is off. This setting takes effect when it is turned on'
    );
    expect(screen.getByTestId('statutory-tds-applicable').textContent).toBe('Yes');
  });
});
