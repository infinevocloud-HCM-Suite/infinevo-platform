import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { EmployeeTdsCard } from './EmployeeTdsCard.jsx';
import { employeeTdsService } from './employeeTdsService.js';
import { currentFy, fyForApi, fyOptions } from '../tax/financialYear.js';
import * as useCanModule from '@shell/screens';

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
  id: 't2',
  regime: 'NEW',
  source: 'OFFICER',
  annual_gross: 1200000,
  annual_taxable_income: 1000000,
  annual_tax: 120000,
  effective_from_period: '2026-10',
  is_active: true,
  note: 'October figure',
  year_to_date: 70000,
  deducted_so_far: 70000,
  remaining: 50000,
  months_left: 5,
  monthly_share: 10000,
  next_pay_month: '2026-12',
  created_at: '2026-10-05T09:00:00Z',
  created_by: 'officer',
};

const OLDER = {
  ...RECORD,
  id: 't1',
  source: 'DECLARATION',
  annual_tax: 100000,
  effective_from_period: '2026-04',
  is_active: false,
  note: 'From the declaration',
  created_at: '2026-04-05T09:00:00Z',
};

function allow(actions) {
  vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => actions.includes(action));
}

const FY = currentFy();
const READ = 'payroll.tax_declaration.read';
const VERIFY = 'payroll.tax_declaration.verify';

describe('EmployeeTdsCard (D-145)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    allow([READ, VERIFY]);
  });

  it('shows the figures, the spread and the history for the financial year', async () => {
    employeeTdsService.get.mockResolvedValueOnce(RECORD);
    employeeTdsService.history.mockResolvedValueOnce([RECORD, OLDER]);

    render(<EmployeeTdsCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByTestId('employee-tds-figures')).toBeDefined());
    expect(employeeTdsService.get).toHaveBeenCalledWith('emp-1', fyForApi(FY));
    expect(employeeTdsService.history).toHaveBeenCalledWith('emp-1', fyForApi(FY));
    expect(screen.getByTestId('employee-tds-annual-tax').textContent).toContain('1,20,000.00');
    expect(screen.getByTestId('employee-tds-deducted').textContent).toContain('70,000.00');
    expect(screen.getByTestId('employee-tds-remaining').textContent).toContain('50,000.00');
    expect(screen.getByTestId('employee-tds-months-left').textContent).toBe('5');
    expect(screen.getByTestId('employee-tds-monthly-share').textContent).toContain('10,000.00');
    const figures = within(screen.getByTestId('employee-tds-figures'));
    expect(figures.getByText('Oct 2026')).toBeDefined();
    expect(figures.getByText('Officer')).toBeDefined();
    expect(figures.getByText('October figure')).toBeDefined();
    expect(figures.getByText('Monthly share (next pay run: Dec 2026)')).toBeDefined();
    expect(screen.getByTestId('employee-tds-history')).toBeDefined();
    expect(screen.getByText('Superseded')).toBeDefined();
    expect(screen.getByText('From the declaration')).toBeDefined();
  });

  it('says there is no record for the year when the server has none', async () => {
    employeeTdsService.get.mockRejectedValueOnce({ code: 'NOT_FOUND', message: 'No TDS record' });
    employeeTdsService.history.mockResolvedValueOnce([]);

    render(<EmployeeTdsCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByTestId('employee-tds-empty')).toBeDefined());
    expect(screen.getByText(`No TDS record for FY ${FY}`)).toBeDefined();
    expect(screen.queryByTestId('employee-tds-history')).toBeNull();
  });

  it('reads the year chosen in the picker', async () => {
    employeeTdsService.get.mockResolvedValue(RECORD);
    employeeTdsService.history.mockResolvedValue([RECORD]);
    const previous = fyOptions(3)[1].value;

    render(<EmployeeTdsCard employeeId="emp-1" />);
    await waitFor(() => expect(screen.getByTestId('employee-tds-figures')).toBeDefined());

    fireEvent.mouseDown(screen.getByRole('combobox'));
    fireEvent.click(await screen.findByText(previous, { selector: '.ant-select-item-option-content' }));

    await waitFor(() => expect(employeeTdsService.get).toHaveBeenCalledWith('emp-1', fyForApi(previous)));
    expect(employeeTdsService.history).toHaveBeenCalledWith('emp-1', fyForApi(previous));
  });

  it('Edit opens the drawer, and a save re-reads the card', async () => {
    employeeTdsService.get.mockResolvedValue(RECORD);
    employeeTdsService.history.mockResolvedValue([RECORD]);
    employeeTdsService.save.mockResolvedValueOnce({ ...RECORD, annual_tax: 90000 });

    render(<EmployeeTdsCard employeeId="emp-1" />);
    await waitFor(() => expect(screen.getByTestId('employee-tds-figures')).toBeDefined());
    const readsBefore = employeeTdsService.get.mock.calls.length;

    fireEvent.click(screen.getByTestId('btn-edit-tds'));
    await waitFor(() => expect(document.getElementById('tds-input-annual-tax').value).toBe('120000'));
    fireEvent.change(document.getElementById('tds-input-annual-tax'), { target: { value: '90000' } });
    fireEvent.change(document.getElementById('tds-input-note'), { target: { value: 'Revised' } });
    fireEvent.click(screen.getByTestId('employee-tds-save'));

    await waitFor(() => expect(employeeTdsService.save).toHaveBeenCalled());
    expect(employeeTdsService.save.mock.calls[0][2].annual_tax).toBe('90000');
    await waitFor(() => expect(employeeTdsService.get.mock.calls.length).toBeGreaterThan(readsBefore));
  });

  it('with no pay run left in the year the label names no month', async () => {
    employeeTdsService.get.mockResolvedValueOnce({ ...RECORD, next_pay_month: null, months_left: 0, monthly_share: 0 });
    employeeTdsService.history.mockResolvedValueOnce([]);

    render(<EmployeeTdsCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByTestId('employee-tds-figures')).toBeDefined());
    const figures = within(screen.getByTestId('employee-tds-figures'));
    expect(figures.getByText('Monthly share')).toBeDefined();
    expect(screen.getByTestId('employee-tds-months-left').textContent).toBe('0');
  });

  it('without payroll.tax_declaration.verify there is no Edit', async () => {
    allow([READ]);
    employeeTdsService.get.mockResolvedValueOnce(RECORD);
    employeeTdsService.history.mockResolvedValueOnce([RECORD]);

    render(<EmployeeTdsCard employeeId="emp-1" />);

    await waitFor(() => expect(screen.getByTestId('employee-tds-figures')).toBeDefined());
    expect(screen.queryByTestId('btn-edit-tds')).toBeNull();
  });

  it('without payroll.tax_declaration.read the card is not shown and nothing is read', () => {
    allow([]);

    render(<EmployeeTdsCard employeeId="emp-1" />);

    expect(screen.queryByTestId('employee-tds-card')).toBeNull();
    expect(employeeTdsService.get).not.toHaveBeenCalled();
  });
});
