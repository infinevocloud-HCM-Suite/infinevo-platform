import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MobilePayslipDetailSheet } from './MobilePayslipDetailSheet.jsx';
import { mobilePayslipService } from './mobilePayslipService.js';

vi.mock('./mobilePayslipService.js', () => ({
  mobilePayslipService: {
    getPayslipDetail: vi.fn(),
    downloadAndSharePayslip: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('MobilePayslipDetailSheet component (W-98.4 §6)', () => {
  const mockPayslipData = {
    run: {
      payrun_id: 'pr-101',
      period: '2026-10',
      paid_on: '2026-10-31',
      status: 'PAID',
    },
    employer: {
      name: 'Infinevo Cloud Solutions',
    },
    employee: {
      id: 'emp-001',
      number: 'EMP-001',
      name: 'Krushna Sonalkar',
      designation: 'Senior Developer',
      department: 'Engineering',
    },
    days: {
      payable_days: 31,
      paid_days: 31,
      lop_days: 0,
    },
    earnings: [
      { code: 'BASIC', name: 'Basic Salary', amount: 45000 },
      { code: 'HRA', name: 'House Rent Allowance', amount: 22500 },
      { code: 'SPECIAL', name: 'Special Allowance', amount: 7500 },
    ],
    deductions: [
      { code: 'EPF', name: 'Provident Fund (PF)', amount: 3600 },
      { code: 'PT', name: 'Professional Tax', amount: 200 },
      { code: 'TDS', name: 'Income Tax (TDS)', amount: 2700 },
    ],
    reimbursements: [],
    totals: {
      gross_earnings: 75000,
      total_deductions: 6500,
      net_pay: 68500,
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches and renders detailed earnings and deductions when opened', async () => {
    mobilePayslipService.getPayslipDetail.mockResolvedValueOnce(mockPayslipData);

    render(
      <MobilePayslipDetailSheet
        open={true}
        onClose={vi.fn()}
        payrunId="pr-101"
      />
    );

    expect(screen.getByTestId('mobile-payslip-detail-sheet')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('October 2026')).toBeTruthy();
      expect(screen.getByText('Krushna Sonalkar')).toBeTruthy();
      expect(screen.getByText('Basic Salary')).toBeTruthy();
      expect(screen.getByText('Provident Fund (PF)')).toBeTruthy();
    });

    // Check net pay rendered
    expect(screen.getByTestId('detail-net-pay').textContent).toContain('68,500.00');

    // Check earnings and deductions section
    expect(screen.getByTestId('earnings-section')).toBeTruthy();
    expect(screen.getByTestId('deductions-section')).toBeTruthy();
  });

  it('triggers onShare or downloadAndSharePayslip on button click', async () => {
    mobilePayslipService.getPayslipDetail.mockResolvedValueOnce(mockPayslipData);
    mobilePayslipService.downloadAndSharePayslip.mockResolvedValueOnce({
      success: true,
      fileName: 'Payslip_2026-10.pdf',
    });

    render(
      <MobilePayslipDetailSheet
        open={true}
        onClose={vi.fn()}
        payrunId="pr-101"
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('share-payslip-button')).toBeTruthy();
    });

    const shareBtn = screen.getByTestId('share-payslip-button');
    fireEvent.click(shareBtn);

    await waitFor(() => {
      expect(mobilePayslipService.downloadAndSharePayslip).toHaveBeenCalledWith({
        payrunId: 'pr-101',
        period: '2026-10',
        payslipData: mockPayslipData,
      });
    });
  });

  it('renders error alert when detail fetch fails', async () => {
    mobilePayslipService.getPayslipDetail.mockRejectedValueOnce(
      new Error('Failed to retrieve record')
    );

    render(
      <MobilePayslipDetailSheet
        open={true}
        onClose={vi.fn()}
        payrunId="pr-error"
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Unable to load payslip')).toBeTruthy();
      expect(screen.getByText('Failed to retrieve record')).toBeTruthy();
    });
  });
});
