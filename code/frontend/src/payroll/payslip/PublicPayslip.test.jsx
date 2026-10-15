import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { PublicPayslip } from './PublicPayslip.jsx';
import { publicPayslipService } from './publicPayslipService.js';

vi.mock('./publicPayslipService.js', () => ({
  publicPayslipService: {
    getPayslip: vi.fn(),
  },
}));

describe('PublicPayslip component', () => {
  const mockPayslip = {
    employer: { name: 'Infinevo Test Co' },
    employee: {
      number: 'ITC-0044',
      name: 'Sahmad',
      designation: 'Senior Software Engineer',
      department: 'Engineering',
      date_of_joining: '2025-01-15',
    },
    run: {
      period: '2026-10',
      pay_date: '2026-10-31',
      paid_on: '2026-10-31',
      status: 'PAID',
    },
    days: {
      payable_days: 31,
      paid_days: 31,
      lop_days: 0,
      unpaid_days: 0,
    },
    earnings: [
      { code: 'BASIC', name: 'Basic Salary', amount: 50000.0 },
      { code: 'HRA', name: 'House Rent Allowance', amount: 25000.0 },
      { code: 'SPECIAL', name: 'Special Allowance', amount: 7720.0 },
    ],
    deductions: [
      { code: 'EPF', name: 'Provident Fund (EPF)', amount: 5280.0 },
      { code: 'PT', name: 'Professional Tax (PT)', amount: 200.0 },
    ],
    totals: {
      gross_earnings: 82720.0,
      total_deductions: 5480.0,
      total_reimbursements: 0.0,
      total_benefits: 0.0,
      net_pay: 77240.0,
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders error state when token is missing', async () => {
    render(<PublicPayslip token="" />);

    await waitFor(() => {
      expect(screen.queryByTestId('public-payslip-error')).not.toBeNull();
    });
    expect(screen.getByText('Unable to view payslip')).toBeDefined();
    expect(screen.getByText('This link is not valid or has expired')).toBeDefined();
  });

  it('renders error state when service call fails (e.g. 404 expired)', async () => {
    publicPayslipService.getPayslip.mockRejectedValueOnce(
      new Error('This link is not valid or has expired'),
    );

    render(<PublicPayslip token="expired-token" />);

    await waitFor(() => {
      expect(screen.queryByTestId('public-payslip-error')).not.toBeNull();
    });
    expect(screen.getByText('This link is not valid or has expired')).toBeDefined();
  });

  it('renders complete payslip on success', async () => {
    publicPayslipService.getPayslip.mockResolvedValueOnce(mockPayslip);

    render(<PublicPayslip token="valid-token-123" />);

    await waitFor(() => {
      expect(screen.queryByTestId('public-payslip-page')).not.toBeNull();
    });

    // Check header and employee summary
    expect(screen.getAllByText('Infinevo Test Co').length).toBeGreaterThan(0);
    expect(screen.getByText('Payslip for Oct 2026')).toBeDefined();
    expect(screen.getByText('Sahmad')).toBeDefined();
    expect(screen.getByText('ITC-0044')).toBeDefined();
    expect(screen.getByText('Senior Software Engineer')).toBeDefined();
    expect(screen.getByText('Engineering')).toBeDefined();

    // Check attendance days
    const daysSection = screen.getByTestId('public-payslip-days');
    expect(daysSection.textContent).toContain('31');

    // Check earnings & deductions
    expect(screen.getByTestId('public-payslip-earnings').textContent).toContain('Basic Salary');
    expect(screen.getByTestId('public-payslip-deductions').textContent).toContain('Provident Fund (EPF)');

    // Check net pay and gross
    const totalsCard = screen.getByTestId('public-payslip-totals');
    expect(totalsCard.textContent).toContain('Net Pay');
  });

  it('triggers window.print when Download/Print button is clicked', async () => {
    publicPayslipService.getPayslip.mockResolvedValueOnce(mockPayslip);
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});

    render(<PublicPayslip token="valid-token-123" />);

    await waitFor(() => {
      expect(screen.queryByTestId('print-payslip-btn')).not.toBeNull();
    });

    fireEvent.click(screen.getByTestId('print-payslip-btn'));
    expect(printSpy).toHaveBeenCalled();

    printSpy.mockRestore();
  });
});
