import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LeaveImport } from './LeaveImport.jsx';
import { leaveImportService } from './leaveImportService.js';
import { documentService } from '../document/documentService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./leaveImportService.js', () => ({
  leaveImportService: {
    start: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
  },
}));

vi.mock('../document/documentService.js', () => ({
  documentService: {
    upload: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('LeaveImport component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
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
        leaveYear: expect.any(String),
        dryRun: true,
      });
      expect(screen.getByText('Import Run Results')).toBeDefined();
      expect(screen.getByText('Completed with Errors')).toBeDefined();
      expect(screen.getByText('Errors Encountered')).toBeDefined();
      expect(screen.getByText(/Download error report document: err-doc-123/i)).toBeDefined();
    });
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
});
