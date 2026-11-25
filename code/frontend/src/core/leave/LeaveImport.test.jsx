import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import {
  LeaveImport,
  currentLeaveYearStart,
  leaveYearLabel,
  leaveYearValue,
} from './LeaveImport.jsx';
import { leaveImportService } from './leaveImportService.js';
import { documentService } from '../document/documentService.js';
import { companyProfileService } from '../settings/companyProfileService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveImportService.js', () => ({
  leaveImportService: {
    start: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
    downloadTemplate: vi.fn(),
  },
}));

vi.mock('../document/documentService.js', () => ({
  documentService: {
    upload: vi.fn(),
    link: vi.fn(),
  },
}));

vi.mock('../settings/companyProfileService.js', () => ({
  companyProfileService: { get: vi.fn() },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('LeaveImport component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    companyProfileService.get.mockResolvedValue({ leaveYearStartMonth: 4 });
    leaveImportService.history.mockResolvedValue({
      content: [
        {
          id: 'imp-001',
          status: 'COMPLETED',
          isDryRun: true,
          leaveYear: '2026',
          rowsTotal: 10,
          rowsImported: 10,
          rowsFailed: 0,
          startedAt: '2026-10-01T10:00:00Z',
        },
      ],
      totalElements: 1,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('has dry run on by default and renders history', async () => {
    render(<LeaveImport />);

    await waitFor(() => {
      expect(screen.getByText('Bulk Leave Import')).toBeDefined();
      expect(screen.getByText('Start Dry Run')).toBeDefined();
      expect(screen.getByText('Dry Run (No database writes):')).toBeDefined();
      expect(screen.getByText('imp-001')).toBeDefined();
    });
  });

  it('renders result panel and error alert when rowsFailed > 0', async () => {
    documentService.upload.mockResolvedValueOnce({ id: 'doc-999' });
    leaveImportService.start.mockResolvedValueOnce({
      id: 'imp-002',
      status: 'COMPLETED_WITH_ERRORS',
      isDryRun: true,
      leaveYear: '2026',
      rowsTotal: 10,
      rowsImported: 8,
      rowsFailed: 2,
      errorDocumentId: 'err-doc-123',
    });

    const { container } = render(<LeaveImport />);

    await waitFor(() => {
      expect(screen.getByText('Bulk Leave Import')).toBeDefined();
    });

    const fileInput = container.querySelector('input[type="file"]');
    const testFile = new File(['emp,days\n1,10'], 'leaves.csv', { type: 'text/csv' });
    fireEvent.change(fileInput, { target: { files: [testFile] } });

    await waitFor(() => {
      expect(documentService.upload).toHaveBeenCalledWith(testFile, 'LEAVE_ATTACHMENT');
    });

    const startBtn = screen.getByRole('button', { name: /Start Dry Run/i });
    expect(startBtn.hasAttribute('disabled')).toBe(false);
    fireEvent.click(startBtn);

    await waitFor(() => {
      expect(leaveImportService.start).toHaveBeenCalledWith({
        documentId: 'doc-999',
        // D-49: an April-cycle company sends the YYYY-YY form the server requires, never a bare year.
        leaveYear: leaveYearValue(currentLeaveYearStart(dayjs(), 4), 4),
        dryRun: true,
      });
      expect(screen.getByText('Import Run Results')).toBeDefined();
      expect(screen.getByText('Completed with Errors')).toBeDefined();
      expect(screen.getByText('Errors Encountered')).toBeDefined();
    });

    // D-51: the report is a button that opens the stored document, not an id printed as text.
    expect(screen.queryByText(/err-doc-123/)).toBeNull();
    documentService.link.mockResolvedValueOnce({ url: 'https://files.test/err-doc-123' });
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    fireEvent.click(screen.getByRole('button', { name: /Download error report/i }));
    await waitFor(() => {
      expect(documentService.link).toHaveBeenCalledWith('err-doc-123');
      expect(openSpy).toHaveBeenCalledWith('https://files.test/err-doc-123', '_blank', 'noopener');
    });
    openSpy.mockRestore();
  });

  it('sends a bare year for a company whose leave year starts in January (D-49)', async () => {
    companyProfileService.get.mockResolvedValue({ leaveYearStartMonth: 1 });
    documentService.upload.mockResolvedValueOnce({ id: 'doc-jan' });
    leaveImportService.start.mockResolvedValueOnce({ id: 'imp-jan', status: 'COMPLETED', rowsFailed: 0 });

    const { container } = render(<LeaveImport />);
    await waitFor(() => expect(companyProfileService.get).toHaveBeenCalled());

    const fileInput = container.querySelector('input[type="file"]');
    fireEvent.change(fileInput, { target: { files: [new File(['a'], 'l.csv', { type: 'text/csv' })] } });
    await waitFor(() => expect(documentService.upload).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /Start Dry Run/i }));
    await waitFor(() => {
      expect(leaveImportService.start).toHaveBeenCalledWith({
        documentId: 'doc-jan',
        leaveYear: String(dayjs().year()),
        dryRun: true,
      });
    });
  });

  it('formats the leave year the way the server stores it (D-49)', () => {
    expect(leaveYearValue(2026, 4)).toBe('2026-27');
    expect(leaveYearValue(2099, 4)).toBe('2099-00');
    expect(leaveYearValue(2026, 1)).toBe('2026');
    expect(leaveYearLabel(2026, 4)).toBe('2026-27 (Apr 2026 to Mar 2027)');
    expect(leaveYearLabel(2026, 1)).toBe('2026 (Jan 2026 to Dec 2026)');
    // February 2027 still belongs to the leave year that began in April 2026.
    expect(currentLeaveYearStart(dayjs('2027-02-15'), 4)).toBe(2026);
    expect(currentLeaveYearStart(dayjs('2027-04-01'), 4)).toBe(2027);
    expect(currentLeaveYearStart(dayjs('2027-02-15'), 1)).toBe(2027);
  });

  it('stops polling when import reaches a terminal status', async () => {
    documentService.upload.mockResolvedValueOnce({ id: 'doc-pending-1' });
    leaveImportService.start.mockResolvedValueOnce({
      id: 'imp-003',
      status: 'PENDING',
      leaveYear: '2026',
      isDryRun: true,
    });

    leaveImportService.get.mockResolvedValueOnce({
      id: 'imp-003',
      status: 'COMPLETED',
      rowsTotal: 5,
      rowsImported: 5,
      rowsFailed: 0,
      isDryRun: true,
    });

    const { container } = render(<LeaveImport />);

    const fileInput = container.querySelector('input[type="file"]');
    const testFile = new File(['emp,days\n1,5'], 'leaves.csv', { type: 'text/csv' });
    fireEvent.change(fileInput, { target: { files: [testFile] } });

    await waitFor(() => {
      expect(documentService.upload).toHaveBeenCalledWith(testFile, 'LEAVE_ATTACHMENT');
    });

    vi.useFakeTimers();

    const startBtn = screen.getByRole('button', { name: /Start Dry Run/i });
    fireEvent.click(startBtn);

    await vi.advanceTimersByTimeAsync(50);
    expect(leaveImportService.start).toHaveBeenCalled();

    // Advance 2s to fire polling interval
    await vi.advanceTimersByTimeAsync(2000);
    expect(leaveImportService.get).toHaveBeenCalledWith('imp-003');
    expect(leaveImportService.get).toHaveBeenCalledTimes(1);

    // Advance another 4s; since status was COMPLETED, interval was cleared and no further get called
    await vi.advanceTimersByTimeAsync(4000);
    expect(leaveImportService.get).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
  });

  it('renders Download Sample Template button and triggers file download on click', async () => {
    leaveImportService.downloadTemplate.mockResolvedValueOnce(
      'employee_number,leave_type_code,days\nEMP-001,SL,12.00\n'
    );
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();

    render(<LeaveImport />);

    await waitFor(() => {
      expect(screen.getByText('Bulk Leave Import')).toBeDefined();
    });

    const downloadBtn = screen.getByRole('button', { name: /Download Sample Template/i });
    expect(downloadBtn).toBeDefined();

    fireEvent.click(downloadBtn);

    await waitFor(() => {
      expect(leaveImportService.downloadTemplate).toHaveBeenCalled();
      expect(window.URL.createObjectURL).toHaveBeenCalled();
    });
  });
});
