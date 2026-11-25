import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { BankFileDrawer } from './BankFileDrawer.jsx';
import { bankFileService, saveBlob } from './bankFileService.js';
import { payrunService } from './payrunService.js';

vi.mock('./bankFileService.js', () => ({
  bankFileService: { templates: vi.fn(), preview: vi.fn(), generate: vi.fn(), download: vi.fn() },
  saveBlob: vi.fn(),
}));
vi.mock('./payrunService.js', () => ({
  payrunService: { employees: vi.fn(), employeeNames: vi.fn() },
}));

describe('BankFileDrawer (W-88.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    bankFileService.templates.mockResolvedValue([
      { id: 'generic-neft', name: 'Generic bulk NEFT', builtIn: true },
      { id: 't1', name: 'HDFC bulk', builtIn: false },
    ]);
    payrunService.employees.mockResolvedValue({
      content: [
        { employee_id: 'e1', employee_number: 'E-101', net_pay: '65000.00' },
        { employee_id: 'e2', employee_number: 'E-102', net_pay: '1000.00' },
      ],
    });
    payrunService.employeeNames.mockResolvedValue(new Map([['e1', 'Asha Rao'], ['e2', 'Ravi Menon']]));
  });

  it('previews everyone with the built-in and lists who is left out and why', async () => {
    bankFileService.preview.mockResolvedValue({
      headings: ['No'],
      rows: [['E-101']],
      rowCount: 1,
      fileTotal: '65000.00',
      selectedNetPay: '66000.00',
      omitted: [{ employeeNumber: 'E-102', reason: 'payment mode is CASH' }],
    });
    render(<BankFileDrawer open payrunId="run-1" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByText('Everyone in this run (2)')).toBeDefined());

    fireEvent.click(screen.getByTestId('btn-bank-preview'));

    await waitFor(() => expect(screen.getByTestId('bank-preview')).toBeDefined());
    expect(bankFileService.preview).toHaveBeenCalledWith('run-1', { templateId: 'generic-neft', employeeIds: [] });
    expect(screen.getByTestId('bank-omitted').textContent).toContain('E-102: payment mode is CASH');
  });

  it('"only the ones I tick" sends the ticked ids, makes the file and saves it', async () => {
    const onGenerated = vi.fn();
    const onClose = vi.fn();
    bankFileService.generate.mockResolvedValue({ id: 'x1', fileName: 'HDFC_bulk_2026-11.csv' });
    const blob = new Blob(['a']);
    bankFileService.download.mockResolvedValue(blob);
    render(<BankFileDrawer open payrunId="run-1" onClose={onClose} onGenerated={onGenerated} />);
    await waitFor(() => expect(screen.getByText('Only the ones I tick')).toBeDefined());

    fireEvent.click(screen.getByText('Only the ones I tick'));
    expect(screen.getByTestId('btn-bank-generate').disabled).toBe(true);
    await waitFor(() => expect(screen.getByText('Asha Rao')).toBeDefined());
    const checkboxes = screen.getByTestId('bank-employee-table').querySelectorAll('tbody input[type="checkbox"]');
    fireEvent.click(checkboxes[0]);
    fireEvent.click(screen.getByTestId('btn-bank-generate'));

    await waitFor(() =>
      expect(bankFileService.generate).toHaveBeenCalledWith('run-1', { templateId: 'generic-neft', employeeIds: ['e1'] }),
    );
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'HDFC_bulk_2026-11.csv'));
    expect(onGenerated).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('shows the server message when making the file fails', async () => {
    bankFileService.generate.mockRejectedValue({ message: 'No employee in this selection can be paid by bank transfer' });
    render(<BankFileDrawer open payrunId="run-1" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByTestId('btn-bank-generate')).toBeDefined());
    fireEvent.click(screen.getByTestId('btn-bank-generate'));
    await waitFor(() =>
      expect(screen.getByText('No employee in this selection can be paid by bank transfer')).toBeDefined(),
    );
  });

  it('loads every page of a run bigger than one page of 100', async () => {
    payrunService.employees.mockImplementation((_id, { page }) =>
      Promise.resolve(
        page === 0
          ? { content: [{ employee_id: 'e1', employee_number: 'E-101' }, { employee_id: 'e2', employee_number: 'E-102' }], last: false }
          : { content: [{ employee_id: 'e3', employee_number: 'E-103' }], last: true },
      ),
    );
    render(<BankFileDrawer open payrunId="run-1" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByText('Everyone in this run (3)')).toBeDefined());
    expect(payrunService.employees).toHaveBeenCalledWith('run-1', { inclusion: 'INCLUDED', page: 1, size: 100 });
  });
});
