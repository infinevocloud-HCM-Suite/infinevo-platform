import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SummarySection } from './SummarySection';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    summary: vi.fn(),
  },
}));

describe('SummarySection', () => {
  const sampleSummary = {
    financial_year: '2026-27',
    tax_regime: 'NEW',
    status: 'SUBMITTED',
    declared: {
      house_rent_annual: 240000,
      home_loan_principal: 50000,
      home_loan_interest: 150000,
      let_out_net: 0,
      section6a_by_group: {
        '80C': 150000,
        '80D': 25000,
      },
      section6a_total: 175000,
      pre_tax_total: 30000,
      prev_employment_income: 200000,
      prev_employment_tax: 15000,
      other_income_total: 12000,
    },
    computed: {
      taxable_income: 900000,
      net_taxable_income: 850000,
      tax_on_taxable_income: 45000,
      tax_ytd_amount: 15000,
      tax_to_be_paid: 30000,
      tds_through_payroll: 15000,
      tds_previous_employer: 15000,
      tds_other_income: 0,
      other_sources_income: 12000,
      exemption_under_section10: 50000,
      exemption_under_section6a: 150000,
      remaining_months: 6,
      computed_at: '2026-04-10T12:00:00Z',
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders declared summary breakdown and forecast calculations', async () => {
    declarationService.summary.mockResolvedValueOnce(sampleSummary);

    render(<SummarySection fy="2026-27" />);

    expect(declarationService.summary).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByText('Tax Declaration & Computation Summary — FY 2026-27')).toBeTruthy();
      expect(screen.getByText('House Rent (Annual)')).toBeTruthy();
      expect(screen.getByText('Section 80C: ₹ 1,50,000')).toBeTruthy();
      expect(screen.getByText('Section 80D: ₹ 25,000')).toBeTruthy();
      expect(screen.getByText('Gross Taxable Income:')).toBeTruthy();
      expect(screen.getByText('₹ 9,00,000')).toBeTruthy();
      expect(screen.getAllByText('₹ 30,000').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('refreshes summary on clicking refresh button', async () => {
    declarationService.summary.mockResolvedValue(sampleSummary);

    render(<SummarySection fy="2026-27" />);

    await waitFor(() => {
      expect(declarationService.summary).toHaveBeenCalledTimes(1);
    });

    const refreshBtn = screen.getByTestId('reload-summary-btn');
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(declarationService.summary).toHaveBeenCalledTimes(2);
    });
  });
});
