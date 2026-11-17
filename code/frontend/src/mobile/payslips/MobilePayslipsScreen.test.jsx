import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MobilePayslipsScreen } from './MobilePayslipsScreen.jsx';
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

describe('MobilePayslipsScreen component (W-98.4 §6)', () => {
  const mockRows = [
    {
      payrunId: 'pr-101',
      period: '2026-10',
      paidOn: '2026-10-31',
      netPay: 68500,
      status: 'PAID',
    },
    {
      payrunId: 'pr-100',
      period: '2026-09',
      paidOn: '2026-09-30',
      netPay: 68500,
      status: 'PAID',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders latest payslip card and history list when payslips exist', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: mockRows,
      total: 2,
      latest: mockRows[0],
    });

    render(<MobilePayslipsScreen />);

    expect(screen.getByTestId('payslips-loading')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByTestId('latest-payslip-card')).toBeTruthy();
      expect(screen.getByTestId('latest-net-pay').textContent).toContain('68,500.00');
      expect(screen.getByText('Past Payslips (2)')).toBeTruthy();
      expect(screen.getByTestId('payslip-item-pr-101')).toBeTruthy();
      expect(screen.getByTestId('payslip-item-pr-100')).toBeTruthy();
    });
  });

  it('renders empty state when no payslips are returned', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: [],
      total: 0,
      latest: null,
    });

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByText('No Payslips Published Yet')).toBeTruthy();
    });
  });

  it('opens detail drawer when clicking Breakdown on latest card', async () => {
    mobilePayslipService.getPayslips.mockResolvedValueOnce({
      rows: mockRows,
      total: 2,
      latest: mockRows[0],
    });
    mobilePayslipService.getPayslipDetail.mockResolvedValueOnce({
      run: { period: '2026-10' },
      employee: { name: 'Krushna' },
      totals: { net_pay: 68500 },
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
      total: 2,
      latest: mockRows[0],
    });
    mobilePayslipService.downloadAndSharePayslip.mockResolvedValueOnce({
      success: true,
      fileName: 'Payslip_2026-10.pdf',
    });

    render(<MobilePayslipsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('download-latest-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('download-latest-btn'));

    await waitFor(() => {
      expect(mobilePayslipService.downloadAndSharePayslip).toHaveBeenCalledWith({
        payrunId: 'pr-101',
        period: '2026-10',
      });
    });
  });
});
