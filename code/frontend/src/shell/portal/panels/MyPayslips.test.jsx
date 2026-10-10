import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { MyPayslips } from './MyPayslips.jsx';
import { apiClient } from '../../../shared/api/client.js';

// The body below is the server's own shape (D-74): PayslipController#listOwn answers
// PayslipApiResponse{status, message, data} whose data is a Spring Page of
// PayslipSummaryResponse{payrun_id, period, paid_on, net_pay} - snake_case by @JsonProperty.
const listBody = (content, extra = {}) => ({
  status: 200,
  message: 'Payslips retrieved successfully',
  data: { content, totalElements: content.length, number: 0, size: 12, ...extra },
});

const JULY = { payrun_id: 'run-07', period: '2026-07', paid_on: '2026-07-31', net_pay: 125000.0 };
const JUNE = { payrun_id: 'run-06', period: '2026-06', paid_on: '2026-06-30', net_pay: '98500.50' };

function renderPanel() {
  return render(
    <MemoryRouter initialEntries={['/me/payslips']}>
      <Routes>
        <Route path="/me/payslips" element={<MyPayslips />} />
        <Route path="/me/payslips/:payrunId" element={<div>payslip page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('MyPayslips lists the caller\'s paid payslips (D-74)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows one row per payslip with the paid date and the net pay in rupees', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: listBody([JULY, JUNE]) });

    renderPanel();

    expect(await screen.findByText('₹ 1,25,000.00')).toBeDefined();
    expect(screen.getByText('₹ 98,500.50')).toBeDefined();
    expect(screen.getByText('31 Jul 2026')).toBeDefined();
    expect(screen.getByText('Jul 2026')).toBeDefined();
    expect(screen.getAllByRole('button', { name: /view/i })).toHaveLength(2);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/payslips', { params: { page: 0, size: 12 } });
    // The placeholder is gone.
    expect(screen.queryByText(/Under Development/i)).toBeNull();
    expect(screen.queryByText(/Backend status/i)).toBeNull();
  });

  it('opens the payslip page for the row', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: listBody([JULY]) });

    renderPanel();

    fireEvent.click(await screen.findByRole('button', { name: /view/i }));
    expect(await screen.findByText('payslip page')).toBeDefined();
  });

  it('says so when there are no payslips yet', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: listBody([]) });

    renderPanel();

    expect(await screen.findByText('No payslips yet.')).toBeDefined();
  });

  it('shows the error and loads again on Retry', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockRejectedValueOnce({ code: 'INTERNAL', message: 'Payroll is unavailable' })
      .mockResolvedValueOnce({ data: listBody([JULY]) });

    renderPanel();

    expect(await screen.findByText('Payroll is unavailable')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('₹ 1,25,000.00')).toBeDefined());
    expect(get).toHaveBeenCalledTimes(2);
  });
});
