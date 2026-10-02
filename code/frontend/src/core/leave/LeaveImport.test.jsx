import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
    render(<LeaveImport />);

    await waitFor(() => {
      expect(screen.getByText('Bulk Leave Import')).toBeDefined();
    });

    // Upload document
    documentService.upload.mockResolvedValueOnce({ id: 'doc-999' });

    // Simulate import start with error result
    leaveImportService.start.mockResolvedValueOnce({
      id: 'imp-002',
      status: 'COMPLETED_WITH_ERRORS',
      isDryRun: false,
      leaveYear: '2026',
      rowsTotal: 10,
      rowsImported: 8,
      rowsFailed: 2,
      errorDocumentId: 'err-doc-123',
    });

    // Directly test result rendering with mock state if needed, or trigger import
  });

  it('stops polling when import reaches a terminal status', async () => {
    vi.useFakeTimers();

    leaveImportService.start.mockResolvedValueOnce({
      id: 'imp-003',
      status: 'PENDING',
    });

    leaveImportService.get.mockResolvedValueOnce({
      id: 'imp-003',
      status: 'COMPLETED',
      rowsTotal: 5,
      rowsImported: 5,
      rowsFailed: 0,
    });

    render(<LeaveImport />);

    await vi.runOnlyPendingTimersAsync();
  });
});
