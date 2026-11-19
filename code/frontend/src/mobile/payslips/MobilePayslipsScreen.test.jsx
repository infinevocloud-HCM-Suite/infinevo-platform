import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import {
  MobilePayslipsScreen,
  getFinancialYear,
  groupPayslipsByFY,
} from './MobilePayslipsScreen.jsx';
import { mobilePayslipService } from './mobilePayslipService.js';

vi.mock('./mobilePayslipService.js', () => ({
  mobilePayslipService: {
    getPayslips: vi.fn(),
    getPayslipDetail: vi.fn(),
    downloadAndSharePayslip: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('MobilePayslipsScreen component (W-98.4 §7)', () => {
  const mockRows = [
    {
      payrunId: 'pr-102',
      period: '2026-10',
      paidOn: '2026-10-31',
      netPay: '68500.00',
      status: 'PAID',
    },
    {
      payrunId: 'pr-101',
      period: '2026-09',
      paidOn: '2026-09-30',
      netPay: '68500.00',
      status: 'PAID',
    },
    {
      payrunId: 'pr-100',
      period: '2026-03',
      paidOn: '2026-03-31',
      netPay: '65000.00',
      status: 'PAID',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Financial Year calculation', () => {
    it('calculates Indian financial years (April to March) correctly', () => {
      expect(getFinancialYear('2026-04')).toBe('FY 2026-27');
      expect(getFinancialYear('2026-10')).toBe('FY 2026-27');
      expect(getFinancialYear('2027-01')).toBe('FY 2026-27');
      expect(getFinancialYear('2027-03')).toBe('FY 2026-27');
      expect(getFinancialYear('2026-03')).toBe('FY 2025-26');
      expect(getFinancialYear('')).toBe('Other');
    });

    it('groups items by Financial Year', () => {
      const grouped = groupPayslipsByFY(mockRows);
      expect(Object.keys(grouped)).toContain('FY 2026-27');
      expect(Object.keys(grouped)).toContain('FY 2025-26');
      expect(grouped['FY 2026-27']).toHaveLength(2);
      expect(grouped['FY 2025-26']).toHaveLength(1);
    });
  });

  it('renders latest payslip card and financial-year grouped history', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: mockRows,
      total: 3,
      latest: mockRows[0],
    });

    render(<MobilePayslipsScreen />);

    expect(screen.getByTestId('payslips-loading')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByTestId('latest-payslip-card')).toBeTruthy();
      expect(screen.getByTestId('latest-net-pay').textContent).toContain('68,500.00');
      // Financial-year group headings
      expect(screen.getByText('FY 2026-27 (2)')).toBeTruthy();
      expect(screen.getByText('FY 2025-26 (1)')).toBeTruthy();
      expect(screen.getByTestId('payslip-item-pr-102')).toBeTruthy();
      expect(screen.getByTestId('payslip-item-pr-101')).toBeTruthy();
      expect(screen.getByTestId('payslip-item-pr-100')).toBeTruthy();
    });
  });

  it('renders distinct empty state when no payslips are returned', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: [],
      total: 0,
      latest: null,
    });

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('payslips-empty-view')).toBeTruthy();
      expect(screen.getByText('No Payslips Published Yet')).toBeTruthy();
    });
  });

  it('renders distinct error state when fetching payslips fails', async () => {
    mobilePayslipService.getPayslips.mockRejectedValueOnce(
      new Error('Network connectivity lost')
    );

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('payslips-error-view')).toBeTruthy();
      expect(screen.getByText('Failed to load payslips')).toBeTruthy();
      expect(screen.getByText('Network connectivity lost')).toBeTruthy();
    });
  });

  it('opens detail drawer when clicking Breakdown on latest card', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: mockRows,
      total: 3,
      latest: mockRows[0],
    });
    mobilePayslipService.getPayslipDetail.mockResolvedValueOnce({
      run: { period: '2026-10' },
      employee: { name: 'Krushna' },
      totals: { net_pay: '68500.00' },
    });

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('view-latest-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('view-latest-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('mobile-payslip-detail-sheet')).toBeTruthy();
    });
  });

  it('triggers download and share when clicking PDF on latest card', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: mockRows,
      total: 3,
      latest: mockRows[0],
    });
    mobilePayslipService.downloadAndSharePayslip.mockResolvedValueOnce({
      success: true,
      fileName: 'payslip-2026-10.pdf',
    });

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('download-latest-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('download-latest-btn'));

    await waitFor(() => {
      expect(mobilePayslipService.downloadAndSharePayslip).toHaveBeenCalledWith({
        payrunId: 'pr-102',
        period: '2026-10',
      });
    });
  });
});
