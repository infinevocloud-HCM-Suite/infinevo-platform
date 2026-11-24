import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TaxRecalcFailuresPage } from './TaxRecalcFailuresPage.jsx';
import { taxRecalcFailureService } from './taxRecalcFailureService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { fyForApi, fyOptions } from './financialYear.js';
import * as useCanModule from '@shell/screens';

vi.mock('./taxRecalcFailureService.js', () => ({
  taxRecalcFailureService: {
    open: vi.fn(),
    retry: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

const READ = 'payroll.tax_declaration.read';
const VERIFY = 'payroll.tax_declaration.verify';

const FAILURE = {
  id: 'f1',
  employee_id: 'e1',
  employee_name: 'Asha Rao',
  financial_year: '2026-2027',
  trigger: 'SALARY_REVISION',
  error_message: 'IllegalStateException: no salary structure',
  attempts: 2,
  first_failed_at: '2026-11-20T05:00:00Z',
  last_failed_at: '2026-11-24T05:00:00Z',
};

function allow(actions) {
  vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => actions.includes(action));
}

describe('TaxRecalcFailuresPage (D-151)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    allow([READ, VERIFY]);
  });

  it('lists open failures with employee, year, reason and attempts, for every year by default', async () => {
    taxRecalcFailureService.open.mockResolvedValueOnce([FAILURE]);

    render(<TaxRecalcFailuresPage />);

    await waitFor(() => expect(screen.getByText('Asha Rao')).toBeDefined());
    expect(taxRecalcFailureService.open).toHaveBeenCalledWith(undefined);
    expect(screen.getByText('2026-2027')).toBeDefined();
    expect(screen.getByText('Salary revision')).toBeDefined();
    expect(screen.getByText('IllegalStateException: no salary structure')).toBeDefined();
    expect(screen.getByTestId('tax-recalc-retry')).toBeDefined();
  });

  it('says so when there is nothing to retry', async () => {
    taxRecalcFailureService.open.mockResolvedValueOnce([]);

    render(<TaxRecalcFailuresPage />);

    await waitFor(() => expect(screen.getByText('No failed tax recalculations')).toBeDefined());
  });

  it('reads the year chosen in the picker', async () => {
    taxRecalcFailureService.open.mockResolvedValue([]);
    const year = fyOptions(3)[0].value;

    render(<TaxRecalcFailuresPage />);
    await waitFor(() => expect(taxRecalcFailureService.open).toHaveBeenCalledTimes(1));

    fireEvent.mouseDown(screen.getByRole('combobox'));
    fireEvent.click(await screen.findByText(year, { selector: '.ant-select-item-option-content' }));

    await waitFor(() => expect(taxRecalcFailureService.open).toHaveBeenCalledWith(fyForApi(year)));
  });

  it('a retry that succeeds says so and reloads the list', async () => {
    taxRecalcFailureService.open.mockResolvedValueOnce([FAILURE]).mockResolvedValueOnce([]);
    taxRecalcFailureService.retry.mockResolvedValueOnce({ annual_tax: 52000 });

    render(<TaxRecalcFailuresPage />);
    await waitFor(() => expect(screen.getByTestId('tax-recalc-retry')).toBeDefined());

    fireEvent.click(screen.getByTestId('tax-recalc-retry'));

    await waitFor(() => expect(taxRecalcFailureService.retry).toHaveBeenCalledWith('f1'));
    await waitFor(() =>
      expect(successMsg).toHaveBeenCalledWith('Tax recalculated', 'Asha Rao, FY 2026-2027: annual tax 52000')
    );
    await waitFor(() => expect(screen.getByText('No failed tax recalculations')).toBeDefined());
  });

  it('a retry that fails again shows the error and reloads the list', async () => {
    const err = { code: 'TAX_RECALC_FAILED', message: 'Tax recalculation failed again: x' };
    taxRecalcFailureService.open.mockResolvedValue([FAILURE]);
    taxRecalcFailureService.retry.mockRejectedValueOnce(err);

    render(<TaxRecalcFailuresPage />);
    await waitFor(() => expect(screen.getByTestId('tax-recalc-retry')).toBeDefined());

    fireEvent.click(screen.getByTestId('tax-recalc-retry'));

    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
    await waitFor(() => expect(taxRecalcFailureService.open).toHaveBeenCalledTimes(2));
  });

  it('a list that cannot be read says so', async () => {
    taxRecalcFailureService.open.mockRejectedValueOnce({ message: 'Server down' });

    render(<TaxRecalcFailuresPage />);

    await waitFor(() => expect(screen.getByTestId('tax-recalc-load-error').textContent).toContain('Server down'));
  });

  it('read alone lists without a Retry button', async () => {
    allow([READ]);
    taxRecalcFailureService.open.mockResolvedValueOnce([FAILURE]);

    render(<TaxRecalcFailuresPage />);

    await waitFor(() => expect(screen.getByText('Asha Rao')).toBeDefined());
    expect(screen.queryByTestId('tax-recalc-retry')).toBeNull();
  });

  it('without read nothing is loaded', () => {
    allow([]);

    render(<TaxRecalcFailuresPage />);

    expect(taxRecalcFailureService.open).not.toHaveBeenCalled();
    expect(screen.queryByTestId('tax-recalc-failures-page')).toBeNull();
  });
});
