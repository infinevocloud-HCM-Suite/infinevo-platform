import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { PublicPayslipPage } from './PublicPayslipPage.jsx';
import { apiClient } from '../../shared/api/client.js';

const line = (code, name, amount, source = 'COMPONENT') => ({ code, name, amount, source, is_taxable: true });

const PAYSLIP = {
  run: {
    payrun_id: 'run-10',
    type: 'REGULAR',
    period: '2026-10',
    pay_date: '2026-10-31',
    paid_on: '2026-11-01',
    status: 'PAID',
  },
  employer: { name: 'Acme Analytics Pvt Ltd' },
  employee: {
    id: 'emp-44',
    number: 'ITC-0044',
    name: 'Sahmad',
    designation: 'Senior Engineer',
    department: 'Engineering',
    date_of_joining: '2025-04-01',
  },
  days: { payable_days: 31, paid_days: 31, lop_days: 0, unpaid_days: 0 },
  earnings: [line('BASIC', 'Basic', 60000), line('HRA', 'House rent allowance', 22720)],
  deductions: [line('PF', 'Provident fund', 1800), line('PT', 'Professional tax', 200)],
  reimbursements: [],
  benefits: [],
  totals: {
    gross_earnings: 82720,
    total_deductions: 2000,
    total_reimbursements: 0,
    total_benefits: 0,
    net_pay: 80720,
  },
};

function renderPublicPage(search = '?t=valid-hmac-token') {
  delete window.location;
  window.location = new URL(`https://example.com/public/payslips${search}`);

  return render(
    <MemoryRouter initialEntries={[`/public/payslips${search}`]}>
      <Routes>
        <Route path="/public/payslips" element={<PublicPayslipPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('PublicPayslipPage (W-36.2 / Defect #7 R-03)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders payslip with header, employee details, earnings and totals when token is valid', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { status: 200, message: 'Payslip retrieved successfully', data: PAYSLIP },
    });

    renderPublicPage('?t=valid-hmac-token');

    expect(await screen.findByText('Acme Analytics Pvt Ltd')).toBeDefined();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payslips/open', {
      params: { t: 'valid-hmac-token' },
    });

    expect(screen.getByText('Sahmad')).toBeDefined();
    expect(screen.getByText('ITC-0044')).toBeDefined();
    expect(screen.getByText('Senior Engineer')).toBeDefined();
    expect(screen.getByText('Engineering')).toBeDefined();
    expect(screen.getByText('31 Oct 2026')).toBeDefined();
    expect(screen.getByText('1 Nov 2026')).toBeDefined();

    const earnings = within(screen.getByTestId('public-payslip-earnings'));
    expect(earnings.getByText('House rent allowance')).toBeDefined();
    expect(earnings.getByText('₹ 22,720.00')).toBeDefined();

    const deductions = within(screen.getByTestId('public-payslip-deductions'));
    expect(deductions.getByText('Provident fund')).toBeDefined();
    expect(deductions.getByText('₹ 1,800.00')).toBeDefined();

    const totals = within(screen.getByTestId('public-payslip-totals'));
    expect(totals.getByText('₹ 82,720.00')).toBeDefined();
    expect(totals.getByText('₹ 2,000.00')).toBeDefined();
    expect(totals.getByText('₹ 80,720.00')).toBeDefined();

    expect(screen.getByTestId('print-payslip-button')).toBeDefined();
  });

  it('renders 404 Result when link token is missing', async () => {
    renderPublicPage('');

    expect(await screen.findByText('This link is not valid or has expired')).toBeDefined();
    expect(screen.getByText('404')).toBeDefined();
    expect(screen.queryByTestId('public-payslip-page')).toBeNull();
  });

  it('renders 404 Result when token is invalid or expired (404 from backend)', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue({
      response: { status: 404, data: { code: 'NOT_FOUND', message: 'This link is not valid or has expired' } },
    });

    renderPublicPage('?t=expired-token');

    expect(await screen.findByText('This link is not valid or has expired')).toBeDefined();
    expect(screen.getByText('404')).toBeDefined();
  });

  it('triggers browser print preview on clicking Print / Save PDF', async () => {
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { status: 200, message: 'ok', data: PAYSLIP },
    });

    renderPublicPage('?t=valid-token');

    const printButton = await screen.findByTestId('print-payslip-button');
    fireEvent.click(printButton);

    expect(printSpy).toHaveBeenCalledTimes(1);
  });
});
