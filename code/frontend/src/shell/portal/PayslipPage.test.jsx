import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { PayslipPage } from './PayslipPage.jsx';
import { apiClient } from '../../shared/api/client.js';

// PayslipController#own answers PayslipApiResponse<PayslipResponse>; every name below is the
// record's @JsonProperty (PayslipResponse.java, PayslipLineResponse.java).
const line = (code, name, amount, source = 'COMPONENT') => ({ code, name, amount, source, is_taxable: true });

const PAYSLIP = {
  run: {
    payrun_id: 'run-07',
    type: 'REGULAR',
    period: '2026-07',
    pay_date: '2026-07-31',
    paid_on: '2026-08-01',
    status: 'PAID',
  },
  employer: { name: 'Acme Analytics Pvt Ltd' },
  employee: {
    id: 'emp-1',
    number: 'EMP-001',
    name: 'Asha Rao',
    designation: 'Engineer',
    department: 'Platform',
    date_of_joining: '2025-04-01',
  },
  days: { payable_days: 31, paid_days: 30, lop_days: 1, unpaid_days: 0 },
  earnings: [line('BASIC', 'Basic', 60000), line('HRA', 'House rent allowance', 24000)],
  deductions: [line('PF', 'Provident fund', 7200), line('PT', 'Professional tax', 200)],
  reimbursements: [line('FUEL', 'Fuel reimbursement', 3000, 'REIMBURSEMENT')],
  benefits: [],
  totals: {
    gross_earnings: 84000,
    total_deductions: 7400,
    total_reimbursements: 3000,
    total_benefits: 0,
    net_pay: 79600,
  },
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/me/payslips/run-07']}>
      <Routes>
        <Route path="/me/payslips/:payrunId" element={<PayslipPage />} />
        <Route path="/me/payslips" element={<div>payslip list</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('PayslipPage renders one payslip (D-74)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('reads the payslip for the route and shows its header, lines and totals', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { status: 200, message: 'Payslip retrieved successfully', data: PAYSLIP },
    });

    renderPage();

    expect(await screen.findByText('Acme Analytics Pvt Ltd')).toBeDefined();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips/run-07');
    expect(screen.getByText('Asha Rao')).toBeDefined();
    expect(screen.getByText('EMP-001')).toBeDefined();
    expect(screen.getByText('Engineer')).toBeDefined();
    expect(screen.getByText('31 Jul 2026')).toBeDefined();
    expect(screen.getByText('1 Aug 2026')).toBeDefined();

    const earnings = within(screen.getByTestId('payslip-earnings'));
    expect(earnings.getByText('House rent allowance')).toBeDefined();
    expect(earnings.getByText('₹ 24,000.00')).toBeDefined();
    const deductions = within(screen.getByTestId('payslip-deductions'));
    expect(deductions.getByText('Provident fund')).toBeDefined();
    expect(deductions.getByText('₹ 7,200.00')).toBeDefined();
    expect(within(screen.getByTestId('payslip-reimbursements')).getByText('Fuel reimbursement')).toBeDefined();

    const totals = within(screen.getByTestId('payslip-totals'));
    expect(totals.getByText('₹ 84,000.00')).toBeDefined();
    expect(totals.getByText('₹ 7,400.00')).toBeDefined();
    expect(totals.getByText('₹ 79,600.00')).toBeDefined();
  });

  it('goes back to the payslip list', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { status: 200, message: 'ok', data: PAYSLIP } });

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /back to payslips/i }));
    expect(await screen.findByText('payslip list')).toBeDefined();
  });

  it('shows the server message when the payslip cannot be read', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue({ code: 'NOT_FOUND', message: 'Payslip not found' });

    renderPage();

    expect(await screen.findByText('Payslip not found')).toBeDefined();
    expect(screen.getByRole('button', { name: /retry/i })).toBeDefined();
  });
});
