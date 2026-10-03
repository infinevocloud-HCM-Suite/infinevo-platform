import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PriorPayrollPage } from './PriorPayrollPage.jsx';
import { priorPayrollService } from './priorPayrollService.js';
import { currentFy } from '../tax/financialYear.js';
import * as useCanModule from '@shell/screens';

vi.mock('./priorPayrollService.js', () => ({
  priorPayrollService: {
    template: vi.fn(),
    upload: vi.fn(),
    import: vi.fn(),
    imports: vi.fn(),
    months: vi.fn(),
    remove: vi.fn(),
    status: vi.fn(),
    errorFileLink: vi.fn(),
  },
}));

vi.mock('./MidYearBanner.jsx', () => ({
  MidYearBanner: () => null,
}));

describe('PriorPayrollPage (W-47.6 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    priorPayrollService.imports.mockResolvedValue({ content: [], totalElements: 0 });
    priorPayrollService.months.mockResolvedValue({ content: [], totalElements: 0 });

    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();
    window.open = vi.fn();
  });

  it('chooses file and clicks Check file which triggers upload and dryRun import', async () => {
    priorPayrollService.upload.mockResolvedValueOnce('doc-1');
    priorPayrollService.import.mockResolvedValueOnce({
      id: 'imp-1',
      rows_total: 5,
      rows_failed: 0,
      rows_imported: 5,
    });

    const { container } = render(<PriorPayrollPage />);

    const input = container.querySelector('input[type="file"]');
    const file = new File(['emp,period\n1,2026-04'], 'prior.csv', { type: 'text/csv' });
    fireEvent.change(input, { target: { files: [file] } });

    const checkBtn = await screen.findByRole('button', { name: /check file/i });
    await waitFor(() => expect(checkBtn.disabled).toBe(false), { timeout: 5000 });
    fireEvent.click(checkBtn);

    await waitFor(
      () => {
        expect(priorPayrollService.upload).toHaveBeenCalledWith(file);
        expect(priorPayrollService.import).toHaveBeenCalledWith({
          documentId: 'doc-1',
          financialYear: currentFy(),
          dryRun: true,
        });
      },
      { timeout: 5000 }
    );
  }, 30000);

  it('Import button is disabled before check, enabled after check, and disabled again when file changes', async () => {
    priorPayrollService.upload.mockResolvedValue('doc-1');
    priorPayrollService.import.mockResolvedValue({
      id: 'imp-1',
      rows_total: 5,
      rows_failed: 0,
      rows_imported: 5,
    });

    const { container } = render(<PriorPayrollPage />);

    const importBtn = screen.getByRole('button', { name: /^import$/i });
    expect(importBtn.disabled).toBe(true);

    const input = container.querySelector('input[type="file"]');
    const file1 = new File(['a'], 'file1.csv', { type: 'text/csv' });
    fireEvent.change(input, { target: { files: [file1] } });

    expect(importBtn.disabled).toBe(true);

    const checkBtn = await screen.findByRole('button', { name: /check file/i });
    await waitFor(() => expect(checkBtn.disabled).toBe(false), { timeout: 5000 });
    fireEvent.click(checkBtn);

    await waitFor(
      () => {
        expect(importBtn.disabled).toBe(false);
      },
      { timeout: 5000 }
    );

    const file2 = new File(['b'], 'file2.csv', { type: 'text/csv' });
    const input2 = container.querySelector('input[type="file"]');
    fireEvent.change(input2, { target: { files: [file2] } });

    await waitFor(
      () => {
        expect(importBtn.disabled).toBe(true);
      },
      { timeout: 5000 }
    );
  }, 30000);

  it('clicking Import and confirming in Popconfirm calls import with dryRun: false and same documentId', async () => {
    priorPayrollService.upload.mockResolvedValueOnce('doc-42');
    priorPayrollService.import
      .mockResolvedValueOnce({
        id: 'imp-dry',
        rows_total: 10,
        rows_failed: 0,
        rows_imported: 10,
      })
      .mockResolvedValueOnce({
        id: 'imp-real',
        rows_total: 10,
        rows_failed: 0,
        rows_imported: 10,
      });

    const { container } = render(<PriorPayrollPage />);

    const input = container.querySelector('input[type="file"]');
    const file = new File(['emp,period'], 'records.csv', { type: 'text/csv' });
    fireEvent.change(input, { target: { files: [file] } });

    const checkBtn = await screen.findByRole('button', { name: /check file/i });
    await waitFor(() => expect(checkBtn.disabled).toBe(false), { timeout: 5000 });
    fireEvent.click(checkBtn);

    await waitFor(
      () => {
        expect(priorPayrollService.upload).toHaveBeenCalled();
      },
      { timeout: 5000 }
    );

    const importBtn = screen.getByText('Import').closest('button');
    await waitFor(() => expect(importBtn.disabled).toBe(false), { timeout: 5000 });
    fireEvent.click(importBtn);

    const confirmBtn = await screen.findByRole('button', { name: /ok|yes/i }, { timeout: 5000 });
    fireEvent.click(confirmBtn);

    await waitFor(
      () => {
        expect(priorPayrollService.import).toHaveBeenCalledWith({
          documentId: 'doc-42',
          financialYear: currentFy(),
          dryRun: false,
        });
      },
      { timeout: 5000 }
    );
  }, 30000);

  it('shows Download errors button when rows_failed > 0 and clicking it calls errorFileLink', async () => {
    window.open = vi.fn();
    priorPayrollService.upload.mockResolvedValue('doc-1');
    priorPayrollService.errorFileLink.mockResolvedValue('https://storage/link/err.csv');

    priorPayrollService.import.mockResolvedValueOnce({
      id: 'imp-fail',
      rows_total: 5,
      rows_failed: 2,
      rows_imported: 3,
      error_document_id: 'err-1',
    });

    const { container } = render(<PriorPayrollPage />);

    const input = container.querySelector('input[type="file"]');
    fireEvent.change(input, { target: { files: [new File(['x'], 'f.csv', { type: 'text/csv' })] } });

    const checkBtn = await screen.findByRole('button', { name: /check file/i });
    await waitFor(() => expect(checkBtn.disabled).toBe(false), { timeout: 5000 });
    fireEvent.click(checkBtn);

    const downloadErrorsBtn = await screen.findByRole(
      'button',
      { name: /download errors/i },
      { timeout: 5000 }
    );
    expect(downloadErrorsBtn).toBeDefined();

    fireEvent.click(downloadErrorsBtn);

    await waitFor(
      () => {
        expect(priorPayrollService.errorFileLink).toHaveBeenCalledWith('err-1');
        expect(window.open).toHaveBeenCalledWith('https://storage/link/err.csv', '_blank', 'noopener');
      },
      { timeout: 5000 }
    );
  }, 30000);

  it('renders Delete button in months table when useCan is true and hides it when false', async () => {
    const monthRow = {
      id: 'm-1',
      employee_number: 'EMP001',
      employee_name: 'Alice Smith',
      period: '2026-04',
      gross_earnings: 50000,
      epf_employee: 1800,
      esi_employee: 0,
      professional_tax: 200,
      tds: 2500,
      net_pay: 45500,
    };
    priorPayrollService.months.mockResolvedValue({
      content: [monthRow],
      totalElements: 1,
    });

    // 1. With useCan -> true
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    const { unmount } = render(<PriorPayrollPage />);

    expect(await screen.findByText('EMP001 - Alice Smith', {}, { timeout: 5000 })).toBeDefined();
    expect(screen.getByRole('button', { name: /delete/i })).toBeDefined();

    unmount();

    // 2. With useCan -> false
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    render(<PriorPayrollPage />);

    expect(await screen.findByText('EMP001 - Alice Smith', {}, { timeout: 5000 })).toBeDefined();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  }, 30000);

  it('Download template calls template() and creates blob URL', async () => {
    const csvContent = 'employee_number,period,gross_earnings\n';
    priorPayrollService.template.mockResolvedValueOnce(csvContent);

    render(<PriorPayrollPage />);

    const dlBtn = screen.getByRole('button', { name: /download template/i });
    fireEvent.click(dlBtn);

    await waitFor(
      () => {
        expect(priorPayrollService.template).toHaveBeenCalled();
        expect(window.URL.createObjectURL).toHaveBeenCalled();
        expect(window.URL.revokeObjectURL).toHaveBeenCalled();
      },
      { timeout: 5000 }
    );
  }, 30000);
});
