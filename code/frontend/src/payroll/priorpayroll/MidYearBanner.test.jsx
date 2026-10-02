import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MidYearBanner } from './MidYearBanner.jsx';
import { priorPayrollService } from './priorPayrollService.js';

vi.mock('./priorPayrollService.js', () => ({
  priorPayrollService: {
    status: vi.fn(),
  },
}));

describe('MidYearBanner (W-47.6 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders alert with missing months and link to prior payroll when missing periods exist', async () => {
    priorPayrollService.status.mockResolvedValueOnce({
      financial_year: '2026-2027',
      missing_periods: ['2026-04', '2026-05'],
      setup_step_skipped: false,
    });

    render(
      <MemoryRouter>
        <MidYearBanner fy="2026-27" />
      </MemoryRouter>
    );

    const alertMsg = await screen.findByText(
      /Payroll for Apr 2026, May 2026 is not loaded. Tax already deducted in those months will be charged again./i,
      {},
      { timeout: 5000 }
    );
    expect(alertMsg).toBeTruthy();

    const link = screen.getByRole('link', { name: /Load prior payroll/i });
    expect(link.getAttribute('href')).toBe('/payroll/prior-payroll');
  });

  it('renders nothing when missing_periods is empty', async () => {
    priorPayrollService.status.mockResolvedValueOnce({
      financial_year: '2026-2027',
      missing_periods: [],
      setup_step_skipped: false,
    });

    const { container } = render(
      <MemoryRouter>
        <MidYearBanner fy="2026-27" />
      </MemoryRouter>
    );

    await waitFor(() => expect(priorPayrollService.status).toHaveBeenCalled());
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  it('renders nothing when setup_step_skipped is true even if missing periods exist', async () => {
    priorPayrollService.status.mockResolvedValueOnce({
      financial_year: '2026-2027',
      missing_periods: ['2026-04'],
      setup_step_skipped: true,
    });

    const { container } = render(
      <MemoryRouter>
        <MidYearBanner fy="2026-27" />
      </MemoryRouter>
    );

    await waitFor(() => expect(priorPayrollService.status).toHaveBeenCalled());
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  it('renders nothing and swallows error when status call rejects', async () => {
    priorPayrollService.status.mockRejectedValueOnce(new Error('Network error'));

    const { container } = render(
      <MemoryRouter>
        <MidYearBanner fy="2026-27" />
      </MemoryRouter>
    );

    await waitFor(() => expect(priorPayrollService.status).toHaveBeenCalled());
    expect(container.querySelector('.ant-alert')).toBeNull();
  });
});
