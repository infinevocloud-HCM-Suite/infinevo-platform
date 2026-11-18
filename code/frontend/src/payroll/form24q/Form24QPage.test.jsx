import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Form24QPage } from './Form24QPage';
import { form24QService } from './form24QService';

vi.mock('./form24QService', () => ({
  form24QService: {
    listChallans: vi.fn(),
    getAnnexure1Preview: vi.fn(),
    exportAnnexure1: vi.fn(),
    getAnnexure2Preview: vi.fn(),
    exportAnnexure2: vi.fn(),
  },
}));

// Mock window.URL methods
globalThis.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
globalThis.URL.revokeObjectURL = vi.fn();

describe('Form24QPage component (W-89.4)', () => {
  const mockPreviewReconciled = {
    financialYear: '2026-2027',
    quarter: 'Q1',
    totalDeducteesCount: 15,
    totalGrossSalaryPaid: 1500000,
    totalTdsDeducted: 120000,
    totalTdsDeposited: 120000,
    shortfallAmount: 0,
    isReconciled: true,
    hasMissingPans: false,
  };

  const mockPreviewShortfall = {
    financialYear: '2026-2027',
    quarter: 'Q1',
    totalDeducteesCount: 10,
    totalGrossSalaryPaid: 1000000,
    totalTdsDeducted: 80000,
    totalTdsDeposited: 50000,
    shortfallAmount: 30000,
    isReconciled: false,
    hasMissingPans: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders header, selectors, statistics, and reconciled status banner', async () => {
    form24QService.listChallans.mockResolvedValueOnce([]);
    form24QService.getAnnexure1Preview.mockResolvedValueOnce(mockPreviewReconciled);

    render(<Form24QPage />);

    expect(screen.getByText('Form 24Q (TDS) Compliance')).toBeTruthy();
    expect(screen.getByTestId('record-challan-btn')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByTestId('reconciled-alert')).toBeTruthy();
      expect(screen.getByText('Return Ready for Filing')).toBeTruthy();
      expect(screen.getByTestId('stat-deductees-count')).toBeTruthy();
    });
  });

  it('renders deposit shortfall and missing PAN warning alerts when applicable', async () => {
    form24QService.listChallans.mockResolvedValueOnce([]);
    form24QService.getAnnexure1Preview.mockResolvedValueOnce(mockPreviewShortfall);

    render(<Form24QPage />);

    await waitFor(() => {
      expect(screen.getByTestId('shortfall-alert')).toBeTruthy();
      expect(screen.getByTestId('missing-pan-alert')).toBeTruthy();
    });
  });

  it('disables Annexure II export buttons on Q1-Q3 and enables them on Q4', async () => {
    form24QService.listChallans.mockResolvedValue([]);
    form24QService.getAnnexure1Preview.mockResolvedValue(mockPreviewReconciled);

    render(<Form24QPage />);

    await waitFor(() => {
      expect(screen.getByTestId('export-a1-csv-btn').hasAttribute('disabled')).toBe(false);
      expect(screen.getByTestId('export-a2-csv-btn').hasAttribute('disabled')).toBe(true);
      expect(screen.getByTestId('export-a2-txt-btn').hasAttribute('disabled')).toBe(true);
    });

    // Switch to Q4
    const q4Radio = screen.getByLabelText(/Q4 \(Jan – Mar\)/i);
    fireEvent.click(q4Radio);

    await waitFor(() => {
      expect(screen.getByTestId('export-a2-csv-btn').hasAttribute('disabled')).toBe(false);
      expect(screen.getByTestId('export-a2-txt-btn').hasAttribute('disabled')).toBe(false);
    });
  });

  it('triggers Annexure I export download when button clicked', async () => {
    form24QService.listChallans.mockResolvedValue([]);
    form24QService.getAnnexure1Preview.mockResolvedValue(mockPreviewReconciled);
    form24QService.exportAnnexure1.mockResolvedValue(new Blob(['test-csv']));

    render(<Form24QPage />);

    await waitFor(() => {
      expect(screen.getByTestId('export-a1-csv-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('export-a1-csv-btn'));

    await waitFor(() => {
      expect(form24QService.exportAnnexure1).toHaveBeenCalledWith(
        expect.any(String),
        'Q1',
        'CSV'
      );
      expect(globalThis.URL.createObjectURL).toHaveBeenCalled();
    });
  });
});
