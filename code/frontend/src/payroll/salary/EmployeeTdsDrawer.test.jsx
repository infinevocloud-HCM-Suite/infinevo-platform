import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EmployeeTdsDrawer } from './EmployeeTdsDrawer.jsx';
import { employeeTdsService } from './employeeTdsService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

vi.mock('./employeeTdsService.js', () => ({
  employeeTdsService: {
    get: vi.fn(),
    history: vi.fn(),
    save: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

const RECORD = {
  id: 't1',
  regime: 'OLD',
  source: 'DECLARATION',
  annual_gross: 1200000,
  annual_taxable_income: 1000000,
  annual_tax: 120000,
  effective_from_period: '2026-10',
  note: 'From the declaration',
};

// Form validation is asynchronous; a loaded full-suite run needs more than the default 1 s.
const SLOW = { timeout: 5000 };

const annualTax = () => document.getElementById('tds-input-annual-tax');
const note = () => document.getElementById('tds-input-note');

function renderDrawer(props = {}) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  render(
    <EmployeeTdsDrawer
      open
      onClose={onClose}
      onSaved={onSaved}
      employeeId="emp-1"
      fy="2026-27"
      record={RECORD}
      {...props}
    />
  );
  return { onClose, onSaved };
}

describe('EmployeeTdsDrawer (D-145)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fills the annual tax and effective month, shows the regime read-only and the replace warning', async () => {
    renderDrawer();

    await waitFor(() => expect(annualTax().value).toBe('120000'));
    expect(document.getElementById('tds-input-effective-month').value).toBe('Oct 2026');
    expect(screen.getByTestId('employee-tds-regime').textContent).toBe('Old');
    expect(screen.getByTestId('employee-tds-replace-warning').textContent).toContain(
      'This figure stays until you change it or apply a suggested figure'
    );
    expect(screen.getByTestId('employee-tds-replace-warning').textContent).not.toContain('will replace this figure');
    expect(screen.queryByTestId('employee-tds-drawer-suggestion')).toBeNull();
    expect(note().value).toBe('');
  });

  it('D-151: shows the suggestion a recalculation left beside the figure', async () => {
    renderDrawer({
      record: { ...RECORD, source: 'OFFICER', suggestion: { regime: 'NEW', annual_tax: 90000, suggested_at: '2026-11-24T05:00:00Z' } },
    });

    await waitFor(() => expect(annualTax().value).toBe('120000'));
    const shown = screen.getByTestId('employee-tds-drawer-suggestion').textContent;
    expect(shown).toContain('90,000.00');
    expect(shown).toContain('New regime');
  });

  it('saves the annual tax as text with the carried figures, then says so and closes', async () => {
    const saved = { ...RECORD, id: 't2', annual_tax: 96000.5 };
    employeeTdsService.save.mockResolvedValueOnce(saved);
    const { onClose, onSaved } = renderDrawer();

    await waitFor(() => expect(annualTax().value).toBe('120000'));
    fireEvent.change(annualTax(), { target: { value: '96000.50' } });
    fireEvent.change(note(), { target: { value: 'October figure from payroll' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(employeeTdsService.save).toHaveBeenCalledWith('emp-1', '2026-2027', {
      regime: 'OLD',
      annual_gross: '1200000',
      annual_taxable_income: '1000000',
      annual_tax: '96000.50',
      effective_from_period: '2026-10',
      note: 'October figure from payroll',
    });
    expect(successMsg).toHaveBeenCalledWith('Success', 'TDS figures saved');
    expect(onSaved).toHaveBeenCalledWith(saved);
  });

  it('with no record yet it starts at the new regime and zero gross and taxable income', async () => {
    employeeTdsService.save.mockResolvedValueOnce({ id: 't1' });
    const { onClose } = renderDrawer({ record: null });

    await waitFor(() => expect(document.getElementById('tds-input-effective-month').value).not.toBe(''));
    expect(screen.getByTestId('employee-tds-regime').textContent).toBe('New');
    fireEvent.change(annualTax(), { target: { value: '0' } });
    fireEvent.change(note(), { target: { value: 'No tax this year' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const body = employeeTdsService.save.mock.calls[0][2];
    expect(body).toMatchObject({ regime: 'NEW', annual_gross: '0', annual_taxable_income: '0', annual_tax: '0' });
    expect(body.effective_from_period >= '2026-04' && body.effective_from_period <= '2027-03').toBe(true);
  });

  it('needs a note', async () => {
    renderDrawer();

    await waitFor(() => expect(annualTax().value).toBe('120000'));
    fireEvent.click(screen.getByTestId('employee-tds-save'));

    await waitFor(() => expect(screen.getByText('Say why the figure is being set')).toBeDefined(), SLOW);
    expect(employeeTdsService.save).not.toHaveBeenCalled();
  });

  it('refuses a negative annual tax, an empty one and more than two decimal places', async () => {
    renderDrawer();
    await waitFor(() => expect(annualTax().value).toBe('120000'));
    fireEvent.change(note(), { target: { value: 'Correction' } });

    fireEvent.change(annualTax(), { target: { value: '-1' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));
    await waitFor(() => expect(screen.getByText('Annual tax must be a number, zero or more')).toBeDefined(), SLOW);

    fireEvent.change(annualTax(), { target: { value: '' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));
    await waitFor(() => expect(screen.getByText('Enter the annual tax')).toBeDefined(), SLOW);

    fireEvent.change(annualTax(), { target: { value: '10.123' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));
    await waitFor(() => expect(screen.getByText('Annual tax takes at most two decimal places')).toBeDefined(), SLOW);

    expect(employeeTdsService.save).not.toHaveBeenCalled();
  });

  it('keeps the drawer open and shows the server error when the save fails', async () => {
    const err = { code: 'VALIDATION_FAILED', message: 'Annual tax must be non-negative' };
    employeeTdsService.save.mockRejectedValueOnce(err);
    const { onClose, onSaved } = renderDrawer();

    await waitFor(() => expect(annualTax().value).toBe('120000'));
    fireEvent.change(note(), { target: { value: 'Correction' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));

    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
    expect(onClose).not.toHaveBeenCalled();
    expect(onSaved).not.toHaveBeenCalled();
  });
});
