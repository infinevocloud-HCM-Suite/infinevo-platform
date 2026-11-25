import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { BankFileHistory } from './BankFileHistory.jsx';
import { bankFileService, saveBlob } from './bankFileService.js';

vi.mock('./bankFileService.js', () => ({
  bankFileService: { history: vi.fn(), download: vi.fn() },
  saveBlob: vi.fn(),
}));

describe('BankFileHistory (W-88.2)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows nothing when no file was made', async () => {
    bankFileService.history.mockResolvedValue([]);
    const { container } = render(<BankFileHistory payrunId="run-1" />);
    await waitFor(() => expect(bankFileService.history).toHaveBeenCalledWith('run-1'));
    await waitFor(() => expect(container.querySelector('[data-testid="bank-file-history"]')).toBeNull());
  });

  it('lists each file and downloads it again', async () => {
    bankFileService.history.mockResolvedValue([
      {
        id: 'x1',
        templateName: 'HDFC bulk',
        fileName: 'HDFC_bulk_2026-11.csv',
        rowCount: 1,
        selectedEmployeeCount: 1,
        fileTotal: '65000.00',
        createdAt: '2026-11-25T10:00:00Z',
        createdBy: 'officer',
      },
    ]);
    const blob = new Blob(['a']);
    bankFileService.download.mockResolvedValue(blob);
    render(<BankFileHistory payrunId="run-1" />);

    await waitFor(() => expect(screen.getByText('HDFC bulk')).toBeDefined());
    expect(screen.getByText('(of 1 ticked)')).toBeDefined();
    fireEvent.click(screen.getByTestId('btn-bank-history-x1'));
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'HDFC_bulk_2026-11.csv'));
  });
});
