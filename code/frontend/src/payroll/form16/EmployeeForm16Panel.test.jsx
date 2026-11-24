import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EmployeeForm16Panel } from './EmployeeForm16Panel';
import { form16Service } from './form16Service';

vi.mock('./form16Service', () => ({
  form16Service: {
    getOwnStatement: vi.fn(),
    getOwnPartA: vi.fn(),
  },
}));

describe('EmployeeForm16Panel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders employee Form 16 header and overview cards', async () => {
    form16Service.getOwnStatement.mockResolvedValueOnce({
      financialYear: '2026-2027',
      assessmentYear: '2027-2028',
      totalDeducted: 5280.0,
      annualTax: 5280.0,
      balance: 0.0,
      deductor: { name: 'Infinevo Test Co' },
      employee: { name: 'Sahmad Khan' },
      quarters: [{ quarter: 'Q3', amountDeducted: 5280.0 }],
      breakdown: { grossTotalIncome: 82720.0 },
    });
    form16Service.getOwnPartA.mockResolvedValueOnce(null);

    render(<EmployeeForm16Panel />);

    expect(screen.getByText('Form 16 & Tax Deductions')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('Total Tax Deducted by Employer')).toBeTruthy();
      expect(screen.getByText('Certificate Pending')).toBeTruthy();
    });
  });

  it('renders Part A download button when certificate is available', async () => {
    form16Service.getOwnStatement.mockResolvedValueOnce({
      financialYear: '2026-2027',
      totalDeducted: 5280.0,
      deductor: { name: 'Infinevo Test Co' },
      employee: { name: 'Sahmad Khan' },
      quarters: [],
    });
    form16Service.getOwnPartA.mockResolvedValueOnce({
      document_id: 'doc-123',
      link: 'https://example.com/download/part-a.pdf',
    });

    render(<EmployeeForm16Panel />);

    await waitFor(() => {
      expect(screen.getByText('Certified Part A Available')).toBeTruthy();
      const downloadBtn = screen.getByText('Download Part A (PDF)');
      expect(downloadBtn).toBeTruthy();
      expect(downloadBtn.closest('a')).toHaveProperty('href', 'https://example.com/download/part-a.pdf');
    });
  });

  it('handles 404 statement error gracefully', async () => {
    form16Service.getOwnStatement.mockRejectedValueOnce({
      response: { status: 404 },
    });
    form16Service.getOwnPartA.mockResolvedValueOnce(null);

    render(<EmployeeForm16Panel />);

    await waitFor(() => {
      expect(screen.getByText(/your employee record could not be found/i)).toBeTruthy();
      // D-149: no statement, so no figures: not a zero and not a regime.
      expect(screen.queryByText('NEW')).toBeNull();
      expect(screen.queryByText(/₹0/)).toBeNull();
    });
  });
  it('renders a nil statement with the no TDS record note, not an error (D-150)', async () => {
    form16Service.getOwnStatement.mockResolvedValueOnce({
      financialYear: '2026-2027',
      assessmentYear: '2027-2028',
      regime: null,
      totalDeducted: 2000.0,
      annualTax: 0,
      balance: -2000.0,
      deductor: { name: 'Infinevo Test Co' },
      employee: { name: 'Sahmad Khan' },
      quarters: [{ quarter: 'Q1', amountDeducted: 2000.0 }],
      breakdown: null,
      breakdownNote: 'NO_TDS_RECORD',
      statementNote: 'NO_TDS_RECORD',
    });
    form16Service.getOwnPartA.mockResolvedValueOnce(null);

    render(<EmployeeForm16Panel />);

    await waitFor(() => {
      expect(screen.getByText('No TDS record for this year')).toBeTruthy();
      expect(screen.getByTestId('form16-statement')).toBeTruthy();
    });
    expect(screen.queryByText('No Form 16 Statement')).toBeNull();
  });
});
