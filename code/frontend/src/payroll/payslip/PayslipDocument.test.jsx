import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { PayslipDocument } from './PayslipDocument.jsx';

describe('PayslipDocument component', () => {
  const basePayslip = {
    employer: { name: 'Acme Corp' },
    employee: {
      number: 'EMP-001',
      name: 'John Doe',
      designation: 'Developer',
      department: 'Engineering',
      date_of_joining: '2025-01-01',
    },
    run: {
      period: '2026-10',
      pay_date: '2026-10-31',
      paid_on: '2026-10-31',
      status: 'PAID',
    },
    days: {
      payable_days: 30,
      paid_days: 30,
      lop_days: 0,
      unpaid_days: 0,
    },
    earnings: [{ code: 'BASIC', name: 'Basic', amount: 50000.0 }],
    totals: {
      gross_earnings: 50000.0,
      total_deductions: 2000.0,
      total_reimbursements: 0.0,
      total_benefits: 0.0,
      net_pay: 48000.0,
    },
  };

  it('renders Professional Tax exactly once without duplicating into Income Tax when no TDS deduction exists', () => {
    const payslip = {
      ...basePayslip,
      deductions: [
        { code: 'EPF', name: 'Provident Fund', amount: 1800.0 },
        { code: 'PROFESSIONAL_TAX', name: 'Professional tax', amount: 200.0 },
      ],
    };

    render(<PayslipDocument payslip={payslip} testIdPrefix="test-payslip" />);

    const deductionsEl = screen.getByTestId('test-payslip-deductions');
    const deductions = within(deductionsEl);

    // Income Tax should default to 0 and not steal Professional Tax
    expect(deductions.getByText('Income Tax')).toBeDefined();
    expect(deductions.getByText('₹ 0.00')).toBeDefined();

    // EPF should be present
    expect(deductions.getByText('Provident Fund')).toBeDefined();
    expect(deductions.getByText('₹ 1,800.00')).toBeDefined();

    // Professional Tax should appear exactly once
    const ptMatches = deductions.getAllByText('Professional tax');
    expect(ptMatches.length).toBe(1);
    expect(deductions.getByText('₹ 200.00')).toBeDefined();
  });

  it('renders TDS, EPF, and PT correctly when all three statutory deductions are present', () => {
    const payslip = {
      ...basePayslip,
      deductions: [
        { code: 'TDS', name: 'Tax deducted at source', amount: 2500.0 },
        { code: 'EPF', name: 'EPF Contribution', amount: 1800.0 },
        { code: 'PT', name: 'Professional Tax (PT)', amount: 200.0 },
        { code: 'LOAN', name: 'Loan Recovery', amount: 1000.0 },
      ],
    };

    render(<PayslipDocument payslip={payslip} testIdPrefix="test-payslip" />);

    const deductionsEl = screen.getByTestId('test-payslip-deductions');
    const deductions = within(deductionsEl);

    expect(deductions.getByText('Tax deducted at source')).toBeDefined();
    expect(deductions.getByText('₹ 2,500.00')).toBeDefined();

    expect(deductions.getByText('EPF Contribution')).toBeDefined();
    expect(deductions.getByText('₹ 1,800.00')).toBeDefined();

    const ptMatches = deductions.getAllByText('Professional Tax (PT)');
    expect(ptMatches.length).toBe(1);
    expect(deductions.getByText('₹ 200.00')).toBeDefined();

    // Other deduction
    expect(deductions.getByText('Loan Recovery')).toBeDefined();
    expect(deductions.getByText('₹ 1,000.00')).toBeDefined();
  });

  it('renders company logo in the header', () => {
    render(<PayslipDocument payslip={basePayslip} testIdPrefix="test-payslip" />);
    const logoEl = screen.getByTestId('test-payslip-logo');
    expect(logoEl).toBeDefined();
    expect(logoEl.getAttribute('alt')).toBe('Logo');
  });

  it('renders custom employer logo when provided', () => {
    const customPayslip = {
      ...basePayslip,
      employer: { name: 'Acme Corp', logo_url: 'https://example.com/acme-logo.png' },
    };
    render(<PayslipDocument payslip={customPayslip} testIdPrefix="test-payslip" />);
    const logoEl = screen.getByTestId('test-payslip-logo');
    expect(logoEl.getAttribute('src')).toBe('https://example.com/acme-logo.png');
  });
});

